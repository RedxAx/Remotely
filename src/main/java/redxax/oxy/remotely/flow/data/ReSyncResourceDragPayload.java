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
    public static final String CHAT = "chat";
    public static final String MOTD_PROFILE = "motd_profile";
    public static final String MESSAGE_RULE = "message_rule";
    public static final String RECIPE_DEFINITION = "recipe_definition";
    public static final String TEXT_TEMPLATE = "text_template";
    public static final String ADVANCEMENT_TREE = "advancement_tree";
    public static final String DIALOG = "dialog";
    public static final String VILLAGE_PROFILE = "village_profile";
    public static final String NPC_DEFINITION = "npc_definition";
    public static final String LOOT_TABLE = "loot_table";
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
        return FLOW.equals(type) || FUNCTION.equals(type) || CUSTOM_CONTENT.equals(type) || GUI.equals(type) || SCOREBOARD.equals(type) || TAB.equals(type) || CHAT.equals(type)
            || MOTD_PROFILE.equals(type) || MESSAGE_RULE.equals(type)
            || RECIPE_DEFINITION.equals(type) || TEXT_TEMPLATE.equals(type) || ADVANCEMENT_TREE.equals(type) || DIALOG.equals(type)
            || VILLAGE_PROFILE.equals(type) || NPC_DEFINITION.equals(type) || LOOT_TABLE.equals(type);
    }

    public ReSyncResourceDragPayload withPath(String newPath) {
        return new ReSyncResourceDragPayload(type, id, displayName, ReSyncProjectMetadata.normalizePath(newPath));
    }
}
