//go:build windows

package main

import (
	"io"
	"os"
	"os/exec"
	"path/filepath"
	"strings"
	"testing"
	"time"

	"golang.org/x/sys/windows"
)

func TestSecureAgentSecretFileUsesPrivateDACL(t *testing.T) {
	path := filepath.Join(t.TempDir(), "secret")
	if err := os.WriteFile(path, []byte("secret"), 0600); err != nil {
		t.Fatal(err)
	}
	if err := secureAgentSecretFile(path); err != nil {
		t.Fatal(err)
	}
	user, err := windows.GetCurrentProcessToken().GetTokenUser()
	if err != nil {
		t.Fatal(err)
	}
	descriptor, err := windows.GetNamedSecurityInfo(path, windows.SE_FILE_OBJECT, windows.DACL_SECURITY_INFORMATION)
	if err != nil {
		t.Fatal(err)
	}
	if want := "(A;;FA;;;" + user.User.Sid.String() + ")"; !strings.Contains(descriptor.String(), want) {
		t.Fatalf("private DACL did not retain only the current user ACE: %s", descriptor.String())
	}
}

func TestSecureAgentSecretDirectoryProtectsChildren(t *testing.T) {
	path := t.TempDir()
	if err := secureAgentSecretDirectory(path); err != nil {
		t.Fatal(err)
	}
	user, err := windows.GetCurrentProcessToken().GetTokenUser()
	if err != nil {
		t.Fatal(err)
	}
	descriptor, err := windows.GetNamedSecurityInfo(path, windows.SE_FILE_OBJECT, windows.DACL_SECURITY_INFORMATION)
	if err != nil {
		t.Fatal(err)
	}
	if want := "(A;OICI;FA;;;" + user.User.Sid.String() + ")"; !strings.Contains(descriptor.String(), want) {
		t.Fatalf("private directory DACL did not protect child objects: %s", descriptor.String())
	}
}

func TestAgentProcessJobTerminatesProcess(t *testing.T) {
	job, err := newAgentProcessJob()
	if err != nil {
		t.Fatal(err)
	}
	command := exec.Command("ping.exe", "-t", "127.0.0.1")
	if err := command.Start(); err != nil {
		_ = job.close()
		t.Fatal(err)
	}
	if err := job.assignProcess(command.Process.Pid); err != nil {
		_ = command.Process.Kill()
		_ = command.Wait()
		_ = job.close()
		t.Fatal(err)
	}
	if err := job.close(); err != nil {
		_ = command.Process.Kill()
		_ = command.Wait()
		t.Fatal(err)
	}
	done := make(chan error, 1)
	go func() {
		done <- command.Wait()
	}()
	select {
	case <-done:
	case <-time.After(5 * time.Second):
		_ = command.Process.Kill()
		<-done
		t.Fatal("job close did not terminate the process promptly")
	}
}

func TestAgentPersistedSecretsUsePrivateDACL(t *testing.T) {
	directory := t.TempDir()
	tokenPath := filepath.Join(directory, "token", "value")
	if _, err := loadAgentToken(tokenPath); err != nil {
		t.Fatal(err)
	}
	credentialPath := filepath.Join(directory, "credential", "value")
	if err := saveAgentCredential(credentialPath, "device-credential-0123456789"); err != nil {
		t.Fatal(err)
	}
	user, err := windows.GetCurrentProcessToken().GetTokenUser()
	if err != nil {
		t.Fatal(err)
	}
	wanted := "(A;;FA;;;" + user.User.Sid.String() + ")"
	for _, path := range []string{tokenPath, credentialPath} {
		descriptor, err := windows.GetNamedSecurityInfo(path, windows.SE_FILE_OBJECT, windows.DACL_SECURITY_INFORMATION)
		if err != nil {
			t.Fatal(err)
		}
		if !strings.Contains(descriptor.String(), wanted) {
			t.Fatalf("persisted secret does not have a private DACL: %s", descriptor.String())
		}
	}
}

func TestAgentStartProcessRetainsWindowsJob(t *testing.T) {
	command := exec.Command("ping.exe", "-t", "127.0.0.1")
	streams, err := agentStartProcess(command, false, io.Discard)
	if err != nil {
		t.Fatal(err)
	}
	if streams.cleanup == nil {
		_ = command.Process.Kill()
		_ = command.Wait()
		t.Fatal("direct process did not retain a Windows Job Object")
	}
	_ = streams.input.Close()
	_ = command.Process.Kill()
	_ = command.Wait()
	if err := streams.cleanup(); err != nil {
		t.Fatal(err)
	}
}
