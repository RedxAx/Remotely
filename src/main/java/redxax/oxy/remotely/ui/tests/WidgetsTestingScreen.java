package redxax.oxy.remotely.ui.tests;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;
import redxax.oxy.remotely.RemotelyClient;
import redxax.oxy.remotely.config.Config;
import redxax.oxy.remotely.terminal.MultiTerminalScreen;
import redxax.oxy.remotely.terminal.TerminalInstance;
import redxax.oxy.remotely.ui.ReScreen;
import redxax.oxy.remotely.ui.widgets.*;
import redxax.oxy.remotely.util.Notification;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;


public class WidgetsTestingScreen extends ReScreen {

    ContextMenuWidget contextMenu;
    private PopupWidget testPopup;

    public WidgetsTestingScreen() {
        super(Text.of("Testing Screen"));
    }

    public void init() {
        super.init();
        addDrawableChild(new AnimatedButton.Builder().pos(70, 42).size(100, 20).label(Text.literal("Show Popup")).onClick(() -> testPopup.show()).build());
        addDrawableChild(new SquareButtonWidget.Builder().pos(70, 72).size(18, 18).image(null).build());
        addDrawableChild(new SquareButtonWidget.Builder().pos(90, 72).size(18, 18).imagePath("/assets/remotely/icons/remotely.png").hint("Best Mod Ever!").build());
        addDrawableChild(new SquareButtonWidget.Builder().pos(110, 72).size(18, 18).imagePath("/assets/remotely/icons/external.png").build());
        addDrawableChild(new SquareButtonWidget.Builder().pos(130, 72).size(18, 18).imagePath("/assets/remotely/icons/terminal.png").build());
        addDrawableChild(new SquareButtonWidget.Builder().pos(150, 72).size(18, 18).imagePath("/assets/remotely/icons/audio.png").build());
        addDrawableChild(new ToggleWidget.Builder().pos(70, 102).size(40, 20).toggleOff(() -> hideButton(1, 2, 3, -1)).toggleOn(() -> showButton(1, 2, 3, -1)).build());
        addDrawableChild(new ToggleWidget.Builder().pos(120, 102).size(50, 20).toggled(true).onChange( () -> Config.background = !Config.background).build());
        addDrawableChild(new ScrollSelectorWidget.Builder().pos(70, 132).size(100, 20).options(List.of("Hello", "World!", "I'm,", "RemotelyOS!")).build());
        addDrawableChild(new TabSwitchWidget.Builder().pos(70, 162).size(100, 20).label("Tabs").options(List.of("ReOS", "1.0.0", "Alpha")).build());
        addDrawableChild(new TextInputWidget.Builder().pos(70, 192).size(100, 20).placeholder("Type here...").text("90% Bug Free!").build());
        addDrawableChild(new DoubleSliderWidget.Builder().pos(70, 222).size(100, 20).label("Volume").value(50).build());
        addDrawableChild(new IconButton.Builder().pos(70, 252).size(100, 20).imagePath("/assets/remotely/icons/zip.png").label(Text.literal("Iconic Button")).centered(true).build());
        addDrawableChild(new AnimatedButton.Builder().pos(360, 42).size(120, 20).label(Text.literal("No Color Animation")).animateColor(false).build());
        addDrawableChild(new AnimatedButton.Builder().pos(360, 72).size(120, 20).label(Text.literal("No Elevation Animation")).animateElevation(false).hint("No Annoying Movements").build());
        addDrawableChild(new AnimatedButton.Builder().pos(360, 102).size(120, 20).label(Text.literal("Flat Button")).flat(true).hint("Pretty Flat").build());
        addDrawableChild(new AnimatedButton.Builder().pos(360, 132).size(120, 20).label(Text.literal("No Open Animation")).entranceAnimation(false).hint("Animation Won't Play On Open").build());
        addDrawableChild(new TerminalWidget.Builder(new TerminalInstance(client, this, UUID.randomUUID())).pos(200, 162).size(400, 150).build());
        contextMenu = new ContextMenuWidget.Builder(this)
                .addItem("Menu Context", null, "Hell Yea")
                .addIconItem("Berger", "/assets/remotely/icons/download.png", () -> new Notification("SAY BURGER OR DIE", "I'M NOT JOKING", Notification.Type.ERROR), "")
                .addItem("Me Sad", null, "")
                .addItem("143", null, "")
                .addItem("I Have No Idea", null, "")
                .addHeaderButton("/assets/remotely/icons/save.png", null, "")
                .addHeaderButton("/assets/remotely/icons/fullscreen.png", null, "")
                .addHeaderButton("/assets/remotely/icons/start.png", null, "")
                .addHeaderButton("/assets/remotely/icons/newchat.png", null, "")
                .addHeaderButton("/assets/remotely/icons/close.png", () -> MinecraftClient.getInstance().setScreen(null), "Close Menu")
                .build();
        addDrawableChild(contextMenu);

        headerBuilder.addLeft("/assets/remotely/icons/remotely.png", () -> MinecraftClient.getInstance().setScreen(new WidgetsTestingScreen()), "Reload Screen")
                .addLeft("/assets/remotely/icons/terminal.png", () -> MinecraftClient.getInstance().setScreen(new MultiTerminalScreen(MinecraftClient.getInstance(), this, RemotelyClient.INSTANCE)), "Terminal")
                .addLeft("/assets/remotely/icons/external.png", () -> client.setScreen(new ContainerTestingScreen()), "Open Container Testing Screen")
                .addLeft("/assets/remotely/icons/start.png", null, "Start")
                .addRight("/assets/remotely/icons/close.png", () -> MinecraftClient.getInstance().setScreen(null), "Close Screen")
                .addRight("/assets/remotely/icons/merge.png", null, "Merge")
                .addRight("/assets/remotely/icons/explorer.png", null, "Explorer")
                .addRight("/assets/remotely/icons/edit.png", null, "Burger")
                .visible(true)
                .build();

        AnimatedButton cancelButton = new AnimatedButton.Builder().label(Text.of("Cancel")).size(80, 20).onClick(() -> testPopup.hide()).accentType(Config.AccentType.DANGER).build();

        AnimatedButton okButton = new AnimatedButton.Builder().label(Text.of("OK")).size(80, 20).onClick(() -> {
                    new Notification("Confirmed!", Notification.Type.SUCCESS);
                    testPopup.hide();
                }).build();

        this.testPopup = new PopupWidget.Builder("Upgraded Popup")
                .pos((width / 2) - (350 / 2), 50)
                .size(350, 500)
                .setResizable(true)
                .setMinSize(250, 300)
                .onClose(() -> new Notification("Popup closed!", Notification.Type.INFO))
                .addTextField("Username", "RedxAx", (newValue) -> System.out.println("Username changed: " + newValue))
                .addDropdown("Difficulty", Arrays.asList("Easy", "Normal", "Hard", "Nightmare", "Ree*"), "Normal", String::toString, (selection) -> System.out.println("Difficulty: " + selection))
                .addRow("Game Settings", true, 20,
                        new TabSwitchWidget.Builder().options(Arrays.asList("Survival", "Creative", "Spectator")).onChange((index) -> System.out.println("Mode index: " + index)).build(),
                        new ScrollSelectorWidget.Builder().options(List.of("Day", "Night", "Twilight")).onChange((index) -> System.out.println("Time index: " + index)).build()
                )

                .addDoubleSlider("Volume", 0.75, (value) -> System.out.println("Volume set to: " + value))
                .addTextArea("Description", "This is a multi-line text area.\nIt supports scrolling and text editing.", 100, (text) -> System.out.println("Description updated"))
                .addRow("Toggles", false, 18, new ToggleWidget.Builder().toggled(true).build(), new ToggleWidget.Builder().build())
                .addRow("", false, 20, cancelButton, okButton).build();

        this.testPopup.hide();
        addDrawableChild(this.testPopup);
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 1) {
            contextMenu.show((int)mouseX, (int) mouseY);
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }
}
