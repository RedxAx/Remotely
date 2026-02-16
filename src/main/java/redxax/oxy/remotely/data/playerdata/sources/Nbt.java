package redxax.oxy.remotely.data.playerdata.sources;

import java.io.DataInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class Nbt {
    private Nbt() {
    }

    static Tag read(DataInputStream dis) throws IOException {
        byte t = dis.readByte();
        if (t == 0) return null;
        String n = readString(dis);
        return readPayload(dis, t, n);
    }

    static Tag find(Tag tag, String name) {
        if (tag == null || tag.value == null) return null;
        if (tag.value instanceof List<?> list) {
            for (Object o : list) {
                if (o instanceof Tag t && name.equals(t.name)) return t;
            }
        }
        return null;
    }

    static Tag parseSnbt(String snbt) {
        if (snbt == null || snbt.isBlank()) return null;
        SnbtParser parser = new SnbtParser(snbt);
        Object value = parser.parseValue();
        if (value instanceof List<?> list) {
            return new Tag("root", list);
        }
        return new Tag("root", value);
    }

    static Map<String, Object> toMap(Tag tag) {
        if (tag == null || !(tag.value instanceof List<?> list)) return Collections.emptyMap();
        Map<String, Object> out = new LinkedHashMap<>();
        for (Object obj : list) {
            if (obj instanceof Tag t) {
                out.put(t.name, normalizeValue(t.value));
            }
        }
        return out;
    }

    static Tag readPayload(DataInputStream dis, byte t, String n) throws IOException {
        switch (t) {
            case 1 -> {
                return new Tag(n, dis.readByte());
            }
            case 2 -> {
                return new Tag(n, dis.readShort());
            }
            case 3 -> {
                return new Tag(n, dis.readInt());
            }
            case 4 -> {
                return new Tag(n, dis.readLong());
            }
            case 5 -> {
                return new Tag(n, dis.readFloat());
            }
            case 6 -> {
                return new Tag(n, dis.readDouble());
            }
            case 7 -> {
                int l7 = dis.readInt();
                byte[] data = new byte[l7];
                dis.readFully(data);
                return new Tag(n, data);
            }
            case 8 -> {
                return new Tag(n, readString(dis));
            }
            case 9 -> {
                byte lt = dis.readByte();
                int ll = dis.readInt();
                List<Object> list = new ArrayList<>(ll);
                for (int i = 0; i < ll; i++) {
                    Tag entry = readPayload(dis, lt, "");
                    list.add(lt == 10 ? entry : entry.value);
                }
                return new Tag(n, list);
            }
            case 10 -> {
                List<Tag> c = new ArrayList<>();
                while (true) {
                    byte tt = dis.readByte();
                    if (tt == 0) break;
                    c.add(readPayload(dis, tt, readString(dis)));
                }
                return new Tag(n, c);
            }
            case 11 -> {
                int l11 = dis.readInt();
                int[] arr = new int[l11];
                for (int i = 0; i < l11; i++) arr[i] = dis.readInt();
                return new Tag(n, arr);
            }
            case 12 -> {
                int l12 = dis.readInt();
                long[] arr = new long[l12];
                for (int i = 0; i < l12; i++) arr[i] = dis.readLong();
                return new Tag(n, arr);
            }
            default -> {
                return new Tag(n, null);
            }
        }
    }

    private static String readString(DataInputStream dis) throws IOException {
        int l = dis.readUnsignedShort();
        byte[] b = new byte[l];
        dis.readFully(b);
        return new String(b, StandardCharsets.UTF_8);
    }

    private static Object normalizeValue(Object value) {
        if (value instanceof Tag t) return toMap(t);
        if (value instanceof List<?> list) {
            List<Object> out = new ArrayList<>(list.size());
            for (Object obj : list) {
                out.add(normalizeValue(obj));
            }
            return out;
        }
        if (value instanceof byte[] arr) {
            List<Integer> out = new ArrayList<>(arr.length);
            for (byte b : arr) out.add((int) b);
            return out;
        }
        if (value instanceof int[] arr) {
            List<Integer> out = new ArrayList<>(arr.length);
            for (int b : arr) out.add(b);
            return out;
        }
        if (value instanceof long[] arr) {
            List<Long> out = new ArrayList<>(arr.length);
            for (long b : arr) out.add(b);
            return out;
        }
        return value;
    }

    private static final class SnbtParser {
        private final String input;
        private int index = 0;

        private SnbtParser(String input) {
            this.input = input;
        }

        private Object parseValue() {
            skipWhitespace();
            if (eof()) return null;
            char c = peek();
            if (c == '{') {
                return parseCompound();
            }
            if (c == '[') {
                return parseListOrArray();
            }
            if (c == '"' || c == '\'') {
                return parseQuotedString();
            }
            return parseLiteral();
        }

        private List<Tag> parseCompound() {
            expect('{');
            skipWhitespace();
            List<Tag> tags = new ArrayList<>();
            if (peek() == '}') {
                index++;
                return tags;
            }
            while (!eof()) {
                String key = parseKey();
                skipWhitespace();
                expect(':');
                Object value = parseValue();
                tags.add(new Tag(key, value));
                skipWhitespace();
                if (peek() == ',') {
                    index++;
                    skipWhitespace();
                    continue;
                }
                if (peek() == '}') {
                    index++;
                    break;
                }
            }
            return tags;
        }

        private Object parseListOrArray() {
            expect('[');
            skipWhitespace();
            if (peek() == ']') {
                index++;
                return Collections.emptyList();
            }
            if (isArrayType(peek())) {
                char type = peek();
                int next = index + 1;
                if (next < input.length() && input.charAt(next) == ';') {
                    index += 2;
                    return parseArray(type);
                }
            }
            List<Object> list = new ArrayList<>();
            while (!eof()) {
                Object value = parseValue();
                if (value instanceof List<?> compound) {
                    list.add(new Tag("", compound));
                } else {
                    list.add(value);
                }
                skipWhitespace();
                if (peek() == ',') {
                    index++;
                    skipWhitespace();
                    continue;
                }
                if (peek() == ']') {
                    index++;
                    break;
                }
            }
            return list;
        }

        private Object parseArray(char type) {
            List<Long> values = new ArrayList<>();
            skipWhitespace();
            while (!eof()) {
                String token = readToken();
                if (!token.isEmpty()) {
                    values.add(parseLongToken(token));
                }
                skipWhitespace();
                if (peek() == ',') {
                    index++;
                    skipWhitespace();
                    continue;
                }
                if (peek() == ']') {
                    index++;
                    break;
                }
            }
            if (type == 'B' || type == 'b') {
                byte[] arr = new byte[values.size()];
                for (int i = 0; i < values.size(); i++) arr[i] = (byte) values.get(i).longValue();
                return arr;
            }
            if (type == 'I' || type == 'i') {
                int[] arr = new int[values.size()];
                for (int i = 0; i < values.size(); i++) arr[i] = values.get(i).intValue();
                return arr;
            }
            long[] arr = new long[values.size()];
            for (int i = 0; i < values.size(); i++) arr[i] = values.get(i);
            return arr;
        }

        private String parseKey() {
            skipWhitespace();
            if (peek() == '"' || peek() == '\'') {
                return parseQuotedString();
            }
            StringBuilder sb = new StringBuilder();
            while (!eof()) {
                char c = peek();
                if (c == ':' || c == ',' || c == '}' || Character.isWhitespace(c)) break;
                sb.append(c);
                index++;
            }
            return sb.toString();
        }

        private String parseQuotedString() {
            char quote = next();
            StringBuilder sb = new StringBuilder();
            while (!eof()) {
                char c = next();
                if (c == quote) break;
                if (c == '\\' && !eof()) {
                    char n = next();
                    switch (n) {
                        case '"' -> sb.append('"');
                        case '\'' -> sb.append('\'');
                        case '\\' -> sb.append('\\');
                        case 'n' -> sb.append('\n');
                        case 'r' -> sb.append('\r');
                        case 't' -> sb.append('\t');
                        case 'b' -> sb.append('\b');
                        case 'f' -> sb.append('\f');
                        case 'u' -> sb.append(parseUnicode());
                        default -> sb.append(n);
                    }
                } else {
                    sb.append(c);
                }
            }
            return sb.toString();
        }

        private char parseUnicode() {
            int end = Math.min(index + 4, input.length());
            String hex = input.substring(index, end);
            index = end;
            try {
                return (char) Integer.parseInt(hex, 16);
            } catch (Exception e) {
                return 0;
            }
        }

        private Object parseLiteral() {
            String token = readToken();
            if (token.isEmpty()) return "";
            if ("true".equalsIgnoreCase(token)) return true;
            if ("false".equalsIgnoreCase(token)) return false;
            char last = token.charAt(token.length() - 1);
            try {
                if (last == 'b' || last == 'B') {
                    return Byte.parseByte(trimSuffix(token));
                }
                if (last == 's' || last == 'S') {
                    return Short.parseShort(trimSuffix(token));
                }
                if (last == 'l' || last == 'L') {
                    return Long.parseLong(trimSuffix(token));
                }
                if (last == 'f' || last == 'F') {
                    return Float.parseFloat(trimSuffix(token));
                }
                if (last == 'd' || last == 'D') {
                    return Double.parseDouble(trimSuffix(token));
                }
                if (token.contains(".") || token.contains("e") || token.contains("E")) {
                    return Double.parseDouble(token);
                }
                long l = Long.parseLong(token);
                if (l >= Integer.MIN_VALUE && l <= Integer.MAX_VALUE) return (int) l;
                return l;
            } catch (Exception ignored) {
                return token;
            }
        }

        private String readToken() {
            skipWhitespace();
            StringBuilder sb = new StringBuilder();
            while (!eof()) {
                char c = peek();
                if (c == ',' || c == ']' || c == '}' || Character.isWhitespace(c)) break;
                sb.append(c);
                index++;
            }
            return sb.toString();
        }

        private long parseLongToken(String token) {
            if (token == null || token.isEmpty()) return 0L;
            String t = token;
            char last = t.charAt(t.length() - 1);
            if (last == 'b' || last == 'B' || last == 's' || last == 'S' || last == 'l' || last == 'L') {
                t = trimSuffix(t);
            }
            try {
                return Long.parseLong(t);
            } catch (Exception e) {
                try {
                    return (long) Double.parseDouble(t);
                } catch (Exception ignored) {
                    return 0L;
                }
            }
        }

        private String trimSuffix(String token) {
            return token.substring(0, token.length() - 1);
        }

        private boolean isArrayType(char c) {
            return c == 'B' || c == 'b' || c == 'I' || c == 'i' || c == 'L' || c == 'l';
        }

        private void skipWhitespace() {
            while (!eof() && Character.isWhitespace(peek())) index++;
        }

        private void expect(char c) {
            if (peek() == c) {
                index++;
            }
        }

        private char next() {
            if (eof()) return 0;
            return input.charAt(index++);
        }

        private char peek() {
            if (eof()) return 0;
            return input.charAt(index);
        }

        private boolean eof() {
            return index >= input.length();
        }
    }

    static final class Tag {
        final String name;
        final Object value;

        Tag(String n, Object v) {
            name = n;
            value = v;
        }
    }
}
