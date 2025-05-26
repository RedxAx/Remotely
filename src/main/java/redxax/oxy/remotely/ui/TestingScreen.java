package redxax.oxy.remotely.ui;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;
import redxax.oxy.remotely.ui.widgets.*;
import redxax.oxy.remotely.util.Notification;

import java.util.List;


public class TestingScreen extends Screen {

    ContextMenuWidget contextMenu;
    public TestingScreen() {
        super(Text.of("Testing Screen"));
    }

    public void init() {
        super.init();
        addDrawableChild(new AnimatedButton.ButtonBuilder().pos(10, 10).size(100, 20).label(Text.literal("Click Me")).build());
        addDrawableChild(new SquareButtonWidget.Builder().pos(10, 40).size(18, 18).image(null).build());
        addDrawableChild(new SquareButtonWidget.Builder().pos(30, 40).size(18, 18).imagePath("/assets/remotely/icons/remotely.png").build());
        addDrawableChild(new SquareButtonWidget.Builder().pos(50, 40).size(18, 18).imagePath("/assets/remotely/icons/external.png").build());
        addDrawableChild(new SquareButtonWidget.Builder().pos(70, 40).size(18, 18).imagePath("/assets/remotely/icons/terminal.png").build());
        addDrawableChild(new SquareButtonWidget.Builder().pos(90, 40).size(18, 18).imagePath("/assets/remotely/icons/audio.png").build());
        addDrawableChild(new ToggleWidget.Builder().pos(10, 70).size(40, 20).build());
        addDrawableChild(new ToggleWidget.Builder().pos(60, 70).size(50, 20).toggled(true).build());
        addDrawableChild(new ScrollSelectorWidget.Builder().pos(10, 100).size(100, 20).options(List.of("Hello", "World!", "I'm,", "RemotelyOS!")).build());
        addDrawableChild(new TabSwitchWidget.Builder().pos(10, 130).size(100, 20).label("Tabs").options(List.of("ReOS", "1.0.0", "Alpha")).build());
        addDrawableChild(new TextInputWidget.Builder().pos(10, 160).size(100, 20).placeholder("Type here...").text("90% Bug Free!").build());
        addDrawableChild(new DoubleSliderWidget.Builder().pos(10, 190).size(100, 20).label("Volume").value(50).build());
        addDrawableChild(new IconButton.Builder().pos(10, 220).size(100, 20).imagePath("/assets/remotely/icons/zip.png").label(Text.literal("Iconic Button")).centered(true).build());
        addDrawableChild(new AnimatedButton.ButtonBuilder().pos(300, 10).size(120, 20).label(Text.literal("No Color Animation")).animateColor(false).build());
        addDrawableChild(new AnimatedButton.ButtonBuilder().pos(300, 40).size(120, 20).label(Text.literal("No Elevation Animation")).animateElevation(false).build());
        addDrawableChild(new AnimatedButton.ButtonBuilder().pos(300, 70).size(120, 20).label(Text.literal("Flat Button")).flat(true).build());
        addDrawableChild(new AnimatedButton.ButtonBuilder().pos(300, 100).size(120, 20).label(Text.literal("No Open Animation")).entranceAnimation(false).build());
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
