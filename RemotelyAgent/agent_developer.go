package main

import (
	"bufio"
	"bytes"
	"context"
	"crypto/sha256"
	"encoding/json"
	"errors"
	"fmt"
	"io"
	"io/fs"
	"mime/multipart"
	"net/http"
	"os"
	"os/exec"
	"path/filepath"
	"regexp"
	"sort"
	"strconv"
	"strings"
	"sync"
	"time"
)

const (
	agentDeveloperBodyLimit   = 2 * 1024 * 1024
	agentDeveloperFileLimit   = 1024 * 1024
	agentDeveloperOutputLimit = 8 * 1024 * 1024
	agentSearchFileLimit      = 4 * 1024 * 1024
	agentSearchResultLimit    = 5000
	agentLSPSessionLimit      = 8
	agentLSPMessageLimit      = 1024 * 1024
	agentLSPRetainedMessages  = 256
	agentUploadChunkLimit     = 1024 * 1024
	agentUploadSessionLimit   = 32
	agentUploadLifetime       = 24 * time.Hour
)

type agentDeveloperCapabilities struct {
	Git            bool                   `json:"git"`
	GitOperations  []string               `json:"gitOperations"`
	Files          bool                   `json:"files"`
	FileOperations []string               `json:"fileOperations"`
	Transfers      bool                   `json:"transfers"`
	Search         bool                   `json:"search"`
	Workflows      []AgentWorkflowBinding `json:"workflows"`
	LSP            []AgentLSPBinding      `json:"lsp"`
}

type agentDeveloperService struct {
	service   *AgentService
	git       string
	gitConfig []string
	workflows map[string]agentResolvedBinding
	lsp       map[string]agentResolvedBinding
	mu        sync.Mutex
	fileMu    sync.Mutex
	sessions  map[string]*agentLSPSession
	uploads   map[string]*agentUploadSession
}

type agentResolvedBinding struct {
	id, root, directory, program string
	args                         []string
}

type agentGitRequest struct {
	Operation        string   `json:"operation"`
	Root             string   `json:"root"`
	Workspace        string   `json:"workspace"`
	Paths            []string `json:"paths"`
	Staged           bool     `json:"staged"`
	Base             string   `json:"base"`
	Head             string   `json:"head"`
	Limit            int      `json:"limit"`
	BranchAction     string   `json:"branchAction"`
	Branch           string   `json:"branch"`
	Message          string   `json:"message"`
	Remote           string   `json:"remote"`
	SetUpstream      bool     `json:"setUpstream"`
	Rebase           bool     `json:"rebase"`
	StashAction      string   `json:"stashAction"`
	StashIndex       int      `json:"stashIndex"`
	StashID          string   `json:"stashId"`
	IncludeUntracked bool     `json:"includeUntracked"`
}

type agentGitResult struct {
	Operation string           `json:"operation"`
	ExitCode  int              `json:"exitCode"`
	Status    *agentGitStatus  `json:"status,omitempty"`
	Diff      string           `json:"diff,omitempty"`
	Commits   []agentGitCommit `json:"commits,omitempty"`
	Branches  []agentGitBranch `json:"branches,omitempty"`
	Stashes   []agentGitStash  `json:"stashes,omitempty"`
	Files     []agentGitFile   `json:"files,omitempty"`
	Stdout    string           `json:"stdout,omitempty"`
	Stderr    string           `json:"stderr,omitempty"`
	Truncated bool             `json:"truncated"`
}

type agentGitStatus struct {
	Branch  string          `json:"branch"`
	Ahead   int             `json:"ahead"`
	Behind  int             `json:"behind"`
	Entries []agentGitEntry `json:"entries"`
}

type agentGitEntry struct {
	Path         string `json:"path"`
	OriginalPath string `json:"originalPath,omitempty"`
	Index        string `json:"index"`
	Worktree     string `json:"worktree"`
}
type agentGitCommit struct {
	Hash        string   `json:"hash"`
	Parents     []string `json:"parents"`
	AuthorName  string   `json:"authorName"`
	AuthorEmail string   `json:"authorEmail"`
	AuthoredAt  string   `json:"authoredAt"`
	Subject     string   `json:"subject"`
}
type agentGitBranch struct {
	Name     string `json:"name"`
	Current  bool   `json:"current"`
	Upstream string `json:"upstream,omitempty"`
}

type agentGitStash struct {
	ID      string `json:"id"`
	Index   int    `json:"index"`
	Ref     string `json:"ref"`
	Branch  string `json:"branch"`
	Message string `json:"message"`
}
type agentGitFile struct {
	Status       string `json:"status"`
	Path         string `json:"path"`
	OriginalPath string `json:"originalPath,omitempty"`
}

type agentDeveloperFileRequest struct {
	Operation       string `json:"operation"`
	Root            string `json:"root"`
	Workspace       string `json:"workspace"`
	Path            string `json:"path"`
	TargetPath      string `json:"targetPath"`
	Content         string `json:"content"`
	Directory       bool   `json:"directory"`
	Recursive       bool   `json:"recursive"`
	Overwrite       bool   `json:"overwrite"`
	ExpectedVersion string `json:"expectedVersion"`
	TargetVersion   string `json:"targetVersion"`
	TrashID         string `json:"trashId"`
}

type agentUploadStartRequest struct {
	Root            string `json:"root"`
	Workspace       string `json:"workspace"`
	Path            string `json:"path"`
	Size            int64  `json:"size"`
	ExpectedVersion string `json:"expectedVersion"`
	Overwrite       bool   `json:"overwrite"`
}
type agentUploadSession struct {
	mu                                                    sync.Mutex
	id, root, workspace, path, temporary, expectedVersion string
	size, offset                                          int64
	overwrite                                             bool
	expires                                               time.Time
	filesystem                                            *os.Root
}
type agentTrashManifest struct {
	ID           string `json:"id"`
	OriginalPath string `json:"originalPath"`
	TrashedAt    int64  `json:"trashedAt"`
	Version      string `json:"version"`
}
type agentDeveloperFileEntry struct {
	Name       string `json:"name"`
	Path       string `json:"path"`
	Directory  bool   `json:"directory"`
	Size       int64  `json:"size"`
	ModifiedAt int64  `json:"modifiedAt"`
	Version    string `json:"version"`
}
type agentDeveloperFileResult struct {
	Path    string                    `json:"path,omitempty"`
	Content string                    `json:"content,omitempty"`
	Size    int64                     `json:"size,omitempty"`
	Version string                    `json:"version,omitempty"`
	Entries []agentDeveloperFileEntry `json:"entries,omitempty"`
	TrashID string                    `json:"trashId,omitempty"`
	Trash   []agentTrashManifest      `json:"trash,omitempty"`
}

type agentSearchRequest struct {
	Root          string `json:"root"`
	Workspace     string `json:"workspace"`
	Query         string `json:"query"`
	Glob          string `json:"glob"`
	CaseSensitive bool   `json:"caseSensitive"`
	Regex         bool   `json:"regex"`
	MaxResults    int    `json:"maxResults"`
}
type agentSearchResult struct {
	Matches   []agentSearchMatch `json:"matches"`
	Truncated bool               `json:"truncated"`
}
type agentSearchMatch struct {
	Path    string `json:"path"`
	Line    int    `json:"line"`
	Column  int    `json:"column"`
	Preview string `json:"preview"`
}
type agentWorkflowRequest struct {
	Workflow string `json:"workflow"`
}
type agentWorkflowResult struct {
	ExitCode  int    `json:"exitCode"`
	Stdout    string `json:"stdout"`
	Stderr    string `json:"stderr"`
	Truncated bool   `json:"truncated"`
}
type agentLSPStartRequest struct {
	Binding   string `json:"binding"`
	Workspace string `json:"workspace"`
}
type agentLSPMessage struct {
	Sequence int64           `json:"sequence"`
	Message  json.RawMessage `json:"message"`
}

type agentLSPSession struct {
	mu       sync.Mutex
	id       string
	owner    *agentDeveloperService
	process  *agentProcess
	output   *agentProcessOutput
	manager  *agentProcessManager
	messages []agentLSPMessage
	next     int64
	closed   chan struct{}
}

func newAgentDeveloperService(service *AgentService, workflows []AgentWorkflowBinding, lsp []AgentLSPBinding) (*agentDeveloperService, error) {
	d := &agentDeveloperService{service: service, workflows: make(map[string]agentResolvedBinding), lsp: make(map[string]agentResolvedBinding), sessions: make(map[string]*agentLSPSession), uploads: make(map[string]*agentUploadSession)}
	d.git, _ = exec.LookPath("git")
	if d.git != "" {
		d.gitConfig = []string{"-c", "core.hooksPath=", "-c", "core.sshCommand=ssh", "-c", "protocol.ext.allow=never", "-c", "diff.external=", "-c", "credential.helper="}
		command := exec.Command(d.git, "config", "--global", "--get-all", "credential.helper")
		if output, err := command.Output(); err == nil && len(output) <= 64*1024 {
			for _, helper := range strings.Split(strings.TrimSpace(string(output)), "\n") {
				if helper != "" && len(helper) <= 1024 && strings.IndexByte(helper, 0) < 0 {
					d.gitConfig = append(d.gitConfig, "-c", "credential.helper="+helper)
				}
			}
		}
	}
	for _, binding := range workflows {
		resolved, err := d.resolveBinding(binding.ID, binding.Root, binding.Directory, binding.Tool, binding.Args, true)
		if err != nil {
			return nil, fmt.Errorf("workflow %q: %w", binding.ID, err)
		}
		if _, ok := d.workflows[binding.ID]; ok {
			return nil, fmt.Errorf("duplicate workflow %q", binding.ID)
		}
		d.workflows[binding.ID] = resolved
	}
	for _, binding := range lsp {
		resolved, err := d.resolveBinding(binding.ID, binding.Root, binding.Directory, binding.Program, binding.Args, false)
		if err != nil {
			return nil, fmt.Errorf("lsp %q: %w", binding.ID, err)
		}
		if _, ok := d.lsp[binding.ID]; ok {
			return nil, fmt.Errorf("duplicate lsp %q", binding.ID)
		}
		d.lsp[binding.ID] = resolved
	}
	return d, nil
}

