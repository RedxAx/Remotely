package redxax.oxy.remotely.flow.ui.studio;

import com.google.gson.JsonObject;
import redxax.oxy.remotely.data.flow.FlowManager;
import redxax.oxy.remotely.data.flow.ReSyncResourceType;
import redxax.oxy.remotely.flow.data.FlowGraph;
import redxax.oxy.remotely.flow.data.ReSyncProjectMetadata;
import redxax.oxy.remotely.flow.data.ReSyncResourceDragPayload;
import redxax.oxy.remotely.worldgen.WorldGenManager;
import redxax.oxy.remotely.worldgen.data.WorldGenProject;
import restudio.rescreen.theme.ThemeManager;
import restudio.rescreen.ui.core.Screen;
import restudio.rescreen.ui.widgets.AnimatedButton;
import restudio.rescreen.ui.widgets.PopupWidget;
import restudio.rescreen.ui.widgets.TextInputWidget;
import restudio.rescreen.util.Notification;

import java.util.function.Consumer;

public final class ReSyncResourceCreator {
    private ReSyncResourceCreator() {
    }

    public record Result(String type, String id, Object resource) {
    }

    public static void showCreatePopup(Screen screen, String serverId, String type, String folder, String template, Consumer<Result> onCreated) {
        if (screen == null) {
            return;
        }
        PopupWidget.Builder builder = new PopupWidget.Builder(createPopupTitle(type)).setResizable(false);
        TextInputWidget idInput = new TextInputWidget.Builder()
            .placeholder(createIdPlaceholder(type))
            .size(220, 22)
            .build();
        builder.addRow(ReSyncResourceDragPayload.FOLDER.equals(type) ? "Name" : "ID", true, 22, idInput);
        PopupWidget[] popupRef = new PopupWidget[1];
        AnimatedButton createButton = new AnimatedButton.Builder()
            .label("Create")
            .accentType(ThemeManager.getAccent("nice"))
            .onClick(() -> {
                String id = idInput.getText() != null ? idInput.getText().trim() : "";
                Result result = create(serverId, type, id, folder, template);
                if (result == null) {
                    return;
                }
                if (onCreated != null) {
                    onCreated.accept(result);
                }
                if (popupRef[0] != null) {
                    popupRef[0].hide();
                }
            })
            .build();
        builder.addRow("", true, 20, createButton);
        popupRef[0] = builder.build();
        screen.addDrawableChild(popupRef[0]);
        popupRef[0].show();
    }

    public static Result create(String serverId, String type, String id, String folder, String template) {
        FlowManager manager = FlowManager.getInstance();
        if (manager == null || serverId == null) {
            new Notification("Error", "No server connection", Notification.Type.ERROR);
            return null;
        }
        if (ReSyncResourceDragPayload.FOLDER.equals(type)) {
            if (id == null || id.isBlank()) {
                new Notification("Explorer", "Invalid Name", Notification.Type.ERROR);
                return null;
            }
            manager.createProjectFolder(serverId, normalizedFolder(folder), id);
            return new Result(type, id, null);
        }
        if (id == null || !id.matches("^[a-zA-Z0-9_]+$")) {
            new Notification("Error", "Invalid ID. Alphanumeric only.", Notification.Type.ERROR);
            return null;
        }
        if (exists(manager, serverId, type, id)) {
            new Notification("Error", resourceTypeName(type) + " ID already exists", Notification.Type.ERROR);
            return null;
        }
        String targetFolder = normalizedFolder(folder);
        Object resource = createResource(manager, serverId, type, id, targetFolder, template);
        if (resource == null && !ReSyncResourceDragPayload.FOLDER.equals(type)) {
            return null;
        }
        ReSyncProjectMetadata metadata = manager.getProjectMetadata(serverId);
        ReSyncProjectMetadata.ResourceEntry entry = metadata.ensureResource(type, id, id, targetFolder);
        entry.setPath(targetFolder);
        manager.saveProjectMetadata(serverId, metadata);
        return new Result(type, id, resource);
    }

