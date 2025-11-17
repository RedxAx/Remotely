package redxax.oxy.remotely.data.player.standard;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import redxax.oxy.remotely.data.managed.PlayerSession;
import redxax.oxy.remotely.data.managed.SessionEvent;
import redxax.oxy.remotely.data.managed.SessionEventType;
import redxax.oxy.remotely.data.player.IPlayerHistoryCollector;
import redxax.oxy.remotely.data.player.IPlayerHistoryProvider;
import restudio.rebase.api.RebaseAPI;
import restudio.rebase.ui.widgets.TerminalWidget;

import java.lang.reflect.Type;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class StandardPlayerHistoryProvider implements IPlayerHistoryProvider, IPlayerHistoryCollector {
    private final Gson gson = new GsonBuilder().disableHtmlEscaping().create();
    private final RebaseAPI api;
    private final TerminalWidget terminalWidget;
    private final Path historyDir;
    private final Map<UUID, List<PlayerSession>> sessionsCache = new HashMap<>();
    private final Map<UUID, PlayerSession> activeSessions = new HashMap<>();
    private final List<PatternHandler> patternHandlers = new ArrayList<>();
    private final Function<String, UUID> nameResolver;
    private final Map<UUID, Map<String, Long>> lastCommandSeen = new HashMap<>();
    private final Map<UUID, Map<String, Long>> lastAccessSeen = new HashMap<>();
    private static final Pattern ANSI_PATTERN = Pattern.compile("\u001B\\[[0-9;]*[A-Za-z]");

    private static final Pattern COMMAND_ISSUED_PATTERN_1 = Pattern.compile("(?:.*\\[INFO]: )?.*?(\\w+) issued server command: (.+)");
    private static final Pattern COMMAND_ISSUED_PATTERN_2 = Pattern.compile("(?:.*\\[INFO]: )?.*?(\\w+) executed command: (.+)");

    private record PatternHandler(Pattern pattern, PatternConsumer consumer) { }

    public interface PatternConsumer {
        void accept(Matcher matcher, String line, long timestamp);
    }

    public StandardPlayerHistoryProvider(RebaseAPI api, TerminalWidget terminalWidget, Path instancePath, Function<String, UUID> nameResolver) {
        this.api = api;
        this.terminalWidget = terminalWidget;
        this.historyDir = instancePath.resolve("Remotely").resolve("player-history");
        this.nameResolver = nameResolver;
    }

    @Override
    public void initialize() {
        ensureDir();
        registerDefaultPatterns();
        if (terminalWidget != null) {
            terminalWidget.addOutputListener(this::onConsoleLine);
        }
    }

    @Override
    public void shutdown() {
        if (terminalWidget != null) {
            terminalWidget.removeOutputListener(this::onConsoleLine);
        }
    }

    public void onConsoleLine(String line) {
        if (line == null || line.isEmpty()) return;
        line = ANSI_PATTERN.matcher(line).replaceAll("");
        long now = System.currentTimeMillis();
        for (PatternHandler ph : patternHandlers) {
            Matcher m = ph.pattern.matcher(line);
            if (m.matches()) ph.consumer.accept(m, line, now);
        }
    }

    public void registerPattern(Pattern pattern, PatternConsumer consumer) {
        patternHandlers.add(new PatternHandler(pattern, consumer));
    }

    @Override
    public void startSession(UUID uuid, String name, String ip, long startTime) {
        PlayerSession session = new PlayerSession(uuid, name, ip, startTime);
        activeSessions.put(uuid, session);
        List<PlayerSession> list = sessionsCache.computeIfAbsent(uuid, u -> new ArrayList<>());
        list.add(session);
        save(uuid);
    }

    @Override
    public void endSession(UUID uuid, long endTime) {
        PlayerSession s = activeSessions.get(uuid);
        if (s != null) {
            s.endTime = endTime;
            save(uuid);
        }
    }

    @Override
    public void recordCommand(UUID uuid, String name, String command, long timestamp) {
        Map<String, Long> seen = lastCommandSeen.computeIfAbsent(uuid, u -> new HashMap<>());
        Long lastTs = seen.get(command);
        if (lastTs != null && (timestamp - lastTs) < 500) return;
        seen.put(command, timestamp);
        PlayerSession s = ensureActiveOrEphemeral(uuid, name, timestamp);
        s.events.add(new SessionEvent(timestamp, SessionEventType.COMMAND, command));
        save(uuid);
    }

    @Override
    public void recordAccessChange(UUID uuid, String name, SessionEventType type, String details, long timestamp) {
        if(uuid == null) return;
        String key = (type == SessionEventType.BAN || type == SessionEventType.UNBAN || type == SessionEventType.KICK) ? type.name() : type.name() + "|" + String.valueOf(details);
        Map<String, Long> seen = lastAccessSeen.computeIfAbsent(uuid, u -> new HashMap<>());
        Long lastTs = seen.get(key);
        if (lastTs != null && (timestamp - lastTs) < 1000) return;
        seen.put(key, timestamp);
        PlayerSession s = ensureActiveOrEphemeral(uuid, name, timestamp);
        s.events.add(new SessionEvent(timestamp, type, details));
        save(uuid);
    }

    public void recordCommandByName(String name, String command, long timestamp) {
        UUID uuid = resolve(name);
        if (uuid == null) return;
        recordCommand(uuid, name, command, timestamp);
    }

    @Override
    public CompletableFuture<List<PlayerSession>> getSessions(UUID uuid) {
        List<PlayerSession> cached = sessionsCache.get(uuid);
        if (cached != null) return CompletableFuture.completedFuture(cached);
        return load(uuid).thenApply(list -> {
            sessionsCache.put(uuid, list);
            return list;
        });
    }

    private PlayerSession ensureActiveOrEphemeral(UUID uuid, String name, long timestamp) {
        PlayerSession s = activeSessions.get(uuid);
        if (s == null) {
            s = new PlayerSession(uuid, name, null, timestamp);
            s.endTime = timestamp;
            List<PlayerSession> list = sessionsCache.computeIfAbsent(uuid, u -> new ArrayList<>());
            list.add(s);
        }
        return s;
    }

    private UUID resolve(String name) {
        if (nameResolver == null) return null;
        return nameResolver.apply(name);
    }

    private void registerDefaultPatterns() {
        registerPattern(COMMAND_ISSUED_PATTERN_1, (m, line, ts) -> recordCommandByName(m.group(1), m.group(2), ts));
        registerPattern(COMMAND_ISSUED_PATTERN_2, (m, line, ts) -> recordCommandByName(m.group(1), m.group(2), ts));
    }

    private void ensureDir() {
        api.fileExists(historyDir).thenAccept(exists -> { if (!exists) api.createDirectory(historyDir); });
    }

    private Path fileFor(UUID uuid) {
        return historyDir.resolve(uuid.toString() + ".json");
    }

    private CompletableFuture<List<PlayerSession>> load(UUID uuid) {
        Path f = fileFor(uuid);
        return api.fileExists(f).thenCompose(exists -> {
            if (!exists) return CompletableFuture.completedFuture(new ArrayList<>());
            return api.readFile(f).thenApply(content -> {
                if (content == null || content.isEmpty()) return new ArrayList<>();
                Type type = new TypeToken<List<PlayerSession>>(){}.getType();
                List<PlayerSession> result = gson.fromJson(content, type);
                return result != null ? result : new ArrayList<>();
            });
        });
    }

    private void save(UUID uuid) {
        List<PlayerSession> list = sessionsCache.getOrDefault(uuid, new ArrayList<>());
        String json = new GsonBuilder().disableHtmlEscaping().setPrettyPrinting().create().toJson(list);
        api.writeFile(fileFor(uuid), json);
    }
}