func (d *agentDeveloperService) resolveBinding(id, rootID, directory, program string, args []string, workflow bool) (agentResolvedBinding, error) {
	if !validAgentID(id) {
		return agentResolvedBinding{}, errors.New("invalid binding id")
	}
	root, ok := d.service.roots[rootID]
	if !ok || !root.Read || !root.Execute {
		return agentResolvedBinding{}, errors.New("binding requires an executable root")
	}
	working, err := d.workspace(root, directory)
	if err != nil {
		return agentResolvedBinding{}, err
	}
	if workflow {
		switch strings.ToLower(program) {
		case "git", "gradle", "java", "gradlew", "gradlew.bat":
		default:
			return agentResolvedBinding{}, errors.New("unsupported workflow tool")
		}
	}
	resolved := ""
	if strings.EqualFold(program, "gradlew") || strings.EqualFold(program, "gradlew.bat") {
		resolved, err = resolveAgentExecutable(root, filepath.Join(directory, program))
	} else {
		resolved, err = exec.LookPath(program)
	}
	if err != nil {
		return agentResolvedBinding{}, err
	}
	for _, arg := range args {
		if len(arg) > 4096 || strings.IndexByte(arg, 0) >= 0 {
			return agentResolvedBinding{}, errors.New("invalid configured argument")
		}
	}
	return agentResolvedBinding{id: id, root: rootID, directory: working, program: resolved, args: append([]string(nil), args...)}, nil
}

func (d *agentDeveloperService) capabilities() agentDeveloperCapabilities {
	result := agentDeveloperCapabilities{
		Git: d.git != "", Files: true, Transfers: true, Search: true,
		FileOperations: []string{"list", "read", "write", "create", "copy", "move", "delete", "trash", "trash-list", "restore", "purge"},
	}
	if result.Git {
		result.GitOperations = []string{"status", "diff", "log", "branch", "commit", "push", "pull", "stage", "unstage", "stash", "commit-files"}
	}
	for _, b := range d.workflows {
		result.Workflows = append(result.Workflows, AgentWorkflowBinding{ID: b.id, Root: b.root})
	}
	for _, b := range d.lsp {
		result.LSP = append(result.LSP, AgentLSPBinding{ID: b.id, Root: b.root})
	}
	sort.Slice(result.Workflows, func(i, j int) bool { return result.Workflows[i].ID < result.Workflows[j].ID })
	sort.Slice(result.LSP, func(i, j int) bool { return result.LSP[i].ID < result.LSP[j].ID })
	return result
}

func (d *agentDeveloperService) handle(w http.ResponseWriter, r *http.Request) {
	switch {
	case r.URL.Path == "/v1/developer/files/download-info" && r.Method == http.MethodGet:
		d.handleDownloadInfo(w, r)
	case r.URL.Path == "/v1/developer/files/download" && r.Method == http.MethodGet:
		d.handleDownload(w, r)
	case r.URL.Path == "/v1/developer/uploads" && r.Method == http.MethodPost:
		d.handleUploadStart(w, r)
	case strings.HasPrefix(r.URL.Path, "/v1/developer/uploads/"):
		d.handleUpload(w, r)
	case r.URL.Path == "/v1/developer/files" && r.Method == http.MethodPost:
		d.handleFiles(w, r)
	case r.URL.Path == "/v1/developer/git" && r.Method == http.MethodPost:
		d.handleGit(w, r)
	case r.URL.Path == "/v1/developer/search" && r.Method == http.MethodPost:
		d.handleSearch(w, r)
	case r.URL.Path == "/v1/developer/workflow/run" && r.Method == http.MethodPost:
		d.handleWorkflow(w, r)
	case r.URL.Path == "/v1/developer/lsp/start" && r.Method == http.MethodPost:
		d.handleLSPStart(w, r)
	case strings.HasPrefix(r.URL.Path, "/v1/developer/lsp/"):
		d.handleLSP(w, r)
	default:
		http.NotFound(w, r)
	}
}

func decodeAgentDeveloper(r *http.Request, target any) error {
	decoder := json.NewDecoder(io.LimitReader(r.Body, agentDeveloperBodyLimit+1))
	decoder.DisallowUnknownFields()
	if err := decoder.Decode(target); err != nil {
		return err
	}
	if err := decoder.Decode(&struct{}{}); !errors.Is(err, io.EOF) {
		return errors.New("request must contain one JSON value")
	}
	return nil
}

func (d *agentDeveloperService) workspace(root agentRoot, relative string) (string, error) {
	path, err := resolveAgentPath(root, relative, false)
	if err != nil {
		return "", err
	}
	info, err := os.Stat(path)
	if err != nil || !info.IsDir() {
		if err != nil {
			return "", err
		}
		return "", errors.New("workspace is not a directory")
	}
	return path, nil
}

func (d *agentDeveloperService) handleDownload(w http.ResponseWriter, r *http.Request) {
	filesystem, path, version, info, err := d.openDownload(r)
	if err != nil {
		status := http.StatusBadRequest
		if errors.Is(err, errAgentVersionConflict) {
			status = http.StatusPreconditionFailed
		}
		http.Error(w, err.Error(), status)
		return
	}
	defer filesystem.Close()
	file, err := filesystem.Open(path)
	if err != nil {
		http.Error(w, err.Error(), http.StatusNotFound)
		return
	}
	defer file.Close()
	if rawOffset := r.URL.Query().Get("offset"); rawOffset != "" {
		offset, offsetErr := strconv.ParseInt(rawOffset, 10, 64)
		limit, limitErr := strconv.ParseInt(r.URL.Query().Get("limit"), 10, 64)
		if offsetErr != nil || limitErr != nil || offset < 0 || offset > info.Size() || limit < 1 || limit > agentUploadChunkLimit {
			http.Error(w, "invalid bounded download range", http.StatusBadRequest)
			return
		}
		if remaining := info.Size() - offset; limit > remaining {
			limit = remaining
		}
		w.Header().Set("Content-Type", "application/octet-stream")
		w.WriteHeader(http.StatusPartialContent)
		_, _ = io.CopyN(w, io.NewSectionReader(file, offset, limit), limit)
		return
	}
	w.Header().Set("ETag", "\""+version+"\"")
	w.Header().Set("Content-Disposition", fmt.Sprintf("attachment; filename=%q", filepath.Base(path)))
	w.Header().Set("Content-Type", "application/octet-stream")
	http.ServeContent(w, r, filepath.Base(path), info.ModTime(), file)
}

func (d *agentDeveloperService) handleDownloadInfo(w http.ResponseWriter, r *http.Request) {
	filesystem, path, version, info, err := d.openDownload(r)
	if err != nil {
		status := http.StatusBadRequest
		if errors.Is(err, errAgentVersionConflict) {
			status = http.StatusPreconditionFailed
		}
		http.Error(w, err.Error(), status)
		return
	}
	_ = filesystem.Close()
	d.service.writeJSON(w, http.StatusOK, map[string]any{"path": filepath.ToSlash(r.URL.Query().Get("path")), "name": filepath.Base(path), "size": info.Size(), "version": version, "chunkSize": agentUploadChunkLimit})
}

func (d *agentDeveloperService) openDownload(r *http.Request) (*os.Root, string, string, os.FileInfo, error) {
	root, ok := d.service.roots[r.URL.Query().Get("root")]
	if !ok || !root.Read {
		return nil, "", "", nil, errors.New("readable root is required")
	}
	workspace, err := resolveAgentRelative(root, r.URL.Query().Get("workspace"), false)
	if err != nil {
		return nil, "", "", nil, err
	}
	filesystem, err := root.filesystem.OpenRoot(workspace)
	if err != nil {
		return nil, "", "", nil, err
	}
	path, err := agentWorkspacePath(".", r.URL.Query().Get("path"))
	if err != nil {
		_ = filesystem.Close()
		return nil, "", "", nil, err
	}
	version, err := agentPathVersion(filesystem, path)
	if err != nil {
		_ = filesystem.Close()
		return nil, "", "", nil, err
	}
	expected := strings.Trim(r.Header.Get("If-Match"), "\"")
	if expected == "" {
		expected = r.URL.Query().Get("version")
	}
	if expected != "" && expected != version {
		_ = filesystem.Close()
		return nil, "", "", nil, errAgentVersionConflict
	}
	info, err := filesystem.Stat(path)
	if err != nil {
		_ = filesystem.Close()
		return nil, "", "", nil, err
	}
	if !info.Mode().IsRegular() {
		_ = filesystem.Close()
		return nil, "", "", nil, errors.New("download path is not a file")
	}
	return filesystem, path, version, info, nil
}

