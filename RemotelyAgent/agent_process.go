package main

import (
	"context"
	"errors"
	"fmt"
	"io"
	"os"
	"os/exec"
	"sort"
	"strings"
	"sync"
	"time"
)

type agentProcess struct {
	mu         sync.Mutex
	id         string
	jobID      string
	path       string
	terminal   bool
	cmd        *exec.Cmd
	input      io.WriteCloser
	resize     func(int, int) error
	output     *agentProcessOutput
	outputDone chan struct{}
	cancel     context.CancelFunc
	done       chan struct{}
	status     string
	pid        int
	exitCode   int
	startedAt  time.Time
	finishedAt time.Time
	err        error
}

const (
	agentMaxRetainedProcessOutput int64 = 256 * 1024 * 1024
	agentProcessInputChunkLimit         = 64 * 1024
	agentProcessInputQueueLimit         = 16
	agentProcessInputWaitTimeout        = 250 * time.Millisecond
)

type agentProcessOutputBudget struct {
	mu   sync.Mutex
	max  int64
	used int64
}

func newAgentProcessOutputBudget(max int64) *agentProcessOutputBudget {
	if max <= 0 {
		max = agentMaxRetainedProcessOutput
	}
	return &agentProcessOutputBudget{max: max}
}

func (b *agentProcessOutputBudget) reserve(size int64) int64 {
	if b == nil || size <= 0 {
		return 0
	}
	b.mu.Lock()
	defer b.mu.Unlock()
	remaining := b.max - b.used
	if remaining <= 0 {
		return 0
	}
	if size > remaining {
		size = remaining
	}
	b.used += size
	return size
}

func (b *agentProcessOutputBudget) release(size int64) {
	if b == nil || size <= 0 {
		return
	}
	b.mu.Lock()
	b.used -= size
	if b.used < 0 {
		b.used = 0
	}
	b.mu.Unlock()
}

type agentProcessOutputWriter struct {
	output *agentProcessOutput
	budget *agentProcessOutputBudget
}

func (w *agentProcessOutputWriter) Write(data []byte) (int, error) {
	if w == nil || w.output == nil {
		return len(data), nil
	}
	w.output.mu.Lock()
	defer w.output.mu.Unlock()
	if w.output.max <= 0 {
		w.output.truncated = true
		return len(data), nil
	}
	remaining := w.output.max - int64(len(w.output.data))
	if remaining <= 0 {
		w.output.truncated = true
		return len(data), nil
	}
	requested := int64(len(data))
	if requested > remaining {
		requested = remaining
	}
	retained := requested
	if w.budget != nil {
		retained = w.budget.reserve(requested)
	}
	if retained > 0 {
		w.output.data = append(w.output.data, data[:retained]...)
	}
	if retained < int64(len(data)) {
		w.output.truncated = true
	}
	return len(data), nil
}

type agentProcessInput struct {
	writer     io.WriteCloser
	queue      chan []byte
	done       chan struct{}
	workerDone chan struct{}
	closeOnce  sync.Once
	errMu      sync.Mutex
	err        error
}

func newAgentProcessInput(writer io.WriteCloser) *agentProcessInput {
	input := &agentProcessInput{
		writer:     writer,
		queue:      make(chan []byte, agentProcessInputQueueLimit),
		done:       make(chan struct{}),
		workerDone: make(chan struct{}),
	}
	go input.run()
	return input
}

func (i *agentProcessInput) run() {
	defer close(i.workerDone)
	for {
		select {
		case <-i.done:
			return
		case data := <-i.queue:
			if _, err := writeAgentProcessInputBytes(i.writer, data); err != nil {
				i.setError(err)
				i.closeWriter()
				return
			}
		}
	}
}

func (i *agentProcessInput) Write(data []byte) (int, error) {
	return writeAgentProcessInput(context.Background(), i, data)
}

func writeAgentProcessInput(ctx context.Context, input io.WriteCloser, data []byte) (int, error) {
	if bounded, ok := input.(*agentProcessInput); ok {
		return bounded.write(ctx, data)
	}
	return input.Write(data)
}

func (i *agentProcessInput) write(ctx context.Context, data []byte) (int, error) {
	if len(data) == 0 {
		return 0, nil
	}
	if len(data) > agentProcessInputChunkLimit {
		return 0, errors.New("process input exceeds configured limit")
	}
	if ctx == nil {
		ctx = context.Background()
	}
	copyData := append([]byte(nil), data...)
	timer := time.NewTimer(agentProcessInputWaitTimeout)
	defer timer.Stop()
	select {
	case <-i.done:
		return 0, i.errorOrClosed()
	case <-ctx.Done():
		return 0, ctx.Err()
	case i.queue <- copyData:
		select {
		case <-i.done:
			return 0, i.errorOrClosed()
		case <-ctx.Done():
			return 0, ctx.Err()
		default:
		}
		return len(data), nil
	case <-timer.C:
		return 0, errors.New("process input is backpressured")
	}
}

