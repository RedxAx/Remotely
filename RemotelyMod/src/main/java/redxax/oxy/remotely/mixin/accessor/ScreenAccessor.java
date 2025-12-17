package redxax.oxy.remotely.mixin.accessor;

import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.narration.NarratableEntry;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(Screen.class)
public interface ScreenAccessor {
    @Invoker("addRenderableWidget")
    <T extends GuiEventListener & NarratableEntry> T remotely$addRenderableWidget(T widget);
}
