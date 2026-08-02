package redxax.oxy.remotely.ui.collaboration;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;
import redxax.oxy.remotely.flow.ui.studio.ReSyncStudioPanelState;
import redxax.oxy.remotely.flow.ui.studio.StudioPanel;
import restudio.rescreen.theme.ThemeManager;
import restudio.rescreen.ui.core.Widget;
import restudio.rescreen.ui.rescreen.Container;
import restudio.rescreen.ui.rescreen.ReScreen;
import restudio.rescreen.ui.widgets.AnimatedButton;
import restudio.rescreen.ui.widgets.DropDownWidget;
import restudio.rescreen.ui.widgets.PopupWidget;
import restudio.rescreen.ui.widgets.ReorderableWidget;
import restudio.rescreen.ui.widgets.RowWidget;
import restudio.rescreen.ui.widgets.TextInputWidget;
import restudio.rescreen.ui.widgets.TitledRowWidget;
import restudio.rescreen.ui.widgets.ToggleWidget;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DesignerCollaborationAuthorityTest {
    @Test
    void resolvesPanelFieldsByIdentityAcrossDifferentRowOrders() {
        ThemeManager.initBrowserDefaults();
        TestScreen sender = new TestScreen();
        TestScreen receiver = new TestScreen();
        StudioPanel senderPanel = new StudioPanel(sender, "resource").show();
        StudioPanel receiverPanel = new StudioPanel(receiver, "resource").show();
        showImmediately(senderPanel);
        showImmediately(receiverPanel);
        AnimatedButton optional = button("Optional");
        AnimatedButton sourceWorld = button("World");
        AnimatedButton remoteWorld = button("World");
        ReSyncStudioPanelState.identify(sourceWorld, "resource-field:world");
        ReSyncStudioPanelState.identify(remoteWorld, "resource-field:world");
        senderPanel.setWidgets(List.of(optional, sourceWorld));
        receiverPanel.setWidgets(List.of(remoteWorld));

        JsonArray path = DesignerCollaborationAuthority.path(sender, sourceWorld);

        assertEquals("panel:resource", path.get(0).getAsString());
        assertEquals("key:resource-field:world", path.get(1).getAsString());
        assertSame(remoteWorld, DesignerCollaborationAuthority.resolve(receiver, path));
    }

    @Test
    void mirrorsTransientDropdownStateThroughTheSharedAuthority() {
        ThemeManager.initBrowserDefaults();
        TestScreen sender = new TestScreen();
        TestScreen receiver = new TestScreen();
        StudioPanel senderPanel = new StudioPanel(sender, "resource").show();
        StudioPanel receiverPanel = new StudioPanel(receiver, "resource").show();
        showImmediately(senderPanel);
        showImmediately(receiverPanel);
        DropDownWidget<String> source = dropdown();
        DropDownWidget<String> remote = dropdown();
        ReSyncStudioPanelState.identify(source, "resource-field:type");
        ReSyncStudioPanelState.identify(remote, "resource-field:type");
        senderPanel.setWidgets(List.of(source));
        receiverPanel.setWidgets(List.of(remote));
        source.onClick(source.getX() + 1, source.getY() + 1, 0);

        JsonObject states = DesignerCollaborationAuthority.widgetStates(sender);
        assertEquals(1, states.size());
        DesignerCollaborationAuthority.applyWidgetStates(receiver,
            List.of(new DesignerCollaborationAuthority.RemoteWidgetState(states, 1L)));

        assertFalse(remote.isExpanded());
        assertTrue(remote.isDropdownVisible());
        assertTrue(DesignerCollaborationAuthority.widgetStates(receiver).isEmpty());
    }

    @Test
    void resolvesAndAccentsFocusedFieldChildrenWithoutDesignerKeys() {
        ThemeManager.initBrowserDefaults();
        TestScreen sender = new TestScreen();
        TestScreen receiver = new TestScreen();
        StudioPanel senderPanel = new StudioPanel(sender, "resource").show();
        StudioPanel receiverPanel = new StudioPanel(receiver, "resource").show();
        showImmediately(senderPanel);
        showImmediately(receiverPanel);
        TextInputWidget sourceInput = input();
        TextInputWidget remoteInput = input();
        TitledRowWidget sourceWorld = row("World", sourceInput);
        TitledRowWidget remoteWorld = row("World", remoteInput);
        senderPanel.setWidgets(List.of(row("Optional", input()), sourceWorld));
        receiverPanel.setWidgets(List.of(remoteWorld));
        sourceWorld.setFocused(true);
        sourceInput.setFocused(true);

        JsonArray path = DesignerCollaborationAuthority.path(sender, sourceInput);
        DesignerCollaborationAuthority.applyFocusAccents(receiver,
            List.of(new DesignerCollaborationAuthority.RemoteFocus(path, 0xFFFF0000)));

        assertSame(sourceInput, DesignerCollaborationAuthority.focused(sender));
        assertSame(remoteInput, DesignerCollaborationAuthority.resolve(receiver, path));
        assertNotNull(remoteInput.getCollaborationAccent());
    }

    @Test
    void resolvesPointersThroughContainerContentCoordinatesAndCurrentScroll() {
        ThemeManager.initBrowserDefaults();
        TestScreen sender = new TestScreen();
        TestScreen receiver = new TestScreen();
        sender.width = 400;
        sender.height = 300;
        receiver.width = 400;
        receiver.height = 300;
        Container source = new Container("source", 10, 20, 200, 220);
        Container remote = new Container("remote", 10, 20, 300, 220);
        source.setScrollOffset(120f);
        remote.setScrollOffset(40f);
        sender.addDrawableChild(source);
        receiver.addDrawableChild(remote);

        JsonObject state = DesignerCollaborationAuthority.pointer(sender, 110, 100, null);
        DesignerCollaborationAuthority.Pointer pointer = DesignerCollaborationAuthority.resolvePointer(receiver, state);

        assertNotNull(pointer);
        assertEquals(160, pointer.x());
        assertEquals(180, pointer.y());
    }

    @Test
    void resolvesPopupFieldsAcrossDifferentScreenRootOrders() {
        ThemeManager.initBrowserDefaults();
        TestScreen sender = new TestScreen();
        TestScreen receiver = new TestScreen();
        TextInputWidget sourceInput = input();
        TextInputWidget remoteInput = input();
        PopupWidget sourcePopup = new PopupWidget.Builder("World Settings").addRow("World", sourceInput).build();
        PopupWidget remotePopup = new PopupWidget.Builder("World Settings").addRow("World", remoteInput).build();
        sender.addDrawableChild(sourcePopup);
        receiver.addDrawableChild(button("Unrelated"));
        receiver.addDrawableChild(remotePopup);

        JsonArray path = DesignerCollaborationAuthority.path(sender, sourceInput);

        assertEquals("PopupWidget:World Settings", path.get(0).getAsJsonObject().get("identity").getAsString());
        assertEquals("key:popup:world-settings/row:world:0/0", path.get(1).getAsString());
        assertSame(remoteInput, DesignerCollaborationAuthority.resolve(receiver, path));
    }

    @Test
    void mirrorsFocusedTextAndSelectionThroughTheWidgetCapability() {
        ThemeManager.initBrowserDefaults();
        TestScreen sender = new TestScreen();
        TestScreen receiver = new TestScreen();
        StudioPanel senderPanel = new StudioPanel(sender, "resource").show();
        StudioPanel receiverPanel = new StudioPanel(receiver, "resource").show();
        showImmediately(senderPanel);
        showImmediately(receiverPanel);
        TextInputWidget source = input();
        TextInputWidget remote = input();
        senderPanel.setWidgets(List.of(row("Name", source)));
        receiverPanel.setWidgets(List.of(row("Name", remote)));
        source.setText("Selected Name");
        source.selectAll();
        source.setFocused(true);

        JsonObject states = DesignerCollaborationAuthority.widgetStates(sender);
        DesignerCollaborationAuthority.applyWidgetStates(receiver,
            List.of(new DesignerCollaborationAuthority.RemoteWidgetState(states, 1L)));

        assertEquals("Selected Name", remote.getText());
    }

    @Test
    void resolvesAndAccentsFieldsInsideReorderableRows() {
        ThemeManager.initBrowserDefaults();
        TestScreen sender = new TestScreen();
        TestScreen receiver = new TestScreen();
        StudioPanel senderPanel = new StudioPanel(sender, "resource").show();
        StudioPanel receiverPanel = new StudioPanel(receiver, "resource").show();
        showImmediately(senderPanel);
        showImmediately(receiverPanel);
        TextInputWidget sourceInput = input();
        TextInputWidget remoteInput = input();
        ReorderableWidget<RowWidget> source = reorderable(sourceInput);
        ReorderableWidget<RowWidget> remote = reorderable(remoteInput);
        senderPanel.setWidgets(List.of(new TitledRowWidget.Builder().title("Paths").size(180, 34).addWidget(source).build()));
        receiverPanel.setWidgets(List.of(new TitledRowWidget.Builder().title("Paths").size(180, 34).addWidget(remote).build()));
        sourceInput.setFocused(true);

        JsonArray path = DesignerCollaborationAuthority.path(sender, sourceInput);
        DesignerCollaborationAuthority.applyFocusAccents(receiver,
            List.of(new DesignerCollaborationAuthority.RemoteFocus(path, 0xFFFF0000)));

        assertSame(remoteInput, DesignerCollaborationAuthority.resolve(receiver, path));
        assertNotNull(remoteInput.getCollaborationAccent());
    }

    @Test
    void toggleKeepsItsSemanticAccentDuringRemoteFocus() {
        ThemeManager.initBrowserDefaults();
        ToggleWidget toggle = new ToggleWidget.Builder().toggled(true).size(40, 18).build();

        toggle.setCollaborationAccent(CollaborationVisuals.accent(0xFFFF0000));

        assertFalse(toggle.canBeFocused());
        assertFalse(toggle.hasCollaborationAccent());
    }

    @Test
    void resolvesFieldsFromAHostOwnedCollaborationContainer() {
        ThemeManager.initBrowserDefaults();
        ExternalContainerScreen sender = new ExternalContainerScreen();
        ExternalContainerScreen receiver = new ExternalContainerScreen();
        Container sourceContainer = new Container("source", 0, 0, 200, 200);
        Container remoteContainer = new Container("remote", 0, 0, 200, 200);
        DropDownWidget<String> source = dropdown();
        DropDownWidget<String> remote = dropdown();
        ReSyncStudioPanelState.identify(source, "resource-field:type");
        ReSyncStudioPanelState.identify(remote, "resource-field:type");
        sourceContainer.addWidget(source);
        remoteContainer.addWidget(remote);
        sender.expose("studio_resource", sourceContainer);
        receiver.expose("studio_resource", remoteContainer);
        source.onClick(source.getX() + 1, source.getY() + 1, 0);

        JsonArray path = DesignerCollaborationAuthority.path(sender, source);
        JsonObject states = DesignerCollaborationAuthority.widgetStates(sender);
        DesignerCollaborationAuthority.applyWidgetStates(receiver,
            List.of(new DesignerCollaborationAuthority.RemoteWidgetState(states, 1L)));

        assertEquals("panel:studio_resource", path.get(0).getAsString());
        assertSame(remote, DesignerCollaborationAuthority.resolve(receiver, path));
        assertTrue(remote.isDropdownVisible());
    }

    private static AnimatedButton button(String label) {
        return new AnimatedButton.Builder().label(label).size(180, 18).build();
    }

    private static DropDownWidget<String> dropdown() {
        return new DropDownWidget.Builder<>(List.of("One", "Two")).size(180, 18).build();
    }

    private static TextInputWidget input() {
        return new TextInputWidget.Builder().size(180, 18).build();
    }

    private static TitledRowWidget row(String title, TextInputWidget input) {
        return new TitledRowWidget.Builder().title(title).size(180, 34).addWidget(input).build();
    }

    private static ReorderableWidget<RowWidget> reorderable(TextInputWidget input) {
        RowWidget row = new RowWidget.Builder().size(180, 18).addWidget(input).build();
        return new ReorderableWidget.Builder<RowWidget>().items(row).size(180, 18).build();
    }

    private static void showImmediately(StudioPanel panel) {
        panel.sidePanel().animation(false);
        panel.sidePanel().update();
    }

    private static final class TestScreen extends ReScreen {
    }

    private static final class ExternalContainerScreen extends ReScreen {
        private String id = "";
        private Widget container;

        private void expose(String id, Widget container) {
            this.id = id;
            this.container = container;
        }

        @Override
        public Map<String, Widget> collaborationContainers() {
            return container != null ? Map.of(id, container) : Map.of();
        }
    }
}
