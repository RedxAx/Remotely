package redxax.oxy.remotely.data.playerdata.sources;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import redxax.oxy.remotely.data.playerdata.PlayerAttributeValue;
import redxax.oxy.remotely.data.playerdata.PlayerData;
import redxax.oxy.remotely.data.playerdata.PlayerEffect;
import redxax.oxy.remotely.data.playerdata.PlayerEnderChest;
import redxax.oxy.remotely.data.playerdata.PlayerItem;
import redxax.oxy.remotely.data.playerdata.PlayerLocation;
import redxax.oxy.remotely.data.playerdata.PlayerStatistic;
import restudio.rescreen.logging.LogSource;
import restudio.rescreen.logging.LogTypes;
import restudio.rescreen.logging.ReLog;


import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;

import restudio.rebase.platform.Async;
import java.util.zip.GZIPInputStream;
import java.util.zip.InflaterInputStream;

public final class PlayerDataParser {
    private static final Gson gson = new Gson();

    private PlayerDataParser() {
    }

    public static Async<PlayerData> parsePlayerData(byte[] raw) {
        return Async.supplyAsync(() -> parseRaw(raw));
    }

    public static PlayerData parsePlayerDataFromSnbt(String snbt) {
        if (snbt == null || snbt.isBlank()) return null;
        try {
            Nbt.Tag root = Nbt.parseSnbt(snbt);
            return parseRoot(root, true, System.currentTimeMillis());
        } catch (Exception e) {
            ReLog.logger(LogTypes.MINECRAFT).source(LogSource.application("Remotely")).component(PlayerDataParser.class).operation("Parse SNBT").error("Could not parse player data", e);
            return null;
        }
    }

    public static PlayerData parsePlayerDataFromFields(Map<String, String> fields, boolean onlineOnly) {
        if (fields == null || fields.isEmpty()) return null;
        List<Nbt.Tag> tags = new ArrayList<>();
        for (Map.Entry<String, String> entry : fields.entrySet()) {
            String value = entry.getValue();
            if (value == null || value.isBlank()) continue;
            Nbt.Tag parsed = Nbt.parseSnbt(value);
            if (parsed == null) continue;
            tags.add(new Nbt.Tag(entry.getKey(), parsed.value));
        }
        if (tags.isEmpty()) return null;
        return parseRoot(new Nbt.Tag("root", tags), onlineOnly, System.currentTimeMillis());
    }

    public static Map<String, Object> parseStats(String json) {
        try {
            if (json == null || json.isBlank()) return Collections.emptyMap();
            return gson.fromJson(json, new TypeToken<Map<String, Object>>() {}.getType());
        } catch (Exception ignored) {
            return Collections.emptyMap();
        }
    }

    public static List<PlayerStatistic> flattenStats(Map<String, Object> stats) {
        if (stats == null || stats.isEmpty()) return Collections.emptyList();
        List<PlayerStatistic> out = new ArrayList<>();
        flattenStatsInto("", stats, out);
        return out;
    }

    public static byte[] decodeBinary(String content) {
        if (content == null) return null;
        String trimmed = content.trim();
        try {
            return Base64.getDecoder().decode(trimmed);
        } catch (Exception ignored) {
            return trimmed.getBytes(StandardCharsets.ISO_8859_1);
        }
    }

    private static PlayerData parseRaw(byte[] raw) {
        try {
            if (raw == null || raw.length == 0) return null;
            Nbt.Tag root = Nbt.read(getStream(raw));
            if (root == null) return null;
            return parseRoot(root, false, System.currentTimeMillis());
        } catch (Exception e) {
            ReLog.logger(LogTypes.MINECRAFT).source(LogSource.application("Remotely")).component(PlayerDataParser.class).operation("Parse NBT").error("Could not parse player data", e);
            return null;
        }
    }

