package redxax.oxy.remotely.data.playerdata.sources;

import net.querz.nbt.io.NBTDeserializer;
import net.querz.nbt.io.NamedTag;
import net.querz.nbt.io.SNBTUtil;

import java.io.DataInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class Nbt {
    private Nbt() {
    }

    static Nbt.Tag read(DataInputStream input) throws IOException {
        NamedTag named = new NBTDeserializer(false).fromStream(input);
        return named == null ? null : QuerzTagConverter.convert(named.getName(), named.getTag());
    }

    static Nbt.Tag find(Nbt.Tag tag, String name) {
        if (tag == null || tag.value == null || !(tag.value instanceof List<?> list)) return null;
        for (Object value : list) {
            if (value instanceof Nbt.Tag child && name.equals(child.name)) return child;
        }
        return null;
    }

    static Nbt.Tag parseSnbt(String snbt) {
        if (snbt == null || snbt.isBlank()) return null;
        try {
            return QuerzTagConverter.convert("root", SNBTUtil.fromSNBT(snbt, true));
        } catch (IOException exception) {
            if (snbt.indexOf('\'') < 0) return null;
            try {
                return QuerzTagConverter.convert("root", SNBTUtil.fromSNBT(normalizeSingleQuotedStrings(snbt), true));
            } catch (IOException ignored) {
                return null;
            }
        }
    }

    private static String normalizeSingleQuotedStrings(String value) {
        StringBuilder normalized = new StringBuilder(value.length());
        boolean singleQuoted = false;
        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            if (!singleQuoted) {
                if (character == '\'') {
                    normalized.append('"');
                    singleQuoted = true;
                } else {
                    normalized.append(character);
                }
                continue;
            }
            if (character == '\'' ) {
                normalized.append('"');
                singleQuoted = false;
            } else if (character == '"') {
                normalized.append('\\').append('"');
            } else if (character == '\\' && index + 1 < value.length() && value.charAt(index + 1) == '\'') {
                normalized.append('\'');
                index++;
            } else {
                normalized.append(character);
            }
        }
        return normalized.toString();
    }

    static Map<String, Object> toMap(Nbt.Tag tag) {
        if (tag == null || !(tag.value instanceof List<?> list)) return Collections.emptyMap();
        Map<String, Object> result = new LinkedHashMap<>();
        for (Object value : list) {
            if (value instanceof Nbt.Tag child) result.put(child.name, normalizeValue(child.value));
        }
        return result;
    }

    private static Object normalizeValue(Object value) {
        if (value instanceof Nbt.Tag tag) return toMap(tag);
        if (value instanceof List<?> list) {
            List<Object> result = new ArrayList<>(list.size());
            for (Object entry : list) result.add(normalizeValue(entry));
            return result;
        }
        if (value instanceof byte[] array) {
            List<Integer> result = new ArrayList<>(array.length);
            for (byte entry : array) result.add((int) entry);
            return result;
        }
        if (value instanceof int[] array) {
            List<Integer> result = new ArrayList<>(array.length);
            for (int entry : array) result.add(entry);
            return result;
        }
        if (value instanceof long[] array) {
            List<Long> result = new ArrayList<>(array.length);
            for (long entry : array) result.add(entry);
            return result;
        }
        return value;
    }

    static final class Tag {
        final String name;
        final Object value;

        Tag(String name, Object value) {
            this.name = name;
            this.value = value;
        }
    }
}
