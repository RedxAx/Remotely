//package redxax.oxy.remotely.mixin;
//
//import org.spongepowered.asm.mixin.Mixin;
//import org.spongepowered.asm.mixin.injection.At;
//import org.spongepowered.asm.mixin.injection.Inject;
//import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
//import static redxax.oxy.remotely.Render.scaleScroll;
//
//import net.minecraft.client.gui.components.events.ContainerEventHandler;
//
//@Mixin(value = ContainerEventHandler.class, targets = "net.minecraft.client.gui.ParentElement")
//public interface ParentElementMixin { //disabled for now
//
//
//    @Inject(method = "mouseScrolled", at = @At("HEAD"))
//    default void mouseScrolled(double mouseX, double mouseY, double vertAmount, CallbackInfoReturnable<Boolean> cir) {
//        scaleScroll(vertAmount);
//    }
//}