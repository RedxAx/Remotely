package redxax.oxy.remotely.data.player.standard;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import redxax.oxy.remotely.data.managed.ManagedPlayer;
import redxax.oxy.remotely.data.managed.PlayerAction;
import redxax.oxy.remotely.data.player.IPlayerActionProvider;
import restudio.rebase.api.RebaseAPI;
import restudio.rebase.instance.Instance;
import restudio.rebase.ui.widgets.TerminalWidget;
import restudio.rescreen.util.Notification;

import java.lang.reflect.Type;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

public class StandardPlayerActionProvider implements IPlayerActionProvider {

    private final RebaseAPI api;
    private final TerminalWidget terminalWidget;
    private final Path playerActionsPath;
    private List<PlayerAction> cachedPlayerActions = new ArrayList<>();
    private final Gson gson = new Gson();

    public StandardPlayerActionProvider(Instance instance, RebaseAPI api, TerminalWidget terminalWidget) {
        this.api = api;
        this.terminalWidget = terminalWidget;
        this.playerActionsPath = Path.of(instance.getPath(), "Remotely", "player-actions.json");
    }

    @Override
    public void initialize() {
        ensureRemotelyDirectory();
        loadPlayerActions();
    }

    @Override
    public void shutdown() {
    }

    @Override
    public CompletableFuture<Void> kickPlayer(ManagedPlayer player, String reason) {
        return runCustomCommand(player, "kick " + player.name + " " + reason);
    }

    @Override
    public CompletableFuture<Void> banPlayer(ManagedPlayer player, String reason, boolean ipBan) {
        String command = ipBan ? "ban-ip " : "ban ";
        command += player.name + " " + reason;
        return runCustomCommand(player, command);
    }

    @Override
    public CompletableFuture<Void> unbanPlayer(ManagedPlayer player) {
        String command = player.isIpBanned && player.ipBanInfo != null ? "pardon-ip " + player.ipBanInfo.ip : "pardon " + player.name;
        return runCustomCommand(player, command);
    }

    @Override
    public CompletableFuture<Void> toggleOp(ManagedPlayer player) {
        String command = player.isOp ? "deop " : "op ";
        return runCustomCommand(player, command + player.name);
    }

    @Override
    public CompletableFuture<Void> runCustomCommand(ManagedPlayer player, String commandTemplate) {
        return CompletableFuture.runAsync(() -> {
            if (terminalWidget == null) {
                new Notification.Builder().message("Failed To Execute").type(Notification.Type.ERROR).description("Terminal is not available");
                return;
            }
            String command = commandTemplate.replace("$name", player.name).replace("$uuid", player.uuid.toString());
            terminalWidget.executeCommand(command);
        });
    }

    @Override
    public CompletableFuture<List<PlayerAction>> getCustomActions() {
        return CompletableFuture.completedFuture(cachedPlayerActions);
    }

    private void loadPlayerActions() {
        loadJsonFile(playerActionsPath, new TypeToken<List<PlayerAction>>() {}).thenAccept(actions -> this.cachedPlayerActions = Objects.requireNonNullElseGet(actions, ArrayList::new));
    }

    private void ensureRemotelyDirectory() {
        Path remotelyDir = playerActionsPath.getParent();
        api.fileExists(remotelyDir).thenAccept(exists -> {
            if (!exists) {
                api.createDirectory(remotelyDir);
            }
        });
    }

    private <T> CompletableFuture<List<T>> loadJsonFile(Path path, TypeToken<List<T>> typeToken) {
        return api.fileExists(path).thenCompose(exists -> {
            if (!exists) {
                return CompletableFuture.completedFuture(new ArrayList<>());
            }
            return api.readFile(path).thenApply(content -> {
                if (content == null || content.isEmpty()) {
                    return new ArrayList<>();
                }
                Type type = typeToken.getType();
                List<T> result = gson.fromJson(content, type);
                return result != null ? result : new ArrayList<>();
            });
        });
    }
}
