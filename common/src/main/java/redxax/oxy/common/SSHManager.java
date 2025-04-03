package redxax.oxy.common;

import com.jcraft.jsch.*;
import redxax.oxy.common.api.IRemotelyResource;
import redxax.oxy.common.servers.RemoteHostInfo;
import redxax.oxy.common.servers.ServerInfo;
import redxax.oxy.common.servers.ServerProcessManager;
import redxax.oxy.common.servers.ServerState;
import redxax.oxy.common.terminal.TerminalInstance;
import redxax.oxy.common.terminal.ServerTerminalInstance;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;
import static redxax.oxy.common.util.DevUtil.devPrint;

public class SSHManager {
    private RemoteHostInfo remoteHost = new RemoteHostInfo();
    private Session sshSession;
    private ChannelShell sshChannel;
    private BufferedReader sshReader;
    private Writer sshWriter;
    private boolean isSSH = false;
    private boolean awaitingPassword = false;
    private String sshPassword = "";
    private TerminalInstance terminalInstance;
    private final ExecutorService executorService = Executors.newFixedThreadPool(2);
    public final ExecutorService sftpExecutor = Executors.newSingleThreadExecutor();
    private final CountDownLatch sessionInitializedLatch = new CountDownLatch(1);
    public ChannelSftp sftpChannel;
    private boolean sftpConnected = false;
    private ServerInfo serverInfo;
    private List<String> remoteCommandsCache = new ArrayList<>();
    private long remoteCommandsLastFetched = 0;
    private static final long REMOTE_COMMANDS_CACHE_DURATION = 60000;
    private volatile boolean readingSSHOutput = false;

    public SSHManager(ServerInfo serverInfo) {
        this.serverInfo = serverInfo;
    }

    public SSHManager(TerminalInstance terminalInstance) {
        this.terminalInstance = terminalInstance;
    }

    public SSHManager(RemoteHostInfo remoteHost) {
        this.remoteHost = remoteHost;
    }

