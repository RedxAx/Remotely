package redxax.oxy.input;

import redxax.oxy.TerminalInstance;
import redxax.oxy.SSHManager;

import java.io.File;
import java.io.IOException;
import java.io.Writer;
import java.util.List;

public class CommandExecutor {

    private final TerminalInstance terminalInstance;
    private final SSHManager sshManager;
    private Writer writer;
    private final CommandLogger commandLogger;
    private final TerminalProcessManager terminalProcessManager;
    private String currentDirectory;


    public CommandExecutor(TerminalInstance terminalInstance, SSHManager sshManager, Writer writer, CommandLogger commandLogger, TerminalProcessManager terminalProcessManager) {
        this.terminalInstance = terminalInstance;
        this.sshManager = sshManager;
        this.commandLogger = commandLogger;
        this.terminalProcessManager = terminalProcessManager;
        this.currentDirectory = terminalProcessManager.getCurrentDirectory();
        this.writer = writer != null ? writer : terminalProcessManager.getWriter();
    }

    public void executeCommand(String command, StringBuilder inputBuffer) throws IOException {
        terminalInstance.logCommand(command);
        commandLogger.logCommand(command);

        if (command.equalsIgnoreCase("exit")) {
            if (sshManager.isSSH()) {
                sshManager.getSshWriter().write("exit\n");
                sshManager.getSshWriter().flush();
            } else {
                shutdown();
            }
        } else if (command.equalsIgnoreCase("clear")) {
            synchronized (terminalInstance.renderer.getTerminalOutput()) {
                terminalInstance.renderer.getTerminalOutput().setLength(0);
            }
            if (sshManager.isSSH()) {
                sshManager.getSshWriter().write("clear\n");
                sshManager.getSshWriter().flush();
            }
        } else if (command.startsWith("ssh ")) {
            sshManager.startSSHConnection(command);
        } else if (sshManager.isSSH()) {
            sshManager.getSshWriter().write(inputBuffer.toString() + "\n");
            sshManager.getSshWriter().flush();
        } else {
            if (command.equalsIgnoreCase("clear")) {
                synchronized (terminalInstance.renderer.getTerminalOutput()) {
                    terminalInstance.renderer.getTerminalOutput().setLength(0);
                }
            } else {
                if (writer != null) {
                    writer.write(inputBuffer.toString() + "\n");
                    writer.flush();
                } else {
                    writer = terminalProcessManager.getWriter();
                    if (writer != null) {
                        writer.write(inputBuffer.toString() + "\n");
                        writer.flush();
                    } else {
                        throw new IOException("Writer is not initialized.");
                    }
                }
            }
            updateCurrentDirectoryFromCommand(command);
        }

        if (!command.isEmpty() && (terminalInstance.getCommandHistory().isEmpty() || !command.equals(terminalInstance.getCommandHistory().get(terminalInstance.getCommandHistory().size() - 1)))) {
            terminalInstance.getCommandHistory().add(command);
            terminalInstance.setHistoryIndex(terminalInstance.getCommandHistory().size());
        }
    }

    private void updateCurrentDirectoryFromCommand(String command) {
        if (command.startsWith("cd ")) {
            String path = command.substring(3).trim();
            File dir = new File(currentDirectory, path);
            if (dir.isDirectory()) {
                currentDirectory = dir.getAbsolutePath();
                terminalProcessManager.setCurrentDirectory(currentDirectory);
                TabCompletionHandler tabCompletionHandler = terminalInstance.getInputHandler().tabCompletionHandler;
                tabCompletionHandler.setCurrentDirectory(currentDirectory);
            }
        }
    }

    private void shutdown() {
        terminalProcessManager.shutdown();
    }

    public List<String> getAvailableCommands(String prefix) {
        return terminalProcessManager.getAvailableCommands(prefix);
    }

    public boolean isExecutable(File file) {
        return terminalProcessManager.isExecutable(file);
    }

    public boolean isWindows() {
        return terminalProcessManager.isWindows();
    }

    public TerminalProcessManager getTerminalProcessManager() {
        return terminalProcessManager;
    }
}