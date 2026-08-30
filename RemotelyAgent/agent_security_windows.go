//go:build windows

package main

import (
	"errors"
	"fmt"
	"os"
	"path/filepath"

	"golang.org/x/sys/windows"
)

func secureAgentSecretFile(path string) error {
	return secureAgentSecretPath(path, false)
}

func secureAgentSecretDirectory(path string) error {
	return secureAgentSecretPath(path, true)
}

func secureAgentSecretPath(path string, directory bool) error {
	if path == "" {
		return errors.New("secret path is required")
	}
	absolute, err := filepath.Abs(path)
	if err != nil {
		return err
	}
	info, err := os.Lstat(absolute)
	if err != nil {
		return err
	}
	if directory {
		if !info.IsDir() {
			return fmt.Errorf("secret path is not a directory: %s", absolute)
		}
	} else if !info.Mode().IsRegular() {
		return fmt.Errorf("secret path is not a regular file: %s", absolute)
	}

	user, err := windows.GetCurrentProcessToken().GetTokenUser()
	if err != nil {
		return fmt.Errorf("resolve current Windows user: %w", err)
	}
	sid := user.User.Sid.String()
	acl := "D:P(A;;FA;;;" + sid + ")"
	if directory {
		acl = "D:P(A;OICI;FA;;;" + sid + ")"
	}
	descriptor, err := windows.SecurityDescriptorFromString(acl)
	if err != nil {
		return fmt.Errorf("create private security descriptor: %w", err)
	}
	dacl, _, err := descriptor.DACL()
	if err != nil {
		return fmt.Errorf("read private security descriptor DACL: %w", err)
	}

	flags := uint32(windows.FILE_FLAG_OPEN_REPARSE_POINT)
	if directory {
		flags |= windows.FILE_FLAG_BACKUP_SEMANTICS
	}
	handle, err := windows.CreateFile(
		windows.StringToUTF16Ptr(absolute),
		windows.READ_CONTROL|windows.WRITE_DAC,
		windows.FILE_SHARE_READ|windows.FILE_SHARE_WRITE|windows.FILE_SHARE_DELETE,
		nil,
		windows.OPEN_EXISTING,
		flags,
		0,
	)
	if err != nil {
		return fmt.Errorf("open secret path for ACL: %w", err)
	}
	defer windows.CloseHandle(handle)

	var handleInfo windows.ByHandleFileInformation
	if err := windows.GetFileInformationByHandle(handle, &handleInfo); err != nil {
		return fmt.Errorf("inspect secret path: %w", err)
	}
	if handleInfo.FileAttributes&windows.FILE_ATTRIBUTE_REPARSE_POINT != 0 {
		return errors.New("secret path must not be a reparse point")
	}
	return windows.SetSecurityInfo(
		handle,
		windows.SE_FILE_OBJECT,
		windows.DACL_SECURITY_INFORMATION|windows.PROTECTED_DACL_SECURITY_INFORMATION,
		nil,
		nil,
		dacl,
		nil,
	)
}
