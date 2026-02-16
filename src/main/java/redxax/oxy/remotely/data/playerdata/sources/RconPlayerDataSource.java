package redxax.oxy.remotely.data.playerdata.sources;

import redxax.oxy.remotely.data.playerdata.PlayerData;
import redxax.oxy.remotely.data.playerdata.PlayerDataSnapshot;
import redxax.oxy.remotely.data.playerdata.PlayerDataSource;
import redxax.oxy.remotely.data.playerdata.PlayerEnderChest;
import restudio.rebase.instance.Instance;
import restudio.rebase.util.Executors;
import restudio.rescreen.debug.DebugManager;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

public class RconPlayerDataSource implements PlayerDataSource {
    private static final String ID = "rcon";
    private static final ConcurrentHashMap<String, RconSession> SESSIONS = new ConcurrentHashMap<>();
    private static final long IDLE_CLOSE_MS = 0L;

    private final Instance instance;
    public RconPlayerDataSource(Instance instance) {
        this.instance = instance;
    }

    @Override
    public String getId() {
        return ID;
    }

    @Override
    public int getPriority() {
        return 30;
    }

    @Override
    public boolean isOnlineOnly() {
        return true;
    }

    @Override
    public CompletableFuture<PlayerDataSnapshot> fetch(UUID uuid, String name) {
        if (uuid == null || instance == null) return CompletableFuture.completedFuture(null);
        return CompletableFuture.supplyAsync(() -> {
            String host = resolveHost();
            int port = resolvePort();
            String password = instance.getServerProperties().getProperty("rcon.password", "");
            if (host == null || host.isBlank() || password.isBlank()) {
                return null;
            }
            try {
                RconSession session = SESSIONS.computeIfAbsent(host + ":" + port, k -> new RconSession(host, port));
                if (IDLE_CLOSE_MS > 0) session.closeIfIdle(IDLE_CLOSE_MS);

                String selector = resolveSelector(name, uuid);
                PlayerData data = fetchPlayerData(session, password, selector, name, uuid);
                if (data == null) return null;
                data = new PlayerData(data.health(), data.food(), data.saturation(), data.experienceLevel(),
                        data.experienceProgress(), data.totalExperience(), data.location(), data.gameMode(),
                        data.flying(), data.fallFlying(), data.inventory(), data.armor(), data.offhand(),
                        data.enderChest(), data.effects(), data.attributes(), data.statistics(),
                        PlayerDataParser.flattenStats(data.statistics()), data.lastModified(), true);

                if (session.isStable()) {
                    try {
                        String statsTarget = name != null && !name.isBlank() ? name : uuid.toString();
                        String statsResponse = session.execute(password, resolveTimeout(), "stats " + statsTarget, false);
                        if (statsResponse != null && !statsResponse.isBlank()) {
                            Map<String, Object> stats = PlayerDataParser.parseStats(statsResponse);
                            if (stats != null && !stats.isEmpty()) {
                                data = new PlayerData(data.health(), data.food(), data.saturation(), data.experienceLevel(),
                                        data.experienceProgress(), data.totalExperience(), data.location(), data.gameMode(),
                                        data.flying(), data.fallFlying(), data.inventory(), data.armor(), data.offhand(),
                                        data.enderChest(), data.effects(), data.attributes(), stats,
                                        PlayerDataParser.flattenStats(stats), data.lastModified(), true);
                            }
                        }
                    } catch (Exception e) {
                        DebugManager.getInstance().log("RconPlayerDataSource", "RCON stats failed: " + e.getClass().getSimpleName() + ": " + e.getMessage());
                    }
                }
                return new PlayerDataSnapshot(uuid, data, ID, getPriority());
            } catch (Exception e) {
                DebugManager.getInstance().log("RconPlayerDataSource", "RCON fetch failed: " + e.getMessage());
                return null;
            }
        }, Executors.IO);
    }

    private PlayerData fetchPlayerData(RconSession session, String password, String selector, String name, UUID uuid) throws Exception {
        PlayerData data = fetchPlayerDataByPaths(session, password, selector, name, uuid);
        if (data != null) return data;
        String fallback = resolveFallbackTarget(name, uuid);
        if (fallback != null && !fallback.isBlank() && !fallback.equals(selector)) {
            return fetchPlayerDataByPaths(session, password, fallback, name, uuid);
        }
        return null;
    }

