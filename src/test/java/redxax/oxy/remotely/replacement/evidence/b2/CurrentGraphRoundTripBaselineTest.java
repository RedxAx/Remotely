package redxax.oxy.remotely.replacement.evidence.b2;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonElement;
import org.junit.jupiter.api.Test;
import redxax.oxy.remotely.flow.data.FlowGraph;
import redxax.oxy.remotely.flow.data.FlowSerializer;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CurrentGraphRoundTripBaselineTest {
    @Test
    void recordsCurrentRecursiveUnknownFieldRoundTrip() throws Exception {
        JsonObject source = json("fixtures/node-replacement/b2/current-round-trip-source.json");
        JsonObject baseline = json("fixtures/node-replacement/b2/current-round-trip-baseline.json");
        JsonObject expected = json("fixtures/node-replacement/b2/" + baseline.get("canonicalOutput").getAsString());
        FlowGraph graph = FlowSerializer.deserialize(source);
        JsonObject output = FlowSerializer.toJsonObject(graph);

        for (String path : strings(baseline, "preserved")) {
            assertEquals(valueAt(source, path), valueAt(output, path), path);
        }
        for (String path : strings(baseline, "discarded")) {
            assertFalse(has(output, path), path);
        }
        for (String path : strings(baseline, "normalized")) {
            assertFalse(canonical(valueAt(source, path)).equals(canonical(valueAt(output, path))), path);
            assertEquals(valueAt(expected, path), valueAt(output, path), path);
        }
        assertEquals(canonical(expected), canonical(output));
        assertEquals(baseline.get("canonicalOutputSha256").getAsString(), sha256(canonical(expected)));
        assertEquals(baseline.get("canonicalOutputSha256").getAsString(), sha256(canonical(output)));
    }

    private JsonObject json(String path) throws IOException {
        try (InputStream stream = getClass().getClassLoader().getResourceAsStream(path)) {
            assertTrue(stream != null, () -> "Missing fixture: " + path);
            return JsonParser.parseString(new String(stream.readAllBytes(), StandardCharsets.UTF_8)).getAsJsonObject();
        }
    }

    private String sha256(String value) throws Exception {
        byte[] hash = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
        StringBuilder text = new StringBuilder(hash.length * 2);
        for (byte valueByte : hash) {
            text.append(String.format("%02x", valueByte));
        }
        return text.toString();
    }

    private List<String> strings(JsonObject object, String name) {
        return object.getAsJsonArray(name).asList().stream().map(value -> value.getAsString()).toList();
    }

    private boolean has(JsonObject object, String path) {
        return valueAt(object, path) != null;
    }

    private com.google.gson.JsonElement valueAt(JsonObject object, String path) {
        com.google.gson.JsonElement value = object;
        for (String segment : path.substring(1).split("/")) {
            if (value == null) {
                return null;
            }
            value = value.isJsonArray() ? value.getAsJsonArray().get(Integer.parseInt(segment)) : value.getAsJsonObject().get(segment);
        }
        return value;
    }

    private String canonical(JsonElement value) {
        if (value.isJsonArray()) {
            return "[" + value.getAsJsonArray().asList().stream().map(this::canonical).collect(java.util.stream.Collectors.joining(",")) + "]";
        }
        if (!value.isJsonObject()) {
            return value.toString();
        }
        Map<String, JsonElement> fields = new TreeMap<>();
        value.getAsJsonObject().entrySet().forEach(entry -> fields.put(entry.getKey(), entry.getValue()));
        return "{" + fields.entrySet().stream().map(entry -> JsonParser.parseString('"' + entry.getKey() + '"').toString() + ":" + canonical(entry.getValue()))
            .collect(java.util.stream.Collectors.joining(",")) + "}";
    }
}
