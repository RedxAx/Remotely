package redxax.oxy.remotely.mixin;

import dev.deftu.omnicore.api.client.render.ImmediateScreenRenderer;
import dev.deftu.omnicore.api.client.render.OmniRenderingContext;
import net.minecraft.client.Minecraft;
//#if MC >= 1.20.1
//$$ import net.minecraft.client.gui.GuiGraphics;
//#else
import com.mojang.blaze3d.vertex.PoseStack;
//#endif
import net.minecraft.client.gui.screens.Screen;
//#if MC >= 1.21.9
//$$ import net.minecraft.client.input.KeyEvent;
//#endif
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import redxax.oxy.remotely.adapters.ICustomWidgetHolder;
import redxax.oxy.remotely.adapters.MinecraftDrawContextAdapter;
import redxax.oxy.remotely.config.Config;
import redxax.oxy.remotely.servers.ReverseProxyManager;
import redxax.oxy.remotely.ui.tests.ContainerTestingScreen;
import redxax.oxy.remotely.ui.tests.WidgetsTestingScreen;
import redxax.oxy.remotely.util.CursorUtils;
import restudio.rescreen.ui.core.ScreenManager;
import restudio.rescreen.ui.core.Widget;
import restudio.rescreen.util.Notification;

import java.util.ArrayList;
import java.util.List;

import static redxax.oxy.remotely.config.Config.enableDebugTools;

@Mixin(value = Screen.class)
public abstract class ScreenMixin implements ICustomWidgetHolder {

    @Shadow public int width;
    @Shadow public int height;

    @Unique
    private final List<Widget> remotely$customWidgets = new ArrayList<>();

    @Unique
    private boolean remotely$wasMouseDown = false;

    @Override
    public void remotely$addWidget(Widget widget) {
        this.remotely$customWidgets.add(widget);
    }

    @Override
    public void remotely$clearWidgets() {
        this.remotely$customWidgets.clear();
    }

    @Inject(method = "render", at = @At("TAIL"))
    //#if MC >= 1.20.1
    //$$ private void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float f, CallbackInfo ci) {
    //#else
    private void render(PoseStack guiGraphics, int mouseX, int mouseY, float f, CallbackInfo ci) {
    //#endif
        restudio.rescreen.config.Config.tickTime();
        CursorUtils.tick();
        Config.globalCursorAnimatedColor = CursorUtils.blendColor();

        if (!remotely$customWidgets.isEmpty()) {
            remotely$handleInput(mouseX, mouseY);

            OmniRenderingContext ctx = OmniRenderingContext.from(guiGraphics);
            ImmediateScreenRenderer.initialize();
            ImmediateScreenRenderer.render(ctx, () -> {
                MinecraftDrawContextAdapter adapter = new MinecraftDrawContextAdapter(ctx);
                for (Widget widget : remotely$customWidgets) {
                    widget.render(adapter, mouseX, mouseY, f);
                }
            });
        }
    }

    @Unique
    private void remotely$handleInput(int mouseX, int mouseY) {
        long handle = Minecraft.getInstance().getWindow().getWindow();
        boolean mouseDown = GLFW.glfwGetMouseButton(handle, GLFW.GLFW_MOUSE_BUTTON_LEFT) == GLFW.GLFW_PRESS;

        if (mouseDown && !remotely$wasMouseDown) {
            for (int i = remotely$customWidgets.size() - 1; i >= 0; i--) {
                Widget widget = remotely$customWidgets.get(i);
                if (widget.isVisible() && widget.isActive() && widget.isMouseOver(mouseX, mouseY)) {
                    if (widget.mouseClicked(mouseX, mouseY, 0)) {
                        break;
                    }
                }
            }
        }
        remotely$wasMouseDown = mouseDown;
    }

    @Unique
    private void remotely$handleDebugKeys(int key, int modifiers) {
        boolean ctrl = (modifiers & GLFW.GLFW_MOD_CONTROL) != 0;
        boolean shift = (modifiers & GLFW.GLFW_MOD_SHIFT) != 0;
        boolean alt = (modifiers & GLFW.GLFW_MOD_ALT) != 0;
        boolean all = alt && shift && ctrl;

        if (key == GLFW.GLFW_KEY_D && all) {
            enableDebugTools = !enableDebugTools;
            new Notification("Toggled Debug Tools To " + enableDebugTools, Notification.Type.INFO);
        }
        if (!enableDebugTools) return;
        if (key == GLFW.GLFW_KEY_T && ctrl) {
            ScreenManager.getInstance().setScreen(new WidgetsTestingScreen());
        }
        if (key == GLFW.GLFW_KEY_C && ctrl) {
            ScreenManager.getInstance().setScreen(new ContainerTestingScreen());
        }
        if (key == GLFW.GLFW_KEY_P && all) {
            ReverseProxyManager.listActivePorts();
        }
    }

    @Inject(method = "keyPressed", at = @At("HEAD"))
    //#if MC >= 1.21.9
    //$$ private void keyPressed(KeyEvent keyEvent, CallbackInfoReturnable<Boolean> cir) {
    //$$     remotely$handleDebugKeys(keyEvent.key(), keyEvent.modifiers());
    //$$ }
    //#else
    private void keyPressed(int keyCode, int scanCode, int modifiers, CallbackInfoReturnable<Boolean> cir) {
        remotely$handleDebugKeys(keyCode, modifiers);
    }
    //#endif
}
