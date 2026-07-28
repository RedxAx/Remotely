package redxax.oxy.remotely.flow.ui;

import redxax.oxy.remotely.data.flow.FlowManager;
import redxax.oxy.remotely.data.flow.OptionCatalogCache;
import redxax.oxy.remotely.data.flow.OptionCatalogItem;
import redxax.oxy.remotely.flow.data.CustomContentDefinition;
import restudio.rescreen.game.MinecraftGameItems;
import restudio.rescreen.platform.lwjgl.MinecraftRenderItem;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class ItemIconPreview {
    private static final String CUSTOM_CONTENT_ASSET_SOURCE = "server:custom_content:asset";
    private static final List<String> PREVIEW_SOURCES = List.of(
        ItemOptionCatalog.SOURCE,
        CUSTOM_CONTENT_ASSET_SOURCE
    );

    public record Preview(String material, Integer customModelData, Map<String, Object> components) {
        public Preview {
            material = material != null && !material.isBlank() ? material : "stone";
            components = components != null ? Map.copyOf(components) : Map.of();
        }

        public MinecraftRenderItem toRenderItem(String name) {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("id", material);
            if (customModelData != null) {
                map.put("customModelData", customModelData);
            }
            if (!components.isEmpty()) {
                map.put("tag", Map.of("components", components));
            }
            MinecraftRenderItem item = MinecraftGameItems.fromMap(map);
            if (item != null) {
                return item;
            }
            return MinecraftGameItems.fromVisual(material, 1, name, List.of(), customModelData);
        }
    }

    private ItemIconPreview() {
    }

    public static Preview resolve(String serverId, String value) {
        if (value == null || value.isBlank()) {
            return new Preview("stone", null, Map.of());
        }
        Preview catalog = previewFromCatalog(serverId, value);
        if (catalog != null) {
            return catalog;
        }
        if (value.startsWith("content:")) {
            return fromContent(serverId, value.substring("content:".length()));
        }
        if (value.startsWith("provider:")) {
            return fromProviderReference(serverId, value);
        }
        return fromVanilla(value);
    }

    private static Preview previewFromCatalog(String serverId, String value) {
        if (serverId == null) {
            return null;
        }
        for (String source : PREVIEW_SOURCES) {
            List<OptionCatalogItem> items = CUSTOM_CONTENT_ASSET_SOURCE.equals(source)
                ? OptionCatalogCache.getInstance().getItemsAcrossContexts(serverId, source)
                : OptionCatalogCache.getInstance().getItems(serverId, source);
            for (OptionCatalogItem item : items) {
                if (item == null) {
                    continue;
                }
                if (matchesCatalogValue(value, item)) {
                    Preview preview = fromCatalogItem(item);
                    if (preview != null) {
                        return preview;
                    }
                }
            }
        }
        return null;
    }

    private static boolean matchesCatalogValue(String requested, OptionCatalogItem item) {
        String catalogValue = item != null ? item.getValue() : null;
        if (requested == null || catalogValue == null) {
            return false;
        }
        if (requested.equals(catalogValue)) {
            return true;
        }
        if (!requested.startsWith("provider:")) {
            return false;
        }
        String rest = requested.substring("provider:".length());
        int split = rest.indexOf(':');
        if (split <= 0 || split >= rest.length() - 1) {
            return false;
        }
        String provider = rest.substring(0, split);
        Object itemProvider = item.getMetadata().get("provider");
        if (itemProvider == null || !provider.equalsIgnoreCase(itemProvider.toString())) {
            return false;
        }
        return rest.substring(split + 1).equals(catalogValue);
    }

    private static Preview fromCatalogItem(OptionCatalogItem item) {
        Preview metadata = fromMetadata(item.getMetadata());
        if (metadata != null) {
            return metadata;
        }
        String icon = item.getIcon();
        return icon.isBlank() ? null : fromVanilla(icon);
    }

    private static Preview fromMetadata(Map<String, Object> metadata) {
        if (metadata == null || metadata.isEmpty()) {
            return null;
        }
        Object materialValue = firstPresent(metadata, "material", "item", "id", "minecraftMaterial", "baseMaterial");
        if (materialValue == null || materialValue.toString().isBlank()) {
            return null;
        }
        Integer customModelData = integer(firstPresent(metadata, "customModelData", "custom_model_data", "modelData", "model_data", "cmd"));
        Map<String, Object> components = componentMap(metadata.get("components"));
        return new Preview(materialValue.toString(), customModelData, components);
    }

    private static Map<String, Object> componentMap(Object raw) {
        if (!(raw instanceof Map<?, ?> map) || map.isEmpty()) {
            return Map.of();
        }
        Map<String, Object> components = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            if (entry.getKey() != null && entry.getValue() != null) {
                components.put(entry.getKey().toString(), entry.getValue());
            }
        }
        return components;
    }

    private static Object firstPresent(Map<String, Object> metadata, String... keys) {
        for (String key : keys) {
            Object value = metadata.get(key);
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    private static Integer integer(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value instanceof String string) {
            try {
                return Integer.parseInt(string.trim());
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        if (value instanceof Map<?, ?> map) {
            Object nested = map.get("value");
            if (nested == null) {
                nested = map.get("model");
            }
            if (nested == null) {
                nested = map.get("data");
            }
            if (nested == null) {
                nested = map.get("floats");
            }
            if (nested == null) {
                nested = map.get("values");
            }
            return integer(nested);
        }
        if (value instanceof List<?> list) {
            return list.isEmpty() ? null : integer(list.getFirst());
        }
        return null;
    }

    private static Preview fromContent(String serverId, String contentId) {
        FlowManager manager = FlowManager.getInstance();
        if (manager == null || serverId == null) {
            return new Preview("BARRIER", null, Map.of());
        }
        CustomContentDefinition content = manager.getCustomContentForServer(serverId).get(contentId);
        if (content == null) {
            return new Preview("BARRIER", null, Map.of());
        }
        String provider = content.getProvider();
        String externalId = content.getExternalId();
        if (provider != null && !provider.isBlank() && externalId != null && !externalId.isBlank()) {
            String providerReference = "provider:" + provider.toLowerCase(Locale.ROOT) + ":" + externalId;
            Preview linked = previewFromCatalog(serverId, providerReference);
            if (linked != null) {
                return linked;
            }
        }
        String material = content.getMaterial();
        if (material == null || material.isBlank()) {
            material = "BARRIER";
        }
        return new Preview(material, content.getCustomModelData(), Map.of());
    }

    private static Preview fromProviderReference(String serverId, String value) {
        Preview catalog = previewFromCatalog(serverId, value);
        if (catalog != null) {
            return catalog;
        }
        return new Preview("PAPER", null, Map.of());
    }

    private static Preview fromVanilla(String value) {
        String material = value.trim();
        if (material.startsWith("minecraft:")) {
            material = material.substring("minecraft:".length());
        }
        if (material.contains(":")) {
            return new Preview(material, null, Map.of());
        }
        return new Preview(material.toUpperCase(Locale.ROOT), null, Map.of());
    }
}
