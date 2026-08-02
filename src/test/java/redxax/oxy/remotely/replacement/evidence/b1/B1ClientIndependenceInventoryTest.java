package redxax.oxy.remotely.replacement.evidence.b1;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class B1ClientIndependenceInventoryTest {
    private static final Map<Path, String> SOURCE_ROOTS = Map.of(
        Path.of("src/main/java/redxax/oxy/remotely"), "Remotely",
        Path.of("RemotelyMod/src/main/java"), "RemotelyMod"
    );
    private static final List<Path> CONTRACT_SOURCES = List.of(Path.of("contracts/resync-protocol.json"));
    private static final Path FIXTURE = Path.of("src/test/resources/fixtures/node-replacement/b1/client-independence-inventory.sha256");
    private static final Path RECORDS_FIXTURE = Path.of("src/test/resources/fixtures/node-replacement/b1/client-independence-inventory.tsv");
    private static final List<Rule> RULES = List.of(
        new Rule("NODE_ID_BRANCH", Pattern.compile("(?:getDefinition|registerServerDefinition|unregisterServerDefinition)\\s*\\(|(?:nodeType|nodeId|definitionId)\\s*(?:==|!=|\\.equals|\\.equalsIgnoreCase|\\.contains)|(?:[A-Z][A-Z0-9_]*|\"[^\"]+\")\\.equals\\(.*getType\\(\\)|switch\\s*\\(.*getType\\(\\)")),
        new Rule("RESOURCE_TYPE_BRANCH", Pattern.compile("(?:enum\\s+ReSyncResourceType|ReSyncResourceType\\.|(?:switch|if)\\s*\\(.*(?:resourceType|type))")),
        new Rule("OPTION_SOURCE_BRANCH", Pattern.compile("(?:optionSource|optionsSource|getServerOptionSource|requestOptionCatalog)")),
        new Rule("PIN_NAME_BRANCH", Pattern.compile("(?:getInputValue|setInputValue|getInputValues\\(\\)\\.(?:get|put|remove)|sourcePin|targetPin).*(?:\"[^\"]+\")")),
        new Rule("STRUCTURAL_KEY_BRANCH", Pattern.compile("(?:getInputValues|setInputValue|sourcePin|targetPin|functionInputs|functionOutputs|EditorPassthrough|editorPassthroughs|branch|repeatable|passthrough)")),
        new Rule("MIRRORED_MODEL_ROUTE", Pattern.compile("(?:FlowGraph|FlowNode|FlowConnection|FlowSerializer|NodeRegistrySnapshot|NodePluginPayload|NodeRegistryCache|NodeRegistryTombstoneCache|SyncedResourceCache|TypedGraphCache|ReSyncFrameCodec)")),
        new Rule("PACKET_ROUTE", Pattern.compile("(?:requestByte|listRequestByte|dataResponseByte|saveByte|deleteByte|saveAckByte|sendFrame|handleDataMessage|FLOW_PACKET_)"))
    );

    @Test
    void sourceInventoryIsCanonicalAndCompleteForTheDeclaredScope() throws Exception {
        List<String> records = inventory();
        assertFalse(records.isEmpty());
        String canonical = String.join("\n", records) + "\n";
        String actual = sha256(canonical);
        String expected = Files.readString(FIXTURE, StandardCharsets.UTF_8).trim();
        List<String> expectedRecords = Files.readAllLines(RECORDS_FIXTURE, StandardCharsets.UTF_8);
        assertEquals(expectedRecords, records, "B1 inventory records changed");
        assertEquals(expected, actual, () -> "B1 canonical inventory SHA-256 changed: " + actual + " records=" + records.size());
        assertControl(records, "NODE_ID_BRANCH|Remotely/flow/ui/NodeWidget.java|3387|if (!\"flow.switch_case\".equals(node.getType()) || node.getInputValues() == null) {");
        assertControl(records, "NODE_ID_BRANCH|Remotely/flow/ui/NodeWidget.java|1080|boolean scheduleNode = AUTOMATION_SCHEDULE_ID.equals(node.getType());");
        assertControl(records, "PIN_NAME_BRANCH|Remotely/data/flow/FlowManager.java|2886|Object previousCommand = start != null && start.getInputValues() != null ? start.getInputValues().get(\"command\") : null;");
        assertControl(records, "RESOURCE_TYPE_BRANCH|Remotely/data/flow/ReSyncResourceType.java|16|public enum ReSyncResourceType {");
    }

    private List<String> inventory() throws IOException {
        List<String> records = new ArrayList<>();
        for (Map.Entry<Path, String> root : SOURCE_ROOTS.entrySet()) {
            try (var paths = Files.walk(root.getKey())) {
                paths.filter(path -> path.toString().endsWith(".java")).sorted().forEach(path -> addRecords(records, root.getKey(), root.getValue(), path));
            }
        }
        CONTRACT_SOURCES.forEach(path -> addRecords(records, path.getParent(), "Contract", path));
        records.sort(Comparator.naturalOrder());
        return records;
    }

    private void addRecords(List<String> records, Path root, String scope, Path path) {
        try {
            List<String> lines = Files.readAllLines(path, StandardCharsets.UTF_8);
            String relative = scope + "/" + root.relativize(path).toString().replace('\\', '/');
            for (int index = 0; index < lines.size(); index++) {
                String line = lines.get(index).strip();
                if (line.isEmpty()) {
                    continue;
                }
                for (Rule rule : RULES) {
                    if (rule.pattern().matcher(line).find()) {
                        records.add(rule.category() + "|" + relative + "|" + (index + 1) + "|" + line);
                    }
                }
            }
        } catch (IOException exception) {
            throw new IllegalStateException("Could not inventory " + path, exception);
        }
    }

    private String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private void assertControl(List<String> records, String expected) {
        assertTrue(records.contains(expected), () -> "Missing B1 inventory control: " + expected);
    }

    private record Rule(String category, Pattern pattern) {
    }
}
