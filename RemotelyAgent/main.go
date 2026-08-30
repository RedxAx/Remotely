package main

import (
	"archive/tar"
	"archive/zip"
	"compress/gzip"
	"crypto/sha1"
	"encoding/base64"
	"encoding/json"
	"errors"
	"flag"
	"fmt"
	"io"
	"net/http"
	"os"
	"os/exec"
	"path/filepath"
	"strings"
	"sync"
	"time"

	"github.com/BurntSushi/toml"
	"gopkg.in/yaml.v3"
)

const agentVersion = "1.4.0"

const (
	agentLegacyArchiveMaxBytes   = 4 * 1024 * 1024 * 1024
	agentLegacyArchiveMaxEntries = 100000
	agentManifestMaxBytes        = 16 * 1024 * 1024
)

type RemoteIndex struct {
	GeneratedAt  int64            `json:"generatedAt"`
	InstancePath string           `json:"instancePath"`
	Resources    []RemoteResource `json:"resources"`
}

type RemoteResource struct {
	Path        string   `json:"path"`
	Type        string   `json:"type"`
	FileName    string   `json:"fileName"`
	Hash        string   `json:"hash"`
	Murmur2     *uint32  `json:"murmur2,omitempty"`
	Size        int64    `json:"size"`
	Mtime       int64    `json:"mtime"`
	Enabled     int      `json:"enabled"`
	Name        string   `json:"name,omitempty"`
	Version     string   `json:"version,omitempty"`
	Authors     []string `json:"authors,omitempty"`
	Description string   `json:"description,omitempty"`
	McVersions  string   `json:"mcVersions,omitempty"`
	IconBase64  string   `json:"iconBase64,omitempty"`
}

type FetchManifest struct {
	Items       []FetchItem `json:"items"`
	Concurrency int         `json:"concurrency"`
	Retries     int         `json:"retries"`
	TimeoutSec  int         `json:"timeoutSec"`
}

type FetchItem struct {
	URL  string `json:"url"`
	Dest string `json:"dest"`
	Sha1 string `json:"sha1,omitempty"`
}

type ModpackSpec struct {
	TargetDir             string      `json:"targetDir"`
	Files                 []FetchItem `json:"files"`
	OverridesTar          string      `json:"overridesTar,omitempty"`
	MrpackPath            string      `json:"mrpackPath,omitempty"`
	CurseForgeZipPath     string      `json:"curseForgeZipPath,omitempty"`
	PreconfiguredPackPath string      `json:"preconfiguredPackPath,omitempty"`
	ServerJarPath         string      `json:"serverJarPath,omitempty"`
	McVersion             string      `json:"mcVersion,omitempty"`
	Concurrency           int         `json:"concurrency"`
	Retries               int         `json:"retries"`
	TimeoutSec            int         `json:"timeoutSec"`
}

func main() {
	if len(os.Args) < 2 {
		usage()
		os.Exit(2)
	}
	switch os.Args[1] {
	case "--version", "version":
		fmt.Println(agentVersion)
	case "index":
		cmdIndex(os.Args[2:])
	case "fetch":
		cmdFetch(os.Args[2:])
	case "modpack-install":
		cmdModpackInstall(os.Args[2:])
	case "extract-curseforge-manifest":
		cmdExtractCurseforgeManifest(os.Args[2:])
	case "check-zip-type":
		cmdCheckZipType(os.Args[2:])
	case "flatten-zip":
		cmdFlattenZip(os.Args[2:])
	case "lifecycle-start":
		cmdLifecycleStart(os.Args[2:])
	case "lifecycle-stop":
		cmdLifecycleStop(os.Args[2:])
	case "lifecycle-send":
		cmdLifecycleSend(os.Args[2:])
	case "lifecycle-status":
		cmdLifecycleStatus(os.Args[2:])
	case "lifecycle-console":
		cmdLifecycleConsole(os.Args[2:])
	case "lifecycle-supervise":
		cmdLifecycleSupervise(os.Args[2:])
	case "serve":
		cmdServe(os.Args[2:])
	case "relay":
		cmdRelay(os.Args[2:])
	default:
		usage()
		os.Exit(2)
	}
}

func usage() {
	fmt.Println(`remotely-agent ` + agentVersion + `
Usage:
  remotely-agent version
  remotely-agent index --dir <instance_dir> --out <out_json> [--format json|ndjson] [--with-meta] [--with-fp] [--with-icons]
  remotely-agent fetch --manifest <manifest.json>
  remotely-agent modpack-install --spec <spec.json>
  remotely-agent extract-curseforge-manifest --manifest <manifest.json>
  remotely-agent check-zip-type --zip <zip_path>
  remotely-agent flatten-zip --zip <zip_path> --target <target_dir>
  remotely-agent install-curseforge-zip --zip <zip_path> --project-id <project_id> --file-id <file_id>
  remotely-agent lifecycle-start --dir <instance_dir> --command <command>
  remotely-agent lifecycle-stop --dir <instance_dir> [--timeout <seconds>]
  remotely-agent lifecycle-send --dir <instance_dir> --command <command>
  remotely-agent lifecycle-status --dir <instance_dir>
  remotely-agent lifecycle-console --dir <instance_dir>
  remotely-agent serve --root <id=path> [--write-root <id>] [--execute-root <id>] [--archive-root <id>] [--origin <origin>] [--token-file <path>]
  remotely-agent relay --endpoint <wss://...> [--pairing-code <code>] --root <id=path> [--write-root <id>] [--execute-root <id>] [--archive-root <id>] [--credential-file <path>]
 `)
}

