package redxax.oxy.remotely.ui.tests;

import restudio.rescreen.config.Config;
import restudio.rescreen.platform.IDrawContext;
import redxax.oxy.remotely.RemotelyClient;
import restudio.rescreen.ui.rescreen.ReScreen;
import restudio.rescreen.ui.widgets.*;
import restudio.rescreen.util.Notification;

import java.util.Arrays;
import java.util.List;


public class WidgetsTestingScreen extends ReScreen {

    ContextMenuWidget contextMenu;
    private PopupWidget testPopup;

    public WidgetsTestingScreen() {
        super();
    }

    public void init() {
        super.init();
        addDrawableChild(new AnimatedButton.Builder().pos(70, 42).size(100, 20).label(("Show Popup")).onClick(() -> testPopup.show()).build());
        addDrawableChild(new SquareButtonWidget.Builder().pos(70, 72).size(18, 18).image(null).build());
        addDrawableChild(new SquareButtonWidget.Builder().pos(90, 72).size(18, 18).imagePath("remotely.png").hint("Best Mod Ever!").build());
        addDrawableChild(new SquareButtonWidget.Builder().pos(110, 72).size(18, 18).imagePath("external.png").build());
        addDrawableChild(new SquareButtonWidget.Builder().pos(130, 72).size(18, 18).imagePath("terminal.png").build());
        addDrawableChild(new SquareButtonWidget.Builder().pos(150, 72).size(18, 18).imagePath("audio.png").build());
        addDrawableChild(new ToggleWidget.Builder().pos(120, 102).size(50, 20).toggled(true).onChange( () -> Config.background = !Config.background).build());
        addDrawableChild(new ScrollSelectorWidget.Builder().pos(70, 132).size(100, 20).options(List.of("Hello", "World!", "I'm,", "RemotelyOS!")).build());
        addDrawableChild(new TabSwitchWidget.Builder().pos(70, 162).size(100, 20).options(List.of("ReOS", "1.0.0", "Alpha")).build());
        addDrawableChild(new TextInputWidget.Builder().pos(70, 192).size(100, 20).placeholder("Type here...").text("90% Bug Free!").build());
        addDrawableChild(new DoubleSliderWidget.Builder().pos(70, 222).size(100, 20).label("Volume").value(50).build());
        addDrawableChild(new IconButton.Builder().pos(70, 252).size(100, 20).imagePath("zip.png").label(("Iconic Button")).centered(true).build());
        addDrawableChild(new AnimatedButton.Builder().pos(360, 42).size(120, 20).label(("No Color Animation")).animateColor(false).build());
        addDrawableChild(new AnimatedButton.Builder().pos(360, 72).size(120, 20).label(("No Elevation Animation")).animateElevation(false).hint("No Annoying Movements").build());
        addDrawableChild(new AnimatedButton.Builder().pos(360, 102).size(120, 20).label(("Flat Button")).flat(true).hint("Pretty Flat").build());
        addDrawableChild(new AnimatedButton.Builder().pos(360, 132).size(120, 20).label(("No Open Animation")).entranceAnimation(false).hint("Animation Won't Play On Open").build());
        contextMenu = new ContextMenuWidget.Builder(this)
                .addItem("Menu Context", null, "Hell Yea")
                .addIconItem("Berger", "download.png", () -> new Notification("SAY BURGER OR DIE", "I'M NOT JOKING", Notification.Type.ERROR), "")
                .addItem("Me Sad", null, "")
                .addItem("143", null, "")
                .addItem("I Have No Idea", null, "")
                .addHeaderButton("save.png", null, "")
                .addHeaderButton("fullscreen.png", null, "")
                .addHeaderButton("start.png", null, "")
                .addHeaderButton("newchat.png", null, "")
                .addHeaderButton("close.png", () -> client.setScreen(null), "Close Menu")
                .build();
        addDrawableChild(contextMenu);

        headerBuilder.addLeft("remotely.png", () -> client.setScreen(new WidgetsTestingScreen()), "Reload Screen")
                .addLeft("external.png", () -> client.setScreen(new ContainerTestingScreen()), "Open Container Testing Screen")
                .addLeft("start.png", null, "Start")
                .addRight("close.png", () -> client.setScreen(null), "Close Screen")
                .addRight("merge.png", null, "Merge")
                .addRight("explorer.png", null, "Explorer")
                .addRight("edit.png", null, "Burger")
                .visible(true)
                .build();

        AnimatedButton cancelButton = new AnimatedButton.Builder().label(("Cancel")).size(80, 20).onClick(() -> testPopup.hide()).accentType(Config.AccentType.DANGER).build();

        AnimatedButton okButton = new AnimatedButton.Builder().label(("OK")).size(80, 20).onClick(() -> {
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
    public void render(IDrawContext context, int mouseX, int mouseY, float delta) {
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
