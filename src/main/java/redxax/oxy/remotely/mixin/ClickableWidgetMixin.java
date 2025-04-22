package redxax.oxy.remotely.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.PlainTextButton;
import net.minecraft.client.gui.components.*;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import redxax.oxy.remotely.Render;
import redxax.oxy.remotely.config.Config;
import redxax.oxy.remotely.mixin.accessor.ITextIconButtonWidget;
import redxax.oxy.remotely.mixin.accessor.SliderWidgetAccessor;

import java.awt.image.BufferedImage;

import static redxax.oxy.remotely.Render.drawSlider;

@Mixin(AbstractWidget.class)
public abstract class ClickableWidgetMixin {
    @Shadow protected int width;
    @Shadow protected int height;
    @Shadow public abstract int getX();
    @Shadow public abstract int getY();
    @Shadow public abstract Component getMessage();
    @Shadow public abstract boolean isMouseOver(double mouseX, double mouseY);
    @Shadow public abstract boolean isFocused();
    @Shadow public abstract int getWidth();
    @Shadow public abstract int getHeight();
    @Shadow public boolean visible;
    @Shadow public boolean active;

    @Inject(method = "render", at = @At("HEAD"), cancellable = true)
    private void render(GuiGraphics context, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        if (!Config.redesignMainMenu) return;
        Minecraft mc = Minecraft.getInstance();
        boolean hovered = isMouseOver(mouseX, mouseY);

        if ((Object)this instanceof AbstractSliderButton widget) {
            double value = ((SliderWidgetAccessor) widget).getValue();
            drawSlider(context, mc, getX(), getY(), getMessage().getString(), value, hovered, isFocused(), mouseX, mouseY, "", width, height);
            ci.cancel();
            return;
        }

        if ((Object)this instanceof ImageWidget w) {
            if (visible) {
                ITextIconButtonWidget acc = (ITextIconButtonWidget) w;
                ResourceLocation tex = acc.getTexture();
                int tw = acc.getTextureWidth();
                int th = acc.getTextureHeight();
                Render.drawSquareButton(context, getX(), getY(), mc, hovered, mouseX, mouseY, getMessage().getString() + (Config.enableDebugTools ? " §6Textured" : ""), null);
            }
            ci.cancel();
            return;
        }

        if (!((Object)this instanceof Button) && !((Object)this instanceof CycleButton)) {
            return;

        } else if (((Object)this instanceof PlainTextButton)) return;

        if (getWidth() == getHeight() && visible) {
            Render.drawSquareButton(context, getX(), getY(), mc, hovered, mouseX, mouseY, getMessage().getString(), null);
            ci.cancel();
        } else if (visible) {
            Render.drawCustomButton(context, getX(), getY(), getMessage().getString(), mc, hovered, false, true, isFocused(), active, getWidth(), getHeight() == 20 ? 18 : getHeight(), Config.globalTextColor, Config.accentHoverColor, mouseX, mouseY, "");
            ci.cancel();
        }
    }
}
