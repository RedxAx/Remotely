package redxax.oxy.remotely.settings.server;

import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

public final class ServerSettingsDocument {
    private final String relativePath;
    private final ServerSettingsFormat format;
    private final boolean required;
    private final boolean createIfMissing;
    private final List<ServerSettingsField> fields;

    public ServerSettingsDocument(String relativePath, ServerSettingsFormat format, boolean required, boolean createIfMissing,
                                  List<ServerSettingsField> fields) {
        this.relativePath = safePath(relativePath);
        this.format = Objects.requireNonNull(format, "format");
        this.required = required;
        this.createIfMissing = createIfMissing;
        this.fields = fields == null ? List.of() : List.copyOf(fields);
        validateFields(this.fields);
    }

    public ServerSettingsDocument(String relativePath, ServerSettingsFormat format, boolean required, boolean createIfMissing) {
        this(relativePath, format, required, createIfMissing, List.of());
    }

    public ServerSettingsDocument(String relativePath, ServerSettingsFormat format, boolean required) {
        this(relativePath, format, required, false);
    }

    public String relativePath() {
        return relativePath;
    }

    public String path() {
        return relativePath;
    }

    public ServerSettingsFormat format() {
        return format;
    }

    public boolean required() {
        return required;
    }

    public boolean createIfMissing() {
        return createIfMissing;
    }

    public boolean optional() {
        return !required;
    }

    public List<ServerSettingsField> fields() {
        return fields;
    }

    private static String safePath(String path) {
        if (path == null || path.isBlank()) {
            throw new IllegalArgumentException("A document path is required");
        }
        String trimmed = path.trim();
        if (trimmed.indexOf('\0') >= 0 || trimmed.indexOf('\\') >= 0) {
            throw new IllegalArgumentException("Document paths must use safe relative separators: " + path);
        }
        Path parsed;
        try {
            parsed = Path.of(trimmed);
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException("Invalid document path: " + path, exception);
        }
        if (parsed.isAbsolute() || parsed.getNameCount() == 0 || trimmed.startsWith("/") || trimmed.matches("^[A-Za-z]:.*")) {
            throw new IllegalArgumentException("Document path must be relative: " + path);
        }
        for (Path segment : parsed) {
            if (segment.toString().equals("..") || segment.toString().equals(".")) {
                throw new IllegalArgumentException("Document path must not contain traversal segments: " + path);
            }
        }
        return parsed.normalize().toString().replace((char) 92, '/');
    }

    private static void validateFields(List<ServerSettingsField> fields) {
        Set<String> ids = new HashSet<>();
        Set<String> keys = new HashSet<>();
        for (ServerSettingsField field : fields) {
            if (field == null || !ids.add(field.id()) || !keys.add(field.key())) {
                throw new IllegalArgumentException("Document field IDs and keys must be unique");
            }
        }
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof ServerSettingsDocument that)) return false;
        return required == that.required && createIfMissing == that.createIfMissing && relativePath.equals(that.relativePath) && format == that.format
                && fields.equals(that.fields);
    }

    @Override
    public int hashCode() {
        return Objects.hash(relativePath, format, required, createIfMissing, fields);
    }
}
