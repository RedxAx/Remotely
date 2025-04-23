//? if fabric {
package redxax.oxy.remotely.loaders.fabric;

import net.fabricmc.loader.api.FabricLoader;
import redxax.oxy.remotely.RemotelyClient;
import net.fabricmc.api.ModInitializer;

public class FabricEntrypoint implements ModInitializer {
//    private KeyMapping openTerminalKeyBinding;
//    private KeyMapping openServerManagerKeyBinding;

    @Override
    public void onInitialize() {
        RemotelyClient remotely = new RemotelyClient();
        remotely.initialize();
//        openTerminalKeyBinding = KeyBindingHelper.registerKeyBinding(new KeyMapping("Open Terminal", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_Z, "Remotely"));
//        openServerManagerKeyBinding = KeyBindingHelper.registerKeyBinding(new KeyMapping("Open Server Manager", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_N, "Remotely"));
//        END_CLIENT_TICK.register(client -> {if (client != null && client.player != null) {
//            if (openTerminalKeyBinding.consumeClick()) {
//                remotely.openMultiTerminalGUI(client);
//            }
//            if (openServerManagerKeyBinding.consumeClick()) {
//                client.setScreen(new ServerManagerScreen(client, RemotelyClient.INSTANCE, RemotelyClient.INSTANCE.servers));
//            }
//        }});
    }

    public static boolean isModLoaded(String modId) {
        return FabricLoader.getInstance().isModLoaded(modId);
    }
}
//?}
