package redxax.oxy.remotely.ui.settings.data;

import redxax.oxy.remotely.network.ConfigurationFormat;
import redxax.oxy.remotely.network.config.NetworkConfigurationAdapter;
import redxax.oxy.remotely.network.config.NetworkConfigurationAdapters;
import redxax.oxy.remotely.libs.snakeyaml.LoaderOptions;
import redxax.oxy.remotely.libs.snakeyaml.Yaml;
import redxax.oxy.remotely.libs.snakeyaml.constructor.SafeConstructor;
import redxax.oxy.remotely.settings.server.ServerSettingsDocument;
import redxax.oxy.remotely.settings.server.ServerSettingsField;
import redxax.oxy.remotely.settings.server.ServerSettingsFieldType;
import redxax.oxy.remotely.settings.server.ServerSettingsFormat;
import redxax.oxy.remotely.settings.server.ServerSettingsPack;
import redxax.oxy.remotely.settings.server.ServerSettingsSnapshot;
import restudio.rebase.api.RebaseAPI;
import restudio.rebase.api.RebaseApiFactory;
import restudio.rebase.instance.Instance;
import restudio.rescreen.ui.settings.Setting;
import restudio.rescreen.ui.settings.options.ConfigOption;

import java.io.IOException;
import java.io.StringReader;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

public class ServerSettingsDataController implements AutoCloseable {
    public static final String SERVER_PROPERTIES_PATH = "server.properties";
    private static final String REMOVE_VALUE = "\u0000";

    private final Instance source;
    private final Path sourcePath;
    private final ServerSettingsSnapshot snapshot;
    private final RebaseAPI sourceApi;
    private final NetworkConfigurationAdapters adapters;
    private final Object stateLock = new Object();
    private final CompletableFuture<Void> loadFuture;
    private final LinkedHashMap<String, DocumentState> documents = new LinkedHashMap<>();
    private final LinkedHashMap<String, List<FieldBinding>> fieldsByTab = new LinkedHashMap<>();
    private final LinkedHashSet<String> documentPaths = new LinkedHashSet<>();
    private final LinkedHashSet<String> availableDocumentPaths = new LinkedHashSet<>();
    private final LinkedHashSet<String> unavailableDocumentPaths = new LinkedHashSet<>();

    public ServerSettingsDataController(Instance instance, ServerSettingsSnapshot snapshot) {
        this(instance, snapshot, RebaseApiFactory.get(Objects.requireNonNull(instance, "instance")));
    }

    public ServerSettingsDataController(Instance instance, ServerSettingsSnapshot snapshot, RebaseAPI api) {
        source = Objects.requireNonNull(instance, "instance");
        sourcePath = normalizedPath(instance.getPath());
        this.snapshot = snapshot == null ? new ServerSettingsSnapshot(List.of()) : snapshot;
        sourceApi = Objects.requireNonNull(api, "api");
        adapters = new NetworkConfigurationAdapters();
        loadFuture = loadDocuments();
    }

    public CompletableFuture<Void> load() {
        return loadFuture;
    }

    public CompletableFuture<Void> ready() {
        return loadFuture;
    }

    public List<String> tabNames() {
        synchronized (stateLock) {
            return List.copyOf(fieldsByTab.keySet());
        }
    }

    public List<String> getTabNames() {
        return tabNames();
    }

    public List<Setting> settings(String tab) {
        synchronized (stateLock) {
            return buildSettings(fieldsByTab.getOrDefault(tab, List.of()));
        }
    }

    public List<Setting> getSettings(String tab) {
        return settings(tab);
    }

    public List<Setting> settingsForTab(String tab) {
        return settings(tab);
    }

    public Map<String, List<Setting>> settingsByTab() {
        synchronized (stateLock) {
            LinkedHashMap<String, List<Setting>> copy = new LinkedHashMap<>();
            fieldsByTab.forEach((tab, fields) -> copy.put(tab, buildSettings(fields)));
            return Collections.unmodifiableMap(copy);
        }
    }

    public List<String> documentPaths() {
        synchronized (stateLock) {
            return List.copyOf(documentPaths);
        }
    }

    public List<String> availableDocumentPaths() {
        synchronized (stateLock) {
            return List.copyOf(availableDocumentPaths);
        }
    }

    public List<String> unavailableDocumentPaths() {
        synchronized (stateLock) {
            return List.copyOf(unavailableDocumentPaths);
        }
    }

    public List<String> getAvailableDocumentPaths() {
        return availableDocumentPaths();
    }

    public boolean isDocumentAvailable(String relativePath) {
        synchronized (stateLock) {
            return availableDocumentPaths.contains(relativePath);
        }
    }

