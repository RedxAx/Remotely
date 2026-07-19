package redxax.oxy.remotely.flow.ui;

import redxax.oxy.remotely.data.flow.FlowManager;
import redxax.oxy.remotely.data.flow.OptionCatalogCache;
import redxax.oxy.remotely.data.flow.OptionCatalogItem;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class ItemOptionCatalog {
    public static final String SOURCE = "server:custom_content:recipe_item";
    private static final String MATERIAL_SOURCE = "server:minecraft:material";

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
    }

    public static boolean isReady(String serverId) {
        return serverId != null
            && OptionCatalogCache.getInstance().hasCatalog(serverId, SOURCE)
            && OptionCatalogCache.getInstance().hasCatalog(serverId, MATERIAL_SOURCE);
    }

    public static List<String> mergedValues(String serverId) {
        ensureLoaded(serverId);
        if (!isReady(serverId)) {
            return List.of("Loading");
        }
        LinkedHashSet<String> values = new LinkedHashSet<>();
        List<String> serverValues = OptionCatalogCache.getInstance().getValues(serverId, SOURCE);
        for (String value : serverValues) {
            if (value != null && !value.isBlank()) {
                values.add(value);
            }
        }
        for (String material : materialOptions(serverId)) {
            if (material != null && !material.isBlank()) {
                values.add(material);
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

    private static List<String> materialOptions(String serverId) {
        return catalogValues(serverId, MATERIAL_SOURCE);
    }

    private static List<String> catalogValues(String serverId, String source) {
        boolean missing = !OptionCatalogCache.getInstance().hasCatalog(serverId, source);
        if (missing) {
            FlowManager manager = FlowManager.getInstance();
            if (manager != null) {
                manager.ensureFlowClient(serverId).requestOptionCatalog(source);
            }
        }
        List<String> values = OptionCatalogCache.getInstance().getValues(serverId, source);
        if (!values.isEmpty()) {
            return values;
        }
        return missing ? List.of("Loading") : List.of();
    }
}
