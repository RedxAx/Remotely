package redxax.oxy.remotely.msmp;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import redxax.oxy.remotely.config.Config;
import redxax.oxy.remotely.msmp.dto.GameRule;
import redxax.oxy.remotely.msmp.dto.Player;
import redxax.oxy.remotely.msmp.dto.TickInfo;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

public class MSMPClient implements IMSMPApi {

    private final HttpClient httpClient;
    private volatile WebSocket webSocket;
    private final Gson gson = new Gson();
    private final AtomicLong nextId = new AtomicLong(1);
    private final Map<Long, CompletableFuture<JsonElement>> pendingRequests = new ConcurrentHashMap<>();
    private final Map<String, Consumer<JsonElement>> notificationHandlers = new ConcurrentHashMap<>();
    private volatile boolean connected = false;
    private final ScheduledThreadPoolExecutor scheduler = new ScheduledThreadPoolExecutor(1, r -> {
        Thread t = new Thread(r, "MSMP-Poller");
        t.setDaemon(true);
        return t;
    });
    private volatile boolean tickPollActive = false;
    private Consumer<TickInfo> tickConsumer;

    public MSMPClient() {
        this.httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    }

    private boolean isDebug() {
        return Config.isDev;
    }

    private void debugLog(String s) {
        if (isDebug()) System.out.println("[MSMP] " + s);
    }

    @Override
    public CompletableFuture<Boolean> connect(String host, int port, String token) {
        CompletableFuture<Boolean> connectFuture = new CompletableFuture<>();
        try {
            URI uri;
            if (host.contains("://")) {
                String base = host.endsWith("/") ? host.substring(0, host.length() - 1) : host;
                if (!base.matches(".*:\\d+$")) base = base + ":" + port;
                uri = new URI(base);
            } else {
                uri = new URI("ws://" + host + ":" + port);
            }
            debugLog("Connecting to " + uri);
            httpClient.newWebSocketBuilder().header("Authorization", "Bearer " + token).buildAsync(uri, new WebSocketListener(connectFuture));
        } catch (Exception e) {
            connectFuture.completeExceptionally(e);
        }
        return connectFuture;
    }

    @Override
    public boolean isConnected() {
        return connected;
    }

    @Override
    public void close() {
        if (webSocket != null && !webSocket.isOutputClosed()) {
            try {
                webSocket.sendClose(WebSocket.NORMAL_CLOSURE, "closing").join();
            } catch (Exception ignored) {}
        }
        connected = false;
        pendingRequests.values().forEach(f -> f.cancel(true));
        pendingRequests.clear();
        stopTickPoll();
        notificationHandlers.clear();
    }

    private void stopTickPoll() {
        tickPollActive = false;
        scheduler.getQueue().clear();
    }

    private CompletableFuture<JsonElement> sendRequest(String method, JsonElement params) {
        if (!connected || webSocket == null) {
            return CompletableFuture.failedFuture(new IllegalStateException("Not connected to MSMP WebSocket."));
        }
        long id = nextId.getAndIncrement();
        JsonObject request = new JsonObject();
        request.addProperty("jsonrpc", "2.0");
        request.addProperty("method", method);
        if (params != null) {
            if (params.isJsonObject()) request.add("params", params.getAsJsonObject());
            else if (params.isJsonArray()) request.add("params", params.getAsJsonArray());
        }
        request.addProperty("id", id);
        CompletableFuture<JsonElement> future = new CompletableFuture<>();
        pendingRequests.put(id, future);
        String payload = gson.toJson(request);
        debugLog("-> " + payload);
        try {
            webSocket.sendText(payload, true);
        } catch (Exception e) {
            pendingRequests.remove(id);
            future.completeExceptionally(e);
        }
        return future;
    }

    private CompletableFuture<JsonElement> tryMethods(List<String> methods, JsonElement params) {
        CompletableFuture<JsonElement> f = new CompletableFuture<>();
        tryMethodSequential(0, methods, params, f);
        return f;
    }

