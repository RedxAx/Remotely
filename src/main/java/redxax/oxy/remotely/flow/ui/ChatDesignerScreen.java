package redxax.oxy.remotely.flow.ui;

import com.google.gson.JsonObject;
import redxax.oxy.remotely.flow.data.ReSyncResourceDragPayload;
import redxax.oxy.remotely.flow.ui.studio.StudioScreen;
import restudio.rescreen.ui.widgets.AnimatedWidget;

import java.util.ArrayList;
import java.util.List;

public class ChatDesignerScreen extends FocusedJsonResourceDesignerScreen {
    public static final List<String> RESOURCE_TYPES = List.of(
        ReSyncResourceDragPayload.CHAT_CHANNEL,
        ReSyncResourceDragPayload.CHAT_FORMAT,
        ReSyncResourceDragPayload.CHAT_RULE,
        ReSyncResourceDragPayload.PRIVATE_MESSAGE_FORMAT,
        ReSyncResourceDragPayload.MENTION_STYLE,
        ReSyncResourceDragPayload.IGNORE_LIST
    );
    private final List<AnimatedWidget> chatHeaderButtons = new ArrayList<>();

    public ChatDesignerScreen(StudioScreen owner, String resourceType, String resourceId, JsonObject resource, String serverId, Object parent) {
        super(owner, resourceType, resourceId, resource, serverId, parent);
        chatHeaderButtons.addAll(super.headerButtons());
        for (String ownedType : RESOURCE_TYPES) {
            chatHeaderButtons.add(headerButton(iconForType(ownedType), labelForType(ownedType), () -> openChatResource(ownedType)));
        }
    }

    public static boolean owns(String resourceType) {
        return RESOURCE_TYPES.contains(resourceType);
    }

    @Override
    public List<AnimatedWidget> headerButtons() {
        return chatHeaderButtons;
    }

    private void openChatResource(String resourceType) {
        if (host != null) {
            host.openWorkspaceResource(resourceType, id);
        }
    }

    private static String labelForType(String resourceType) {
        return switch (resourceType) {
            case ReSyncResourceDragPayload.CHAT_CHANNEL -> "Channel";
            case ReSyncResourceDragPayload.CHAT_FORMAT -> "Format";
            case ReSyncResourceDragPayload.CHAT_RULE -> "Rule";
            case ReSyncResourceDragPayload.PRIVATE_MESSAGE_FORMAT -> "Private";
            case ReSyncResourceDragPayload.MENTION_STYLE -> "Mention";
            case ReSyncResourceDragPayload.IGNORE_LIST -> "Ignore";
            default -> "Chat";
        };
    }

    protected static String iconForType(String resourceType) {
        return ReSyncResourceDragPayload.IGNORE_LIST.equals(resourceType) ? "delete.png" : "chat.png";
    }
}
