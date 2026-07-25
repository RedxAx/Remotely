package redxax.oxy.remotely.data.flow;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class OptionCatalogLoader {
    private OptionCatalogLoader() {
    }

    public static Profile profile(String... sources) {
        List<Request> requests = new ArrayList<>();
        if (sources != null) {
            for (String source : sources) {
                if (source != null && !source.isBlank()) {
                    requests.add(new Request(source, Map.of()));
                }
            }
        }
        return new Profile(requests);
    }

    public static Request request(String source) {
        return new Request(source, Map.of());
    }

    public static Request request(String source, Map<String, Object> context) {
        return new Request(source, context);
    }

    public static void preload(String serverId, String source) {
        preload(serverId, new Request(source, Map.of()));
    }

    public static void preload(String serverId, String source, Map<String, Object> context) {
        preload(serverId, new Request(source, context));
    }

    public static void preload(String serverId, Request... requests) {
        preload(serverId, requests != null ? Arrays.asList(requests) : List.of());
    }

    public static void preload(String serverId, Collection<Request> requests) {
        load(serverId, requests, false);
    }

    public static void refresh(String serverId, String source) {
        refresh(serverId, new Request(source, Map.of()));
    }

    public static void refresh(String serverId, String source, Map<String, Object> context) {
        refresh(serverId, new Request(source, context));
    }

    public static void refresh(String serverId, Request... requests) {
        load(serverId, requests != null ? Arrays.asList(requests) : List.of(), true);
    }

    public static Snapshot snapshot(String serverId, String source) {
        return snapshot(serverId, source, Map.of());
    }

    public static Snapshot snapshot(String serverId, String source, Map<String, Object> context) {
        Request request = new Request(source, context);
        FlowManager manager = FlowManager.getInstance();
        if (manager == null || serverId == null || serverId.isBlank() || request.source().isBlank()) {
            return Snapshot.missing(request);
        }
        ReSyncFlowClient client = manager.ensureFlowClient(serverId);
        String contextKey = client.optionCatalogContextKey(request.context());
        client.requestOptionCatalog(request.source(), request.context());
        OptionCatalogCache cache = OptionCatalogCache.getInstance();
        boolean present = cache.hasCatalog(serverId, request.source(), contextKey);
        boolean loading = !present || cache.isStale(serverId, request.source(), contextKey)
            || cache.isRequestInFlight(serverId, request.source(), contextKey);
        return new Snapshot(request, contextKey, cache.getValues(serverId, request.source(), contextKey),
            cache.getItems(serverId, request.source(), contextKey), loading,
            cache.getStatus(serverId, request.source(), contextKey), cache.getDiagnostic(serverId, request.source(), contextKey));
    }

    private static void load(String serverId, Collection<Request> requests, boolean forceRefresh) {
        FlowManager manager = FlowManager.getInstance();
        if (manager == null || serverId == null || serverId.isBlank() || requests == null || requests.isEmpty()) {
            return;
        }
        ReSyncFlowClient client = manager.ensureFlowClient(serverId);
        Map<String, Request> distinct = new LinkedHashMap<>();
        for (Request request : requests) {
            if (request == null || request.source().isBlank()) {
                continue;
            }
            String key = request.source() + "\u0000" + client.optionCatalogContextKey(request.context());
            distinct.putIfAbsent(key, request);
        }
        for (Request request : distinct.values()) {
            client.requestOptionCatalog(request.source(), request.context(), forceRefresh);
        }
    }

    public record Request(String source, Map<String, Object> context) {
        public Request {
            source = source != null ? source : "";
            if (context == null || context.isEmpty()) {
                context = Map.of();
            } else {
                Map<String, Object> copied = new LinkedHashMap<>();
                context.forEach((key, value) -> {
                    if (key != null) {
                        copied.put(key, value);
                    }
                });
                context = copied.isEmpty() ? Map.of() : Collections.unmodifiableMap(copied);
            }
        }
    }

    public record Profile(List<Request> requests) {
        public Profile {
            requests = requests != null ? requests.stream().filter(request -> request != null && !request.source().isBlank()).toList() : List.of();
        }

        public void preload(String serverId) {
            OptionCatalogLoader.preload(serverId, requests);
        }

        public void refresh(String serverId) {
            OptionCatalogLoader.load(serverId, requests, true);
        }
    }

    public record Snapshot(Request request, String contextKey, List<String> values, List<OptionCatalogItem> items,
                           boolean loading, String status, String diagnostic) {
        public Snapshot {
            request = request != null ? request : new Request("", Map.of());
            contextKey = contextKey != null ? contextKey : "";
            values = values != null ? List.copyOf(values) : List.of();
            items = items != null ? items.stream().filter(item -> item != null).toList() : List.of();
            status = status != null ? status : "missing";
            diagnostic = diagnostic != null ? diagnostic : "";
        }

        private static Snapshot missing(Request request) {
            return new Snapshot(request, "", List.of(), List.of(), true, "missing", "Catalog has not been loaded");
        }
    }
}
