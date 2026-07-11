package redxax.oxy.remotely.data.player.management;

import redxax.oxy.remotely.data.flow.player.PlayerDossier;
import redxax.oxy.remotely.data.flow.player.PlayerFacetState;
import redxax.oxy.remotely.data.playerdata.PlayerAttributeValue;
import redxax.oxy.remotely.data.playerdata.PlayerData;
import redxax.oxy.remotely.data.playerdata.PlayerEffect;
import redxax.oxy.remotely.data.playerdata.PlayerEnderChest;
import redxax.oxy.remotely.data.playerdata.PlayerItem;
import redxax.oxy.remotely.data.playerdata.PlayerLocation;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;

public final class ReSyncPlayerDataAdapter {
    private ReSyncPlayerDataAdapter() {
    }

    public static PlayerDataContribution adapt(PlayerDossier dossier, long connectionGeneration) {
        if (dossier == null) return null;
        PlayerFacetState facet = dossier.getFacets().get("playerData");
        if (facet == null || facet.getData().isEmpty()) return null;
        Map<String, Object> data = facet.getData();
        PlayerData playerData = new PlayerData(number(data.get("health"), -1d), integer(data.get("food"), -1), decimal(data.get("saturation"), -1f),
            integer(data.get("experienceLevel"), -1), decimal(data.get("experienceProgress"), -1f), integer(data.get("totalExperience"), -1),
            location(map(data.get("location"))), text(data.get("gameMode")), bool(data.get("flying")), bool(data.get("fallFlying")),
            items(data.get("inventory")), items(data.get("armor")), items(data.get("offhand")), new PlayerEnderChest(items(data.get("enderChest"))),
            effects(data.get("effects")), attributes(data.get("attributes")), Collections.emptyMap(), Collections.emptyList(), facet.getUpdatedAt(), true);
        long revision = Math.max(longValue(data.get("stateRevision"), 0L), longValue(data.get("inventoryRevision"), 0L));
        EnumSet<PlayerSection> sections = EnumSet.of(PlayerSection.OVERVIEW);
        if (data.containsKey("inventory")) sections.add(PlayerSection.INVENTORY);
        if (data.containsKey("enderChest")) sections.add(PlayerSection.ENDER_CHEST);
        if (data.containsKey("effects")) sections.add(PlayerSection.EFFECTS);
        return new PlayerDataContribution(playerData, sections, "resync", facet.getUpdatedAt(), connectionGeneration, revision);
    }

    private static PlayerLocation location(Map<String, Object> value) {
        if (value.isEmpty() || !value.containsKey("x") || !value.containsKey("y") || !value.containsKey("z")) return null;
        return new PlayerLocation(number(value.get("x"), 0d), number(value.get("y"), 0d), number(value.get("z"), 0d), decimal(value.get("yaw"), 0f), decimal(value.get("pitch"), 0f), text(value.get("dimension")));
    }

    private static List<PlayerItem> items(Object value) {
        if (!(value instanceof List<?> list)) return List.of();
        List<PlayerItem> items = new ArrayList<>();
        for (Object entry : list) {
            Map<String, Object> item = map(entry);
            String id = text(item.get("id"));
            if (id == null || id.isBlank() || "minecraft:air".equalsIgnoreCase(id)) continue;
            items.add(new PlayerItem(id, integer(item.get("count"), 1), integer(item.get("slot"), items.size()), map(item.get("tag"))));
        }
        return List.copyOf(items);
    }

    private static List<PlayerEffect> effects(Object value) {
        if (!(value instanceof List<?> list)) return List.of();
        List<PlayerEffect> effects = new ArrayList<>();
        for (Object entry : list) {
            Map<String, Object> effect = map(entry);
            String id = text(effect.get("id"));
            if (id == null || id.isBlank()) continue;
            effects.add(new PlayerEffect(id, integer(effect.get("amplifier"), 0), integer(effect.get("duration"), 0), bool(effect.get("ambient")), bool(effect.get("showParticles")), !effect.containsKey("showIcon") || bool(effect.get("showIcon"))));
        }
        return List.copyOf(effects);
    }

    private static List<PlayerAttributeValue> attributes(Object value) {
        if (!(value instanceof List<?> list)) return List.of();
        List<PlayerAttributeValue> attributes = new ArrayList<>();
        for (Object entry : list) {
            Map<String, Object> attribute = map(entry);
            String id = text(attribute.get("id"));
            if (id == null || id.isBlank()) continue;
            double base = number(attribute.get("base"), 0d);
            attributes.add(new PlayerAttributeValue(id, base, number(attribute.get("value"), number(attribute.get("current"), base))));
        }
        return List.copyOf(attributes);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> map(Object value) {
        return value instanceof Map<?, ?> raw ? (Map<String, Object>) raw : Map.of();
    }

    private static String text(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private static boolean bool(Object value) {
        return value instanceof Boolean bool ? bool : value != null && Boolean.parseBoolean(String.valueOf(value));
    }

    private static int integer(Object value, int fallback) {
        return value instanceof Number number ? number.intValue() : fallback;
    }

    private static long longValue(Object value, long fallback) {
        return value instanceof Number number ? number.longValue() : fallback;
    }

    private static float decimal(Object value, float fallback) {
        return value instanceof Number number ? number.floatValue() : fallback;
    }

    private static double number(Object value, double fallback) {
        return value instanceof Number number ? number.doubleValue() : fallback;
    }
}