    public void setTerminalInstance(TerminalInstance terminalInstance) {
        this.terminalInstance = terminalInstance;
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
            sshSession.connect(10000);
            isSSH = true;
            connectSFTP();
        } catch (Exception e) {
            if (terminalInstance != null) {
                terminalInstance.appendOutput("SSH connection failed: " + e.getMessage() + "\n");
            }
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
                if (terminalInstance != null) {
                    terminalInstance.appendOutput("SFTP connection failed: " + e.getMessage() + "\n");
                }
                sftpConnected = false;
            }
        });
    }

    public boolean isSFTPConnected() {
        return sftpConnected;
    }

    public void prepareRemoteDirectory(String path) {
        if (!sftpConnected) return;
        sftpExecutor.submit(() -> {
            try {
                String[] parts = path.replace("\\", "/").split("/");
                StringBuilder current = new StringBuilder();
                for (String p : parts) {
                    if (p.trim().isEmpty()) continue;
                    current.append("/").append(p);
                    try {
                        sftpChannel.cd(current.toString());
                    } catch (SftpException e) {
                        sftpChannel.mkdir(current.toString());
                    }
                }
                devPrint("Prepared remote directory: " + path);
            } catch (Exception e) {
                devPrint("Failed to prepare remote directory: " + path + ": " + e.getMessage());
            }
        });
    }

    public void downloadMrPackBinary(String user) {
        if (!isSSH || sshSession == null || !sshSession.isConnected()) {
            if (terminalInstance != null) {
                terminalInstance.appendOutput("SSH not connected.\n");
            }
            return;
        }
        executorService.submit(() -> {
            try {
                ChannelExec channelExec = (ChannelExec) sshSession.openChannel("exec");
                String homePath = user.equals("root") ? "/root/remotely/" : "/home/" + user + "/remotely/";
                prepareRemoteDirectory(homePath);
                String command = "wget -O " + homePath + "mrpack-install-linux https://github.com/nothub/mrpack-install/releases/download/v0.16.10/mrpack-install-linux && chmod 0755 " + homePath + "mrpack-install-linux";
                devPrint("Downloading MrPack binary: " + command);
                channelExec.setCommand(command);
                channelExec.setInputStream(null);
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                channelExec.setOutputStream(out);
                channelExec.setErrStream(out);
                channelExec.connect();
                while (!channelExec.isClosed()) {
                    Thread.sleep(100);
                }
                String output = out.toString(StandardCharsets.UTF_8);
                System.out.println(output);
                channelExec.disconnect();
                devPrint("Downloaded MrPack binary: " + homePath + "$mrpack-install-linux");
            } catch (Exception e) {
                System.out.println("Failed to download MrPack: " + e.getMessage());
            }
        });
    }

    public boolean installMrPackOnRemote(ServerInfo serverInfo, IRemotelyResource resource) {
        if (!isSSH || sshSession == null || !sshSession.isConnected()) {
            if (terminalInstance != null) {
                terminalInstance.appendOutput("SSH not connected.\n");
            }
            return false;
        }
        try {
            String user = serverInfo.remoteHost.user;
            String homePath = user.equals("root") ? "/root/remotely/mrpack-install-linux" : "/home/" + user + "/remotely/mrpack-install-linux";
            if (!remoteFileExists(homePath)) {
                downloadMrPackBinary(user);
            }
            ChannelExec channelExec = (ChannelExec) sshSession.openChannel("exec");
            StringBuilder cmd = new StringBuilder();
            cmd.append(homePath);
            cmd.append(" ").append(resource.getProjectId()).append(" ").append(resource.getVersion()).append(" ");
            cmd.append(" --server-dir ").append(remoteHost.getHomeDirectory()).append("/remotely/servers/\"").append(resource.getName()).append("\"");
            cmd.append(" --server-file server.jar");
            devPrint("Installing MrPack on remote: " + cmd);
            channelExec.setCommand(cmd.toString());
            channelExec.setInputStream(null);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            channelExec.setOutputStream(out);
            channelExec.setErrStream(out);
            channelExec.connect();
            while (!channelExec.isClosed()) {
                Thread.sleep(100);
            }
            String output = out.toString(StandardCharsets.UTF_8);
            System.out.println(output);
            channelExec.disconnect();
            return true;
        } catch (Exception e) {
            System.out.println("Failed to install MrPack on remote: " + e.getMessage() + "\n");
            return false;
        }
    }

    public boolean remoteFileExists(String path) {
        devPrint("Checking if remote file exists: " + path);
        try {
            ChannelExec channelExec = (ChannelExec) sshSession.openChannel("exec");
            channelExec.setCommand("test -f " + path + " && echo exists || echo not_exist");
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            channelExec.setOutputStream(out);
            channelExec.connect();
            while (!channelExec.isClosed()) {
                Thread.sleep(100);
            }
            String output = out.toString(StandardCharsets.UTF_8).trim();
            channelExec.disconnect();
            devPrint("The File: " + output);
            return "exists".equals(output);
        } catch (Exception e) {
            return false;
        }
    }

    public void launchRemoteServer(String folder, String jarPath) {
        if (!isSSH || sshSession == null || !sshSession.isConnected()) {
            if (terminalInstance != null) {
                terminalInstance.appendOutput("SSH not connected.\n");
            }
            return;
        }
        executorService.submit(() -> {
            try {
                ensureTmuxInstalled();
                String sessionName;
                if (terminalInstance instanceof ServerTerminalInstance sti) {
                    sessionName = "server_" + Integer.toHexString(sti.serverInfo.path.hashCode());
                } else {
                    sessionName = "server_" + terminalInstance.terminalId.toString().replace("-", "");
                }
                ChannelExec checkChannel = (ChannelExec) sshSession.openChannel("exec");
                checkChannel.setCommand("tmux has-session -t " + sessionName + " 2>/dev/null");
                ByteArrayOutputStream checkOut = new ByteArrayOutputStream();
                checkChannel.setOutputStream(checkOut);
                checkChannel.connect();
                while (!checkChannel.isClosed()) {
                    Thread.sleep(100);
                }
                int exitStatus = checkChannel.getExitStatus();
                checkChannel.disconnect();
                if (exitStatus != 0) {
                    String scriptFilePath = folder + "/start.sh";
                    if (remoteFileExists(scriptFilePath)) {
                        String content = readRemoteFile(scriptFilePath);
                        if (!content.contains("-Dnet.kyori.ansi.colorLevel=indexed256")) {
                            StringBuilder commandStr = ServerProcessManager.getCommandStr();
                            writeRemoteFile(scriptFilePath, commandStr.toString());
                        }
                    } else {
                        StringBuilder commandStr = ServerProcessManager.getCommandStr();
                        writeRemoteFile(scriptFilePath, commandStr.toString());
                    }
                    ChannelExec createChannel = (ChannelExec) sshSession.openChannel("exec");
                    String createCommand = "tmux new-session -d -s " + sessionName + " 'cd " + folder + " && ./start.sh'";
                    createChannel.setCommand(createCommand);
                    createChannel.connect();
                    while (!createChannel.isClosed()) {
                        Thread.sleep(100);
                    }
                    createChannel.disconnect();
                }
                if (sshChannel != null && sshChannel.isConnected()) {
                    sshChannel.disconnect();
                }
                ChannelShell ch = (ChannelShell) sshSession.openChannel("shell");
                ch.setPty(true);
                ch.connect();
                sshChannel = ch;
                sshReader = new BufferedReader(new InputStreamReader(sshChannel.getInputStream(), StandardCharsets.UTF_8));
                sshWriter = new OutputStreamWriter(sshChannel.getOutputStream(), StandardCharsets.UTF_8);
                terminalInstance.appendOutput("Attaching to tmux session: " + sessionName + "\n");
                sshWriter.write("tmux attach-session -t " + sessionName + "\n");
                sshWriter.flush();
                readSSHOutput();
            } catch (Exception e) {
                if (terminalInstance != null) {
                    terminalInstance.appendOutput("Failed to start remote server: " + e.getMessage() + "\n");
                }
                if (serverInfo != null) {
                    serverInfo.state = ServerState.CRASHED;
                }
            }
        });
    }

    private void ensureTmuxInstalled() throws Exception {
        ChannelExec exec = (ChannelExec) sshSession.openChannel("exec");
        exec.setCommand("command -v tmux");
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        exec.setOutputStream(output);
        exec.connect();
        while (!exec.isClosed()) {
            Thread.sleep(100);
        }
        String result = output.toString(StandardCharsets.UTF_8).trim();
        exec.disconnect();
        if (result.isEmpty()) {
            ChannelExec installChannel = (ChannelExec) sshSession.openChannel("exec");
            String installCommand = "sudo apt-get update && sudo apt-get install -y tmux";
            installChannel.setCommand(installCommand);
            installChannel.connect();
            while (!installChannel.isClosed()) {
                Thread.sleep(100);
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
                while (isSSH && (line = sshReader.readLine()) != null) {
                    if (terminalInstance != null) {
                        terminalInstance.appendOutput(line + "\n");
                    }
                }
                if (serverInfo != null && serverInfo.state == ServerState.STARTING) {
                    serverInfo.state = ServerState.RUNNING;
                }
            } catch (IOException e) {
                if (terminalInstance != null) {
                    terminalInstance.appendOutput("Error reading SSH output: " + e.getMessage() + "\n");
                }
            } finally {
                readingSSHOutput = false;
            }
        });
    }

    public void startSSHConnection(String command) {
        executorService.submit(() -> {
            try {
                if (terminalInstance != null) {
                    terminalInstance.appendOutput("Connecting...\n");
                }
                String[] parts = command.split(" ");
                if (parts.length < 2) {
                    if (terminalInstance != null) {
                        terminalInstance.appendOutput("Usage: ssh user@host[:port]\n");
                    }
                    sessionInitializedLatch.countDown();
                    return;
                }
                String userHost = parts[1];
                String[] userHostParts = userHost.split("@");
                if (userHostParts.length != 2) {
                    if (terminalInstance != null) {
                        terminalInstance.appendOutput("Invalid SSH command.\n");
                    }
                    sessionInitializedLatch.countDown();
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
                        if (terminalInstance != null) {
                            terminalInstance.appendOutput("Invalid port. Using 22.\n");
                        }
                        port = 22;
                    }
                } else {
                    host = hostPort;
                }
                JSch jsch = new JSch();
                sshSession = jsch.getSession(user, host, port);
                sshSession.setConfig("StrictHostKeyChecking", "no");
                awaitingPassword = true;
                if (terminalInstance != null) {
                    terminalInstance.appendOutput("Password: ");
                }
                sessionInitializedLatch.countDown();
            } catch (Exception e) {
                if (terminalInstance != null) {
                    terminalInstance.appendOutput("SSH connection failed: " + e.getMessage() + "\n");
                }
                sessionInitializedLatch.countDown();
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
                connectSFTP();
                executorService.submit(this::readSSHChannel);
                if (terminalInstance != null) {
                    terminalInstance.appendOutput("Connected.\n");
                }
            } catch (Exception e) {
                if (terminalInstance != null) {
                    terminalInstance.appendOutput("SSH connection failed: " + e.getMessage() + "\n");
                }
                isSSH = false;
                if (sshSession != null && sshSession.isConnected()) {
                    sshSession.disconnect();
                }
            }
        });
    }

    private void readSSHChannel() {
        try {
            String line;
            while (isSSH && (line = sshReader.readLine()) != null) {
                if (terminalInstance != null) {
                    terminalInstance.appendOutput(line + "\n");
                }
            }
            if (isSSH) {
                isSSH = false;
                if (terminalInstance != null) {
                    terminalInstance.appendOutput("SSH session terminated.\n");
                }
            }
        } catch (IOException e) {
            if (terminalInstance != null) {
                terminalInstance.appendOutput("Error reading SSH output: " + e.getMessage() + "\n");
            }
        }
    }

    public void shutdown() {
        try {
            if (sftpChannel != null && sftpChannel.isConnected()) {
                sftpChannel.disconnect();
            }
            if (sshChannel != null && sshChannel.isConnected()) {
                sshChannel.disconnect();
            }
            if (sshSession != null && sshSession.isConnected()) {
                sshSession.disconnect();
            }
            isSSH = false;
            awaitingPassword = false;
            sftpConnected = false;
        } catch (Exception ignored) {}
    }

    public boolean isSSH() {
        return isSSH;
    }

    public boolean waitForSessionInitialization(long timeoutMillis) {
        try {
            return sessionInitializedLatch.await(timeoutMillis, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    public void setSshPassword(String sshPassword) {
        this.sshPassword = sshPassword;
    }

    public boolean isAwaitingPassword() {
        return awaitingPassword;
    }

    public void setAwaitingPassword(boolean awaitingPassword) {
        this.awaitingPassword = awaitingPassword;
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
        if (!sftpConnected) return Collections.emptyList();
        Vector<ChannelSftp.LsEntry> entries = sftpChannel.ls(dir);
        return entries.parallelStream()
                .map(ChannelSftp.LsEntry::getFilename)
                .filter(name -> !name.equals(".") && !name.equals(".."))
                .collect(Collectors.toList());
    }

    public void copyRemote(String source, String dest) throws Exception {
        runRemoteCommand("cp -r \"" + source + "\" \"" + dest + "\"");
    }

    public void renameRemote(String source, String dest) throws Exception {
        sftpChannel.rename(source, dest);
    }

    public void deleteRemote(String path) throws Exception {
        runRemoteCommand("rm -rf \"" + path + "\"");
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
        if (!sftpConnected) return;
        sftpExecutor.submit(() -> {
            try (OutputStream out = sftpChannel.put(remotePath)) {
                out.write(content.getBytes(StandardCharsets.UTF_8));
            } catch (Exception e) {
                if (terminalInstance != null) {
                    terminalInstance.appendOutput("Failed to write file: " + e.getMessage() + "\n");
                }
            }
        });
    }

    public String readRemoteFile(String remotePath) {
        if (!sftpConnected) return "";
        try {
            return sftpExecutor.submit(() -> {
                StringBuilder sb = new StringBuilder();
                try (InputStream in = sftpChannel.get(remotePath);
                     BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        sb.append(line).append("\n");
                    }
                }
                return sb.toString();
            }).get();
        } catch (Exception e) {
            if (terminalInstance != null) {
                terminalInstance.appendOutput("Failed to read file: " + e.getMessage() + "\n");
            }
            return "";
        }
    }

    public String getSshPassword() {
        return sshPassword;
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
            if (terminalInstance != null) {
                terminalInstance.appendOutput("Error fetching remote commands: " + e.getMessage() + "\n");
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
        if (!isSSH || sshSession == null || !sshSession.isConnected()) {
            if (terminalInstance != null) {
                terminalInstance.appendOutput("SSH not connected.\n");
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

    public void renameRemoteFolder(String path, String newRemotePath) {
        if (!sftpConnected) return;
        sftpExecutor.submit(() -> {
            try {
                if (!isRemoteDirectory(newRemotePath)) {
                    devPrint("Rename: Directory Doesn't Exist, Creating remote directory: " + newRemotePath);
                    prepareRemoteDirectory(newRemotePath);
                }
                sftpChannel.rename(path, newRemotePath);
                devPrint("Renamed remote folder: " + path + " to " + newRemotePath);
            } catch (Exception e) {
                devPrint("Failed to rename remote folder: " + path + " to " + newRemotePath + ": " + e.getMessage());
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
            Channel channel = sshSession.openChannel("sftp");
            channel.connect();
            sftpChannel = (ChannelSftp) channel;
            sftpConnected = true;
        } catch (Exception e) {
            sftpConnected = false;
            devPrint("Synchronous SFTP connection failed: " + e.getMessage());
        }
    }

    public String runRemoteCommandWithOutput(String sizeCommand) {
        if (!isSSH || sshSession == null || !sshSession.isConnected()) {
            if (terminalInstance != null) {
                devPrint("SSH not connected.");
            }
            return "";
        }
        try {
            return executorService.submit(() -> {
                try {
                    ChannelExec channelExec = (ChannelExec) sshSession.openChannel("exec");
                    channelExec.setCommand(sizeCommand);
                    ByteArrayOutputStream out = new ByteArrayOutputStream();
                    channelExec.setOutputStream(out);
                    channelExec.setErrStream(out);
                    channelExec.connect();
                    while (!channelExec.isClosed()) {
                        Thread.sleep(50);
                    }
                    String output = out.toString(StandardCharsets.UTF_8);
                    channelExec.disconnect();
                    return output;
                } catch (Exception e) {
                    devPrint("Failed to run remote command: " + sizeCommand + ": " + e.getMessage());
                    return "";
                }
            }).get();
        } catch (Exception e) {
            devPrint("Failed to run remote command: " + sizeCommand + ": " + e.getMessage());
            return "";
        }
    }
}
