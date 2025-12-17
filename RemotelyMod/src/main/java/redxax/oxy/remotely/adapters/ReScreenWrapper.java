package redxax.oxy.remotely.adapters;

import dev.deftu.omnicore.api.client.input.KeyboardModifiers;
import dev.deftu.omnicore.api.client.input.OmniKey;
import dev.deftu.omnicore.api.client.input.OmniMouseButton;
import dev.deftu.omnicore.api.client.render.ImmediateScreenRenderer;
import dev.deftu.omnicore.api.client.render.OmniRenderingContext;
import dev.deftu.omnicore.api.client.render.OmniResolution;
import dev.deftu.omnicore.api.client.screen.KeyPressEvent;
import dev.deftu.omnicore.api.client.screen.OmniScreen;
import dev.deftu.textile.Text;
import net.minecraft.client.Minecraft;
import org.jetbrains.annotations.NotNull;
import restudio.rescreen.platform.IDrawContext;
import restudio.rescreen.config.Config;
import restudio.rescreen.ui.core.ScreenManager;

import java.lang.reflect.Field;

public class ReScreenWrapper extends OmniScreen {
    private final restudio.rescreen.ui.core.Screen libScreen;
    private final ScreenManager sm = ScreenManager.getInstance();
    private long lastFrameTime = 0;

    public ReScreenWrapper(restudio.rescreen.ui.core.Screen libScreen) {
        super(Text.literal("ReScreen Wrapper " + libScreen.getClass().getSimpleName()));
        this.libScreen = libScreen;
    }

    @Override
    public void onInitialize(int width, int height) {
        super.onInitialize(width, height);
        lastFrameTime = System.nanoTime();
        //#if MC >= 1.21.9
        long handle = Minecraft.getInstance().getWindow().handle();
        //#else
        //$$ long handle = Minecraft.getInstance().getWindow().getWindow();
        //#endif
        sm.setWindowHandle(handle);
        try {
            Field f = restudio.rescreen.Main.class.getDeclaredField("window");
            f.setAccessible(true);
            f.setLong(null, handle);
        } catch (Throwable ignored) {}
        sm.updateDimensions(OmniResolution.getWindowWidth(), OmniResolution.getWindowHeight());
        float persistedScale = Config.configManager != null ? Config.configManager.getGuiScale() : (float) OmniResolution.getScaleFactor();
        sm.setGuiScale(persistedScale);
        ImmediateScreenRenderer.initialize();
        sm.setScreen(libScreen);
    }

    @Override
    public void onRender(@NotNull OmniRenderingContext ctx, int mouseX, int mouseY, float tickDelta) {
        this.onBackgroundRender(ctx, mouseX, mouseY, tickDelta);
        super.onRender(ctx, mouseX, mouseY, tickDelta);

        long now = System.nanoTime();
        float deltaSeconds = (float) ((now - lastFrameTime) / 1_000_000_000.0);
        lastFrameTime = now;

        if (deltaSeconds > 0.1f) deltaSeconds = 0.1f;
        if (deltaSeconds < 0.0f) deltaSeconds = 0.016f;

        sm.updateDimensions(OmniResolution.getWindowWidth(), OmniResolution.getWindowHeight());
        float mcScale = (float) OmniResolution.getScaleFactor();
        float reScale = sm.getGuiScale();
        if (mcScale == 0 || reScale == 0) return;
        float renderScale = reScale / mcScale;
        float mouseScale = mcScale / reScale;
        IDrawContext libCtx = new MinecraftDrawContextAdapter(ctx, renderScale);

        float finalDelta = deltaSeconds;
        ImmediateScreenRenderer.render(ctx, () -> {
            ctx.pose().push();
            ctx.pose().scale(renderScale, renderScale, 1f);
            sm.render(libCtx, (int) (mouseX * mouseScale), (int) (mouseY * mouseScale), finalDelta);
            sm.processTasks();
            ctx.pose().pop();
        });
    }

    @Override
    public boolean onMouseClick(@NotNull OmniMouseButton button, double mouseX, double mouseY, @NotNull KeyboardModifiers modifiers) {
        double sf = OmniResolution.getScaleFactor();
        boolean handled = sm.mouseClicked(mouseX * sf, mouseY * sf, button.getCode());
        return handled || super.onMouseClick(button, mouseX, mouseY, modifiers);
    }

    @Override
    public boolean onMouseRelease(@NotNull OmniMouseButton button, double mouseX, double mouseY, @NotNull KeyboardModifiers modifiers) {
        double sf = OmniResolution.getScaleFactor();
        boolean handled = sm.mouseReleased(mouseX * sf, mouseY * sf, button.getCode());
        return handled || super.onMouseRelease(button, mouseX, mouseY, modifiers);
    }

    @Override
    public boolean onMouseDrag(@NotNull OmniMouseButton button, double mouseX, double mouseY, double deltaX, double deltaY, long clickTime, @NotNull KeyboardModifiers modifiers) {
        double sf = OmniResolution.getScaleFactor();
        boolean handled = sm.mouseDragged(mouseX * sf, mouseY * sf, button.getCode(), deltaX * sf, deltaY * sf);
        return handled || super.onMouseDrag(button, mouseX, mouseY, deltaX, deltaY, clickTime, modifiers);
    }

    @Override
    public boolean onMouseScroll(double mouseX, double mouseY, double amount, double horizontalAmount) {
        double sf = OmniResolution.getScaleFactor();
        boolean handled = sm.mouseScrolled(mouseX * sf, mouseY * sf, horizontalAmount, amount);
        return handled || super.onMouseScroll(mouseX, mouseY, amount, horizontalAmount);
    }

    @Override
    public boolean onKeyPress(@NotNull OmniKey key, int scanCode, char typedChar, @NotNull KeyboardModifiers modifiers, @NotNull KeyPressEvent event) {
        boolean handled = false;
        if (event == KeyPressEvent.PRESSED) {
            handled = sm.keyPressed(key.getCode(), scanCode, modifiers.toMods());
        } else if (event == KeyPressEvent.TYPED) {
            handled = sm.charTyped(typedChar, modifiers.toMods());
        }
        return handled || super.onKeyPress(key, scanCode, typedChar, modifiers, event);
    }

    @Override
    public boolean onKeyRelease(@NotNull OmniKey key, int scanCode, @NotNull KeyboardModifiers modifiers) {
        boolean handled = sm.keyReleased(key.getCode(), scanCode, modifiers.toMods());
        return handled || super.onKeyRelease(key, scanCode, modifiers);
    }

    @Override
    public void onScreenClose() {
        super.onScreenClose();
        restudio.rescreen.ui.core.Screen current = sm.getCurrentScreen();
        if (current != null) current.removed();
        sm.setScreen(null);
    }

    public void onDisplayed() {}

    public restudio.rescreen.ui.core.Screen getScreen() {
        return libScreen;
    }
}
