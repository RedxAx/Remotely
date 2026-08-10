package redxax.oxy.remotely.settings.server;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ServerSettingsCatalogCoverageTest {
    private static final List<String> METADATA_RESOURCES = List.of(
            "server-settings/builtin.yml",
            "server-settings/server-properties.yml",
            "server-settings/bukkit.yml",
            "server-settings/spigot.yml",
            "server-settings/paper-global.yml",
            "server-settings/paper-world.yml",
            "server-settings/velocity.yml"
    );
    private static final Map<String, Set<String>> PACK_ALIASES = Map.of(
            "server", Set.of("server", "minecraft-server", "server-properties", "minecraft-server-properties"),
            "bukkit", Set.of("bukkit"),
            "spigot", Set.of("spigot", "spigot-paper", "spigot-world-overrides"),
            "paper", Set.of("paper", "paper-global"),
            "paper-world", Set.of("paper-world"),
            "velocity", Set.of("velocity")
    );
    private static final Set<String> ALLOWLIST_REASONS = Set.of(
            "deprecated", "deprecated-version-drift", "internal-version", "managed-internal", "sensitive", "sensitive-value", "unstable-instance-key"
    );

    @Test
    void installedYamlPathsHaveStableCatalogCoverage() throws IOException {
        Catalog catalog = Catalog.load("server-settings/fixtures/installed-catalog.tsv");
        List<ServerSettingsPack> packs = loadMetadata();
        List<String> missing = missing(catalog.required(), packs);
        List<String> missingDynamic = missing(catalog.dynamic(), packs);

        assertEquals(464, catalog.required().size());
        assertEquals(38, catalog.allowlisted().size());
        assertEquals(17, catalog.dynamic().size());
        assertTrue(catalog.allowlisted().stream().allMatch(row -> ALLOWLIST_REASONS.contains(row.reason())));
        assertTrue(catalog.dynamic().stream().allMatch(row -> !row.reason().isBlank()));
        assertTrue(missing.isEmpty(), () -> "Missing installed YAML catalog paths (" + missing.size() + "): " + missing);
        assertTrue(missingDynamic.isEmpty(), () -> "Missing installed YAML dynamic sections (" + missingDynamic.size() + "): " + missingDynamic);
    }

    @Test
    void installedServerPropertiesHaveStableCatalogCoverage() throws IOException {
        Catalog catalog = Catalog.load("server-settings/fixtures/installed-server-properties.tsv");
        List<ServerSettingsPack> packs = loadMetadata();
        List<String> missing = missing(catalog.required(), packs);

        assertEquals(74, catalog.required().size());
        assertEquals(13, catalog.allowlisted().size());
        assertTrue(catalog.allowlisted().stream().allMatch(row -> ALLOWLIST_REASONS.contains(row.reason())));
        assertTrue(missing.isEmpty(), () -> "Missing installed server.properties catalog paths (" + missing.size() + "): " + missing);
    }

    @Test
    void catalogFieldsDoNotDuplicatePackDocumentKeys() throws IOException {
        List<String> duplicates = duplicateFields(loadMetadata());

        assertTrue(duplicates.isEmpty(), () -> "Duplicate catalog fields: " + duplicates);
    }

    private static List<ServerSettingsPack> loadMetadata() throws IOException {
        ServerSettingsMetadataParser parser = new ServerSettingsMetadataParser();
        List<ServerSettingsPack> packs = new ArrayList<>();
        for (String resource : METADATA_RESOURCES) {
            try (InputStream input = ServerSettingsCatalogCoverageTest.class.getClassLoader().getResourceAsStream(resource)) {
                if (input == null) {
                    continue;
                }
                packs.addAll(parser.parse(input, resource).packs());
            }
        }
        return packs;
    }

    private static List<String> missing(List<CatalogRow> rows, Collection<ServerSettingsPack> packs) {
        List<String> missing = new ArrayList<>();
        for (CatalogRow row : rows) {
            if (packs.stream().noneMatch(pack -> covers(row, pack))) {
                missing.add(row.pack() + ":" + row.document() + ":" + row.path() + " [" + row.shape() + "]");
            }
        }
        return missing;
    }

    private static boolean covers(CatalogRow row, ServerSettingsPack pack) {
        if (!PACK_ALIASES.getOrDefault(row.pack(), Set.of(row.pack())).contains(pack.id())) {
            return false;
        }
        return pack.documents().stream()
                .filter(document -> document.relativePath().equals(row.document()))
                .flatMap(document -> document.fields().stream())
                .anyMatch(field -> covers(row, field));
    }

    private static boolean covers(CatalogRow row, ServerSettingsField field) {
        String fieldKey = field.key();
        String rowPath = row.path();
        boolean typedDescendant = row.shape().equals("map") && fieldKey.startsWith(rowPath + ".");
        boolean exact = fieldKey.equals(rowPath) || wildcardMatches(fieldKey, rowPath);
        boolean structuredAncestor = field.type() == ServerSettingsFieldType.MAP
                && (rowPath.startsWith(fieldKey + ".") || wildcardAncestorMatches(fieldKey, rowPath));
        return typedDescendant || (exact || structuredAncestor) && (structuredAncestor || typeCovers(row.shape(), field));
    }

    private static boolean typeCovers(String shape, ServerSettingsField field) {
        ServerSettingsFieldType type = field.type();
        return Arrays.stream(shape.split("\\|"))
                .map(String::trim)
                .anyMatch(value -> switch (value) {
                    case "boolean" -> type == ServerSettingsFieldType.BOOLEAN || type.booleanUnion();
                    case "integer" -> type == ServerSettingsFieldType.INTEGER || type.integerUnion() || selectHasNumericOptions(field, true);
                    case "number" -> type == ServerSettingsFieldType.DECIMAL || type.decimalUnion() || selectHasNumericOptions(field, false);
                    case "string" -> !type.numeric() && !type.structured() || type.sentinel();
                    case "mixed" -> !type.structured();
                    case "list" -> type == ServerSettingsFieldType.LIST;
                    case "map" -> type == ServerSettingsFieldType.MAP;
                    default -> false;
                });
    }

    private static boolean wildcardAncestorMatches(String pattern, String value) {
        if (!pattern.endsWith(".*")) {
            return false;
        }
        String prefix = pattern.substring(0, pattern.length() - 2);
        return !prefix.isBlank() && (value.equals(prefix) || value.startsWith(prefix + "."));
    }

    private static boolean selectHasNumericOptions(ServerSettingsField field, boolean integral) {
        if (field.type() != ServerSettingsFieldType.SELECT || field.options().isEmpty()) {
            return false;
        }
        try {
            return field.options().stream().allMatch(option -> {
                BigDecimal value = new BigDecimal(option);
                return !integral || value.stripTrailingZeros().scale() <= 0;
            });
        } catch (NumberFormatException exception) {
            return false;
        }
    }

    private static boolean wildcardMatches(String pattern, String value) {
        if (!pattern.contains("*") && !pattern.contains("?")) {
            return false;
        }
        StringBuilder regex = new StringBuilder("^");
        for (int index = 0; index < pattern.length(); index++) {
            char character = pattern.charAt(index);
            if (character == '*') {
                regex.append(".*");
            } else if (character == '?') {
                regex.append('.');
            } else {
                regex.append(Pattern.quote(String.valueOf(character)));
            }
        }
        return Pattern.compile(regex.append('$').toString(), Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE).matcher(value).matches();
    }

    private static List<String> duplicateFields(Collection<ServerSettingsPack> packs) {
        Map<String, Set<String>> fields = new LinkedHashMap<>();
        List<String> duplicates = new ArrayList<>();
        for (ServerSettingsPack pack : packs) {
            String canonicalPack = canonicalPack(pack.id());
            for (ServerSettingsDocument document : pack.documents()) {
                String documentKey = canonicalPack + ":" + document.relativePath();
                Set<String> keys = fields.computeIfAbsent(documentKey, ignored -> new HashSet<>());
                for (ServerSettingsField field : document.fields()) {
                    if (!keys.add(field.key())) {
                        duplicates.add(documentKey + ":" + field.key());
                    }
                }
            }
        }
        return duplicates;
    }

    private static String canonicalPack(String packId) {
        return PACK_ALIASES.entrySet().stream()
                .filter(entry -> entry.getValue().contains(packId))
                .map(Map.Entry::getKey)
                .findFirst()
                .orElse(packId.toLowerCase(Locale.ROOT));
    }

    private record CatalogRow(String kind, String pack, String document, String path, String shape, String reason) {
    }

    private record Catalog(List<CatalogRow> required, List<CatalogRow> allowlisted, List<CatalogRow> dynamic) {
        private static Catalog load(String resource) throws IOException {
            List<CatalogRow> required = new ArrayList<>();
            List<CatalogRow> allowlisted = new ArrayList<>();
            List<CatalogRow> dynamic = new ArrayList<>();
            try (InputStream input = ServerSettingsCatalogCoverageTest.class.getClassLoader().getResourceAsStream(resource)) {
                if (input == null) {
                    throw new IllegalStateException("Missing catalog fixture: " + resource);
                }
                try (InputStreamReader reader = new InputStreamReader(input, StandardCharsets.UTF_8)) {
                    StringBuilder content = new StringBuilder();
                    char[] buffer = new char[4096];
                    int read;
                    while ((read = reader.read(buffer)) >= 0) {
                        content.append(buffer, 0, read);
                    }
                    for (String line : content.toString().split("\\R")) {
                        if (line.isBlank() || line.startsWith("#")) {
                            continue;
                        }
                        String[] parts = line.split("\\|", -1);
                        if (parts.length != 6) {
                            throw new IllegalArgumentException("Invalid catalog row: " + line);
                        }
                        CatalogRow row = new CatalogRow(parts[0], parts[1], parts[2], parts[3], parts[4], parts[5]);
                        switch (row.kind()) {
                            case "REQUIRED" -> required.add(row);
                            case "ALLOW" -> allowlisted.add(row);
                            case "DYNAMIC" -> dynamic.add(row);
                            default -> throw new IllegalArgumentException("Unknown catalog row kind: " + row.kind());
                        }
                    }
                }
            }
            return new Catalog(List.copyOf(required), List.copyOf(allowlisted), List.copyOf(dynamic));
        }
    }
}
