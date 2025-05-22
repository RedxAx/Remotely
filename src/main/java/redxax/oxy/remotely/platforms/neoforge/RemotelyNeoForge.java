//? if neoforge {
/*package redxax.oxy.remotely.platforms.neoforge;

import net.minecraft.client.MinecraftClient;
import redxax.oxy.remotely.RemotelyInit;
import redxax.oxy.remotely.ModPlatform;
import net.neoforged.fml.ModList;
import net.neoforged.fml.ModLoadingContext;
import net.neoforged.fml.common.Mod;
//? if <1.21 {
/^import net.neoforged.neoforge.client.ConfigScreenHandler;
^///?} else {
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import redxax.oxy.remotely.servers.SettingsScreen;

import static redxax.oxy.remotely.config.Config.remotelyDir;

//?}
@Mod("remotely")
public class RemotelyNeoForge {
	public RemotelyNeoForge() {
        RemotelyInit.entrypoint(new NeoForgePlatform());
        ModLoadingContext.get().registerExtensionPoint(
                //? if <1.21 {
                /^ConfigScreenHandler.ConfigScreenFactory.class,
                () -> new ConfigScreenHandler.ConfigScreenFactory(
                        ((client, parent) -> new SettingsScreen("config", parent, remotelyDir.toString(), SettingsScreen.settings)
                )
                ^///?} else {
                IConfigScreenFactory.class, () -> (client, parent) -> new SettingsScreen("config", parent, remotelyDir.toString(), SettingsScreen.settings)
                //?}
        );
	}
    public static class NeoForgePlatform implements ModPlatform {
        @Override
        public String getModloader() {
            return "NeoForge";
        }

        @Override
        public boolean isModLoaded(String modId) {
            return ModList.get().isLoaded(modId);
        }
    }
}
*///?}