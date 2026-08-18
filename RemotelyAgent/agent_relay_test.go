package main

import (
	"bufio"
	"context"
	"crypto/tls"
	"crypto/x509"
	"encoding/json"
	"fmt"
	"io"
	"net"
	"net/http"
	"net/http/httptest"
	"os"
	"path/filepath"
	"strings"
	"sync"
	"testing"
	"time"
)

func TestAgentRelayPairsAndDispatchesScopedRequest(t *testing.T) {
	root := t.TempDir()
	service, err := NewAgentService(AgentServiceConfig{Token: []byte("01234567890123456789012345678901"), Roots: []AgentRoot{{ID: "workspace", Path: root, Read: true}}})
	if err != nil {
		t.Fatal(err)
	}
	defer service.Close()
	var mu sync.Mutex
	connections := 0
	responseReceived := make(chan struct{}, 1)
	server := httptest.NewTLSServer(http.HandlerFunc(func(writer http.ResponseWriter, request *http.Request) {
		if request.Header.Get("Sec-WebSocket-Protocol") != agentRelaySubprotocol {
			return
		}
		mu.Lock()
		connections++
		connection := connections
		mu.Unlock()
		if connection == 1 && request.Header.Get("X-Remotely-Pairing-Code") != "one-time-code" {
			return
		}
		if connection > 1 && request.Header.Get("Authorization") != "Bearer device-credential-0123456789" {
			return
		}
		hijacker, ok := writer.(http.Hijacker)
		if !ok {
			return
		}
		connectionSocket, buffered, err := hijacker.Hijack()
		if err != nil {
			return
		}
		defer connectionSocket.Close()
		key := request.Header.Get("Sec-WebSocket-Key")
		_, _ = fmt.Fprintf(connectionSocket, "HTTP/1.1 101 Switching Protocols\r\nUpgrade: websocket\r\nConnection: Upgrade\r\nSec-WebSocket-Accept: %s\r\nSec-WebSocket-Protocol: %s\r\n\r\n", websocketAccept(key), agentRelaySubprotocol)
		reader := buffered.Reader
		if connection == 1 {
			writeAgentRelayTestFrame(connectionSocket, mustAgentRelayJSON(agentRelayEnvelope{Type: "pair.accept", Credential: "device-credential-0123456789"}))
		} else {
			writeAgentRelayTestFrame(connectionSocket, mustAgentRelayJSON(agentRelayEnvelope{Type: "auth.ok"}))
		}
		if _, _, err := readAgentRelayTestFrame(reader); err != nil {
			return
		}
		writeAgentRelayTestFrame(connectionSocket, mustAgentRelayJSON(agentRelayEnvelope{Type: "request", ID: "capabilities", Method: http.MethodGet, Path: "/v1/capabilities"}))
		_, data, err := readAgentRelayTestFrame(reader)
		if err != nil {
			return
		}
		var response agentRelayEnvelope
		if err := json.Unmarshal(data, &response); err != nil {
			return
		}
		if response.Type == "response" && response.ID == "capabilities" && response.Status == http.StatusOK {
			responseReceived <- struct{}{}
		}
	}))
	defer server.Close()
	pool := x509.NewCertPool()
	pool.AddCert(server.Certificate())
	credentialFile := filepath.Join(t.TempDir(), "device")
	relay, err := NewAgentRelay(AgentRelayConfig{Endpoint: "wss" + strings.TrimPrefix(server.URL, "https"), PairingCode: "one-time-code", CredentialFile: credentialFile, Service: service, TLSConfig: &tls.Config{RootCAs: pool}})
	if err != nil {
		t.Fatal(err)
	}
	ctx, cancel := context.WithCancel(context.Background())
	defer cancel()
	result := make(chan error, 1)
	go func() { result <- relay.Run(ctx) }()
	select {
	case <-responseReceived:
	case <-time.After(5 * time.Second):
		t.Fatal("relay request was not dispatched")
	}
	credential, present, err := loadAgentCredential(credentialFile)
	if err != nil || !present || credential != "device-credential-0123456789" {
		t.Fatalf("device credential was not persisted safely: %q %v", credential, err)
	}
	cancel()
	select {
	case err := <-result:
		if err != nil {
			t.Fatal(err)
		}
	case <-time.After(2 * time.Second):
		t.Fatal("relay did not stop after context cancellation")
	}
}

func TestAgentRelayRequiresWSS(t *testing.T) {
	_, err := NewAgentRelay(AgentRelayConfig{Endpoint: "ws://127.0.0.1:1", Service: &AgentService{}})
	if err == nil || !strings.Contains(err.Error(), "wss") {
		t.Fatalf("expected wss validation error, got %v", err)
	}
}