func cmdIndex(args []string) {
	fs := flag.NewFlagSet("index", flag.ExitOnError)
	dir := fs.String("dir", "", "Instance directory")
	out := fs.String("out", "", "Output JSON path")
	format := fs.String("format", "json", "Output format: json|ndjson")
	withMeta := fs.Bool("with-meta", false, "Parse metadata from archives")
	withFP := fs.Bool("with-fp", false, "Compute Murmur2 fingerprints")
	withIcons := fs.Bool("with-icons", false, "Extract icons (base64)")
	_ = fs.Parse(args)

	if *dir == "" || *out == "" {
		fmt.Fprintln(os.Stderr, "index: --dir and --out are required")
		os.Exit(2)
	}
	idx, err := buildIndex(*dir, *withMeta, *withFP, *withIcons)
	if err != nil {
		fmt.Fprintf(os.Stderr, "index error: %v\n", err)
		os.Exit(1)
	}
	tmp := *out + ".tmp"
	if err := writeIndex(idx, tmp, *format); err != nil {
		fmt.Fprintf(os.Stderr, "write error: %v\n", err)
		os.Exit(1)
	}
	if err := atomicReplace(tmp, *out); err != nil {
		fmt.Fprintf(os.Stderr, "atomic mv error: %v\n", err)
		os.Exit(1)
	}
	fmt.Println("OK")
}

func buildIndex(instanceDir string, withMeta, withFP, withIcons bool) (*RemoteIndex, error) {
	res := &RemoteIndex{
		GeneratedAt:  time.Now().Unix(),
		InstancePath: instanceDir,
	}
	targets := []struct {
		Rel  string
		Type string
	}{
		{"mods", "MOD"},
		{"plugins", "PLUGIN"},
		{"resourcepacks", "RESOURCE_PACK"},
		{"shaderpacks", "SHADER_PACK"},
		{"datapacks", "DATA_PACK"},
	}
	for _, t := range targets {
		dir := filepath.Join(instanceDir, t.Rel)
		fi, err := os.Stat(dir)
		if err != nil || !fi.IsDir() {
			continue
		}
		entries, _ := os.ReadDir(dir)
		for _, e := range entries {
			if e.IsDir() {
				continue
			}
			name := e.Name()
			lower := strings.ToLower(name)
			if !(strings.HasSuffix(lower, ".jar") || strings.HasSuffix(lower, ".zip") || strings.HasSuffix(lower, ".disabled")) {
				continue
			}
			full := filepath.Join(dir, name)
			info, err := os.Stat(full)
			if err != nil {
				continue
			}
			enabled := 1
			if strings.HasSuffix(lower, ".disabled") {
				enabled = 0
			}
			item := RemoteResource{
				Path:     full,
				Type:     t.Type,
				FileName: name,
				Size:     info.Size(),
				Mtime:    info.ModTime().Unix(),
				Enabled:  enabled,
			}

			if h, err := sha1File(full); err == nil {
				item.Hash = h
			}

			if withFP && (strings.HasSuffix(lower, ".jar") || strings.HasSuffix(lower, ".zip")) {
				if fp, err := murmur2File(full); err == nil {
					item.Murmur2 = &fp
				}
			}

			if withMeta && (strings.HasSuffix(lower, ".jar") || strings.HasSuffix(lower, ".zip")) {
				readArchiveMeta(full, &item, withIcons)
			}

			if withMeta && (t.Type == "RESOURCE_PACK" || t.Type == "SHADER_PACK" || t.Type == "DATA_PACK") && (strings.HasSuffix(lower, ".zip")) {
				readPackMeta(full, &item, withIcons)
			}
			res.Resources = append(res.Resources, item)
		}
	}
	return res, nil
}

func writeIndex(idx *RemoteIndex, outPath, format string) error {
	if err := os.MkdirAll(filepath.Dir(outPath), 0o755); err != nil {
		return err
	}
	f, err := os.Create(outPath)
	if err != nil {
		return err
	}
	defer f.Close()
	if format == "ndjson" {
		for _, r := range idx.Resources {
			b, _ := json.Marshal(r)
			if _, err := f.Write(append(b, '\n')); err != nil {
				return err
			}
		}
	} else {
		enc := json.NewEncoder(f)
		enc.SetEscapeHTML(false)
		if err := enc.Encode(idx); err != nil {
			return err
		}
	}
	if err := f.Sync(); err != nil {
		return err
	}
	return nil
}

func atomicReplace(tmp, final string) error {
	return os.Rename(tmp, final)
}

func cmdFetch(args []string) {
	fs := flag.NewFlagSet("fetch", flag.ExitOnError)
	manifestPath := fs.String("manifest", "", "Manifest JSON")
	_ = fs.Parse(args)
	if *manifestPath == "" {
		fmt.Fprintln(os.Stderr, "fetch: --manifest is required")
		os.Exit(2)
	}
	var mf FetchManifest
	b, err := os.ReadFile(*manifestPath)
	if err != nil {
		fmt.Fprintf(os.Stderr, "read manifest: %v\n", err)
		os.Exit(1)
	}
	if err := json.Unmarshal(b, &mf); err != nil {
		fmt.Fprintf(os.Stderr, "manifest json: %v\n", err)
		os.Exit(1)
	}
	if mf.Concurrency <= 0 {
		mf.Concurrency = 4
	}
	if mf.Retries < 0 {
		mf.Retries = 2
	}
	if mf.TimeoutSec <= 0 {
		mf.TimeoutSec = 120
	}
	err = fetchAll(mf)
	if err != nil {
		fmt.Fprintf(os.Stderr, "fetch failed: %v\n", err)
		os.Exit(1)
	}
	fmt.Println("Done")
}