func (d *agentDeveloperService) handleUploadStart(w http.ResponseWriter, r *http.Request) {
	var request agentUploadStartRequest
	if err := decodeAgentDeveloper(r, &request); err != nil {
		http.Error(w, err.Error(), http.StatusBadRequest)
		return
	}
	root, ok := d.service.roots[request.Root]
	if !ok || !root.Read || !root.Write {
		http.Error(w, "writable root is required", http.StatusForbidden)
		return
	}
	if request.Size < 0 || request.Size > d.service.config.MaxFileBytes {
		http.Error(w, "upload exceeds configured limit", http.StatusBadRequest)
		return
	}
	workspace, err := resolveAgentRelative(root, request.Workspace, false)
	if err != nil {
		http.Error(w, err.Error(), http.StatusBadRequest)
		return
	}
	filesystem, err := root.filesystem.OpenRoot(workspace)
	if err != nil {
		http.Error(w, err.Error(), http.StatusBadRequest)
		return
	}
	path, err := agentWorkspacePath(".", request.Path)
	if err != nil {
		_ = filesystem.Close()
		http.Error(w, err.Error(), http.StatusBadRequest)
		return
	}
	if _, err := filesystem.Lstat(path); err == nil {
		if !request.Overwrite || request.ExpectedVersion == "" {
			_ = filesystem.Close()
			http.Error(w, "overwrite requires expectedVersion", http.StatusPreconditionRequired)
			return
		}
		if err := agentCheckFileVersion(filesystem, path, request.ExpectedVersion); err != nil {
			_ = filesystem.Close()
			http.Error(w, err.Error(), http.StatusConflict)
			return
		}
	} else if !errors.Is(err, os.ErrNotExist) {
		_ = filesystem.Close()
		http.Error(w, err.Error(), http.StatusBadRequest)
		return
	}
	d.mu.Lock()
	d.pruneUploadsLocked()
	if len(d.uploads) >= agentUploadSessionLimit {
		d.mu.Unlock()
		_ = filesystem.Close()
		http.Error(w, "too many active uploads", http.StatusTooManyRequests)
		return
	}
	id := agentRandomID("upload")
	temporary := filepath.Join(".remotely-uploads", id+".part")
	if err := mkdirAgentAll(filesystem, filepath.Dir(temporary)); err != nil {
		d.mu.Unlock()
		_ = filesystem.Close()
		http.Error(w, err.Error(), http.StatusBadRequest)
		return
	}
	file, err := filesystem.OpenFile(temporary, os.O_WRONLY|os.O_CREATE|os.O_EXCL, 0600)
	if err != nil {
		d.mu.Unlock()
		_ = filesystem.Close()
		http.Error(w, err.Error(), http.StatusConflict)
		return
	}
	_ = file.Close()
	session := &agentUploadSession{id: id, root: request.Root, workspace: request.Workspace, path: path, temporary: temporary, expectedVersion: request.ExpectedVersion, size: request.Size, overwrite: request.Overwrite, expires: time.Now().Add(agentUploadLifetime), filesystem: filesystem}
	d.uploads[id] = session
	d.mu.Unlock()
	d.service.writeJSON(w, http.StatusCreated, map[string]any{"uploadId": id, "chunkSize": agentUploadChunkLimit, "offset": 0, "expiresAt": session.expires.UnixMilli()})
}

func (d *agentDeveloperService) handleUpload(w http.ResponseWriter, r *http.Request) {
	parts := strings.Split(strings.Trim(strings.TrimPrefix(r.URL.Path, "/v1/developer/uploads/"), "/"), "/")
	if len(parts) < 1 || !validAgentID(parts[0]) {
		http.NotFound(w, r)
		return
	}
	d.mu.Lock()
	session, ok := d.uploads[parts[0]]
	d.mu.Unlock()
	if !ok {
		http.NotFound(w, r)
		return
	}
	if len(parts) == 2 && parts[1] == "complete" && r.Method == http.MethodPost {
		d.completeUpload(w, session)
		return
	}
	switch r.Method {
	case http.MethodGet:
		session.mu.Lock()
		d.service.writeJSON(w, http.StatusOK, map[string]any{"uploadId": session.id, "offset": session.offset, "size": session.size, "expiresAt": session.expires.UnixMilli()})
		session.mu.Unlock()
	case http.MethodPut:
		d.writeUploadChunk(w, r, session)
	case http.MethodDelete:
		d.removeUpload(session.id)
		w.WriteHeader(http.StatusNoContent)
	default:
		http.Error(w, "method not allowed", http.StatusMethodNotAllowed)
	}
}

func (d *agentDeveloperService) writeUploadChunk(w http.ResponseWriter, r *http.Request, session *agentUploadSession) {
	offset, err := strconv.ParseInt(r.URL.Query().Get("offset"), 10, 64)
	if err != nil || offset < 0 {
		http.Error(w, "valid offset is required", http.StatusBadRequest)
		return
	}
	session.mu.Lock()
	defer session.mu.Unlock()
	if offset != session.offset {
		d.service.writeJSON(w, http.StatusConflict, map[string]int64{"offset": session.offset})
		return
	}
	file, err := session.filesystem.OpenFile(session.temporary, os.O_WRONLY, 0600)
	if err != nil {
		http.Error(w, err.Error(), http.StatusConflict)
		return
	}
	defer file.Close()
	if _, err := file.Seek(offset, io.SeekStart); err != nil {
		http.Error(w, err.Error(), http.StatusBadRequest)
		return
	}
	var source io.Reader = r.Body
	var part *multipart.Part
	if strings.HasPrefix(strings.ToLower(r.Header.Get("Content-Type")), "multipart/form-data") {
		reader, multipartErr := r.MultipartReader()
		if multipartErr != nil {
			http.Error(w, "invalid multipart upload chunk", http.StatusBadRequest)
			return
		}
		part, multipartErr = reader.NextPart()
		if multipartErr != nil {
			http.Error(w, "multipart upload has no chunk", http.StatusBadRequest)
			return
		}
		defer part.Close()
		source = part
	}
	limited := io.LimitReader(source, agentUploadChunkLimit+1)
	written, err := io.Copy(file, limited)
	if err != nil || written > agentUploadChunkLimit || session.offset+written > session.size {
		_ = file.Truncate(offset)
		http.Error(w, "invalid upload chunk", http.StatusBadRequest)
		return
	}
	if err := file.Sync(); err != nil {
		http.Error(w, err.Error(), http.StatusBadRequest)
		return
	}
	session.offset += written
	session.expires = time.Now().Add(agentUploadLifetime)
	d.service.writeJSON(w, http.StatusOK, map[string]int64{"offset": session.offset})
}

func (d *agentDeveloperService) completeUpload(w http.ResponseWriter, session *agentUploadSession) {
	session.mu.Lock()
	defer session.mu.Unlock()
	if session.offset != session.size {
		d.service.writeJSON(w, http.StatusConflict, map[string]int64{"offset": session.offset})
		return
	}
	if _, err := session.filesystem.Lstat(session.path); err == nil {
		if !session.overwrite || agentCheckFileVersion(session.filesystem, session.path, session.expectedVersion) != nil {
			http.Error(w, errAgentVersionConflict.Error(), http.StatusConflict)
			return
		}
	}
	input, err := session.filesystem.Open(session.temporary)
	if err != nil {
		http.Error(w, err.Error(), http.StatusBadRequest)
		return
	}
	_, err = writeAgentFileRoot(session.filesystem, session.path, input, session.size)
	_ = input.Close()
	if err != nil {
		http.Error(w, err.Error(), http.StatusBadRequest)
		return
	}
	result, err := agentFileMutationResult(session.filesystem, ".", session.path)
	if err != nil {
		http.Error(w, err.Error(), http.StatusBadRequest)
		return
	}
	d.service.writeJSON(w, http.StatusOK, result)
	go d.removeUpload(session.id)
}

func (d *agentDeveloperService) removeUpload(id string) {
	d.mu.Lock()
	session, ok := d.uploads[id]
	if ok {
		delete(d.uploads, id)
	}
	d.mu.Unlock()
	if ok {
		session.mu.Lock()
		_ = session.filesystem.Remove(session.temporary)
		_ = session.filesystem.Close()
		session.mu.Unlock()
	}
}

func (d *agentDeveloperService) pruneUploadsLocked() {
	for id, session := range d.uploads {
		if time.Now().After(session.expires) {
			delete(d.uploads, id)
			session.mu.Lock()
			_ = session.filesystem.Remove(session.temporary)
			_ = session.filesystem.Close()
			session.mu.Unlock()
		}
	}
}

var errAgentVersionConflict = errors.New("file version conflict")
var errAgentVersionRequired = errors.New("expectedVersion is required")

