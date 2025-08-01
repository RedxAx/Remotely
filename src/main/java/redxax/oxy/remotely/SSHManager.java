package redxax.oxy.remotely;

import com.jcraft.jsch.*;
import redxax.oxy.remotely.resources.IRemotelyResource;
import redxax.oxy.remotely.servers.RemoteHostInfo;
import redxax.oxy.remotely.servers.ServerInfo;
import redxax.oxy.remotely.servers.ServerState;
import redxax.oxy.remotely.ui.widgets.TerminalWidget;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;

import static redxax.oxy.remotely.util.DevUtil.devPrint;

public class SSHManager {
    private RemoteHostInfo remoteHost;
    private Session sshSession;
    private ChannelShell sshChannel;
    private BufferedReader sshReader;
    private Writer sshWriter;
    private boolean isSSH = false;
    private boolean awaitingPassword = false;
    private TerminalWidget terminalWidget;
    private final ExecutorService executorService = Executors.newFixedThreadPool(2);
    public final ExecutorService sftpExecutor = Executors.newSingleThreadExecutor();
    public ChannelSftp sftpChannel;
    private boolean sftpConnected = false;
    private List<String> remoteCommandsCache = new ArrayList<>();
    private long remoteCommandsLastFetched = 0;
    private static final long REMOTE_COMMANDS_CACHE_DURATION = 60000;
    private volatile boolean readingSSHOutput = false;
    private static final int CONNECTION_TIMEOUT = 10000;
    private static final int OPERATION_TIMEOUT = 30000;
    private final ScheduledExecutorService connectionMonitor = Executors.newSingleThreadScheduledExecutor();
    private ScheduledFuture<?> monitorTask;
    private final Map<String, CachedDirectoryListing> directoryCache = new ConcurrentHashMap<>();
    private static final long DIRECTORY_CACHE_DURATION = 30000;

    private static class CachedDirectoryListing {
        final List<ChannelSftp.LsEntry> entries;
        final long timestamp;

        CachedDirectoryListing(List<ChannelSftp.LsEntry> entries) {
            this.entries = entries;
            this.timestamp = System.currentTimeMillis();
        }

        boolean isExpired() {
            return System.currentTimeMillis() - timestamp > DIRECTORY_CACHE_DURATION;
        }
    }

    public SSHManager(TerminalWidget terminalWidget) {
        this.terminalWidget = terminalWidget;
        this.remoteHost = null;
    }

    public SSHManager(RemoteHostInfo remoteHost) {
        this.remoteHost = remoteHost;
    }

    public void setTerminalWidget(TerminalWidget terminalWidget) {
        this.terminalWidget = terminalWidget;
    }

    public void connectToRemoteHost(String user, String host, int port, String password) {
        try {
            if (sshSession != null && sshSession.isConnected()) return;

            JSch jsch = new JSch();
            sshSession = jsch.getSession(user, host, port);
            sshSession.setConfig("StrictHostKeyChecking", "no");
            sshSession.setConfig("ServerAliveInterval", "30");
            sshSession.setConfig("ServerAliveCountMax", "5");
            sshSession.setPassword(password);
            sshSession.connect(CONNECTION_TIMEOUT);
            isSSH = true;
            connectSFTP();

            startConnectionMonitor();
        } catch (Exception e) {
            if (terminalWidget != null) {
                terminalWidget.appendOutput("SSH connection failed: " + e.getMessage() + "\n");
            }
            isSSH = false;
        }
    }

    private void startConnectionMonitor() {
        if (monitorTask != null && !monitorTask.isDone()) {
            monitorTask.cancel(false);
        }

        monitorTask = connectionMonitor.scheduleAtFixedRate(() -> {
            try {
                if (remoteHost == null) return;

                if (sshSession == null || !sshSession.isConnected()) {
                    devPrint("SSH connection lost. Attempting to reconnect...");
                    reconnect();
                }

                if (isSSH && (sftpChannel == null || !sftpChannel.isConnected())) {
                    devPrint("SFTP connection lost. Attempting to reconnect...");
                    connectSFTP();
                }
            } catch (Exception e) {
                devPrint("Error in connection monitor: " + e.getMessage());
            }
        }, 30, 30, TimeUnit.SECONDS);
    }

