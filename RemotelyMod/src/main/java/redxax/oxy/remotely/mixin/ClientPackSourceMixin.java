//#if FABRIC
package redxax.oxy.remotely.mixin;

import net.minecraft.client.resources.ClientPackSource;
import net.minecraft.server.packs.repository.Pack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import redxax.oxy.remotely.resources.RemotelyBuiltInResourcePack;

import java.util.function.BiConsumer;
import java.util.function.Function;

@Mixin(ClientPackSource.class)
public class ClientPackSourceMixin {
    @Inject(method = "populatePackList", at = @At("TAIL"))
    private void populateRemotelyPack(BiConsumer<String, Function<String, Pack>> packs, CallbackInfo ci) {
        packs.accept(RemotelyBuiltInResourcePack.id(), RemotelyBuiltInResourcePack::create);
    }
}
//#endif
