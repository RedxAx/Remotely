package main

import (
	"bytes"
	"context"
	"crypto/tls"
	"encoding/json"
	"errors"
	"fmt"
	"io"
	"net/http"
	"os"
	"path/filepath"
	"strings"
	"sync"
	"time"
)

type AgentRelayConfig struct {
	Endpoint       string
	PairingCode    string
	CredentialFile string
	Service        *AgentService
	TLSConfig      *tls.Config
	PinnedSPKI     []string
	MaxConcurrent  int
	QueueMessages  int
	QueueBytes     int64
}

type AgentRelay struct {
	config AgentRelayConfig
}

type agentRelayEnvelope struct {
	Type       string          `json:"type"`
	ID         string          `json:"id,omitempty"`
	Method     string          `json:"method,omitempty"`
	Path       string          `json:"path,omitempty"`
	Body       []byte          `json:"body,omitempty"`
	Status     int             `json:"status,omitempty"`
	Code       string          `json:"code,omitempty"`
	Error      string          `json:"error,omitempty"`
	Credential string          `json:"credential,omitempty"`
	JobID      string          `json:"jobId,omitempty"`
	ProcessID  string          `json:"processId,omitempty"`
	Offset     int64           `json:"offset,omitempty"`
	Data       json.RawMessage `json:"data,omitempty"`
}

type agentRelayPairingError struct {
	err     error
	revoked bool
}

func (e *agentRelayPairingError) Error() string {
	return e.err.Error()
}

func (e *agentRelayPairingError) Unwrap() error {
	return e.err
}

func NewAgentRelay(config AgentRelayConfig) (*AgentRelay, error) {
	if err := validateAgentRelayURL(config.Endpoint); err != nil {
		return nil, err
	}
	if config.Service == nil {
		return nil, errors.New("relay service is required")
	}
	if config.CredentialFile == "" {
		config.CredentialFile = defaultAgentCredentialFile()
	}
	if len(config.PairingCode) > 256 || strings.ContainsAny(config.PairingCode, "\r\n") {
		return nil, errors.New("invalid relay pairing code")
	}
	if config.MaxConcurrent <= 0 {
		config.MaxConcurrent = 16
	}
	if config.MaxConcurrent > 64 {
		config.MaxConcurrent = 64
	}
	if config.QueueMessages <= 0 {
		config.QueueMessages = 128
	}
	if config.QueueMessages > 1024 {
		config.QueueMessages = 1024
	}
	if config.QueueBytes <= 0 {
		config.QueueBytes = 16 * 1024 * 1024
	}
	if config.QueueBytes > 64*1024*1024 {
		config.QueueBytes = 64 * 1024 * 1024
	}
	return &AgentRelay{config: config}, nil
}

func (r *AgentRelay) Run(ctx context.Context) error {
	credential, present, err := loadAgentCredential(r.config.CredentialFile)
	if err != nil {
		return err
	}
	if !present && strings.TrimSpace(r.config.PairingCode) == "" {
		return errors.New("relay pairing code or device credential is required")
	}
	delay := time.Second
	for {
		if err := ctx.Err(); err != nil {
			return nil
		}
		pairingCode := ""
		if credential == "" {
			pairingCode = r.config.PairingCode
		}
		websocket, dialErr := dialAgentWebSocket(ctx, r.config.Endpoint, agentWebSocketConfig{TLSConfig: r.config.TLSConfig, PinnedSPKI: r.config.PinnedSPKI, PairingCode: pairingCode, Credential: credential})
		if dialErr != nil {
			var authErr *agentRelayAuthError
			if errors.As(dialErr, &authErr) {
				if credential != "" {
					_ = removeAgentCredential(r.config.CredentialFile)
				}
				return dialErr
			}
			if credential == "" && pairingCode != "" {
				return dialErr
			}
			if err := waitAgentRelay(ctx, delay); err != nil {
				return nil
			}
			if delay < 30*time.Second {
				delay *= 2
				if delay > 30*time.Second {
					delay = 30 * time.Second
				}
			}
			continue
		}
		delay = time.Second
		session := newAgentRelaySession(r, websocket)
		newCredential, accepted, establishErr := session.establish(ctx)
		if establishErr != nil {
			_ = websocket.close()
			if ctx.Err() != nil {
				return nil
			}
			var pairingErr *agentRelayPairingError
			if errors.As(establishErr, &pairingErr) {
				if pairingErr.revoked || credential != "" {
					_ = removeAgentCredential(r.config.CredentialFile)
				}
				return establishErr
			}
			if err := waitAgentRelay(ctx, delay); err != nil {
				return nil
			}
			if delay < 30*time.Second {
				delay *= 2
				if delay > 30*time.Second {
					delay = 30 * time.Second
				}
			}
			continue
		}
		if accepted && newCredential != "" {
			if err := saveAgentCredential(r.config.CredentialFile, newCredential); err != nil {
				_ = websocket.close()
				return err
			}
			credential = newCredential
			r.config.PairingCode = ""
		}
		if err := session.send(agentRelayEnvelope{Type: "ready"}); err != nil {
			_ = websocket.close()
			if ctx.Err() != nil {
				return nil
			}
			if err := waitAgentRelay(ctx, delay); err != nil {
				return nil
			}
			continue
		}
		_ = session.run(ctx)
		if err := ctx.Err(); err != nil {
			return nil
		}
		if credential == "" && r.config.PairingCode == "" {
			return errors.New("relay disconnected before pairing completed")
		}
		if err := waitAgentRelay(ctx, delay); err != nil {
			return nil
		}
		if delay < 30*time.Second {
			delay *= 2
			if delay > 30*time.Second {
				delay = 30 * time.Second
			}
		}
	}
}

