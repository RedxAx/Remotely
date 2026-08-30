package redxax.oxy.remotely.data.flow.world;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.LinkedHashMap;
import java.util.Map;

public final class WorldRegistryJson {
    private WorldRegistryJson() {
    }

    public static JsonObject write(WorldRegistryEntry value) {
        WorldRegistryEntry world = value == null ? new WorldRegistryEntry() : value;
        JsonObject result = new JsonObject();
        put(result, "worldName", world.getWorldName());
        put(result, "environment", world.getEnvironment());
        put(result, "generator", world.getGenerator());
        put(result, "generatorConfig", world.getGeneratorConfig());
        put(result, "difficulty", world.getDifficulty());
        result.addProperty("loaded", world.isLoaded());
        result.addProperty("isolatedPlayerState", world.isIsolatedPlayerState());
        result.addProperty("timeLockEnabled", world.isTimeLockEnabled());
        result.addProperty("lockedTime", world.getLockedTime());
        result.addProperty("weatherLockEnabled", world.isWeatherLockEnabled());
        result.addProperty("lockedStorm", world.isLockedStorm());
        result.addProperty("lockedThundering", world.isLockedThundering());
        result.add("profileSettings", profile(world.getProfileSettings()));
        JsonObject rules = new JsonObject();
        world.getGameRules().forEach(rules::addProperty);
        result.add("gameRules", rules);
        result.addProperty("updatedAt", world.getUpdatedAt());
        return result;
    }

    public static WorldRegistryEntry read(JsonObject value) {
        JsonObject root = value == null ? new JsonObject() : value;
        WorldRegistryEntry world = new WorldRegistryEntry();
        world.setWorldName(text(root, "worldName"));
        world.setEnvironment(text(root, "environment"));
        world.setGenerator(text(root, "generator"));
        world.setGeneratorConfig(text(root, "generatorConfig"));
        world.setDifficulty(text(root, "difficulty"));
        world.setLoaded(bool(root, "loaded", false));
        world.setIsolatedPlayerState(bool(root, "isolatedPlayerState", false));
        world.setTimeLockEnabled(bool(root, "timeLockEnabled", false));
        world.setLockedTime(longValue(root, "lockedTime", 0));
        world.setWeatherLockEnabled(bool(root, "weatherLockEnabled", false));
        world.setLockedStorm(bool(root, "lockedStorm", false));
        world.setLockedThundering(bool(root, "lockedThundering", false));
        world.setProfileSettings(profile(child(root, "profileSettings")));
        Map<String, String> rules = new LinkedHashMap<>();
        JsonObject encodedRules = child(root, "gameRules");
        if (encodedRules != null) encodedRules.entrySet().forEach(entry -> rules.put(entry.getKey(), entry.getValue().getAsString()));
        world.setGameRules(rules);
        world.setUpdatedAt(longValue(root, "updatedAt", 0));
        return world;
    }

    private static JsonObject profile(WorldProfileSettings value) {
        WorldProfileSettings profile = value == null ? new WorldProfileSettings() : value;
        JsonObject result = new JsonObject();
        put(result, "alias", profile.getAlias()); put(result, "accessPermission", profile.getAccessPermission());
        put(result, "bypassPermission", profile.getBypassPermission()); put(result, "respawnWorld", profile.getRespawnWorld());
        put(result, "gameMode", profile.getGameMode()); put(result, "arrivalMessage", profile.getArrivalMessage());
        put(result, "denyMessage", profile.getDenyMessage()); put(result, "inventoryGroupId", profile.getInventoryGroupId());
        put(result, "linkedNetherWorld", profile.getLinkedNetherWorld()); put(result, "linkedEndWorld", profile.getLinkedEndWorld());
        put(result, "linkedOverworld", profile.getLinkedOverworld());
        result.addProperty("hidden", profile.isHidden()); result.addProperty("forceGameMode", profile.isForceGameMode());
        result.addProperty("customSpawnEnabled", profile.isCustomSpawnEnabled()); result.addProperty("spawnX", profile.getSpawnX());
        result.addProperty("spawnY", profile.getSpawnY()); result.addProperty("spawnZ", profile.getSpawnZ());
        result.addProperty("spawnYaw", profile.getSpawnYaw()); result.addProperty("spawnPitch", profile.getSpawnPitch());
        result.addProperty("entryFeeEnabled", profile.isEntryFeeEnabled()); result.addProperty("entryFee", profile.getEntryFee());
        result.addProperty("pvpEnabled", profile.isPvpEnabled()); result.addProperty("keepSpawnLoaded", profile.isKeepSpawnLoaded());
        result.addProperty("autoSaveEnabled", profile.isAutoSaveEnabled()); result.addProperty("animalSpawnsEnabled", profile.isAnimalSpawnsEnabled());
        result.addProperty("monsterSpawnsEnabled", profile.isMonsterSpawnsEnabled()); result.addProperty("hungerEnabled", profile.isHungerEnabled());
        result.addProperty("autoHealEnabled", profile.isAutoHealEnabled()); result.addProperty("bedRespawnEnabled", profile.isBedRespawnEnabled());
        result.addProperty("anchorRespawnEnabled", profile.isAnchorRespawnEnabled()); result.addProperty("netherScale", profile.getNetherScale());
        result.addProperty("endScale", profile.getEndScale()); result.addProperty("autoLinkNetherPortal", profile.isAutoLinkNetherPortal());
        result.addProperty("autoLinkEndPortal", profile.isAutoLinkEndPortal());
        result.addProperty("nonLivingEntitySpawnsEnabled", profile.isNonLivingEntitySpawnsEnabled());
        return result;
    }

