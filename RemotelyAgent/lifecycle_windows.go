//go:build windows

package main

import (
	"bufio"
	"crypto/sha1"
	"crypto/subtle"
	"encoding/base64"
	"encoding/hex"
	"encoding/json"
	"errors"
	"flag"
	"fmt"
	"io"
	"net"
	"os"
	"os/exec"
	"path/filepath"
	"strconv"
	"strings"
	"sync"
	"time"
)

type lifecyclePaths struct {
	RunDir        string
	SocketDir     string
	SocketPath    string
	LogPath       string
	StatusPath    string
	SupervisorPid string
	ServerPid     string
	ExitCode      string
	TokenPath     string
	ControlPath   string
}

type lifecycleStatus struct {
	Status        string `json:"status"`
	Running       bool   `json:"running"`
	SupervisorPid string `json:"supervisorPid"`
	ServerPid     string `json:"serverPid"`
	ExitCode      string `json:"exitCode"`
	SocketPath    string `json:"socketPath"`
}

func cmdLifecycleStart(args []string) {
	fs := flag.NewFlagSet("lifecycle-start", flag.ExitOnError)
	dir := fs.String("dir", "", "Instance directory")
	command := fs.String("command", "", "Startup command")
	_ = fs.Parse(args)
	if *dir == "" || *command == "" {
		fmt.Fprintln(os.Stderr, "lifecycle-start: --dir and --command are required")
		os.Exit(2)
	}
	if err := lifecycleStart(*dir, *command); err != nil {
		fmt.Fprintln(os.Stderr, err)
		os.Exit(1)
	}
}

func cmdLifecycleStop(args []string) {
	fs := flag.NewFlagSet("lifecycle-stop", flag.ExitOnError)
	dir := fs.String("dir", "", "Instance directory")
	timeout := fs.Int("timeout", 180, "Grace timeout seconds")
	_ = fs.Parse(args)
	if *dir == "" {
		fmt.Fprintln(os.Stderr, "lifecycle-stop: --dir is required")
		os.Exit(2)
	}
	if err := lifecycleStop(*dir, time.Duration(*timeout)*time.Second); err != nil {
		fmt.Fprintln(os.Stderr, err)
		os.Exit(1)
	}
}

func cmdLifecycleSend(args []string) {
	fs := flag.NewFlagSet("lifecycle-send", flag.ExitOnError)
	dir := fs.String("dir", "", "Instance directory")
	command := fs.String("command", "", "Command")
	_ = fs.Parse(args)
	if *dir == "" || *command == "" {
		fmt.Fprintln(os.Stderr, "lifecycle-send: --dir and --command are required")
		os.Exit(2)
	}
	if err := lifecycleSend(*dir, *command); err != nil {
		fmt.Fprintln(os.Stderr, err)
		os.Exit(1)
	}
}

func cmdLifecycleStatus(args []string) {
	fs := flag.NewFlagSet("lifecycle-status", flag.ExitOnError)
	dir := fs.String("dir", "", "Instance directory")
	_ = fs.Parse(args)
	if *dir == "" {
		fmt.Fprintln(os.Stderr, "lifecycle-status: --dir is required")
		os.Exit(2)
	}
	status := lifecycleReadStatus(*dir)
	data, _ := json.Marshal(status)
	fmt.Println(string(data))
}

func cmdLifecycleConsole(args []string) {
	fs := flag.NewFlagSet("lifecycle-console", flag.ExitOnError)
	dir := fs.String("dir", "", "Instance directory")
	_ = fs.Parse(args)
	if *dir == "" {
		fmt.Fprintln(os.Stderr, "lifecycle-console: --dir is required")
		os.Exit(2)
	}
	if err := lifecycleConsole(*dir); err != nil {
		fmt.Fprintln(os.Stderr, err)
		os.Exit(1)
	}
}