func (d *agentDeveloperService) handleFiles(w http.ResponseWriter, r *http.Request) {
	var request agentDeveloperFileRequest
	if err := decodeAgentDeveloper(r, &request); err != nil {
		http.Error(w, err.Error(), http.StatusBadRequest)
		return
	}
	root, ok := d.service.roots[request.Root]
	if !ok || !root.Read {
		http.Error(w, "readable root is required", http.StatusForbidden)
		return
	}
	mutating := request.Operation == "write" || request.Operation == "create" || request.Operation == "copy" || request.Operation == "move" || request.Operation == "delete" || request.Operation == "trash" || request.Operation == "restore" || request.Operation == "purge"
	if mutating && !root.Write {
		http.Error(w, "write capability is required", http.StatusForbidden)
		return
	}
	workspace, err := resolveAgentRelative(root, request.Workspace, false)
	if err != nil {
		http.Error(w, err.Error(), http.StatusBadRequest)
		return
	}
	filesystem, err := root.filesystem.OpenRoot(workspace)
	if err != nil {
		http.Error(w, err.Error(), http.StatusNotFound)
		return
	}
	defer filesystem.Close()
	d.fileMu.Lock()
	defer d.fileMu.Unlock()
	result, err := d.executeFile(filesystem, ".", request)
	if err != nil {
		status := http.StatusBadRequest
		if errors.Is(err, errAgentVersionConflict) || errors.Is(err, os.ErrExist) {
			status = http.StatusConflict
		} else if errors.Is(err, errAgentVersionRequired) {
			status = http.StatusPreconditionRequired
		} else if errors.Is(err, os.ErrNotExist) {
			status = http.StatusNotFound
		}
		http.Error(w, err.Error(), status)
		return
	}
	d.service.writeJSON(w, http.StatusOK, result)
}

func (d *agentDeveloperService) executeFile(filesystem *os.Root, workspace string, request agentDeveloperFileRequest) (agentDeveloperFileResult, error) {
	path, err := agentWorkspacePath(workspace, request.Path)
	if err != nil {
		return agentDeveloperFileResult{}, err
	}
	switch request.Operation {
	case "list":
		return agentListDeveloperFiles(filesystem, workspace, path)
	case "read":
		return agentReadDeveloperFile(filesystem, workspace, path, min(d.service.config.MaxFileBytes, int64(agentDeveloperFileLimit)))
	case "write":
		if request.ExpectedVersion == "" {
			return agentDeveloperFileResult{}, errAgentVersionRequired
		}
		if int64(len(request.Content)) > min(d.service.config.MaxFileBytes, int64(agentDeveloperFileLimit)) {
			return agentDeveloperFileResult{}, errors.New("file exceeds configured limit")
		}
		if err := agentCheckFileVersion(filesystem, path, request.ExpectedVersion); err != nil {
			return agentDeveloperFileResult{}, err
		}
		if _, err := writeAgentFileRoot(filesystem, path, strings.NewReader(request.Content), int64(len(request.Content))); err != nil {
			return agentDeveloperFileResult{}, err
		}
		return agentFileMutationResult(filesystem, workspace, path)
	case "create":
		if _, err := filesystem.Lstat(path); err == nil {
			return agentDeveloperFileResult{}, os.ErrExist
		} else if !errors.Is(err, os.ErrNotExist) {
			return agentDeveloperFileResult{}, err
		}
		if request.Directory {
			if err := mkdirAgentAll(filesystem, path); err != nil {
				return agentDeveloperFileResult{}, err
			}
		} else {
			if int64(len(request.Content)) > min(d.service.config.MaxFileBytes, int64(agentDeveloperFileLimit)) {
				return agentDeveloperFileResult{}, errors.New("file exceeds configured limit")
			}
			if err := mkdirAgentAll(filesystem, filepath.Dir(path)); err != nil {
				return agentDeveloperFileResult{}, err
			}
			file, err := filesystem.OpenFile(path, os.O_WRONLY|os.O_CREATE|os.O_EXCL, 0600)
			if err != nil {
				return agentDeveloperFileResult{}, err
			}
			_, writeErr := io.WriteString(file, request.Content)
			closeErr := file.Close()
			if writeErr != nil {
				_ = filesystem.Remove(path)
				return agentDeveloperFileResult{}, writeErr
			}
			if closeErr != nil {
				return agentDeveloperFileResult{}, closeErr
			}
		}
		return agentFileMutationResult(filesystem, workspace, path)
	case "move":
		if request.ExpectedVersion == "" {
			return agentDeveloperFileResult{}, errAgentVersionRequired
		}
		target, err := agentWorkspacePath(workspace, request.TargetPath)
		if err != nil {
			return agentDeveloperFileResult{}, err
		}
		if path == workspace || target == workspace {
			return agentDeveloperFileResult{}, errors.New("workspace root cannot be moved")
		}
		if err := agentCheckFileVersion(filesystem, path, request.ExpectedVersion); err != nil {
			return agentDeveloperFileResult{}, err
		}
		if withinAgentRoot(path, target) {
			return agentDeveloperFileResult{}, errors.New("directory cannot be moved into itself")
		}
		if _, err := filesystem.Lstat(target); err == nil {
			if !request.Overwrite {
				return agentDeveloperFileResult{}, os.ErrExist
			}
			if request.TargetVersion == "" {
				return agentDeveloperFileResult{}, errors.New("targetVersion is required when overwriting")
			}
			if err := agentCheckFileVersion(filesystem, target, request.TargetVersion); err != nil {
				return agentDeveloperFileResult{}, err
			}
			if err := removeAgentAll(filesystem, target); err != nil {
				return agentDeveloperFileResult{}, err
			}
		} else if !errors.Is(err, os.ErrNotExist) {
			return agentDeveloperFileResult{}, err
		}
		if err := moveAgentDeveloperEntry(filesystem, path, target, d.service.config.MaxArchiveBytes); err != nil {
			return agentDeveloperFileResult{}, err
		}
		return agentFileMutationResult(filesystem, workspace, target)
	case "copy":
		if request.ExpectedVersion == "" {
			return agentDeveloperFileResult{}, errAgentVersionRequired
		}
		target, err := agentWorkspacePath(workspace, request.TargetPath)
		if err != nil {
			return agentDeveloperFileResult{}, err
		}
		if err := agentCheckFileVersion(filesystem, path, request.ExpectedVersion); err != nil {
			return agentDeveloperFileResult{}, err
		}
		if withinAgentRoot(path, target) {
			return agentDeveloperFileResult{}, errors.New("directory cannot be copied into itself")
		}
		if _, err := filesystem.Lstat(target); err == nil {
			return agentDeveloperFileResult{}, os.ErrExist
		} else if !errors.Is(err, os.ErrNotExist) {
			return agentDeveloperFileResult{}, err
		}
		if err := copyAgentDeveloperEntry(filesystem, path, target, d.service.config.MaxArchiveBytes); err != nil {
			return agentDeveloperFileResult{}, err
		}
		return agentFileMutationResult(filesystem, workspace, target)
	case "delete":
		if request.ExpectedVersion == "" {
			return agentDeveloperFileResult{}, errAgentVersionRequired
		}
		if path == workspace {
			return agentDeveloperFileResult{}, errors.New("workspace root cannot be deleted")
		}
		if err := agentCheckFileVersion(filesystem, path, request.ExpectedVersion); err != nil {
			return agentDeveloperFileResult{}, err
		}
		if request.Recursive {
			err = removeAgentAll(filesystem, path)
		} else {
			err = filesystem.Remove(path)
		}
		return agentDeveloperFileResult{Path: filepath.ToSlash(strings.TrimPrefix(path, workspace+string(filepath.Separator)))}, err
	case "trash":
		if request.ExpectedVersion == "" {
			return agentDeveloperFileResult{}, errAgentVersionRequired
		}
		if path == workspace {
			return agentDeveloperFileResult{}, errors.New("workspace root cannot be trashed")
		}
		if err := agentCheckFileVersion(filesystem, path, request.ExpectedVersion); err != nil {
			return agentDeveloperFileResult{}, err
		}
		id := agentRandomID("trash")
		container := filepath.Join(".remotely-trash", id)
		if err := moveAgentDeveloperEntry(filesystem, path, filepath.Join(container, "data"), d.service.config.MaxArchiveBytes); err != nil {
			return agentDeveloperFileResult{}, err
		}
		manifest := agentTrashManifest{ID: id, OriginalPath: agentDeveloperRelative(workspace, path), TrashedAt: time.Now().UnixMilli(), Version: request.ExpectedVersion}
		data, _ := json.Marshal(manifest)
		if _, err := writeAgentFileRoot(filesystem, filepath.Join(container, "manifest.json"), bytes.NewReader(data), int64(len(data))); err != nil {
			return agentDeveloperFileResult{}, err
		}
		return agentDeveloperFileResult{Path: manifest.OriginalPath, TrashID: id, Version: request.ExpectedVersion}, nil
	case "trash-list":
		return agentListTrash(filesystem)
	case "restore":
		return d.restoreTrash(filesystem, workspace, request)
	case "purge":
		manifest, err := agentReadTrashManifest(filesystem, request.TrashID)
		if err != nil {
			return agentDeveloperFileResult{}, err
		}
		if request.ExpectedVersion == "" {
			return agentDeveloperFileResult{}, errAgentVersionRequired
		}
		if request.ExpectedVersion != manifest.Version {
			return agentDeveloperFileResult{}, errAgentVersionConflict
		}
		if err := removeAgentAll(filesystem, filepath.Join(".remotely-trash", request.TrashID)); err != nil {
			return agentDeveloperFileResult{}, err
		}
		return agentDeveloperFileResult{TrashID: request.TrashID}, nil
	default:
		return agentDeveloperFileResult{}, errors.New("unsupported file operation")
	}
}