func waitAgentRelay(ctx context.Context, delay time.Duration) error {
	timer := time.NewTimer(delay)
	defer timer.Stop()
	select {
	case <-ctx.Done():
		return ctx.Err()
	case <-timer.C:
		return nil
	}
}

type agentRelaySession struct {
	relay     *AgentRelay
	websocket *agentWebSocket
	ctx       context.Context
	cancel    context.CancelFunc
	writer    *agentRelayWriter
	requests  chan struct{}
	watchMu   sync.Mutex
	watches   map[string]*agentRelayWatch
	closed    sync.Once
}

type agentRelayWatch struct {
	cancel context.CancelFunc
}

func newAgentRelaySession(relay *AgentRelay, websocket *agentWebSocket) *agentRelaySession {
	return &agentRelaySession{relay: relay, websocket: websocket, requests: make(chan struct{}, relay.config.MaxConcurrent), watches: make(map[string]*agentRelayWatch)}
}

func (s *agentRelaySession) establish(ctx context.Context) (string, bool, error) {
	closed := make(chan struct{})
	go func() {
		select {
		case <-ctx.Done():
			_ = s.websocket.close()
		case <-closed:
		}
	}()
	defer close(closed)
	_ = s.websocket.conn.SetReadDeadline(time.Now().Add(15 * time.Second))
	opcode, data, err := s.websocket.readMessage()
	_ = s.websocket.conn.SetReadDeadline(time.Time{})
	if err != nil {
		return "", false, err
	}
	if opcode != 1 && opcode != 2 {
		return "", false, errors.New("relay pairing response is not a data message")
	}
	var envelope agentRelayEnvelope
	if err := json.Unmarshal(data, &envelope); err != nil {
		return "", false, err
	}
	switch envelope.Type {
	case "pair.accept":
		if envelope.Credential == "" || len(envelope.Credential) > 4096 || strings.ContainsAny(envelope.Credential, "\r\n") {
			return "", false, &agentRelayPairingError{err: errors.New("relay pairing response has no device credential")}
		}
		return envelope.Credential, true, nil
	case "auth.ok":
		return "", false, nil
	case "auth.revoked":
		return "", false, &agentRelayPairingError{err: errors.New("relay device credential was revoked"), revoked: true}
	case "error":
		if envelope.Code == "revoked" {
			return "", false, &agentRelayPairingError{err: errors.New("relay device credential was revoked"), revoked: true}
		}
		return "", false, &agentRelayPairingError{err: errors.New(envelope.Error)}
	default:
		return "", false, errors.New("relay pairing response has an unknown type")
	}
}