func cmdModpackInstall(args []string) {
	fs := flag.NewFlagSet("modpack-install", flag.ExitOnError)
	specPath := fs.String("spec", "", "Spec JSON")
	_ = fs.Parse(args)

	if *specPath == "" {
		fmt.Fprintln(os.Stderr, "modpack-install: --spec is required")
		os.Exit(2)
	}
	var spec ModpackSpec
	b, err := os.ReadFile(*specPath)
	if err != nil {
		fmt.Fprintf(os.Stderr, "read spec: %v\n", err)
		os.Exit(1)
	}
	if err := json.Unmarshal(b, &spec); err != nil {
		fmt.Fprintf(os.Stderr, "spec json: %v\n", err)
		os.Exit(1)
	}
	if spec.Concurrency <= 0 {
		spec.Concurrency = 4
	}
	if spec.Retries < 0 {
		spec.Retries = 2
	}
	if spec.TimeoutSec <= 0 {
		spec.TimeoutSec = 180
	}

	if strings.TrimSpace(spec.MrpackPath) != "" {
		if err := processMrpack(spec.MrpackPath, &spec); err != nil {
			fmt.Fprintf(os.Stderr, "process mrpack failed: %v\n", err)
			os.Exit(1)
		}
	}

	if strings.TrimSpace(spec.CurseForgeZipPath) != "" {
		if err := processCurseForgeZip(spec.CurseForgeZipPath, &spec); err != nil {
			fmt.Fprintf(os.Stderr, "process curseforge zip failed: %v\n", err)
			os.Exit(1)
		}
	}

	if strings.TrimSpace(spec.PreconfiguredPackPath) != "" {
		if err := processPreconfiguredPack(spec.PreconfiguredPackPath, &spec); err != nil {
			fmt.Fprintf(os.Stderr, "process preconfigured pack failed: %v\n", err)
			os.Exit(1)
		}
	}

	mf := FetchManifest{
		Items:       spec.Files,
		Concurrency: spec.Concurrency,
		Retries:     spec.Retries,
		TimeoutSec:  spec.TimeoutSec,
	}
	if err := validateModpackFetchItems(spec.TargetDir, mf.Items); err != nil {
		fmt.Fprintf(os.Stderr, "invalid download destination: %v\n", err)
		os.Exit(1)
	}
	if err := fetchAll(mf); err != nil {
		fmt.Fprintf(os.Stderr, "downloads failed: %v\n", err)
		os.Exit(1)
	}

	if strings.TrimSpace(spec.OverridesTar) != "" {
		if err := extractTarGz(spec.OverridesTar, spec.TargetDir); err != nil {
			fmt.Fprintf(os.Stderr, "extract overrides failed: %v\n", err)
			os.Exit(1)
		}
	}
	fmt.Println("Complete")
}

func cmdExtractCurseforgeManifest(args []string) {
	fs := flag.NewFlagSet("extract-curseforge-manifest", flag.ExitOnError)
	manifestPath := fs.String("manifest", "", "Manifest .zip file")
	_ = fs.Parse(args)

	if *manifestPath == "" {
		fmt.Fprintln(os.Stderr, "extract-curseforge-manifest: --manifest is required")
		os.Exit(2)
	}

	z, err := zip.OpenReader(*manifestPath)
	if err != nil {
		fmt.Fprintf(os.Stderr, "failed to open zip: %v\n", err)
		os.Exit(1)
	}
	defer z.Close()

	for _, f := range z.File {
		if f.Name == "manifest.json" {
			rc, err := f.Open()
			if err != nil {
				fmt.Fprintf(os.Stderr, "failed to open manifest.json: %v\n", err)
				os.Exit(1)
			}
			manifestData, err := io.ReadAll(rc)
			rc.Close()
			if err != nil {
				fmt.Fprintf(os.Stderr, "failed to read manifest.json: %v\n", err)
				os.Exit(1)
			}
			fmt.Println(string(manifestData))
			return
		}
	}
	fmt.Fprintln(os.Stderr, "manifest.json not found in zip")
	os.Exit(1)
}

func cmdCheckZipType(args []string) {
	fs := flag.NewFlagSet("check-zip-type", flag.ExitOnError)
	zipPath := fs.String("zip", "", "Zip file to check")
	_ = fs.Parse(args)

	if *zipPath == "" {
		fmt.Fprintln(os.Stderr, "check-zip-type: --zip is required")
		os.Exit(2)
	}

	z, err := zip.OpenReader(*zipPath)
	if err != nil {
		fmt.Fprintln(os.Stderr, "Error:", err)
		os.Exit(1)
	}
	defer z.Close()

	hasManifest := false
	hasServerFiles := false
	hasFTBInstaller := false

	startScripts := []string{"run.sh", "install.sh", "start.sh", "run.bat", "install.bat", "start.bat"}
	dirsToCheck := []string{"mods/", "config/", "libraries/", "serverpack/"}

	for _, f := range z.File {
		if f.Name == "manifest.json" {
			hasManifest = true
		}
		if zipFileContainsFTBInstallerReference(f) {
			hasFTBInstaller = true
		}

		for _, script := range startScripts {
			if f.Name == script {
				hasServerFiles = true
			}
		}

		if f.Name == "server.jar" || strings.HasSuffix(f.Name, ".jar") {
			if !strings.Contains(f.Name, "libraries/") && !strings.Contains(f.Name, "versions/") {
				hasServerFiles = true
			}
		}

		for _, dir := range dirsToCheck {
			if strings.HasPrefix(f.Name, dir) {
				hasServerFiles = true
			}
		}
	}

	if hasFTBInstaller {
		fmt.Println("ftb-preconfigured")
	} else if hasManifest {
		fmt.Println("manifest")
	} else if hasServerFiles {
		fmt.Println("preconfigured")
	} else {
		fmt.Println("unknown")
	}
}