func copyAgentDeveloperEntry(filesystem *os.Root, source, target string, maxBytes int64) error {
	entries := 0
	var copied int64
	var copyEntry func(string, string) error
	copyEntry = func(currentSource, currentTarget string) error {
		entries++
		if entries > 100000 {
			return errors.New("move contains too many entries")
		}
		info, err := filesystem.Lstat(currentSource)
		if err != nil {
			return err
		}
		if info.Mode()&os.ModeSymlink != 0 {
			return errors.New("symbolic links cannot be moved")
		}
		if info.IsDir() {
			if err := filesystem.Mkdir(currentTarget, info.Mode().Perm()); err != nil && !errors.Is(err, os.ErrExist) {
				return err
			}
			directory, err := filesystem.Open(currentSource)
			if err != nil {
				return err
			}
			children, readErr := directory.ReadDir(-1)
			_ = directory.Close()
			if readErr != nil {
				return readErr
			}
			for _, child := range children {
				if err := copyEntry(filepath.Join(currentSource, child.Name()), filepath.Join(currentTarget, child.Name())); err != nil {
					return err
				}
			}
			return nil
		}
		if copied > maxBytes-info.Size() {
			return errors.New("move exceeds configured limit")
		}
		input, err := filesystem.Open(currentSource)
		if err != nil {
			return err
		}
		output, err := filesystem.OpenFile(currentTarget, os.O_WRONLY|os.O_CREATE|os.O_EXCL, info.Mode().Perm())
		if err != nil {
			_ = input.Close()
			return err
		}
		written, copyErr := io.Copy(output, input)
		closeErr := output.Close()
		_ = input.Close()
		if copyErr != nil {
			return copyErr
		}
		if closeErr != nil {
			return closeErr
		}
		copied += written
		return nil
	}
	if err := mkdirAgentAll(filesystem, filepath.Dir(target)); err != nil {
		return err
	}
	if err := copyEntry(source, target); err != nil {
		_ = removeAgentAll(filesystem, target)
		return err
	}
	return nil
}

func moveAgentDeveloperEntry(filesystem *os.Root, source, target string, maxBytes int64) error {
	if err := copyAgentDeveloperEntry(filesystem, source, target, maxBytes); err != nil {
		return err
	}
	return removeAgentAll(filesystem, source)
}

func agentReadTrashManifest(filesystem *os.Root, id string) (agentTrashManifest, error) {
	if !validAgentID(id) {
		return agentTrashManifest{}, errors.New("invalid trash id")
	}
	file, err := filesystem.Open(filepath.Join(".remotely-trash", id, "manifest.json"))
	if err != nil {
		return agentTrashManifest{}, err
	}
	defer file.Close()
	var manifest agentTrashManifest
	decoder := json.NewDecoder(io.LimitReader(file, 64*1024))
	if err := decoder.Decode(&manifest); err != nil || manifest.ID != id {
		return agentTrashManifest{}, errors.New("invalid trash manifest")
	}
	return manifest, nil
}

func agentListTrash(filesystem *os.Root) (agentDeveloperFileResult, error) {
	directory, err := filesystem.Open(".remotely-trash")
	if errors.Is(err, os.ErrNotExist) {
		return agentDeveloperFileResult{Trash: []agentTrashManifest{}}, nil
	}
	if err != nil {
		return agentDeveloperFileResult{}, err
	}
	defer directory.Close()
	entries, err := directory.ReadDir(10001)
	if err != nil {
		return agentDeveloperFileResult{}, err
	}
	if len(entries) > 10000 {
		return agentDeveloperFileResult{}, errors.New("trash contains too many entries")
	}
	result := agentDeveloperFileResult{Trash: make([]agentTrashManifest, 0, len(entries))}
	for _, entry := range entries {
		manifest, err := agentReadTrashManifest(filesystem, entry.Name())
		if err == nil {
			result.Trash = append(result.Trash, manifest)
		}
	}
	sort.Slice(result.Trash, func(i, j int) bool { return result.Trash[i].TrashedAt > result.Trash[j].TrashedAt })
	return result, nil
}

func (d *agentDeveloperService) restoreTrash(filesystem *os.Root, workspace string, request agentDeveloperFileRequest) (agentDeveloperFileResult, error) {
	manifest, err := agentReadTrashManifest(filesystem, request.TrashID)
	if err != nil {
		return agentDeveloperFileResult{}, err
	}
	if request.ExpectedVersion == "" {
		return agentDeveloperFileResult{}, errAgentVersionRequired
	}
	if request.ExpectedVersion != manifest.Version {
		return agentDeveloperFileResult{}, errAgentVersionConflict
	}
	targetPath := manifest.OriginalPath
	if request.TargetPath != "" {
		targetPath = request.TargetPath
	}
	target, err := agentWorkspacePath(workspace, targetPath)
	if err != nil {
		return agentDeveloperFileResult{}, err
	}
	if _, err := filesystem.Lstat(target); err == nil {
		if !request.Overwrite {
			return agentDeveloperFileResult{}, os.ErrExist
		}
		if request.TargetVersion == "" {
			return agentDeveloperFileResult{}, errors.New("targetVersion is required when overwriting")
		}
		if err := agentCheckFileVersion(filesystem, target, request.TargetVersion); err != nil {
			return agentDeveloperFileResult{}, err
		}
		if err := removeAgentAll(filesystem, target); err != nil {
			return agentDeveloperFileResult{}, err
		}
	} else if !errors.Is(err, os.ErrNotExist) {
		return agentDeveloperFileResult{}, err
	}
	if err := moveAgentDeveloperEntry(filesystem, filepath.Join(".remotely-trash", request.TrashID, "data"), target, d.service.config.MaxArchiveBytes); err != nil {
		return agentDeveloperFileResult{}, err
	}
	_ = removeAgentAll(filesystem, filepath.Join(".remotely-trash", request.TrashID))
	return agentFileMutationResult(filesystem, workspace, target)
}

func agentWorkspacePath(workspace, requested string) (string, error) {
	relative, err := cleanAgentRelativePath(requested)
	if err != nil {
		return "", err
	}
	first := strings.Split(filepath.ToSlash(relative), "/")[0]
	if first == ".remotely-trash" || first == ".remotely-uploads" {
		return "", errors.New("path is reserved for agent recovery data")
	}
	path := filepath.Clean(filepath.Join(workspace, relative))
	if !withinAgentRoot(workspace, path) {
		return "", errors.New("path escapes the selected workspace")
	}
	return path, nil
}

func agentListDeveloperFiles(filesystem *os.Root, workspace, path string) (agentDeveloperFileResult, error) {
	directory, err := filesystem.Open(path)
	if err != nil {
		return agentDeveloperFileResult{}, err
	}
	defer directory.Close()
	entries, err := directory.ReadDir(-1)
	if err != nil {
		return agentDeveloperFileResult{}, err
	}
	if len(entries) > 10000 {
		return agentDeveloperFileResult{}, errors.New("directory contains too many entries")
	}
	result := agentDeveloperFileResult{Path: agentDeveloperRelative(workspace, path)}
	for _, entry := range entries {
		if entry.Name() == ".remotely-trash" || entry.Name() == ".remotely-uploads" {
			continue
		}
		if entry.Type()&os.ModeSymlink != 0 {
			continue
		}
		info, err := entry.Info()
		if err != nil {
			return agentDeveloperFileResult{}, err
		}
		version, err := agentPathVersion(filesystem, filepath.Join(path, entry.Name()))
		if err != nil {
			return agentDeveloperFileResult{}, err
		}
		result.Entries = append(result.Entries, agentDeveloperFileEntry{Name: entry.Name(), Path: filepath.ToSlash(filepath.Join(result.Path, entry.Name())), Directory: info.IsDir(), Size: info.Size(), ModifiedAt: info.ModTime().UnixMilli(), Version: version})
	}
	sort.Slice(result.Entries, func(i, j int) bool { return result.Entries[i].Name < result.Entries[j].Name })
	result.Version = agentDirectoryVersion(result.Entries)
	return result, nil
}

func agentReadDeveloperFile(filesystem *os.Root, workspace, path string, limit int64) (agentDeveloperFileResult, error) {
	file, err := filesystem.Open(path)
	if err != nil {
		return agentDeveloperFileResult{}, err
	}
	defer file.Close()
	data, err := io.ReadAll(io.LimitReader(file, limit+1))
	if err != nil {
		return agentDeveloperFileResult{}, err
	}
	if int64(len(data)) > limit || bytes.IndexByte(data, 0) >= 0 {
		return agentDeveloperFileResult{}, errors.New("file is too large or is not text")
	}
	version, err := agentPathVersion(filesystem, path)
	if err != nil {
		return agentDeveloperFileResult{}, err
	}
	return agentDeveloperFileResult{Path: agentDeveloperRelative(workspace, path), Content: string(data), Size: int64(len(data)), Version: version}, nil
}

