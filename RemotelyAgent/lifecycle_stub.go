//go:build !linux && !darwin && !freebsd && !windows

package main

import (
	"fmt"
	"os"
)

func lifecycleUnsupported(name string) {
	fmt.Fprintf(os.Stderr, "%s is not supported on this platform\n", name)
	os.Exit(1)
}

func cmdLifecycleStart(args []string) {
	lifecycleUnsupported("lifecycle-start")
}

func cmdLifecycleStop(args []string) {
	lifecycleUnsupported("lifecycle-stop")
}

func cmdLifecycleSend(args []string) {
	lifecycleUnsupported("lifecycle-send")
}

func cmdLifecycleStatus(args []string) {
	lifecycleUnsupported("lifecycle-status")
}

func cmdLifecycleConsole(args []string) {
	lifecycleUnsupported("lifecycle-console")
}

func cmdLifecycleSupervise(args []string) {
	lifecycleUnsupported("lifecycle-supervise")
}
