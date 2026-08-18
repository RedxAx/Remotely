package redxax.oxy.remotely.data.integrations.luckperms;

import java.util.List;
import java.util.Map;
import java.util.Objects;

public class LuckPermsDTOs {

    public static class PermissionHolder {
        public String uniqueId;
        public String name;
        public String username;
        public List<Node> nodes;
        public Metadata metadata;
        public String displayName;
        public Integer weight;
    }

    public static class User extends PermissionHolder {}
    public static class Group extends PermissionHolder {}

    public static class Node {
        public String key;
        public String type;
        public Boolean value;
        public List<Context> context;
        public Long expiry;

        public Node() {}
        public Node(String key, boolean value) {
            this.key = key;
            this.value = value;
            this.type = "permission";
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof Node node)) return false;
            return Objects.equals(key, node.key) && Objects.equals(value, node.value) && Objects.equals(context, node.context);
        }

        @Override
        public int hashCode() {
            return Objects.hash(key, value, context);
        }
    }

    public static class Context {
        public String key;
        public String value;
        public Context() {}
        public Context(String key, String value) {
            this.key = key;
            this.value = value;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof Context context)) return false;
            return Objects.equals(key, context.key) && Objects.equals(value, context.value);
        }

        @Override
        public int hashCode() {
            return Objects.hash(key, value);
        }
    }

    public static class Metadata {
        public Map<String, String> meta;
        public String prefix;
        public String suffix;
        public String primaryGroup;
    }

    public static class SearchResult {
        public String uniqueId;
        public String name;
    }

    public static class UserSearchResult {
        public String uniqueId;
        public List<Node> results;
    }

    public static class GroupSearchResult {
        public String name;
        public List<Node> results;
    }

    public static class Track {
        public String name;
        public List<String> groups;
    }

    public static class Health {
        public boolean health;
        public Map<String, Object> details;
    }
}
