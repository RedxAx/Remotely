package redxax.oxy.remotely.network;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

class NetworkLifecycleEulaTest {
    @Test
    void createsAnAcceptedEulaWhenMissing() {
        assertEquals("#By changing the setting below to TRUE you are indicating your agreement to our EULA (https://aka.ms/MinecraftEULA).\neula=true\n", NetworkLifecycleJobManager.ensureEulaAccepted(""));
    }

    @Test
    void changesARejectedEulaWithoutDiscardingItsHeader() {
        assertEquals("# Minecraft EULA\neula=true\n", NetworkLifecycleJobManager.ensureEulaAccepted("# Minecraft EULA\neula=false\n"));
    }

    @Test
    void leavesAnAcceptedEulaUntouched() {
        String accepted = "# Existing\r\neula=TRUE\r\n";
        assertSame(accepted, NetworkLifecycleJobManager.ensureEulaAccepted(accepted));
    }

    @Test
    void appendsTheSettingWhenItIsAbsent() {
        assertEquals("# Existing\r\neula=true\r\n", NetworkLifecycleJobManager.ensureEulaAccepted("# Existing\r\n"));
    }
}