    public Map<String, String> changedFileContents() {
        synchronized (stateLock) {
            LinkedHashMap<String, String> changed = new LinkedHashMap<>();
            for (DocumentState document : documents.values()) {
                synchronizeChangedValues(document);
                if (!document.changedValues.isEmpty()) {
                    changed.put(document.definition.relativePath(), applyMutations(document, document.baselineContent, document.changedValues));
                }
            }
            return Collections.unmodifiableMap(changed);
        }
    }

    public Map<String, String> getChangedFileContents() {
        return changedFileContents();
    }

    public CompletableFuture<Void> save(Instance target) {
        Objects.requireNonNull(target, "target");
        return loadFuture.thenCompose(ignored -> saveLoaded(target));
    }

    @Override
    public void close() {
        synchronized (stateLock) {
            documents.clear();
            fieldsByTab.clear();
            documentPaths.clear();
            availableDocumentPaths.clear();
            unavailableDocumentPaths.clear();
        }
    }

    private CompletableFuture<Void> loadDocuments() {
        List<DocumentDefinition> definitions = definitions();
        synchronized (stateLock) {
            definitions.forEach(definition -> documentPaths.add(definition.relativePath()));
        }
        List<CompletableFuture<LoadedDocument>> loads = definitions.stream().map(this::loadDocument).toList();
        if (loads.isEmpty()) {
            synchronized (stateLock) {
                rebuildSettings();
            }
            return CompletableFuture.completedFuture(null);
        }
        return CompletableFuture.allOf(loads.toArray(CompletableFuture[]::new)).thenRun(() -> {
            synchronized (stateLock) {
                for (CompletableFuture<LoadedDocument> load : loads) {
                    LoadedDocument loaded = load.join();
                    if (loaded.available()) {
                        DocumentState state = new DocumentState(loaded.definition(), loaded.content(), loaded.exists(), adapters.get(adapterFormat(loaded.definition().format())));
                        initializeDocument(state);
                        documents.put(state.definition.relativePath(), state);
                        availableDocumentPaths.add(state.definition.relativePath());
                    } else if (loaded.definition().required() && !loaded.definition().createIfMissing()) {
                        unavailableDocumentPaths.add(loaded.definition().relativePath());
                    }
                }
                rebuildSettings();
            }
        });
    }

    private CompletableFuture<LoadedDocument> loadDocument(DocumentDefinition definition) {
        Path path = resolve(source, definition.relativePath());
        CompletableFuture<Boolean> existsFuture;
        try {
            existsFuture = sourceApi.fileExists(path);
        } catch (RuntimeException exception) {
            return CompletableFuture.completedFuture(new LoadedDocument(definition, false, false, ""));
        }
        if (existsFuture == null) {
            return CompletableFuture.completedFuture(new LoadedDocument(definition, false, false, ""));
        }
        return existsFuture.handle((exists, error) -> error == null && Boolean.TRUE.equals(exists)).thenCompose(exists -> {
            if (!exists) {
                return CompletableFuture.completedFuture(new LoadedDocument(definition, definition.createIfMissing(), false, ""));
            }
            CompletableFuture<String> readFuture;
            try {
                readFuture = sourceApi.readFile(path);
            } catch (RuntimeException exception) {
                return CompletableFuture.completedFuture(new LoadedDocument(definition, false, true, ""));
            }
            if (readFuture == null) {
                return CompletableFuture.completedFuture(new LoadedDocument(definition, false, true, ""));
            }
            return readFuture.handle((content, error) -> error == null
                    ? new LoadedDocument(definition, true, true, content == null ? "" : content)
                    : new LoadedDocument(definition, false, true, ""));
        });
    }

    private List<DocumentDefinition> definitions() {
        LinkedHashMap<String, DocumentDefinition> definitions = new LinkedHashMap<>();
        for (ServerSettingsPack pack : snapshot.packs()) {
            if (pack == null || !pack.appliesTo(source)) {
                continue;
            }
            for (ServerSettingsDocument document : pack.documents()) {
                if (document == null) {
                    continue;
                }
                DocumentDefinition definition = new DocumentDefinition(document.relativePath(), document.format(), document.required(),
                        document.createIfMissing(), document.fields());
                DocumentDefinition existing = definitions.get(definition.relativePath());
                if (existing == null) {
                    definitions.put(definition.relativePath(), definition);
                } else {
                    definitions.put(definition.relativePath(), existing.merge(definition));
                }
            }
        }
        return List.copyOf(definitions.values());
    }

    private void initializeDocument(DocumentState document) {
        if (isServerProperties(document.definition.relativePath())) {
            updateServerProperties(document.baselineContent);
        }
        for (ServerSettingsField field : document.definition.fields()) {
            boolean present = document.adapter.contains(document.baselineContent, field.key());
            String rawValue = document.adapter.read(document.baselineContent, field.key());
            Object initial = parseValue(document, field, present ? rawValue : null);
            FieldBinding binding = new FieldBinding(document, field, initial, defaultValue(field), present, rawValue);
            document.bindings.add(binding);
            document.baselines.putIfAbsent(field.key(), new BaselineValue(present, rawValue));
        }
    }

