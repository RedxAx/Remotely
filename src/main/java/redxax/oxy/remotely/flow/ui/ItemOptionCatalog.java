package redxax.oxy.remotely.flow.ui;

import redxax.oxy.remotely.data.flow.FlowManager;
import redxax.oxy.remotely.data.flow.OptionCatalogCache;
import redxax.oxy.remotely.data.flow.OptionCatalogItem;
import redxax.oxy.remotely.packcontent.PackContentRegistry;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public final class ItemOptionCatalog {
    public static final String SOURCE = "server:custom_content:recipe_item";
    private static final String MATERIAL_SOURCE = "server:minecraft:material";
    private static final String PROVIDER_SOURCE = "server:custom_content:provider";
    private static final List<String> FALLBACK_MATERIAL_OPTIONS = List.of(
        "STONE", "COBBLESTONE", "OAK_PLANKS", "OAK_LOG", "GLASS", "GLASS_PANE",
        "GRAY_STAINED_GLASS_PANE", "WHITE_STAINED_GLASS_PANE", "BLACK_STAINED_GLASS_PANE",
        "RED_STAINED_GLASS_PANE", "GREEN_STAINED_GLASS_PANE", "BLUE_STAINED_GLASS_PANE",
        "BARRIER", "CHEST", "ENDER_CHEST", "ANVIL", "BOOK", "PAPER", "MAP",
        "COMPASS", "CLOCK", "DIAMOND", "EMERALD", "GOLD_INGOT", "IRON_INGOT",
        "NETHERITE_INGOT", "REDSTONE", "AMETHYST_SHARD", "ENDER_PEARL",
        "TOTEM_OF_UNDYING", "PLAYER_HEAD", "NAME_TAG"
    );

    private ItemOptionCatalog() {
    }

    public static void ensureLoaded(String serverId) {
        FlowManager manager = FlowManager.getInstance();
        if (manager == null || serverId == null) {
            return;
        }
        if (!OptionCatalogCache.getInstance().hasCatalog(serverId, SOURCE)) {
            manager.ensureFlowClient(serverId).requestOptionCatalog(SOURCE);
        }
        if (!OptionCatalogCache.getInstance().hasCatalog(serverId, MATERIAL_SOURCE)) {
            manager.ensureFlowClient(serverId).requestOptionCatalog(MATERIAL_SOURCE);
        }
        if (!OptionCatalogCache.getInstance().hasCatalog(serverId, PROVIDER_SOURCE)) {
            manager.ensureFlowClient(serverId).requestOptionCatalog(PROVIDER_SOURCE);
        }
        for (String source : List.of(
            "server:custom_content:nexo_item",
            "server:custom_content:nexo_armor",
            "server:custom_content:nexo_block",
            "server:custom_content:nexo_furniture"
        )) {
            if (!OptionCatalogCache.getInstance().hasCatalog(serverId, source)) {
                manager.ensureFlowClient(serverId).requestOptionCatalog(source);
            }
        }
    }

    public static boolean isReady(String serverId) {
        return serverId != null
            && (OptionCatalogCache.getInstance().hasCatalog(serverId, SOURCE)
            || OptionCatalogCache.getInstance().hasCatalog(serverId, MATERIAL_SOURCE));
    }

    public static List<String> mergedValues(String serverId) {
        ensureLoaded(serverId);
        if (!isReady(serverId)) {
            return List.of("Loading");
        }
        LinkedHashSet<String> values = new LinkedHashSet<>();
        List<String> serverValues = OptionCatalogCache.getInstance().getValues(serverId, SOURCE);
        boolean hasReSync = false;
        for (String value : serverValues) {
            if (value != null && value.startsWith("content:")) {
                values.add(value);
                hasReSync = true;
            }
        }
        if (!hasReSync) {
            appendLocalReSyncRecipeValues(serverId, values);
        }
        appendProviderRecipeValues(serverId, values);
        for (String value : serverValues) {
            if (value != null && value.startsWith("provider:")) {
                values.add(value);
            }
        }
        for (String material : materialOptions(serverId)) {
            if (material != null && !material.isBlank()) {
                values.add(material);
            }
        }
        for (String value : serverValues) {
            if (value != null && !value.isBlank() && !value.contains(":")) {
                values.add(value);
            }
        }
        return new ArrayList<>(values);
    }

    public static Map<String, OptionCatalogItem> byValue(String serverId) {
        Map<String, OptionCatalogItem> map = new LinkedHashMap<>();
        for (OptionCatalogItem item : OptionCatalogCache.getInstance().getItems(serverId, SOURCE)) {
            if (item == null) {
                continue;
            }
            String value = item.getValue();
            if (value == null || value.isBlank()) {
                continue;
            }
            map.putIfAbsent(value, item);
        }
        for (OptionCatalogItem item : OptionCatalogCache.getInstance().getItems(serverId, MATERIAL_SOURCE)) {
            if (item == null) {
                continue;
            }
            String value = item.getValue();
            if (value == null || value.isBlank()) {
                continue;
            }
            map.putIfAbsent(value, item);
        }
        return map;
    }

    public static String groupForValue(String value, OptionCatalogItem item) {
        if (item != null && !item.getGroup().isBlank()) {
            String group = item.getGroup();
            return group.contains("_") ? formatGroupLabel(group) : group;
        }
        if (value.startsWith("content:")) {
            return "ReSync";
        }
        if (value.startsWith("provider:")) {
            return providerGroupLabel(value);
        }
        return "Vanilla";
    }

    public static String label(String serverId, String value) {
        if (value == null || value.isBlank()) {
            return "none";
        }
        for (OptionCatalogItem item : OptionCatalogCache.getInstance().getItems(serverId, SOURCE)) {
            if (value.equals(item.getValue())) {
                return item.getLabel();
            }
        }
        for (OptionCatalogItem item : OptionCatalogCache.getInstance().getItems(serverId, MATERIAL_SOURCE)) {
            if (value.equals(item.getValue())) {
                return item.getLabel();
            }
        }
        if (value.startsWith("provider:")) {
            int split = value.lastIndexOf(':');
            if (split > 0 && split < value.length() - 1) {
                return value.substring(split + 1);
            }
        }
        return formatOptionLabel(value);
    }

    public static String formatOptionLabel(String value) {
        if (value == null || value.isBlank()) {
            return "none";
        }
        String cleaned = value.trim().replace("minecraft:", "").replace('_', ' ').replace('-', ' ');
        StringBuilder builder = new StringBuilder();
        for (String part : cleaned.split("\\s+")) {
            if (part.isBlank()) {
                continue;
            }
            if (!builder.isEmpty()) {
                builder.append(' ');
            }
            builder.append(part.substring(0, 1).toUpperCase(Locale.ROOT));
            if (part.length() > 1) {
                builder.append(part.substring(1).toLowerCase(Locale.ROOT));
            }
        }
        return builder.isEmpty() ? value : builder.toString();
    }

    private static String providerGroupLabel(String value) {
        String rest = value.substring("provider:".length());
        int split = rest.indexOf(':');
        if (split <= 0) {
            return "Providers";
        }
        return formatGroupLabel(rest.substring(0, split) + "_item");
    }

    private static String formatGroupLabel(String group) {
        String label = formatOptionLabel(group);
        return label + "s";
    }

    private static void appendLocalReSyncRecipeValues(String serverId, Set<String> values) {
        FlowManager manager = FlowManager.getInstance();
        if (manager == null || serverId == null) {
            return;
        }
        manager.getCustomContentForServer(serverId).values().stream()
            .filter(content -> content != null && content.getId() != null && !content.getId().isBlank())
            .filter(content -> {
                String contentType = content.getType() != null ? content.getType().toLowerCase(Locale.ROOT) : "";
                return Set.of("item", "armor", "block").contains(contentType);
            })
            .map(content -> "content:" + content.getId())
            .forEach(values::add);
    }

    private static void appendProviderRecipeValues(String serverId, Set<String> values) {
        for (String provider : providerOptions(serverId)) {
            if (provider == null || provider.isBlank() || "Loading".equals(provider) || "vanilla".equalsIgnoreCase(provider)) {
                continue;
            }
            String providerKey = provider.toLowerCase(Locale.ROOT);
            LinkedHashSet<String> externalIds = new LinkedHashSet<>();
            for (String type : List.of("item", "armor", "block")) {
                List<String> catalogAssets = providerCatalogAssets(serverId, type, provider);
                if (!catalogAssets.isEmpty() && !catalogAssets.equals(List.of("Loading"))) {
                    externalIds.addAll(catalogAssets);
                }
            }
            if (externalIds.isEmpty()) {
                for (PackContentRegistry.PackAssetOption option : PackContentRegistry.get().assetOptions(provider)) {
                    if (option.id() != null && !option.id().isBlank()) {
                        externalIds.add(option.id());
                    }
                }
            }
            for (String externalId : externalIds) {
                values.add("provider:" + providerKey + ":" + externalId);
            }
        }
    }

    private static List<String> providerOptions(String serverId) {
        List<String> catalogProviders = catalogValues(serverId, PROVIDER_SOURCE);
        List<String> providers = new ArrayList<>(catalogProviders);
        providers.remove("Loading");
        if (!providers.contains("vanilla")) {
            providers.add("vanilla");
        }
        for (PackContentRegistry.ProviderStatus status : PackContentRegistry.get().statuses()) {
            String name = status.providerName().toLowerCase(Locale.ROOT);
            if (name.contains("nexo") && !providers.contains("nexo")) {
                providers.add("nexo");
            }
            if (name.contains("itemsadder") && !providers.contains("itemsadder")) {
                providers.add("itemsadder");
            }
        }
        return providers;
    }

    private static List<String> providerCatalogAssets(String serverId, String type, String provider) {
        List<String> values = new ArrayList<>();
        for (String source : providerCatalogSources(type, provider)) {
            values.addAll(catalogValues(serverId, source));
        }
        List<String> assets = values.stream()
            .filter(value -> !"Loading".equals(value))
            .distinct()
            .sorted(String.CASE_INSENSITIVE_ORDER)
            .toList();
        return assets.isEmpty() && values.contains("Loading") ? List.of("Loading") : assets;
    }

    private static List<String> providerCatalogSources(String type, String provider) {
        if (provider == null || !provider.equalsIgnoreCase("nexo")) {
            return List.of();
        }
        return switch (type) {
            case "block" -> List.of("server:custom_content:nexo_block", "server:custom_content:nexo_furniture");
            case "armor" -> List.of("server:custom_content:nexo_armor");
            default -> List.of("server:custom_content:nexo_item");
        };
    }

    private static List<String> materialOptions(String serverId) {
        List<String> values = OptionCatalogCache.getInstance().getValues(serverId, MATERIAL_SOURCE);
        if (!values.isEmpty()) {
            return values;
        }
        FlowManager manager = FlowManager.getInstance();
        if (manager != null && serverId != null) {
            manager.ensureFlowClient(serverId).requestOptionCatalog(MATERIAL_SOURCE);
        }
        return FALLBACK_MATERIAL_OPTIONS;
    }

    private static List<String> catalogValues(String serverId, String source) {
        List<String> values = OptionCatalogCache.getInstance().getValues(serverId, source);
        if (!values.isEmpty()) {
            return values;
        }
        if (!OptionCatalogCache.getInstance().hasCatalog(serverId, source)) {
            FlowManager manager = FlowManager.getInstance();
            if (manager != null) {
                manager.ensureFlowClient(serverId).requestOptionCatalog(source);
            }
            return List.of("Loading");
        }
        return List.of();
    }
}