    private void reconnect() {
        if (remoteHost == null) return;

        try {
            if (sftpChannel != null && sftpChannel.isConnected()) {
                sftpChannel.disconnect();
            }
            if (sshSession != null && sshSession.isConnected()) {
                sshSession.disconnect();
            }

            JSch jsch = new JSch();
            sshSession = jsch.getSession(remoteHost.getUser(), remoteHost.getIp(), remoteHost.getPort());
            sshSession.setConfig("StrictHostKeyChecking", "no");
            sshSession.setConfig("ServerAliveInterval", "30");
            sshSession.setConfig("ServerAliveCountMax", "5");
            sshSession.setPassword(remoteHost.getPassword());
            sshSession.connect(CONNECTION_TIMEOUT);
            isSSH = true;
            connectSFTP();
            devPrint("Successfully reconnected to " + remoteHost.getIp());
        } catch (Exception e) {
            devPrint("Reconnection failed: " + e.getMessage());
            isSSH = false;
        }
    }

    public void connectSFTP() {
        if (sshSession == null || !sshSession.isConnected()) return;
        sftpExecutor.submit(() -> {
            try {
                Channel channel = sshSession.openChannel("sftp");
                channel.connect();
                sftpChannel = (ChannelSftp) channel;
                sftpConnected = true;
            } catch (Exception e) {
                if (terminalWidget != null) {
                    terminalWidget.appendOutput("SFTP connection failed: " + e.getMessage() + "\n");
                }
                sftpConnected = false;
            }
        });
    }

    public boolean isSFTPConnected() {
        return sftpChannel != null && sftpChannel.isConnected();
    }

    public void launchRemoteServer(String serverPath) {
        if (!isSSH() || sshSession == null || !sshSession.isConnected()) {
            if (terminalWidget != null) {
                terminalWidget.appendOutput("SSH not connected. Cannot launch remote server.\n");
            }
            return;
        }
        executorService.submit(() -> {
            try {
                ensureTmuxInstalled();
                String sessionName = "remotely_server_" + remoteHost.getIp().replace('.', '_') + "_" + serverPath.hashCode();

                if (!isTmuxSessionRunning(sessionName)) {
                    devPrint("Tmux session " + sessionName + " not found. Creating...");
                    String startScriptPath = serverPath + "/start.sh";
                    String command = "cd " + serverPath + " && ./start.sh";
                    createTmuxSession(sessionName, command);
                    if (terminalWidget.getServerInfo() != null) {
                        terminalWidget.getServerInfo().state = ServerState.STARTING;
                    }
                } else {
                    devPrint("Attaching to existing tmux session: " + sessionName);
                }

                attachToTmuxSession(sessionName);

            } catch (Exception e) {
                if (terminalWidget != null) {
                    terminalWidget.appendOutput("Failed to start or attach to remote server session: " + e.getMessage() + "\n");
                }
                if (terminalWidget.getServerInfo() != null) {
                    terminalWidget.getServerInfo().state = ServerState.CRASHED;
                }
            }
        });
    }

    private boolean isTmuxSessionRunning(String sessionName) throws JSchException, InterruptedException {
        ChannelExec channel = (ChannelExec) sshSession.openChannel("exec");
        channel.setCommand("tmux has-session -t " + sessionName);
        channel.connect();
        while (channel.isConnected()) {
            Thread.sleep(100);
        }
        int exitStatus = channel.getExitStatus();
        channel.disconnect();
        return exitStatus == 0;
    }

    private void createTmuxSession(String sessionName, String command) throws JSchException, InterruptedException {
        ChannelExec channel = (ChannelExec) sshSession.openChannel("exec");
        channel.setCommand("tmux new-session -d -s " + sessionName + " \"" + command + "\"");
        channel.connect();
        while (channel.isConnected()) {
            Thread.sleep(100);
        }
        channel.disconnect();
    }

