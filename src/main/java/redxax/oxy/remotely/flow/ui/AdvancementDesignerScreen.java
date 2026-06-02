package redxax.oxy.remotely.flow.ui;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.lwjgl.glfw.GLFW;
import redxax.oxy.remotely.data.flow.FlowManager;
import redxax.oxy.remotely.data.flow.OptionCatalogCache;
import redxax.oxy.remotely.data.flow.ReSyncResourceType;
import redxax.oxy.remotely.flow.ui.studio.ReSyncStudioPanelState;
import restudio.rebase.ui.widgets.editor.CodeEditorWidget;
import restudio.rescreen.game.MinecraftGameItems;
import restudio.rescreen.platform.IDrawContext;
import restudio.rescreen.platform.lwjgl.MinecraftRenderItem;
import restudio.rescreen.ui.rescreen.Container;
import restudio.rescreen.ui.rescreen.ReScreen;
import restudio.rescreen.ui.rescreen.SidePanel;
import restudio.rescreen.ui.rescreen.layout.ManagedLayout;
import restudio.rescreen.ui.widgets.TextInputWidget;
import restudio.rescreen.ui.widgets.TitledRowWidget;
import restudio.rescreen.ui.widgets.ItemSelectorWidget;
import restudio.rescreen.util.Notification;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.ArrayList;
import java.util.function.Consumer;

public class AdvancementDesignerScreen extends ReScreen {
    private static final String ICON_CATALOG = "server:custom_content:recipe_item";
    private static final int NODE_WIDTH = 112;
    private static final int NODE_HEIGHT = 38;
    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();
    private final JsonObject tree;
    private final String serverId;
    private final Object parent;
    private final ReSyncStudioPanelState panelState = new ReSyncStudioPanelState();
    private final Deque<String> undo = new ArrayDeque<>();
    private final Deque<String> redo = new ArrayDeque<>();
    private SidePanel inspector;
    private CodeEditorWidget jsonInput;
    private String selectedNode = "root";
    private String draggedNode;
    private double panX = 120;
    private double panY = 90;

    public AdvancementDesignerScreen(JsonObject tree, String serverId, Object parent) {
        this.tree = tree;
        this.serverId = serverId;
        this.parent = parent;
        autoResizeContainers = false;
    }

    @Override
    public void init() {
        super.init();
        header().reset();
        header().addRight("save.png", this::save, "Save");
        header().addRight("goforward.png", this::redo, "Redo");
        header().addRight("goback.png", this::undo, "Undo");
        header().addRight("delete.png", this::deleteSelected, "Delete Node");
        header().addRight("add.png", this::addNode, "Add Node");
        header().build();
        buildInspector();
    }

    @Override
    public void render(IDrawContext context, int mouseX, int mouseY, float delta) {
        if (inspector != null) {
            int top = header().headerSize + 5;
            panelState.width(inspector.getDesiredWidth());
            inspector.y(top).height(Math.max(120, height - top - 8)).width(panelState.width());
        }
        super.render(context, mouseX, mouseY, delta);
    }

