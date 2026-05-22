package redxax.oxy.remotely.mixin;

import net.minecraft.client.Minecraft;
//#if MC >= 26.1
//$$ import net.minecraft.client.gui.GuiGraphicsExtractor;
//#endif
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
//#if MC >= 1.20.1 && MC < 26.1
import net.minecraft.client.gui.GuiGraphics;
//#else
//$$ import com.mojang.blaze3d.vertex.PoseStack;
//#endif
import net.minecraft.client.gui.screens.Screen;
//#if MC >= 26.1
//$$ import net.minecraft.client.input.KeyEvent;
//#endif
//#if MC >= 1.21.9 && MC < 26.1
import net.minecraft.client.input.KeyEvent;
//#endif
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import redxax.oxy.remotely.adapters.ICustomWidgetHolder;
import redxax.oxy.remotely.adapters.MinecraftDrawContextAdapter;
import redxax.oxy.remotely.config.Config;
import redxax.oxy.remotely.RemotelyClient;
import redxax.oxy.remotely.rematrix.mc.RematrixContext;
import redxax.oxy.remotely.rematrix.mc.RematrixScale;
import redxax.oxy.remotely.rematrix.mc.RematrixScreen;
import redxax.oxy.remotely.servers.ReProxyManager;
import redxax.oxy.remotely.ui.tests.ContainerTestingScreen;
import redxax.oxy.remotely.ui.tests.WidgetsTestingScreen;
import redxax.oxy.remotely.util.CursorUtils;
import redxax.oxy.remotely.util.ScreenInitHelper;
import restudio.rescreen.Main;
import restudio.rescreen.ui.MouseCursor;
import restudio.rescreen.ui.core.ScreenManager;
import restudio.rescreen.ui.core.Widget;
import restudio.rescreen.ui.widgets.AnimatedWidget;
import restudio.rescreen.ui.widgets.IconButton;
import restudio.rescreen.util.Notification;

import java.util.ArrayList;
import java.util.List;

import static redxax.oxy.remotely.config.Config.enableDebugTools;

@Mixin(value = Screen.class)
public abstract class ScreenMixin implements ICustomWidgetHolder {

    @Unique
    private final List<Widget> remotely$customWidgets = new ArrayList<>();

    @Unique
    private boolean remotely$wasMouseDown = false;

    @Unique
    private IconButton remotely$editOverlayButton;

    @Unique
    private int remotely$editOverlayRevision = -1;

    @Unique
    private boolean remotely$overlayEditable;

    @Unique
    private String remotely$overlayServerId;

    @Unique
    private String remotely$overlayGuiId;

    @Unique
    private String remotely$overlayFlowId;

    @Unique
    private int remotely$overlayStateRevision = -1;

    @Override
    public void remotely$addWidget(Widget widget) {
        this.remotely$customWidgets.add(widget);
    }

    @Override
    public void remotely$clearWidgets() {
        this.remotely$customWidgets.clear();
    }

