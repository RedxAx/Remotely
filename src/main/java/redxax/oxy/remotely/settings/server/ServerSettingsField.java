package redxax.oxy.remotely.settings.server;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.List;
import java.util.Objects;

public final class ServerSettingsField {
    public enum Type {
        BOOLEAN,
        BOOLEAN_OR_DEFAULT,
        BOOLEAN_OR_DISABLED,
        INTEGER,
        INTEGER_OR_DEFAULT,
        INTEGER_OR_DISABLED,
        DECIMAL,
        DECIMAL_OR_DEFAULT,
        DECIMAL_OR_DISABLED,
        TEXT,
        SELECT,
        DURATION,
        DURATION_OR_DISABLED,
        LIST,
        MAP;

        public ServerSettingsFieldType toFieldType() {
            return ServerSettingsFieldType.valueOf(name());
        }
    }

    private final String id;
    private final String key;
    private final ServerSettingsFieldType type;
    private final String tab;
    private final String group;
    private final String name;
    private final String description;
    private final Object defaultValue;
    private final boolean defaultSpecified;
    private final boolean nullable;
    private final BigDecimal min;
    private final BigDecimal max;
    private final List<String> options;

    public ServerSettingsField(String id, String key, ServerSettingsFieldType type, String tab, String group, String name,
                               String description, Object defaultValue, BigDecimal min, BigDecimal max, List<String> options) {
        this(id, key, type, tab, group, name, description, defaultValue, min, max, options, false, defaultValue != null);
    }

    public ServerSettingsField(String id, String key, ServerSettingsFieldType type, String tab, String group, String name,
                               String description, Object defaultValue, BigDecimal min, BigDecimal max, List<String> options,
                               boolean nullable) {
        this(id, key, type, tab, group, name, description, defaultValue, min, max, options, nullable, defaultValue != null);
    }

    public ServerSettingsField(String id, String key, ServerSettingsFieldType type, String tab, String group, String name,
                               String description, Object defaultValue, BigDecimal min, BigDecimal max, List<String> options,
                               boolean nullable, boolean defaultSpecified) {
        this.id = required(id, "id");
        this.key = required(key, "key");
        this.type = Objects.requireNonNull(type, "type");
        this.tab = required(tab, "tab");
        this.group = required(group, "group");
        this.name = required(name, "name");
        this.description = required(description, "description");
        this.defaultValue = immutableValue(defaultValue);
        this.defaultSpecified = defaultSpecified;
        this.nullable = nullable;
        this.min = min;
        this.max = max;
        if (min != null && max != null && min.compareTo(max) > 0) {
            throw new IllegalArgumentException("Field minimum cannot exceed maximum: " + id);
        }
        this.options = options == null ? List.of() : List.copyOf(options);
        validateValueBounds();
    }

    public ServerSettingsField(String id, String key, Type type, String tab, String group, String name, String description,
                               Object defaultValue, BigDecimal min, BigDecimal max, List<String> options) {
        this(id, key, Objects.requireNonNull(type, "type").toFieldType(), tab, group, name, description, defaultValue, min, max, options);
    }

    public ServerSettingsField(String id, String key, Type type, String tab, String group, String name, String description,
                               Object defaultValue, BigDecimal min, BigDecimal max, List<String> options, boolean nullable) {
        this(id, key, Objects.requireNonNull(type, "type").toFieldType(), tab, group, name, description, defaultValue, min, max, options, nullable);
    }

    public ServerSettingsField(String id, String key, Type type, String tab, String group, String name, String description,
                               Object defaultValue, BigDecimal min, BigDecimal max, List<String> options,
                               boolean nullable, boolean defaultSpecified) {
        this(id, key, Objects.requireNonNull(type, "type").toFieldType(), tab, group, name, description, defaultValue, min, max, options, nullable, defaultSpecified);
    }

    public String id() {
        return id;
    }

    public String key() {
        return key;
    }

    public ServerSettingsFieldType type() {
        return type;
    }

    public ServerSettingsFieldType fieldType() {
        return type;
    }

    public String tab() {
        return tab;
    }

    public String group() {
        return group;
    }

    public String name() {
        return name;
    }

    public String description() {
        return description;
    }

    public Object defaultValue() {
        return defaultValue;
    }

    public Object defaultValueOrNull() {
        return defaultValue;
    }

    public boolean defaultSpecified() {
        return defaultSpecified;
    }

    public boolean nullable() {
        return nullable;
    }

    public BigDecimal min() {
        return min;
    }

    public BigDecimal max() {
        return max;
    }

    public BigDecimal minimum() {
        return min;
    }

    public BigDecimal maximum() {
        return max;
    }

    public List<String> options() {
        return options;
    }

