//go:build windows

package main

import (
	"errors"
	"fmt"
	"sync"
	"unsafe"

	"golang.org/x/sys/windows"
)

type agentProcessJob struct {
	mu     sync.Mutex
	handle windows.Handle
}

func newAgentProcessJob() (*agentProcessJob, error) {
	handle, err := windows.CreateJobObject(nil, nil)
	if err != nil {
		return nil, fmt.Errorf("create Windows Job Object: %w", err)
	}
	info := windows.JOBOBJECT_EXTENDED_LIMIT_INFORMATION{}
	info.BasicLimitInformation.LimitFlags = windows.JOB_OBJECT_LIMIT_KILL_ON_JOB_CLOSE
	if _, err := windows.SetInformationJobObject(handle, windows.JobObjectExtendedLimitInformation, uintptr(unsafe.Pointer(&info)), uint32(unsafe.Sizeof(info))); err != nil {
		_ = windows.CloseHandle(handle)
		return nil, fmt.Errorf("configure Windows Job Object: %w", err)
	}
	return &agentProcessJob{handle: handle}, nil
}

func (job *agentProcessJob) assignProcess(pid int) error {
	if pid <= 0 {
		return errors.New("process ID is invalid")
	}
	process, err := windows.OpenProcess(windows.PROCESS_SET_QUOTA|windows.PROCESS_TERMINATE|windows.PROCESS_QUERY_LIMITED_INFORMATION, false, uint32(pid))
	if err != nil {
		return fmt.Errorf("open process for Windows Job Object: %w", err)
	}
	defer windows.CloseHandle(process)
	job.mu.Lock()
	defer job.mu.Unlock()
	if job.handle == 0 {
		return errors.New("Windows Job Object is closed")
	}
	if err := windows.AssignProcessToJobObject(job.handle, process); err != nil {
		return fmt.Errorf("assign process to Windows Job Object: %w", err)
	}
	return nil
}

func (job *agentProcessJob) terminate(exitCode uint32) error {
	job.mu.Lock()
	defer job.mu.Unlock()
	if job.handle == 0 {
		return errors.New("Windows Job Object is closed")
	}
	if err := windows.TerminateJobObject(job.handle, exitCode); err != nil {
		return fmt.Errorf("terminate Windows Job Object: %w", err)
	}
	return nil
}

func (job *agentProcessJob) close() error {
	job.mu.Lock()
	defer job.mu.Unlock()
	if job.handle == 0 {
		return nil
	}
	handle := job.handle
	job.handle = 0
	return windows.CloseHandle(handle)
}