    @Inject(
    //#if MC >= 26.1
    //$$     method = "extractRenderState",
    //#else
        method = "render",
    //#endif
        at = @At("TAIL")
    )
    //#if MC >= 26.1
    //$$ private void render(GuiGraphicsExtractor guiGraphics, int mouseX, int mouseY, float f, CallbackInfo ci) {
    //#elseif MC >= 1.20.1
    private void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float f, CallbackInfo ci) {
    //#else
    //$$ private void render(PoseStack guiGraphics, int mouseX, int mouseY, float f, CallbackInfo ci) {
    //#endif
        restudio.rescreen.config.Config.tickTime();
        CursorUtils.tick();
        Config.globalCursorAnimatedColor = CursorUtils.blendColor();

        ScreenInitHelper.ensureTitleScreenButtons((Screen) (Object) this);
        remotely$updateEditOverlay();

        boolean hasWidgets = !remotely$customWidgets.isEmpty();
        boolean renderCursor = restudio.rescreen.config.Config.customMouse && !(((Object) this) instanceof RematrixScreen);

        boolean renderPinned = !(((Object) this) instanceof RematrixScreen);

        if (hasWidgets || renderCursor || renderPinned) {
            MouseCursor.beginFrame();

            //#if NEOFORGE && MC < 1.21.10
            //$$ Main.setWindow(Minecraft.getInstance().getWindow().getWindow());
            //#elseif MC >= 1.21.6 || MC >= 26.1
            Main.setWindow(Minecraft.getInstance().getWindow().handle());
            //#else
            //$$ Main.setWindow(Minecraft.getInstance().getWindow().getWindow());
            //#endif

            if (hasWidgets) {
                remotely$handleInput(mouseX, mouseY);
            }

            //#if MC >= 26.1
            //$$ var pose = guiGraphics.pose();
            //$$ pose.pushMatrix();
            //$$ pose.identity();
            //#endif
            //#if MC >= 1.21.6 && MC < 26.1
            var pose = guiGraphics.pose();
            pose.pushMatrix();
            pose.identity();
            //#endif
            //#if MC < 1.21.6 && MC < 26.1
            //$$ guiGraphics.pose().pushPose();
            //$$ guiGraphics.pose().last().pose().identity();
            //#endif

            //#if MC >= 26.1
            //$$ RematrixContext ctx = new RematrixContext(guiGraphics);
            //#endif
            //#if MC >= 1.20.1 && MC < 26.1
            RematrixContext ctx = new RematrixContext(guiGraphics);
            //#endif
            //#if MC < 1.20.1
            //$$ RematrixMcContext ctx = new RematrixMcContext(guiGraphics);
            //#endif
            MinecraftDrawContextAdapter adapter = new MinecraftDrawContextAdapter(ctx);
            if (hasWidgets) {
                for (Widget widget : remotely$customWidgets) {
                    widget.render(adapter, mouseX, mouseY, f);
                }
                for (Widget widget : remotely$customWidgets) {
                    if (widget instanceof AnimatedWidget animatedWidget) {
                        animatedWidget.renderHintOverlay(adapter);
                    }
                }
            }
            if (renderPinned) {
                ScreenManager sm = ScreenManager.getInstance();
                Minecraft minecraft = Minecraft.getInstance();
                RematrixScale.ensureConfigured(minecraft);
                int windowWidth = minecraft.getWindow().getWidth();
                int windowHeight = minecraft.getWindow().getHeight();
                sm.updateDimensions(windowWidth, windowHeight);
                float mcScale = (float) minecraft.getWindow().getGuiScale();
                float reScale = sm.getGuiScale();
                if (mcScale != 0 && reScale != 0) {
                    float renderScale = reScale / mcScale;
                    float mouseScale = mcScale / reScale;
                    //#if MC >= 26.1
                    //$$ pose.pushMatrix();
                    //$$ pose.scale(renderScale, renderScale);
                    //#endif
                    //#if MC >= 1.21.6 && MC < 26.1
                    pose.pushMatrix();
                    pose.scale(renderScale, renderScale);
                    //#endif
                    //#if MC < 1.21.6 && MC < 26.1
                    //$$ guiGraphics.pose().pushPose();
                    //$$ guiGraphics.pose().scale(renderScale, renderScale, 1f);
                    //#endif
                    //#if MC >= 26.1
                    //$$ RematrixContext pinnedCtx = new RematrixContext(guiGraphics, renderScale);
                    //#endif
                    //#if MC >= 1.20.1 && MC < 26.1
                    RematrixContext pinnedCtx = new RematrixContext(guiGraphics, renderScale);
                    //#endif
                    //#if MC < 1.20.1
                    //$$ RematrixMcContext pinnedCtx = new RematrixMcContext(guiGraphics, renderScale);
                    //#endif
                    MinecraftDrawContextAdapter pinnedAdapter = new MinecraftDrawContextAdapter(pinnedCtx);
                    sm.renderPinnedInGameWindows(pinnedAdapter, (int) (mouseX * mouseScale), (int) (mouseY * mouseScale), f);
                    sm.processTasks();
                    //#if MC >= 26.1
                    //$$ pose.popMatrix();
                    //#endif
                    //#if MC >= 1.21.6 && MC < 26.1
                    pose.popMatrix();
                    //#endif
                    //#if MC < 1.21.6 && MC < 26.1
                    //$$ guiGraphics.pose().popPose();
                    //#endif
                }
            }
            if (renderCursor) {
                MouseCursor.updateAndRender(adapter, mouseX, mouseY);
            }

            //#if MC >= 26.1
            //$$ pose.popMatrix();
            //#endif
            //#if MC >= 1.21.6 && MC < 26.1
            pose.popMatrix();
            //#endif
            //#if MC < 1.21.6 && MC < 26.1
            //$$ guiGraphics.pose().popPose();
            //#endif
        }
    }

    @Unique
    private void remotely$handleInput(int mouseX, int mouseY) {
        //#if NEOFORGE && MC < 1.21.10
        //$$ long handle = Minecraft.getInstance().getWindow().getWindow();
        //#elseif MC >= 1.21.6 || MC >= 26.1 || MC == 1.21.10
        long handle = Minecraft.getInstance().getWindow().handle();
        //#else
        //$$ long handle = Minecraft.getInstance().getWindow().getWindow();
        //#endif
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
    private void remotely$updateEditOverlay() {
        boolean hasState = remotely$refreshOverlayState();
        boolean show = hasState
            && remotely$overlayEditable
            && remotely$overlayGuiId != null
            && !remotely$overlayGuiId.isBlank()
            && ((Object) this) instanceof AbstractContainerScreen;

        if (!show) {
            if (remotely$editOverlayButton != null) {
                remotely$clearWidgets();
                remotely$editOverlayButton = null;
                remotely$editOverlayRevision = -1;
            }
            return;
        }

        if (remotely$editOverlayButton == null || remotely$editOverlayRevision != remotely$overlayStateRevision) {
            remotely$clearWidgets();
            remotely$editOverlayButton = new IconButton.Builder()
                .imagePath("edit.png")
                .size(18, 18)
                .iconSize(16)
                .onClick(() -> {
                    if (RemotelyClient.INSTANCE == null || RemotelyClient.INSTANCE.getFlowManager() == null) {
                        return;
                    }
                    if (!remotely$refreshOverlayState() || remotely$overlayGuiId == null || remotely$overlayGuiId.isBlank()) {
                        return;
                    }
                    RemotelyClient.INSTANCE.getFlowManager().openGuiDesigner(remotely$overlayServerId, null, remotely$overlayGuiId, this);
                })
                .build();
            remotely$editOverlayButton.entranceAnimationEnabled = false;
            remotely$addWidget(remotely$editOverlayButton);
            remotely$editOverlayRevision = remotely$overlayStateRevision;
        }

        int[] containerBounds = remotely$getContainerBounds();
        int x;
        int y;
        int screenWidth = Minecraft.getInstance().getWindow().getGuiScaledWidth();
        if (containerBounds != null) {
            x = containerBounds[0] + containerBounds[2] + 6;
            y = containerBounds[1];
            if (x + 18 > screenWidth - 2) {
                x = Math.max(6, screenWidth - 24);
            }
            y = Math.max(6, y);
        } else {
            x = Math.max(6, screenWidth - 24);
            y = 6;
        }
        remotely$editOverlayButton.setPosition(x, y);
        remotely$editOverlayButton.setWidth(18);
        remotely$editOverlayButton.setHeight(18);
    }

    @Unique
    private boolean remotely$refreshOverlayState() {
        if (RemotelyClient.INSTANCE == null || RemotelyClient.INSTANCE.getFlowManager() == null) {
            return remotely$refreshOverlayStateFallback();
        }
        Object manager = RemotelyClient.INSTANCE.getFlowManager();
        try {
            Class<?> cls = manager.getClass();
            remotely$overlayEditable = (boolean) cls.getMethod("isOverlayEditable").invoke(manager);
            remotely$overlayServerId = (String) cls.getMethod("getOverlayServerId").invoke(manager);
            remotely$overlayGuiId = (String) cls.getMethod("getOverlayGuiId").invoke(manager);
            remotely$overlayFlowId = (String) cls.getMethod("getOverlayFlowId").invoke(manager);
            remotely$overlayStateRevision = (int) cls.getMethod("getOverlayRevision").invoke(manager);
            return true;
        } catch (Exception ignored) {
            return remotely$refreshOverlayStateFallback();
        }
    }

    @Unique
    private boolean remotely$refreshOverlayStateFallback() {
        try {
            Class<?> stateClass = Class.forName("redxax.oxy.remotely.flow.ui.GuiEditOverlayState");
            Object snapshot = stateClass.getMethod("snapshot").invoke(null);
            if (snapshot == null) {
                return false;
            }
            Class<?> snapClass = snapshot.getClass();
            remotely$overlayEditable = (boolean) snapClass.getMethod("editable").invoke(snapshot);
            remotely$overlayServerId = (String) snapClass.getMethod("serverId").invoke(snapshot);
            remotely$overlayGuiId = (String) snapClass.getMethod("guiId").invoke(snapshot);
            remotely$overlayFlowId = (String) snapClass.getMethod("flowId").invoke(snapshot);
            remotely$overlayStateRevision = (int) snapClass.getMethod("revision").invoke(snapshot);
            return true;
        } catch (Exception ignored) {
            return false;
        }
    }

    @Unique
    private int[] remotely$getContainerBounds() {
        if (!((Object) this instanceof AbstractContainerScreen)) {
            return null;
        }
        try {
            Class<?> cls = AbstractContainerScreen.class;
            java.lang.reflect.Field leftField = cls.getDeclaredField("leftPos");
            java.lang.reflect.Field topField = cls.getDeclaredField("topPos");
            java.lang.reflect.Field widthField = cls.getDeclaredField("imageWidth");
            leftField.setAccessible(true);
            topField.setAccessible(true);
            widthField.setAccessible(true);
            int leftPos = (int) leftField.get(this);
            int topPos = (int) topField.get(this);
            int imageWidth = (int) widthField.get(this);
            return new int[] { leftPos, topPos, imageWidth };
        } catch (Exception ignored) {
            return null;
        }
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
        if (key == GLFW.GLFW_KEY_T && all) {
            ScreenManager.getInstance().setScreen(new WidgetsTestingScreen());
        }
        if (key == GLFW.GLFW_KEY_C && all) {
            ScreenManager.getInstance().setScreen(new ContainerTestingScreen());
        }
        if (key == GLFW.GLFW_KEY_P && all) {
            new Notification("ReProxy Tunnels", String.join(", ", ReProxyManager.listActiveTunnels()), Notification.Type.INFO);
        }
    }

    @Inject(method = "keyPressed", at = @At("HEAD"))
    //#if MC >= 1.21.9 || MC >= 26.1
     private void keyPressed(KeyEvent keyEvent, CallbackInfoReturnable<Boolean> cir) {
          remotely$handleDebugKeys(keyEvent.key(), keyEvent.modifiers());
      }
    //#else
    //$$ private void keyPressed(int keyCode, int scanCode, int modifiers, CallbackInfoReturnable<Boolean> cir) {
    //$$     remotely$handleDebugKeys(keyCode, modifiers);
    //$$ }
    //#endif

    @Inject(method = "keyPressed", at = @At("HEAD"), cancellable = true)
    //#if MC >= 1.21.9 || MC >= 26.1
    private void keyPressedPinnedInGame(KeyEvent keyEvent, CallbackInfoReturnable<Boolean> cir) {
        if (ScreenManager.getInstance().keyPressedPinnedInGame(keyEvent.key(), keyEvent.scancode(), keyEvent.modifiers())) {
            cir.setReturnValue(true);
        }
    }
    //#else
    //$$ private void keyPressedPinnedInGame(int keyCode, int scanCode, int modifiers, CallbackInfoReturnable<Boolean> cir) {
    //$$     if (ScreenManager.getInstance().keyPressedPinnedInGame(keyCode, scanCode, modifiers)) {
    //$$         cir.setReturnValue(true);
    //$$     }
    //$$ }
    //#endif
}