    private void tryMethodSequential(int idx, List<String> methods, JsonElement params, CompletableFuture<JsonElement> target) {
        if (idx >= methods.size()) {
            target.completeExceptionally(new RuntimeException("No compatible method"));
            return;
        }
        String method = methods.get(idx);
        sendRequest(method, params).whenComplete((res, err) -> {
            if (err != null) tryMethodSequential(idx + 1, methods, params, target);
            else target.complete(res);
        });
    }

    private void onNotification(String method, JsonElement params) {
        Consumer<JsonElement> h = notificationHandlers.get(method);
        if (h == null) h = notificationHandlers.get(method.toUpperCase());
        if (h != null) h.accept(params);
    }

    private void bindNotification(String key, Consumer<JsonElement> handler) {
        notificationHandlers.put(key, handler);
        notificationHandlers.put(key.toUpperCase(), handler);
    }

    @Override
    public void subscribeToTicks(Consumer<TickInfo> onTick) {
        this.tickConsumer = onTick;
        bindNotification("minecraft:notification/server/tick", payload -> {
            TickInfo info = parseTickPayload(payload);
            if (info != null) onTick.accept(info);
        });
        bindNotification("minecraft:notification/tick", payload -> {
            TickInfo info = parseTickPayload(payload);
            if (info != null) onTick.accept(info);
        });
        bindNotification("TICK_INFO", payload -> {
            Double v = extractNumber(payload, "tick_time_ms");
            if (v != null) onTick.accept(new TickInfo(v));
        });
        startTickPollFallback();
    }

    private void startTickPollFallback() {
        if (tickPollActive) return;
        tickPollActive = true;
        scheduler.scheduleAtFixedRate(() -> {
            if (!connected || tickConsumer == null) return;
            List<String> methods = List.of("minecraft:server/status", "minecraft:server/status/");
            tryMethods(methods, null).thenAccept(res -> {
                Double ms = extractNumber(res, "mspt");
                if (ms == null) ms = extractNumber(res, "tick_time_ms");
                if (ms == null) {
                    Double tps = extractNumber(res, "tps");
                    if (tps != null && tps > 0) ms = 1000.0 / tps;
                }
                if (ms != null) tickConsumer.accept(new TickInfo(ms));
            }).exceptionally(e -> null);
            }, 1500, 1500, TimeUnit.MILLISECONDS);
    }

    @Override
    public void subscribeToPlayers(Consumer<List<Player>> onPlayerChange) {
        bindNotification("minecraft:notification/players/joined", payload -> refreshPlayers(onPlayerChange));
        bindNotification("minecraft:notification/players/left", payload -> refreshPlayers(onPlayerChange));
        bindNotification("PLAYER_JOINED", payload -> refreshPlayers(onPlayerChange));
        bindNotification("PLAYER_LEFT", payload -> refreshPlayers(onPlayerChange));
        bindNotification("minecraft:notification/operators/added", payload -> refreshPlayers(onPlayerChange));
        bindNotification("minecraft:notification/operators/removed", payload -> refreshPlayers(onPlayerChange));
        bindNotification("minecraft:notification/server/status", payload -> {
            List<Player> fromStatus = parsePlayersFromServerStatus(payload);
            if (fromStatus != null) {
                enrichWithOperators(fromStatus).thenAccept(onPlayerChange);
            }
        });
    }

    @Override
    public void subscribeToChat(Consumer<String> onChatMessage) {
        bindNotification("minecraft:notification/chat/message", payload -> {
            String m = extractString(payload, "message");
            if (m == null) m = extractString(payload, "text");
            if (m != null) onChatMessage.accept(m);
        });
        bindNotification("CHAT_MESSAGE", payload -> {
            String m = extractString(payload, "message");
            if (m == null) m = extractString(payload, "text");
            if (m != null) onChatMessage.accept(m);
        });
    }

