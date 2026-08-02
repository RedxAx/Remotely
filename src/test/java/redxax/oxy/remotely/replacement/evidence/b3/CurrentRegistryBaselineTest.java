package redxax.oxy.remotely.flow.cache;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import redxax.oxy.remotely.flow.registry.NodeDefinition;
import redxax.oxy.remotely.flow.registry.NodeRegistry;
import redxax.oxy.remotely.flow.sync.FlowOptionSourceMetadata;
import redxax.oxy.remotely.flow.sync.NodePluginPayload;
import redxax.oxy.remotely.flow.sync.NodeRegistrySnapshot;
import restudio.resync.flow.contract.FlowTypeMetadata;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.SplittableRandom;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CurrentRegistryBaselineTest {
    private static final Gson GSON = new Gson();

    @TempDir
    Path temporaryDirectory;

    @Test
    void recordsDeterministicCurrentRegistryPayloadHydrationAndCacheBaseline() throws Exception {
        BaselineFixture fixture = GSON.fromJson(Files.readString(Path.of("src", "test", "resources", "fixtures", "node-replacement", "b3", "registry-baseline.json")), BaselineFixture.class);
        NodeRegistrySnapshot snapshot = snapshot(fixture);
        byte[] wirePayload = canonicalBytes(snapshot);
        assertEquals(new String(wirePayload, StandardCharsets.UTF_8), new String(canonicalBytes(snapshot(fixture)), StandardCharsets.UTF_8));

        NodeRegistryCache cache = new NodeRegistryCache(temporaryDirectory.resolve("registry-cache.json"));
        cache.applySnapshot("baseline-server", snapshot);
        NodeRegistrySnapshot restored = cache.getSnapshot("baseline-server");
        byte[] cacheProjection = canonicalBytes(restored);
        assertEquals(new String(cacheProjection, StandardCharsets.UTF_8), new String(canonicalBytes(cache.getSnapshot("baseline-server")), StandardCharsets.UTF_8));
        CacheProjectionManifest manifest = cacheProjectionManifest(snapshot, restored);
        System.out.printf("B3 registry baseline diagnostic wirePayloadBytes=%d wirePayloadSha256=%s cacheProjectionBytes=%d cacheProjectionSha256=%s manifest=%s%n",
            wirePayload.length, sha256(wirePayload), cacheProjection.length, sha256(cacheProjection), manifest.describe());
        assertNotEquals(new String(wirePayload, StandardCharsets.UTF_8), new String(cacheProjection, StandardCharsets.UTF_8));
        assertTrue(manifest.preservedByPluginId(), manifest::describe);
        assertTrue(manifest.onlyExpectedTransformations(), manifest::describe);
        assertEquals(fixture.pluginCount(), restored.getPlugins().size());
        assertEquals(fixture.pluginCount() * fixture.nodesPerPlugin(), restored.getNodeIds().size());
        assertEquals(fixture.typeCount(), restored.getTypeMetadata().size());
        assertEquals(fixture.optionSourceCount(), restored.getOptionSourceMetadata().size());

        NodeRegistry registry = new NodeRegistry();
        warm(registry, snapshot, fixture.warmupIterations());
        List<Long> hydrationNanos = measureHydration(registry, snapshot, fixture.measurementIterations());
        List<Long> cacheNanos = measureCacheMaterialization(fixture, fixture.measurementIterations());
        long payloadBytes = wirePayload.length;
        long projectionBytes = cacheProjection.length;
        long cacheBytes = Files.size(temporaryDirectory.resolve("registry-cache.json"));
        long memoryDelta = memoryDelta(snapshot);

        assertEquals(fixture.pluginCount() * fixture.nodesPerPlugin(), registry.getAllDefinitions("baseline-server").size());
        System.out.printf("B3 registry baseline wirePayloadBytes=%d wirePayloadSha256=%s cacheProjectionBytes=%d cacheProjectionSha256=%s cacheFileBytes=%d manifest=%s nodes=%d plugins=%d types=%d options=%d hydrateMedianNanos=%d hydrateP95Nanos=%d cacheMedianNanos=%d cacheP95Nanos=%d memoryDeltaBytes=%d%n",
            payloadBytes, sha256(wirePayload), projectionBytes, sha256(cacheProjection), cacheBytes, manifest.describe(), restored.getNodeIds().size(), restored.getPlugins().size(), restored.getTypeMetadata().size(),
            restored.getOptionSourceMetadata().size(), percentile(hydrationNanos, 50), percentile(hydrationNanos, 95),
            percentile(cacheNanos, 50), percentile(cacheNanos, 95), memoryDelta);
        assertPayloadGolden(fixture, payloadBytes, sha256(wirePayload), projectionBytes, sha256(cacheProjection));
        assertTrue(cacheBytes > 0L);
    }

    private void assertPayloadGolden(BaselineFixture fixture, long wirePayloadBytes, String wirePayloadSha256,
                                     long cacheProjectionBytes, String cacheProjectionSha256) {
        if (fixture.wirePayloadBytes() > 0) {
            assertEquals(fixture.wirePayloadBytes(), wirePayloadBytes);
            assertEquals(fixture.wirePayloadSha256(), wirePayloadSha256);
            assertEquals(fixture.cacheProjectionBytes(), cacheProjectionBytes);
            assertEquals(fixture.cacheProjectionSha256(), cacheProjectionSha256);
        }
    }

    private CacheProjectionManifest cacheProjectionManifest(NodeRegistrySnapshot wire, NodeRegistrySnapshot projection) {
        JsonObject wireJson = canonical(GSON.toJsonTree(wire)).getAsJsonObject();
        JsonObject projectionJson = canonical(GSON.toJsonTree(projection)).getAsJsonObject();
        List<String> wireOrder = wire.getPlugins().stream().map(NodePluginPayload::getPluginId).toList();
        List<String> projectionOrder = projection.getPlugins().stream().map(NodePluginPayload::getPluginId).toList();
        Map<String, JsonElement> wirePlugins = pluginsById(wireJson.getAsJsonArray("plugins"));
        Map<String, JsonElement> projectionPlugins = pluginsById(projectionJson.getAsJsonArray("plugins"));
        wireJson.remove("plugins");
        projectionJson.remove("plugins");
        List<String> changedFields = changedTopLevelFields(wireJson, projectionJson);
        boolean cacheMapDefaults = !wireJson.has("propertyActions") && !wireJson.has("propertyOutputTypes")
            && projectionJson.get("propertyActions").isJsonObject() && projectionJson.getAsJsonObject("propertyActions").size() == 0
            && projectionJson.get("propertyOutputTypes").isJsonObject() && projectionJson.getAsJsonObject("propertyOutputTypes").size() == 0;
        return new CacheProjectionManifest(wireOrder, projectionOrder, wirePlugins.equals(projectionPlugins), changedFields, cacheMapDefaults);
    }

    private List<String> changedTopLevelFields(JsonObject wire, JsonObject projection) {
        TreeSet<String> fields = new TreeSet<>();
        fields.addAll(wire.keySet());
        fields.addAll(projection.keySet());
        return fields.stream().filter(field -> !Objects.equals(wire.get(field), projection.get(field))).toList();
    }

    private Map<String, JsonElement> pluginsById(JsonArray plugins) {
        Map<String, JsonElement> result = new LinkedHashMap<>();
        for (JsonElement plugin : plugins) {
            result.put(plugin.getAsJsonObject().get("pluginId").getAsString(), plugin);
        }
        return result;
    }

    private NodeRegistrySnapshot snapshot(BaselineFixture fixture) {
        SplittableRandom random = new SplittableRandom(fixture.seed());
        NodeRegistrySnapshot snapshot = new NodeRegistrySnapshot();
        snapshot.setContractVersion(NodeRegistrySnapshot.CURRENT_CONTRACT_VERSION);
        snapshot.setMinimumClientContractVersion(NodeRegistrySnapshot.MINIMUM_SUPPORTED_CONTRACT_VERSION);
        snapshot.setServerIdentity("baseline-server");
        snapshot.setFullSync(true);
        snapshot.setRegistryChecksum("b3-current-registry-baseline");
        snapshot.setGeneratedAt(1_784_246_400_000L);
        snapshot.setCapabilities(List.of("nodes", "types", "catalogs", "extensions"));
        snapshot.setRegistryDiagnostics(Map.of("fixture", "b3", "seed", fixture.seed()));

        List<NodePluginPayload> plugins = new ArrayList<>();
        List<String> nodeIds = new ArrayList<>();
        for (int plugin = 0; plugin < fixture.pluginCount(); plugin++) {
            NodePluginPayload payload = new NodePluginPayload();
            payload.setPluginId("fixture:" + plugin);
            payload.setVersion("1.0." + plugin);
            payload.setChecksum("fixture-" + plugin);
            List<NodeDefinition> nodes = new ArrayList<>();
            for (int node = 0; node < fixture.nodesPerPlugin(); node++) {
                String id = "fixture:" + plugin + ".node_" + node;
                nodeIds.add(id);
                nodes.add(new NodeDefinition.Builder(id, "Node " + plugin + " " + node, NodeDefinition.NodeCategory.UTILITY)
                    .description("Deterministic registry baseline node " + random.nextInt())
                    .handler("fixture:handler")
                    .build());
            }
            payload.setNodes(nodes);
            plugins.add(payload);
        }
        snapshot.setPlugins(plugins);
        snapshot.setNodeIds(nodeIds);

        List<FlowTypeMetadata> types = new ArrayList<>();
        for (int type = 0; type < fixture.typeCount(); type++) {
            FlowTypeMetadata metadata = new FlowTypeMetadata("fixture:type_" + type, "Fixture Type " + type,
                0xFF000000 | random.nextInt(0x00FFFFFF), "string", true, true, true);
            metadata.setOwner("fixture");
            metadata.setCatalogSource("fixture:catalog_" + (type % fixture.optionSourceCount()));
            types.add(metadata);
        }
        snapshot.setTypeMetadata(types);

        List<FlowOptionSourceMetadata> sources = new ArrayList<>();
        for (int source = 0; source < fixture.optionSourceCount(); source++) {
            sources.add(new FlowOptionSourceMetadata("fixture:catalog_" + source, "fixture", "SEARCHABLE_LIST", true,
                "Fixture Catalog " + source, "fixture:type_" + (source % fixture.typeCount()), List.of("player", "world")));
        }
        snapshot.setOptionSourceMetadata(sources);
        return snapshot;
    }

    private void warm(NodeRegistry registry, NodeRegistrySnapshot snapshot, int iterations) {
        for (int iteration = 0; iteration < iterations; iteration++) {
            registry.clearServer("baseline-server");
            assertTrue(registry.applySnapshot("baseline-server", snapshot));
        }
    }

    private List<Long> measureHydration(NodeRegistry registry, NodeRegistrySnapshot snapshot, int iterations) {
        List<Long> values = new ArrayList<>();
        for (int iteration = 0; iteration < iterations; iteration++) {
            registry.clearServer("baseline-server");
            long started = System.nanoTime();
            assertTrue(registry.applySnapshot("baseline-server", snapshot));
            values.add(System.nanoTime() - started);
        }
        return values;
    }

    private List<Long> measureCacheMaterialization(BaselineFixture fixture, int iterations) throws Exception {
        List<Long> values = new ArrayList<>();
        for (int iteration = 0; iteration < iterations; iteration++) {
            Path path = temporaryDirectory.resolve("cache-" + iteration + ".json");
            NodeRegistryCache cache = new NodeRegistryCache(path);
            cache.applySnapshot("baseline-server", snapshot(fixture));
            long started = System.nanoTime();
            NodeRegistrySnapshot restored = cache.getSnapshot("baseline-server");
            values.add(System.nanoTime() - started);
            assertEquals(fixture.pluginCount() * fixture.nodesPerPlugin(), restored.getNodeIds().size());
        }
        return values;
    }

    private long memoryDelta(NodeRegistrySnapshot snapshot) {
        Runtime runtime = Runtime.getRuntime();
        long before = runtime.totalMemory() - runtime.freeMemory();
        NodeRegistry registry = new NodeRegistry();
        registry.applySnapshot("memory-baseline-server", snapshot);
        long after = runtime.totalMemory() - runtime.freeMemory();
        return Math.max(0L, after - before);
    }

    private byte[] canonicalBytes(NodeRegistrySnapshot snapshot) {
        return canonical(GSON.toJsonTree(snapshot)).toString().getBytes(StandardCharsets.UTF_8);
    }

    private String sha256(byte[] value) throws Exception {
        byte[] digest = MessageDigest.getInstance("SHA-256").digest(value);
        StringBuilder result = new StringBuilder();
        for (byte part : digest) {
            result.append(String.format("%02x", part));
        }
        return result.toString();
    }

    private JsonElement canonical(JsonElement element) {
        if (element.isJsonArray()) {
            JsonArray array = new JsonArray();
            element.getAsJsonArray().forEach(value -> array.add(canonical(value)));
            return array;
        }
        if (!element.isJsonObject()) {
            return element.deepCopy();
        }
        JsonObject object = new JsonObject();
        element.getAsJsonObject().entrySet().stream().sorted(Comparator.comparing(Map.Entry::getKey))
            .forEach(entry -> object.add(entry.getKey(), canonical(entry.getValue())));
        return object;
    }

    private long percentile(List<Long> values, int percentile) {
        List<Long> sorted = values.stream().sorted().toList();
        return sorted.get(Math.min(sorted.size() - 1, Math.max(0, (int) Math.ceil(sorted.size() * percentile / 100.0) - 1)));
    }

    private record BaselineFixture(long seed, int pluginCount, int nodesPerPlugin, int typeCount, int optionSourceCount,
                                   int warmupIterations, int measurementIterations, long wirePayloadBytes, String wirePayloadSha256,
                                   long cacheProjectionBytes, String cacheProjectionSha256) {
    }

    private record CacheProjectionManifest(List<String> wirePluginOrder, List<String> cachePluginOrder, boolean preservedByPluginId,
                                           List<String> changedTopLevelFields, boolean cacheMapDefaults) {
        private boolean onlyExpectedTransformations() {
            return preservedByPluginId && cacheMapDefaults && !wirePluginOrder.equals(cachePluginOrder)
                && changedTopLevelFields.equals(List.of("propertyActions", "propertyOutputTypes"));
        }

        private String describe() {
            return "pluginOrder=" + wirePluginOrder + "->" + cachePluginOrder + ", pluginContentById=" + preservedByPluginId
                + ", changedTopLevelFields=" + changedTopLevelFields + ", cacheMapDefaults=" + cacheMapDefaults;
        }
    }
}