    private static PlayerData parseRoot(Nbt.Tag root, boolean onlineOnly, long timestamp) {
        double health = getFloat(root, "Health", -1f);
        int food = getInt(root, "foodLevel", -1);
        float saturation = getFloat(root, "foodSaturationLevel", -1f);
        int xpLevel = getInt(root, "XpLevel", -1);
        float xpProgress = getFloat(root, "XpP", -1f);
        int xpTotal = getInt(root, "XpTotal", -1);

        PlayerLocation location = parseLocation(root);
        String gameMode = parseGameMode(root);
        Nbt.Tag abilities = Nbt.find(root, "abilities");
        boolean flying = getByteAsInt(abilities, "flying", getByteAsInt(root, "flying", 0)) == 1;
        boolean fallFlying = getByteAsInt(root, "FallFlying", 0) == 1;

        List<PlayerItem> rawInventory = parseItemList(root, "Inventory");
        if (rawInventory.isEmpty()) rawInventory = parseItemList(root, "inventory");
        if (rawInventory.isEmpty()) {
        }

        List<PlayerItem> inventory = new ArrayList<>();
        List<PlayerItem> invArmor = new ArrayList<>();
        List<PlayerItem> invOffhand = new ArrayList<>();
        for (PlayerItem item : rawInventory) {
            if (item == null) continue;
            int slot = item.slot();
            if (isInventoryArmorSlot(slot)) {
                invArmor.add(item);
                continue;
            }
            if (isInventoryOffhandSlot(slot)) {
                invOffhand.add(item);
                continue;
            }
            inventory.add(item);
        }

        List<PlayerItem> armor = parseItemList(root, "ArmorItems");
        if (armor.isEmpty()) armor = parseItemList(root, "armor");
        if (armor.isEmpty()) armor = parseEquipmentArmor(root);
        if (armor.isEmpty()) armor = invArmor;
        if (armor.isEmpty()) {
        }

        List<PlayerItem> offhand = parseItemList(root, "Offhand");
        if (offhand.isEmpty()) offhand = parseItemList(root, "OffhandItems");
        if (offhand.isEmpty()) offhand = parseItemList(root, "offhand");
        if (offhand.isEmpty()) offhand = parseEquipmentOffhand(root);
        if (offhand.isEmpty()) offhand = parseHandItemsOffhand(root);
        if (offhand.isEmpty()) offhand = invOffhand;
        if (offhand.isEmpty()) {
        }

        PlayerEnderChest enderChest = new PlayerEnderChest(parseItemList(root, "EnderItems"));
        if (enderChest.items().isEmpty()) enderChest = new PlayerEnderChest(parseItemList(root, "enderChest"));
        List<PlayerEffect> effects = parseEffects(root);
        List<PlayerAttributeValue> attributes = parseAttributes(root);
        if (effects.isEmpty()) {
        }
        if (attributes.isEmpty()) {
        }

        return new PlayerData(health, food, saturation, xpLevel, xpProgress, xpTotal, location, gameMode, flying,
                fallFlying, inventory, armor, offhand, enderChest, effects, attributes, Collections.emptyMap(),
                Collections.emptyList(), timestamp, onlineOnly);
    }

    private static PlayerLocation parseLocation(Nbt.Tag root) {
        Nbt.Tag posTag = Nbt.find(root, "Pos");
        if (posTag == null || !(posTag.value instanceof List<?> positions) || positions.size() < 3) return null;
        double x = 0;
        double y = 0;
        double z = 0;
        x = toDouble(positions.get(0));
        y = toDouble(positions.get(1));
        z = toDouble(positions.get(2));
        Nbt.Tag rotTag = Nbt.find(root, "Rotation");
        float yaw = 0f;
        float pitch = 0f;
        if (rotTag != null && rotTag.value instanceof List<?> list && list.size() >= 2) {
            yaw = toFloat(list.get(0));
            pitch = toFloat(list.get(1));
        }
        String dimension = getString(root, "Dimension", null);
        if (dimension != null && dimension.startsWith("minecraft:")) {
            dimension = dimension.substring("minecraft:".length());
        }
        return new PlayerLocation(x, y, z, yaw, pitch, dimension);
    }

    private static String parseGameMode(Nbt.Tag root) {
        int type = getInt(root, "playerGameType", -1);
        if (type < 0) return null;
        return switch (type) {
            case 0 -> "survival";
            case 1 -> "creative";
            case 2 -> "adventure";
            case 3 -> "spectator";
            default -> String.valueOf(type);
        };
    }

    private static List<PlayerItem> parseItemList(Nbt.Tag root, String key) {
        Nbt.Tag invTag = Nbt.find(root, key);
        if (invTag == null || !(invTag.value instanceof List<?> list)) return Collections.emptyList();
        List<PlayerItem> items = new ArrayList<>();
        int index = 0;
        for (Object obj : list) {
            if (!(obj instanceof Nbt.Tag tag)) continue;
            String id = getString(tag, "id", null);
            int count = getByteAsInt(tag, "Count", -1);
            if (count < 0) count = getInt(tag, "count", 0);
            int slot = getByteAsInt(tag, "Slot", Integer.MIN_VALUE);
            if (slot == Integer.MIN_VALUE) slot = index;
            if (id == null) continue;
            Map<String, Object> tagData = tagToMap(tag, "tag");
            if (tagData.isEmpty()) tagData = tagToMap(tag, "components");
            items.add(new PlayerItem(id, count, slot, tagData));
            index++;
        }
        return items;
    }

