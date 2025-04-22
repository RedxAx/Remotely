package redxax.oxy.remotely.util;

//import net.fabricmc.api.EnvType;
//import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;

public class SoundUtils {

    public static final SoundEvent UI_INTERACTION = SoundEvent.createVariableRangeEvent(ResourceLocation.tryBuild("remotely", "ui_interaction"));

//    @Environment(EnvType.CLIENT)
    public static void playClick() {
        //Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(UI_INTERACTION, 1));
    }
}