    private static Object createResource(FlowManager manager, String serverId, String type, String id, String folder, String template) {
        return switch (type) {
            case ReSyncResourceDragPayload.FLOW -> manager.createFlow(serverId, id, false, template != null ? template : "Blank");
            case ReSyncResourceDragPayload.FUNCTION -> manager.createFlow(serverId, id, true);
            case ReSyncResourceDragPayload.COMMAND -> {
                FlowGraph graph = manager.createFlow(serverId, id, false, "Command");
                manager.setCommandBinding(serverId, id, id);
                yield graph;
            }
            case ReSyncResourceDragPayload.GUI -> manager.createGui(serverId, id);
            case ReSyncResourceDragPayload.SCOREBOARD -> manager.createScoreboard(serverId, id);
            case ReSyncResourceDragPayload.TAB -> manager.createTab(serverId, id);
            case ReSyncResourceDragPayload.CHAT_CHANNEL, ReSyncResourceDragPayload.CHAT_FORMAT,
                 ReSyncResourceDragPayload.CHAT_RULE, ReSyncResourceDragPayload.PRIVATE_MESSAGE_FORMAT, ReSyncResourceDragPayload.MENTION_STYLE,
                 ReSyncResourceDragPayload.IGNORE_LIST, ReSyncResourceDragPayload.MOTD_PROFILE, ReSyncResourceDragPayload.MESSAGE_RULE,
                 ReSyncResourceDragPayload.RECIPE_DEFINITION, ReSyncResourceDragPayload.TEXT_TEMPLATE, ReSyncResourceDragPayload.ADVANCEMENT_TREE,
                 ReSyncResourceDragPayload.DIALOG -> {
                ReSyncResourceType resourceType = ReSyncResourceType.byTypeId(type);
                JsonObject resource = resourceType != null ? manager.createJsonResource(serverId, resourceType, id, folder) : null;
                if (resource != null && resourceType != null) {
                    manager.saveJsonResource(serverId, resourceType, resource);
                }
                yield resource;
            }
            case ReSyncResourceDragPayload.WORLDGEN -> {
                WorldGenProject project = WorldGenManager.getInstance().createProjectTemplate("Continental", id);
                WorldGenManager.getInstance().saveWorldGen(serverId, project);
                WorldGenManager.getInstance().ensureLocalDefinitions(serverId);
                yield project;
            }
            default -> null;
        };
    }

    public static boolean exists(FlowManager manager, String serverId, String type, String id) {
        if (manager == null || serverId == null || type == null || id == null) {
            return false;
        }
        if (!ReSyncResourceDragPayload.CUSTOM_CONTENT.equals(type) && manager.getProjectMetadata(serverId).findResource(type, id) != null) {
            return true;
        }
        return switch (type) {
            case ReSyncResourceDragPayload.FLOW, ReSyncResourceDragPayload.FUNCTION -> manager.getFlowsForServer(serverId).containsKey(id);
            case ReSyncResourceDragPayload.CUSTOM_CONTENT -> manager.getCustomContentForServer(serverId).containsKey(id);
            case ReSyncResourceDragPayload.COMMAND -> manager.getCommandBinding(serverId, id) != null || manager.getFlowsForServer(serverId).containsKey(id);
            case ReSyncResourceDragPayload.GUI -> manager.getGuisForServer(serverId).containsKey(id);
            case ReSyncResourceDragPayload.SCOREBOARD -> manager.getScoreboardsForServer(serverId).containsKey(id);
            case ReSyncResourceDragPayload.TAB -> manager.getTabsForServer(serverId).containsKey(id);
            case ReSyncResourceDragPayload.CHAT_CHANNEL, ReSyncResourceDragPayload.CHAT_FORMAT,
                 ReSyncResourceDragPayload.CHAT_RULE, ReSyncResourceDragPayload.PRIVATE_MESSAGE_FORMAT, ReSyncResourceDragPayload.MENTION_STYLE,
                 ReSyncResourceDragPayload.IGNORE_LIST, ReSyncResourceDragPayload.MOTD_PROFILE, ReSyncResourceDragPayload.MESSAGE_RULE,
                 ReSyncResourceDragPayload.RECIPE_DEFINITION, ReSyncResourceDragPayload.TEXT_TEMPLATE, ReSyncResourceDragPayload.ADVANCEMENT_TREE,
                 ReSyncResourceDragPayload.DIALOG -> {
                ReSyncResourceType resourceType = ReSyncResourceType.byTypeId(type);
                yield resourceType != null && manager.getJsonResourcesForServer(serverId, resourceType).containsKey(id);
            }
            default -> false;
        };
    }