func agentCheckFileVersion(filesystem *os.Root, path, expected string) error {
	if expected == "" {
		return nil
	}
	current, err := agentPathVersion(filesystem, path)
	if err != nil {
		return err
	}
	if current != expected {
		return errAgentVersionConflict
	}
	return nil
}

func agentPathVersion(filesystem *os.Root, path string) (string, error) {
	file, err := filesystem.Open(path)
	if err != nil {
		return "", err
	}
	defer file.Close()
	info, err := file.Stat()
	if err != nil {
		return "", err
	}
	state := fmt.Sprintf("%t:%d:%d:%d", info.IsDir(), info.Size(), info.ModTime().UnixNano(), info.Mode())
	return fmt.Sprintf("%x", sha256.Sum256([]byte(state))), nil
}

func agentFileMutationResult(filesystem *os.Root, workspace, path string) (agentDeveloperFileResult, error) {
	info, err := filesystem.Stat(path)
	if err != nil {
		return agentDeveloperFileResult{}, err
	}
	version, err := agentPathVersion(filesystem, path)
	return agentDeveloperFileResult{Path: agentDeveloperRelative(workspace, path), Size: info.Size(), Version: version}, err
}

func agentDeveloperRelative(workspace, path string) string {
	relative, _ := filepath.Rel(workspace, path)
	return filepath.ToSlash(relative)
}

func agentDirectoryVersion(entries []agentDeveloperFileEntry) string {
	data, _ := json.Marshal(entries)
	return fmt.Sprintf("%x", sha256.Sum256(data))
}

func (d *agentDeveloperService) handleGit(w http.ResponseWriter, r *http.Request) {
	if d.git == "" {
		http.Error(w, "git is unavailable", http.StatusNotImplemented)
		return
	}
	var request agentGitRequest
	if err := decodeAgentDeveloper(r, &request); err != nil {
		http.Error(w, err.Error(), 400)
		return
	}
	root, ok := d.service.roots[request.Root]
	if !ok || !root.Read || !root.Execute {
		http.Error(w, "executable root is required", 403)
		return
	}
	mutating := request.Operation == "commit" || request.Operation == "push" || request.Operation == "pull" || request.Operation == "stage" || request.Operation == "unstage" || (request.Operation == "stash" && request.StashAction != "" && request.StashAction != "list") || (request.Operation == "branch" && request.BranchAction != "" && request.BranchAction != "list")
	if mutating && !root.Write {
		http.Error(w, "write capability is required", http.StatusForbidden)
		return
	}
	workspace, err := d.workspace(root, request.Workspace)
	if err != nil {
		http.Error(w, err.Error(), 400)
		return
	}
	args, err := agentGitArgs(&request)
	if err != nil {
		http.Error(w, err.Error(), 400)
		return
	}
	id, err := d.service.jobs.start("developer.git."+request.Operation, func(ctx context.Context, report *agentJobReporter) (any, error) {
		ctx, cancel := context.WithTimeout(ctx, 10*time.Minute)
		defer cancel()
		return d.runGit(ctx, report, workspace, request, args)
	})
	if err != nil {
		http.Error(w, err.Error(), 429)
		return
	}
	d.service.writeJSON(w, http.StatusAccepted, map[string]string{"jobId": id})
}

func agentGitArgs(r *agentGitRequest) ([]string, error) {
	if len(r.Paths) > 256 {
		return nil, errors.New("too many paths")
	}
	paths := make([]string, 0, len(r.Paths))
	for _, p := range r.Paths {
		clean, err := cleanAgentRelativePath(p)
		if err != nil || clean == "." || strings.HasPrefix(clean, "-") {
			return nil, errors.New("invalid git path")
		}
		paths = append(paths, filepath.ToSlash(clean))
	}
	ref := func(v string) bool {
		return v == "" || (len(v) <= 256 && !strings.HasPrefix(v, "-") && !strings.ContainsAny(v, " \t\r\n~^:?*[\\") && !strings.Contains(v, "..") && !strings.HasSuffix(v, "."))
	}
	if !ref(r.Base) || !ref(r.Head) || !ref(r.Branch) || !ref(r.Remote) {
		return nil, errors.New("invalid git reference")
	}
	switch r.Operation {
	case "status":
		return []string{"status", "--porcelain=v2", "--branch", "-z", "--untracked-files=all"}, nil
	case "diff":
		a := []string{"diff", "--no-ext-diff", "--no-color"}
		if r.Staged {
			a = append(a, "--staged")
		}
		if r.Base != "" {
			a = append(a, r.Base)
		}
		if r.Head != "" {
			a = append(a, r.Head)
		}
		if len(paths) > 0 {
			a = append(a, "--")
			a = append(a, paths...)
		}
		return a, nil
	case "log":
		limit := r.Limit
		if limit <= 0 {
			limit = 50
		}
		if limit > 500 {
			return nil, errors.New("log limit exceeds 500")
		}
		return []string{"log", "--no-decorate", "--date=iso-strict", "--max-count=" + strconv.Itoa(limit), "--pretty=format:%H%x1f%P%x1f%an%x1f%ae%x1f%aI%x1f%s%x1e"}, nil
	case "branch":
		switch r.BranchAction {
		case "", "list":
			return []string{"branch", "--format=%(HEAD)%09%(refname:short)%09%(upstream:short)"}, nil
		case "create":
			if r.Branch == "" {
				break
			}
			return []string{"branch", r.Branch}, nil
		case "switch":
			if r.Branch == "" {
				break
			}
			return []string{"switch", r.Branch}, nil
		case "delete":
			if r.Branch == "" {
				break
			}
			return []string{"branch", "--delete", r.Branch}, nil
		}
		return nil, errors.New("invalid branch operation")
	case "commit":
		if len(r.Message) == 0 || len(r.Message) > 8192 || strings.IndexByte(r.Message, 0) >= 0 {
			return nil, errors.New("invalid commit message")
		}
		if len(paths) == 0 {
			return []string{"commit", "-m", r.Message}, nil
		}
		a := []string{"commit", "-m", r.Message, "--"}
		return append(a, paths...), nil
	case "push":
		if r.Branch != "" && r.Remote == "" {
			return nil, errors.New("push branch requires a remote")
		}
		a := []string{"push", "--porcelain"}
		if r.SetUpstream {
			a = append(a, "--set-upstream")
		}
		if r.Remote != "" {
			a = append(a, r.Remote)
		}
		if r.Branch != "" {
			a = append(a, r.Branch)
		}
		return a, nil
	case "pull":
		if r.Branch != "" && r.Remote == "" {
			return nil, errors.New("pull branch requires a remote")
		}
		a := []string{"pull", "--no-edit"}
		if r.Rebase {
			a = append(a, "--rebase")
		}
		if r.Remote != "" {
			a = append(a, r.Remote)
		}
		if r.Branch != "" {
			a = append(a, r.Branch)
		}
		return a, nil
	case "stage":
		if len(paths) == 0 {
			return nil, errors.New("stage requires paths")
		}
		return append([]string{"add", "--"}, paths...), nil
	case "unstage":
		if len(paths) == 0 {
			return nil, errors.New("unstage requires paths")
		}
		return append([]string{"restore", "--staged", "--"}, paths...), nil
	case "stash":
		switch r.StashAction {
		case "", "list":
			return []string{"stash", "list", "--format=%H%x1f%gd%x1f%gs%x1e"}, nil
		case "push":
			if len(r.Message) > 8192 || strings.IndexByte(r.Message, 0) >= 0 {
				return nil, errors.New("invalid stash message")
			}
			a := []string{"stash", "push"}
			if r.IncludeUntracked {
				a = append(a, "--include-untracked")
			}
			if r.Message != "" {
				a = append(a, "--message", r.Message)
			}
			if len(paths) > 0 {
				a = append(a, "--")
				a = append(a, paths...)
			}
			return a, nil
		case "pop", "drop":
			if r.StashIndex < 0 || r.StashIndex > 10000 {
				return nil, errors.New("invalid stash index")
			}
			return []string{"stash", r.StashAction, fmt.Sprintf("stash@{%d}", r.StashIndex)}, nil
		case "apply":
			if !regexp.MustCompile(`^[0-9a-fA-F]{40}([0-9a-fA-F]{24})?$`).MatchString(r.StashID) {
				return nil, errors.New("invalid stable stash id")
			}
			return []string{"stash", "apply", r.StashID}, nil
		default:
			return nil, errors.New("invalid stash operation")
		}
	case "commit-files":
		ref := r.Head
		if ref == "" {
			ref = "HEAD"
		}
		return []string{"diff-tree", "--no-commit-id", "--name-status", "-r", "-z", ref}, nil
	default:
		return nil, errors.New("unsupported git operation")
	}
}

