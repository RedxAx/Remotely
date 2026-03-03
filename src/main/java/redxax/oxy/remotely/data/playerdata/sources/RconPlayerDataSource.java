package redxax.oxy.remotely.data.playerdata.sources;

import redxax.oxy.remotely.data.playerdata.PlayerData;
import redxax.oxy.remotely.data.playerdata.PlayerDataSnapshot;
import redxax.oxy.remotely.data.playerdata.PlayerDataSource;
import redxax.oxy.remotely.data.playerdata.PlayerEnderChest;
import redxax.oxy.remotely.data.playerdata.PlayerItem;
import restudio.rebase.minecraft.RconClient;
import restudio.rebase.instance.Instance;
import restudio.rebase.util.Executors;
import restudio.rescreen.debug.DebugManager;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

public class RconPlayerDataSource implements PlayerDataSource {
    private static final String ID = "rcon";
    private static final ConcurrentHashMap<String, RconSession> SESSIONS = new ConcurrentHashMap<>();
    private static final long IDLE_CLOSE_MS = 0L;
    private static final int[] INVENTORY_SLOTS = buildInventorySlots();
    private static final int[] ENDER_SLOTS = buildEnderSlots();

    private final Instance instance;
    private final boolean includeStats;
    public RconPlayerDataSource(Instance instance) {
        this(instance, false);
    }

