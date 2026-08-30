package redxax.oxy.remotely.data.flow;

import redxax.oxy.remotely.flow.data.FlowJson;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.IntFunction;

public class OptionCatalogItem {
    private static final String CUSTOM_DATA = "minecraft:custom_data";
    private String value;
    private String label;
    private String description;
    private String icon;
    private String group;
    private Map<String, Object> metadata;

    public void setValue(String value) {
        this.value = value;
    }

    public void setLabel(String label) {
        this.label = label;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public void setIcon(String icon) {
        this.icon = icon;
    }

    public void setGroup(String group) {
        this.group = group;
    }

    public void setMetadata(Map<String, Object> metadata) {
        this.metadata = metadata;
    }

    public String getValue() {
        return value;
    }

    public String getLabel() {
        return label != null && !label.isBlank() ? label : value;
    }

    public String getDescription() {
        return description != null ? description : "";
    }

    public String getIcon() {
        return icon != null ? icon : "";
    }

    public String getGroup() {
        return group != null ? group : "";
    }

    public Map<String, Object> getMetadata() {
        return metadata != null ? metadata : Map.of();
    }

    @Override
    public boolean equals(Object object) {
        if (this == object) {
            return true;
        }
        if (!(object instanceof OptionCatalogItem item)) {
            return false;
        }
        return Objects.equals(getValue(), item.getValue())
            && Objects.equals(getLabel(), item.getLabel())
            && Objects.equals(getDescription(), item.getDescription())
            && Objects.equals(getIcon(), item.getIcon())
            && Objects.equals(getGroup(), item.getGroup())
            && Objects.equals(stableMetadata(), item.stableMetadata());
    }

    @Override
    public int hashCode() {
        return Objects.hash(getValue(), getLabel(), getDescription(), getIcon(), getGroup(), stableMetadata());
    }

    private Map<String, Object> stableMetadata() {
        Map<String, Object> stable = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : getMetadata().entrySet()) {
            stable.put(entry.getKey(), stableValue(entry.getValue(), "components".equals(entry.getKey())));
        }
        return stable;
    }

    private Object stableValue(Object value, boolean components) {
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> stable = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                String key = FlowJson.text(entry.getKey());
                if (!components || !CUSTOM_DATA.equals(key)) {
                    stable.put(key, stableValue(entry.getValue(), false));
                }
            }
            return stable;
        }
        if (value instanceof Collection<?> collection) {
            return collection.stream().map(entry -> stableValue(entry, false)).toList();
        }
        if (value instanceof Object[] array) {
            return stableArray(array.length, index -> array[index]);
        }
        if (value instanceof boolean[] array) {
            return stableArray(array.length, index -> array[index]);
        }
        if (value instanceof byte[] array) {
            return stableArray(array.length, index -> array[index]);
        }
        if (value instanceof short[] array) {
            return stableArray(array.length, index -> array[index]);
        }
        if (value instanceof int[] array) {
            return stableArray(array.length, index -> array[index]);
        }
        if (value instanceof long[] array) {
            return stableArray(array.length, index -> array[index]);
        }
        if (value instanceof float[] array) {
            return stableArray(array.length, index -> array[index]);
        }
        if (value instanceof double[] array) {
            return stableArray(array.length, index -> array[index]);
        }
        if (value instanceof char[] array) {
            return stableArray(array.length, index -> array[index]);
        }
        if (value instanceof Number number) {
            try {
                String text = FlowJson.text(number);
                return new BigDecimal(text).stripTrailingZeros();
            } catch (NumberFormatException ignored) {
                return number.doubleValue();
            }
        }
        return value;
    }

    private List<Object> stableArray(int length, IntFunction<Object> values) {
        List<Object> stable = new ArrayList<>(length);
        for (int index = 0; index < length; index++) {
            stable.add(stableValue(values.apply(index), false));
        }
        return stable;
    }
}
