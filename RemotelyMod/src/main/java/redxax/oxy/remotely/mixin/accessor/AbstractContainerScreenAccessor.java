package redxax.oxy.remotely.mixin.accessor;

import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(AbstractContainerScreen.class)
public interface AbstractContainerScreenAccessor {
    @Accessor("leftPos")
    int remotely$getLeftPos();

    @Accessor("topPos")
    int remotely$getTopPos();

    @Accessor("imageWidth")
    int remotely$getImageWidth();
}