func TestAgentRelayRejectsBadTLSPin(t *testing.T) {
	server := httptest.NewTLSServer(http.HandlerFunc(func(writer http.ResponseWriter, request *http.Request) {}))
	defer server.Close()
	pool := x509.NewCertPool()
	pool.AddCert(server.Certificate())
	ctx, cancel := context.WithTimeout(context.Background(), time.Second)
	defer cancel()
	_, err := dialAgentWebSocket(ctx, "wss"+strings.TrimPrefix(server.URL, "https"), agentWebSocketConfig{TLSConfig: &tls.Config{RootCAs: pool}, PinnedSPKI: []string{"00"}})
	if err == nil || !strings.Contains(err.Error(), "pin mismatch") {
		t.Fatalf("expected pin mismatch, got %v", err)
	}
}

func TestAgentRelayCredentialAndQueueBounds(t *testing.T) {
	path := filepath.Join(t.TempDir(), "nested", "device")
	if err := saveAgentCredential(path, "device-credential-0123456789"); err != nil {
		t.Fatal(err)
	}
	credential, present, err := loadAgentCredential(path)
	if err != nil || !present || credential != "device-credential-0123456789" {
		t.Fatalf("credential persistence failed: %q %v", credential, err)
	}
	writer := newAgentRelayWriter(nil, 1, 64)
	if err := writer.enqueue(agentRelayEnvelope{Type: "response", ID: "one"}); err != nil {
		t.Fatal(err)
	}
	if err := writer.enqueue(agentRelayEnvelope{Type: "response", ID: "two"}); err == nil {
		t.Fatal("expected bounded relay queue to reject a second message")
	}
	writer.close()
}

func TestAgentWebSocketRejectsReservedFrameBits(t *testing.T) {
	client, server := net.Pipe()
	defer client.Close()
	defer server.Close()
	websocket := &agentWebSocket{conn: client, reader: bufio.NewReader(client), maxFrame: agentRelayMaxFrame}
	go func() {
		_, _ = server.Write([]byte{0xc2, 0x00})
	}()
	if _, _, err := websocket.readMessage(); err == nil || !strings.Contains(err.Error(), "unsupported extension") {
		t.Fatalf("expected reserved-bit rejection, got %v", err)
	}
}

func TestAgentWebSocketRejectsNonMinimalLength(t *testing.T) {
	client, server := net.Pipe()
	defer client.Close()
	defer server.Close()
	websocket := &agentWebSocket{conn: client, reader: bufio.NewReader(client), maxFrame: agentRelayMaxFrame}
	go func() {
		_, _ = server.Write([]byte{0x82, 126, 0, 1, 'x'})
	}()
	if _, _, err := websocket.readMessage(); err == nil || !strings.Contains(err.Error(), "non-minimal") {
		t.Fatalf("expected non-minimal-length rejection, got %v", err)
	}
}

func TestAgentCredentialRejectsSymlink(t *testing.T) {
	directory := t.TempDir()
	target := filepath.Join(directory, "target")
	if err := os.WriteFile(target, []byte("device-credential-0123456789"), 0600); err != nil {
		t.Fatal(err)
	}
	link := filepath.Join(directory, "device")
	if err := os.Symlink(target, link); err != nil {
		t.Skipf("symlinks are unavailable: %v", err)
	}
	if _, _, err := loadAgentCredential(link); err == nil || !strings.Contains(err.Error(), "regular file") {
		t.Fatalf("expected credential symlink rejection, got %v", err)
	}
}

func TestAgentRelayReconnectsWithPersistedCredential(t *testing.T) {
	root := t.TempDir()
	service, err := NewAgentService(AgentServiceConfig{Token: []byte("01234567890123456789012345678901"), Roots: []AgentRoot{{ID: "workspace", Path: root, Read: true}}})
	if err != nil {
		t.Fatal(err)
	}
	defer service.Close()
	secondConnected := make(chan struct{}, 1)
	releaseSecond := make(chan struct{})
	var mu sync.Mutex
	connections := 0
	server := httptest.NewTLSServer(http.HandlerFunc(func(writer http.ResponseWriter, request *http.Request) {
		mu.Lock()
		connections++
		connection := connections
		mu.Unlock()
		hijacker, ok := writer.(http.Hijacker)
		if !ok {
			return
		}
		connectionSocket, _, err := hijacker.Hijack()
		if err != nil {
			return
		}
		defer connectionSocket.Close()
		_, _ = fmt.Fprintf(connectionSocket, "HTTP/1.1 101 Switching Protocols\r\nUpgrade: websocket\r\nConnection: Upgrade\r\nSec-WebSocket-Accept: %s\r\nSec-WebSocket-Protocol: %s\r\n\r\n", websocketAccept(request.Header.Get("Sec-WebSocket-Key")), agentRelaySubprotocol)
		if connection == 1 {
			writeAgentRelayTestFrame(connectionSocket, mustAgentRelayJSON(agentRelayEnvelope{Type: "pair.accept", Credential: "device-credential-0123456789"}))
		} else {
			if request.Header.Get("Authorization") != "Bearer device-credential-0123456789" {
				return
			}
			writeAgentRelayTestFrame(connectionSocket, mustAgentRelayJSON(agentRelayEnvelope{Type: "auth.ok"}))
			secondConnected <- struct{}{}
			<-releaseSecond
			return
		}
		reader := bufio.NewReader(connectionSocket)
		_, _, _ = readAgentRelayTestFrame(reader)
	}))
	defer server.Close()
	pool := x509.NewCertPool()
	pool.AddCert(server.Certificate())
	relay, err := NewAgentRelay(AgentRelayConfig{Endpoint: "wss" + strings.TrimPrefix(server.URL, "https"), PairingCode: "one-time-code", CredentialFile: filepath.Join(t.TempDir(), "device"), Service: service, TLSConfig: &tls.Config{RootCAs: pool}})
	if err != nil {
		t.Fatal(err)
	}
	ctx, cancel := context.WithCancel(context.Background())
	result := make(chan error, 1)
	go func() { result <- relay.Run(ctx) }()
	select {
	case <-secondConnected:
	case <-time.After(5 * time.Second):
		cancel()
		t.Fatal("relay did not reconnect with the persisted credential")
	}
	close(releaseSecond)
	cancel()
	select {
	case err := <-result:
		if err != nil {
			t.Fatal(err)
		}
	case <-time.After(2 * time.Second):
		t.Fatal("relay did not stop after reconnect test")
	}
}

