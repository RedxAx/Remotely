package redxax.oxy.common.util;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.Identifier;

public class SoundUtils {

    public static final SoundEvent UI_INTERACTION = SoundEvent.of(Identifier.of("remotely", "ui_interaction"));

    @Environment(EnvType.CLIENT)
    public static void playClick() {
        MinecraftClient.getInstance().getSoundManager().play(PositionedSoundInstance.master(UI_INTERACTION, 1));
    }
}