    @Override
    public void subscribeToLifecycle(Consumer<String> onEvent) {
        bindNotification("minecraft:notification/server/started", p -> onEvent.accept("started"));
        bindNotification("minecraft:notification/server/stopping", p -> onEvent.accept("stopping"));
        bindNotification("minecraft:notification/server/saving", p -> onEvent.accept("saving"));
        bindNotification("minecraft:notification/server/saved", p -> onEvent.accept("saved"));
        bindNotification("minecraft:notification/server/status", p -> onEvent.accept("status"));
    }

    private void refreshPlayers(Consumer<List<Player>> onPlayerChange) {
        getPlayers().thenAccept(onPlayerChange);
    }

    private String extractString(JsonElement payload, String key) {
        if (payload == null) return null;
        if (payload.isJsonObject() && payload.getAsJsonObject().has(key)) {
            try {
                return payload.getAsJsonObject().get(key).getAsString();
            } catch (Exception ignored) {}
        }
        if (payload.isJsonArray()) {
            JsonArray a = payload.getAsJsonArray();
            if (!a.isEmpty() && a.get(0).isJsonObject()) {
                JsonObject o = a.get(0).getAsJsonObject();
                if (o.has(key)) {
                    try {
                        return o.get(key).getAsString();
                    } catch (Exception ignored) {}
                }
            }
        }
        return null;
    }

    @Override
    public CompletableFuture<List<Player>> getPlayers() {
        List<String> methods = List.of("minecraft:players", "minecraft:players/", "minecraft:players/list");
        return tryMethods(methods, null).thenCompose(result -> {
            JsonArray arr = null;
            if (result != null) {
                if (result.isJsonArray()) {
                    arr = result.getAsJsonArray();
                } else if (result.isJsonObject()) {
                    JsonObject obj = result.getAsJsonObject();
                    if (obj.has("players") && obj.get("players").isJsonArray())
                        arr = obj.getAsJsonArray("players");
                    else if (obj.has("list") && obj.get("list").isJsonArray())
                        arr = obj.getAsJsonArray("list");
                    else if (obj.entrySet().size() == 1 && obj.entrySet().iterator().next().getValue().isJsonArray()) {
                        arr = obj.entrySet().iterator().next().getValue().getAsJsonArray();
                    }
                }
            }
            List<Player> players = arr == null ? new ArrayList<>() : decodePlayersArray(arr);
            return enrichWithOperators(players);
        });
    }

    private CompletableFuture<List<Player>> enrichWithOperators(List<Player> players) {
        return getOperatorIds().thenApply(opIds -> {
            for (Player p : players) {
                if (p.uuid != null && opIds.contains(p.uuid.toString())) {
                    p.isOperator = true;
                }
            }
            return players;
        });
    }

    private List<Player> decodePlayersArray(JsonArray arr) {
        List<Player> players = new ArrayList<>();
        for (JsonElement e : arr) {
            if (!e.isJsonObject()) continue;
            JsonObject o = e.getAsJsonObject();
            String name = o.has("name") ? safeGetString(o, "name") : "Unknown";
            String uuidStr = o.has("uuid") ? safeGetString(o, "uuid") : (o.has("id") ? safeGetString(o, "id") : UUID.randomUUID().toString());
            UUID uuid;
            try {
                uuid = UUID.fromString(uuidStr);
            } catch (Exception ex) {
                uuid = UUID.randomUUID();
            }
            Player p = new Player(name, uuid);
            if (o.has("is_operator")) {
                try {
                    p.isOperator = o.get("is_operator").getAsBoolean();
                } catch (Exception ignored) {}
            }
            if (o.has("ping")) {
                try {
                    p.ping = o.get("ping").getAsInt();
                } catch (Exception ignored) {}
            }
            if (o.has("address")) {
                try {
                    p.address = safeGetString(o, "address");
                } catch (Exception ignored) {}
            }
            players.add(p);
        }
        return players;
    }