func (d *agentDeveloperService) runGit(ctx context.Context, report *agentJobReporter, workspace string, request agentGitRequest, args []string) (any, error) {
	env := map[string]string{"GIT_TERMINAL_PROMPT": "0", "GCM_INTERACTIVE": "Never", "GIT_PAGER": "cat", "GIT_CONFIG_NOSYSTEM": "1"}
	args = append(append([]string(nil), d.gitConfig...), args...)
	process, stdout, err := d.service.processes.startProtocol(d.git, workspace, args, env)
	if err != nil {
		return nil, err
	}
	defer d.service.processes.discardOutput(stdout)
	_, err = process.await(ctx, report)
	snap := process.snapshot()
	out, _, outTruncated, _ := stdout.read(0, agentDeveloperOutputLimit)
	stderr, _, errTruncated, _ := process.output.read(0, agentDeveloperOutputLimit)
	result := agentGitResult{Operation: request.Operation, ExitCode: snap.ExitCode, Stderr: agentRedactDeveloper(string(stderr)), Truncated: outTruncated || errTruncated}
	if err == nil && snap.ExitCode != 0 {
		err = fmt.Errorf("git exited with code %d", snap.ExitCode)
	}
	switch request.Operation {
	case "status":
		result.Status = parseAgentGitStatus(out)
	case "log":
		result.Commits = parseAgentGitLog(out)
	case "branch":
		if request.BranchAction == "" || request.BranchAction == "list" {
			result.Branches = parseAgentGitBranches(out)
		} else {
			result.Stdout = agentRedactDeveloper(string(out))
		}
	case "diff":
		result.Diff = string(out)
	case "stash":
		if request.StashAction == "" || request.StashAction == "list" {
			result.Stashes = parseAgentGitStashes(out)
		} else {
			result.Stdout = agentRedactDeveloper(string(out))
		}
	case "commit-files":
		result.Files = parseAgentGitFiles(out)
	default:
		result.Stdout = agentRedactDeveloper(string(out))
	}
	return result, err
}

func parseAgentGitStashes(data []byte) []agentGitStash {
	var result []agentGitStash
	for _, record := range bytes.Split(data, []byte{0x1e}) {
		fields := strings.SplitN(strings.TrimSpace(string(record)), "\x1f", 3)
		if len(fields) != 3 {
			continue
		}
		index := 0
		_, _ = fmt.Sscanf(fields[1], "stash@{%d}", &index)
		branch := ""
		message := fields[2]
		if separator := strings.Index(message, ": "); separator >= 0 {
			prefix := message[:separator]
			message = message[separator+2:]
			if position := strings.LastIndex(prefix, " on "); position >= 0 {
				branch = prefix[position+4:]
			}
		}
		result = append(result, agentGitStash{ID: fields[0], Index: index, Ref: fields[1], Branch: branch, Message: message})
	}
	return result
}

func parseAgentGitFiles(data []byte) []agentGitFile {
	var result []agentGitFile
	records := bytes.Split(data, []byte{0})
	for index := 0; index+1 < len(records); {
		status := string(records[index])
		index++
		if status == "" || index >= len(records) {
			break
		}
		file := agentGitFile{Status: status, Path: string(records[index])}
		index++
		if (strings.HasPrefix(status, "R") || strings.HasPrefix(status, "C")) && index < len(records) {
			file.OriginalPath = file.Path
			file.Path = string(records[index])
			index++
		}
		result = append(result, file)
	}
	return result
}

func parseAgentGitStatus(data []byte) *agentGitStatus {
	r := &agentGitStatus{}
	records := bytes.Split(data, []byte{0})
	for index := 0; index < len(records); index++ {
		raw := records[index]
		line := string(raw)
		if strings.HasPrefix(line, "# branch.head ") {
			r.Branch = strings.TrimPrefix(line, "# branch.head ")
		} else if strings.HasPrefix(line, "# branch.ab ") {
			fmt.Sscanf(line, "# branch.ab +%d -%d", &r.Ahead, &r.Behind)
		} else if strings.HasPrefix(line, "1 ") {
			p := strings.SplitN(line, " ", 9)
			if len(p) == 9 {
				r.Entries = append(r.Entries, agentGitEntry{Path: p[8], Index: string(p[1][0]), Worktree: string(p[1][1])})
			}
		} else if strings.HasPrefix(line, "2 ") {
			parts := strings.SplitN(line, " ", 10)
			if len(parts) == 10 {
				entry := agentGitEntry{Path: parts[9], Index: string(parts[1][0]), Worktree: string(parts[1][1])}
				if index+1 < len(records) {
					index++
					entry.OriginalPath = string(records[index])
				}
				r.Entries = append(r.Entries, entry)
			}
		} else if strings.HasPrefix(line, "? ") {
			r.Entries = append(r.Entries, agentGitEntry{Path: line[2:], Index: "?", Worktree: "?"})
		}
	}
	return r
}

func parseAgentGitLog(data []byte) []agentGitCommit {
	var result []agentGitCommit
	for _, record := range bytes.Split(data, []byte{0x1e}) {
		fields := strings.Split(string(bytes.TrimSpace(record)), "\x1f")
		if len(fields) == 6 {
			result = append(result, agentGitCommit{Hash: fields[0], Parents: strings.Fields(fields[1]), AuthorName: fields[2], AuthorEmail: fields[3], AuthoredAt: fields[4], Subject: fields[5]})
		}
	}
	return result
}
func parseAgentGitBranches(data []byte) []agentGitBranch {
	var result []agentGitBranch
	for _, line := range strings.Split(strings.TrimSpace(string(data)), "\n") {
		fields := strings.SplitN(strings.TrimSuffix(line, "\r"), "\t", 3)
		if len(fields) != 3 || fields[1] == "" {
			continue
		}
		result = append(result, agentGitBranch{Name: fields[1], Current: fields[0] == "*", Upstream: fields[2]})
	}
	return result
}

func (d *agentDeveloperService) handleSearch(w http.ResponseWriter, r *http.Request) {
	var request agentSearchRequest
	if err := decodeAgentDeveloper(r, &request); err != nil {
		http.Error(w, err.Error(), 400)
		return
	}
	root, ok := d.service.roots[request.Root]
	if !ok || !root.Read {
		http.Error(w, "readable root is required", 403)
		return
	}
	workspace, err := d.workspace(root, request.Workspace)
	if err != nil {
		http.Error(w, err.Error(), 400)
		return
	}
	if len(request.Query) == 0 || len(request.Query) > 4096 || len(request.Glob) > 512 {
		http.Error(w, "invalid search", 400)
		return
	}
	limit := request.MaxResults
	if limit <= 0 {
		limit = 500
	}
	if limit > agentSearchResultLimit {
		http.Error(w, "search result limit exceeds 5000", 400)
		return
	}
	id, err := d.service.jobs.start("developer.search", func(ctx context.Context, report *agentJobReporter) (any, error) {
		ctx, cancel := context.WithTimeout(ctx, 2*time.Minute)
		defer cancel()
		return runAgentSearch(ctx, report, workspace, request, limit)
	})
	if err != nil {
		http.Error(w, err.Error(), 429)
		return
	}
	d.service.writeJSON(w, 202, map[string]string{"jobId": id})
}

func runAgentSearch(ctx context.Context, report *agentJobReporter, workspace string, request agentSearchRequest, limit int) (agentSearchResult, error) {
	var expression *regexp.Regexp
	var err error
	query := request.Query
	if !request.CaseSensitive {
		query = "(?i)" + regexp.QuoteMeta(query)
	} else if !request.Regex {
		query = regexp.QuoteMeta(query)
	}
	if request.Regex {
		query = request.Query
		if !request.CaseSensitive {
			query = "(?i)" + query
		}
	}
	expression, err = regexp.Compile(query)
	if err != nil {
		return agentSearchResult{}, err
	}
	result := agentSearchResult{}
	err = filepath.WalkDir(workspace, func(path string, entry fs.DirEntry, walkErr error) error {
		if walkErr != nil {
			return walkErr
		}
		if err := ctx.Err(); err != nil {
			return err
		}
		relative, _ := filepath.Rel(workspace, path)
		if entry.IsDir() {
			if relative != "." && (entry.Name() == ".git" || entry.Name() == "node_modules" || entry.Name() == "build") {
				return filepath.SkipDir
			}
			return nil
		}
		if entry.Type()&os.ModeSymlink != 0 {
			return nil
		}
		if request.Glob != "" {
			matched, globErr := filepath.Match(request.Glob, filepath.ToSlash(relative))
			if globErr != nil {
				return globErr
			}
			if !matched {
				return nil
			}
		}
		info, infoErr := entry.Info()
		if infoErr != nil {
			return infoErr
		}
		if info.Size() > agentSearchFileLimit {
			return nil
		}
		file, openErr := os.Open(path)
		if openErr != nil {
			return openErr
		}
		data, readErr := io.ReadAll(io.LimitReader(file, agentSearchFileLimit+1))
		_ = file.Close()
		if readErr != nil {
			return readErr
		}
		if len(data) > agentSearchFileLimit {
			return nil
		}
		if bytes.IndexByte(data, 0) >= 0 {
			return nil
		}
		scanner := bufio.NewScanner(bytes.NewReader(data))
		scanner.Buffer(make([]byte, 64*1024), agentSearchFileLimit)
		line := 0
		for scanner.Scan() {
			line++
			text := scanner.Text()
			locations := expression.FindAllStringIndex(text, -1)
			for _, location := range locations {
				result.Matches = append(result.Matches, agentSearchMatch{Path: filepath.ToSlash(relative), Line: line, Column: location[0] + 1, Preview: agentBoundText(text, 2048)})
				if len(result.Matches) >= limit {
					result.Truncated = true
					return errors.New("search result limit")
				}
			}
		}
		report.set(int64(len(result.Matches)), int64(limit), filepath.ToSlash(relative))
		return scanner.Err()
	})
	if err != nil && err.Error() != "search result limit" {
		return result, err
	}
	return result, nil
}

