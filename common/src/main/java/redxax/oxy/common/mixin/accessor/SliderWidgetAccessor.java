package redxax.oxy.common.mixin.accessor;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import net.minecraft.client.gui.widget.SliderWidget;

@Mixin(SliderWidget.class)
public interface SliderWidgetAccessor {
    @Accessor("value")
    double getValue();
}