    private List<Player> parsePlayersFromServerStatus(JsonElement payload) {
        try {
            JsonObject status = null;
            if (payload == null) return null;
            if (payload.isJsonObject() && payload.getAsJsonObject().has("status") && payload.getAsJsonObject().get("status").isJsonObject()) {
                status = payload.getAsJsonObject().getAsJsonObject("status");
            } else if (payload.isJsonArray() && payload.getAsJsonArray().size() > 0) {
                JsonElement first = payload.getAsJsonArray().get(0);
                if (first.isJsonObject() && first.getAsJsonObject().has("status") && first.getAsJsonObject().get("status").isJsonObject()) {
                    status = first.getAsJsonObject().getAsJsonObject("status");
                }
            }
            if (status == null) return null;
            if (status.has("players") && status.get("players").isJsonArray()) {
                return decodePlayersArray(status.getAsJsonArray("players"));
            }
        } catch (Exception ignored) {}
        return null;
    }

    private String safeGetString(JsonObject o, String k) {
        try {
            return o.get(k).getAsString();
        } catch (Exception e) {
            return "";
        }
    }

    private CompletableFuture<Set<String>> getOperatorIds() {
        List<String> methods = List.of("minecraft:operators", "minecraft:operators/");
        return tryMethods(methods, null).thenApply(result -> {
            Set<String> ops = new HashSet<>();
            JsonArray arr = null;
            if (result != null) {
                if (result.isJsonArray()) {
                    arr = result.getAsJsonArray();
                } else if (result.isJsonObject()) {
                    JsonObject obj = result.getAsJsonObject();
                    if (obj.has("operators") && obj.get("operators").isJsonArray()) {
                        arr = obj.getAsJsonArray("operators");
                    } else if (obj.entrySet().size() == 1 && obj.entrySet().iterator().next().getValue().isJsonArray()) {
                        arr = obj.entrySet().iterator().next().getValue().getAsJsonArray();
                    }
                }
            }
            if (arr == null) return ops;
            for (JsonElement e : arr) {
                if (!e.isJsonObject()) continue;
                JsonObject op = e.getAsJsonObject();
                if (op.has("player") && op.get("player").isJsonObject()) {
                    JsonObject pl = op.getAsJsonObject("player");
                    if (pl.has("id")) {
                        try {
                            String id = pl.get("id").getAsString();
                            ops.add(id);
                        } catch (Exception ignored) {}
                    }
                } else if (op.has("id")) {
                    try {
                        String id = op.get("id").getAsString();
                        ops.add(id);
                    } catch (Exception ignored) {}
                }
            }
            return ops;
        }).exceptionally(e -> new HashSet<>());
    }

    @Override
    public CompletableFuture<Void> kickPlayer(String uuid, String reason) {
        List<String> methods = List.of("minecraft:players/kick", "minecraft:players/kick/");
        JsonObject playerObj = new JsonObject();
        playerObj.addProperty("id", uuid);
        JsonObject kickObj = new JsonObject();
        kickObj.add("player", playerObj);
        if (reason != null && !reason.isEmpty()) {
            JsonObject msg = new JsonObject();
            msg.addProperty("literal", reason);
            kickObj.add("message", msg);
        }
        JsonObject params = new JsonObject();
        JsonArray kickArr = new JsonArray();
        kickArr.add(kickObj);
        params.add("kick", kickArr);
        return tryMethods(methods, params).thenApply(v -> null);
    }

    @Override
    public CompletableFuture<Void> banPlayer(String uuid, String reason) {
        List<String> methods = List.of("minecraft:bans/add", "minecraft:bans/add/");
        JsonObject pl = new JsonObject();
        pl.addProperty("id", uuid);
        JsonObject ban = new JsonObject();
        ban.add("player", pl);
        if (reason != null && !reason.isEmpty()) ban.addProperty("reason", reason);
        JsonObject params = new JsonObject();
        JsonArray add = new JsonArray();
        add.add(ban);
        params.add("add", add);
        return tryMethods(methods, params).thenApply(v -> null);
    }

