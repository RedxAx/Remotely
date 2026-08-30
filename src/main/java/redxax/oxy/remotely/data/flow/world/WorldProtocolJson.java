package redxax.oxy.remotely.data.flow.world;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import redxax.oxy.remotely.flow.data.FlowJson;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

public final class WorldProtocolJson {
    public static WorldSnapshot readSnapshot(JsonElement value) {
        JsonObject root = object(value);
        if (root == null) {
            return null;
        }
        WorldSnapshot snapshot = new WorldSnapshot();
        snapshot.setDashboard(objects(root, "dashboard", WorldProtocolJson::dashboard));
        snapshot.setWorlds(objects(root, "worlds", WorldProtocolJson::world));
        snapshot.setPortals(objects(root, "portals", WorldProtocolJson::portal));
        snapshot.setInventoryGroups(objects(root, "inventoryGroups", WorldProtocolJson::inventoryGroup));
        snapshot.setSignPortals(objects(root, "signPortals", WorldProtocolJson::signPortal));
        snapshot.setGameRuleDescriptors(objects(root, "gameRuleDescriptors", WorldProtocolJson::gameRule));
        snapshot.setGeneratorDescriptors(objects(root, "generatorDescriptors", WorldProtocolJson::generator));
        snapshot.setGeneratorHints(strings(root, "generatorHints"));
        snapshot.setGeneratedAt(FlowJson.longValue(root, "generatedAt", 0));
        return snapshot;
    }

    public static WorldMapSnapshot readMapSnapshot(JsonElement value) {
        JsonObject root = object(value);
        if (root == null) {
            return null;
        }
        WorldMapSnapshot snapshot = new WorldMapSnapshot();
        snapshot.setControls(objects(root, "controls", WorldProtocolJson::control));
        snapshot.setDrawings(objects(root, "drawings", WorldProtocolJson::drawing));
        snapshot.setGeneratedAt(FlowJson.longValue(root, "generatedAt", 0));
        return snapshot;
    }

    public static WorldOperationResult readOperationResult(JsonElement value) {
        JsonObject root = object(value);
        if (root == null) {
            return null;
        }
        JsonElement encodedData = root.get("data");
        if (encodedData != null && !encodedData.isJsonNull() && !encodedData.isJsonObject()) {
            return null;
        }
        WorldOperationResult result = new WorldOperationResult();
        result.setSuccess(FlowJson.bool(root, "success", false));
        result.setAction(FlowJson.string(root, "action", null));
        result.setMessage(FlowJson.string(root, "message", null));
        result.setWorldName(FlowJson.string(root, "worldName", null));
        result.setOperationId(FlowJson.string(root, "operationId", null));
        result.setActorClientId(FlowJson.string(root, "actorClientId", null));
        result.setStartedAt(FlowJson.longValue(root, "startedAt", 0));
        result.setFinishedAt(FlowJson.longValue(root, "finishedAt", 0));
        result.setSafetyBackupId(FlowJson.string(root, "safetyBackupId", null));
        result.setAuditId(FlowJson.string(root, "auditId", null));
        result.setStatus(FlowJson.string(root, "status", null));
        result.setRequiresConfirmation(FlowJson.bool(root, "requiresConfirmation", false));
        result.setData(readOperationData(root.get("data")));
        return result;
    }

    public static Map<String, Object> readOperationData(JsonElement value) {
        Object decoded = readValue(value);
        if (!(decoded instanceof Map<?, ?> source)) {
            return new LinkedHashMap<>();
        }
        Map<String, Object> data = new LinkedHashMap<>();
        source.forEach((key, item) -> data.put(FlowJson.text(key), item));
        return data;
    }

    public static Object readValue(JsonElement value) {
        return FlowJson.value(value);
    }