func cmdFlattenZip(args []string) {
	fs := flag.NewFlagSet("flatten-zip", flag.ExitOnError)
	zipPath := fs.String("zip", "", "Zip file to extract and flatten")
	targetDir := fs.String("target", "", "Target directory")
	_ = fs.Parse(args)

	if *zipPath == "" || *targetDir == "" {
		fmt.Fprintln(os.Stderr, "flatten-zip: --zip and --target are required")
		os.Exit(2)
	}

	z, err := zip.OpenReader(*zipPath)
	if err != nil {
		fmt.Fprintf(os.Stderr, "failed to open zip: %v\n", err)
		os.Exit(1)
	}
	defer z.Close()
	if err := os.MkdirAll(*targetDir, 0o755); err != nil {
		fmt.Fprintf(os.Stderr, "failed to create target directory: %v\n", err)
		os.Exit(1)
	}
	canonicalTarget, err := filepath.EvalSymlinks(*targetDir)
	if err != nil {
		fmt.Fprintf(os.Stderr, "failed to resolve target directory: %v\n", err)
		os.Exit(1)
	}
	var extractedBytes uint64

	for index, f := range z.File {
		if index >= agentLegacyArchiveMaxEntries || f.UncompressedSize64 > agentLegacyArchiveMaxBytes-extractedBytes {
			fmt.Fprintln(os.Stderr, "archive exceeds the supported extraction limit")
			os.Exit(1)
		}
		cleanName, pathErr := safeAgentArchivePath(f.Name)
		if pathErr != nil {
			fmt.Fprintf(os.Stderr, "unsafe archive path %s: %v\n", f.Name, pathErr)
			os.Exit(1)
		}
		if cleanName == "." || cleanName == "" {
			continue
		}

		destPath := filepath.Join(canonicalTarget, cleanName)
		if err := ensureAgentDestination(canonicalTarget, destPath); err != nil {
			fmt.Fprintf(os.Stderr, "unsafe archive destination %s: %v\n", f.Name, err)
			os.Exit(1)
		}

		if f.FileInfo().IsDir() {
			if err := os.MkdirAll(destPath, 0o755); err != nil {
				fmt.Fprintf(os.Stderr, "failed to create directory %s: %v\n", destPath, err)
				os.Exit(1)
			}
		} else {
			if f.Mode()&os.ModeType != 0 {
				fmt.Fprintf(os.Stderr, "unsupported archive entry %s\n", f.Name)
				os.Exit(1)
			}
			if err := os.MkdirAll(filepath.Dir(destPath), 0o755); err != nil {
				fmt.Fprintf(os.Stderr, "failed to create parent directory for %s: %v\n", destPath, err)
				os.Exit(1)
			}

			rc, err := f.Open()
			if err != nil {
				fmt.Fprintf(os.Stderr, "failed to open %s: %v\n", f.Name, err)
				os.Exit(1)
			}

			outFile, err := os.Create(destPath)
			if err != nil {
				rc.Close()
				fmt.Fprintf(os.Stderr, "failed to create %s: %v\n", destPath, err)
				os.Exit(1)
			}

			if _, err := io.Copy(outFile, rc); err != nil {
				outFile.Close()
				rc.Close()
				fmt.Fprintf(os.Stderr, "failed to write %s: %v\n", destPath, err)
				os.Exit(1)
			}

			if err := outFile.Close(); err != nil {
				rc.Close()
				fmt.Fprintf(os.Stderr, "failed to close %s: %v\n", destPath, err)
				os.Exit(1)
			}
			rc.Close()

			if err := os.Chmod(destPath, f.Mode()); err != nil {
				fmt.Fprintf(os.Stderr, "warning: failed to set permissions for %s: %v\n", destPath, err)
			}
			extractedBytes += f.UncompressedSize64
		}
	}

	fmt.Println("OK")
}

func processCurseForgeZip(zipPath string, spec *ModpackSpec) error {
	z, err := zip.OpenReader(zipPath)
	if err != nil {
		return err
	}
	defer z.Close()

	var overrideFiles []*zip.File
	var serverJarFile *zip.File

	for _, f := range z.File {
		switch f.Name {
		case "server.jar":
			serverJarFile = f
		default:
			if strings.HasPrefix(f.Name, "overrides/") && f.Name != "overrides/" {
				overrideFiles = append(overrideFiles, f)
			}
		}
	}

	if serverJarFile != nil {
		serverJarDest := filepath.Join(spec.TargetDir, "server.jar")
		if err := extractFileFromZip(z, serverJarFile, serverJarDest); err != nil {
			return fmt.Errorf("extract server.jar: %w", err)
		}
		spec.ServerJarPath = serverJarDest
	}

	if len(overrideFiles) > 0 {
		tmpOverrides := zipPath + ".overrides.tar.gz"
		if err := createOverridesTarGz(overrideFiles, tmpOverrides); err != nil {
			return err
		}
		spec.OverridesTar = tmpOverrides
	}

	return nil
}

func processPreconfiguredPack(zipPath string, spec *ModpackSpec) error {
	return processFlattenPack(zipPath, spec.TargetDir, true)
}