    @Override
    public CompletableFuture<Void> opPlayer(String uuid, int level) {
        List<String> methods = List.of("minecraft:operators/add", "minecraft:operators/add/");
        JsonObject pl = new JsonObject();
        pl.addProperty("id", uuid);
        JsonObject op = new JsonObject();
        op.add("player", pl);
        op.addProperty("permissionLevel", level);
        JsonObject params = new JsonObject();
        JsonArray add = new JsonArray();
        add.add(op);
        params.add("add", add);
        return tryMethods(methods, params).thenApply(v -> null);
    }

    @Override
    public CompletableFuture<Void> deopPlayer(String uuid) {
        List<String> methods = List.of("minecraft:operators/remove", "minecraft:operators/remove/");
        JsonObject pl = new JsonObject();
        pl.addProperty("id", uuid);
        JsonObject params = new JsonObject();
        JsonArray rem = new JsonArray();
        rem.add(pl);
        params.add("remove", rem);
        return tryMethods(methods, params).thenApply(v -> null);
    }

    @Override
    public CompletableFuture<List<GameRule>> getGameRules() {
        List<String> methods = List.of("minecraft:gamerules", "minecraft:gamerules/", "minecraft:gamerules/list");
        return tryMethods(methods, null).thenApply(result -> {
            JsonArray arr = null;
            if (result != null) {
                if (result.isJsonArray()) {
                    arr = result.getAsJsonArray();
                } else if (result.isJsonObject()) {
                    JsonObject obj = result.getAsJsonObject();
                    if (obj.has("gamerules") && obj.get("gamerules").isJsonArray()) {
                        arr = obj.getAsJsonArray("gamerules");
                    } else if (obj.entrySet().size() == 1 && obj.entrySet().iterator().next().getValue().isJsonArray()) {
                        arr = obj.entrySet().iterator().next().getValue().getAsJsonArray();
                    }
                }
            }
            if (arr == null) return List.of();
            List<GameRule> rules = new ArrayList<>();
            for (JsonElement e : arr) {
                if (!e.isJsonObject()) continue;
                JsonObject o = e.getAsJsonObject();
                String name = o.has("key") ? safeGetString(o, "key") : (o.has("name") ? safeGetString(o, "name") : "");
                String value = o.has("value") ? safeGetString(o, "value") : "";
                String type = o.has("type") ? safeGetString(o, "type") : "string";
                rules.add(new GameRule(name, value, type));
            }
            return rules;
        });
    }

    @Override
    public CompletableFuture<Void> setGameRule(String ruleName, String value) {
        List<String> methods = List.of("minecraft:gamerules/update", "minecraft:gamerules/update/");
        JsonObject gr = new JsonObject();
        gr.addProperty("key", ruleName);
        gr.addProperty("value", value);
        JsonObject params = new JsonObject();
        params.add("gamerule", gr);
        return tryMethods(methods, params).thenApply(v -> null);
    }

    @Override
    public void discoverApi() {
        sendRequest("rpc.discover", null);
    }

    private TickInfo parseTickPayload(JsonElement payload) {
        if (payload == null) return null;
        if (payload.isJsonArray()) {
            JsonArray a = payload.getAsJsonArray();
            if (!a.isEmpty() && a.get(0).isJsonObject()) {
                Double v = extractNumber(a.get(0).getAsJsonObject(), "mspt");
                if (v == null) v = extractNumber(a.get(0).getAsJsonObject(), "tick_time_ms");
                if (v != null) return new TickInfo(v);
            }
            return null;
        }
        if (payload.isJsonObject()) {
            Double v = extractNumber(payload.getAsJsonObject(), "mspt");
            if (v == null) v = extractNumber(payload.getAsJsonObject(), "tick_time_ms");
            if (v != null) return new TickInfo(v);
        }
        return null;
    }