    private static WorldProfileSettings profile(JsonObject value) {
        JsonObject root = value == null ? new JsonObject() : value;
        WorldProfileSettings profile = new WorldProfileSettings();
        profile.setAlias(text(root, "alias")); profile.setHidden(bool(root, "hidden", false));
        profile.setAccessPermission(text(root, "accessPermission")); profile.setBypassPermission(text(root, "bypassPermission"));
        profile.setRespawnWorld(text(root, "respawnWorld")); profile.setForceGameMode(bool(root, "forceGameMode", false));
        profile.setGameMode(text(root, "gameMode")); profile.setCustomSpawnEnabled(bool(root, "customSpawnEnabled", false));
        profile.setSpawnX(decimal(root, "spawnX", 0)); profile.setSpawnY(decimal(root, "spawnY", 0)); profile.setSpawnZ(decimal(root, "spawnZ", 0));
        profile.setSpawnYaw((float) decimal(root, "spawnYaw", 0)); profile.setSpawnPitch((float) decimal(root, "spawnPitch", 0));
        profile.setEntryFeeEnabled(bool(root, "entryFeeEnabled", false)); profile.setEntryFee(decimal(root, "entryFee", 0));
        profile.setPvpEnabled(bool(root, "pvpEnabled", true)); profile.setKeepSpawnLoaded(bool(root, "keepSpawnLoaded", true));
        profile.setAutoSaveEnabled(bool(root, "autoSaveEnabled", true)); profile.setAnimalSpawnsEnabled(bool(root, "animalSpawnsEnabled", true));
        profile.setMonsterSpawnsEnabled(bool(root, "monsterSpawnsEnabled", true)); profile.setHungerEnabled(bool(root, "hungerEnabled", true));
        profile.setAutoHealEnabled(bool(root, "autoHealEnabled", true)); profile.setBedRespawnEnabled(bool(root, "bedRespawnEnabled", true));
        profile.setAnchorRespawnEnabled(bool(root, "anchorRespawnEnabled", true)); profile.setArrivalMessage(text(root, "arrivalMessage"));
        profile.setDenyMessage(text(root, "denyMessage")); profile.setInventoryGroupId(text(root, "inventoryGroupId"));
        profile.setLinkedNetherWorld(text(root, "linkedNetherWorld")); profile.setLinkedEndWorld(text(root, "linkedEndWorld"));
        profile.setLinkedOverworld(text(root, "linkedOverworld")); profile.setNetherScale(decimal(root, "netherScale", 8));
        profile.setEndScale(decimal(root, "endScale", 1)); profile.setAutoLinkNetherPortal(bool(root, "autoLinkNetherPortal", true));
        profile.setAutoLinkEndPortal(bool(root, "autoLinkEndPortal", true));
        profile.setNonLivingEntitySpawnsEnabled(bool(root, "nonLivingEntitySpawnsEnabled", true));
        return profile;
    }

    private static void put(JsonObject value, String name, String item) { if (item != null) value.addProperty(name, item); }
    private static JsonObject child(JsonObject value, String name) { JsonElement item = value.get(name); return item != null && item.isJsonObject() ? item.getAsJsonObject() : null; }
    private static String text(JsonObject value, String name) { JsonElement item = value.get(name); return item == null || item.isJsonNull() ? "" : item.getAsString(); }
    private static boolean bool(JsonObject value, String name, boolean fallback) { JsonElement item = value.get(name); return item == null || item.isJsonNull() ? fallback : item.getAsBoolean(); }
    private static long longValue(JsonObject value, String name, long fallback) { JsonElement item = value.get(name); return item == null || item.isJsonNull() ? fallback : item.getAsLong(); }
    private static double decimal(JsonObject value, String name, double fallback) { JsonElement item = value.get(name); return item == null || item.isJsonNull() ? fallback : item.getAsDouble(); }
}