func (s *agentRelaySession) run(ctx context.Context) error {
	s.ctx, s.cancel = context.WithCancel(ctx)
	s.websocket.readTimeout = 90 * time.Second
	s.writer = newAgentRelayWriter(s.websocket, s.relay.config.QueueMessages, s.relay.config.QueueBytes)
	go s.writer.run()
	writerError := make(chan error, 1)
	go func() {
		select {
		case err := <-s.writer.errors:
			writerError <- err
		case <-s.ctx.Done():
		}
	}()
	readerError := make(chan error, 1)
	go func() {
		readerError <- s.readLoop()
	}()
	heartbeatError := make(chan error, 1)
	go func() {
		ticker := time.NewTicker(30 * time.Second)
		defer ticker.Stop()
		for {
			select {
			case <-s.ctx.Done():
				return
			case <-ticker.C:
				if err := s.websocket.writeMessage(0x9, nil); err != nil {
					heartbeatError <- err
					return
				}
			}
		}
	}()
	select {
	case err := <-readerError:
		s.shutdown()
		return err
	case err := <-writerError:
		s.shutdown()
		return err
	case err := <-heartbeatError:
		s.shutdown()
		return err
	case <-ctx.Done():
		s.shutdown()
		return nil
	}
}

func (s *agentRelaySession) readLoop() error {
	for {
		opcode, data, err := s.websocket.readMessage()
		if err != nil {
			return err
		}
		if opcode != 1 && opcode != 2 {
			return errors.New("relay message has an unsupported opcode")
		}
		var envelope agentRelayEnvelope
		if len(data) > agentRelayMaxMessage {
			return errors.New("relay message exceeds limit")
		}
		if err := json.Unmarshal(data, &envelope); err != nil {
			return err
		}
		if len(envelope.Type) > 32 || len(envelope.ID) > 128 || len(envelope.Method) > 16 || len(envelope.Path) > 16*1024 {
			return errors.New("relay envelope field exceeds limit")
		}
		switch envelope.Type {
		case "request":
			if envelope.ID == "" || envelope.Method == "" || envelope.Path == "" {
				if err := s.send(agentRelayEnvelope{Type: "error", ID: envelope.ID, Error: "request id, method, and path are required"}); err != nil {
					return err
				}
				continue
			}
			select {
			case s.requests <- struct{}{}:
				go func(request agentRelayEnvelope) {
					defer func() { <-s.requests }()
					s.handleRequest(request)
				}(envelope)
			default:
				if err := s.send(agentRelayEnvelope{Type: "error", ID: envelope.ID, Error: "too many concurrent relay requests"}); err != nil {
					return err
				}
			}
		case "cancel":
			s.cancelWatch(envelope.ID)
		default:
			if err := s.send(agentRelayEnvelope{Type: "error", ID: envelope.ID, Error: "unknown relay message type"}); err != nil {
				return err
			}
		}
	}
}

func (s *agentRelaySession) handleRequest(request agentRelayEnvelope) {
	status, body, err := s.relay.config.Service.dispatchRelay(s.ctx, request.Method, request.Path, request.Body)
	if err != nil {
		_ = s.send(agentRelayEnvelope{Type: "response", ID: request.ID, Status: http.StatusBadGateway, Error: err.Error()})
		return
	}
	if err := s.send(agentRelayEnvelope{Type: "response", ID: request.ID, Status: status, Body: body}); err != nil {
		return
	}
	var result struct {
		JobID     string `json:"jobId"`
		ProcessID string `json:"processId"`
	}
	if json.Unmarshal(body, &result) == nil {
		if result.JobID != "" || result.ProcessID != "" {
			s.watch(request.ID, result.JobID, result.ProcessID)
		}
	}
}

