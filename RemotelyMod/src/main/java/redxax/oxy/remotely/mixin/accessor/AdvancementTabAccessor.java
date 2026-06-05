package redxax.oxy.remotely.mixin.accessor;

//#if MC >= 1.21.1
import net.minecraft.advancements.AdvancementNode;
//#else
//$$ import net.minecraft.advancements.Advancement;
//#endif
import net.minecraft.client.gui.screens.advancements.AdvancementTab;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(AdvancementTab.class)
public interface AdvancementTabAccessor {
    //#if MC >= 1.21.1
    @Invoker("getRootNode")
    AdvancementNode remotely$getRootNode();
    //#else
    //$$ @Invoker("getAdvancement")
    //$$ Advancement remotely$getAdvancement();
    //#endif
}