func writeAgentProcessInputBytes(writer io.WriteCloser, data []byte) (int, error) {
	if writer == nil {
		return 0, errors.New("process does not accept input")
	}
	written, err := writer.Write(data)
	if err == nil && written != len(data) {
		err = io.ErrShortWrite
	}
	return written, err
}

func (i *agentProcessInput) setError(err error) {
	i.errMu.Lock()
	i.err = err
	i.errMu.Unlock()
}

func (i *agentProcessInput) errorOrClosed() error {
	i.errMu.Lock()
	err := i.err
	i.errMu.Unlock()
	if err != nil {
		return err
	}
	return errors.New("process input is closed")
}

func (i *agentProcessInput) closeWriter() {
	i.closeOnce.Do(func() {
		close(i.done)
		if i.writer != nil {
			_ = i.writer.Close()
		}
	})
}

func (i *agentProcessInput) Close() error {
	i.closeWriter()
	timer := time.NewTimer(agentProcessInputWaitTimeout)
	defer timer.Stop()
	select {
	case <-i.workerDone:
		return nil
	case <-timer.C:
		return errors.New("process input close timed out")
	}
}

type agentProcessManager struct {
	mu           sync.Mutex
	processes    map[string]*agentProcess
	maxOutput    int64
	maxProcesses int
	maxRecords   int
	outputBudget *agentProcessOutputBudget
}

func newAgentProcessManager(maxOutput int64, maxProcesses int) *agentProcessManager {
	return newAgentProcessManagerWithBudget(maxOutput, maxProcesses, 0)
}

func newAgentProcessManagerWithBudget(maxOutput int64, maxProcesses int, maxRetainedOutput int64) *agentProcessManager {
	if maxOutput <= 0 {
		maxOutput = 16 * 1024 * 1024
	}
	if maxOutput > agentMaxRetainedProcessOutput {
		maxOutput = agentMaxRetainedProcessOutput
	}
	if maxProcesses <= 0 {
		maxProcesses = 64
	}
	if maxProcesses > 256 {
		maxProcesses = 256
	}
	maxRecords := maxProcesses * 4
	if maxRetainedOutput <= 0 {
		maxRetainedOutput = maxOutput
		if maxRetainedOutput <= int64(^uint64(0)>>1)/int64(maxProcesses) {
			maxRetainedOutput *= int64(maxProcesses)
		} else {
			maxRetainedOutput = agentMaxRetainedProcessOutput
		}
		if maxRetainedOutput > agentMaxRetainedProcessOutput {
			maxRetainedOutput = agentMaxRetainedProcessOutput
		}
	}
	if maxRetainedOutput > agentMaxRetainedProcessOutput {
		maxRetainedOutput = agentMaxRetainedProcessOutput
	}
	if maxRetainedOutput < maxOutput {
		maxRetainedOutput = maxOutput
	}
	return &agentProcessManager{
		processes:    make(map[string]*agentProcess),
		maxOutput:    maxOutput,
		maxProcesses: maxProcesses,
		maxRecords:   maxRecords,
		outputBudget: newAgentProcessOutputBudget(maxRetainedOutput),
	}
}

func (m *agentProcessManager) start(root agentRoot, request agentProcessRequest) (*agentProcess, error) {
	if request.Path == "" {
		return nil, errors.New("process path is required")
	}
	path, err := resolveAgentExecutable(root, request.Path)
	if err != nil {
		return nil, err
	}
	workingDir := root.canonical
	if request.WorkingDir != "" {
		workingDir, err = resolveAgentRelativePath(root, request.WorkingDir, false)
		if err != nil {
			return nil, err
		}
	}
	return m.startResolved(path, workingDir, request.Args, request.Environment, request.Terminal)
}

func (m *agentProcessManager) startResolved(path, workingDir string, args []string, environmentValues map[string]string, terminal bool) (*agentProcess, error) {
	return m.startResolvedIO(path, workingDir, args, environmentValues, terminal, nil)
}

func (m *agentProcessManager) startProtocol(path, workingDir string, args []string, environmentValues map[string]string) (*agentProcess, *agentProcessOutput, error) {
	protocolOutput := newAgentProcessOutput(m.maxOutput)
	process, err := m.startResolvedIO(path, workingDir, args, environmentValues, false, m.outputWriter(protocolOutput))
	if err != nil {
		m.discardOutput(protocolOutput)
		return nil, nil, err
	}
	return process, protocolOutput, nil
}