func TestAgentRelayRemovesRevokedCredential(t *testing.T) {
	server := httptest.NewTLSServer(http.HandlerFunc(func(writer http.ResponseWriter, request *http.Request) {
		hijacker, ok := writer.(http.Hijacker)
		if !ok {
			return
		}
		connection, _, err := hijacker.Hijack()
		if err != nil {
			return
		}
		defer connection.Close()
		_, _ = fmt.Fprintf(connection, "HTTP/1.1 101 Switching Protocols\r\nUpgrade: websocket\r\nConnection: Upgrade\r\nSec-WebSocket-Accept: %s\r\nSec-WebSocket-Protocol: %s\r\n\r\n", websocketAccept(request.Header.Get("Sec-WebSocket-Key")), agentRelaySubprotocol)
		writeAgentRelayTestFrame(connection, mustAgentRelayJSON(agentRelayEnvelope{Type: "auth.revoked"}))
	}))
	defer server.Close()
	pool := x509.NewCertPool()
	pool.AddCert(server.Certificate())
	credentialFile := filepath.Join(t.TempDir(), "device")
	if err := saveAgentCredential(credentialFile, "device-credential-0123456789"); err != nil {
		t.Fatal(err)
	}
	service, err := NewAgentService(AgentServiceConfig{Token: []byte("01234567890123456789012345678901"), Roots: []AgentRoot{{ID: "workspace", Path: t.TempDir(), Read: true}}})
	if err != nil {
		t.Fatal(err)
	}
	defer service.Close()
	relay, err := NewAgentRelay(AgentRelayConfig{Endpoint: "wss" + strings.TrimPrefix(server.URL, "https"), CredentialFile: credentialFile, Service: service, TLSConfig: &tls.Config{RootCAs: pool}})
	if err != nil {
		t.Fatal(err)
	}
	err = relay.Run(context.Background())
	if err == nil || !strings.Contains(err.Error(), "revoked") {
		t.Fatalf("expected revocation error, got %v", err)
	}
	if _, err := os.Stat(credentialFile); !os.IsNotExist(err) {
		t.Fatalf("revoked credential was not removed: %v", err)
	}
}

func writeAgentRelayTestFrame(connection net.Conn, payload []byte) {
	header := []byte{0x82}
	switch {
	case len(payload) < 126:
		header = append(header, byte(len(payload)))
	case len(payload) <= 0xffff:
		header = append(header, 126, byte(len(payload)>>8), byte(len(payload)))
	default:
		header = append(header, 127, 0, 0, 0, 0, byte(len(payload)>>24), byte(len(payload)>>16), byte(len(payload)>>8), byte(len(payload)))
	}
	_, _ = connection.Write(header)
	_, _ = connection.Write(payload)
}

func readAgentRelayTestFrame(reader *bufio.Reader) (byte, []byte, error) {
	first, err := reader.ReadByte()
	if err != nil {
		return 0, nil, err
	}
	second, err := reader.ReadByte()
	if err != nil {
		return 0, nil, err
	}
	length := int(second & 0x7f)
	if length == 126 {
		var extended [2]byte
		if _, err := io.ReadFull(reader, extended[:]); err != nil {
			return 0, nil, err
		}
		length = int(extended[0])<<8 | int(extended[1])
	} else if length == 127 {
		return 0, nil, fmt.Errorf("test frame too large")
	}
	var mask [4]byte
	if second&0x80 != 0 {
		if _, err := io.ReadFull(reader, mask[:]); err != nil {
			return 0, nil, err
		}
	}
	payload := make([]byte, length)
	if _, err := io.ReadFull(reader, payload); err != nil {
		return 0, nil, err
	}
	if second&0x80 != 0 {
		for i := range payload {
			payload[i] ^= mask[i%4]
		}
	}
	return first & 0x0f, payload, nil
}
