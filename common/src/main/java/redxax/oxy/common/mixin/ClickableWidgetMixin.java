package redxax.oxy.common.mixin;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.widget.*;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.At;
import redxax.oxy.common.Render;
import redxax.oxy.common.config.Config;
import redxax.oxy.common.mixin.accessor.SliderWidgetAccessor;

import java.awt.image.BufferedImage;

import static redxax.oxy.common.Render.drawSlider;
import static redxax.oxy.common.config.Config.redesignMainMenu;
import static redxax.oxy.common.util.DevUtil.devPrint;
import static redxax.oxy.common.util.ImageUtil.loadResourceIcon;

@Mixin(ClickableWidget.class)
public abstract class ClickableWidgetMixin {
    @Shadow protected int width;
    @Shadow protected int height;
    @Shadow public abstract int getX();
    @Shadow public abstract int getY();
    @Shadow public abstract Text getMessage();
    @Shadow public abstract boolean isMouseOver(double mouseX, double mouseY);
    @Shadow public abstract boolean isFocused();
    @Shadow public abstract int getWidth();
    @Shadow public abstract int getHeight();

    @Inject(method = "render", at = @At("HEAD"), cancellable = true)
    protected void render(DrawContext context, int mouseX, int mouseY, float delta, CallbackInfo ci) throws Exception {
        if (!redesignMainMenu) {
            return;
        }
        MinecraftClient mc = MinecraftClient.getInstance();
        boolean hovered = this.isMouseOver(mouseX, mouseY);
        if ((Object)this instanceof SliderWidget widget) {
            double value = ((SliderWidgetAccessor) widget).getValue();
            drawSlider(context, mc, getX(), getY(), getMessage().getString(), value, hovered, isFocused(), mouseX, mouseY, "", this.width, this.height);
            ci.cancel();
            return;
        } else if ((Object) this instanceof TextIconButtonWidget) {
            BufferedImage image = null;
            try {
                if (getMessage().toString().equals(Text.translatable("options.accessibility").toString()))
                    image = loadResourceIcon("/assets/remotely/icons/accessibility.png");
                else if (getMessage().toString().equals(Text.translatable("options.language").toString()))
                    image = loadResourceIcon("/assets/minecraft/textures/gui/sprites/icon/language.png");
            } catch (Exception e) {
                devPrint("Failed to load icon: " + e.getMessage());
            }

            Render.drawSquareButton(context, getX(), getY(), mc, hovered, mouseX, mouseY, getMessage().getString(), image);
            ci.cancel();
            return;
        }

        if (!((Object)this instanceof ButtonWidget) && !((Object)this instanceof CyclingButtonWidget || (Object)this instanceof PressableTextWidget)) {
            return;
        }
        if (getWidth() == getHeight()) {
            BufferedImage image = null;
            Render.drawSquareButton(context, getX(), getY(), mc, hovered, mouseX, mouseY, getMessage().getString(), image);
            return;
        } else {
            Render.drawCustomButton(context, this.getX(), this.getY(), getMessage().getString(), mc, hovered, false, true, isFocused(), getWidth(), getHeight() == 20 ? 18 : getHeight(), Config.globalTextColor, Config.accentHoverColor, mouseX, mouseY, "");
        }
        ci.cancel();
    }


}
