package redxax.oxy.remotely.settings.server;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;

public final class ServerSettingsPack {
    private final String id;
    private final String name;
    private final String description;
    private final int priority;
    private final List<String> applicableSoftwareIds;
    private final List<ServerSettingsDocument> documents;

    public ServerSettingsPack(String id, String name, String description, int priority, Collection<String> applicableSoftwareIds,
                              Collection<ServerSettingsDocument> documents) {
        this.id = required(id, "id");
        this.name = required(name, "name");
        this.description = required(description, "description");
        this.priority = priority;
        this.applicableSoftwareIds = normalizeApplicableIds(applicableSoftwareIds);
        this.documents = documents == null ? List.of() : List.copyOf(documents);
        if (this.documents.isEmpty()) {
            throw new IllegalArgumentException("A settings pack needs at least one document: " + id);
        }
    }

    public ServerSettingsPack(String id, int priority, Collection<String> applicableSoftwareIds, Collection<ServerSettingsDocument> documents) {
        this(id, id, id, priority, applicableSoftwareIds, documents);
    }

    public String id() {
        return id;
    }

    public String name() {
        return name;
    }

    public String description() {
        return description;
    }

    public int priority() {
        return priority;
    }

    public List<String> applicableSoftwareIds() {
        return applicableSoftwareIds;
    }

    public List<String> applicableSoftware() {
        return applicableSoftwareIds;
    }

    public List<ServerSettingsDocument> documents() {
        return documents;
    }

    public boolean appliesTo(Collection<String> softwareTokens) {
        if (softwareTokens == null || softwareTokens.isEmpty()) {
            return false;
        }
        LinkedHashSet<String> tokens = new LinkedHashSet<>();
        softwareTokens.forEach(token -> addTokenVariants(tokens, token));
        return applicableSoftwareIds.stream().anyMatch(pattern -> tokens.stream().anyMatch(token -> wildcardMatches(pattern, token)));
    }

    private static List<String> normalizeApplicableIds(Collection<String> ids) {
        if (ids == null || ids.isEmpty()) {
            throw new IllegalArgumentException("A settings pack needs applicable software IDs");
        }
        LinkedHashSet<String> normalized = new LinkedHashSet<>();
        for (String id : ids) {
            if (id == null || id.isBlank()) {
                throw new IllegalArgumentException("Applicable software IDs must be nonblank");
            }
            normalized.add(id.trim().toLowerCase(Locale.ROOT));
        }
        return List.copyOf(normalized);
    }

    private static void addTokenVariants(Collection<String> tokens, String token) {
        if (token == null || token.isBlank()) {
            return;
        }
        String normalized = token.trim().toLowerCase(Locale.ROOT);
        tokens.add(normalized);
        tokens.add(normalized.replace('_', '-'));
        tokens.add(normalized.replace('-', '_'));
        tokens.add(normalized.replace(' ', '-'));
    }

    private static boolean wildcardMatches(String pattern, String token) {
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
        return Pattern.compile(regex.append('$').toString(), Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE).matcher(token).matches();
    }

    private static String required(String value, String label) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("A nonblank pack " + label + " is required");
        }
        return value.trim();
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof ServerSettingsPack that)) return false;
        return priority == that.priority && id.equals(that.id) && name.equals(that.name) && description.equals(that.description)
                && applicableSoftwareIds.equals(that.applicableSoftwareIds) && documents.equals(that.documents);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, name, description, priority, applicableSoftwareIds, documents);
    }
}
