package redxax.oxy.common.mixin;

import net.minecraft.client.MinecraftClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import redxax.oxy.common.config.Config;
import redxax.oxy.common.util.CursorUtils;

@Mixin(MinecraftClient.class)
public class MinecraftClientMixin {
    @Inject(method = "render", at = @At("HEAD"))
    private void onRender(boolean bl, CallbackInfo ci) {
        Config.tickTime();
        Config.globalCursorAnimatedColor = CursorUtils.blendColor();
    }
}
