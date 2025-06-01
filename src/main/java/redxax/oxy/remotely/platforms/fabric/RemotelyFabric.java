//? if fabric {
/*package redxax.oxy.remotely.platforms.fabric;

import redxax.oxy.remotely.ModPlatform;
import net.fabricmc.api.ModInitializer;
import redxax.oxy.remotely.RemotelyInit;
import net.fabricmc.loader.api.FabricLoader;

public class RemotelyFabric implements ModInitializer {
	@Override
	public void onInitialize() {
		RemotelyInit.entrypoint(new FabricPlatform());

	}
	public static class FabricPlatform implements ModPlatform{

		@Override
		public String getModloader() {
			return "Fabric";
		}

		@Override
		public boolean isModLoaded(String modloader) {
			return FabricLoader.getInstance().isModLoaded(modloader);
		}
	}
}
*///?}