    private void rebuildSettings() {
        fieldsByTab.clear();
        for (DocumentState document : documents.values()) {
            for (FieldBinding binding : document.bindings) {
                fieldsByTab.computeIfAbsent(binding.field.tab(), ignored -> new ArrayList<>()).add(binding);
            }
        }
    }

    private List<Setting> buildSettings(List<FieldBinding> bindings) {
        LinkedHashMap<String, Setting.Builder> grouped = new LinkedHashMap<>();
        for (FieldBinding binding : bindings) {
            Setting.Builder builder = grouped.computeIfAbsent(binding.field.group(), Setting.Builder::new);
            builder.addOption(option(binding));
        }
        return grouped.values().stream().map(Setting.Builder::build).toList();
    }

    private ConfigOption<?> option(FieldBinding binding) {
        ServerSettingsField field = binding.field;
        return switch (field.type()) {
            case BOOLEAN -> booleanOption(binding);
            case INTEGER, DECIMAL -> textOption(binding);
            case TEXT, SELECT, DURATION, DURATION_OR_DISABLED, LIST, MAP, BOOLEAN_OR_DEFAULT, BOOLEAN_OR_DISABLED, INTEGER_OR_DEFAULT, INTEGER_OR_DISABLED, DECIMAL_OR_DEFAULT, DECIMAL_OR_DISABLED -> textOption(binding);
        };
    }

    private ConfigOption<Boolean> booleanOption(FieldBinding binding) {
        ServerSettingsField field = binding.field;
        return ConfigOption.<Boolean>builder(field.name())
                .description(field.description())
                .bind(() -> (Boolean) readValue(binding), value -> writeValue(binding, value))
                .defaultValue((Boolean) binding.defaultValue)
                .build();
    }

    private ConfigOption<String> textOption(FieldBinding binding) {
        ServerSettingsField field = binding.field;
        ConfigOption.Builder<String> builder = ConfigOption.<String>builder(field.name())
                .description(field.description())
                .bind(() -> (String) readValue(binding), value -> writeValue(binding, value))
                .defaultValue((String) binding.defaultValue);
        if (field.type() == ServerSettingsFieldType.SELECT) {
            List<String> options = new ArrayList<>(field.options());
            String current = binding.value == null ? null : binding.value.toString();
            if (current != null && !options.contains(current)) {
                options.add(current);
            }
            builder.options(options);
        }
        return builder.build();
    }

    private Object readValue(FieldBinding binding) {
        synchronized (stateLock) {
            if (isServerProperties(binding.document.definition.relativePath())) {
                String raw = source.getServerProperties().getProperty(binding.field.key());
                return raw == null ? binding.value : parseValue(binding.field, raw);
            }
            return binding.value;
        }
    }

    private void writeValue(FieldBinding binding, Object value) {
        synchronized (stateLock) {
            Object parsed = parseValue(binding.field, serializeValue(binding.field.type(), value));
            boolean sameInitial = Objects.equals(parsed, binding.value);
            boolean hadChange = binding.document.changedValues.containsKey(binding.field.key());
            binding.value = parsed;
            if (sameInitial && !hadChange) {
                return;
            }
            String serialized = serializeDocumentValue(binding.document, binding.field, parsed);
            BaselineValue baseline = binding.document.baselines.getOrDefault(binding.field.key(), new BaselineValue(false, ""));
            if (sameInitial) {
                binding.document.changedValues.remove(binding.field.key());
            } else if (baseline.same(!isRemoval(serialized), comparableValue(binding.document, binding.field.key(), serialized))) {
                binding.document.changedValues.remove(binding.field.key());
            } else {
                binding.document.changedValues.put(binding.field.key(), serialized);
            }
            if (isServerProperties(binding.document.definition.relativePath())) {
                String propertiesValue = serializeValue(binding.field.type(), parsed);
                if (binding.field.nullable() && isNullToken(propertiesValue)) {
                    source.getServerProperties().remove(binding.field.key());
                } else {
                    source.getServerProperties().setProperty(binding.field.key(), propertiesValue);
                }
            }
        }
    }

