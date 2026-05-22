package redxax.oxy.remotely.mixin.accessor;

import net.minecraft.client.Minecraft;
import net.minecraft.client.server.IntegratedServer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(Minecraft.class)
public interface MinecraftAccessor {
    @Accessor("singleplayerServer")
    void remotely$setSingleplayerServer(IntegratedServer server);

    @Accessor("isLocalServer")
    void remotely$setLocalServer(boolean localServer);
}