    private PlayerData fetchPlayerDataByPaths(RconSession session, String password, String selector, String name, UUID uuid) throws Exception {
        if (!session.isStable()) return null;

        boolean hasData = false;

        String health = extractDataValue(session.executeWithDebug(password, resolveTimeout(), "data get entity " + selector + " Health", false, "RconDebug"));
        String food = extractDataValue(session.executeWithDebug(password, resolveTimeout(), "data get entity " + selector + " foodLevel", false, "RconDebug"));
        String saturation = extractDataValue(session.executeWithDebug(password, resolveTimeout(), "data get entity " + selector + " foodSaturationLevel", false, "RconDebug"));
        String xpLevel = extractDataValue(session.executeWithDebug(password, resolveTimeout(), "data get entity " + selector + " XpLevel", false, "RconDebug"));
        String xpProgress = extractDataValue(session.executeWithDebug(password, resolveTimeout(), "data get entity " + selector + " XpP", false, "RconDebug"));
        String xpTotal = extractDataValue(session.executeWithDebug(password, resolveTimeout(), "data get entity " + selector + " XpTotal", false, "RconDebug"));
        String pos = extractDataValue(session.executeWithDebug(password, resolveTimeout(), "data get entity " + selector + " Pos", false, "RconDebug"));
        String rotation = extractDataValue(session.executeWithDebug(password, resolveTimeout(), "data get entity " + selector + " Rotation", false, "RconDebug"));
        String dimension = extractDataValue(session.executeWithDebug(password, resolveTimeout(), "data get entity " + selector + " Dimension", false, "RconDebug"));
        String gameType = extractDataValue(session.executeWithDebug(password, resolveTimeout(), "data get entity " + selector + " playerGameType", false, "RconDebug"));
        String abilities = extractDataValue(session.executeWithDebug(password, resolveTimeout(), "data get entity " + selector + " abilities", false, "RconDebug"));
        String fallFlying = extractDataValue(session.executeWithDebug(password, resolveTimeout(), "data get entity " + selector + " FallFlying", false, "RconDebug"));
        String inventory = extractDataValue(session.executeWithDebug(password, resolveTimeout(), "data get entity " + selector + " Inventory", true, "RconDebug"));
        if (inventory == null || inventory.isBlank()) {
            inventory = extractDataValue(session.executeWithDebug(password, resolveTimeout(), "data get entity " + selector + " inventory", true, "RconDebug"));
        }
        debugField("Inventory", inventory);
        String armor = extractDataValue(session.executeWithDebug(password, resolveTimeout(), "data get entity " + selector + " ArmorItems", false, "RconDebug"));
        if (armor == null || armor.isBlank()) {
            armor = extractDataValue(session.executeWithDebug(password, resolveTimeout(), "data get entity " + selector + " armor", false, "RconDebug"));
        }
        debugField("ArmorItems", armor);
        String offhand = extractDataValue(session.executeWithDebug(password, resolveTimeout(), "data get entity " + selector + " OffhandItems", false, "RconDebug"));
        if (offhand == null || offhand.isBlank()) {
            offhand = extractDataValue(session.executeWithDebug(password, resolveTimeout(), "data get entity " + selector + " Offhand", false, "RconDebug"));
        }
        debugField("OffhandItems", offhand);
        String equipment = extractDataValue(session.executeWithDebug(password, resolveTimeout(), "data get entity " + selector + " equipment", true, "RconDebug"));
        if (equipment == null || equipment.isBlank()) {
            equipment = extractDataValue(session.executeWithDebug(password, resolveTimeout(), "data get entity " + selector + " Equipment", true, "RconDebug"));
        }
        debugField("equipment", equipment);
        String effects = null;
        String attributes = null;
        String ender = extractDataValue(session.executeWithDebug(password, resolveTimeout(), "data get entity " + selector + " EnderItems", false, "RconDebug"));
        if (ender == null || ender.isBlank()) {
            ender = extractDataValue(session.executeWithDebug(password, resolveTimeout(), "data get entity " + selector + " enderChest", false, "RconDebug"));
        }
        debugField("EnderItems", ender);
        effects = extractDataValue(session.executeWithDebug(password, resolveTimeout(), "data get entity " + selector + " ActiveEffects", false, "RconDebug"));
        if (effects == null || effects.isBlank()) {
            effects = extractDataValue(session.executeWithDebug(password, resolveTimeout(), "data get entity " + selector + " active_effects", true, "RconDebug"));
        }
        if (effects == null || effects.isBlank()) {
            effects = extractDataValue(session.executeWithDebug(password, resolveTimeout(), "data get entity " + selector + " activeEffects", true, "RconDebug"));
        }
        debugField("ActiveEffects", effects);
        attributes = extractDataValue(session.executeWithDebug(password, resolveTimeout(), "data get entity " + selector + " Attributes", false, "RconDebug"));
        if (attributes == null || attributes.isBlank()) {
            attributes = extractDataValue(session.executeWithDebug(password, resolveTimeout(), "data get entity " + selector + " attributes", true, "RconDebug"));
        }
        if (attributes == null || attributes.isBlank()) {
            attributes = extractDataValue(session.executeWithDebug(password, resolveTimeout(), "data get entity " + selector + " AttributeInstances", true, "RconDebug"));
        }
        debugField("Attributes", attributes);

        Map<String, String> fields = new java.util.LinkedHashMap<>();
        putField(fields, "Health", health);
        putField(fields, "foodLevel", food);
        putField(fields, "foodSaturationLevel", saturation);
        putField(fields, "XpLevel", xpLevel);
        putField(fields, "XpP", xpProgress);
        putField(fields, "XpTotal", xpTotal);
        putField(fields, "Pos", pos);
        putField(fields, "Rotation", rotation);
        putField(fields, "Dimension", dimension);
        putField(fields, "playerGameType", gameType);
        putField(fields, "abilities", abilities);
        putField(fields, "FallFlying", fallFlying);
        putField(fields, "Inventory", inventory);
        putField(fields, "equipment", equipment);
        putField(fields, "EnderItems", ender);
        putField(fields, "active_effects", effects);
        putField(fields, "attributes", attributes);

        PlayerData parsed = PlayerDataParser.parsePlayerDataFromFields(fields, true);
        if (parsed == null) {
            DebugManager.getInstance().log("RconPlayerDataSource", "RCON field snapshot parse failed for " + (name != null ? name : uuid));
            return null;
        }
        DebugManager.getInstance().log("RconDebug", "snapshot inv=" + parsed.inventory().size() + " armor=" + parsed.armor().size() + " offhand=" + parsed.offhand().size() + " effects=" + parsed.effects().size() + " attributes=" + parsed.attributes().size());
        return parsed;
    }