func cmdLifecycleSupervise(args []string) {
	fs := flag.NewFlagSet("lifecycle-supervise", flag.ExitOnError)
	dir := fs.String("dir", "", "Instance directory")
	command := fs.String("command", "", "Startup command")
	_ = fs.Parse(args)
	if *dir == "" || *command == "" {
		os.Exit(2)
	}
	os.Exit(lifecycleSupervise(*dir, *command))
}

func lifecycleStart(dir, command string) error {
	paths, err := lifecycleResolvePaths(dir)
	if err != nil {
		return err
	}
	if lifecycleSocketReady(paths.SocketPath, 300*time.Millisecond) {
		fmt.Println("RUNNING")
		return nil
	}
	if err := os.MkdirAll(paths.RunDir, 0755); err != nil {
		return err
	}
	if err := os.MkdirAll(paths.SocketDir, 0700); err != nil {
		return err
	}
	if err := secureAgentSecretDirectory(paths.RunDir); err != nil {
		return err
	}
	if err := secureAgentSecretDirectory(paths.SocketDir); err != nil {
		return err
	}
	token, err := loadAgentToken(paths.TokenPath)
	if err != nil {
		return err
	}
	if err := secureAgentSecretFile(paths.TokenPath); err != nil {
		return err
	}
	if err := writeLifecycleSecret(paths.SocketPath+".token", token); err != nil {
		return err
	}
	_ = os.Remove(paths.ExitCode)
	_ = os.WriteFile(paths.LogPath, nil, 0644)
	_ = os.WriteFile(paths.StatusPath, []byte("starting"), 0644)
	executable, err := os.Executable()
	if err != nil {
		return err
	}
	child := exec.Command(executable, "lifecycle-supervise", "--dir", dir, "--command", command)
	child.Stdout = nil
	child.Stderr = nil
	child.Stdin = nil
	if err := child.Start(); err != nil {
		return err
	}
	_ = os.WriteFile(paths.SupervisorPid, []byte(strconv.Itoa(child.Process.Pid)), 0644)
	_ = child.Process.Release()
	deadline := time.Now().Add(10 * time.Second)
	for time.Now().Before(deadline) {
		if lifecycleSocketReady(paths.SocketPath, 200*time.Millisecond) {
			fmt.Println("STARTED")
			return nil
		}
		if code := readTrim(paths.ExitCode); code != "" {
			_ = os.WriteFile(paths.StatusPath, []byte("crashed"), 0644)
			return fmt.Errorf("START_FAILED exit=%s", code)
		}
		time.Sleep(100 * time.Millisecond)
	}
	_ = os.WriteFile(paths.StatusPath, []byte("crashed"), 0644)
	return errors.New("START_FAILED socket timeout")
}

func lifecycleStop(dir string, timeout time.Duration) error {
	paths, err := lifecycleResolvePaths(dir)
	if err != nil {
		return err
	}
	if !lifecycleSocketReady(paths.SocketPath, 300*time.Millisecond) {
		fmt.Println("STOPPED")
		return nil
	}
	if _, err := lifecycleControl(paths.SocketPath, "STOP\n", 2*time.Second); err != nil {
		return err
	}
	deadline := time.Now().Add(timeout)
	for time.Now().Before(deadline) {
		if !lifecycleSocketReady(paths.SocketPath, 200*time.Millisecond) {
			fmt.Println("STOPPED")
			return nil
		}
		time.Sleep(time.Second)
	}
	_, _ = lifecycleControl(paths.SocketPath, "KILL\n", 2*time.Second)
	killDeadline := time.Now().Add(5 * time.Second)
	for time.Now().Before(killDeadline) {
		if !lifecycleSocketReady(paths.SocketPath, 200*time.Millisecond) {
			break
		}
		time.Sleep(100 * time.Millisecond)
	}
	fmt.Println("KILLED")
	return nil
}

