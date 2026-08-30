//go:build linux || darwin || freebsd

package main

import (
	"io"
	"os/exec"

	"github.com/creack/pty"
)

type agentProcessStreams struct {
	input   io.WriteCloser
	output  io.ReadCloser
	resize  func(int, int) error
	cleanup func() error
}

func agentTerminalSupported() bool {
	return true
}

func agentStartProcess(cmd *exec.Cmd, terminal bool, output io.Writer, stderrOutput ...io.Writer) (agentProcessStreams, error) {
	if terminal {
		ptmx, err := pty.Start(cmd)
		if err != nil {
			return agentProcessStreams{}, err
		}
		return agentProcessStreams{input: ptmx, output: ptmx, resize: func(rows, columns int) error {
			return pty.Setsize(ptmx, &pty.Winsize{Rows: uint16(rows), Cols: uint16(columns)})
		}, cleanup: ptmx.Close}, nil
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
	if err := cmd.Start(); err != nil {
		input.Close()
		return agentProcessStreams{}, err
	}
	return agentProcessStreams{input: input}, nil
}
