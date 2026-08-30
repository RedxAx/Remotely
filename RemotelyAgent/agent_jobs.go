package main

import (
	"context"
	"encoding/json"
	"errors"
	"sync"
	"time"
)

const (
	agentMaxRetainedJobResultBytes  = 16 * 1024 * 1024
	agentMaxJobProgressMessageBytes = 4096
	agentMaxJobErrorBytes           = 4096
	agentMaxJobKindBytes            = 256
)

type agentJobSnapshot struct {
	ID         string           `json:"id"`
	Kind       string           `json:"kind"`
	Status     string           `json:"status"`
	Progress   agentJobProgress `json:"progress"`
	Result     any              `json:"result,omitempty"`
	Error      string           `json:"error,omitempty"`
	StartedAt  int64            `json:"startedAt,omitempty"`
	FinishedAt int64            `json:"finishedAt,omitempty"`
}

type agentJobProgress struct {
	Completed int64  `json:"completed"`
	Total     int64  `json:"total,omitempty"`
	Message   string `json:"message,omitempty"`
}

type agentJobReporter struct {
	job *agentJob
}

func (r *agentJobReporter) set(completed, total int64, message string) {
	if r == nil || r.job == nil {
		return
	}
	if len(message) > agentMaxJobProgressMessageBytes {
		message = message[:agentMaxJobProgressMessageBytes]
	}
	r.job.mu.Lock()
	if r.job.status == "running" {
		r.job.progress = agentJobProgress{Completed: completed, Total: total, Message: message}
	}
	r.job.mu.Unlock()
}

type agentJob struct {
	mu          sync.Mutex
	id          string
	kind        string
	status      string
	progress    agentJobProgress
	result      any
	err         error
	startedAt   time.Time
	finishedAt  time.Time
	cancel      context.CancelFunc
	resultBytes int64
}

type agentJobManager struct {
	mu                  sync.Mutex
	jobs                map[string]*agentJob
	maxJobs             int
	maxRecords          int
	maxResultBytes      int64
	retainedResultBytes int64
}

func newAgentJobManager(maxJobs int) *agentJobManager {
	return newAgentJobManagerWithBudget(maxJobs, 0)
}

func newAgentJobManagerWithBudget(maxJobs int, maxResultBytes int64) *agentJobManager {
	if maxJobs <= 0 {
		maxJobs = 64
	}
	if maxJobs > 256 {
		maxJobs = 256
	}
	if maxResultBytes <= 0 || maxResultBytes > agentMaxRetainedJobResultBytes {
		maxResultBytes = agentMaxRetainedJobResultBytes
	}
	return &agentJobManager{jobs: make(map[string]*agentJob), maxJobs: maxJobs, maxRecords: maxJobs * 4, maxResultBytes: maxResultBytes}
}

func (m *agentJobManager) start(kind string, work func(context.Context, *agentJobReporter) (any, error)) (string, error) {
	if len(kind) > agentMaxJobKindBytes {
		kind = kind[:agentMaxJobKindBytes]
	}
	m.mu.Lock()
	active := 0
	for _, job := range m.jobs {
		job.mu.Lock()
		if job.status == "queued" || job.status == "running" {
			active++
		}
		job.mu.Unlock()
	}
	if active >= m.maxJobs {
		m.mu.Unlock()
		return "", errors.New("too many active jobs")
	}
	m.pruneLocked()
	if len(m.jobs) >= m.maxRecords {
		m.mu.Unlock()
		return "", errors.New("too many retained job records")
	}
	ctx, cancel := context.WithCancel(context.Background())
	job := &agentJob{id: agentRandomID("job"), kind: kind, status: "queued", cancel: cancel}
	m.jobs[job.id] = job
	m.mu.Unlock()

	go func() {
		defer cancel()
		job.mu.Lock()
		job.status = "running"
		job.startedAt = time.Now()
		job.mu.Unlock()
		result, err := work(ctx, &agentJobReporter{job: job})
		resultBytes := agentRetainedJobResultBytes(result, m.maxResultBytes)
		storedResult := result
		m.mu.Lock()
		if resultBytes > 0 && (resultBytes > m.maxResultBytes || m.retainedResultBytes > m.maxResultBytes-resultBytes) {
			storedResult = nil
			resultBytes = 0
		} else {
			m.retainedResultBytes += resultBytes
		}
		m.mu.Unlock()
		job.mu.Lock()
		defer job.mu.Unlock()
		job.result = storedResult
		job.resultBytes = resultBytes
		job.err = agentBoundedJobError(err)
		job.finishedAt = time.Now()
		switch {
		case errors.Is(ctx.Err(), context.Canceled):
			job.status = "canceled"
		case err != nil:
			job.status = "failed"
		default:
			job.status = "completed"
		}
	}()
	return job.id, nil
}

func (m *agentJobManager) pruneLocked() {
	for len(m.jobs) >= m.maxRecords {
		var oldestID string
		var oldest time.Time
		for id, job := range m.jobs {
			job.mu.Lock()
			finishedAt := job.finishedAt
			terminal := job.status != "queued" && job.status != "running"
			job.mu.Unlock()
			if terminal && (oldestID == "" || finishedAt.Before(oldest)) {
				oldestID = id
				oldest = finishedAt
			}
		}
		if oldestID == "" {
			return
		}
		job := m.jobs[oldestID]
		delete(m.jobs, oldestID)
		job.mu.Lock()
		m.retainedResultBytes -= job.resultBytes
		if m.retainedResultBytes < 0 {
			m.retainedResultBytes = 0
		}
		job.result = nil
		job.resultBytes = 0
		job.mu.Unlock()
	}
}

func agentRetainedJobResultBytes(result any, limit int64) int64 {
	if result == nil || limit <= 0 {
		return 0
	}
	switch value := result.(type) {
	case string:
		if int64(len(value)) > limit {
			return limit + 1
		}
		return int64(len(value))
	case []byte:
		if int64(len(value)) > limit {
			return limit + 1
		}
		return int64(len(value))
	case agentProcessSnapshot:
		return 256
	case agentArchiveResult:
		return 64
	default:
		data, err := json.Marshal(result)
		if err != nil || int64(len(data)) > limit {
			return limit + 1
		}
		return int64(len(data))
	}
}

func agentBoundedJobError(err error) error {
	if err == nil {
		return nil
	}
	message := err.Error()
	if len(message) > agentMaxJobErrorBytes {
		message = message[:agentMaxJobErrorBytes]
	}
	return errors.New(message)
}

func (m *agentJobManager) snapshot(id string) (agentJobSnapshot, bool) {
	m.mu.Lock()
	job, ok := m.jobs[id]
	m.mu.Unlock()
	if !ok {
		return agentJobSnapshot{}, false
	}
	job.mu.Lock()
	defer job.mu.Unlock()
	snapshot := agentJobSnapshot{
		ID:         job.id,
		Kind:       job.kind,
		Status:     job.status,
		Progress:   job.progress,
		Result:     job.result,
		StartedAt:  job.startedAt.UnixMilli(),
		FinishedAt: job.finishedAt.UnixMilli(),
	}
	if job.err != nil {
		snapshot.Error = job.err.Error()
	}
	return snapshot, true
}

func (m *agentJobManager) cancel(id string) bool {
	m.mu.Lock()
	job, ok := m.jobs[id]
	m.mu.Unlock()
	if !ok {
		return false
	}
	job.mu.Lock()
	active := job.status == "queued" || job.status == "running"
	cancel := job.cancel
	job.mu.Unlock()
	if active {
		cancel()
	}
	return true
}
