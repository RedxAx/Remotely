package redxax.oxy.remotely.adapters;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;
import restudio.rescreen.platform.IDrawContext;
import restudio.rescreen.ui.core.ScreenManager;

public class ReScreenWrapper extends Screen {
    private final restudio.rescreen.ui.core.Screen libScreen;
    private final ScreenManager sm = ScreenManager.getInstance();

    public ReScreenWrapper(restudio.rescreen.ui.core.Screen libScreen) {
        super(Text.of("ReScreen Wrapper " + libScreen.getClass().getSimpleName()));
        this.libScreen = libScreen;
    }

    @Override
    protected void init() {
        super.init();

        syncReScreenTimingAndAnimationConfig();

        sm.setGuiScale((float) this.client.getWindow().getScaleFactor());
        sm.updateDimensions(MinecraftClient.getInstance().getWindow().getWidth(), MinecraftClient.getInstance().getWindow().getHeight());

        try {
            sm.execute(() -> {});
        } catch (Throwable ignored) {}

        sm.setScreen(libScreen);
    }

    @Override
    public void render(DrawContext drawContext, int mouseX, int mouseY, float delta) {
        super.render(drawContext, mouseX, mouseY, delta);

        IDrawContext libCtx = new MinecraftDrawContextAdapter(drawContext);

        if (this.width != sm.getScaledWidth() || this.height != sm.getScaledHeight() || (float) this.client.getWindow().getScaleFactor() != sm.getGuiScale()) {
            sm.setGuiScale((float) this.client.getWindow().getScaleFactor());
            sm.updateDimensions(this.client.getWindow().getWidth(), this.client.getWindow().getHeight());
        }

        sm.render(libCtx, mouseX, mouseY, delta);
        sm.processTasks();
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        double sf = this.client.getWindow().getScaleFactor();
        boolean handled = sm.mouseClicked(mouseX * sf, mouseY * sf, button);
        return handled || super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        double sf = this.client.getWindow().getScaleFactor();
        boolean handled = sm.mouseReleased(mouseX * sf, mouseY * sf, button);
        return handled || super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        double sf = this.client.getWindow().getScaleFactor();
        boolean handled = sm.mouseDragged(mouseX * sf, mouseY * sf, button, deltaX * sf, deltaY * sf);
        return handled || super.mouseDragged(mouseX, mouseY, button, deltaX, deltaY);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontal, double vertical) {
        double sf = this.client.getWindow().getScaleFactor();
        boolean handled = sm.mouseScrolled(mouseX * sf, mouseY * sf, horizontal, vertical);
        return handled || super.mouseScrolled(mouseX, mouseY, horizontal, vertical);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        boolean handled = sm.keyPressed(keyCode, scanCode, modifiers);
        return handled || super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean keyReleased(int keyCode, int scanCode, int modifiers) {
        boolean handled = sm.keyReleased(keyCode, scanCode, modifiers);
        return handled || super.keyReleased(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char chr, int modifiers) {
        boolean handled = sm.charTyped(chr, modifiers);
        return handled || super.charTyped(chr, modifiers);
    }

    @Override
    public void removed() {
        super.removed();
        restudio.rescreen.ui.core.Screen current = sm.getCurrentScreen();
        if (current != null) current.removed();
        sm.setScreen(null);
    }

    @Override
    public void onDisplayed() {
        super.onDisplayed();
        restudio.rescreen.ui.core.Screen current = sm.getCurrentScreen();
        if (current != null) current.onDisplayed();
    }

    private void syncReScreenTimingAndAnimationConfig() {
//        restudio.rescreen.config.Config.globalExpandSpeed = redxax.oxy.remotely.config.Config.globalExpandSpeed;
//        restudio.rescreen.config.Config.globalMovementSpeed = redxax.oxy.remotely.config.Config.globalMovementSpeed;
//        restudio.rescreen.config.Config.globalScrollSpeed = redxax.oxy.remotely.config.Config.globalScrollSpeed;
//        restudio.rescreen.config.Config.scaleAnimationSpeed = redxax.oxy.remotely.config.Config.scaleAnimationSpeed;
//        restudio.rescreen.config.Config.animScaleFactor = (float) this.client.getWindow().getScaleFactor();
//        restudio.rescreen.config.Config.targetScaleFactor = (float) this.client.getWindow().getScaleFactor();
//        restudio.rescreen.config.Config.shadow = redxax.oxy.remotely.config.Config.shadow;
    }
}