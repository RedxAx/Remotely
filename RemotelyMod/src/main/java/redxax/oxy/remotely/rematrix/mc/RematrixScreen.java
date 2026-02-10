package redxax.oxy.remotely.rematrix.mc;

import net.minecraft.client.Minecraft;
//#if MC >= 1.20.1
import net.minecraft.client.gui.GuiGraphics;
//#endif
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import redxax.oxy.remotely.adapters.MinecraftDrawContextAdapter;
import restudio.rescreen.config.Config;
import restudio.rescreen.ui.core.ScreenManager;

import java.lang.reflect.Field;

public class RematrixScreen extends Screen {
    private static restudio.rescreen.ui.core.Screen suspendedScreen;
    private static boolean skipCloseCleanup;
    private final restudio.rescreen.ui.core.Screen libScreen;
    private final ScreenManager sm = ScreenManager.getInstance();
    private long lastFrameTime;

    public RematrixScreen(restudio.rescreen.ui.core.Screen libScreen) {
        super(Component.literal("ReScreen " + libScreen.getClass().getSimpleName()));
        this.libScreen = libScreen;
        suspendedScreen = libScreen;
    }

    @Override
    protected void init() {
        super.init();
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
        int windowWidth = Minecraft.getInstance().getWindow().getWidth();
        int windowHeight = Minecraft.getInstance().getWindow().getHeight();
        sm.updateDimensions(windowWidth, windowHeight);
        float mcScale = (float) Minecraft.getInstance().getWindow().getGuiScale();
        float persistedScale = Config.configManager != null ? Config.configManager.getGuiScale() : mcScale;
        sm.setGuiScale(persistedScale);
        if (Config.desktopMode && (sm.getDesktopSuperScreen() == null || libScreen.shouldForceSuperScreen())) {
            sm.setDesktopSuperScreen(libScreen);
        }
        sm.setScreen(libScreen);
    }

