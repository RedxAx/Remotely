package main

import (
	"context"
	"encoding/json"
	"errors"
	"io"
	"net/http"
	"net/http/httptest"
	"os"
	"path/filepath"
	"strings"
	"testing"
)

func TestAgentGitArgumentsRejectOptionAndTraversalInjection(t *testing.T) {
	requests := []agentGitRequest{
		{Operation: "diff", Paths: []string{"--output=outside"}},
		{Operation: "diff", Paths: []string{"../outside"}},
		{Operation: "push", Remote: "--exec"},
		{Operation: "branch", BranchAction: "switch", Branch: "bad..branch"},
	}
	for _, request := range requests {
		if _, err := agentGitArgs(&request); err == nil {
			t.Fatalf("expected request to be rejected: %#v", request)
		}
	}
}

func TestAgentGitParsersProduceStructuredResults(t *testing.T) {
	status := parseAgentGitStatus([]byte("# branch.head main\x00# branch.ab +2 -1\x001 M. N... 100644 100644 100644 abc abc app.go\x00? new.go\x00"))
	if status.Branch != "main" || status.Ahead != 2 || status.Behind != 1 || len(status.Entries) != 2 || status.Entries[0].Path != "app.go" {
		t.Fatalf("unexpected status: %#v", status)
	}
	commits := parseAgentGitLog([]byte("abc\x1fparent\x1fAda\x1fada@example.test\x1f2026-08-11T00:00:00Z\x1fChange\x1e"))
	if len(commits) != 1 || commits[0].Hash != "abc" || commits[0].Subject != "Change" {
		t.Fatalf("unexpected commits: %#v", commits)
	}
}

func TestAgentSearchStaysInWorkspaceAndBoundsResults(t *testing.T) {
	workspace := t.TempDir()
	if err := os.WriteFile(filepath.Join(workspace, "one.txt"), []byte("needle needle\n"), 0600); err != nil {
		t.Fatal(err)
	}
	if err := os.Mkdir(filepath.Join(workspace, ".git"), 0700); err != nil {
		t.Fatal(err)
	}
	if err := os.WriteFile(filepath.Join(workspace, ".git", "secret"), []byte("needle"), 0600); err != nil {
		t.Fatal(err)
	}
	result, err := runAgentSearch(context.Background(), nil, workspace, agentSearchRequest{Query: "needle"}, 1)
	if err != nil {
		t.Fatal(err)
	}
	if len(result.Matches) != 1 || !result.Truncated || result.Matches[0].Path != "one.txt" {
		t.Fatalf("unexpected search result: %#v", result)
	}
}

func TestAgentLSPParserBoundsAndSequencesMessages(t *testing.T) {
	session := &agentLSPSession{}
	first := json.RawMessage(`{"jsonrpc":"2.0","method":"ready"}`)
	frame := append([]byte("Content-Length: 34\r\n\r\n"), first...)
	remaining := session.parse(frame)
	if len(remaining) != 0 || len(session.messages) != 1 || session.messages[0].Sequence != 1 || !json.Valid(session.messages[0].Message) {
		t.Fatalf("unexpected parsed messages: %#v, remaining %q", session.messages, remaining)
	}
	invalid := session.parse([]byte("Content-Length: 2000000\r\n\r\n"))
	if len(invalid) != 0 {
		t.Fatal("oversized message header was retained")
	}
}

func TestAgentDeveloperBindingRequiresConfiguredArgumentArray(t *testing.T) {
	if _, _, err := parseAgentDeveloperBinding(`build|workspace|.|gradle|["test"]`); err != nil {
		t.Fatal(err)
	}
	if _, _, err := parseAgentDeveloperBinding(`build|workspace|.|gradle|"test"`); err == nil {
		t.Fatal("non-array arguments were accepted")
	}
}

