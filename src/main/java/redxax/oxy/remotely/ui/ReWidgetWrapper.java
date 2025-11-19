package redxax.oxy.remotely.ui;

import dev.deftu.omnicore.api.client.render.OmniRenderingContext;
import dev.deftu.omnicore.api.client.render.OmniResolution;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.InputWithModifiers;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.NotNull;
import redxax.oxy.remotely.adapters.MinecraftDrawContextAdapter;
import restudio.rescreen.ui.core.Widget;

public class ReWidgetWrapper extends AbstractButton {
    private final Widget wrapped;

    public ReWidgetWrapper(Widget wrapped) {
        super(wrapped.getX(), wrapped.getY(), wrapped.getWidth(), wrapped.getHeight(), Component.literal(wrapped.getMessage()));
        this.wrapped = wrapped;
    }

    public Widget getWrapped() {
        return this.wrapped;
    }

    @Override
    public void onPress(InputWithModifiers inputWithModifiers) {

    }

    @Override
    public void renderWidget(GuiGraphics context, int mouseX, int mouseY, float delta) {
        MinecraftDrawContextAdapter adapter = new MinecraftDrawContextAdapter(OmniRenderingContext.from(context));
        wrapped.renderWidget(adapter, mouseX, mouseY, delta);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent button, boolean doubleClick) {
        double scale = OmniResolution.getScaleFactor();
        return wrapped.mouseClicked(button.x() * scale, button.y() * scale, button.button());
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent button) {
        double scale = OmniResolution.getScaleFactor();
        return wrapped.mouseReleased(button.x() * scale, button.y() * scale, button.button());
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent button, double deltaX, double deltaY) {
        double scale = OmniResolution.getScaleFactor();
        return wrapped.mouseDragged(button.x() * scale, button.y() * scale, button.button(), deltaX * scale, deltaY * scale);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        double scale = OmniResolution.getScaleFactor();
        return wrapped.mouseScrolled((int) (mouseX * scale), (int) (mouseY * scale), verticalAmount);
    }

    @Override
    public boolean keyPressed(KeyEvent key) {
        return wrapped.keyPressed(key.key(), key.scancode(), key.modifiers());
    }

    @Override
    public boolean keyReleased(KeyEvent key) {
        return wrapped.keyReleased(key.key(), key.scancode(), key.modifiers());
    }

    @Override
    public boolean charTyped(CharacterEvent key) {
        return wrapped.charTyped((char) key.codepoint(), key.modifiers());
    }

    @Override
    public void setFocused(boolean focused) {
        wrapped.setFocused(focused);
    }

    @Override protected void updateWidgetNarration(NarrationElementOutput narrationElementOutput) {}

    @Override
    public boolean isFocused() {
        return false;
    }
}