    private CompletableFuture<Void> saveLoaded(Instance target) {
        List<SaveRequest> changed = new ArrayList<>();
        synchronized (stateLock) {
            for (DocumentState document : documents.values()) {
                if (isServerProperties(document.definition.relativePath())) {
                    continue;
                }
                synchronizeChangedValues(document);
                if (!document.changedValues.isEmpty()) {
                    changed.add(new SaveRequest(document, document.changedValues));
                }
            }
        }
        if (changed.isEmpty()) {
            return CompletableFuture.completedFuture(null);
        }
        boolean sameTarget = sameInstancePath(target);
        RebaseAPI targetApi = sameTarget ? sourceApi : RebaseApiFactory.get(target);
        List<CompletableFuture<SavePlan>> plans = changed.stream().map(request -> readSavePlan(targetApi, target, request)).toList();
        return CompletableFuture.allOf(plans.toArray(CompletableFuture[]::new)).thenCompose(ignored -> {
            List<SavePlan> resolved = plans.stream().map(CompletableFuture::join).toList();
            if (sameTarget) {
                try {
                    resolved.forEach(this::validateConflict);
                } catch (RuntimeException exception) {
                    return CompletableFuture.failedFuture(exception);
                }
            }
            CompletableFuture<Void> writes = CompletableFuture.completedFuture(null);
            for (SavePlan plan : resolved) {
                writes = writes.thenCompose(ignoredWrite -> plan.api().writeFile(plan.path(), plan.updatedContent()).thenRun(() -> markSaved(plan)));
            }
            return writes;
        });
    }

    private CompletableFuture<SavePlan> readSavePlan(RebaseAPI api, Instance target, SaveRequest request) {
        DocumentState document = request.document();
        Path path = resolve(target, document.definition.relativePath());
        CompletableFuture<Boolean> exists;
        try {
            exists = api.fileExists(path);
        } catch (RuntimeException exception) {
            return CompletableFuture.failedFuture(exception);
        }
        if (exists == null) {
            return CompletableFuture.failedFuture(new IllegalStateException("Could not check configuration document: " + document.definition.relativePath()));
        }
        return exists.thenCompose(present -> {
            if (!Boolean.TRUE.equals(present)) {
                String updated = applyMutations(document, "", request.mutations());
                return CompletableFuture.completedFuture(new SavePlan(api, path, document, request.mutations(), false, "", updated));
            }
            CompletableFuture<String> content = api.readFile(path);
            if (content == null) {
                return CompletableFuture.failedFuture(new IllegalStateException("Could not read configuration document: " + document.definition.relativePath()));
            }
            return content.thenApply(latest -> {
                String resolved = latest == null ? "" : latest;
                return new SavePlan(api, path, document, request.mutations(), true, resolved, applyMutations(document, resolved, request.mutations()));
            });
        });
    }

    private void validateConflict(SavePlan plan) {
        DocumentState document = plan.document();
        for (String key : plan.mutations().keySet()) {
            BaselineValue baseline = document.baselines.getOrDefault(key, new BaselineValue(false, ""));
            boolean latestPresent = plan.exists() && document.adapter.contains(plan.latestContent(), key);
            String latestValue = plan.exists() ? document.adapter.read(plan.latestContent(), key) : "";
            if (!baseline.same(latestPresent, latestValue)) {
                throw new ServerSettingsConflictException(document.definition.relativePath(), key, baseline.value(), latestValue);
            }
        }
    }

    private void markSaved(SavePlan plan) {
        synchronized (stateLock) {
            DocumentState document = plan.document();
            document.baselineContent = plan.updatedContent();
            document.exists = true;
            plan.mutations().forEach((key, value) -> document.changedValues.remove(key, value));
            Map<String, String> remaining = new LinkedHashMap<>(document.changedValues);
            refreshBaselines(document);
            for (FieldBinding binding : document.bindings) {
                String value = remaining.get(binding.field.key());
                if (value != null) {
                    binding.value = parseValue(document, binding.field, value);
                }
            }
        }
    }

    private String applyMutations(DocumentState document, String content, Map<String, String> mutations) {
        String updated = content == null ? "" : content;
        Map<String, String> effective = mergeOverlappingMapMutations(document, mutations);
        List<Map.Entry<String, String>> ordered = effective.entrySet().stream()
                .sorted(Comparator.comparingInt(entry -> keyDepth(entry.getKey())))
                .toList();
        for (Map.Entry<String, String> mutation : ordered) {
            updated = isRemoval(mutation.getValue())
                    ? document.adapter.remove(updated, mutation.getKey())
                    : document.adapter.apply(updated, mutation.getKey(), mutation.getValue());
        }
        return updated;
    }

    private Map<String, String> mergeOverlappingMapMutations(DocumentState document, Map<String, String> mutations) {
        LinkedHashMap<String, String> effective = new LinkedHashMap<>(mutations);
        for (Map.Entry<String, String> root : mutations.entrySet()) {
            if (isRemoval(root.getValue())) {
                continue;
            }
            Object parsed = yamlValue(root.getValue());
            if (!(parsed instanceof Map<?, ?>)) {
                continue;
            }
            LinkedHashMap<Object, Object> merged = mutableMap(parsed);
            boolean changed = false;
            List<String> rootSegments = pathSegments(root.getKey());
            for (Map.Entry<String, String> nested : mutations.entrySet()) {
                List<String> nestedSegments = pathSegments(nested.getKey());
                if (nestedSegments.size() > rootSegments.size() && nestedSegments.subList(0, rootSegments.size()).equals(rootSegments)) {
                    applyNestedMutation(merged, nestedSegments.subList(rootSegments.size(), nestedSegments.size()), nested.getValue());
                    effective.remove(nested.getKey());
                    changed = true;
                }
            }
            if (changed) {
                effective.put(root.getKey(), structuredDefault(merged, true));
            }
        }
        return effective;
    }