func (m *agentProcessManager) startResolvedIO(path, workingDir string, args []string, environmentValues map[string]string, terminal bool, stdout io.Writer) (*agentProcess, error) {
	info, err := os.Stat(path)
	if err != nil {
		return nil, err
	}
	if !info.Mode().IsRegular() {
		return nil, errors.New("process path is not a regular file")
	}
	if info, err := os.Stat(workingDir); err != nil || !info.IsDir() {
		if err != nil {
			return nil, err
		}
		return nil, errors.New("process working directory is not a directory")
	}
	for _, arg := range args {
		if strings.IndexByte(arg, 0) >= 0 {
			return nil, errors.New("process arguments must not contain null bytes")
		}
	}
	environment, err := agentEnvironment(environmentValues)
	if err != nil {
		return nil, err
	}
	ctx, cancel := context.WithCancel(context.Background())
	cmd := exec.CommandContext(ctx, path, args...)
	cmd.Dir = workingDir
	cmd.Env = environment
	output := newAgentProcessOutput(m.maxOutput)
	outputWriter := m.outputWriter(output)
	if stdout == nil {
		stdout = outputWriter
	}
	m.mu.Lock()
	m.pruneLocked()
	if len(m.processes) >= m.maxRecords {
		m.mu.Unlock()
		cancel()
		return nil, errors.New("too many retained process records")
	}
	active := 0
	for _, existing := range m.processes {
		existing.mu.Lock()
		if existing.status == "running" {
			active++
		}
		existing.mu.Unlock()
	}
	if active >= m.maxProcesses {
		m.mu.Unlock()
		cancel()
		return nil, errors.New("too many active processes")
	}
	streams, err := agentStartProcess(cmd, terminal, stdout, outputWriter)
	if err != nil {
		m.mu.Unlock()
		cancel()
		return nil, err
	}
	process := &agentProcess{
		id:         agentRandomID("process"),
		path:       path,
		terminal:   terminal,
		cmd:        cmd,
		input:      newAgentProcessInput(streams.input),
		resize:     streams.resize,
		output:     output,
		outputDone: nil,
		cancel:     cancel,
		done:       make(chan struct{}),
		status:     "running",
		pid:        cmd.Process.Pid,
		startedAt:  time.Now(),
	}
	m.processes[process.id] = process
	m.mu.Unlock()
	if streams.output != nil {
		process.outputDone = make(chan struct{})
		go func() {
			defer close(process.outputDone)
			_, _ = io.Copy(outputWriter, streams.output)
			_ = streams.output.Close()
		}()
	}
	go process.wait(streams.cleanup)
	return process, nil
}

func (m *agentProcessManager) pruneLocked() {
	for len(m.processes) >= m.maxRecords {
		var oldestID string
		var oldest time.Time
		for id, process := range m.processes {
			process.mu.Lock()
			finishedAt := process.finishedAt
			terminal := process.status != "running"
			process.mu.Unlock()
			if terminal && (oldestID == "" || finishedAt.Before(oldest)) {
				oldestID = id
				oldest = finishedAt
			}
		}
		if oldestID == "" {
			return
		}
		process := m.processes[oldestID]
		delete(m.processes, oldestID)
		m.discardOutput(process.output)
	}
}

func (m *agentProcessManager) outputWriter(output *agentProcessOutput) io.Writer {
	return &agentProcessOutputWriter{output: output, budget: m.outputBudget}
}

func (m *agentProcessManager) discardOutput(output *agentProcessOutput) {
	if output == nil {
		return
	}
	output.mu.Lock()
	retained := int64(len(output.data))
	output.data = nil
	output.mu.Unlock()
	if m.outputBudget != nil {
		m.outputBudget.release(retained)
	}
}

func resolveAgentExecutable(root agentRoot, relative string) (string, error) {
	path, err := resolveAgentRelativePath(root, relative, false)
	if err != nil {
		return "", err
	}
	info, err := os.Stat(path)
	if err != nil {
		return "", err
	}
	if !info.Mode().IsRegular() {
		return "", errors.New("process path is not a regular file")
	}
	return path, nil
}

func resolveAgentRelativePath(root agentRoot, relative string, write bool) (string, error) {
	if strings.IndexByte(relative, 0) >= 0 || relative == "" || strings.HasPrefix(relative, "/") || strings.HasPrefix(relative, "\\") {
		return "", errors.New("path must be relative to the selected root")
	}
	return resolveAgentPath(root, relative, write)
}

