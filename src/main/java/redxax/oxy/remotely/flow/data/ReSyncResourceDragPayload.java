package redxax.oxy.remotely.flow.data;

public record ReSyncResourceDragPayload(String type, String id, String displayName, String path) {
    public static final String FOLDER = "folder";
    public static final String FLOW = "flow";
    public static final String FUNCTION = "function";
    public static final String COMMAND = "command";
    public static final String CUSTOM_CONTENT = "custom_content";
    public static final String GUI = "gui";
    public static final String SCOREBOARD = "scoreboard";
    public static final String TAB = "tab";
    public static final String CHAT_CHANNEL = "chat_channel";
    public static final String CHAT_FORMAT = "chat_format";
    public static final String CHAT_RULE = "chat_rule";
    public static final String PRIVATE_MESSAGE_FORMAT = "private_message_format";
    public static final String MENTION_STYLE = "mention_style";
    public static final String IGNORE_LIST = "ignore_list";
    public static final String MOTD_PROFILE = "motd_profile";
    public static final String MESSAGE_RULE = "message_rule";
    public static final String RECIPE_DEFINITION = "recipe_definition";
    public static final String TEXT_TEMPLATE = "text_template";
    public static final String WORLDGEN = "worldgen";
    public static final String WORLD = "world";

    public String key() {
        return ReSyncProjectMetadata.resourceKey(type, id);
    }

    public boolean isFolder() {
        return FOLDER.equals(type);
    }

    public boolean isGraphResource() {
        return FLOW.equals(type) || FUNCTION.equals(type) || COMMAND.equals(type) || CUSTOM_CONTENT.equals(type);
    }

    public boolean isLiteralAssignable() {
        return FLOW.equals(type) || FUNCTION.equals(type) || CUSTOM_CONTENT.equals(type) || GUI.equals(type) || SCOREBOARD.equals(type) || TAB.equals(type)
            || CHAT_CHANNEL.equals(type) || CHAT_FORMAT.equals(type) || CHAT_RULE.equals(type)
            || PRIVATE_MESSAGE_FORMAT.equals(type) || MENTION_STYLE.equals(type) || IGNORE_LIST.equals(type) || MOTD_PROFILE.equals(type) || MESSAGE_RULE.equals(type)
            || RECIPE_DEFINITION.equals(type) || TEXT_TEMPLATE.equals(type);
    }

    public ReSyncResourceDragPayload withPath(String newPath) {
        return new ReSyncResourceDragPayload(type, id, displayName, ReSyncProjectMetadata.normalizePath(newPath));
    }
}