    private Double extractNumber(JsonElement obj, String key) {
        if (obj == null) return null;
        if (obj.isJsonObject() && obj.getAsJsonObject().has(key)) {
            try {
                return obj.getAsJsonObject().get(key).getAsDouble();
            } catch (Exception ignored) {}
        }
        return null;
    }

    private class WebSocketListener implements WebSocket.Listener {
        private final StringBuilder textBuilder = new StringBuilder();
        private final CompletableFuture<Boolean> connectFuture;

        public WebSocketListener(CompletableFuture<Boolean> connectFuture) {
            this.connectFuture = connectFuture;
        }

        @Override
        public void onOpen(WebSocket ws) {
            webSocket = ws;
            connected = true;
            debugLog("Connected");
            connectFuture.complete(true);
            MSMPClient.this.discoverApi();
            WebSocket.Listener.super.onOpen(ws);
        }

        @Override
        public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
            textBuilder.append(data);
            if (last) {
                String raw = textBuilder.toString();
                debugLog("<- " + raw);
                try {
                    JsonElement parsed = gson.fromJson(raw, JsonElement.class);
                    if (parsed == null)
                        return WebSocket.Listener.super.onText(webSocket, data, last);
                    if (parsed.isJsonArray()) {
                        for (JsonElement elem : parsed.getAsJsonArray()) {
                            handleMessageElement(elem);
                        }
                    } else {
                        handleMessageElement(parsed);
                    }
                } catch (Exception e) {
                    debugLog("onText error: " + e.getMessage());
                    pendingRequests.forEach((k, f) -> f.completeExceptionally(e));
                    pendingRequests.clear();
                } finally {
                    textBuilder.setLength(0);
                }
            }
            return WebSocket.Listener.super.onText(webSocket, data, last);
        }

        private void handleMessageElement(JsonElement elem) {
            if (!elem.isJsonObject()) return;
            JsonObject message = elem.getAsJsonObject();
            if (message.has("id")) {
                try {
                    long id = message.get("id").getAsLong();
                    CompletableFuture<JsonElement> future = pendingRequests.remove(id);
                    if (future != null) {
                        if (message.has("result")) {
                            future.complete(message.get("result"));
                        } else if (message.has("error")) {
                            JsonObject err = message.getAsJsonObject("error");
                            String msg = err.has("message") ? err.get("message").getAsString() : "Unknown error";
                            future.completeExceptionally(new RuntimeException("MSMP Error: " + msg));
                        } else {
                            future.complete(null);
                        }
                    }
                } catch (Exception e) {
                    debugLog("Result handling error: " + e.getMessage());
                }
            } else if (message.has("method")) {
                String method = message.get("method").getAsString();
                JsonElement params = message.has("params") ? message.get("params") : null;
                onNotification(method, params);
            }
        }

        @Override
        public CompletionStage<?> onClose(WebSocket webSocket, int statusCode, String reason) {
            connected = false;
            debugLog("Closed " + statusCode + " " + reason);
            if (!connectFuture.isDone()) connectFuture.complete(false);
            pendingRequests.forEach((k, f) -> f.completeExceptionally(new RuntimeException("Connection closed")));
            pendingRequests.clear();
            stopTickPoll();
            return WebSocket.Listener.super.onClose(webSocket, statusCode, reason);
        }

        @Override
        public void onError(WebSocket webSocket, Throwable error) {
            connected = false;
            debugLog("Error " + error.getMessage());
            if (!connectFuture.isDone()) connectFuture.completeExceptionally(error);
            pendingRequests.forEach((k, f) -> f.completeExceptionally(error));
            pendingRequests.clear();
            stopTickPoll();
            WebSocket.Listener.super.onError(webSocket, error);
        }

        @Override
        public CompletionStage<?> onPing(WebSocket webSocket, ByteBuffer message) {
            webSocket.sendPong(message);
            return WebSocket.Listener.super.onPing(webSocket, message);
        }
    }
}