func (s *agentRelaySession) watch(requestID, jobID, processID string) {
	watchCtx, cancel := context.WithCancel(s.ctx)
	watch := &agentRelayWatch{cancel: cancel}
	s.watchMu.Lock()
	previous := s.watches[requestID]
	s.watches[requestID] = watch
	s.watchMu.Unlock()
	if previous != nil {
		previous.cancel()
	}
	go func() {
		defer func() {
			s.watchMu.Lock()
			if s.watches[requestID] == watch {
				delete(s.watches, requestID)
			}
			s.watchMu.Unlock()
			cancel()
		}()
		jobDone := jobID == ""
		processDone := processID == ""
		var offset int64
		ticker := time.NewTicker(200 * time.Millisecond)
		defer ticker.Stop()
		for {
			if jobID != "" {
				snapshot, ok := s.relay.config.Service.jobs.snapshot(jobID)
				if !ok {
					_ = s.send(agentRelayEnvelope{Type: "progress", ID: requestID, JobID: jobID, Error: "job was not found"})
					return
				}
				data, _ := json.Marshal(snapshot)
				if s.send(agentRelayEnvelope{Type: "progress", ID: requestID, JobID: jobID, Data: data}) != nil {
					return
				}
				jobDone = snapshot.Status == "completed" || snapshot.Status == "failed" || snapshot.Status == "canceled"
			}
			if processID != "" {
				process, ok := s.relay.config.Service.processes.get(processID)
				if !ok {
					_ = s.send(agentRelayEnvelope{Type: "progress", ID: requestID, ProcessID: processID, Error: "process was not found"})
					return
				}
				snapshot := process.snapshot()
				data, _ := json.Marshal(snapshot)
				if s.send(agentRelayEnvelope{Type: "progress", ID: requestID, ProcessID: processID, Data: data}) != nil {
					return
				}
				chunk, nextOffset, _, readErr := process.output.read(offset, 256*1024)
				if readErr != nil {
					_ = s.send(agentRelayEnvelope{Type: "output", ID: requestID, ProcessID: processID, Error: readErr.Error()})
					return
				}
				if len(chunk) > 0 {
					if s.send(agentRelayEnvelope{Type: "output", ID: requestID, ProcessID: processID, Offset: offset, Body: chunk}) != nil {
						return
					}
				}
				offset = nextOffset
				processDone = snapshot.Status != "running" && offset >= snapshot.OutputSize
			}
			if jobDone && processDone {
				return
			}
			select {
			case <-watchCtx.Done():
				return
			case <-ticker.C:
			}
		}
	}()
}

func (s *agentRelaySession) cancelWatch(id string) {
	s.watchMu.Lock()
	watch := s.watches[id]
	s.watchMu.Unlock()
	if watch != nil {
		watch.cancel()
	}
}

func (s *agentRelaySession) send(envelope agentRelayEnvelope) error {
	if s.writer == nil {
		return s.websocket.writeMessage(2, mustAgentRelayJSON(envelope))
	}
	err := s.writer.enqueue(envelope)
	if err != nil {
		s.shutdown()
	}
	return err
}

func (s *agentRelaySession) shutdown() {
	s.closed.Do(func() {
		if s.cancel != nil {
			s.cancel()
		}
		s.watchMu.Lock()
		for _, watch := range s.watches {
			watch.cancel()
		}
		s.watches = make(map[string]*agentRelayWatch)
		s.watchMu.Unlock()
		if s.writer != nil {
			s.writer.close()
		}
		_ = s.websocket.close()
	})
}

type agentRelayWriter struct {
	websocket *agentWebSocket
	queue     chan []byte
	maxBytes  int64
	bytes     int64
	mu        sync.Mutex
	closed    bool
	closeOnce sync.Once
	errors    chan error
}

func newAgentRelayWriter(websocket *agentWebSocket, maxMessages int, maxBytes int64) *agentRelayWriter {
	return &agentRelayWriter{websocket: websocket, queue: make(chan []byte, maxMessages), maxBytes: maxBytes, errors: make(chan error, 1)}
}

func (w *agentRelayWriter) enqueue(envelope agentRelayEnvelope) error {
	data, err := json.Marshal(envelope)
	if err != nil {
		return err
	}
	if int64(len(data)) > agentRelayMaxMessage {
		return errors.New("relay message exceeds limit")
	}
	w.mu.Lock()
	defer w.mu.Unlock()
	if w.closed || w.bytes+int64(len(data)) > w.maxBytes {
		return errors.New("relay outbound queue is full")
	}
	select {
	case w.queue <- data:
		w.bytes += int64(len(data))
		return nil
	default:
		return errors.New("relay outbound queue is full")
	}
}

func (w *agentRelayWriter) run() {
	for data := range w.queue {
		if err := w.websocket.writeMessage(2, data); err != nil {
			select {
			case w.errors <- err:
			default:
			}
			return
		}
		w.mu.Lock()
		w.bytes -= int64(len(data))
		w.mu.Unlock()
	}
}

