//? if fabric {
package redxax.oxy.remotely.loaders.fabric;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.KeyMapping;
import org.lwjgl.glfw.GLFW;
import redxax.oxy.remotely.RemotelyClient;
import net.fabricmc.api.ModInitializer;
import redxax.oxy.remotely.servers.ServerManagerScreen;

import static net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents.END_CLIENT_TICK;

public class FabricEntrypoint implements ModInitializer {
    private KeyMapping openTerminalKeyBinding;
    private KeyMapping openServerManagerKeyBinding;

    @Override
    public void onInitialize() {
        RemotelyClient remotely = new RemotelyClient();
        openTerminalKeyBinding = KeyBindingHelper.registerKeyBinding(new KeyMapping("Open Terminal", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_Z, "Remotely"));
        openServerManagerKeyBinding = KeyBindingHelper.registerKeyBinding(new KeyMapping("Open Server Manager", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_N, "Remotely"));
        END_CLIENT_TICK.register(client -> {if (client != null && client.player != null) {
            if (openTerminalKeyBinding.consumeClick()) {
                remotely.openMultiTerminalGUI(client);
            }
            if (openServerManagerKeyBinding.consumeClick()) {
                client.setScreen(new ServerManagerScreen(client, RemotelyClient.INSTANCE, RemotelyClient.INSTANCE.servers));
            }
        }});
        remotely.initialize();
    }

    public static boolean isModLoaded(String modId) {
        return FabricLoader.getInstance().isModLoaded(modId);
    }
}
//?}
