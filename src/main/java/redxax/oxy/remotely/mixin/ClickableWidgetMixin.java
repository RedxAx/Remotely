package redxax.oxy.remotely.mixin;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.client.gui.widget.CyclingButtonWidget;
import net.minecraft.client.gui.widget.IconWidget;
import net.minecraft.client.gui.widget.PressableTextWidget;
import net.minecraft.client.gui.widget.SliderWidget;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import redxax.oxy.remotely.Render;
import redxax.oxy.remotely.config.Config;
import redxax.oxy.remotely.mixin.accessor.ImageWidgetTextureAccessor;
import redxax.oxy.remotely.mixin.accessor.SliderWidgetAccessor;

import static redxax.oxy.remotely.Render.*;

@Mixin(ClickableWidget.class)
public abstract class ClickableWidgetMixin {
    @Shadow protected int width;
    @Shadow protected int height;
    @Shadow public abstract int getX();
    @Shadow public abstract int getY();
    @Shadow public abstract Text getMessage();
    @Shadow private int navigationOrder;
    @Shadow public abstract boolean isMouseOver(double mouseX, double mouseY);
    @Shadow public abstract boolean isFocused();
    @Shadow public abstract int getWidth();
    @Shadow public abstract int getHeight();
    @Shadow public boolean visible;
    @Shadow public boolean active;

    @Shadow private Text message;

    @Inject(method = "render", at = @At("HEAD"), cancellable = true)
    private void render(DrawContext context, int mouseX, int mouseY, float delta, CallbackInfo ci) {
//        if (!Config.redesignMainMenu) return;
        return;
//        MinecraftClient mc = MinecraftClient.getInstance();
//        boolean hovered = isMouseOver(mouseX, mouseY);
//
//        if ((Object)this instanceof SliderWidget widget) {
//            double value = ((SliderWidgetAccessor) widget).getValue();
//            drawSlider(context, mc, getX(), getY(), getMessage().getString(), value, hovered, isFocused(), mouseX, mouseY, "", width, height);
//            ci.cancel();
//            return;
//        }
//
//        if ((Object)this instanceof IconWidget w) {
//            if (visible) {
//                ImageWidgetTextureAccessor acc = (ImageWidgetTextureAccessor) w;
//                Identifier tex = acc.getTexture();
//                int tw = acc.getTextureWidth();
//                int th = acc.getTextureHeight();
////                Render.drawSquareButton(context, getX(), getY(), mc, hovered, mouseX, mouseY, getMessage().getString() + (Config.enableDebugTools ? " §6Textured" : ""), tex, tw, th);
//            }
//            ci.cancel();
//            return;
//        }
//
//        if (!((Object)this instanceof ButtonWidget) && !((Object)this instanceof CyclingButtonWidget)) {
//            return;
//
//        } else if (((Object)this instanceof PressableTextWidget)) return;
//
//        if (getWidth() == getHeight() && visible) {
//            Render.drawSquareButton(context, getX(), getY(), mc, hovered, mouseX, mouseY, getMessage().getString(), null);
//            ci.cancel();
//        } else if (visible) {
//            Render.drawCustomButton(context, getX(), getY(), getMessage().getString(), mc, hovered, false, true, isFocused(), active, getWidth(), getHeight() == 20 ? 18 : getHeight(), Config.getTextColor(message.hashCode() + navigationOrder, hovered, true, active, false, false, false), Config.getTextColor(message.hashCode() + navigationOrder, hovered, true, active, false, false, false), mouseX, mouseY, "");
//            ci.cancel();
//        }
    }
}