func (w *agentRelayWriter) close() {
	w.closeOnce.Do(func() {
		w.mu.Lock()
		w.closed = true
		w.mu.Unlock()
		close(w.queue)
	})
}

func mustAgentRelayJSON(envelope agentRelayEnvelope) []byte {
	data, _ := json.Marshal(envelope)
	return data
}

func (s *AgentService) dispatchRelay(ctx context.Context, method, path string, body []byte) (int, []byte, error) {
	if !strings.HasPrefix(path, "/v1/") {
		return http.StatusBadRequest, nil, errors.New("relay path is outside the capability API")
	}
	switch method {
	case http.MethodGet, http.MethodPost, http.MethodPut, http.MethodDelete:
	default:
		return http.StatusBadRequest, nil, errors.New("relay method is not supported")
	}
	if len(body) > agentRelayMaxMessage {
		return http.StatusRequestEntityTooLarge, nil, errors.New("relay request body exceeds limit")
	}
	request, err := http.NewRequestWithContext(ctx, method, "http://agent.invalid"+path, bytes.NewReader(body))
	if err != nil {
		return http.StatusBadRequest, nil, err
	}
	request.Header.Set("Authorization", "Bearer "+string(s.token))
	writer := &agentRelayResponseWriter{header: make(http.Header), max: agentRelayMaxMessage}
	s.ServeHTTP(writer, request)
	return writer.status, writer.body.Bytes(), writer.err
}

type agentRelayResponseWriter struct {
	header http.Header
	body   bytes.Buffer
	status int
	max    int64
	err    error
}

func (w *agentRelayResponseWriter) Header() http.Header {
	return w.header
}

func (w *agentRelayResponseWriter) WriteHeader(status int) {
	if w.status == 0 {
		w.status = status
	}
}

func (w *agentRelayResponseWriter) Write(data []byte) (int, error) {
	if w.status == 0 {
		w.status = http.StatusOK
	}
	if int64(w.body.Len()+len(data)) > w.max {
		w.err = errors.New("relay response exceeds limit")
		return 0, w.err
	}
	return w.body.Write(data)
}

func loadAgentCredential(path string) (string, bool, error) {
	if path == "" {
		return "", false, errors.New("device credential file is required")
	}
	abs, err := filepath.Abs(path)
	if err != nil {
		return "", false, err
	}
	credential, present, err := readAgentSecret(abs, 16, 4096)
	if err != nil {
		return "", false, fmt.Errorf("device credential: %w", err)
	}
	return credential, present, nil
}

func saveAgentCredential(path, credential string) error {
	if len(credential) < 16 || len(credential) > 4096 || strings.ContainsAny(credential, "\r\n") {
		return errors.New("invalid device credential")
	}
	abs, err := filepath.Abs(path)
	if err != nil {
		return err
	}
	if err := os.MkdirAll(filepath.Dir(abs), 0700); err != nil {
		return err
	}
	if err := secureAgentSecretDirectory(filepath.Dir(abs)); err != nil {
		return err
	}
	tmp, err := os.CreateTemp(filepath.Dir(abs), ".remotely-agent-credential-*")
	if err != nil {
		return err
	}
	tmpPath := tmp.Name()
	defer os.Remove(tmpPath)
	_ = tmp.Chmod(0600)
	if _, err := io.WriteString(tmp, credential); err != nil {
		tmp.Close()
		return err
	}
	if err := tmp.Sync(); err != nil {
		tmp.Close()
		return err
	}
	if err := tmp.Close(); err != nil {
		return err
	}
	if err := os.Rename(tmpPath, abs); err != nil {
		return err
	}
	if err := os.Chmod(abs, 0600); err != nil {
		return err
	}
	return secureAgentSecretFile(abs)
}

func removeAgentCredential(path string) error {
	if path == "" {
		return nil
	}
	err := os.Remove(path)
	if errors.Is(err, os.ErrNotExist) {
		return nil
	}
	return err
}

func defaultAgentCredentialFile() string {
	home, err := os.UserHomeDir()
	if err != nil || home == "" {
		home = os.TempDir()
	}
	return filepath.Join(home, ".remotely", "agent", "device")
}