func processFlattenPack(zipPath string, targetDir string, executeInstallScript bool) error {
	z, err := zip.OpenReader(zipPath)
	if err != nil {
		return err
	}
	defer z.Close()
	if err := os.MkdirAll(targetDir, 0o755); err != nil {
		return err
	}
	canonicalTarget, err := filepath.EvalSymlinks(targetDir)
	if err != nil {
		return err
	}

	for _, f := range z.File {
		cleanName, err := safeAgentArchivePath(f.Name)
		if err != nil {
			return err
		}
		if cleanName == "." || cleanName == "" {
			continue
		}

		destPath := filepath.Join(canonicalTarget, cleanName)
		if err := ensureAgentDestination(canonicalTarget, destPath); err != nil {
			return err
		}

		if f.FileInfo().IsDir() {
			if err := os.MkdirAll(destPath, 0o755); err != nil {
				return fmt.Errorf("failed to create directory %s: %w", destPath, err)
			}
		} else {
			if err := os.MkdirAll(filepath.Dir(destPath), 0o755); err != nil {
				return fmt.Errorf("failed to create parent directory for %s: %w", destPath, err)
			}

			rc, err := f.Open()
			if err != nil {
				return fmt.Errorf("failed to open %s: %w", f.Name, err)
			}

			outFile, err := os.Create(destPath)
			if err != nil {
				rc.Close()
				return fmt.Errorf("failed to create %s: %w", destPath, err)
			}

			if _, err := io.Copy(outFile, rc); err != nil {
				outFile.Close()
				rc.Close()
				return fmt.Errorf("failed to write %s: %w", destPath, err)
			}

			if err := outFile.Close(); err != nil {
				rc.Close()
				return fmt.Errorf("failed to close %s: %w", destPath, err)
			}
			rc.Close()

			if err := os.Chmod(destPath, f.Mode()); err != nil {
				fmt.Fprintf(os.Stderr, "warning: failed to set permissions for %s: %v\n", destPath, err)
			}
		}
	}

	ftbBootstrap, err := findFTBInstallerBootstrap(targetDir)
	if err != nil {
		return err
	}
	if ftbBootstrap != "" {
		return installFTBServerPack(ftbBootstrap, targetDir)
	}

	startScripts := []string{"run.sh", "install.sh", "start.sh"}
	var scriptsFound []string
	for _, scriptName := range startScripts {
		scriptPath := filepath.Join(targetDir, scriptName)
		if _, err := os.Stat(scriptPath); err == nil {
			scriptsFound = append(scriptsFound, scriptName)
		}
	}

	for _, scriptName := range scriptsFound {
		scriptPath := filepath.Join(targetDir, scriptName)
		if executeInstallScript && scriptName == "install.sh" {
			fmt.Fprintf(os.Stderr, "Running install script: %s\n", scriptName)
			cmd := exec.Command("/bin/bash", scriptPath)
			cmd.Dir = targetDir
			if output, err := cmd.CombinedOutput(); err != nil {
				fmt.Fprintf(os.Stderr, "Warning: install script %s failed: %v\nOutput: %s\n", scriptName, err, string(output))
			}
		}
		if strings.HasSuffix(scriptName, ".sh") {
			if err := os.Chmod(scriptPath, 0o755); err != nil {
				fmt.Fprintf(os.Stderr, "warning: failed to make %s executable: %v\n", scriptName, err)
			}
		}
	}

	return nil
}

func zipFileContainsFTBInstallerReference(file *zip.File) bool {
	name := strings.ToLower(filepath.Base(filepath.ToSlash(file.Name)))
	if name != "install.sh" && name != "install.bat" {
		return false
	}
	rc, err := file.Open()
	if err != nil {
		return false
	}
	defer rc.Close()
	content, err := io.ReadAll(io.LimitReader(rc, 256*1024))
	if err != nil {
		return false
	}
	return containsFTBInstallerReference(string(content))
}

func containsFTBInstallerReference(content string) bool {
	normalized := strings.ToLower(content)
	return strings.Contains(normalized, "ftbteam/ftb-server-installer") || strings.Contains(normalized, "ftb-server-installer_") || strings.Contains(normalized, "ftb-server-")
}

func findFTBInstallerBootstrap(targetDir string) (string, error) {
	bootstrap := ""
	err := filepath.WalkDir(targetDir, func(path string, entry os.DirEntry, err error) error {
		if err != nil {
			return err
		}
		if entry.IsDir() {
			relative, relativeErr := filepath.Rel(targetDir, path)
			if relativeErr == nil && relative != "." && strings.Count(filepath.ToSlash(relative), "/") >= 2 {
				return filepath.SkipDir
			}
			return nil
		}
		if !strings.EqualFold(entry.Name(), "install.sh") {
			return nil
		}
		content, readErr := os.ReadFile(path)
		if readErr != nil {
			return readErr
		}
		if containsFTBInstallerReference(string(content)) {
			bootstrap = path
			return io.EOF
		}
		return nil
	})
	if err != nil && err != io.EOF {
		return "", err
	}
	return bootstrap, nil
}

func installFTBServerPack(bootstrapPath string, targetDir string) error {
	bootstrapDir := filepath.Dir(bootstrapPath)
	bootstrap := exec.Command("/bin/sh", "./"+filepath.Base(bootstrapPath))
	bootstrap.Dir = bootstrapDir
	if output, err := bootstrap.CombinedOutput(); err != nil {
		return fmt.Errorf("FTB installer bootstrap failed: %w\n%s", err, string(output))
	}

	entries, err := os.ReadDir(bootstrapDir)
	if err != nil {
		return err
	}
	installerPath := ""
	for _, entry := range entries {
		name := strings.ToLower(entry.Name())
		if !entry.IsDir() && strings.HasPrefix(name, "ftb-server-installer") && !strings.HasSuffix(name, ".log") {
			installerPath = filepath.Join(bootstrapDir, entry.Name())
			break
		}
	}
	if installerPath == "" {
		return fmt.Errorf("FTB server installer binary was not downloaded")
	}
	if err := os.Chmod(installerPath, 0o755); err != nil {
		return fmt.Errorf("make FTB server installer executable: %w", err)
	}

	installer := exec.Command(installerPath, "-auto", "-force", "-no-colours", "-dir", targetDir)
	installer.Dir = bootstrapDir
	installer.Stdout = os.Stdout
	installer.Stderr = os.Stderr
	if err := installer.Run(); err != nil {
		return fmt.Errorf("FTB server installer failed: %w", err)
	}

	startupPath := filepath.Join(targetDir, "run.sh")
	if info, err := os.Stat(startupPath); err != nil || info.IsDir() {
		return fmt.Errorf("FTB server installer completed without creating run.sh")
	}
	manifestPath := filepath.Join(targetDir, ".manifest.json")
	if info, err := os.Stat(manifestPath); err != nil || info.IsDir() {
		return fmt.Errorf("FTB server installer completed without creating .manifest.json")
	}
	if err := os.Chmod(startupPath, 0o755); err != nil {
		return fmt.Errorf("make FTB startup script executable: %w", err)
	}
	return nil
}