func (d *agentDeveloperService) handleWorkflow(w http.ResponseWriter, r *http.Request) {
	var request agentWorkflowRequest
	if err := decodeAgentDeveloper(r, &request); err != nil {
		http.Error(w, err.Error(), 400)
		return
	}
	binding, ok := d.workflows[request.Workflow]
	if !ok {
		http.Error(w, "unknown workflow", 404)
		return
	}
	id, err := d.service.jobs.start("developer.workflow."+binding.id, func(ctx context.Context, report *agentJobReporter) (any, error) {
		ctx, cancel := context.WithTimeout(ctx, 30*time.Minute)
		defer cancel()
		process, stdout, startErr := d.service.processes.startProtocol(binding.program, binding.directory, binding.args, map[string]string{"GIT_TERMINAL_PROMPT": "0", "GCM_INTERACTIVE": "Never"})
		if startErr != nil {
			return nil, startErr
		}
		defer d.service.processes.discardOutput(stdout)
		_, awaitErr := process.await(ctx, report)
		snap := process.snapshot()
		out, _, ot, _ := stdout.read(0, agentDeveloperOutputLimit)
		stderr, _, et, _ := process.output.read(0, agentDeveloperOutputLimit)
		result := agentWorkflowResult{ExitCode: snap.ExitCode, Stdout: agentRedactDeveloper(string(out)), Stderr: agentRedactDeveloper(string(stderr)), Truncated: ot || et}
		if awaitErr == nil && snap.ExitCode != 0 {
			awaitErr = fmt.Errorf("workflow exited with code %d", snap.ExitCode)
		}
		return result, awaitErr
	})
	if err != nil {
		http.Error(w, err.Error(), 429)
		return
	}
	d.service.writeJSON(w, 202, map[string]string{"jobId": id})
}

func (d *agentDeveloperService) handleLSPStart(w http.ResponseWriter, r *http.Request) {
	var request agentLSPStartRequest
	if err := decodeAgentDeveloper(r, &request); err != nil {
		http.Error(w, err.Error(), 400)
		return
	}
	binding, ok := d.lsp[request.Binding]
	if !ok {
		http.Error(w, "unknown lsp binding", 404)
		return
	}
	root := d.service.roots[binding.root]
	workspace := binding.directory
	var err error
	if request.Workspace != "" {
		workspace, err = d.workspace(root, request.Workspace)
		if err != nil {
			http.Error(w, err.Error(), 400)
			return
		}
	}
	d.mu.Lock()
	if len(d.sessions) >= agentLSPSessionLimit {
		d.mu.Unlock()
		http.Error(w, "too many lsp sessions", 429)
		return
	}
	process, output, err := d.service.processes.startProtocol(binding.program, workspace, binding.args, nil)
	if err != nil {
		d.mu.Unlock()
		http.Error(w, err.Error(), 400)
		return
	}
	session := &agentLSPSession{id: agentRandomID("lsp"), process: process, output: output, manager: d.service.processes, closed: make(chan struct{})}
	session.owner = d
	d.sessions[session.id] = session
	d.mu.Unlock()
	go session.read()
	d.service.writeJSON(w, 201, map[string]string{"sessionId": session.id})
}

func (d *agentDeveloperService) handleLSP(w http.ResponseWriter, r *http.Request) {
	parts := strings.Split(strings.TrimPrefix(r.URL.Path, "/v1/developer/lsp/"), "/")
	if len(parts) != 2 {
		http.NotFound(w, r)
		return
	}
	d.mu.Lock()
	session, ok := d.sessions[parts[0]]
	d.mu.Unlock()
	if !ok {
		http.NotFound(w, r)
		return
	}
	switch parts[1] {
	case "message":
		if r.Method != http.MethodPost {
			http.Error(w, "method not allowed", 405)
			return
		}
		body, err := io.ReadAll(io.LimitReader(r.Body, agentLSPMessageLimit+1))
		if err != nil || len(body) > agentLSPMessageLimit || !json.Valid(body) {
			http.Error(w, "invalid JSON-RPC message", 400)
			return
		}
		framed := append([]byte(fmt.Sprintf("Content-Length: %d\r\n\r\n", len(body))), body...)
		for len(framed) > 0 {
			size := len(framed)
			if size > agentProcessInputChunkLimit {
				size = agentProcessInputChunkLimit
			}
			if err := session.process.writeInputContext(r.Context(), framed[:size]); err != nil {
				http.Error(w, err.Error(), 409)
				return
			}
			framed = framed[size:]
		}
		w.WriteHeader(204)
	case "messages":
		if r.Method != http.MethodGet {
			http.Error(w, "method not allowed", 405)
			return
		}
		after, _ := strconv.ParseInt(r.URL.Query().Get("after"), 10, 64)
		limit, _ := strconv.Atoi(r.URL.Query().Get("limit"))
		if limit <= 0 || limit > 256 {
			limit = 100
		}
		session.mu.Lock()
		messages := make([]agentLSPMessage, 0, limit)
		for _, message := range session.messages {
			if message.Sequence > after && len(messages) < limit {
				messages = append(messages, message)
			}
		}
		running := session.process.snapshot().Status == "running"
		session.mu.Unlock()
		d.service.writeJSON(w, 200, map[string]any{"messages": messages, "running": running})
	case "stop":
		if r.Method != http.MethodPost {
			http.Error(w, "method not allowed", 405)
			return
		}
		d.removeSession(session.id)
		w.WriteHeader(204)
	default:
		http.NotFound(w, r)
	}
}

func (s *agentLSPSession) read() {
	defer close(s.closed)
	defer s.manager.discardOutput(s.output)
	defer func() {
		s.owner.mu.Lock()
		if s.owner.sessions[s.id] == s {
			delete(s.owner.sessions, s.id)
		}
		s.owner.mu.Unlock()
	}()
	var consumed int64
	buffer := []byte{}
	ticker := time.NewTicker(20 * time.Millisecond)
	defer ticker.Stop()
	for {
		data, total, _, _ := s.output.read(consumed, 256*1024)
		if len(data) > 0 {
			buffer = append(buffer, data...)
			consumed += int64(len(data))
			buffer = s.parse(buffer)
		}
		if s.process.snapshot().Status != "running" && consumed >= total {
			return
		}
		<-ticker.C
	}
}
func (s *agentLSPSession) parse(data []byte) []byte {
	for {
		headerEnd := bytes.Index(data, []byte("\r\n\r\n"))
		if headerEnd < 0 {
			return data
		}
		length := -1
		for _, line := range strings.Split(string(data[:headerEnd]), "\r\n") {
			parts := strings.SplitN(line, ":", 2)
			if len(parts) == 2 && strings.EqualFold(strings.TrimSpace(parts[0]), "Content-Length") {
				length, _ = strconv.Atoi(strings.TrimSpace(parts[1]))
			}
		}
		if length < 0 || length > agentLSPMessageLimit {
			return nil
		}
		start := headerEnd + 4
		if len(data) < start+length {
			return data
		}
		message := append(json.RawMessage(nil), data[start:start+length]...)
		if json.Valid(message) {
			s.mu.Lock()
			s.next++
			s.messages = append(s.messages, agentLSPMessage{Sequence: s.next, Message: message})
			if len(s.messages) > agentLSPRetainedMessages {
				s.messages = s.messages[len(s.messages)-agentLSPRetainedMessages:]
			}
			s.mu.Unlock()
		}
		data = data[start+length:]
	}
}

func (d *agentDeveloperService) removeSession(id string) {
	d.mu.Lock()
	session, ok := d.sessions[id]
	if ok {
		delete(d.sessions, id)
	}
	d.mu.Unlock()
	if ok {
		session.process.stop()
	}
}
func (d *agentDeveloperService) close() {
	d.mu.Lock()
	sessions := make([]*agentLSPSession, 0, len(d.sessions))
	for _, s := range d.sessions {
		sessions = append(sessions, s)
	}
	uploads := make([]*agentUploadSession, 0, len(d.uploads))
	for _, upload := range d.uploads {
		uploads = append(uploads, upload)
	}
	d.sessions = make(map[string]*agentLSPSession)
	d.uploads = make(map[string]*agentUploadSession)
	d.mu.Unlock()
	for _, s := range sessions {
		s.process.stop()
	}
	for _, upload := range uploads {
		upload.mu.Lock()
		_ = upload.filesystem.Remove(upload.temporary)
		_ = upload.filesystem.Close()
		upload.mu.Unlock()
	}
}
func agentBoundText(value string, limit int) string {
	if len(value) > limit {
		return value[:limit]
	}
	return value
}
func agentRedactDeveloper(value string) string {
	credential := regexp.MustCompile(`(?i)(https?://)[^/@\s]+@`)
	value = credential.ReplaceAllString(value, "$1")
	token := regexp.MustCompile(`(?i)(token|password|authorization)([=: ]+)[^\s]+`)
	return token.ReplaceAllString(value, "$1$2[redacted]")
}