    private void validateValueBounds() {
        if (!type.numeric() && (min != null || max != null)) {
            throw new IllegalArgumentException("Only numeric fields may define ranges: " + id);
        }
        if (type == ServerSettingsFieldType.SELECT && options.isEmpty()) {
            throw new IllegalArgumentException("Select fields need at least one option: " + id);
        }
        if (type != ServerSettingsFieldType.SELECT && !options.isEmpty()) {
            throw new IllegalArgumentException("Only select fields may define options: " + id);
        }
        for (String option : options) {
            required(option, "option");
        }
        if (options.size() != options.stream().distinct().count()) {
            throw new IllegalArgumentException("Select options must be unique: " + id);
        }
        if (defaultSpecified && defaultValue == null && !nullable) {
            throw new IllegalArgumentException("Null defaults require a nullable field: " + id);
        }
        if (defaultValue != null) {
            switch (type) {
                case BOOLEAN -> requireType(defaultValue instanceof Boolean, "boolean");
                case BOOLEAN_OR_DEFAULT, BOOLEAN_OR_DISABLED -> {
                    requireType(defaultValue instanceof Boolean || defaultValue instanceof String, "boolean or sentinel");
                    String sentinel = type == ServerSettingsFieldType.BOOLEAN_OR_DEFAULT ? "default" : "disabled";
                    if (defaultValue instanceof String value && !value.equalsIgnoreCase(sentinel)) {
                        throw new IllegalArgumentException("Invalid boolean union sentinel: " + id);
                    }
                }
                case INTEGER -> {
                    requireType(isIntegral(defaultValue), "integer");
                    validateNumericDefault();
                }
                case DECIMAL -> {
                    requireType(defaultValue instanceof Number, "decimal");
                    validateNumericDefault();
                }
                case INTEGER_OR_DEFAULT, INTEGER_OR_DISABLED -> {
                    requireType(defaultValue instanceof Number || defaultValue instanceof String, "integer or sentinel");
                    if (defaultValue instanceof Number) {
                        validateNumericDefault();
                        if (!isIntegral(defaultValue)) throw new IllegalArgumentException("Integer union default must be integral: " + id);
                    } else if (!defaultValue.equals(type == ServerSettingsFieldType.INTEGER_OR_DEFAULT ? "default" : "disabled")) {
                        throw new IllegalArgumentException("Invalid integer union sentinel: " + id);
                    }
                }
                case DECIMAL_OR_DEFAULT, DECIMAL_OR_DISABLED -> {
                    requireType(defaultValue instanceof Number || defaultValue instanceof String, "decimal or sentinel");
                    if (defaultValue instanceof Number) validateNumericDefault();
                    else if (!defaultValue.equals(type == ServerSettingsFieldType.DECIMAL_OR_DEFAULT ? "default" : "disabled")) throw new IllegalArgumentException("Invalid decimal union sentinel: " + id);
                }
                case TEXT, DURATION, DURATION_OR_DISABLED -> requireType(defaultValue instanceof String, "text");
                case SELECT -> {
                    requireType(defaultValue instanceof String, "select value");
                    if (!options.contains(defaultValue)) {
                        throw new IllegalArgumentException("Select default is not an option: " + id);
                    }
                }
                case LIST -> requireType(defaultValue instanceof List<?>, "list");
                case MAP -> requireType(defaultValue instanceof Map<?, ?>, "map");
            }
        }
    }

    private void validateNumericDefault() {
        BigDecimal value = numericValue(defaultValue);
        if (min != null && value.compareTo(min) < 0 || max != null && value.compareTo(max) > 0) {
            throw new IllegalArgumentException("Field default is outside its range: " + id);
        }
        if ((type == ServerSettingsFieldType.INTEGER || type.integerUnion()) && value.stripTrailingZeros().scale() > 0) {
            throw new IllegalArgumentException("Integer field default must be integral: " + id);
        }
    }

    private static boolean isIntegral(Object value) {
        if (!(value instanceof Number number)) {
            return false;
        }
        return numericValue(number).stripTrailingZeros().scale() <= 0;
    }

    private static BigDecimal numericValue(Object value) {
        if (!(value instanceof Number number)) {
            throw new IllegalArgumentException("A numeric value is required");
        }
        return new BigDecimal(number.toString());
    }

    private static Object immutableValue(Object value) {
        if (value == null || value instanceof String || value instanceof Boolean || value instanceof Number) return value;
        if (value instanceof List<?> list) {
            return Collections.unmodifiableList(list.stream().map(ServerSettingsField::immutableValue).toList());
        }
        if (value instanceof Map<?, ?> map) {
            LinkedHashMap<Object, Object> immutable = new LinkedHashMap<>();
            map.forEach((key, nested) -> immutable.put(immutableValue(key), immutableValue(nested)));
            return Collections.unmodifiableMap(immutable);
        }
        throw new IllegalArgumentException("Unsupported default value type: " + value.getClass().getName());
    }

    private static void requireType(boolean condition, String expected) {
        if (!condition) {
            throw new IllegalArgumentException("Expected " + expected + " default value");
        }
    }

    private static String required(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("A nonblank field " + name + " is required");
        }
        return value.trim();
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof ServerSettingsField that)) return false;
        return id.equals(that.id) && key.equals(that.key) && type == that.type && tab.equals(that.tab) && group.equals(that.group)
                && name.equals(that.name) && description.equals(that.description) && Objects.equals(defaultValue, that.defaultValue)
                && defaultSpecified == that.defaultSpecified && nullable == that.nullable
                && Objects.equals(min, that.min) && Objects.equals(max, that.max) && options.equals(that.options);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, key, type, tab, group, name, description, defaultValue, defaultSpecified, nullable, min, max, options);
    }
}