func fetchAll(mf FetchManifest) error {
	sem := make(chan struct{}, mf.Concurrency)
	var wg sync.WaitGroup
	errCh := make(chan error, len(mf.Items))
	client := &http.Client{Timeout: time.Duration(mf.TimeoutSec) * time.Second}
	for _, it := range mf.Items {
		it := it
		wg.Add(1)
		go func() {
			defer wg.Done()
			sem <- struct{}{}
			defer func() { <-sem }()
			var lastErr error
			for attempt := 0; attempt <= mf.Retries; attempt++ {
				if err := doFetchItem(client, it); err != nil {
					lastErr = err
					time.Sleep(time.Duration(300+attempt*200) * time.Millisecond)
					continue
				}
				lastErr = nil
				break
			}
			if lastErr != nil {
				errCh <- fmt.Errorf("download %s -> %s: %w", it.URL, it.Dest, lastErr)
			}
		}()
	}
	wg.Wait()
	close(errCh)
	if len(errCh) > 0 {
		var sb strings.Builder
		for e := range errCh {
			sb.WriteString(e.Error())
			sb.WriteByte('\n')
		}
		return fmt.Errorf("%s", sb.String())
	}
	return nil
}

func doFetchItem(client *http.Client, it FetchItem) error {
	if err := os.MkdirAll(filepath.Dir(it.Dest), 0o755); err != nil {
		return err
	}
	out, err := os.CreateTemp(filepath.Dir(it.Dest), ".remotely-fetch-*")
	if err != nil {
		return err
	}
	tmp := out.Name()
	defer os.Remove(tmp)
	resp, err := client.Get(it.URL)
	if err != nil {
		out.Close()
		_ = os.Remove(tmp)
		return err
	}
	if resp.StatusCode >= 400 {
		resp.Body.Close()
		out.Close()
		return fmt.Errorf("http %d", resp.StatusCode)
	}
	_, err = io.Copy(out, resp.Body)
	resp.Body.Close()
	if err != nil {
		out.Close()
		return err
	}
	if err := out.Sync(); err != nil {
		out.Close()
		return err
	}
	if err := out.Close(); err != nil {
		return err
	}
	if it.Sha1 != "" {
		sum, err := sha1File(tmp)
		if err != nil {
			return err
		}
		if !strings.EqualFold(sum, it.Sha1) {
			return fmt.Errorf("sha1 mismatch")
		}
	}
	return os.Rename(tmp, it.Dest)
}

func processMrpack(mrpackPath string, spec *ModpackSpec) error {
	z, err := zip.OpenReader(mrpackPath)
	if err != nil {
		return err
	}
	defer z.Close()

	var manifestData []byte
	var overridesFiles []*zip.File
	var serverJarFile *zip.File
	for _, f := range z.File {
		switch f.Name {
		case "modrinth.index.json":
			rc, err := f.Open()
			if err != nil {
				return err
			}
			manifestData, err = io.ReadAll(io.LimitReader(rc, agentManifestMaxBytes+1))
			rc.Close()
			if err != nil {
				return err
			}
			if len(manifestData) > agentManifestMaxBytes {
				return errors.New("modrinth manifest exceeds the supported limit")
			}
		case "server.jar":
			serverJarFile = f
		default:
			if strings.HasPrefix(f.Name, "overrides/") && f.Name != "overrides/" {
				overridesFiles = append(overridesFiles, f)
			}
		}
	}

	var manifest MrpackManifest
	if err := json.Unmarshal(manifestData, &manifest); err != nil {
		return err
	}

	if serverJarFile != nil {
		serverJarDest := filepath.Join(spec.TargetDir, "server.jar")
		if err := extractFileFromZip(z, serverJarFile, serverJarDest); err != nil {
			return fmt.Errorf("extract server.jar: %w", err)
		}
		spec.ServerJarPath = serverJarDest
	}

	if manifest.Dependencies != nil {
		if mcVer, ok := manifest.Dependencies["minecraft"]; ok {
			spec.McVersion = mcVer
		}
	}

	spec.Files = nil
	if err := os.MkdirAll(spec.TargetDir, 0o755); err != nil {
		return err
	}
	canonicalTarget, err := filepath.EvalSymlinks(spec.TargetDir)
	if err != nil {
		return err
	}
	destinations := make(map[string]struct{}, len(manifest.Files))
	for _, mf := range manifest.Files {
		if mf.Downloads != nil && len(mf.Downloads) > 0 {
			relative, pathErr := safeAgentArchivePath(mf.Path)
			if pathErr != nil || relative == "." {
				return fmt.Errorf("invalid modrinth file path %q", mf.Path)
			}
			destination := filepath.Join(canonicalTarget, relative)
			if err := ensureAgentDestination(canonicalTarget, destination); err != nil {
				return err
			}
			destinationKey := strings.ToLower(filepath.Clean(destination))
			if _, exists := destinations[destinationKey]; exists {
				return fmt.Errorf("duplicate modrinth file path %q", mf.Path)
			}
			destinations[destinationKey] = struct{}{}
			sha1 := ""
			if mf.Hashes != nil {
				sha1 = mf.Hashes["sha1"]
			}
			spec.Files = append(spec.Files, FetchItem{
				URL:  mf.Downloads[0],
				Dest: destination,
				Sha1: sha1,
			})
		}
	}

	if len(overridesFiles) > 0 {
		tmpOverrides := mrpackPath + ".overrides.tar.gz"
		if err := createOverridesTarGz(overridesFiles, tmpOverrides); err != nil {
			return err
		}
		spec.OverridesTar = tmpOverrides
	}

	result, _ := json.MarshalIndent(spec, "", "  ")
	fmt.Println(string(result))
	return nil
}