    private void applyNestedMutation(Map<Object, Object> root, List<String> segments, String value) {
        Map<Object, Object> current = root;
        for (int index = 0; index < segments.size() - 1; index++) {
            String segment = segments.get(index);
            Object next = current.get(segment);
            if (!(next instanceof Map<?, ?>)) {
                LinkedHashMap<Object, Object> replacement = new LinkedHashMap<>();
                current.put(segment, replacement);
                current = replacement;
            } else {
                LinkedHashMap<Object, Object> replacement = mutableMap(next);
                current.put(segment, replacement);
                current = replacement;
            }
        }
        String leaf = segments.getLast();
        if (isRemoval(value)) {
            current.remove(leaf);
        } else {
            current.put(leaf, yamlValue(value));
        }
    }

    private LinkedHashMap<Object, Object> mutableMap(Object value) {
        LinkedHashMap<Object, Object> result = new LinkedHashMap<>();
        if (value instanceof Map<?, ?> map) {
            map.forEach((key, nested) -> result.put(key, nested instanceof Map<?, ?> ? mutableMap(nested) : nested));
        }
        return result;
    }

    private Object yamlValue(String value) {
        if (value == null || isRemoval(value)) {
            return null;
        }
        try {
            LoaderOptions options = new LoaderOptions();
            options.setAllowDuplicateKeys(false);
            return new Yaml(new SafeConstructor(options)).load(value);
        } catch (RuntimeException exception) {
            return value;
        }
    }

    private int keyDepth(String key) {
        return Math.max(0, pathSegments(key).size() - 1);
    }

    private List<String> pathSegments(String key) {
        List<String> segments = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean escaped = false;
        char[] characters = key == null ? new char[0] : key.toCharArray();
        for (char character : characters) {
            if (escaped) {
                current.append(character);
                escaped = false;
            } else if (character == '\\') {
                escaped = true;
            } else if (character == '.') {
                segments.add(current.toString());
                current.setLength(0);
            } else {
                current.append(character);
            }
        }
        if (escaped) {
            current.append('\\');
        }
        segments.add(current.toString());
        return segments;
    }

    private String comparableValue(DocumentState document, String key, String serialized) {
        String content = isRemoval(serialized) ? document.adapter.remove("", key) : document.adapter.apply("", key, serialized);
        return document.adapter.read(content, key);
    }

    private void synchronizeChangedValues(DocumentState document) {
        for (FieldBinding binding : document.bindings) {
            if (isServerProperties(document.definition.relativePath())) {
                String raw = source.getServerProperties().getProperty(binding.field.key());
                if (raw != null) {
                    Object value = parseValue(binding.field, raw);
                    if (!Objects.equals(value, binding.value)) {
                        binding.value = value;
                        document.changedValues.put(binding.field.key(), serializeValue(binding.field.type(), value));
                    }
                }
            }
        }
    }

    private void refreshBaselines(DocumentState document) {
        document.baselines.clear();
        for (FieldBinding binding : document.bindings) {
            boolean present = document.adapter.contains(document.baselineContent, binding.field.key());
            String raw = document.adapter.read(document.baselineContent, binding.field.key());
            document.baselines.putIfAbsent(binding.field.key(), new BaselineValue(present, raw));
            binding.presentAtLoad = present;
            binding.rawAtLoad = raw;
            binding.value = parseValue(document, binding.field, present ? raw : null);
        }
    }

    private void updateServerProperties(String content) {
        if (content == null) {
            return;
        }
        Properties properties = new Properties();
        try (StringReader reader = new StringReader(content)) {
            properties.load(reader);
        } catch (IOException ignored) {
            return;
        }
        source.getServerProperties().clear();
        source.getServerProperties().putAll(properties);
    }

