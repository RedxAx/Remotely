package main

import (
	"context"
	"errors"
	"io"
	"strings"
	"testing"
	"time"
)

type agentBlockingInputWriter struct {
	closed chan struct{}
}

func newAgentBlockingInputWriter() *agentBlockingInputWriter {
	return &agentBlockingInputWriter{closed: make(chan struct{})}
}

func (w *agentBlockingInputWriter) Write(data []byte) (int, error) {
	<-w.closed
	return 0, io.ErrClosedPipe
}

func (w *agentBlockingInputWriter) Close() error {
	select {
	case <-w.closed:
	default:
		close(w.closed)
	}
	return nil
}

func waitAgentJob(t *testing.T, manager *agentJobManager, id string) agentJobSnapshot {
	t.Helper()
	deadline := time.Now().Add(time.Second)
	for time.Now().Before(deadline) {
		snapshot, ok := manager.snapshot(id)
		if ok && snapshot.Status != "queued" && snapshot.Status != "running" {
			return snapshot
		}
		time.Sleep(time.Millisecond)
	}
	t.Fatal("job did not finish")
	return agentJobSnapshot{}
}

func TestAgentProcessInputClosesBlockedWriter(t *testing.T) {
	writer := newAgentBlockingInputWriter()
	input := newAgentProcessInput(writer)
	for index := 0; len(input.queue) < cap(input.queue); index++ {
		if _, err := input.Write([]byte("x")); err != nil {
			t.Fatalf("queue write %d failed: %v", index, err)
		}
	}
	if err := input.Close(); err != nil {
		t.Fatal(err)
	}
	if _, err := input.Write([]byte("x")); err == nil {
		t.Fatal("input write succeeded after close")
	}
}

func TestAgentProcessInputRejectsOversizedWrite(t *testing.T) {
	writer := newAgentBlockingInputWriter()
	input := newAgentProcessInput(writer)
	defer input.Close()
	if _, err := input.Write([]byte(strings.Repeat("x", agentProcessInputChunkLimit+1))); err == nil {
		t.Fatal("expected oversized process input to be rejected")
	}
}

func TestAgentProcessOutputUsesGlobalBudget(t *testing.T) {
	manager := newAgentProcessManagerWithBudget(8, 2, 10)
	first := newAgentProcessOutput(manager.maxOutput)
	second := newAgentProcessOutput(manager.maxOutput)
	if _, err := manager.outputWriter(first).Write([]byte("1234")); err != nil {
		t.Fatal(err)
	}
	if _, err := manager.outputWriter(second).Write([]byte("abcdefgh")); err != nil {
		t.Fatal(err)
	}
	first.mu.Lock()
	firstSize := len(first.data)
	first.mu.Unlock()
	second.mu.Lock()
	secondSize := len(second.data)
	secondLimit := second.truncated
	second.mu.Unlock()
	if firstSize != 4 || secondSize != 6 || !secondLimit {
		t.Fatalf("unexpected retained output sizes: first=%d second=%d truncated=%v", firstSize, secondSize, secondLimit)
	}
	manager.outputBudget.mu.Lock()
	used := manager.outputBudget.used
	manager.outputBudget.mu.Unlock()
	if used != 10 {
		t.Fatalf("global output budget used %d bytes, expected 10", used)
	}
	manager.discardOutput(first)
	manager.outputBudget.mu.Lock()
	used = manager.outputBudget.used
	manager.outputBudget.mu.Unlock()
	if used != 6 {
		t.Fatalf("discarded output left %d bytes retained", used)
	}
}

func TestAgentJobsUseGlobalResultBudget(t *testing.T) {
	manager := newAgentJobManagerWithBudget(2, 8)
	firstID, err := manager.start("test", func(context.Context, *agentJobReporter) (any, error) {
		return "12345", nil
	})
	if err != nil {
		t.Fatal(err)
	}
	first := waitAgentJob(t, manager, firstID)
	if first.Result != "12345" {
		t.Fatalf("first result was not retained: %#v", first.Result)
	}
	secondID, err := manager.start("test", func(context.Context, *agentJobReporter) (any, error) {
		return "67890", nil
	})
	if err != nil {
		t.Fatal(err)
	}
	second := waitAgentJob(t, manager, secondID)
	if second.Result != nil {
		t.Fatalf("second result exceeded global budget but was retained: %#v", second.Result)
	}
	manager.mu.Lock()
	retained := manager.retainedResultBytes
	manager.mu.Unlock()
	if retained != 5 {
		t.Fatalf("global job result budget retained %d bytes, expected 5", retained)
	}
}

func TestAgentProcessInputContextCancellation(t *testing.T) {
	writer := newAgentBlockingInputWriter()
	input := newAgentProcessInput(writer)
	defer input.Close()
	for index := 0; len(input.queue) < cap(input.queue); index++ {
		if _, err := input.Write([]byte("x")); err != nil {
			t.Fatalf("queue write %d failed: %v", index, err)
		}
	}
	ctx, cancel := context.WithCancel(context.Background())
	cancel()
	if _, err := writeAgentProcessInput(ctx, input, []byte("x")); !errors.Is(err, context.Canceled) {
		t.Fatalf("expected context cancellation, got %v", err)
	}
}
