package redxax.oxy.remotely.mixin.accessor;

import net.minecraft.client.gui.screens.advancements.AdvancementTab;
import net.minecraft.client.gui.screens.advancements.AdvancementsScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(AdvancementsScreen.class)
public interface AdvancementsScreenAccessor {
    @Accessor("selectedTab")
    AdvancementTab remotely$getSelectedTab();
}