func lifecycleSend(dir, command string) error {
	paths, err := lifecycleResolvePaths(dir)
	if err != nil {
		return err
	}
	payload := base64.StdEncoding.EncodeToString([]byte(command))
	out, err := lifecycleControl(paths.SocketPath, "SEND "+payload+"\n", 3*time.Second)
	if err != nil {
		return err
	}
	fmt.Println(strings.TrimSpace(out))
	return nil
}

func lifecycleReadStatus(dir string) lifecycleStatus {
	paths, err := lifecycleResolvePaths(dir)
	if err != nil {
		return lifecycleStatus{Status: "stopped", Running: false}
	}
	running := lifecycleSocketReady(paths.SocketPath, 100*time.Millisecond)
	status := readTrim(paths.StatusPath)
	if status == "" {
		status = "stopped"
	}
	if !running && (status == "starting" || status == "running" || status == "stopping") {
		status = "stopped"
	}
	return lifecycleStatus{Status: status, Running: running, SupervisorPid: readTrim(paths.SupervisorPid), ServerPid: readTrim(paths.ServerPid), ExitCode: readTrim(paths.ExitCode), SocketPath: paths.SocketPath}
}

func lifecycleConsole(dir string) error {
	paths, err := lifecycleResolvePaths(dir)
	if err != nil {
		return err
	}
	var offset int64
	for {
		if data, readErr := os.ReadFile(paths.LogPath); readErr == nil && int64(len(data)) > offset {
			_, _ = os.Stdout.Write(data[offset:])
			offset = int64(len(data))
		}
		if !lifecycleSocketReady(paths.SocketPath, 100*time.Millisecond) {
			fmt.Print("\r\nServer Not Running\r\nWaiting For Start\r\n")
			time.Sleep(500 * time.Millisecond)
			continue
		}
		reader := bufio.NewReader(os.Stdin)
		for {
			line, readErr := reader.ReadString('\n')
			if len(line) > 0 {
				if sendErr := lifecycleSend(dir, strings.TrimSpace(line)); sendErr != nil {
					break
				}
			}
			if readErr != nil {
				break
			}
		}
		time.Sleep(500 * time.Millisecond)
	}
}

