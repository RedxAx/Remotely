package redxax.oxy.remotely.mixin;

import net.minecraft.client.Minecraft;
//#if MC >= 1.21.1
import net.minecraft.client.DeltaTracker;
//#endif
//#if MC >= 26.1
//$$ import net.minecraft.client.gui.GuiGraphicsExtractor;
//#elseif MC >= 1.20.1
import net.minecraft.client.gui.GuiGraphics;
//#else
//$$ import com.mojang.blaze3d.vertex.PoseStack;
//#endif
import net.minecraft.client.gui.Gui;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import redxax.oxy.remotely.adapters.MinecraftDrawContextAdapter;
import redxax.oxy.remotely.rematrix.mc.RematrixContext;
import restudio.rescreen.Main;
import restudio.rescreen.ui.core.ScreenManager;

@Mixin(Gui.class)
public class GuiMixin {

    //#if MC >= 26.1
    //$$ @Inject(method = "extractRenderState", at = @At("TAIL"))
    //$$ private void render(GuiGraphicsExtractor guiGraphics, DeltaTracker deltaTracker, CallbackInfo ci) {
    //#else
    @Inject(method = "render", at = @At("TAIL"))
    //#if MC >= 1.21.1
    private void render(GuiGraphics guiGraphics, DeltaTracker deltaTracker, CallbackInfo ci) {
    //#elseif MC >= 1.20.1
    //$$ private void render(GuiGraphics guiGraphics, float tickDelta, CallbackInfo ci) {
    //#else
    //$$ private void render(PoseStack guiGraphics, float tickDelta, CallbackInfo ci) {
    //#endif
    //#endif
        Minecraft minecraft = Minecraft.getInstance();
        //#if MC >= 26.2
        //$$ if (minecraft.gui.screen() != null) return;
        //#else
        if (minecraft.screen != null) return;
        //#endif

        //#if NEOFORGE && MC < 1.21.10
        //$$ Main.setWindow(minecraft.getWindow().getWindow());
        //#elseif MC >= 1.21.6 || MC >= 26.1
        Main.setWindow(minecraft.getWindow().handle());
        //#else
        //$$ Main.setWindow(minecraft.getWindow().getWindow());
        //#endif

        ScreenManager sm = ScreenManager.getInstance();
        int windowWidth = minecraft.getWindow().getWidth();
        int windowHeight = minecraft.getWindow().getHeight();
        sm.updateDimensions(windowWidth, windowHeight);
        float mcScale = (float) minecraft.getWindow().getGuiScale();
        float reScale = sm.getGuiScale();
        if (mcScale == 0 || reScale == 0) return;

        float renderScale = reScale / mcScale;
        int mouseX = sm.getMouseX();
        int mouseY = sm.getMouseY();

        //#if MC >= 26.1
        //$$ RematrixContext ctx = new RematrixContext(guiGraphics, renderScale);
        //#elseif MC >= 1.20.1
        RematrixContext ctx = new RematrixContext(guiGraphics, renderScale);
        //#else
        //$$ RematrixMcContext ctx = new RematrixMcContext(guiGraphics, renderScale);
        //#endif
        MinecraftDrawContextAdapter adapter = new MinecraftDrawContextAdapter(ctx);

        //#if MC >= 26.1
        //$$ var pose = guiGraphics.pose();
        //$$ pose.pushMatrix();
        //$$ pose.scale(renderScale, renderScale);
        //#endif
        //#if MC >= 1.21.6 && MC < 26.1
        var pose = guiGraphics.pose();
        pose.pushMatrix();
        pose.scale(renderScale, renderScale);
        //#endif
        //#if MC >= 1.20.1 && MC < 1.21.6 && MC < 26.1
        //$$ var pose = guiGraphics.pose();
        //$$ pose.pushPose();
        //$$ pose.scale(renderScale, renderScale, 1f);
        //#endif
        //#if MC < 1.20.1
        //$$ guiGraphics.pushPose();
        //$$ guiGraphics.scale(renderScale, renderScale, 1f);
        //#endif

        //#if MC >= 1.21.1
        sm.renderPinnedInGameWindows(adapter, mouseX, mouseY, deltaTracker.getRealtimeDeltaTicks());
        //#elseif MC >= 1.20.1
        //$$ sm.renderPinnedInGameWindows(adapter, mouseX, mouseY, tickDelta);
        //#else
        //$$ sm.renderPinnedInGameWindows(adapter, mouseX, mouseY, tickDelta);
        //#endif
        sm.processTasks();

        //#if MC >= 26.1
        //$$ pose.popMatrix();
        //#endif
        //#if MC >= 1.21.6 && MC < 26.1
        pose.popMatrix();
        //#endif
        //#if MC >= 1.20.1 && MC < 1.21.6 && MC < 26.1
        //$$ pose.popPose();
        //#endif
        //#if MC < 1.20.1
        //$$ guiGraphics.popPose();
        //#endif
    }
}