    private void attachToTmuxSession(String sessionName) throws JSchException, IOException {
        if (sshChannel != null && sshChannel.isConnected()) {
            sshChannel.disconnect();
        }
        sshChannel = (ChannelShell) sshSession.openChannel("shell");
        sshChannel.setPty(true);
        sshChannel.connect();

        sshWriter = new OutputStreamWriter(sshChannel.getOutputStream(), StandardCharsets.UTF_8);
        sshReader = new BufferedReader(new InputStreamReader(sshChannel.getInputStream(), StandardCharsets.UTF_8));

        sshWriter.write("tmux attach-session -t " + sessionName + "\n");
        sshWriter.flush();

        readSSHOutput();
    }


    private void ensureTmuxInstalled() throws Exception {
        ChannelExec exec = (ChannelExec) sshSession.openChannel("exec");
        exec.setCommand("command -v tmux");
        exec.connect();
        while (exec.isConnected()) {
            Thread.sleep(100);
        }
        int status = exec.getExitStatus();
        exec.disconnect();

        if (status != 0) {
            terminalWidget.appendOutput("tmux not found. Attempting to install...\n");
            ChannelExec installChannel = (ChannelExec) sshSession.openChannel("exec");
            // This is a common command, but might fail on non-debian systems.
            String installCommand = "sudo apt-get update && sudo apt-get install -y tmux";
            installChannel.setCommand(installCommand);
            installChannel.connect();
            while (installChannel.isConnected()) {
                Thread.sleep(100);
            }
            if(installChannel.getExitStatus() != 0) {
                terminalWidget.appendOutput("Failed to install tmux automatically. Please install it on the remote host.\n");
            } else {
                terminalWidget.appendOutput("tmux installed successfully.\n");
            }
            installChannel.disconnect();
        }
    }

    private void readSSHOutput() {
        if (readingSSHOutput) return;
        readingSSHOutput = true;
        executorService.submit(() -> {
            try {
                isSSH = true;
                String line;
                while (isSSH && sshReader != null && (line = sshReader.readLine()) != null) {
                    if (terminalWidget != null) {
                        terminalWidget.appendOutput(line + "\n");
                    }
                }
            } catch (IOException e) {
                if (isSSH && terminalWidget != null) { // only log if we expect to be connected
                    terminalWidget.appendOutput("Error reading SSH output: " + e.getMessage() + "\n");
                }
            } finally {
                readingSSHOutput = false;
            }
        });
    }

    public void startSSHConnection(String command) {
        executorService.submit(() -> {
            try {
                if (terminalWidget != null) {
                    terminalWidget.appendOutput("Connecting...\n");
                }
                String[] parts = command.split(" ");
                if (parts.length < 2) {
                    if (terminalWidget != null) {
                        terminalWidget.appendOutput("Usage: ssh user@host[:port]\n");
                    }
                    return;
                }
                String userHost = parts[1];
                String[] userHostParts = userHost.split("@");
                if (userHostParts.length != 2) {
                    if (terminalWidget != null) {
                        terminalWidget.appendOutput("Invalid SSH command.\n");
                    }
                    return;
                }
                String user = userHostParts[0];
                String hostPort = userHostParts[1];
                String host;
                int port = 22;
                if (hostPort.contains(":")) {
                    String[] hostPortParts = hostPort.split(":");
                    host = hostPortParts[0];
                    try {
                        port = Integer.parseInt(hostPortParts[1]);
                    } catch (NumberFormatException e) {
                        if (terminalWidget != null) {
                            terminalWidget.appendOutput("Invalid port. Using 22.\n");
                        }
                    }
                } else {
                    host = hostPort;
                }
                JSch jsch = new JSch();
                sshSession = jsch.getSession(user, host, port);
                sshSession.setConfig("StrictHostKeyChecking", "no");
                awaitingPassword = true;
                if (terminalWidget != null) {
                    terminalWidget.appendOutput("Password: ");
                }
            } catch (Exception e) {
                if (terminalWidget != null) {
                    terminalWidget.appendOutput("SSH connection failed: " + e.getMessage() + "\n");
                }
            }
        });
    }