    private boolean appendValue(StringBuilder sb, String key, String value) {
        if (value == null || value.isBlank()) return false;
        if (sb.length() > 1 && sb.charAt(sb.length() - 1) != '{') sb.append(',');
        sb.append(key).append(':').append(value);
        return true;
    }

    private void putField(Map<String, String> fields, String key, String value) {
        if (fields == null || key == null || value == null) return;
        String v = value.trim();
        if (v.isBlank()) return;
        fields.put(key, v);
    }

    private void debugField(String label, String value) {
        String v = value != null ? value.trim() : "";
        DebugManager.getInstance().log("RconDebug", "field=" + label + " value=" + v);
    }

    private String extractDataValue(String response) {
        if (response == null) return null;
        String trimmed = response.trim();
        if (trimmed.isEmpty()) return null;
        String lower = trimmed.toLowerCase();
        if (lower.contains("no entity") || lower.contains("not found") || lower.contains("found no elements matching")) return null;
        int idx = trimmed.indexOf("entity data:");
        if (idx >= 0) {
            int colon = trimmed.indexOf(':', idx);
            if (colon >= 0 && colon + 1 < trimmed.length()) return trimmed.substring(colon + 1).trim();
        }
        int colon = trimmed.indexOf(':');
        if (colon >= 0 && colon + 1 < trimmed.length()) return trimmed.substring(colon + 1).trim();
        return trimmed;
    }

    private String resolveSelector(String name, UUID uuid) {
        if (name != null && !name.isBlank()) {
            String safeName = name.replace("\\\"", "");
            return "@a[name=\"" + safeName + "\",limit=1]";
        }
        return uuid != null ? uuid.toString() : "";
    }

    private String resolveFallbackTarget(String name, UUID uuid) {
        if (name != null && !name.isBlank()) return name;
        return uuid != null ? uuid.toString() : null;
    }

    private String safePreview(String response) {
        if (response == null) return "null";
        String cleaned = response.replace('\n', ' ').replace('\r', ' ').trim();
        int max = 240;
        if (cleaned.length() <= max) return cleaned;
        return cleaned.substring(0, max);
    }

    private String resolveHost() {
        String host = instance.getServerProperties().getProperty("rcon.ip", "");
        if (!host.isBlank()) return host;
        return "127.0.0.1";
    }

    private int resolvePort() {
        String portStr = instance.getServerProperties().getProperty("rcon.port", "25575");
        if (portStr.isBlank()) portStr = "25575";
        try {
            return Integer.parseInt(portStr);
        } catch (Exception ignored) {
            return 25575;
        }
    }

    private int resolveTimeout() {
        return 3500;
    }