    private Object parseValue(ServerSettingsField field, String raw) {
        String value = raw == null ? "" : raw.trim();
        Object fallback = defaultValue(field);
        if (raw == null || value.isEmpty()) {
            return fallback;
        }
        if (field.nullable() && isNullToken(value)) {
            return "null";
        }
        try {
            return switch (field.type()) {
                case BOOLEAN -> value.equalsIgnoreCase("true") ? true : value.equalsIgnoreCase("false") ? false : fallback;
                case BOOLEAN_OR_DEFAULT -> booleanUnionValue(value, "default", fallback);
                case BOOLEAN_OR_DISABLED -> booleanUnionValue(value, "disabled", fallback);
                case INTEGER -> validateInteger(value, field) ? value : fallback;
                case DECIMAL -> validateDecimal(value, field) ? value : fallback;
                case INTEGER_OR_DEFAULT -> unionIntegerValue(value, "default", fallback, field);
                case INTEGER_OR_DISABLED -> unionIntegerValue(value, "disabled", fallback, field);
                case DECIMAL_OR_DEFAULT -> unionDecimalValue(value, "default", fallback, field);
                case DECIMAL_OR_DISABLED -> unionDecimalValue(value, "disabled", fallback, field);
                case TEXT -> raw;
                case DURATION -> validDuration(value) ? raw : fallback;
                case DURATION_OR_DISABLED -> value.equalsIgnoreCase("disabled") || validDuration(value) ? raw : fallback;
                case SELECT -> raw;
                case LIST -> structuredValue(field, raw, '[', ']') ? raw : fallback;
                case MAP -> structuredValue(field, raw, '{', '}') ? raw : fallback;
            };
        } catch (RuntimeException exception) {
            return fallback;
        }
    }

    private Object parseValue(DocumentState document, ServerSettingsField field, String raw) {
        return parseValue(field, decodeDocumentValue(document, field.type(), raw));
    }

    private String decodeDocumentValue(DocumentState document, ServerSettingsFieldType type, String value) {
        if (value == null) {
            return value;
        }
        if (document.definition.format() == ServerSettingsFormat.PROPERTIES) {
            Properties properties = new Properties();
            try (StringReader reader = new StringReader("value=" + value)) {
                properties.load(reader);
                return properties.getProperty("value", value);
            } catch (IOException ignored) {
                return value;
            }
        }
        if (document.definition.format() != ServerSettingsFormat.TOML || type != ServerSettingsFieldType.TEXT && type != ServerSettingsFieldType.SELECT && type != ServerSettingsFieldType.DURATION && type != ServerSettingsFieldType.DURATION_OR_DISABLED) {
            return value;
        }
        String trimmed = value.trim();
        if (trimmed.length() < 2) {
            return trimmed;
        }
        if (trimmed.startsWith("\"") && trimmed.endsWith("\"")) {
            return trimmed.substring(1, trimmed.length() - 1).replace("\\\"", "\"").replace("\\\\", "\\");
        }
        if (trimmed.startsWith("'") && trimmed.endsWith("'")) {
            return trimmed.substring(1, trimmed.length() - 1);
        }
        return trimmed;
    }

    private String serializeDocumentValue(DocumentState document, ServerSettingsField field, Object value) {
        ServerSettingsFieldType type = field.type();
        String serialized = serializeValue(type, value);
        if (field.nullable() && isNullToken(serialized)) {
            if (document.definition.format() == ServerSettingsFormat.TOML || document.definition.format() == ServerSettingsFormat.PROPERTIES) {
                return REMOVE_VALUE;
            }
            return "null";
        }
        if (document.definition.format() == ServerSettingsFormat.YAML && (type == ServerSettingsFieldType.TEXT || type == ServerSettingsFieldType.SELECT || type == ServerSettingsFieldType.DURATION || type == ServerSettingsFieldType.DURATION_OR_DISABLED)) {
            if (type == ServerSettingsFieldType.SELECT && serialized.matches("-?\\d+(?:\\.\\d+)?")) {
                return serialized;
            }
            return "'" + serialized.replace("'", "''") + "'";
        }
        if (document.definition.format() != ServerSettingsFormat.TOML || type != ServerSettingsFieldType.TEXT && type != ServerSettingsFieldType.SELECT && type != ServerSettingsFieldType.DURATION && type != ServerSettingsFieldType.DURATION_OR_DISABLED) {
            return serialized;
        }
        return "\"" + serialized.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }

    private Object defaultValue(ServerSettingsField field) {
        if (field.defaultSpecified()) {
            if (field.defaultValue() == null) {
                return field.nullable() ? "null" : "";
            }
            return switch (field.type()) {
                case BOOLEAN -> (Boolean) field.defaultValue();
                case INTEGER, DECIMAL -> field.defaultValue().toString();
                case TEXT, SELECT, DURATION, DURATION_OR_DISABLED, BOOLEAN_OR_DEFAULT, BOOLEAN_OR_DISABLED, INTEGER_OR_DEFAULT, INTEGER_OR_DISABLED, DECIMAL_OR_DEFAULT, DECIMAL_OR_DISABLED -> field.defaultValue().toString();
                case LIST -> structuredDefault(field.defaultValue(), false);
                case MAP -> structuredDefault(field.defaultValue(), true);
            };
        }
        return switch (field.type()) {
            case BOOLEAN -> false;
            case BOOLEAN_OR_DEFAULT -> "default";
            case BOOLEAN_OR_DISABLED -> "disabled";
            case INTEGER -> "0";
            case DECIMAL -> "0.0";
            case INTEGER_OR_DEFAULT -> "default";
            case INTEGER_OR_DISABLED -> "disabled";
            case DECIMAL_OR_DEFAULT -> "default";
            case DECIMAL_OR_DISABLED -> "disabled";
            case TEXT -> "";
            case SELECT -> field.options().getFirst();
            case DURATION -> "";
            case DURATION_OR_DISABLED -> "disabled";
            case LIST -> "[]";
            case MAP -> "{}";
        };
    }

