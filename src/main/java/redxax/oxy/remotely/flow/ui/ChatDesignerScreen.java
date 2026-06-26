package redxax.oxy.remotely.flow.ui;

import com.google.gson.JsonObject;
import redxax.oxy.remotely.flow.data.ReSyncResourceDragPayload;
import redxax.oxy.remotely.flow.ui.studio.StudioScreen;
import restudio.rescreen.platform.IDrawContext;

import java.util.ArrayList;
import java.util.List;

public class ChatDesignerScreen extends FocusedJsonResourceDesignerScreen {
    public ChatDesignerScreen(StudioScreen owner, String resourceId, JsonObject resource, String serverId, Object parent) {
        super(owner, ReSyncResourceDragPayload.CHAT, resourceId, resource, serverId, parent);
    }

    @Override
    protected boolean centeredTextPreview() {
        return true;
    }

    @Override
    protected void renderResourcePreview(IDrawContext context, int previewX, int previewY, int previewWidth, int previewHeight, int mouseX, int mouseY, int text, int muted) {
        renderChatRealPreview(context, previewX, previewY, previewWidth, previewHeight, text, muted);
    }

    @Override
    protected List<String> editorFields() {
        return chatFields();
    }

    @Override
    protected List<ResourcePanelSection> editorSections(List<String> fields) {
        return appendRemainingSections(List.of(
            new ResourcePanelSection("Channel", fields.stream().filter(field -> field.equals("displayName") || field.startsWith("channel.")).toList()),
            new ResourcePanelSection("Format", fields.stream().filter(field -> field.startsWith("format.")).toList()),
            new ResourcePanelSection("Rule", fields.stream().filter(field -> field.startsWith("rule.")).toList()),
            new ResourcePanelSection("Private Messages", fields.stream().filter(field -> field.startsWith("privateMessages.")).toList()),
            new ResourcePanelSection("Mentions", fields.stream().filter(field -> field.startsWith("mention.")).toList()),
            new ResourcePanelSection("Ignore", fields.stream().filter(field -> field.equals("ignorePlayersText")).toList())
        ), fields);
    }

    @Override
    protected List<String> actionOptions(String field) {
        return List.of("block", "replace", "flow", "channel");
    }

    @Override
    protected List<String> customSelectorOptions(String field) {
        return "rule.action".equals(field) ? actionOptions(field) : null;
    }

    @Override
    protected boolean customDropdownField(String field) {
        return "rule.action".equals(field);
    }

    @Override
    protected boolean customCodeField(String field) {
        return List.of(
            "format.template",
            "rule.replacement",
            "privateMessages.sender",
            "privateMessages.receiver",
            "privateMessages.spy",
            "mention.template",
            "ignorePlayersText"
        ).contains(field);
    }

    @Override
    protected int customCodeFieldHeight(String field) {
        return customCodeField(field) ? dynamicCodeFieldHeight(field) : -1;
    }

    @Override
    protected String resourceSummary() {
        return firstFilled(jsonText("displayName"), jsonPathText("channel.prefix"), "Chat");
    }

    @Override
    protected String resourceDisplayName() {
        return "Chat";
    }

    @Override
    protected String mentionPreviewTemplate() {
        return jsonPathText("mention.template");
    }

    protected void renderChatRealPreview(IDrawContext context, int previewX, int previewY, int previewWidth, int previewHeight, int text, int muted) {
        int centerX = previewX + previewWidth / 2;
        int chatY = previewY + Math.max(18, previewHeight / 2 - 48);
        String prefix = jsonPathText("channel.prefix");
        String template = jsonPathText("format.template");
        if (template.isBlank()) {
            template = "{prefix}{sender}: {message}";
        }
        String line = template.replace("{prefix}", prefix).replace("{sender}", "Steve").replace("{receiver}", "Alex").replace("{message}", "Hello @Alex");
        String firstLine = applyMentionPreview(line);
        String secondLine = "<gray>Alex: Looks good";
        String thirdLine = "<yellow>@Steve</yellow> synced";
        drawFormattedLine(context, firstLine, centeredTextX(firstLine, centerX), chatY + 16, text, true);
        drawFormattedLine(context, secondLine, centeredTextX(secondLine, centerX), chatY + 36, muted, true);
        drawFormattedLine(context, thirdLine, centeredTextX(thirdLine, centerX), chatY + 56, text, true);
    }

    protected List<String> chatFields() {
        List<String> fields = new ArrayList<>(List.of(
            "displayName",
            "channel.prefix",
            "format.template",
            "channel.range",
            "channel.speakPermission",
            "channel.readPermission",
            "channel.allowMiniMessage",
            "channel.miniMessagePermission",
            "rule.contains",
            "rule.action",
            "rule.replacement",
            "rule.channel",
            "rule.flowId"
        ));
        fields.addAll(List.of(
            "privateMessages.sender",
            "privateMessages.receiver",
            "privateMessages.spy",
            "privateMessages.privateMessageFlow",
            "mention.template",
            "mention.mentionFlow",
            "ignorePlayersText"
        ));
        return fields;
    }
}