    private String extractSnbt(String response) {
        if (response == null) return null;
        String normalized = response.replace("\r", "").replace("\n", " ");
        int start = indexOfIgnoreCase(normalized, "entity data:");
        if (start >= 0) {
            start = normalized.indexOf('{', start);
        } else {
            start = normalized.indexOf('{');
        }
        if (start < 0) return null;
        int end = findMatchingBraceEnd(normalized, start);
        if (end < 0) return null;
        return normalized.substring(start, end + 1);
    }

    private int indexOfIgnoreCase(String text, String target) {
        if (text == null || target == null) return -1;
        return text.toLowerCase().indexOf(target.toLowerCase());
    }

    private int findMatchingBraceEnd(String s, int start) {
        int depth = 0;
        boolean inQuotes = false;
        char quote = 0;
        for (int i = start; i < s.length(); i++) {
            char c = s.charAt(i);
            if (inQuotes) {
                if (c == '\\' && i + 1 < s.length()) {
                    i++;
                    continue;
                }
                if (c == quote) {
                    inQuotes = false;
                    quote = 0;
                }
                continue;
            }
            if (c == '\'' || c == '"') {
                inQuotes = true;
                quote = c;
                continue;
            }
            if (c == '{') {
                depth++;
                continue;
            }
            if (c == '}') {
                depth--;
                if (depth == 0) return i;
            }
        }
        return -1;
    }

    private static final class RconSession {
        private final String host;
        private final int port;

        private RconClient client;
        private String authedPassword;
        private long lastUsed;
        private int consecutiveFailures;
        private long stableUntil;

        private RconSession(String host, int port) {
            this.host = host;
            this.port = port;
            this.lastUsed = System.currentTimeMillis();
        }

        synchronized void closeIfIdle(long maxIdleMs) {
            if (client == null) return;
            long now = System.currentTimeMillis();
            if (now - lastUsed < maxIdleMs) return;
            closeNow();
        }

        synchronized boolean isStable() {
            return System.currentTimeMillis() >= stableUntil;
        }

        synchronized String execute(String password, int timeoutMs, String command) throws Exception {
            return execute(password, timeoutMs, command, true);
        }

        synchronized String execute(String password, int timeoutMs, String command, boolean allowMultiPacket) throws Exception {
            long now = System.currentTimeMillis();
            if (now < stableUntil && client == null) {
                throw new IllegalStateException("RCON backoff");
            }
            lastUsed = System.currentTimeMillis();
            ensureConnected(password, timeoutMs);
            try {
                String out = client.execute(command, allowMultiPacket);
                lastUsed = System.currentTimeMillis();
                consecutiveFailures = 0;
                return out;
            } catch (Exception e) {
                consecutiveFailures++;
                long backoff = Math.min(2500L, 250L * consecutiveFailures);
                stableUntil = System.currentTimeMillis() + backoff;
                DebugManager.getInstance().log("RconPlayerDataSource", "RCON command failed: " + command + " -> " + e.getClass().getSimpleName() + ": " + e.getMessage());
                closeNow();
                throw e;
            }
        }

        synchronized String executeWithDebug(String password, int timeoutMs, String command, boolean allowMultiPacket, String tag) throws Exception {
            long now = System.currentTimeMillis();
            if (now < stableUntil && client == null) {
                throw new IllegalStateException("RCON backoff");
            }
            lastUsed = System.currentTimeMillis();
            ensureConnected(password, timeoutMs);
            try {
                String out = client.executeWithDebug(command, allowMultiPacket, tag);
                lastUsed = System.currentTimeMillis();
                consecutiveFailures = 0;
                return out;
            } catch (Exception e) {
                consecutiveFailures++;
                long backoff = Math.min(2500L, 250L * consecutiveFailures);
                stableUntil = System.currentTimeMillis() + backoff;
                DebugManager.getInstance().log("RconPlayerDataSource", "RCON command failed: " + command + " -> " + e.getClass().getSimpleName() + ": " + e.getMessage());
                closeNow();
                throw e;
            }
        }

        private void ensureConnected(String password, int timeoutMs) throws Exception {
            if (client == null) {
                client = new RconClient(host, port, timeoutMs);
                authedPassword = null;
            }
            if (authedPassword == null || !authedPassword.equals(password)) {
                if (!client.authenticate(password)) {
                    closeNow();
                    throw new IllegalStateException("RCON auth failed");
                }
                authedPassword = password;
            }
        }

        private void closeNow() {
            if (client == null) return;
            try {
                client.close();
            } catch (Exception ignored) {
            }
            client = null;
            authedPassword = null;
        }
    }
}