func TestAgentDeveloperFilesUseEntryVersionsForConditionalMutations(t *testing.T) {
	directory := t.TempDir()
	filesystem, err := os.OpenRoot(directory)
	if err != nil {
		t.Fatal(err)
	}
	defer filesystem.Close()
	developer := &agentDeveloperService{service: &AgentService{config: AgentServiceConfig{MaxFileBytes: agentDeveloperFileLimit, MaxArchiveBytes: 8 * 1024 * 1024}}}
	created, err := developer.executeFile(filesystem, ".", agentDeveloperFileRequest{Operation: "create", Path: "src/main.txt", Content: "one"})
	if err != nil {
		t.Fatal(err)
	}
	listing, err := developer.executeFile(filesystem, ".", agentDeveloperFileRequest{Operation: "list", Path: "src"})
	if err != nil || len(listing.Entries) != 1 || listing.Entries[0].Version != created.Version {
		t.Fatalf("entry version does not match create result: %#v %v", listing, err)
	}
	if _, err := developer.executeFile(filesystem, ".", agentDeveloperFileRequest{Operation: "write", Path: "src/main.txt", Content: "two", ExpectedVersion: "stale"}); !errors.Is(err, errAgentVersionConflict) {
		t.Fatalf("expected version conflict, got %v", err)
	}
	written, err := developer.executeFile(filesystem, ".", agentDeveloperFileRequest{Operation: "write", Path: "src/main.txt", Content: "two", ExpectedVersion: created.Version})
	if err != nil {
		t.Fatal(err)
	}
	copied, err := developer.executeFile(filesystem, ".", agentDeveloperFileRequest{Operation: "copy", Path: "src/main.txt", TargetPath: "src/copy.txt", ExpectedVersion: written.Version})
	if err != nil {
		t.Fatal(err)
	}
	trashed, err := developer.executeFile(filesystem, ".", agentDeveloperFileRequest{Operation: "trash", Path: "src/copy.txt", ExpectedVersion: copied.Version})
	if err != nil {
		t.Fatal(err)
	}
	trash, err := developer.executeFile(filesystem, ".", agentDeveloperFileRequest{Operation: "trash-list"})
	if err != nil || len(trash.Trash) != 1 || trash.Trash[0].ID != trashed.TrashID {
		t.Fatalf("unexpected trash listing: %#v %v", trash, err)
	}
	restored, err := developer.executeFile(filesystem, ".", agentDeveloperFileRequest{Operation: "restore", TrashID: trashed.TrashID, TargetPath: "src/restored.txt", ExpectedVersion: trashed.Version})
	if err != nil || restored.Path != "src/restored.txt" {
		t.Fatalf("unexpected restore: %#v %v", restored, err)
	}
	moved, err := developer.executeFile(filesystem, ".", agentDeveloperFileRequest{Operation: "move", Path: "src/main.txt", TargetPath: "src/moved.txt", ExpectedVersion: written.Version})
	if err != nil {
		t.Fatal(err)
	}
	if _, err := developer.executeFile(filesystem, ".", agentDeveloperFileRequest{Operation: "delete", Path: "src/moved.txt", ExpectedVersion: moved.Version}); err != nil {
		t.Fatal(err)
	}
}

func TestAgentGitExtendedOperationsRemainStructured(t *testing.T) {
	stage, err := agentGitArgs(&agentGitRequest{Operation: "stage", Paths: []string{"src/main.go"}})
	if err != nil || strings.Join(stage, " ") != "add -- src/main.go" {
		t.Fatalf("unexpected stage arguments: %q %v", stage, err)
	}
	if _, err := agentGitArgs(&agentGitRequest{Operation: "stage", Paths: []string{"--all"}}); err == nil {
		t.Fatal("stage accepted option injection")
	}
	stash, err := agentGitArgs(&agentGitRequest{Operation: "stash", StashAction: "drop", StashIndex: 3})
	if err != nil || strings.Join(stash, " ") != "stash drop stash@{3}" {
		t.Fatalf("unexpected stash arguments: %q %v", stash, err)
	}
	stableID := strings.Repeat("a", 40)
	apply, err := agentGitArgs(&agentGitRequest{Operation: "stash", StashAction: "apply", StashID: stableID})
	if err != nil || strings.Join(apply, " ") != "stash apply "+stableID {
		t.Fatalf("unexpected stable stash apply: %q %v", apply, err)
	}
	stashes := parseAgentGitStashes([]byte(stableID + "\x1fstash@{2}\x1fWIP on main: change\x1e"))
	if len(stashes) != 1 || stashes[0].ID != stableID || stashes[0].Index != 2 {
		t.Fatalf("unexpected stable stash listing: %#v", stashes)
	}
	files := parseAgentGitFiles([]byte("M\x00main.go\x00R100\x00old.go\x00new.go\x00"))
	if len(files) != 2 || files[1].OriginalPath != "old.go" || files[1].Path != "new.go" {
		t.Fatalf("unexpected commit files: %#v", files)
	}
}

