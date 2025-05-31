package redxax.oxy.remotely.ui.tests;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;
import redxax.oxy.remotely.RemotelyClient;
import redxax.oxy.remotely.config.Config;
import redxax.oxy.remotely.terminal.MultiTerminalScreen;
import redxax.oxy.remotely.ui.ReScreen;
import redxax.oxy.remotely.ui.widgets.*;
import redxax.oxy.remotely.util.Notification;

import java.util.List;


public class WidgetsTestingScreen extends ReScreen {

    ContextMenuWidget contextMenu;
    public WidgetsTestingScreen() {
        super(Text.of("Testing Screen"));
    }

    public void init() {
        super.init();
        addDrawableChild(new AnimatedButton.ButtonBuilder().pos(70, 42).size(100, 20).label(Text.literal("Click Me")).onClick(() -> headerBuilder.nextPosition()).build());
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
        addDrawableChild(new AnimatedButton.ButtonBuilder().pos(360, 42).size(120, 20).label(Text.literal("No Color Animation")).animateColor(false).build());
        addDrawableChild(new AnimatedButton.ButtonBuilder().pos(360, 72).size(120, 20).label(Text.literal("No Elevation Animation")).animateElevation(false).hint("No Annoying Movements").build());
        addDrawableChild(new AnimatedButton.ButtonBuilder().pos(360, 102).size(120, 20).label(Text.literal("Flat Button")).flat(true).hint("Pretty Flat").build());
        addDrawableChild(new AnimatedButton.ButtonBuilder().pos(360, 132).size(120, 20).label(Text.literal("No Open Animation")).entranceAnimation(false).hint("Animation Won't Play On Open").build());
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