func lifecycleSupervise(dir, command string) int {
	paths, err := lifecycleResolvePaths(dir)
	if err != nil {
		return 2
	}
	_ = os.MkdirAll(paths.RunDir, 0755)
	_ = os.MkdirAll(paths.SocketDir, 0700)
	if err := secureAgentSecretDirectory(paths.RunDir); err != nil {
		return 1
	}
	if err := secureAgentSecretDirectory(paths.SocketDir); err != nil {
		return 1
	}
	_ = os.Remove(paths.SocketPath)
	token, err := loadAgentToken(paths.TokenPath)
	if err != nil {
		return 1
	}
	if err := secureAgentSecretFile(paths.TokenPath); err != nil {
		return 1
	}
	if err := writeLifecycleSecret(paths.SocketPath+".token", token); err != nil {
		return 1
	}
	logFile, err := os.OpenFile(paths.LogPath, os.O_CREATE|os.O_WRONLY|os.O_APPEND, 0644)
	if err != nil {
		return 1
	}
	defer logFile.Close()
	listener, err := net.Listen("tcp", "127.0.0.1:0")
	if err != nil {
		return 1
	}
	defer listener.Close()
	_ = os.WriteFile(paths.SocketPath, []byte(listener.Addr().String()), 0644)

	cmd := exec.Command("cmd.exe", "/D", "/S", "/C", command)
	cmd.Dir = dir
	cmd.Stdout = logFile
	cmd.Stderr = logFile
	stdin, err := cmd.StdinPipe()
	if err != nil {
		return 1
	}
	if err := cmd.Start(); err != nil {
		_, _ = fmt.Fprintf(logFile, "failed to start: %v\n", err)
		return 1
	}
	job, err := newAgentProcessJob()
	if err != nil {
		_, _ = fmt.Fprintf(logFile, "failed to create process job: %v\n", err)
		_ = cmd.Process.Kill()
		_ = cmd.Wait()
		return 1
	}
	defer job.close()
	if err := job.assignProcess(cmd.Process.Pid); err != nil {
		_, _ = fmt.Fprintf(logFile, "failed to attach process job: %v\n", err)
		_ = cmd.Process.Kill()
		_ = cmd.Wait()
		return 1
	}
	_ = os.WriteFile(paths.ServerPid, []byte(strconv.Itoa(cmd.Process.Pid)), 0644)
	_ = os.WriteFile(paths.StatusPath, []byte("running"), 0644)
	var clientsMu sync.Mutex
	var acceptWG sync.WaitGroup
	var clientsWG sync.WaitGroup
	stopping := false
	acceptWG.Add(1)
	go func() {
		defer acceptWG.Done()
		for {
			conn, acceptErr := listener.Accept()
			if acceptErr != nil {
				return
			}
			clientsWG.Add(1)
			go func() {
				defer clientsWG.Done()
				lifecycleHandleWindowsClient(conn, stdin, token, &clientsMu, &stopping, paths, job)
			}()
		}
	}()
	waitErr := cmd.Wait()
	_ = stdin.Close()
	_ = listener.Close()
	acceptWG.Wait()
	clientsWG.Wait()
	_ = os.Remove(paths.SocketPath)
	exitCode := 0
	if waitErr != nil {
		if exitErr, ok := waitErr.(*exec.ExitError); ok {
			exitCode = exitErr.ExitCode()
		} else {
			exitCode = 1
		}
	}
	_ = os.WriteFile(paths.ExitCode, []byte(strconv.Itoa(exitCode)), 0644)
	clientsMu.Lock()
	wasStopping := stopping
	clientsMu.Unlock()
	if exitCode == 0 || wasStopping {
		_ = os.WriteFile(paths.StatusPath, []byte("stopped"), 0644)
	} else {
		_ = os.WriteFile(paths.StatusPath, []byte("crashed"), 0644)
	}
	return exitCode
}

func lifecycleHandleWindowsClient(conn net.Conn, stdin io.Writer, token []byte, clientsMu *sync.Mutex, stopping *bool, paths lifecyclePaths, job *agentProcessJob) {
	defer conn.Close()
	_ = conn.SetDeadline(time.Now().Add(5 * time.Second))
	reader := bufio.NewReader(io.LimitReader(conn, 128*1024))
	line, err := reader.ReadString('\n')
	if err != nil {
		return
	}
	trimmed := strings.TrimSpace(line)
	if !strings.HasPrefix(trimmed, "AUTH ") {
		return
	}
	remainder := strings.TrimPrefix(trimmed, "AUTH ")
	separator := strings.IndexByte(remainder, ' ')
	if separator <= 0 || subtle.ConstantTimeCompare([]byte(remainder[:separator]), token) != 1 {
		return
	}
	command := strings.TrimSpace(remainder[separator+1:])
	switch {
	case command == "STOP":
		clientsMu.Lock()
		*stopping = true
		_, writeErr := io.WriteString(stdin, lifecycleStopInput)
		clientsMu.Unlock()
		if writeErr != nil {
			_, _ = io.WriteString(conn, "ERR\n")
			return
		}
		_, _ = io.WriteString(conn, "OK\n")
	case command == "KILL":
		clientsMu.Lock()
		*stopping = true
		clientsMu.Unlock()
		if err := job.terminate(1); err != nil {
			_, _ = io.WriteString(conn, "ERR\n")
			return
		}
		_, _ = io.WriteString(conn, "OK\n")
	case strings.HasPrefix(command, "SEND "):
		payload, decodeErr := base64.StdEncoding.DecodeString(strings.TrimPrefix(command, "SEND "))
		if decodeErr != nil || len(payload) > 64*1024 {
			_, _ = io.WriteString(conn, "ERR\n")
			return
		}
		clientsMu.Lock()
		_, writeErr := stdin.Write(append(payload, '\n'))
		clientsMu.Unlock()
		if writeErr != nil {
			_, _ = io.WriteString(conn, "ERR\n")
			return
		}
		_, _ = io.WriteString(conn, "OK\n")
	case command == "STATUS":
		_, _ = io.WriteString(conn, readTrim(paths.StatusPath)+"\n")
	default:
		_, _ = io.WriteString(conn, "ERR\n")
	}
}