    //#if MC >= 1.20.1
    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float tickDelta) {
        //#if MC >= 1.20.1 && MC < 1.20.6
        //$$ this.renderBackground(guiGraphics);
        //#endif
        super.render(guiGraphics, mouseX, mouseY, tickDelta);

        long now = System.nanoTime();
        float deltaSeconds = (float) ((now - lastFrameTime) / 1_000_000_000.0);
        lastFrameTime = now;

        if (deltaSeconds > 0.1f) deltaSeconds = 0.1f;
        if (deltaSeconds < 0.0f) deltaSeconds = 0.016f;

        int windowWidth = Minecraft.getInstance().getWindow().getWidth();
        int windowHeight = Minecraft.getInstance().getWindow().getHeight();
        sm.updateDimensions(windowWidth, windowHeight);
        float mcScale = (float) Minecraft.getInstance().getWindow().getGuiScale();
        float reScale = sm.getGuiScale();
        if (mcScale == 0 || reScale == 0) return;
        float renderScale = reScale / mcScale;
        float mouseScale = mcScale / reScale;

        RematrixMcContext ctx = new RematrixMcContext(guiGraphics, renderScale);
        MinecraftDrawContextAdapter libCtx = new MinecraftDrawContextAdapter(ctx);


        //#if MC >= 1.21.9
        var pose = guiGraphics.pose();
        pose.pushMatrix();
        pose.scale(renderScale, renderScale);
        //#endif
        //#if MC >= 1.20.1 && MC < 1.21.6
        //$$ var pose = guiGraphics.pose();
        //$$ pose.pushPose();
        //$$ pose.scale(renderScale, renderScale, 1f);
        //#endif
        //#if MC >= 1.21.6 && MC < 1.21.9
        //$$ var pose = guiGraphics.pose();
        //$$ pose.pushMatrix();
        //$$ pose.scale(renderScale, renderScale);
        //#endif
        sm.render(libCtx, (int) (mouseX * mouseScale), (int) (mouseY * mouseScale), deltaSeconds);
        sm.processTasks();
        //#if MC >= 1.21.9
        pose.popMatrix();
        //#endif
        //#if MC >= 1.20.1 && MC < 1.21.6
        //$$ pose.popPose();
        //#endif
        //#if MC >= 1.21.6 && MC < 1.21.9
        //$$ pose.popMatrix();
        //#endif
    }
    //#endif

    //#if MC < 1.20.1
    //$$ @Override
    //$$ public void render(com.mojang.blaze3d.vertex.PoseStack poseStack, int mouseX, int mouseY, float tickDelta) {
    //$$     this.renderBackground(poseStack, mouseX, mouseY, tickDelta);
    //$$     super.render(poseStack, mouseX, mouseY, tickDelta);
    //$$
    //$$     long now = System.nanoTime();
    //$$     float deltaSeconds = (float) ((now - lastFrameTime) / 1_000_000_000.0);
    //$$     lastFrameTime = now;
    //$$
    //$$     if (deltaSeconds > 0.1f) deltaSeconds = 0.1f;
    //$$     if (deltaSeconds < 0.0f) deltaSeconds = 0.016f;
    //$$
    //$$     int windowWidth = Minecraft.getInstance().getWindow().getWidth();
    //$$     int windowHeight = Minecraft.getInstance().getWindow().getHeight();
    //$$     sm.updateDimensions(windowWidth, windowHeight);
    //$$     float mcScale = (float) Minecraft.getInstance().getWindow().getGuiScale();
    //$$     float reScale = sm.getGuiScale();
    //$$     if (mcScale == 0 || reScale == 0) return;
    //$$     float renderScale = reScale / mcScale;
    //$$     float mouseScale = mcScale / reScale;
    //$$
    //$$     RematrixMcContext ctx = new RematrixMcContext(poseStack, renderScale);
    //$$     MinecraftDrawContextAdapter libCtx = new MinecraftDrawContextAdapter(ctx);
    //$$
    //$$     poseStack.pushPose();
    //$$     poseStack.scale(renderScale, renderScale, 1f);
    //$$     sm.render(libCtx, (int) (mouseX * mouseScale), (int) (mouseY * mouseScale), deltaSeconds);
    //$$     sm.processTasks();
    //$$     poseStack.popPose();
    //$$ }
    //#endif

    //#if MC >= 1.21.9
    @Override
    public boolean mouseClicked(net.minecraft.client.input.MouseButtonEvent event, boolean bl) {
        double sf = getInputScale();
        boolean handled = sm.mouseClicked(event.x() * sf, event.y() * sf, event.button());
        return handled || super.mouseClicked(event, bl);
    }
    //#else
    //$$ @Override
    //$$ public boolean mouseClicked(double mouseX, double mouseY, int button) {
    //$$     double sf = getInputScale();
    //$$     boolean handled = sm.mouseClicked(mouseX * sf, mouseY * sf, button);
    //$$     return handled || super.mouseClicked(mouseX, mouseY, button);
    //$$ }
    //#endif

    //#if MC >= 1.21.9
    @Override
    public boolean mouseReleased(net.minecraft.client.input.MouseButtonEvent event) {
        double sf = getInputScale();
        boolean handled = sm.mouseReleased(event.x() * sf, event.y() * sf, event.button());
        return handled || super.mouseReleased(event);
    }
    //#else
    //$$ @Override
    //$$ public boolean mouseReleased(double mouseX, double mouseY, int button) {
    //$$     double sf = getInputScale();
    //$$     boolean handled = sm.mouseReleased(mouseX * sf, mouseY * sf, button);
    //$$     return handled || super.mouseReleased(mouseX, mouseY, button);
    //$$ }
    //#endif

    //#if MC >= 1.21.9
    @Override
    public boolean mouseDragged(net.minecraft.client.input.MouseButtonEvent event, double deltaX, double deltaY) {
        double sf = getInputScale();
        boolean handled = sm.mouseDragged(event.x() * sf, event.y() * sf, event.button(), deltaX * sf, deltaY * sf);
        return handled || super.mouseDragged(event, deltaX, deltaY);
    }
    //#else
    //$$ @Override
    //$$ public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
    //$$     double sf = getInputScale();
    //$$     boolean handled = sm.mouseDragged(mouseX * sf, mouseY * sf, button, deltaX * sf, deltaY * sf);
    //$$     return handled || super.mouseDragged(mouseX, mouseY, button, deltaX, deltaY);
    //$$ }
    //#endif

    //#if MC >= 1.20.3
    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        double sf = getInputScale();
        boolean handled = sm.mouseScrolled(mouseX * sf, mouseY * sf, horizontalAmount, verticalAmount);
        return handled || super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }
    //#endif
    //#if MC < 1.20.3
    //$$ @Override
    //$$ public boolean mouseScrolled(double mouseX, double mouseY, double amount) {
    //$$     double sf = getInputScale();
    //$$     boolean handled = sm.mouseScrolled(mouseX * sf, mouseY * sf, 0.0, amount);
    //$$     return handled || super.mouseScrolled(mouseX, mouseY, amount);
    //$$ }
    //#endif

    //#if MC >= 1.21.9
    @Override
    public boolean keyPressed(net.minecraft.client.input.KeyEvent keyEvent) {
        boolean handled = sm.keyPressed(keyEvent.key(), keyEvent.scancode(), keyEvent.modifiers());
        return handled || super.keyPressed(keyEvent);
    }
    //#else
    //$$ @Override
    //$$ public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
    //$$     boolean handled = sm.keyPressed(keyCode, scanCode, modifiers);
    //$$     return handled || super.keyPressed(keyCode, scanCode, modifiers);
    //$$ }
    //#endif

    //#if MC >= 1.21.9
    @Override
    public boolean charTyped(net.minecraft.client.input.CharacterEvent characterEvent) {
        int codepoint = characterEvent.codepoint();
        boolean handled = false;
        char[] chars = Character.toChars(codepoint);
        for (char chr : chars) {
            handled = sm.charTyped(chr, characterEvent.modifiers()) || handled;
        }
        return handled || super.charTyped(characterEvent);
    }
    //#else
    //$$ @Override
    //$$ public boolean charTyped(char chr, int modifiers) {
    //$$     boolean handled = sm.charTyped(chr, modifiers);
    //$$     return handled || super.charTyped(chr, modifiers);
    //$$ }
    //#endif

    //#if MC >= 1.21.9
    @Override
    public boolean keyReleased(net.minecraft.client.input.KeyEvent keyEvent) {
        boolean handled = sm.keyReleased(keyEvent.key(), keyEvent.scancode(), keyEvent.modifiers());
        return handled || super.keyReleased(keyEvent);
    }
    //#else
    //$$ @Override
    //$$ public boolean keyReleased(int keyCode, int scanCode, int modifiers) {
    //$$     boolean handled = sm.keyReleased(keyCode, scanCode, modifiers);
    //$$     return handled || super.keyReleased(keyCode, scanCode, modifiers);
    //$$ }
    //#endif

    @Override
    public void onClose() {
        super.onClose();
        if (skipCloseCleanup) {
            skipCloseCleanup = false;
            return;
        }
        restudio.rescreen.ui.core.Screen current = sm.getCurrentScreen();
        if (current != null) current.removed();
        sm.setScreen(null);
        suspendedScreen = null;
    }

    public void onDisplayed() {
    }

    public restudio.rescreen.ui.core.Screen getScreen() {
        return libScreen;
    }

    public static boolean toggleScreen() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen instanceof RematrixScreen wrapper) {
            suspendedScreen = wrapper.getScreen();
            skipCloseCleanup = true;
            mc.setScreen(null);
            return true;
        }
        if (suspendedScreen == null) return false;
        mc.setScreen(new RematrixScreen(suspendedScreen));
        return true;
    }

    private double getInputScale() {
        return Minecraft.getInstance().getWindow().getGuiScale();
    }
}
