package redxax.oxy.remotely.flow.ui;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import redxax.oxy.remotely.flow.data.ReSyncResourceDragPayload;
import redxax.oxy.remotely.flow.ui.studio.StudioScreen;
import restudio.rescreen.platform.IDrawContext;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class TextTemplateDesignerScreen extends FocusedJsonResourceDesignerScreen {
    public TextTemplateDesignerScreen(StudioScreen owner, String resourceId, JsonObject resource, String serverId, Object parent) {
        super(owner, ReSyncResourceDragPayload.TEXT_TEMPLATE, resourceId, resource, serverId, parent);
    }

    @Override
    protected void renderResourcePreview(IDrawContext context, int previewX, int previewY, int previewWidth, int previewHeight, int mouseX, int mouseY, int text, int muted) {
        renderTextRealPreview(context, previewX, previewY, previewWidth, previewHeight, text, muted);
    }

    @Override
    protected List<String> editorFields() {
        return textTemplateFields();
    }

    @Override
    protected boolean customDropdownField(String field) {
        return "mode".equals(field);
    }

    @Override
    protected boolean customRebuildOnSelection(String field) {
        return "mode".equals(field);
    }

    @Override
    protected List<String> modeOptions() {
        return List.of("frames", "typing", "scroll", "bounce", "blink", "pulse", "rainbow", "wave", "wipe", "sparkle");
    }

    @Override
    protected List<String> customSelectorOptions(String field) {
        return "mode".equals(field) ? modeOptions() : null;
    }

    @Override
    protected boolean customCodeField(String field) {
        return List.of("text", "framesText", "colorsText").contains(field);
    }

    @Override
    protected int customCodeFieldHeight(String field) {
        return customCodeField(field) ? dynamicCodeFieldHeight(field) : -1;
    }

    @Override
    protected String textFieldDescription() {
        return "Base text for text-template modes.\nSupports MiniMessage tags.";
    }

    @Override
    protected String modeFieldDescription() {
        return "Text-template animation mode.\nframes: cycle each frame.\ntyping: reveal text over time.\nscroll: moving text window.\nbounce: moving text window that reverses at edges.\nblink: alternate visible and blank.\npulse, rainbow, wave, wipe, sparkle: cosmetic animated text.";
    }

    @Override
    protected boolean remountPanelOnFieldReload() {
        return true;
    }

    @Override
    protected boolean handleSpecialJsonTextWrite(String field, String value) {
        if (!"text".equals(field)) {
            return false;
        }
        putTemplateText(value);
        return true;
    }

    @Override
    protected void sanitizeLegacyResourceFields() {
        resource.remove("colorsText");
        if (jsonText("mode").isBlank()) {
            resource.addProperty("mode", "frames");
        }
        JsonArray frames = resource.has("frames") && resource.get("frames").isJsonArray() ? resource.getAsJsonArray("frames") : new JsonArray();
        if (frames.isEmpty()) {
            String text = jsonText("text");
            if (text.isBlank()) {
                text = id;
            }
            if (text != null && !text.isBlank()) {
                frames.add(text);
                resource.add("frames", frames);
            }
        }
    }

    @Override
    protected String resourceSummary() {
        return firstFilled(jsonText("text"), "Template");
    }

    @Override
    protected String resourceDisplayName() {
        return "Text";
    }

    protected void renderTextRealPreview(IDrawContext context, int previewX, int previewY, int previewWidth, int previewHeight, int text, int muted) {
        int centerY = previewY + previewHeight / 2;
        List<String> lines = previewTextLines();
        int startY = centerY - Math.min(3, lines.size()) * 12;
        for (int i = 0; i < Math.min(5, lines.size()); i++) {
            drawFormattedLine(context, lines.get(i), previewX + 28, startY + i * 24, i == 0 ? text : muted, true);
        }
    }

    protected List<String> textTemplateFields() {
        List<String> fields = new ArrayList<>(List.of("mode"));
        switch (jsonText("mode").toLowerCase(Locale.ROOT)) {
            case "typing" -> {
                fields.add("text");
                fields.add("frameMillis");
                fields.add("visibleCharacters");
            }
            case "scroll", "scrolling" -> {
                fields.add("text");
                fields.add("frameMillis");
                fields.add("width");
            }
            case "bounce" -> {
                fields.add("text");
                fields.add("frameMillis");
                fields.add("width");
            }
            case "blink" -> {
                fields.add("text");
                fields.add("frameMillis");
            }
            case "pulse", "sparkle" -> {
                fields.add("text");
                fields.add("frameMillis");
                fields.add("color");
                fields.add("secondaryColor");
            }
            case "rainbow" -> {
                fields.add("text");
                fields.add("frameMillis");
            }
            case "wave" -> {
                fields.add("text");
                fields.add("frameMillis");
                fields.add("colorsText");
            }
            case "wipe" -> {
                fields.add("text");
                fields.add("frameMillis");
                fields.add("visibleCharacters");
            }
            default -> {
                fields.add("framesText");
                fields.add("frameMillis");
            }
        }
        return fields;
    }

    protected List<String> previewTextLines() {
        List<String> lines = new ArrayList<>();
        JsonArray frames = resource.has("frames") && resource.get("frames").isJsonArray() ? resource.getAsJsonArray("frames") : new JsonArray();
        for (JsonElement frame : frames) {
            if (!frame.isJsonNull() && !frame.getAsString().isBlank()) {
                lines.add(frame.getAsString());
            }
        }
        if (!lines.isEmpty()) {
            return lines;
        }
        String text = jsonText("text");
        for (String line : text.split("\\R")) {
            if (!line.isBlank()) {
                lines.add(line);
            }
        }
        return lines.isEmpty() ? List.of("Text") : lines;
    }
}