func agentEnvironment(values map[string]string) ([]string, error) {
	variables := make(map[string]string)
	for _, entry := range os.Environ() {
		parts := strings.SplitN(entry, "=", 2)
		if len(parts) == 2 {
			variables[parts[0]] = parts[1]
		}
	}
	if len(values) == 0 {
		environment := make([]string, 0, len(variables))
		for key, value := range variables {
			environment = append(environment, key+"="+value)
		}
		sort.Strings(environment)
		return environment, nil
	}
	keys := make([]string, 0, len(values))
	for key := range values {
		keys = append(keys, key)
	}
	sort.Strings(keys)
	for _, key := range keys {
		if key == "" || strings.IndexByte(key, '=') >= 0 || strings.IndexByte(key, 0) >= 0 || strings.IndexByte(values[key], 0) >= 0 {
			return nil, errors.New("invalid process environment entry")
		}
		variables[key] = values[key]
	}
	environment := make([]string, 0, len(variables))
	for key, value := range variables {
		environment = append(environment, key+"="+value)
	}
	sort.Strings(environment)
	return environment, nil
}

func (m *agentProcessManager) get(id string) (*agentProcess, bool) {
	m.mu.Lock()
	process, ok := m.processes[id]
	m.mu.Unlock()
	return process, ok
}

func (p *agentProcess) wait(cleanup func() error) {
	err := p.cmd.Wait()
	p.mu.Lock()
	input := p.input
	p.mu.Unlock()
	if input != nil {
		_ = input.Close()
	}
	if cleanup != nil {
		_ = cleanup()
	}
	if p.outputDone != nil {
		<-p.outputDone
	}
	p.mu.Lock()
	defer p.mu.Unlock()
	p.finishedAt = time.Now()
	p.err = err
	if err == nil {
		p.status = "exited"
		p.exitCode = 0
	} else if exitErr, ok := err.(*exec.ExitError); ok {
		p.status = "exited"
		p.exitCode = exitErr.ExitCode()
	} else {
		p.status = "failed"
	}
	close(p.done)
}

func (p *agentProcess) setJobID(id string) {
	p.mu.Lock()
	p.jobID = id
	p.mu.Unlock()
}

func (p *agentProcess) snapshot() agentProcessSnapshot {
	p.mu.Lock()
	defer p.mu.Unlock()
	p.output.mu.Lock()
	outputSize := int64(len(p.output.data))
	outputLimit := p.output.truncated
	p.output.mu.Unlock()
	snapshot := agentProcessSnapshot{
		ID:          p.id,
		JobID:       p.jobID,
		Status:      p.status,
		Path:        p.path,
		Terminal:    p.terminal,
		PID:         p.pid,
		ExitCode:    p.exitCode,
		StartedAt:   p.startedAt.UnixMilli(),
		FinishedAt:  p.finishedAt.UnixMilli(),
		OutputSize:  outputSize,
		OutputLimit: outputLimit,
	}
	if p.err != nil && p.status != "exited" {
		snapshot.Error = p.err.Error()
	}
	return snapshot
}

func (p *agentProcess) writeInput(data []byte) error {
	return p.writeInputContext(context.Background(), data)
}

func (p *agentProcess) writeInputContext(ctx context.Context, data []byte) error {
	p.mu.Lock()
	input := p.input
	status := p.status
	p.mu.Unlock()
	if input == nil {
		return errors.New("process does not accept input")
	}
	if status != "running" {
		return errors.New("process is not running")
	}
	if len(data) > agentProcessInputChunkLimit {
		return errors.New("process input exceeds configured limit")
	}
	if ctx == nil {
		ctx = context.Background()
	}
	_, err := writeAgentProcessInput(ctx, input, data)
	return err
}

func (p *agentProcess) resizeTerminal(rows, columns int) error {
	if rows < 1 || rows > 1000 || columns < 1 || columns > 1000 {
		return errors.New("terminal size is outside the supported range")
	}
	p.mu.Lock()
	resize := p.resize
	p.mu.Unlock()
	if resize == nil {
		return errors.New("process does not expose a terminal")
	}
	return resize(rows, columns)
}

func (p *agentProcess) stop() {
	p.mu.Lock()
	if p.status != "running" {
		p.mu.Unlock()
		return
	}
	cancel := p.cancel
	process := p.cmd.Process
	input := p.input
	p.mu.Unlock()
	if input != nil {
		_ = input.Close()
	}
	cancel()
	if process != nil {
		_ = process.Kill()
	}
}

func (p *agentProcess) await(ctx context.Context, report *agentJobReporter) (any, error) {
	select {
	case <-p.done:
		report.set(1, 1, "finished")
		return p.snapshot(), nil
	case <-ctx.Done():
		p.stop()
		<-p.done
		return nil, ctx.Err()
	}
}

func (p *agentProcess) String() string {
	return fmt.Sprintf("%s:%s", p.id, p.path)
}
