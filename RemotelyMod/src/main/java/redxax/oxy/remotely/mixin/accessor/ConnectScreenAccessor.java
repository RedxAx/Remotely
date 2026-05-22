package redxax.oxy.remotely.mixin.accessor;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.TransferState;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(ConnectScreen.class)
public interface ConnectScreenAccessor {
    @Invoker("<init>")
    static ConnectScreen remotely$create(Screen parent, Component connectFailedTitle) {
        throw new AssertionError();
    }

    @Invoker("connect")
    void remotely$connect(Minecraft minecraft, ServerAddress serverAddress, ServerData serverData, TransferState transferState);
}