    private String serializeValue(ServerSettingsFieldType type, Object value) {
        if (value == null) {
            return "";
        }
        return switch (type) {
            case BOOLEAN -> Boolean.toString((Boolean) value);
            case INTEGER, DECIMAL, TEXT, SELECT, DURATION, DURATION_OR_DISABLED, LIST, MAP, BOOLEAN_OR_DEFAULT, BOOLEAN_OR_DISABLED, INTEGER_OR_DEFAULT, INTEGER_OR_DISABLED, DECIMAL_OR_DEFAULT, DECIMAL_OR_DISABLED -> value.toString();
        };
    }

    private String booleanUnionValue(String value, String sentinel, Object fallback) {
        return value.equalsIgnoreCase(sentinel) || value.equalsIgnoreCase("true") || value.equalsIgnoreCase("false") ? value : (String) fallback;
    }

    private String unionIntegerValue(String value, String sentinel, Object fallback, ServerSettingsField field) {
        if (value.equalsIgnoreCase(sentinel)) {
            return value;
        }
        try {
            BigInteger parsed = new BigInteger(value);
            BigDecimal decimal = new BigDecimal(parsed);
            if (field.min() != null && decimal.compareTo(field.min()) < 0 || field.max() != null && decimal.compareTo(field.max()) > 0) {
                return (String) fallback;
            }
            return value;
        } catch (NumberFormatException ignored) {
            return (String) fallback;
        }
    }

    private String unionDecimalValue(String value, String sentinel, Object fallback, ServerSettingsField field) {
        if (value.equalsIgnoreCase(sentinel)) {
            return value;
        }
        try {
            BigDecimal parsed = new BigDecimal(value);
            if (field.min() != null && parsed.compareTo(field.min()) < 0 || field.max() != null && parsed.compareTo(field.max()) > 0) {
                return (String) fallback;
            }
            return value;
        } catch (NumberFormatException ignored) {
            return (String) fallback;
        }
    }

    private boolean validateInteger(String value, ServerSettingsField field) {
        try {
            BigInteger parsed = new BigInteger(value);
            BigDecimal decimal = new BigDecimal(parsed);
            return (field.min() == null || decimal.compareTo(field.min()) >= 0)
                    && (field.max() == null || decimal.compareTo(field.max()) <= 0);
        } catch (NumberFormatException exception) {
            return false;
        }
    }

    private boolean validateDecimal(String value, ServerSettingsField field) {
        try {
            BigDecimal parsed = new BigDecimal(value);
            return (field.min() == null || parsed.compareTo(field.min()) >= 0)
                    && (field.max() == null || parsed.compareTo(field.max()) <= 0);
        } catch (NumberFormatException exception) {
            return false;
        }
    }

    private boolean isNullToken(String value) {
        return value.equalsIgnoreCase("null") || value.equals("~");
    }

    private boolean isRemoval(String value) {
        return REMOVE_VALUE.equals(value);
    }

    private boolean validDuration(String value) {
        return value != null && value.matches("(?i)(?:-?\\d+(?:\\.\\d+)?)(?:ms|s|m|h|d|t)?");
    }

    private boolean structuredValue(ServerSettingsField field, String raw, char opening, char closing) {
        String value = raw == null ? "" : raw.trim();
        if (value.isEmpty()) {
            return false;
        }
        try {
            LoaderOptions options = new LoaderOptions();
            options.setAllowDuplicateKeys(false);
            Object parsed = new Yaml(new SafeConstructor(options)).load(value);
            return field.type() == ServerSettingsFieldType.LIST ? parsed instanceof List<?> : parsed instanceof Map<?, ?>;
        } catch (RuntimeException exception) {
            return false;
        }
    }

    private String structuredDefault(Object value, boolean map) {
        if (value == null) {
            return map ? "{}" : "[]";
        }
        if (value instanceof Map<?, ?> values) {
            return values.entrySet().stream().map(entry -> formatFlow(entry.getKey()) + ": " + formatFlow(entry.getValue()))
                    .collect(Collectors.joining(", ", "{", "}"));
        }
        if (value instanceof Iterable<?> values) {
            List<String> items = new ArrayList<>();
            values.forEach(item -> items.add(formatFlow(item)));
            return items.isEmpty() ? "[]" : "[" + String.join(", ", items) + "]";
        }
        return value.toString();
    }