func lifecycleControl(sock, command string, timeout time.Duration) (string, error) {
	addr, err := readLifecycleControlFile(sock, 256)
	if err != nil {
		return "", err
	}
	address := strings.TrimSpace(string(addr))
	if err := loopbackAgentAddress(address); err != nil {
		return "", err
	}
	if err := secureAgentSecretFile(sock + ".token"); err != nil {
		return "", err
	}
	token, present, err := readAgentSecret(sock+".token", 16, 4096)
	if err != nil {
		return "", err
	}
	if !present {
		return "", errors.New("lifecycle control token is missing")
	}
	conn, err := net.DialTimeout("tcp", address, timeout)
	if err != nil {
		return "", err
	}
	defer conn.Close()
	_ = conn.SetDeadline(time.Now().Add(timeout))
	if _, err := conn.Write([]byte("AUTH " + token + " " + command)); err != nil {
		return "", err
	}
	data, err := io.ReadAll(conn)
	if err != nil {
		return "", err
	}
	return string(data), nil
}

func lifecycleSocketReady(sock string, timeout time.Duration) bool {
	status, err := lifecycleControl(sock, "STATUS\n", timeout)
	if err != nil {
		return false
	}
	switch strings.TrimSpace(status) {
	case "starting", "running", "stopping", "stopped", "crashed":
		return true
	default:
		return false
	}
}

func readLifecycleControlFile(path string, maximum int64) ([]byte, error) {
	file, err := os.Open(path)
	if err != nil {
		return nil, err
	}
	defer file.Close()
	data, err := io.ReadAll(io.LimitReader(file, maximum+1))
	if err != nil {
		return nil, err
	}
	if int64(len(data)) > maximum {
		return nil, errors.New("lifecycle control file exceeds the supported limit")
	}
	return data, nil
}

func writeLifecycleSecret(path string, data []byte) error {
	if info, err := os.Lstat(path); err == nil {
		if !info.Mode().IsRegular() {
			return errors.New("lifecycle secret path must be a regular file")
		}
	} else if !errors.Is(err, os.ErrNotExist) {
		return err
	}
	if err := os.WriteFile(path, data, 0600); err != nil {
		return err
	}
	return secureAgentSecretFile(path)
}

func lifecycleResolvePaths(dir string) (lifecyclePaths, error) {
	abs, err := filepath.Abs(dir)
	if err != nil {
		return lifecyclePaths{}, err
	}
	runDir := filepath.Join(abs, ".remotely", "run")
	home, err := os.UserHomeDir()
	if err != nil || home == "" {
		home = os.TempDir()
	}
	sum := sha1.Sum([]byte(abs))
	socketDir := filepath.Join(home, ".remotely", "run", "sockets")
	socketPath := filepath.Join(socketDir, hex.EncodeToString(sum[:])[:24]+".addr")
	return lifecyclePaths{RunDir: runDir, SocketDir: socketDir, SocketPath: socketPath, LogPath: filepath.Join(runDir, "console.log"), StatusPath: filepath.Join(runDir, "status"), SupervisorPid: filepath.Join(runDir, "supervisor.pid"), ServerPid: filepath.Join(runDir, "server.pid"), ExitCode: filepath.Join(runDir, "exit.code"), TokenPath: filepath.Join(runDir, "control.token"), ControlPath: socketPath}, nil
}

func readTrim(path string) string {
	data, err := os.ReadFile(path)
	if err != nil {
		return ""
	}
	return strings.TrimSpace(string(data))
}
