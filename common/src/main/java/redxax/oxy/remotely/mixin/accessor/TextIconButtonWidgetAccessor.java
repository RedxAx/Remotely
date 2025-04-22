package redxax.oxy.remotely.mixin.accessor;

import net.minecraft.client.gui.widget.TextIconButtonWidget;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(TextIconButtonWidget.class)
public interface TextIconButtonWidgetAccessor {
    @Accessor("texture")
    Identifier getTexture();

    @Accessor("textureWidth")
    int getTextureWidth();

    @Accessor("textureHeight")
    int getTextureHeight();
}
