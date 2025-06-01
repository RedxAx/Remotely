package redxax.oxy.remotely.mixin.accessor;
//? =1.20.1 {
import net.minecraft.client.gui.widget.TexturedButtonWidget;
//?} else {
/*import net.minecraft.client.gui.widget.TextIconButtonWidget;
*///?}
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
//? =1.20.1 {
@Mixin(TexturedButtonWidget.class)
//?} else {
/*@Mixin(TextIconButtonWidget.class)
*///?}
public interface ImageWidgetTextureAccessor {
    @Accessor("texture")
    Identifier getTexture();

    @Accessor("textureWidth")
    int getTextureWidth();

    @Accessor("textureHeight")
    int getTextureHeight();
}