    public void connectSSHWithPassword(String password) {
        executorService.submit(() -> {
            try {
                sshSession.setPassword(password);
                sshSession.connect(10000);
                sshChannel = (ChannelShell) sshSession.openChannel("shell");
                sshChannel.setPty(true);
                sshChannel.connect();
                sshReader = new BufferedReader(new InputStreamReader(sshChannel.getInputStream(), StandardCharsets.UTF_8));
                sshWriter = new OutputStreamWriter(sshChannel.getOutputStream(), StandardCharsets.UTF_8);
                isSSH = true;
                awaitingPassword = false;
                connectSFTP();
                readSSHOutput();
                if (terminalWidget != null) {
                    terminalWidget.appendOutput("Connected.\n");
                }
            } catch (Exception e) {
                if (terminalWidget != null) {
                    terminalWidget.appendOutput("SSH connection failed: " + e.getMessage() + "\n");
                }
                isSSH = false;
                if (sshSession != null && sshSession.isConnected()) {
                    sshSession.disconnect();
                }
            }
        });
    }

    public void shutdown() {
        try {
            if (monitorTask != null && !monitorTask.isDone()) {
                monitorTask.cancel(true);
            }
            connectionMonitor.shutdownNow();

            directoryCache.clear();

            if (sftpChannel != null && sftpChannel.isConnected()) {
                sftpChannel.disconnect();
            }
            sftpConnected = false;

            if (sshChannel != null && sshChannel.isConnected()) {
                sshChannel.disconnect();
            }

            if (sshSession != null && sshSession.isConnected()) {
                sshSession.disconnect();
            }

            isSSH = false;
            awaitingPassword = false;

            executorService.shutdownNow();
            sftpExecutor.shutdownNow();

            devPrint("SSH connection shutdown completed");
        } catch (Exception e) {
            devPrint("Error during SSH shutdown: " + e.getMessage());
        }
    }

    public boolean isSSH() {
        return sshSession != null && sshSession.isConnected();
    }

    public boolean isAwaitingPassword() {
        return awaitingPassword;
    }

    public Writer getSshWriter() {
        return sshWriter;
    }

    public boolean isRemoteDirectory(String path) {
        if (!sftpConnected) return false;
        try {
            return sftpExecutor.submit(() -> {
                SftpATTRS attrs = sftpChannel.stat(path);
                return attrs.isDir();
            }).get();
        } catch (Exception e) {
            return false;
        }
    }

    public List<String> listRemoteDirectory(String dir) throws Exception {
        if (!sftpConnected) {
            connectSFTPSync();
            if (!sftpConnected) {
                return Collections.emptyList();
            }
        }

        String cacheKey = dir;
        CachedDirectoryListing cachedListing = directoryCache.get(cacheKey);
        if (cachedListing != null && !cachedListing.isExpired()) {
            return cachedListing.entries.stream()
                    .map(ChannelSftp.LsEntry::getFilename)
                    .filter(name -> !name.equals(".") && !name.equals(".."))
                    .collect(Collectors.toList());
        }

        Future<Vector<ChannelSftp.LsEntry>> future = sftpExecutor.submit(() -> sftpChannel.ls(dir));

        try {
            Vector<ChannelSftp.LsEntry> entries = future.get(OPERATION_TIMEOUT, TimeUnit.MILLISECONDS);

            directoryCache.put(cacheKey, new CachedDirectoryListing(new ArrayList<>(entries)));

            return entries.stream()
                    .map(ChannelSftp.LsEntry::getFilename)
                    .filter(name -> !name.equals(".") && !name.equals(".."))
                    .collect(Collectors.toList());
        } catch (TimeoutException e) {
            future.cancel(true);
            devPrint("SFTP listing operation timed out for: " + dir);
            throw new Exception("Operation timed out");
        } catch (Exception e) {
            devPrint("Error listing remote directory: " + e.getMessage());
            throw e;
        }
    }

