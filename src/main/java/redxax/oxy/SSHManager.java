package redxax.oxy;

import com.jcraft.jsch.JSch;
import com.jcraft.jsch.Session;
import com.jcraft.jsch.ChannelShell;
import com.jcraft.jsch.ChannelExec;
import com.jcraft.jsch.UserInfo;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.ArrayList;
import java.util.List;

public class SSHManager {

    private Session sshSession;
    private ChannelShell sshChannel;
    private BufferedReader sshReader;
    private Writer sshWriter;
    private boolean isSSH = false;
    private boolean awaitingPassword = false;
    private String sshPassword = "";
    private final TerminalInstance terminalInstance;
    private final ExecutorService executorService = Executors.newFixedThreadPool(2);

    private boolean isMinecraftServerDetected = false;
    private boolean isMinecraftServerLoaded = false;

    private List<String> remoteCommandsCache = new ArrayList<>();
    private long remoteCommandsLastFetched = 0;
    private static final long REMOTE_COMMANDS_CACHE_DURATION = 60 * 1000;

    public SSHManager(TerminalInstance terminalInstance) {
        this.terminalInstance = terminalInstance;
    }

    public void startSSHConnection(String command) {
        executorService.submit(() -> {
            try {
                terminalInstance.appendOutput("Connecting...\n");
                String[] parts = command.split(" ");
                if (parts.length < 2) {
                    terminalInstance.appendOutput("Usage: ssh user@host\n");
                    return;
                }
                String userHost = parts[1];
                String[] userHostParts = userHost.split("@");
                if (userHostParts.length != 2) {
                    terminalInstance.appendOutput("Invalid SSH command. Use ssh user@host\n");
                    return;
                }
                String user = userHostParts[0];
                String host = userHostParts[1];

                JSch jsch = new JSch();
                sshSession = jsch.getSession(user, host, 22);
                sshSession.setConfig("StrictHostKeyChecking", "no");
                sshSession.setUserInfo(new SSHUserInfo());
                awaitingPassword = true;
                terminalInstance.appendOutput("Password: ");
            } catch (Exception e) {
                terminalInstance.appendOutput("SSH connection failed: " + e.getMessage() + "\n");
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

                executorService.submit(this::readSSHOutput);

                terminalInstance.appendOutput("Connected.\n");
            } catch (Exception e) {
                terminalInstance.appendOutput("SSH connection failed: " + e.getMessage() + "\n");
                isSSH = false;
                if (sshSession != null && sshSession.isConnected()) {
                    sshSession.disconnect();
                }
            }
        });
    }

    private void readSSHOutput() {
        try {
            String line;
            while (isSSH && (line = sshReader.readLine()) != null) {
                line = line.replace("\u0000", "").replace("\r", "");
                terminalInstance.appendOutput(line + "\n");
                if (!isMinecraftServerDetected) {
                    if (line.contains("Starting minecraft server")) {
                        isMinecraftServerDetected = true;
                    }
                } else if (!isMinecraftServerLoaded) {
                    if (line.contains("Done") && line.contains("For help, type \"help\"")) {
                        isMinecraftServerLoaded = true;
                        fetchMinecraftServerCommands();
                    }
                }
            }
            if (isSSH) {
                isSSH = false;
                terminalInstance.appendOutput("SSH session terminated.\n");
            }
        } catch (IOException e) {
            terminalInstance.appendOutput("Error reading SSH output: " + e.getMessage() + "\n");
        }
    }

    private void fetchMinecraftServerCommands() {
        executorService.submit(() -> {
            try {
                sshWriter.write("help\n");
                sshWriter.flush();
                List<String> commands = new ArrayList<>();
                boolean readingCommands = false;
                String line;
                while ((line = sshReader.readLine()) != null) {
                    line = line.replace("\u0000", "").replace("\r", "");
                    if (line.startsWith("----")) {
                        readingCommands = !readingCommands;
                        continue;
                    }
                    if (readingCommands) {
                        String cmd = line.trim().split(" ")[0];
                        commands.add(cmd);
                    }
                    if (line.contains("For help, type \"help\"")) {
                        break;
                    }
                }
                synchronized (this) {
                    remoteCommandsCache = commands;
                    remoteCommandsLastFetched = System.currentTimeMillis();
                }
            } catch (IOException e) {
                terminalInstance.appendOutput("Failed to fetch Minecraft server commands: " + e.getMessage() + "\n");
            }
        });
    }

    public void shutdown() {
        if (sshChannel != null && sshChannel.isConnected()) {
            sshChannel.disconnect();
        }
        if (sshSession != null && sshSession.isConnected()) {
            sshSession.disconnect();
        }
        isSSH = false;
        awaitingPassword = false;
    }

    public boolean isAwaitingPassword() {
        return awaitingPassword;
    }

    public void setAwaitingPassword(boolean awaitingPassword) {
        this.awaitingPassword = awaitingPassword;
    }

    public String getSshPassword() {
        return sshPassword;
    }

    public void setSshPassword(String sshPassword) {
        this.sshPassword = sshPassword;
    }

    public boolean isSSH() {
        return isSSH;
    }

    public Writer getSshWriter() {
        return sshWriter;
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
            terminalInstance.appendOutput("Error fetching remote commands: " + e.getMessage() + "\n");
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

    private class SSHUserInfo implements UserInfo {

        @Override
        public String getPassword() {
            return sshPassword;
        }

        @Override
        public boolean promptYesNo(String message) {
            return true;
        }

        @Override
        public String getPassphrase() {
            return null;
        }

        @Override
        public boolean promptPassphrase(String message) {
            return false;
        }

        @Override
        public boolean promptPassword(String message) {
            return true;
        }

        @Override
        public void showMessage(String message) {
            terminalInstance.appendOutput(message + "\n");
        }
    }
}
