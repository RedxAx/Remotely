package redxax.oxy.input;

import redxax.oxy.TerminalInstance;
import redxax.oxy.SSHManager;

import java.io.File;
import java.io.IOException;
import java.io.Writer;

public class CommandExecutor {

    private final TerminalInstance terminalInstance;
    private final SSHManager sshManager;
    private Writer writer;
    private final TerminalProcessManager terminalProcessManager;
    private String currentDirectory;


    public CommandExecutor(TerminalInstance terminalInstance, SSHManager sshManager, Writer writer, TerminalProcessManager terminalProcessManager) {
        this.terminalInstance = terminalInstance;
        this.sshManager = sshManager;
        this.terminalProcessManager = terminalProcessManager;
        this.currentDirectory = terminalProcessManager.getCurrentDirectory();
        this.writer = writer != null ? writer : terminalProcessManager.getWriter();
    }

    public void executeCommand(String command, StringBuilder inputBuffer) throws IOException {

        if (command.equalsIgnoreCase("exit")) {
            if (sshManager.isSSH()) {
                sshManager.getSshWriter().write("exit\n");
                sshManager.getSshWriter().flush();
            } else {
                shutdown();
            }
        } else if (command.equalsIgnoreCase("clear")) {
            terminalInstance.renderer.clearOutput();
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
            updateCurrentDirectoryFromCommand(command);
        }

        if (!command.isEmpty() && (terminalInstance.getCommandHistory() == null || terminalInstance.getCommandHistory().isEmpty() || !command.equals(terminalInstance.getCommandHistory().get(terminalInstance.getCommandHistory().size() - 1)))) {
            if (terminalInstance.getCommandHistory() != null) {
                terminalInstance.getCommandHistory().add(command);
                terminalInstance.setHistoryIndex(terminalInstance.getCommandHistory().size());
            }
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

    public TerminalProcessManager getTerminalProcessManager() {
        return terminalProcessManager;
    }
}