    public static WorldRegistryEntry readWorld(Object value) {
        JsonObject root = object(tree(value));
        if (root == null) {
            return null;
        }
        try {
            return WorldRegistryJson.read(root);
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    public static WorldInventoryGroup readInventoryGroup(Object value) {
        JsonObject root = object(tree(value));
        if (root == null) {
            return null;
        }
        try {
            return inventoryGroup(root);
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private static WorldDashboardEntry dashboard(JsonObject root) {
        WorldDashboardEntry value = new WorldDashboardEntry();
        value.setWorldName(FlowJson.string(root, "worldName", null));
        value.setStatus(FlowJson.string(root, "status", null));
        value.setPlayerCount(FlowJson.integer(root, "playerCount", 0));
        value.setEnvironment(FlowJson.string(root, "environment", null));
        value.setLoaded(FlowJson.bool(root, "loaded", false));
        value.setDifficulty(FlowJson.string(root, "difficulty", null));
        value.setIsolatedPlayerState(FlowJson.bool(root, "isolatedPlayerState", false));
        value.setTimeLockEnabled(FlowJson.bool(root, "timeLockEnabled", false));
        value.setWeatherLockEnabled(FlowJson.bool(root, "weatherLockEnabled", false));
        value.setAlias(FlowJson.string(root, "alias", null));
        value.setHidden(FlowJson.bool(root, "hidden", false));
        value.setForceGameMode(FlowJson.bool(root, "forceGameMode", false));
        value.setGameMode(FlowJson.string(root, "gameMode", null));
        value.setEntryFeeEnabled(FlowJson.bool(root, "entryFeeEnabled", false));
        value.setEntryFee(FlowJson.decimal(root, "entryFee", 0));
        value.setPvpEnabled(FlowJson.bool(root, "pvpEnabled", false));
        value.setKeepSpawnLoaded(FlowJson.bool(root, "keepSpawnLoaded", false));
        value.setAutoSaveEnabled(FlowJson.bool(root, "autoSaveEnabled", false));
        value.setAnimalSpawnsEnabled(FlowJson.bool(root, "animalSpawnsEnabled", false));
        value.setMonsterSpawnsEnabled(FlowJson.bool(root, "monsterSpawnsEnabled", false));
        value.setHungerEnabled(FlowJson.bool(root, "hungerEnabled", false));
        value.setAutoHealEnabled(FlowJson.bool(root, "autoHealEnabled", false));
        value.setBedRespawnEnabled(FlowJson.bool(root, "bedRespawnEnabled", false));
        value.setAnchorRespawnEnabled(FlowJson.bool(root, "anchorRespawnEnabled", false));
        return value;
    }

    private static WorldRegistryEntry world(JsonObject root) {
        return WorldRegistryJson.read(root);
    }

    private static WorldPortal portal(JsonObject root) {
        WorldPortal value = new WorldPortal();
        value.setPortalId(FlowJson.string(root, "portalId", null));
        value.setPortalName(FlowJson.string(root, "portalName", null));
        value.setSourceWorld(FlowJson.string(root, "sourceWorld", null));
        value.setMinX(FlowJson.decimal(root, "minX", 0));
        value.setMinY(FlowJson.decimal(root, "minY", 0));
        value.setMinZ(FlowJson.decimal(root, "minZ", 0));
        value.setMaxX(FlowJson.decimal(root, "maxX", 0));
        value.setMaxY(FlowJson.decimal(root, "maxY", 0));
        value.setMaxZ(FlowJson.decimal(root, "maxZ", 0));
        value.setDestinationWorld(FlowJson.string(root, "destinationWorld", null));
        value.setDestinationX(FlowJson.decimal(root, "destinationX", 0));
        value.setDestinationY(FlowJson.decimal(root, "destinationY", 0));
        value.setDestinationZ(FlowJson.decimal(root, "destinationZ", 0));
        value.setDestinationYaw((float) FlowJson.decimal(root, "destinationYaw", 0));
        value.setDestinationPitch((float) FlowJson.decimal(root, "destinationPitch", 0));
        value.setEnabled(FlowJson.bool(root, "enabled", true));
        value.setLastUsedAt(FlowJson.longValue(root, "lastUsedAt", 0));
        value.setAccessPermission(FlowJson.string(root, "accessPermission", null));
        value.setBypassPermission(FlowJson.string(root, "bypassPermission", null));
        value.setUsageFeeEnabled(FlowJson.bool(root, "usageFeeEnabled", false));
        value.setUsageFee(FlowJson.decimal(root, "usageFee", 0));
        value.setCooldownMillis(FlowJson.longValue(root, "cooldownMillis", 0));
        value.setPriority(FlowJson.integer(root, "priority", 0));
        value.setSafeTeleport(FlowJson.bool(root, "safeTeleport", true));
        value.setPreserveVelocity(FlowJson.bool(root, "preserveVelocity", false));
        value.setEnterMessage(FlowJson.string(root, "enterMessage", null));
        value.setVehiclePassthroughEnabled(nullableBoolean(root, "vehiclePassthroughEnabled"));
        value.setEntityPassthroughEnabled(nullableBoolean(root, "entityPassthroughEnabled"));
        value.setDestinationMode(FlowJson.string(root, "destinationMode", null));
        value.setCannonPower(FlowJson.decimal(root, "cannonPower", 0));
        return value;
    }

    private static WorldInventoryGroup inventoryGroup(JsonObject root) {
        WorldInventoryGroup value = new WorldInventoryGroup();
        value.setGroupId(FlowJson.string(root, "groupId", null));
        value.setDisplayName(FlowJson.string(root, "displayName", null));
        value.setWorlds(strings(root, "worlds"));
        value.setShareInventory(FlowJson.bool(root, "shareInventory", true));
        value.setShareArmor(FlowJson.bool(root, "shareArmor", true));
        value.setShareOffhand(FlowJson.bool(root, "shareOffhand", true));
        value.setShareEnderChest(FlowJson.bool(root, "shareEnderChest", true));
        value.setShareHealth(FlowJson.bool(root, "shareHealth", true));
        value.setShareHunger(FlowJson.bool(root, "shareHunger", true));
        value.setShareExperience(FlowJson.bool(root, "shareExperience", true));
        value.setShareGameMode(FlowJson.bool(root, "shareGameMode", true));
        value.setSharePotionEffects(FlowJson.bool(root, "sharePotionEffects", true));
        value.setShareLastLocation(FlowJson.bool(root, "shareLastLocation", true));
        value.setShareBedSpawn(FlowJson.bool(root, "shareBedSpawn", true));
        value.setUpdatedAt(FlowJson.longValue(root, "updatedAt", 0));
        return value;
    }

    private static WorldSignPortal signPortal(JsonObject root) {
        WorldSignPortal value = new WorldSignPortal();
        value.setSignId(FlowJson.string(root, "signId", null));
        value.setWorldName(FlowJson.string(root, "worldName", null));
        value.setX(FlowJson.integer(root, "x", 0));
        value.setY(FlowJson.integer(root, "y", 0));
        value.setZ(FlowJson.integer(root, "z", 0));
        value.setPortalId(FlowJson.string(root, "portalId", null));
        value.setPortalName(FlowJson.string(root, "portalName", null));
        value.setEnabled(FlowJson.bool(root, "enabled", true));
        value.setUpdatedAt(FlowJson.longValue(root, "updatedAt", 0));
        return value;
    }

    private static WorldGameRuleDescriptor gameRule(JsonObject root) {
        WorldGameRuleDescriptor value = new WorldGameRuleDescriptor();
        value.setName(FlowJson.string(root, "name", null));
        value.setType(FlowJson.string(root, "type", null));
        return value;
    }

    private static WorldGeneratorDescriptor generator(JsonObject root) {
        WorldGeneratorDescriptor value = new WorldGeneratorDescriptor();
        value.setId(FlowJson.string(root, "id", null));
        value.setDisplayName(FlowJson.string(root, "displayName", null));
        value.setBuiltIn(FlowJson.bool(root, "builtIn", false));
        value.setConfigurable(FlowJson.bool(root, "configurable", false));
        value.setConfigPlaceholder(FlowJson.string(root, "configPlaceholder", null));
        value.setDefaultConfig(FlowJson.string(root, "defaultConfig", null));
        return value;
    }

    private static WorldMapControl control(JsonObject root) {
        WorldMapControl value = new WorldMapControl();
        value.setExtensionId(FlowJson.string(root, "extensionId", null));
        value.setControlId(FlowJson.string(root, "controlId", null));
        value.setLabel(FlowJson.string(root, "label", null));
        value.setKind(FlowJson.string(root, "kind", null));
        value.setData(readOperationData(root.get("data")));
        return value;
    }

    private static WorldMapDrawing drawing(JsonObject root) {
        WorldMapDrawing value = new WorldMapDrawing();
        value.setExtensionId(FlowJson.string(root, "extensionId", null));
        value.setDrawingId(FlowJson.string(root, "drawingId", null));
        value.setLabel(FlowJson.string(root, "label", null));
        value.setKind(FlowJson.string(root, "kind", null));
        value.setWorldName(FlowJson.string(root, "worldName", null));
        value.setCoordinates(objects(root, "coordinates", WorldProtocolJson::coordinate));
        value.setData(readOperationData(root.get("data")));
        return value;
    }

    private static WorldMapCoordinate coordinate(JsonObject root) {
        WorldMapCoordinate value = new WorldMapCoordinate();
        value.setX(FlowJson.decimal(root, "x", 0));
        value.setY(FlowJson.decimal(root, "y", 0));
        value.setZ(FlowJson.decimal(root, "z", 0));
        return value;
    }

    private static <T> List<T> objects(JsonObject root, String key, Function<JsonObject, T> decoder) {
        List<T> values = new ArrayList<>();
        FlowJson.array(root, key).forEach(item -> {
            if (item.isJsonNull()) {
                values.add(null);
            } else if (item.isJsonObject()) {
                values.add(decoder.apply(item.getAsJsonObject()));
            }
        });
        return values;
    }

    private static List<String> strings(JsonObject root, String key) {
        List<String> values = new ArrayList<>();
        FlowJson.array(root, key).forEach(item -> values.add(item.isJsonNull() ? null : string(item, null)));
        return values;
    }

    private static JsonObject object(JsonElement value) {
        return value != null && value.isJsonObject() ? value.getAsJsonObject() : null;
    }

    private static JsonElement tree(Object value) {
        if (value == null) {
            return JsonNull.INSTANCE;
        }
        if (value instanceof JsonElement json) {
            return json;
        }
        if (value instanceof WorldRegistryEntry world) {
            return WorldRegistryJson.write(world);
        }
        if (value instanceof String text) {
            return new JsonPrimitive(text);
        }
        if (value instanceof Number number) {
            return new JsonPrimitive(number);
        }
        if (value instanceof Boolean bool) {
            return new JsonPrimitive(bool);
        }
        if (value instanceof Map<?, ?> map) {
            JsonObject result = new JsonObject();
            map.forEach((key, item) -> result.add(FlowJson.text(key), tree(item)));
            return result;
        }
        if (value instanceof Iterable<?> iterable) {
            JsonArray result = new JsonArray();
            iterable.forEach(item -> result.add(tree(item)));
            return result;
        }
        return FlowJson.value(value);
    }

    private static String string(JsonElement value, String fallback) {
        try {
            return value == null || value.isJsonNull() ? fallback : value.getAsString();
        } catch (RuntimeException ignored) {
            return fallback;
        }
    }

    private static Boolean nullableBoolean(JsonObject root, String key) {
        JsonElement value = root == null ? null : root.get(key);
        if (value == null || value.isJsonNull()) {
            return null;
        }
        try {
            return value.getAsBoolean();
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private WorldProtocolJson() {
    }
}
