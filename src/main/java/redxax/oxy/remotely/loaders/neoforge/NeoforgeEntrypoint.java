//? if neoforge {
/*package redxax.oxy.remotely.loaders.neoforge;

import net.neoforged.fml.ModList;
import net.neoforged.fml.ModLoadingContext;
import net.neoforged.fml.common.Mod;
import redxax.oxy.remotely.RemotelyClient;

@Mod("remotely")
public class NeoforgeEntrypoint {

    public NeoforgeEntrypoint() {
        RemotelyClient remotely = new RemotelyClient();
        remotely.initialize();
    }

    public static boolean isModLoaded(String modId) {
        return ModList.get().isLoaded(modId);
    }
}
*///?}
