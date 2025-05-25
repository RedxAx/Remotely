package redxax.oxy.remotely.ui;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;
import redxax.oxy.remotely.ui.widgets.*;

import java.util.List;


public class TestingScreen extends Screen {
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
        addDrawableChild(new ToggleWidget.Builder().pos(10, 70).size(100, 20).build());
        addDrawableChild(new ScrollSelectorWidget.Builder().pos(10, 100).size(100, 20).options(List.of("Hello", "World!", "I'm,", "RemotelyOS!")).build());
        addDrawableChild(new TabSwitchWidget.Builder().pos(10, 130).size(100, 20).label("Tabs").options(List.of("ReOS", "1.0.0", "Alpha")).build());
        addDrawableChild(new TextInputWidget.Builder().pos(10, 160).size(100, 20).placeholder("Type here...").text("90% Bug Free!").build());
        addDrawableChild(new DoubleSliderWidget.Builder().pos(10, 190).size(100, 20).label("Volume").value(50).build());
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);
    }
}