    public static String createPopupTitle(String type) {
        return "Create " + resourceTypeName(type);
    }

    public static String createIdPlaceholder(String type) {
        return switch (type) {
            case ReSyncResourceDragPayload.FOLDER -> "Folder Name";
            case ReSyncResourceDragPayload.FUNCTION -> "Function ID";
            case ReSyncResourceDragPayload.COMMAND -> "Command ID";
            case ReSyncResourceDragPayload.CUSTOM_CONTENT -> "Content ID";
            case ReSyncResourceDragPayload.GUI -> "GUI ID";
            case ReSyncResourceDragPayload.SCOREBOARD -> "Scoreboard ID";
            case ReSyncResourceDragPayload.TAB -> "Tab ID";
            case ReSyncResourceDragPayload.CHAT_CHANNEL -> "Channel ID";
            case ReSyncResourceDragPayload.CHAT_FORMAT -> "Format ID";
            case ReSyncResourceDragPayload.CHAT_RULE -> "Rule ID";
            case ReSyncResourceDragPayload.PRIVATE_MESSAGE_FORMAT -> "PM Format ID";
            case ReSyncResourceDragPayload.MENTION_STYLE -> "Mention ID";
            case ReSyncResourceDragPayload.IGNORE_LIST -> "Ignore List ID";
            case ReSyncResourceDragPayload.MOTD_PROFILE -> "MOTD ID";
            case ReSyncResourceDragPayload.MESSAGE_RULE -> "Message Rule ID";
            case ReSyncResourceDragPayload.RECIPE_DEFINITION -> "Recipe ID";
            case ReSyncResourceDragPayload.TEXT_TEMPLATE -> "Text ID";
            case ReSyncResourceDragPayload.ADVANCEMENT_TREE -> "Advancement ID";
            case ReSyncResourceDragPayload.DIALOG -> "Dialog ID";
            case ReSyncResourceDragPayload.WORLDGEN -> "Project ID";
            default -> "Flow ID";
        };
    }

    public static String resourceTypeName(String type) {
        return switch (type) {
            case ReSyncResourceDragPayload.FOLDER -> "Folder";
            case ReSyncResourceDragPayload.FUNCTION -> "Function";
            case ReSyncResourceDragPayload.COMMAND -> "Command";
            case ReSyncResourceDragPayload.CUSTOM_CONTENT -> "Content";
            case ReSyncResourceDragPayload.GUI -> "GUI";
            case ReSyncResourceDragPayload.SCOREBOARD -> "Scoreboard";
            case ReSyncResourceDragPayload.TAB -> "Tab";
            case ReSyncResourceDragPayload.CHAT_CHANNEL -> "Chat";
            case ReSyncResourceDragPayload.CHAT_FORMAT -> "Chat Format";
            case ReSyncResourceDragPayload.CHAT_RULE -> "Chat Rule";
            case ReSyncResourceDragPayload.PRIVATE_MESSAGE_FORMAT -> "PM Format";
            case ReSyncResourceDragPayload.MENTION_STYLE -> "Mention";
            case ReSyncResourceDragPayload.IGNORE_LIST -> "Ignore List";
            case ReSyncResourceDragPayload.MOTD_PROFILE -> "MOTD";
            case ReSyncResourceDragPayload.MESSAGE_RULE -> "Message Rule";
            case ReSyncResourceDragPayload.RECIPE_DEFINITION -> "Recipe";
            case ReSyncResourceDragPayload.TEXT_TEMPLATE -> "Text";
            case ReSyncResourceDragPayload.ADVANCEMENT_TREE -> "Advancement";
            case ReSyncResourceDragPayload.DIALOG -> "Dialog";
            case ReSyncResourceDragPayload.WORLDGEN -> "WorldGen";
            default -> "Flow";
        };
    }

    private static String normalizedFolder(String folder) {
        return ReSyncProjectMetadata.normalizePath(folder == null ? "" : folder);
    }
}
