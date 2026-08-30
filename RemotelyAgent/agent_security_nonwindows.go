//go:build !windows

package main

func secureAgentSecretFile(path string) error {
	return nil
}

func secureAgentSecretDirectory(path string) error {
	return nil
}