    private String formatFlow(Object value) {
        if (value == null) return "null";
        if (value instanceof Map<?, ?>) return structuredDefault(value, true);
        if (value instanceof Iterable<?>) return structuredDefault(value, false);
        if (value instanceof Number || value instanceof Boolean) return value.toString();
        String text = value.toString();
        if (text.matches("[A-Za-z0-9_./:-]+")) return text;
        return "'" + text.replace("'", "''") + "'";
    }

    private boolean isServerProperties(String path) {
        return SERVER_PROPERTIES_PATH.equals(path);
    }

    private ConfigurationFormat adapterFormat(ServerSettingsFormat format) {
        return switch (format) {
            case PROPERTIES -> ConfigurationFormat.PROPERTIES;
            case YAML -> ConfigurationFormat.YAML;
            case TOML -> ConfigurationFormat.TOML;
        };
    }

    private Path resolve(Instance instance, String relativePath) {
        String root = instance.getPath();
        if (root == null || root.isBlank()) {
            return Path.of(relativePath);
        }
        Path base = Path.of(root).toAbsolutePath().normalize();
        Path target = base.resolve(relativePath).normalize();
        if (!target.startsWith(base)) {
            throw new IllegalArgumentException("Configuration path escapes the instance: " + relativePath);
        }
        return target;
    }

    private boolean sameInstancePath(Instance target) {
        return sourcePath != null && sourcePath.equals(normalizedPath(target.getPath()));
    }

    private Path normalizedPath(String value) {
        return value == null || value.isBlank() ? null : Path.of(value).toAbsolutePath().normalize();
    }

    private record DocumentDefinition(String relativePath, ServerSettingsFormat format, boolean required, boolean createIfMissing,
                                      List<ServerSettingsField> fields) {
        private DocumentDefinition {
            fields = fields == null ? List.of() : List.copyOf(fields);
        }

        private DocumentDefinition merge(DocumentDefinition other) {
            if (format != other.format) {
                throw new IllegalArgumentException("Multiple formats were registered for configuration document: " + relativePath);
            }
            List<ServerSettingsField> merged = new ArrayList<>(fields);
            for (ServerSettingsField field : other.fields) {
                if (merged.stream().anyMatch(existing -> existing.id().equalsIgnoreCase(field.id()) || existing.key().equals(field.key()))) {
                    throw new IllegalArgumentException("Duplicate configuration field in " + relativePath + ": " + field.id());
                }
                merged.add(field);
            }
            return new DocumentDefinition(relativePath, format, required || other.required, createIfMissing || other.createIfMissing, merged);
        }
    }

    private record LoadedDocument(DocumentDefinition definition, boolean available, boolean exists, String content) {
    }

    private record SaveRequest(DocumentState document, Map<String, String> mutations) {
        private SaveRequest {
            mutations = Collections.unmodifiableMap(new LinkedHashMap<>(mutations));
        }
    }

    private record SavePlan(RebaseAPI api, Path path, DocumentState document, Map<String, String> mutations, boolean exists,
                            String latestContent, String updatedContent) {
    }

    private static final class DocumentState {
        private final DocumentDefinition definition;
        private final NetworkConfigurationAdapter adapter;
        private final List<FieldBinding> bindings = new ArrayList<>();
        private final LinkedHashMap<String, BaselineValue> baselines = new LinkedHashMap<>();
        private final LinkedHashMap<String, String> changedValues = new LinkedHashMap<>();
        private String baselineContent;
        private boolean exists;

        private DocumentState(DocumentDefinition definition, String baselineContent, boolean exists, NetworkConfigurationAdapter adapter) {
            this.definition = definition;
            this.baselineContent = baselineContent == null ? "" : baselineContent;
            this.exists = exists;
            this.adapter = adapter;
        }
    }

    private static final class FieldBinding {
        private final DocumentState document;
        private final ServerSettingsField field;
        private final Object defaultValue;
        private Object value;
        private boolean presentAtLoad;
        private String rawAtLoad;

        private FieldBinding(DocumentState document, ServerSettingsField field, Object value, Object defaultValue,
                             boolean presentAtLoad, String rawAtLoad) {
            this.document = document;
            this.field = field;
            this.defaultValue = defaultValue;
            this.value = value;
            this.presentAtLoad = presentAtLoad;
            this.rawAtLoad = rawAtLoad;
        }
    }

    private record BaselineValue(boolean present, String value) {
        private boolean same(boolean latestPresent, String latestValue) {
            return present == latestPresent && Objects.equals(value, latestValue);
        }

        private boolean matches(String desired) {
            return present && Objects.equals(value, desired);
        }
    }
}