    public Map<String, Boolean> listRemoteDirectoryWithTypes(String dir) throws Exception {
        if (!sftpConnected) {
            connectSFTPSync();
            if (!sftpConnected) {
                return Collections.emptyMap();
            }
        }

        String cacheKey = dir;
        CachedDirectoryListing cachedListing = directoryCache.get(cacheKey);
        if (cachedListing != null && !cachedListing.isExpired()) {
            return cachedListing.entries.stream()
                    .filter(entry -> !entry.getFilename().equals(".") && !entry.getFilename().equals(".."))
                    .collect(Collectors.toMap(
                            ChannelSftp.LsEntry::getFilename,
                            entry -> entry.getAttrs().isDir()
                    ));
        }

        Future<Vector<ChannelSftp.LsEntry>> future = sftpExecutor.submit(() -> sftpChannel.ls(dir));

        try {
            Vector<ChannelSftp.LsEntry> entries = future.get(OPERATION_TIMEOUT, TimeUnit.MILLISECONDS);

            directoryCache.put(cacheKey, new CachedDirectoryListing(new ArrayList<>(entries)));

            return entries.stream()
                    .filter(entry -> !entry.getFilename().equals(".") && !entry.getFilename().equals(".."))
                    .collect(Collectors.toMap(
                            ChannelSftp.LsEntry::getFilename,
                            entry -> entry.getAttrs().isDir()
                    ));
        } catch (TimeoutException e) {
            future.cancel(true);
            devPrint("SFTP listing operation timed out for: " + dir);
            throw new Exception("Operation timed out");
        } catch (Exception e) {
            devPrint("Error listing remote directory with types: " + e.getMessage());
            throw e;
        }
    }

    public void renameRemote(String source, String dest) throws Exception {
        sftpChannel.rename(source, dest);
    }

    public void upload(Path local, String remote) throws Exception {
        if (Files.isDirectory(local)) {
            uploadDirectory(local, remote);
        } else {
            sftpChannel.put(local.toString(), remote);
        }
    }

