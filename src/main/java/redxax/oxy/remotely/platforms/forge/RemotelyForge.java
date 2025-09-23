//? forge {
/*package redxax.oxy.remotely.platforms.forge;

import redxax.oxy.remotely.RemotelyInit;
import redxax.oxy.remotely.ModPlatform;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;
import redxax.oxy.remotely.config.SettingsScreen;

import static redxax.oxy.remotely.config.Config.remotelyDir;

@Mod("remotely")
public class RemotelyForge {
	public RemotelyForge() {
		RemotelyInit.entrypoint(new ForgePlatform());
        MinecraftForge.registerConfigScreen((client, parent) -> new SettingsScreen(client, "config", parent, remotelyDir.toString(), SettingsScreen.settings));
	}
	public static class ForgePlatform implements ModPlatform {
		@Override
		public String getModloader() {
			return "LexForge";
		}

		@Override
		public boolean isModLoaded(String modId) {
			return ModList.get().isLoaded(modId);
		}
	}

}
*///?}