    public RconPlayerDataSource(Instance instance, boolean includeStats) {
        this.instance = instance;
        this.includeStats = includeStats;
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

                if (includeStats && session.isStable()) {
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
        InventorySlots inventorySlots = fetchInventorySlots(session, password, selector);
        EquipmentSlots equipmentSlots = fetchEquipmentSlots(session, password, selector);
        List<PlayerItem> enderOverride = fetchEnderSlots(session, password, selector);
        String inventory = null;
        if (inventorySlots == null) {
            inventory = extractDataValue(session.executeWithDebug(password, resolveTimeout(), "data get entity " + selector + " Inventory", true, "RconDebug"));
            if (inventory == null || inventory.isBlank()) {
                inventory = extractDataValue(session.executeWithDebug(password, resolveTimeout(), "data get entity " + selector + " inventory", true, "RconDebug"));
            }
            debugField("Inventory", inventory);
        }
        String armor = null;
        String offhand = null;
        String equipment = null;
        if (equipmentSlots == null) {
            armor = extractDataValue(session.executeWithDebug(password, resolveTimeout(), "data get entity " + selector + " ArmorItems", false, "RconDebug"));
            if (armor == null || armor.isBlank()) {
                armor = extractDataValue(session.executeWithDebug(password, resolveTimeout(), "data get entity " + selector + " armor", false, "RconDebug"));
            }
            debugField("ArmorItems", armor);
            offhand = extractDataValue(session.executeWithDebug(password, resolveTimeout(), "data get entity " + selector + " OffhandItems", false, "RconDebug"));
            if (offhand == null || offhand.isBlank()) {
                offhand = extractDataValue(session.executeWithDebug(password, resolveTimeout(), "data get entity " + selector + " Offhand", false, "RconDebug"));
            }
            debugField("OffhandItems", offhand);
            equipment = extractDataValue(session.executeWithDebug(password, resolveTimeout(), "data get entity " + selector + " equipment", true, "RconDebug"));
            if (equipment == null || equipment.isBlank()) {
                equipment = extractDataValue(session.executeWithDebug(password, resolveTimeout(), "data get entity " + selector + " Equipment", true, "RconDebug"));
            }
            debugField("equipment", equipment);
        }
        String effects = null;
        String attributes = null;
        String ender = null;
        if (enderOverride == null) {
            ender = extractDataValue(session.executeWithDebug(password, resolveTimeout(), "data get entity " + selector + " EnderItems", false, "RconDebug"));
            if (ender == null || ender.isBlank()) {
                ender = extractDataValue(session.executeWithDebug(password, resolveTimeout(), "data get entity " + selector + " enderChest", false, "RconDebug"));
            }
            debugField("EnderItems", ender);
        }
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

        Map<String, String> fields = new LinkedHashMap<>();
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
        if (inventorySlots != null || equipmentSlots != null || enderOverride != null) {
            List<PlayerItem> nextInventory = inventorySlots != null ? inventorySlots.inventory() : parsed.inventory();
            List<PlayerItem> nextArmor = parsed.armor();
            if (equipmentSlots != null && !equipmentSlots.armor().isEmpty()) {
                nextArmor = equipmentSlots.armor();
            } else if (inventorySlots != null && !inventorySlots.armor().isEmpty()) {
                nextArmor = inventorySlots.armor();
            }
            List<PlayerItem> nextOffhand = parsed.offhand();
            if (equipmentSlots != null && !equipmentSlots.offhand().isEmpty()) {
                nextOffhand = equipmentSlots.offhand();
            } else if (inventorySlots != null && !inventorySlots.offhand().isEmpty()) {
                nextOffhand = inventorySlots.offhand();
            }
            PlayerEnderChest nextEnder = enderOverride != null ? new PlayerEnderChest(enderOverride) : parsed.enderChest();
            parsed = new PlayerData(parsed.health(), parsed.food(), parsed.saturation(), parsed.experienceLevel(),
                    parsed.experienceProgress(), parsed.totalExperience(), parsed.location(), parsed.gameMode(),
                    parsed.flying(), parsed.fallFlying(), nextInventory, nextArmor, nextOffhand,
                    nextEnder, parsed.effects(), parsed.attributes(), parsed.statistics(),
                    parsed.flattenedStatistics(), parsed.lastModified(), parsed.onlineOnly());
        }
        DebugManager.getInstance().log("RconDebug", "snapshot inv=" + parsed.inventory().size() + " armor=" + parsed.armor().size() + " offhand=" + parsed.offhand().size() + " effects=" + parsed.effects().size() + " attributes=" + parsed.attributes().size());
        return parsed;
    }

    private InventorySlots fetchInventorySlots(RconSession session, String password, String selector) {
        try {
            List<PlayerItem> inventory = new ArrayList<>();
            List<PlayerItem> armor = new ArrayList<>();
            List<PlayerItem> offhand = new ArrayList<>();
            for (int slot : INVENTORY_SLOTS) {
                String path = "Inventory[{Slot:" + slot + "b}]";
                String value = extractDataValue(session.execute(password, resolveTimeout(), "data get entity " + selector + " " + path, false));
                if (value == null || value.isBlank()) continue;
                PlayerItem item = parseItemFromSnbt(value, slot);
                if (item == null) continue;
                int resolvedSlot = item.slot();
                if (resolvedSlot == -106) {
                    offhand.add(item);
                } else if (resolvedSlot >= 100 && resolvedSlot <= 103) {
                    armor.add(item);
                } else {
                    inventory.add(item);
                }
            }
            return new InventorySlots(inventory, armor, offhand);
        } catch (Exception e) {
            DebugManager.getInstance().log("RconPlayerDataSource", "RCON inventory slot fetch failed: " + e.getClass().getSimpleName() + ": " + e.getMessage());
            return null;
        }
    }

    private EquipmentSlots fetchEquipmentSlots(RconSession session, String password, String selector) {
        try {
            List<PlayerItem> armor = new ArrayList<>();
            PlayerItem head = fetchEquipmentItem(session, password, selector, "head", 103);
            if (head != null) armor.add(head);
            PlayerItem chest = fetchEquipmentItem(session, password, selector, "chest", 102);
            if (chest != null) armor.add(chest);
            PlayerItem legs = fetchEquipmentItem(session, password, selector, "legs", 101);
            if (legs != null) armor.add(legs);
            PlayerItem feet = fetchEquipmentItem(session, password, selector, "feet", 100);
            if (feet != null) armor.add(feet);
            PlayerItem offhand = fetchEquipmentItem(session, password, selector, "offhand", -106);
            if (offhand == null) offhand = fetchEquipmentItem(session, password, selector, "off_hand", -106);
            if (offhand == null) offhand = fetchEquipmentItem(session, password, selector, "offHand", -106);
            List<PlayerItem> offhandList = offhand != null ? List.of(offhand) : Collections.emptyList();
            return new EquipmentSlots(armor, offhandList);
        } catch (Exception e) {
            DebugManager.getInstance().log("RconPlayerDataSource", "RCON equipment fetch failed: " + e.getClass().getSimpleName() + ": " + e.getMessage());
            return null;
        }
    }

    private PlayerItem fetchEquipmentItem(RconSession session, String password, String selector, String key, int slot) throws Exception {
        String value = extractDataValue(session.execute(password, resolveTimeout(), "data get entity " + selector + " equipment." + key, false));
        if (value == null || value.isBlank()) return null;
        return parseItemFromSnbt(value, slot);
    }

    private List<PlayerItem> fetchEnderSlots(RconSession session, String password, String selector) {
        try {
            List<PlayerItem> items = new ArrayList<>();
            for (int slot : ENDER_SLOTS) {
                String path = "EnderItems[{Slot:" + slot + "b}]";
                String value = extractDataValue(session.execute(password, resolveTimeout(), "data get entity " + selector + " " + path, false));
                if (value == null || value.isBlank()) continue;
                PlayerItem item = parseItemFromSnbt(value, slot);
                if (item != null) items.add(item);
            }
            return items;
        } catch (Exception e) {
            DebugManager.getInstance().log("RconPlayerDataSource", "RCON ender fetch failed: " + e.getClass().getSimpleName() + ": " + e.getMessage());
            return null;
        }
    }

    private PlayerItem parseItemFromSnbt(String snbt, int fallbackSlot) {
        if (snbt == null || snbt.isBlank()) return null;
        Nbt.Tag tag = Nbt.parseSnbt(snbt);
        if (tag == null) return null;
        if (tag.value instanceof List<?> list) {
            if (list.isEmpty()) return null;
            if (list.get(0) instanceof Nbt.Tag first && (first.name == null || first.name.isEmpty())) {
                for (Object obj : list) {
                    if (obj instanceof Nbt.Tag itemTag) {
                        PlayerItem parsed = toPlayerItem(itemTag, fallbackSlot);
                        if (parsed != null) return parsed;
                    }
                }
                return null;
            }
            return toPlayerItem(new Nbt.Tag("item", list), fallbackSlot);
        }
        return null;
    }

    private PlayerItem toPlayerItem(Nbt.Tag tag, int fallbackSlot) {
        if (tag == null) return null;
        String id = getString(tag, "id", null);
        if (id == null) return null;
        int count = getByteAsInt(tag, "Count", -1);
        if (count < 0) count = getInt(tag, "count", 0);
        int slot = getByteAsInt(tag, "Slot", Integer.MIN_VALUE);
        if (slot == Integer.MIN_VALUE) slot = fallbackSlot;
        Map<String, Object> tagData = tagToMap(tag, "tag");
        if (tagData.isEmpty()) tagData = tagToMap(tag, "components");
        return new PlayerItem(id, count, slot, tagData);
    }

    private Map<String, Object> tagToMap(Nbt.Tag root, String key) {
        Nbt.Tag tag = Nbt.find(root, key);
        if (tag == null) return Collections.emptyMap();
        if (tag.value instanceof List<?> list) {
            return Nbt.toMap(new Nbt.Tag(key, list));
        }
        if (tag.value instanceof Map<?, ?> map) {
            Map<String, Object> out = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                out.put(String.valueOf(entry.getKey()), entry.getValue());
            }
            return out;
        }
        return Collections.emptyMap();
    }