    private void uploadDirectory(Path localDir, String remoteDir) throws Exception {
        try {
            sftpChannel.stat(remoteDir);
        } catch (SftpException e) {
            sftpChannel.mkdir(remoteDir);
        }
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(localDir)) {
            for (Path entry : stream) {
                String entryRemote = remoteDir + "/" + entry.getFileName().toString().replace("\\", "/");
                if (Files.isDirectory(entry)) {
                    uploadDirectory(entry, entryRemote);
                } else {
                    sftpChannel.put(entry.toString(), entryRemote);
                }
            }
        }
    }

    public void download(String remote, Path local) throws Exception {
        if (isRemoteDirectory(remote)) {
            downloadDirectory(remote, local);
        } else {
            Files.createDirectories(local.getParent());
            sftpChannel.get(remote, local.toString());
        }
    }

    private void downloadDirectory(String remoteDir, Path localDir) throws Exception {
        Files.createDirectories(localDir);
        Vector<ChannelSftp.LsEntry> list = sftpChannel.ls(remoteDir);
        for (ChannelSftp.LsEntry entry : list) {
            String name = entry.getFilename();
            if (".".equals(name) || "..".equals(name)) {
                continue;
            }
            String remotePath = remoteDir + "/" + name;
            Path localPath = localDir.resolve(name);
            if (entry.getAttrs().isDir()) {
                downloadDirectory(remotePath, localPath);
            } else {
                sftpChannel.get(remotePath, localPath.toString());
            }
        }
    }

    public void writeRemoteFile(String remotePath, String content) {
        if (!isSFTPConnected()) return;
        sftpExecutor.submit(() -> {
            try (InputStream in = new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8))) {
                sftpChannel.put(in, remotePath, ChannelSftp.OVERWRITE);
            } catch (Exception e) {
                if (terminalWidget != null) {
                    terminalWidget.appendOutput("Failed to write file: " + e.getMessage() + "\n");
                }
            }
        });
    }

    public String readRemoteFile(String remotePath) throws Exception {
        if (!isSFTPConnected()) {
            throw new IOException("SFTP not connected");
        }
        try {
            return sftpExecutor.submit(() -> {
                try (InputStream in = sftpChannel.get(remotePath);
                     ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
                    in.transferTo(baos);
                    return baos.toString(StandardCharsets.UTF_8);
                }
            }).get(OPERATION_TIMEOUT, TimeUnit.MILLISECONDS);
        } catch (Exception e) {
            if (terminalWidget != null) {
                terminalWidget.appendOutput("Failed to read file: " + e.getMessage() + "\n");
            }
            throw e;
        }
    }

    public List<String> getSSHCommands(String prefix) {
        synchronized (this) {
            if (System.currentTimeMillis() - remoteCommandsLastFetched < REMOTE_COMMANDS_CACHE_DURATION) {
                List<String> result = new ArrayList<>();
                for (String cmd : remoteCommandsCache) {
                    if (cmd.startsWith(prefix)) {
                        result.add(cmd);
                    }
                }
                return result;
            }
        }
        try {
            return fetchRemoteCommands(prefix);
        } catch (Exception e) {
            if (terminalWidget != null) {
                terminalWidget.appendOutput("Error fetching remote commands: " + e.getMessage() + "\n");
            }
            return new ArrayList<>();
        }
    }

    private List<String> fetchRemoteCommands(String prefix) throws Exception {
        ChannelExec channelExec = (ChannelExec) sshSession.openChannel("exec");
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        channelExec.setOutputStream(baos);
        channelExec.setCommand("compgen -c");
        channelExec.connect();
        while (!channelExec.isClosed()) {
            Thread.sleep(100);
        }
        channelExec.disconnect();
        String output = baos.toString(StandardCharsets.UTF_8);
        String[] commands = output.split("\\s+");
        List<String> result = new ArrayList<>();
        for (String cmd : commands) {
            if (cmd.startsWith(prefix)) {
                result.add(cmd);
            }
        }
        synchronized (this) {
            remoteCommandsCache = Arrays.asList(commands);
            remoteCommandsLastFetched = System.currentTimeMillis();
        }
        return result;
    }

    public void runRemoteCommand(String s) {
        if (!isSSH() || sshSession == null || !sshSession.isConnected()) {
            if (terminalWidget != null) {
                terminalWidget.appendOutput("SSH not connected.\n");
            }
            return;
        }
        executorService.submit(() -> {
            try {
                ChannelExec channelExec = (ChannelExec) sshSession.openChannel("exec");
                channelExec.setCommand(s);
                devPrint("Running remote command: " + s);
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                channelExec.setOutputStream(out);
                channelExec.setErrStream(out);
                channelExec.connect();
                while (!channelExec.isClosed()) {
                    Thread.sleep(100);
                }
                channelExec.disconnect();
            } catch (Exception e) {
                System.out.println("Failed to run remote command: " + e.getMessage());
            }
        });
    }

    public void renameRemoteFile(String source, String dest) throws Exception {
        if (!sftpConnected) {
            connectSFTPSync();
            if (!sftpConnected) {
                throw new Exception("SFTP not connected");
            }
        }
        try {
            sftpChannel.rename(source, dest);
        } catch (Exception e) {
            throw new Exception("Failed to rename remote file: " + source + " to " + dest + ": " + e.getMessage(), e);
        }
    }

    public void connectSFTPSync() {
        if (sshSession == null || !sshSession.isConnected()) return;
        try {
            if (sftpChannel != null && sftpChannel.isConnected()) return;
            Channel channel = sshSession.openChannel("sftp");
            channel.connect(CONNECTION_TIMEOUT);
            sftpChannel = (ChannelSftp) channel;
            sftpConnected = true;
        } catch (Exception e) {
            sftpConnected = false;
            devPrint("Synchronous SFTP connection failed: " + e.getMessage());
        }
    }

    public boolean remoteFileExists(String path) {
        devPrint("Checking if remote file exists: " + path);
        try {
            if(!isSFTPConnected()) connectSFTPSync();
            sftpChannel.stat(path);
            return true;
        } catch (SftpException e) {
            if (e.id == ChannelSftp.SSH_FX_NO_SUCH_FILE) {
                return false;
            }
            return false;
        } catch (Exception e) {
            return false;
        }
    }

    public String runRemoteCommandWithOutput(String command) {
        if (!isSSH() || sshSession == null || !sshSession.isConnected()) {
            return "SSH not connected.";
        }
        try {
            ChannelExec channelExec = (ChannelExec) sshSession.openChannel("exec");
            channelExec.setCommand(command);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            channelExec.setOutputStream(out);
            channelExec.setErrStream(out);
            channelExec.connect(CONNECTION_TIMEOUT);
            while (channelExec.isConnected()) {
                Thread.sleep(100);
            }
            channelExec.disconnect();
            return out.toString(StandardCharsets.UTF_8);
        } catch (Exception e) {
            devPrint("Failed to run remote command with output: " + e.getMessage());
            return "Error: " + e.getMessage();
        }
    }

    public void prepareRemoteDirectorySync(String path) throws JSchException, SftpException {
        if (!isSFTPConnected()) connectSFTPSync();
        String[] folders = path.replace("\\", "/").split("/");
        String currentPath = "";
        for (String folder : folders) {
            if (folder.isEmpty()) {
                if(currentPath.isEmpty()) currentPath = "/";
                continue;
            }
            currentPath = currentPath.equals("/") ? currentPath + folder : currentPath + "/" + folder;
            try {
                sftpChannel.stat(currentPath);
            } catch (SftpException e) {
                if (e.id == ChannelSftp.SSH_FX_NO_SUCH_FILE) {
                    sftpChannel.mkdir(currentPath);
                } else {
                    throw e;
                }
            }
        }
    }

    public void downloadMrPackBinary(String user) {
        if (!isSSH() || sshSession == null || !sshSession.isConnected()) {
            if (terminalWidget != null) terminalWidget.appendOutput("SSH not connected.\n");
            return;
        }
        executorService.submit(() -> {
            try {
                String homePath = user.equals("root") ? "/root/remotely/" : "/home/" + user + "/remotely/";
                prepareRemoteDirectorySync(homePath);
                String command = "wget -O " + homePath + "mrpack-install-linux https://github.com/nothub/mrpack-install/releases/download/v0.16.10/mrpack-install-linux && chmod +x " + homePath + "mrpack-install-linux";
                runRemoteCommand(command);
            } catch (Exception e) {
                devPrint("Failed to download MrPack: " + e.getMessage());
            }
        });
    }

    public boolean installMrPackOnRemote(ServerInfo serverInfo, IRemotelyResource resource) {
        if (!isSSH() || sshSession == null || !sshSession.isConnected()) {
            if (terminalWidget != null) terminalWidget.appendOutput("SSH not connected.\n");
            return false;
        }
        try {
            String user = serverInfo.remoteHost.user;
            String homePath = user.equals("root") ? "/root/remotely/mrpack-install-linux" : "/home/" + user + "/remotely/mrpack-install-linux";
            if (!remoteFileExists(homePath)) {
                downloadMrPackBinary(user);
            }

            String serverDir = remoteHost.getHomeDirectory() + "remotely/servers/\"" + resource.getName() + "\"";
            String command = homePath + " " + resource.getProjectId() + " " + resource.getVersion() + " --server-dir " + serverDir + " --server-file server.jar";
            devPrint("Installing MrPack on remote: " + command);
            runRemoteCommand(command);
            return true;
        } catch (Exception e) {
            devPrint("Failed to install MrPack on remote: " + e.getMessage());
            return false;
        }
    }
}