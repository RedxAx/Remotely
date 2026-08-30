package redxax.oxy.remotely.web.platform;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import restudio.rebase.backend.FileExplorerRuntime;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

final class BrowserFileExplorerPersistence {
    private static final String TABS_PREFIX = "ui.explorer.tabs.";
    private final BrowserRemotelyConfigStore config;

    BrowserFileExplorerPersistence(BrowserRemotelyConfigStore config) {
        this.config = config;
    }

    FileExplorerRuntime.ExplorerSettings settings() {
        if (!available()) return defaults();
        try {
            return new FileExplorerRuntime.ExplorerSettings(config.getExplorerSort(), config.getExplorerLastSort(),
                    config.getExplorerRememberSort(), config.getExplorerSortPerTab(), config.getExplorerOpenOnDoubleClick());
        } catch (Throwable failure) {
            return defaults();
        }
    }

    void saveSort(String sort) {
        if (!available()) return;
        String value = sort == null || sort.isBlank() ? "Name" : sort;
        config.setExplorerSort(value);
        config.setExplorerLastSort(value);
        config.save();
    }

    void saveTabs(String key, List<FileExplorerRuntime.TabDescriptor> tabs, int activeIndex) {
        if (!available()) return;
        FileExplorerRuntime.LoadedState state = filter(new FileExplorerRuntime.LoadedState(tabs, activeIndex));
        config.set(storageKey(key), encode(state));
        config.save();
    }

    FileExplorerRuntime.LoadedState loadTabs(String key) {
        if (!available()) return empty();
        try {
            return filter(decode(config.get(storageKey(key), "")));
        } catch (Throwable failure) {
            return empty();
        }
    }

    private boolean available() {
        return config != null && BrowserLaunchSession.authenticated()
                && !BrowserLaunchSession.metadata().subjectId().isBlank();
    }

    private static FileExplorerRuntime.ExplorerSettings defaults() {
        return new FileExplorerRuntime.ExplorerSettings("Name", "", false, false, true);
    }

    private static FileExplorerRuntime.LoadedState empty() {
        return new FileExplorerRuntime.LoadedState(List.of(), -1);
    }

    private static String storageKey(String key) {
        return TABS_PREFIX + encodeValue(key == null ? "" : key);
    }

    private static String encode(FileExplorerRuntime.LoadedState state) {
        JsonObject root = new JsonObject();
        root.addProperty("activeIndex", state.activeIndex());
        JsonArray tabs = new JsonArray();
        for (FileExplorerRuntime.TabDescriptor descriptor : state.tabs()) {
            JsonObject value = new JsonObject();
            BrowserJson.put(value, "path", descriptor.path);
            BrowserJson.put(value, "apiType", descriptor.apiType);
            BrowserJson.put(value, "apiHost", descriptor.apiHost);
            JsonObject extras = new JsonObject();
            descriptor.extras.forEach((key, item) -> BrowserJson.put(extras, key, item));
            value.add("extras", extras);
            tabs.add(value);
        }
        root.add("tabs", tabs);
        return BrowserJson.write(root);
    }

    private static FileExplorerRuntime.LoadedState decode(String serialized) {
        if (serialized == null || serialized.isBlank()) return empty();
        JsonObject root = BrowserJson.object(serialized);
        JsonElement values = BrowserJson.element(root, "tabs");
        if (values == null || !values.isJsonArray()) return empty();
        List<FileExplorerRuntime.TabDescriptor> tabs = new ArrayList<>();
        values.getAsJsonArray().forEach(element -> {
            if (element == null || !element.isJsonObject()) return;
            JsonObject value = element.getAsJsonObject();
            FileExplorerRuntime.TabDescriptor descriptor = new FileExplorerRuntime.TabDescriptor();
            descriptor.path = BrowserJson.string(value, "path");
            descriptor.apiType = BrowserJson.string(value, "apiType");
            descriptor.apiHost = BrowserJson.string(value, "apiHost");
            JsonElement extras = BrowserJson.element(value, "extras");
            if (extras != null && extras.isJsonObject()) {
                extras.getAsJsonObject().entrySet().forEach(entry -> {
                    if (entry.getValue() != null && entry.getValue().isJsonPrimitive()) {
                        descriptor.extras.put(entry.getKey(), entry.getValue().getAsString());
                    }
                });
            }
            tabs.add(descriptor);
        });
        return new FileExplorerRuntime.LoadedState(tabs, BrowserJson.integer(root, "activeIndex", -1));
    }

    private static FileExplorerRuntime.LoadedState filter(FileExplorerRuntime.LoadedState state) {
        if (state == null || state.tabs().isEmpty()) return empty();
        List<FileExplorerRuntime.TabDescriptor> tabs = new ArrayList<>();
        int activeIndex = -1;
        for (int index = 0; index < state.tabs().size(); index++) {
            FileExplorerRuntime.TabDescriptor source = state.tabs().get(index);
            FileExplorerRuntime.TabDescriptor descriptor = remoteDescriptor(source);
            if (descriptor == null) continue;
            if (index == state.activeIndex()) activeIndex = tabs.size();
            tabs.add(descriptor);
        }
        return new FileExplorerRuntime.LoadedState(tabs, activeIndex);
    }

    private static FileExplorerRuntime.TabDescriptor remoteDescriptor(FileExplorerRuntime.TabDescriptor source) {
        if (source == null || source.path == null || source.path.isBlank()) return null;
        String serverId = source.extras.get("serverId");
        String type = source.apiType == null ? "" : source.apiType.trim();
        if (serverId == null || serverId.isBlank() || (!type.isBlank() && !"restudio".equalsIgnoreCase(type))) return null;
        FileExplorerRuntime.TabDescriptor descriptor = new FileExplorerRuntime.TabDescriptor();
        descriptor.path = source.path;
        descriptor.apiType = "restudio";
        descriptor.apiHost = source.apiHost;
        descriptor.extras.putAll(source.extras);
        return descriptor;
    }

    private static String encodeValue(String value) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }
}
