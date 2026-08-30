//go:build windows

package main

import (
	"errors"
	"io"
	"os/exec"
)

type agentProcessStreams struct {
	input   io.WriteCloser
	output  io.ReadCloser
	resize  func(int, int) error
	cleanup func() error
}

func agentTerminalSupported() bool {
	return false
}

func agentStartProcess(cmd *exec.Cmd, terminal bool, output io.Writer, stderrOutput ...io.Writer) (agentProcessStreams, error) {
	if terminal {
		return agentProcessStreams{}, errors.New("terminal processes are not supported on Windows")
	}
	stderr := output
	if len(stderrOutput) > 0 && stderrOutput[0] != nil {
		stderr = stderrOutput[0]
	}
	input, err := cmd.StdinPipe()
	if err != nil {
		return agentProcessStreams{}, err
	}
	cmd.Stdout = output
	cmd.Stderr = stderr
	job, err := newAgentProcessJob()
	if err != nil {
		input.Close()
		return agentProcessStreams{}, err
	}
	if err := cmd.Start(); err != nil {
		input.Close()
		_ = job.close()
		return agentProcessStreams{}, err
	}
	if err := job.assignProcess(cmd.Process.Pid); err != nil {
		input.Close()
		_ = cmd.Process.Kill()
		_ = cmd.Wait()
		_ = job.close()
		return agentProcessStreams{}, err
	}
	return agentProcessStreams{input: input, cleanup: job.close}, nil
}
