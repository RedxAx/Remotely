package redxax.oxy.remotely.flow.ui;

import redxax.oxy.remotely.data.flow.OptionCatalogItem;
import redxax.oxy.remotely.data.flow.OptionCatalogLoader;
import redxax.oxy.remotely.flow.data.FlowJson;
import restudio.rescreen.ui.widgets.ItemSelectorWidget;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Supplier;

public final class OptionCatalogSelector {
    private OptionCatalogSelector() {
    }

    public static Runnable refreshAction(String serverId, String source) {
        return refreshAction(serverId, source, Map.of());
    }

    public static Runnable refreshAction(String serverId, String source, Map<String, Object> context) {
        Map<String, Object> safeContext = OptionCatalogLoader.request(source, context).context();
        return () -> OptionCatalogLoader.refresh(serverId, source, safeContext);
    }

    public static ItemSelectorWidget.AsyncItemSnapshot snapshot(String serverId, String source, Supplier<String> selectedSupplier,
        Consumer<String> onSelected) {
        return snapshot(serverId, source, Map.of(), List::of, selectedSupplier, onSelected, "No Options");
    }

    public static ItemSelectorWidget.AsyncItemSnapshot snapshot(String serverId, String source, Map<String, Object> context,
        Supplier<? extends Collection<String>> fallbackSupplier, Supplier<String> selectedSupplier, Consumer<String> onSelected,
        String emptyMessage) {
        OptionCatalogLoader.Snapshot catalog = OptionCatalogLoader.snapshot(serverId, source, context);
        Map<String, OptionCatalogItem> richByValue = new LinkedHashMap<>();
        for (OptionCatalogItem item : catalog.items()) {
            if (real(item.getValue())) {
                richByValue.putIfAbsent(item.getValue(), item);
            }
        }
        Map<String, String> values = new LinkedHashMap<>();
        for (String value : catalog.values()) {
            if (real(value)) {
                values.putIfAbsent(value, value);
            }
        }
        Collection<String> fallback = fallbackSupplier != null ? fallbackSupplier.get() : List.of();
        if (fallback != null) {
            for (String value : fallback) {
                if (real(value)) {
                    values.putIfAbsent(value, value);
                }
            }
        }
        List<ItemSelectorWidget.AsyncItem> items = new ArrayList<>();
        for (OptionCatalogItem item : richByValue.values()) {
            String value = item.getValue();
            Object aliases = item.getMetadata().get("aliases");
            String searchTerms = String.join(" ", value, item.getLabel(), item.getDescription(), item.getGroup(),
                aliases != null ? FlowJson.text(aliases) : "");
            items.add(new ItemSelectorWidget.AsyncItem(label(item, value), item.getIcon(), item.getDescription(), searchTerms,
                item.getGroup(), onSelected != null ? () -> onSelected.accept(value) : null));
            values.remove(value);
        }
        for (String value : values.keySet()) {
            items.add(new ItemSelectorWidget.AsyncItem(value, "", value, onSelected != null ? () -> onSelected.accept(value) : null));
        }
        String selected = selectedSupplier != null ? selectedSupplier.get() : "";
        boolean selectedIncluded = richByValue.containsKey(selected) || values.containsKey(selected);
        if (real(selected) && !selectedIncluded) {
            items.add(new ItemSelectorWidget.AsyncItem(selected, "", selected,
                onSelected != null ? () -> onSelected.accept(selected) : null));
        }
        String message = emptyMessage != null && !emptyMessage.isBlank() ? emptyMessage : "No Options";
        if (!catalog.loading() && !"available".equals(catalog.status()) && !"missing".equals(catalog.status())) {
            message = "Catalog Unavailable";
        }
        return new ItemSelectorWidget.AsyncItemSnapshot(items, catalog.loading(), message);
    }

    public static String label(String serverId, String source, String value) {
        return label(serverId, source, Map.of(), value);
    }

    public static String label(String serverId, String source, Map<String, Object> context, String value) {
        if (!real(value)) {
            return value != null ? value : "";
        }
        return OptionCatalogLoader.snapshot(serverId, source, context).items().stream()
            .filter(item -> value.equals(item.getValue()))
            .map(item -> label(item, value))
            .findFirst()
            .orElse(value);
    }

    private static String label(OptionCatalogItem item, String fallback) {
        return item != null && item.getLabel() != null && !item.getLabel().isBlank() ? item.getLabel() : fallback;
    }

    private static boolean real(String value) {
        return value != null && !value.isBlank() && !"Loading".equals(value) && !"No Options".equals(value);
    }
}