    private static boolean isInventoryArmorSlot(int slot) {
        return slot >= 100 && slot <= 103;
    }

    private static boolean isInventoryOffhandSlot(int slot) {
        return slot == -106;
    }

    private static List<PlayerItem> parseEquipmentArmor(Nbt.Tag root) {
        Nbt.Tag equipment = Nbt.find(root, "equipment");
        if (equipment == null) equipment = Nbt.find(root, "Equipment");
        if (equipment == null) return Collections.emptyList();

        List<PlayerItem> armor = new ArrayList<>();
        PlayerItem head = parseEquipmentItem(equipment, "head", 103);
        if (head != null) armor.add(head);
        PlayerItem chest = parseEquipmentItem(equipment, "chest", 102);
        if (chest != null) armor.add(chest);
        PlayerItem legs = parseEquipmentItem(equipment, "legs", 101);
        if (legs != null) armor.add(legs);
        PlayerItem feet = parseEquipmentItem(equipment, "feet", 100);
        if (feet != null) armor.add(feet);
        return armor;
    }

    private static List<PlayerItem> parseEquipmentOffhand(Nbt.Tag root) {
        Nbt.Tag equipment = Nbt.find(root, "equipment");
        if (equipment == null) equipment = Nbt.find(root, "Equipment");
        if (equipment == null) return Collections.emptyList();

        PlayerItem offhand = parseEquipmentItem(equipment, "offhand", -106);
        if (offhand == null) offhand = parseEquipmentItem(equipment, "off_hand", -106);
        if (offhand == null) offhand = parseEquipmentItem(equipment, "offHand", -106);
        if (offhand == null) return Collections.emptyList();
        List<PlayerItem> out = new ArrayList<>(1);
        out.add(offhand);
        return out;
    }

    private static PlayerItem parseEquipmentItem(Nbt.Tag equipment, String key, int slot) {
        if (equipment == null) return null;
        Nbt.Tag item = Nbt.find(equipment, key);
        if (item == null) return null;
        String id = getString(item, "id", null);
        if (id == null || id.isBlank() || "minecraft:air".equalsIgnoreCase(id)) return null;
        int count = getByteAsInt(item, "Count", -1);
        if (count < 0) count = getInt(item, "count", 0);
        Map<String, Object> tagData = tagToMap(item, "tag");
        if (tagData.isEmpty()) tagData = tagToMap(item, "components");
        return new PlayerItem(id, count, slot, tagData);
    }

    private static List<PlayerItem> parseHandItemsOffhand(Nbt.Tag root) {
        Nbt.Tag handItems = Nbt.find(root, "HandItems");
        if (handItems == null || !(handItems.value instanceof List<?> list) || list.size() < 2) return Collections.emptyList();
        Object obj = list.get(1);
        if (!(obj instanceof Nbt.Tag tag)) return Collections.emptyList();
        String id = getString(tag, "id", null);
        if (id == null || id.isBlank() || "minecraft:air".equalsIgnoreCase(id)) return Collections.emptyList();
        int count = getByteAsInt(tag, "Count", -1);
        if (count < 0) count = getInt(tag, "count", 0);
        Map<String, Object> tagData = tagToMap(tag, "tag");
        List<PlayerItem> out = new ArrayList<>(1);
        out.add(new PlayerItem(id, count, -106, tagData));
        return out;
    }

    private static List<PlayerEffect> parseEffects(Nbt.Tag root) {
        Nbt.Tag tag = Nbt.find(root, "ActiveEffects");
        if (tag == null) tag = Nbt.find(root, "active_effects");
        if (tag == null) tag = Nbt.find(root, "activeEffects");
        if (tag == null || !(tag.value instanceof List<?> list)) return Collections.emptyList();
        List<PlayerEffect> effects = new ArrayList<>();
        for (Object obj : list) {
            if (!(obj instanceof Nbt.Tag effectTag)) continue;
            String id = getString(effectTag, "Id", null);
            if (id == null) id = getString(effectTag, "id", null);
            if (id == null) {
                int numericId = getInt(effectTag, "Id", -1);
                if (numericId >= 0) {
                    id = String.valueOf(numericId);
                }
            }
            int amplifier = getByteAsInt(effectTag, "Amplifier", -1);
            if (amplifier < 0) amplifier = getByteAsInt(effectTag, "amplifier", 0);
            int duration = getInt(effectTag, "Duration", Integer.MIN_VALUE);
            if (duration == Integer.MIN_VALUE) duration = getInt(effectTag, "duration", 0);
            boolean ambient = getByteAsInt(effectTag, "Ambient", -1) == 1;
            if (!ambient) ambient = getByteAsInt(effectTag, "ambient", 0) == 1;
            int sp = getByteAsInt(effectTag, "ShowParticles", -1);
            if (sp < 0) sp = getByteAsInt(effectTag, "show_particles", 1);
            boolean showParticles = sp == 1;
            int si = getByteAsInt(effectTag, "ShowIcon", -1);
            if (si < 0) si = getByteAsInt(effectTag, "show_icon", 1);
            boolean showIcon = si == 1;
            effects.add(new PlayerEffect(id, amplifier, duration, ambient, showParticles, showIcon));
        }
        return effects;
    }

