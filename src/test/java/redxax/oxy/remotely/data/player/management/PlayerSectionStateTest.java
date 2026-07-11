package redxax.oxy.remotely.data.player.management;

import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlayerSectionStateTest {
    @Test
    void distinguishesUnsupportedEmptyAndStale() {
        PlayerSectionState<String> unsupported = PlayerSectionState.unsupported();
        PlayerSectionState<String> empty = PlayerSectionState.ready("", "world", 1L, 0L, false, true);
        PlayerSectionState<String> stale = PlayerSectionState.failed("previous", "resync", "Disconnected");

        assertFalse(unsupported.supported());
        assertEquals(PlayerSectionState.Status.EMPTY, empty.status());
        assertEquals(PlayerSectionState.Status.STALE, stale.status());
        assertEquals("previous", stale.value());
    }

    @Test
    void providerCapabilitiesExposeOnlyDeclaredSectionsAndOperations() {
        PlayerProviderCapabilities capabilities = new PlayerProviderCapabilities("resync", EnumSet.of(PlayerSection.INVENTORY), Set.of(PlayerOperation.Type.INVENTORY_EDIT));

        assertTrue(capabilities.supports(PlayerSection.INVENTORY));
        assertFalse(capabilities.supports(PlayerSection.STATS));
        assertTrue(capabilities.supports(PlayerOperation.Type.INVENTORY_EDIT));
    }
}
