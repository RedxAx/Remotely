//package redxax.oxy.remotely.mixin;//package redxax.oxy.remotely.mixin;
//
//import net.minecraft.client.gui.ParentElement;
//import org.spongepowered.asm.mixin.Mixin;
//import org.spongepowered.asm.mixin.injection.At;
//import org.spongepowered.asm.mixin.injection.Inject;
//import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
//import static redxax.oxy.remotely.Render.scaleScroll;
//
//
//@Mixin(value = ParentElement.class)
//public interface ParentElementMixin { //disabled for now
//
//
//    @Inject(method = "mouseScrolled", at = @At("HEAD"))
//    default void mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount, CallbackInfoReturnable<Boolean> cir) {
//        scaleScroll(verticalAmount);
//    }
//}