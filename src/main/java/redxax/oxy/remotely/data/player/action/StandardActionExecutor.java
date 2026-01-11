package redxax.oxy.remotely.data.player.action;

import redxax.oxy.remotely.data.player.model.UnifiedPlayer;
import restudio.rebase.ui.widgets.TerminalWidget;
import restudio.rescreen.debug.DebugManager;

import java.util.concurrent.CompletableFuture;

public class StandardActionExecutor implements IActionExecutor {
    private final TerminalWidget terminal;

    public StandardActionExecutor(TerminalWidget terminal) {
        this.terminal = terminal;
    }

    @Override
    public boolean canExecute(String actionType) {
        return switch (actionType) {
            case "kick", "ban", "unban", "op", "deop", "command" -> true;
            default -> false;
        };
    }

    @Override
    public CompletableFuture<Void> execute(UnifiedPlayer player, String actionType, Object... args) {
        if (terminal == null) {
            DebugManager.getInstance().log("StandardActionExecutor", "Terminal is null!");
            return CompletableFuture.completedFuture(null);
        }
        String name = player.getName();
        if (name == null && !actionType.equals("command")) {
            DebugManager.getInstance().log("StandardActionExecutor", "Player name is null!");
            return CompletableFuture.completedFuture(null);
        }

        DebugManager.getInstance().log("StandardActionExecutor", "Executing " + actionType + " for " + name);

        switch (actionType) {
            case "kick" -> {
                String reason = args.length > 0 ? (String) args[0] : "Kicked by operator";
                terminal.executeCommand("kick " + name + " " + reason);
            }
            case "ban" -> {
                String reason = args.length > 0 ? (String) args[0] : "Banned by operator";
                boolean ipBan = args.length > 1 && (boolean) args[1];
                if (ipBan) {
                    terminal.executeCommand("ban-ip " + name + " " + reason);
                } else {
                    terminal.executeCommand("ban " + name + " " + reason);
                }
            }
            case "unban" -> {
                terminal.executeCommand("pardon " + name);
            }
            case "op" -> {
                terminal.executeCommand("op " + name);
            }
            case "deop" -> {
                terminal.executeCommand("deop " + name);
            }
            case "command" -> {
                String command = args.length > 0 ? (String) args[0] : null;
                if (command != null) {
                    terminal.executeCommand(command);
                }
            }
        }
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public int getPriority() {
        return 10;
    }
}