    private String getString(Nbt.Tag root, String key, String def) {
        if (root == null) return def;
        Nbt.Tag t = Nbt.find(root, key);
        if (t == null || t.value == null) return def;
        if (t.value instanceof String s) return s;
        return String.valueOf(t.value);
    }

    private int getInt(Nbt.Tag root, String key, int def) {
        if (root == null) return def;
        Nbt.Tag t = Nbt.find(root, key);
        if (t == null || t.value == null) return def;
        if (t.value instanceof Number n) return n.intValue();
        return def;
    }

    private int getByteAsInt(Nbt.Tag root, String key, int def) {
        if (root == null) return def;
        Nbt.Tag t = Nbt.find(root, key);
        if (t == null || t.value == null) return def;
        if (t.value instanceof Number n) return n.intValue();
        return def;
    }


    private static int[] buildInventorySlots() {
        int[] slots = new int[45];
        int index = 0;
        for (int i = 0; i <= 35; i++) {
            slots[index++] = i;
        }
        for (int i = 80; i <= 83; i++) {
            slots[index++] = i;
        }
        for (int i = 100; i <= 103; i++) {
            slots[index++] = i;
        }
        slots[index] = -106;
        return slots;
    }

    private static int[] buildEnderSlots() {
        int[] slots = new int[27];
        for (int i = 0; i <= 26; i++) {
            slots[i] = i;
        }
        return slots;
    }

    private record InventorySlots(List<PlayerItem> inventory, List<PlayerItem> armor, List<PlayerItem> offhand) {
    }

    private record EquipmentSlots(List<PlayerItem> armor, List<PlayerItem> offhand) {
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
