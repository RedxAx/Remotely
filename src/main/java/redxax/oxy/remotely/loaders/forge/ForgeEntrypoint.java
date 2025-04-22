//? if forge {
/*package redxax.oxy.remotely.loaders.forge;

import net.minecraftforge.fml.common.Mod;
import redxax.oxy.remotely.RemotelyClient;

@Mod("remotely")
public class ForgeEntrypoint {

    public ForgeEntrypoint() {
        RemotelyClient remotely = new RemotelyClient();
        remotely.initialize();
    }

    public static boolean isModLoaded(String modId) {
        return net.minecraftforge.fml.ModList.get().isLoaded(modId);
    }
}
*///?}
