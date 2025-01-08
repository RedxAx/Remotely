package redxax.oxy;

import com.jcraft.jsch.*;
import redxax.oxy.explorer.Notification;
import redxax.oxy.servers.RemoteHostInfo;
import redxax.oxy.servers.ServerInfo;
import redxax.oxy.servers.ServerState;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.*;

import static redxax.oxy.DevUtil.*;

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
    private final ExecutorService sftpExecutor = Executors.newSingleThreadExecutor();
    private final CountDownLatch sessionInitializedLatch = new CountDownLatch(1);
    private ChannelSftp sftpChannel;
    private boolean sftpConnected = false;
    private ServerInfo serverInfo;
    private List<String> remoteCommandsCache = new ArrayList<>();
    private long remoteCommandsLastFetched = 0;
    private static final long REMOTE_COMMANDS_CACHE_DURATION = 60000;

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
            } catch (Exception ignored) {}
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
                String command = "wget -O " + homePath + "mrpack-install-linux https://github.com/nothub/mrpack-install/releases/download/v0.16.10/mrpack-install-linux && chmod 0755 " + homePath + "mrpack-install-linux";
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
            } catch (Exception e) {
                System.out.println("Failed to download MrPack: " + e.getMessage());
            }
        });
    }

    public void runMrPackOnRemote(ServerInfo s) {
        if (!isSSH || sshSession == null || !sshSession.isConnected()) {
            if (terminalInstance != null) {
                terminalInstance.appendOutput("SSH not connected.\n");
            }
            return;
        }
        executorService.submit(() -> {
            try {
                String user = s.remoteHost.user;
                String homePath = user.equals("root") ? "/root/remotely/mrpack-install-linux" : "/home/" + user + "/remotely/mrpack-install-linux";
                if (!remoteFileExists(homePath)) {
                    downloadMrPackBinary(user);
                }
                ChannelExec channelExec = (ChannelExec) sshSession.openChannel("exec");
                StringBuilder cmd = new StringBuilder();
                cmd.append(homePath).append(" server");
                if (s.type.equalsIgnoreCase("vanilla")) {
                    cmd.append(" vanilla");
                } else {
                    cmd.append(" ").append(s.type);
                }
                cmd.append(" --server-dir ").append(s.path.replace(" ", "\\ "));
                if (!s.version.equalsIgnoreCase("latest")) {
                    cmd.append(" --minecraft-version ").append(s.version);
                }
                System.out.println("Running MrPack on remote: " + cmd);
                cmd.append(" --server-file server.jar");
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
            } catch (Exception e) {
                System.out.println("Failed to run MrPack on remote: " + e.getMessage() + "\n");
            }
        });
    }

    private boolean remoteFileExists(String path) { //don't forget to add "/" before the file path.
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
                ChannelShell ch = (ChannelShell) sshSession.openChannel("shell");
                ch.setPty(true);
                ch.connect();
                sshChannel = ch;
                sshReader = new BufferedReader(new InputStreamReader(sshChannel.getInputStream(), StandardCharsets.UTF_8));
                sshWriter = new OutputStreamWriter(sshChannel.getOutputStream(), StandardCharsets.UTF_8);
                if (terminalInstance != null) {
                    terminalInstance.appendOutput("Remote server starting...\n");
                }
                String scriptFilePath = "cd " + folder + "&& ./start.sh";
                if (!remoteFileExists(folder + "/start.sh")) {
                    if (terminalInstance != null) terminalInstance.appendOutput("You Don't Have a start.sh, Creating One...\n");

                    StringBuilder commandStr = ServerProcessManager.getCommandStr();
                    sshWriter.write("echo \"" + commandStr.toString().replace("\"", "\\\"") + "\" > " + scriptFilePath + "\n");
                    sshWriter.write("chmod +x " + scriptFilePath + "\n");
                    if (terminalInstance != null)
                        terminalInstance.appendOutput("start.sh Created, Starting Server...\n");
                }
                sshWriter.write(scriptFilePath + "\n");
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

    private void readSSHOutput() {
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

    public List<String> listRemoteDirectory(String dir) throws SftpException, ExecutionException, InterruptedException {
        if (!sftpConnected) return Collections.emptyList();
        return sftpExecutor.submit(() -> {
            Vector<ChannelSftp.LsEntry> list = sftpChannel.ls(dir);
            List<String> result = new ArrayList<>();
            for (ChannelSftp.LsEntry entry : list) {
                if (!entry.getFilename().equals(".") && !entry.getFilename().equals("..")) {
                    result.add(entry.getFilename());
                }
            }
            return result;
        }).get();
    }

    public void deleteRemoteDirectory(String remotePath) {
        if (!sftpConnected) return;
        sftpExecutor.submit(() -> {
            try {
                String command = "rm -rf " + remotePath;
                ChannelExec channelExec = (ChannelExec) sshSession.openChannel("exec");
                channelExec.setCommand(command);
                channelExec.connect();
                while (!channelExec.isClosed()) {
                    Thread.sleep(100);
                }
                channelExec.disconnect();
            } catch (Exception e) {
                if (terminalInstance != null) {
                    terminalInstance.appendOutput("Failed to delete remote directory: " + e.getMessage() + "\n");
                }
            }
        });
    }

    public void uploadRemotePath(String string, String remotePath) {
        if (!sftpConnected) return;
        sftpExecutor.submit(() -> {
            try {
                sftpChannel.put(string, remotePath);
            } catch (Exception e) {
                if (terminalInstance != null) {
                    terminalInstance.appendOutput("Failed to upload file: " + e.getMessage() + "\n");
                }
            }
        });
    }

    public void downloadRemotePath(String remotePath, Path resolve) {
        if (!sftpConnected) return;
        sftpExecutor.submit(() -> {
            try {
                sftpChannel.get(remotePath, resolve.toString());
            } catch (Exception e) {
                if (terminalInstance != null) {
                    terminalInstance.appendOutput("Failed to download file: " + e.getMessage() + "\n");
                }
            }
        });
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
}
