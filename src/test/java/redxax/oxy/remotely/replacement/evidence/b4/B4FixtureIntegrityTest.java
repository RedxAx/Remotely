package redxax.oxy.remotely.replacement.evidence.b4;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class B4FixtureIntegrityTest {
    private static final String ROOT = "fixtures/node-replacement/b4/";
    private static final String MANIFEST_HASH = "13de00bca2a24b7b3fb236534def44433c056a0955014347126ab8ecb98f13e1";

    @Test
    void fixturesRemainDeterministicJsonEvidence() throws Exception {
        JsonObject manifest = object("manifest.json");
        JsonObject heads = manifest.getAsJsonObject("supportedHeads");
        JsonObject fixtures = manifest.getAsJsonObject("fixtures");

        assertEquals("b2941e66d762ffb76391715fbf4487c068fe12ea", heads.get("resync").getAsString());
        assertEquals("76c4eb8989aaf856a4f002fa7fe6e56a67c373df", heads.get("remotely").getAsString());
        assertTrue(manifest.getAsJsonArray("sourceAnchors").size() >= 5);
        assertEquals(MANIFEST_HASH, sha256(bytes("manifest.json")));
        assertTrue(manifest.getAsJsonArray("sourceAnchors").asList().stream()
            .map(value -> value.getAsString()).allMatch(this::sourceAnchorExists));

        for (Map.Entry<String, com.google.gson.JsonElement> entry : fixtures.entrySet()) {
            byte[] bytes = bytes(entry.getKey());
            JsonObject root = JsonParser.parseString(new String(bytes, StandardCharsets.UTF_8)).getAsJsonObject();

            assertTrue(root.has("fixture"));
            assertEquals(entry.getValue().getAsString(), sha256(bytes));
        }

        JsonObject missing = object("missing-provider-graph.json");
        assertEquals(2, missing.getAsJsonArray("connections").size());
        assertTrue(missing.getAsJsonObject("nodes").getAsJsonObject("missing").has("opaqueNodeField"));

        JsonObject sameId = object("same-id-resources.json");
        assertEquals(3, sameId.getAsJsonArray("resources").size());
        assertEquals(3, sameId.getAsJsonArray("resources").asList().stream()
            .map(value -> value.getAsJsonObject().get("type").getAsString()).distinct().count());
    }

    private byte[] bytes(String file) throws IOException {
        try (InputStream stream = getClass().getClassLoader().getResourceAsStream(ROOT + file)) {
            if (stream == null) {
                throw new IOException("Missing fixture: " + file);
            }
            return stream.readAllBytes();
        }
    }

    private JsonObject object(String file) throws IOException {
        return JsonParser.parseString(new String(bytes(file), StandardCharsets.UTF_8)).getAsJsonObject();
    }

    private boolean sourceAnchorExists(String anchor) {
        int separator = anchor.indexOf('#');
        if (separator < 1 || separator == anchor.length() - 1) {
            return false;
        }
        String source = anchor.substring(0, separator);
        Path path = source.startsWith("Remotely/")
            ? Path.of(source.substring("Remotely/".length()))
            : Path.of("..").resolve(source);
        try {
            return Files.isRegularFile(path) && Files.readString(path).contains(anchor.substring(separator + 1) + "(");
        } catch (IOException exception) {
            return false;
        }
    }

    private String sha256(byte[] bytes) throws NoSuchAlgorithmException {
        StringBuilder result = new StringBuilder();
        for (byte value : MessageDigest.getInstance("SHA-256").digest(bytes)) {
            result.append(String.format("%02x", value));
        }
        return result.toString();
    }
}