func TestAgentDeveloperWorkspaceRootRejectsSiblingSymlink(t *testing.T) {
	root := t.TempDir()
	workspace := filepath.Join(root, "workspace")
	outside := filepath.Join(root, "outside")
	if err := os.Mkdir(workspace, 0700); err != nil {
		t.Fatal(err)
	}
	if err := os.Mkdir(outside, 0700); err != nil {
		t.Fatal(err)
	}
	if err := os.WriteFile(filepath.Join(outside, "secret.txt"), []byte("secret"), 0600); err != nil {
		t.Fatal(err)
	}
	if err := os.Symlink(filepath.Join("..", "outside"), filepath.Join(workspace, "linked")); err != nil {
		t.Skipf("symlinks are unavailable: %v", err)
	}
	service, err := NewAgentService(AgentServiceConfig{Token: []byte("01234567890123456789012345678901"), Roots: []AgentRoot{{ID: "root", Path: root, Read: true}}})
	if err != nil {
		t.Fatal(err)
	}
	defer service.Close()
	server := httptest.NewServer(service)
	defer server.Close()
	request, err := http.NewRequest(http.MethodPost, server.URL+"/v1/developer/files", strings.NewReader(`{"operation":"read","root":"root","workspace":"workspace","path":"linked/secret.txt"}`))
	if err != nil {
		t.Fatal(err)
	}
	request.Header.Set("Authorization", "Bearer 01234567890123456789012345678901")
	response, err := http.DefaultClient.Do(request)
	if err != nil {
		t.Fatal(err)
	}
	defer response.Body.Close()
	if response.StatusCode == http.StatusOK {
		t.Fatal("workspace symlink escaped into a sibling directory")
	}
}

func TestAgentUploadResumesCompletesAndDownloadsRanges(t *testing.T) {
	root := t.TempDir()
	service, err := NewAgentService(AgentServiceConfig{Token: []byte("01234567890123456789012345678901"), Roots: []AgentRoot{{ID: "root", Path: root, Read: true, Write: true}}})
	if err != nil {
		t.Fatal(err)
	}
	defer service.Close()
	server := httptest.NewServer(service)
	defer server.Close()
	do := func(method, path, body string) *http.Response {
		t.Helper()
		request, err := http.NewRequest(method, server.URL+path, strings.NewReader(body))
		if err != nil {
			t.Fatal(err)
		}
		request.Header.Set("Authorization", "Bearer 01234567890123456789012345678901")
		response, err := http.DefaultClient.Do(request)
		if err != nil {
			t.Fatal(err)
		}
		return response
	}
	start := do(http.MethodPost, "/v1/developer/uploads", `{"root":"root","path":"large.bin","size":5}`)
	var started map[string]any
	if err := json.NewDecoder(start.Body).Decode(&started); err != nil {
		t.Fatal(err)
	}
	_ = start.Body.Close()
	id, _ := started["uploadId"].(string)
	chunk := do(http.MethodPut, "/v1/developer/uploads/"+id+"?offset=0", "large")
	_ = chunk.Body.Close()
	if chunk.StatusCode != http.StatusOK {
		t.Fatalf("unexpected chunk status %d", chunk.StatusCode)
	}
	complete := do(http.MethodPost, "/v1/developer/uploads/"+id+"/complete", "")
	_ = complete.Body.Close()
	if complete.StatusCode != http.StatusOK {
		t.Fatalf("unexpected complete status %d", complete.StatusCode)
	}
	request, _ := http.NewRequest(http.MethodGet, server.URL+"/v1/developer/files/download?root=root&path=large.bin", nil)
	request.Header.Set("Authorization", "Bearer 01234567890123456789012345678901")
	request.Header.Set("Range", "bytes=1-3")
	response, err := http.DefaultClient.Do(request)
	if err != nil {
		t.Fatal(err)
	}
	data, _ := io.ReadAll(response.Body)
	_ = response.Body.Close()
	if response.StatusCode != http.StatusPartialContent || string(data) != "arg" {
		t.Fatalf("unexpected ranged download: %d %q", response.StatusCode, data)
	}
	info := do(http.MethodGet, "/v1/developer/files/download-info?root=root&path=large.bin", "")
	var metadata map[string]any
	if err := json.NewDecoder(info.Body).Decode(&metadata); err != nil {
		t.Fatal(err)
	}
	_ = info.Body.Close()
	version, _ := metadata["version"].(string)
	bounded := do(http.MethodGet, "/v1/developer/files/download?root=root&path=large.bin&version="+version+"&offset=2&limit=2", "")
	data, _ = io.ReadAll(bounded.Body)
	_ = bounded.Body.Close()
	if bounded.StatusCode != http.StatusPartialContent || string(data) != "rg" {
		t.Fatalf("unexpected relay-safe download: %d %q", bounded.StatusCode, data)
	}
}