    @Override
    public void renderBackground(IDrawContext context, int mouseX, int mouseY, float delta) {
        super.renderBackground(context, mouseX, mouseY, delta);
        JsonObject nodes = nodes();
        for (Map.Entry<String, JsonElement> entry : nodes.entrySet()) {
            JsonObject node = entry.getValue().getAsJsonObject();
            String parentId = text(node, "parent");
            if (!parentId.isBlank() && !parentId.contains(":")) {
                String localParent = parentId;
                if (parentId.contains("/")) {
                    String[] parts = parentId.split("/", 2);
                    localParent = parts.length == 2 && parts[0].equals(text(tree, "id")) ? parts[1] : "";
                }
                JsonObject parentNode = nodes.has(localParent) ? nodes.getAsJsonObject(localParent) : null;
                if (parentNode != null) {
                    int x1 = nodeX(parentNode) + NODE_WIDTH;
                    int y1 = nodeY(parentNode) + NODE_HEIGHT / 2;
                    int x2 = nodeX(node);
                    int y2 = nodeY(node) + NODE_HEIGHT / 2;
                    int middle = x1 + (x2 - x1) / 2;
                    context.fill(Math.min(x1, middle), y1, Math.max(x1, middle) + 1, y1 + 1, 0xFF777777);
                    context.fill(middle, Math.min(y1, y2), middle + 1, Math.max(y1, y2) + 1, 0xFF777777);
                    context.fill(Math.min(middle, x2), y2, Math.max(middle, x2) + 1, y2 + 1, 0xFF777777);
                }
            }
        }
        for (Map.Entry<String, JsonElement> entry : nodes.entrySet()) {
            JsonObject node = entry.getValue().getAsJsonObject();
            int x = nodeX(node);
            int y = nodeY(node);
            boolean selected = entry.getKey().equals(selectedNode);
            int frame = frameColor(text(object(node, "display"), "frame"));
            context.fill(x, y, x + NODE_WIDTH, y + NODE_HEIGHT, selected ? 0xFF496A87 : 0xFF30343A);
            context.fill(x, y, x + NODE_WIDTH, y + 1, frame);
            context.fill(x, y + NODE_HEIGHT - 1, x + NODE_WIDTH, y + NODE_HEIGHT, frame);
            context.fill(x, y, x + 1, y + NODE_HEIGHT, frame);
            context.fill(x + NODE_WIDTH - 1, y, x + NODE_WIDTH, y + NODE_HEIGHT, frame);
            MinecraftRenderItem icon = icon(node);
            if (icon != null) {
                context.drawItem(icon, x + 5, y + 10, 0);
            }
            context.drawText(title(entry.getKey(), node), x + 27, y + 8, 0xFFFFFFFF, true);
            context.drawText(entry.getKey(), x + 27, y + 21, 0xFFB8C1CA, false);
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (super.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }
        if (button != GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            return false;
        }
        for (Map.Entry<String, JsonElement> entry : nodes().entrySet()) {
            JsonObject node = entry.getValue().getAsJsonObject();
            int x = nodeX(node);
            int y = nodeY(node);
            if (mouseX >= x && mouseX <= x + NODE_WIDTH && mouseY >= y && mouseY <= y + NODE_HEIGHT) {
                snapshot();
                selectedNode = entry.getKey();
                draggedNode = entry.getKey();
                refreshInspector();
                return true;
            }
        }
        draggedNode = "";
        return true;
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        if (button != GLFW.GLFW_MOUSE_BUTTON_LEFT || draggedNode == null) {
            return super.mouseDragged(mouseX, mouseY, button, deltaX, deltaY);
        }
        if (draggedNode.isBlank()) {
            panX += deltaX;
            panY += deltaY;
            return true;
        }
        JsonObject node = nodes().getAsJsonObject(draggedNode);
        JsonObject position = object(node, "position");
        position.addProperty("x", decimal(position, "x") + deltaX);
        position.addProperty("y", decimal(position, "y") + deltaY);
        refreshJson();
        return true;
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        draggedNode = null;
        return super.mouseReleased(mouseX, mouseY, button);
    }

    private void buildInspector() {
        if (inspector == null) {
            inspector = createSidePanel("advancement_inspector")
                .minWidth(220)
                .width(250)
                .show();
        }
        refreshInspector();
    }

    private void refreshInspector() {
        if (inspector == null) {
            return;
        }
        Container container = inspector.container();
        container.clearWidgets();
        container.layout(new ManagedLayout()).columns(1).padding(panelState.padding()).scrolling(true).enableSelecting(false);
        int rowWidth = panelState.rowWidth(inspector);
        JsonObject node = nodes().has(selectedNode) ? nodes().getAsJsonObject(selectedNode) : null;
        if (node != null) {
            JsonObject display = object(node, "display");
            container.addWidget(panelState.hint("Node " + selectedNode, rowWidth));
            container.addWidget(row("Title", text(display, "title"), value -> update(display, "title", value), rowWidth));
            container.addWidget(row("Description", text(display, "description"), value -> update(display, "description", value), rowWidth));
            container.addWidget(row("Icon", text(display, "icon"), value -> update(display, "icon", value), rowWidth));
            container.addWidget(selector("Icon Catalog", iconOptions(), text(display, "icon"), value -> update(display, "icon", value), rowWidth));
            container.addWidget(row("Frame", text(display, "frame"), value -> update(display, "frame", value), rowWidth));
            container.addWidget(row("Parent", text(node, "parent"), value -> update(node, "parent", value), rowWidth));
            container.addWidget(selector("Parent Picker", parentOptions(), text(node, "parent"), value -> update(node, "parent", value), rowWidth));
            container.addWidget(panelState.hint("Parent: node, tree/node, Or minecraft:id", rowWidth));
            container.addWidget(jsonSection("Criteria", object(node, "criteria"), value -> replace(node, "criteria", value), rowWidth, 116));
            container.addWidget(jsonSection("Requirements", node.has("requirements") ? node.get("requirements") : new JsonArray(), value -> node.add("requirements", value), rowWidth, 84));
            container.addWidget(jsonSection("Rewards", object(node, "rewards"), value -> replace(node, "rewards", value), rowWidth, 84));
            container.addWidget(jsonSection("Completion", object(node, "onComplete"), value -> replace(node, "onComplete", value), rowWidth, 100));
        }
        jsonInput = new CodeEditorWidget(0, 0, rowWidth, 180);
        jsonInput.setText(gson.toJson(tree));
        jsonInput.onChange = ignored -> applyJson();
        container.addWidget(panelState.codeRow("Advanced JSON", jsonInput, rowWidth, 198));
    }

    private void addNode() {
        snapshot();
        String id = nextNodeId();
        JsonObject node = new JsonObject();
        node.addProperty("enabled", true);
        node.addProperty("parent", selectedNode != null ? selectedNode : "root");
        JsonObject position = new JsonObject();
        position.addProperty("x", 160);
        position.addProperty("y", nodes().size() * 55);
        node.add("position", position);
        JsonObject display = new JsonObject();
        display.addProperty("title", id);
        display.addProperty("description", "Server Progress");
        display.addProperty("icon", "minecraft:stone");
        display.addProperty("frame", "task");
        display.addProperty("showToast", true);
        display.addProperty("announceToChat", false);
        display.addProperty("hidden", false);
        node.add("display", display);
        node.add("criteria", new JsonObject());
        nodes().add(id, node);
        selectedNode = id;
        refreshJson();
        refreshInspector();
    }

    private void deleteSelected() {
        if (selectedNode == null || "root".equals(selectedNode)) {
            return;
        }
        snapshot();
        nodes().remove(selectedNode);
        for (Map.Entry<String, JsonElement> entry : nodes().entrySet()) {
            JsonObject node = entry.getValue().getAsJsonObject();
            if (selectedNode.equals(text(node, "parent"))) {
                node.addProperty("parent", "root");
            }
        }
        selectedNode = "root";
        refreshJson();
        refreshInspector();
    }

    private void save() {
        applyJson();
        FlowManager manager = FlowManager.getInstance();
        if (manager != null && serverId != null) {
            manager.saveJsonResource(serverId, ReSyncResourceType.ADVANCEMENT_TREE, tree);
        }
        new Notification("Advancement Saved", text(tree, "displayName"), Notification.Type.SUCCESS);
    }

    private void applyJson() {
        if (jsonInput == null) {
            return;
        }
        try {
            JsonObject parsed = JsonParser.parseString(jsonInput.getText()).getAsJsonObject();
            tree.keySet().clear();
            for (Map.Entry<String, JsonElement> entry : parsed.entrySet()) {
                tree.add(entry.getKey(), entry.getValue());
            }
        } catch (RuntimeException ignored) {
        }
    }

    private void snapshot() {
        undo.push(gson.toJson(tree));
        redo.clear();
    }

    private void undo() {
        if (!undo.isEmpty()) {
            redo.push(gson.toJson(tree));
            restore(undo.pop());
        }
    }

    private void redo() {
        if (!redo.isEmpty()) {
            undo.push(gson.toJson(tree));
            restore(redo.pop());
        }
    }

    private void restore(String json) {
        JsonObject restored = JsonParser.parseString(json).getAsJsonObject();
        tree.keySet().clear();
        for (Map.Entry<String, JsonElement> entry : restored.entrySet()) {
            tree.add(entry.getKey(), entry.getValue());
        }
        refreshJson();
        refreshInspector();
    }

    private void refreshJson() {
        if (jsonInput != null && !jsonInput.isFocused()) {
            jsonInput.setText(gson.toJson(tree));
        }
    }

    private JsonObject nodes() {
        if (!tree.has("nodes") || !tree.get("nodes").isJsonObject()) {
            tree.add("nodes", new JsonObject());
        }
        return tree.getAsJsonObject("nodes");
    }

    private TitledRowWidget row(String label, String value, Consumer<String> onChange, int width) {
        TextInputWidget input = panelState.input(label, value, next -> {
            snapshot();
            onChange.accept(next);
            refreshJson();
        });
        input.setWidth(width - 8);
        return panelState.row(label, input, width);
    }

    private TitledRowWidget jsonSection(String label, JsonElement value, Consumer<JsonElement> onChange, int width, int height) {
        CodeEditorWidget editor = new CodeEditorWidget(0, 0, width - 8, height - 18);
        editor.setText(gson.toJson(value));
        editor.onChange = ignored -> {
            try {
                JsonElement parsed = JsonParser.parseString(editor.getText());
                snapshot();
                onChange.accept(parsed);
                refreshJson();
            } catch (RuntimeException ignoredFailure) {
            }
        };
        return panelState.codeRow(label, editor, width, height);
    }

    private TitledRowWidget selector(String label, List<String> options, String selected, Consumer<String> onChange, int width) {
        ItemSelectorWidget selector = new ItemSelectorWidget.Builder(this)
            .size(width - 8, 104)
            .embedded(true)
            .dismissOnSelect(false)
            .emptyMessage("No Options")
            .build();
        for (String option : options) {
            selector.addItem(option, () -> {
                snapshot();
                onChange.accept(option);
                refreshJson();
            });
        }
        selector.setSelectedItem(selected);
        TitledRowWidget row = panelState.row(label, selector, width);
        row.setHeight(122);
        return row;
    }

    private List<String> iconOptions() {
        FlowManager manager = FlowManager.getInstance();
        if (manager != null && serverId != null && !OptionCatalogCache.getInstance().hasCatalog(serverId, ICON_CATALOG)) {
            manager.ensureFlowClient(serverId).requestOptionCatalog(ICON_CATALOG);
        }
        List<String> options = new ArrayList<>(OptionCatalogCache.getInstance().getValues(serverId, ICON_CATALOG));
        if (options.isEmpty()) {
            options.addAll(List.of("stone", "book", "diamond", "emerald", "nether_star"));
        }
        return options.stream().distinct().sorted(String.CASE_INSENSITIVE_ORDER).toList();
    }

    private List<String> parentOptions() {
        List<String> options = new ArrayList<>();
        FlowManager manager = FlowManager.getInstance();
        if (manager != null && serverId != null) {
            for (Map.Entry<String, JsonObject> entry : manager.getJsonResourcesForServer(serverId, ReSyncResourceType.ADVANCEMENT_TREE).entrySet()) {
                JsonObject resourceNodes = entry.getValue().has("nodes") && entry.getValue().get("nodes").isJsonObject() ? entry.getValue().getAsJsonObject("nodes") : new JsonObject();
                for (String nodeId : resourceNodes.keySet()) {
                    options.add(entry.getKey().equals(text(tree, "id")) ? nodeId : entry.getKey() + "/" + nodeId);
                }
            }
        }
        if (options.isEmpty()) {
            options.addAll(nodes().keySet());
        }
        options.remove(selectedNode);
        return options.stream().distinct().sorted(String.CASE_INSENSITIVE_ORDER).toList();
    }

    private void update(JsonObject object, String key, String value) {
        object.addProperty(key, value != null ? value : "");
    }

    private void replace(JsonObject object, String key, JsonElement value) {
        object.add(key, value != null ? value : new JsonObject());
    }

    private MinecraftRenderItem icon(JsonObject node) {
        String icon = text(object(node, "display"), "icon");
        if (icon.startsWith("content:") || icon.startsWith("provider:")) {
            icon = "stone";
        }
        if (icon.startsWith("minecraft:")) {
            icon = icon.substring("minecraft:".length());
        }
        return MinecraftGameItems.fromVisual(icon.isBlank() ? "stone" : icon, 1, title("", node), List.of(), null);
    }

    private int frameColor(String frame) {
        return switch (frame) {
            case "challenge" -> 0xFF9D48CC;
            case "goal" -> 0xFFB57B27;
            default -> 0xFF6E8F3A;
        };
    }

    private String nextNodeId() {
        int index = nodes().size();
        while (nodes().has("node_" + index)) {
            index++;
        }
        return "node_" + index;
    }

    private int nodeX(JsonObject node) {
        return (int) Math.round(panX + decimal(object(node, "position"), "x"));
    }

    private int nodeY(JsonObject node) {
        return (int) Math.round(panY + decimal(object(node, "position"), "y"));
    }

    private String title(String id, JsonObject node) {
        JsonObject display = object(node, "display");
        String title = text(display, "title");
        return title.isBlank() ? id : title;
    }

    private JsonObject object(JsonObject value, String key) {
        if (!value.has(key) || !value.get(key).isJsonObject()) {
            value.add(key, new JsonObject());
        }
        return value.getAsJsonObject(key);
    }

    private String text(JsonObject value, String key) {
        return value != null && value.has(key) && !value.get(key).isJsonNull() ? value.get(key).getAsString() : "";
    }

    private double decimal(JsonObject value, String key) {
        return value != null && value.has(key) && !value.get(key).isJsonNull() ? value.get(key).getAsDouble() : 0;
    }
}