    private static List<PlayerAttributeValue> parseAttributes(Nbt.Tag root) {
        Nbt.Tag tag = Nbt.find(root, "Attributes");
        if (tag == null) tag = Nbt.find(root, "attributes");
        if (tag == null) tag = Nbt.find(root, "AttributeInstances");
        if (tag == null || !(tag.value instanceof List<?> list)) return Collections.emptyList();
        List<PlayerAttributeValue> attributes = new ArrayList<>();
        for (Object obj : list) {
            if (!(obj instanceof Nbt.Tag attrTag)) continue;
            String id = getString(attrTag, "Name", null);
            if (id == null) id = getString(attrTag, "id", null);
            double base = getDouble(attrTag, "Base", Double.NaN);
            if (Double.isNaN(base)) base = getDouble(attrTag, "base", 0d);
            double current = getDouble(attrTag, "Current", Double.NaN);
            if (Double.isNaN(current)) current = getDouble(attrTag, "current", base);
            if (Double.isNaN(current)) current = base;
            if (id == null) continue;
            attributes.add(new PlayerAttributeValue(id, base, current));
        }
        return attributes;
    }

    private static void flattenStatsInto(String prefix, Object node, List<PlayerStatistic> out) {
        if (node instanceof Map<?, ?> map) {
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                String key = String.valueOf(entry.getKey());
                String next = prefix.isEmpty() ? key : prefix + "." + key;
                flattenStatsInto(next, entry.getValue(), out);
            }
            return;
        }
        if (node instanceof List<?> list) {
            int index = 0;
            for (Object item : list) {
                String next = prefix + "[" + index + "]";
                flattenStatsInto(next, item, out);
                index++;
            }
            return;
        }
        if (node instanceof Number n) {
            out.add(new PlayerStatistic(prefix, n.longValue()));
        }
    }

    private static Map<String, Object> tagToMap(Nbt.Tag root, String key) {
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

    private static DataInputStream getStream(byte[] data) throws Exception {
        ByteArrayInputStream bais = new ByteArrayInputStream(data);
        if (data.length > 2 && (data[0] == (byte) 0x1F && data[1] == (byte) 0x8B)) {
            return new DataInputStream(new GZIPInputStream(bais));
        }
        if (data.length > 2 && (data[0] & 0x0F) == 8) {
            return new DataInputStream(new InflaterInputStream(bais));
        }
        return new DataInputStream(bais);
    }

    private static String getString(Nbt.Tag root, String key, String def) {
        if (root == null) return def;
        Nbt.Tag t = Nbt.find(root, key);
        if (t == null || t.value == null) return def;
        if (t.value instanceof String s) return s;
        return String.valueOf(t.value);
    }

    private static int getInt(Nbt.Tag root, String key, int def) {
        if (root == null) return def;
        Nbt.Tag t = Nbt.find(root, key);
        if (t == null || t.value == null) return def;
        if (t.value instanceof Number n) return n.intValue();
        return def;
    }

    private static float getFloat(Nbt.Tag root, String key, float def) {
        if (root == null) return def;
        Nbt.Tag t = Nbt.find(root, key);
        if (t == null || t.value == null) return def;
        if (t.value instanceof Number n) return n.floatValue();
        return def;
    }

    private static double getDouble(Nbt.Tag root, String key, double def) {
        if (root == null) return def;
        Nbt.Tag t = Nbt.find(root, key);
        if (t == null || t.value == null) return def;
        if (t.value instanceof Number n) return n.doubleValue();
        return def;
    }

    private static int getByteAsInt(Nbt.Tag root, String key, int def) {
        if (root == null) return def;
        Nbt.Tag t = Nbt.find(root, key);
        if (t == null || t.value == null) return def;
        if (t.value instanceof Number n) return n.intValue();
        return def;
    }

    private static double toDouble(Object v) {
        if (v instanceof Number n) return n.doubleValue();
        return 0;
    }

    private static float toFloat(Object v) {
        if (v instanceof Number n) return n.floatValue();
        return 0f;
    }
}