func validateModpackFetchItems(targetDir string, items []FetchItem) error {
	if targetDir == "" {
		return errors.New("target directory is required")
	}
	if err := os.MkdirAll(targetDir, 0o755); err != nil {
		return err
	}
	canonicalTarget, err := filepath.EvalSymlinks(targetDir)
	if err != nil {
		return err
	}
	destinations := make(map[string]struct{}, len(items))
	for index, item := range items {
		if item.Dest == "" {
			return errors.New("download destination is required")
		}
		destination := item.Dest
		if !filepath.IsAbs(destination) {
			destination = filepath.Join(canonicalTarget, destination)
		}
		destination, err = filepath.Abs(destination)
		if err != nil {
			return err
		}
		if !withinAgentRoot(canonicalTarget, destination) {
			return fmt.Errorf("download destination escapes the target directory: %s", item.Dest)
		}
		if err := ensureAgentDestination(canonicalTarget, destination); err != nil {
			return err
		}
		items[index].Dest = destination
		key := strings.ToLower(filepath.Clean(destination))
		if _, exists := destinations[key]; exists {
			return fmt.Errorf("duplicate download destination: %s", item.Dest)
		}
		destinations[key] = struct{}{}
	}
	return nil
}

type MrpackManifest struct {
	Files []struct {
		Path      string            `json:"path"`
		Hashes    map[string]string `json:"hashes"`
		Downloads []string          `json:"downloads"`
	} `json:"files"`
	Dependencies map[string]string `json:"dependencies"`
}

func extractFileFromZip(z *zip.ReadCloser, zf *zip.File, dest string) error {
	rc, err := zf.Open()
	if err != nil {
		return err
	}
	defer rc.Close()

	if err := os.MkdirAll(filepath.Dir(dest), 0o755); err != nil {
		return err
	}

	out, err := os.CreateTemp(filepath.Dir(dest), ".remotely-extract-*")
	if err != nil {
		return err
	}
	tmp := out.Name()
	defer os.Remove(tmp)

	if _, err := io.Copy(out, rc); err != nil {
		out.Close()
		return err
	}
	if err := out.Sync(); err != nil {
		out.Close()
		return err
	}
	if err := out.Close(); err != nil {
		return err
	}
	return os.Rename(tmp, dest)
}

func createOverridesTarGz(files []*zip.File, outPath string) error {
	f, err := os.Create(outPath)
	if err != nil {
		return err
	}
	defer f.Close()

	gzw := gzip.NewWriter(f)
	defer gzw.Close()

	tw := tar.NewWriter(gzw)
	defer tw.Close()

	for _, zf := range files {
		name := strings.TrimPrefix(zf.Name, "overrides/")
		var err error
		name, err = safeAgentArchivePath(name)
		if err != nil {
			return err
		}
		if name == "." || name == "" {
			continue
		}

		header, err := tar.FileInfoHeader(zf.FileInfo(), name)
		if err != nil {
			return err
		}
		header.Name = name

		if err := tw.WriteHeader(header); err != nil {
			return err
		}

		rc, err := zf.Open()
		if err != nil {
			return err
		}
		_, err = io.Copy(tw, rc)
		rc.Close()
		if err != nil {
			return err
		}
	}

	return nil
}

func extractTarGz(tarGzPath, destDir string) error {
	if err := os.MkdirAll(destDir, 0o755); err != nil {
		return err
	}
	canonicalDest, err := filepath.EvalSymlinks(destDir)
	if err != nil {
		return err
	}
	f, err := os.Open(tarGzPath)
	if err != nil {
		return err
	}
	defer f.Close()

	gzr, err := gzip.NewReader(f)
	if err != nil {
		return err
	}
	defer gzr.Close()

	tr := tar.NewReader(gzr)
	for {
		hdr, err := tr.Next()
		if err == io.EOF {
			break
		}
		if err != nil {
			return err
		}
		relative, err := safeAgentArchivePath(hdr.Name)
		if err != nil {
			return err
		}
		target := filepath.Join(canonicalDest, relative)
		if err := ensureAgentDestination(canonicalDest, target); err != nil {
			return err
		}
		switch hdr.Typeflag {
		case tar.TypeDir:
			if err := os.MkdirAll(target, 0o755); err != nil {
				return err
			}
		case tar.TypeReg:
			if hdr.Size < 0 {
				return errors.New("archive entry has an invalid size")
			}
			if err := os.MkdirAll(filepath.Dir(target), 0o755); err != nil {
				return err
			}
			out, err := os.Create(target)
			if err != nil {
				return err
			}
			if _, err := io.Copy(out, tr); err != nil {
				out.Close()
				return err
			}
			if err := out.Sync(); err != nil {
				return err
			}
			if err := out.Close(); err != nil {
				return err
			}
		default:
			return errors.New("archive contains unsupported link or special entry")
		}
	}
	return nil
}

func sha1File(p string) (string, error) {
	f, err := os.Open(p)
	if err != nil {
		return "", err
	}
	defer f.Close()
	h := sha1.New()
	if _, err := io.Copy(h, f); err != nil {
		return "", err
	}
	return fmt.Sprintf("%x", h.Sum(nil)), nil
}

func murmur2File(p string) (uint32, error) {
	b, err := os.ReadFile(p)
	if err != nil {
		return 0, err
	}
	return murmur2(b, 1), nil
}

func murmur2(data []byte, seed uint32) uint32 {
	const m uint32 = 0x5bd1e995
	const r uint32 = 24
	length := uint32(len(data))
	h := seed ^ length
	var i int
	for length >= 4 {
		k := uint32(data[i]) | uint32(data[i+1])<<8 | uint32(data[i+2])<<16 | uint32(data[i+3])<<24
		k *= m
		k ^= k >> r
		k *= m
		h *= m
		h ^= k
		i += 4
		length -= 4
	}
	switch length {
	case 3:
		h ^= uint32(data[i+2]) << 16
		fallthrough
	case 2:
		h ^= uint32(data[i+1]) << 8
		fallthrough
	case 1:
		h ^= uint32(data[i])
		h *= m
	}
	h ^= h >> 13
	h *= m
	h ^= h >> 15
	return h
}

func readArchiveMeta(path string, item *RemoteResource, withIcons bool) {
	r, err := zip.OpenReader(path)
	if err != nil {
		return
	}
	defer r.Close()

	var iconFound bool
	for _, f := range r.File {
		name := f.Name
		lower := strings.ToLower(name)
		switch {
		case lower == "plugin.yml" || lower == "bungee.yml":
			if rc, err := f.Open(); err == nil {
				parsePluginYAML(rc, item)
				rc.Close()
			}
		case lower == "fabric.mod.json":
			if rc, err := f.Open(); err == nil {
				parseFabricJSON(rc, item)
				rc.Close()
			}
		case lower == "meta-inf/mods.toml", lower == "meta-inf/neoforge.mods.toml":
			if rc, err := f.Open(); err == nil {
				parseModsTOML(rc, item)
				rc.Close()
			}
		}

		if withIcons && !iconFound {
			if lower == "pack.png" || strings.HasSuffix(lower, "/pack.png") || strings.HasSuffix(lower, "/icon.png") {
				if rc, err := f.Open(); err == nil {
					buf, _ := io.ReadAll(rc)
					rc.Close()
					if len(buf) > 0 {
						item.IconBase64 = base64.StdEncoding.EncodeToString(buf)
						iconFound = true
					}
				}
			}
		}
	}
}

func parsePluginYAML(r io.Reader, item *RemoteResource) {
	var m map[string]interface{}
	dec := yaml.NewDecoder(r)
	if err := dec.Decode(&m); err != nil {
		return
	}
	if v, ok := m["name"].(string); ok && item.Name == "" {
		item.Name = v
	}
	if v, ok := m["version"].(string); ok && item.Version == "" {
		item.Version = v
	}
	if v, ok := m["description"].(string); ok && item.Description == "" {
		item.Description = v
	}
	if v, ok := m["authors"]; ok {
		switch vv := v.(type) {
		case []interface{}:
			var a []string
			for _, el := range vv {
				if s, ok := el.(string); ok {
					a = append(a, s)
				}
			}
			if len(a) > 0 {
				item.Authors = a
			}
		case string:
			item.Authors = []string{vv}
		}
	}
}

func parseFabricJSON(r io.Reader, item *RemoteResource) {
	var m map[string]interface{}
	dec := json.NewDecoder(r)
	if err := dec.Decode(&m); err != nil {
		return
	}
	if v, ok := m["name"].(string); ok && item.Name == "" {
		item.Name = v
	}
	if v, ok := m["version"].(string); ok && item.Version == "" {
		item.Version = v
	}
	if v, ok := m["description"].(string); ok && item.Description == "" {
		item.Description = v
	}
	if v, ok := m["authors"]; ok {
		switch vv := v.(type) {
		case []interface{}:
			var a []string
			for _, el := range vv {
				if s, ok := el.(string); ok {
					a = append(a, s)
				} else if obj, ok := el.(map[string]interface{}); ok {
					if n, ok := obj["name"].(string); ok {
						a = append(a, n)
					}
				}
			}
			if len(a) > 0 {
				item.Authors = a
			}
		case string:
			item.Authors = []string{vv}
		}
	}
	if v, ok := m["depends"].(map[string]interface{}); ok {
		if mc, ok := v["minecraft"]; ok {
			switch mm := mc.(type) {
			case string:
				item.McVersions = mm
			default:
				b, _ := json.Marshal(mm)
				item.McVersions = string(b)
			}
		}
	}

	if v, ok := m["icon"].(string); ok {

		_ = v
	}
}

func parseModsTOML(r io.Reader, item *RemoteResource) {
	var doc map[string]interface{}
	if _, err := toml.NewDecoder(r).Decode(&doc); err != nil {
		return
	}
	mods, ok := doc["mods"]
	if ok {
		switch arr := mods.(type) {
		case []interface{}:
			if len(arr) > 0 {
				if first, ok := arr[0].(map[string]interface{}); ok {
					if n, ok := first["displayName"].(string); ok && item.Name == "" {
						item.Name = n
					}
					if v, ok := first["version"].(string); ok && v != "${file.jarVersion}" && item.Version == "" {
						item.Version = v
					}
					if d, ok := first["description"].(string); ok && item.Description == "" {
						item.Description = d
					}
					if a, ok := first["authors"].(string); ok && len(item.Authors) == 0 {
						item.Authors = []string{a}
					}
				}
			}
		}
	}

}

func readPackMeta(path string, item *RemoteResource, withIcons bool) {
	r, err := zip.OpenReader(path)
	if err != nil {
		return
	}
	defer r.Close()
	for _, f := range r.File {
		lower := strings.ToLower(f.Name)
		if lower == "pack.mcmeta" {
			if rc, err := f.Open(); err == nil {
				var m map[string]interface{}
				_ = json.NewDecoder(rc).Decode(&m)
				rc.Close()
				if p, ok := m["pack"].(map[string]interface{}); ok {
					if d, ok := p["description"].(string); ok && item.Description == "" {
						item.Description = d
					}
				}
			}
		}
		if withIcons && (lower == "pack.png" || strings.HasSuffix(lower, "/pack.png")) {
			if rc, err := f.Open(); err == nil {
				buf, _ := io.ReadAll(rc)
				rc.Close()
				if len(buf) > 0 {
					item.IconBase64 = base64.StdEncoding.EncodeToString(buf)
				}
			}
		}
	}
}
