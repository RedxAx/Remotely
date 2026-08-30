package redxax.oxy.remotely.ui.server;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ServerManagerAccountHydrationTest {
    @Test
    void acceptsFallbackNameUpgradeToSubject() {
        ServerScreenHost.AccountIdentity requested = new ServerScreenHost.AccountIdentity(true, "", "Account Name", "steve.png");
        ServerScreenHost.AccountIdentity hydrated = new ServerScreenHost.AccountIdentity(true, "subject-id", "Account Name", "avatar.png");

        assertTrue(ServerManagerScreen.compatibleAccountHydration(requested, hydrated));
    }

    @Test
    void rejectsAccountSwitches() {
        ServerScreenHost.AccountIdentity requested = new ServerScreenHost.AccountIdentity(true, "subject-a", "Account", "steve.png");
        ServerScreenHost.AccountIdentity switched = new ServerScreenHost.AccountIdentity(true, "subject-b", "Account", "avatar.png");
        ServerScreenHost.AccountIdentity renamedFallback = new ServerScreenHost.AccountIdentity(true, "subject-b", "Other Account", "avatar.png");

        assertFalse(ServerManagerScreen.compatibleAccountHydration(requested, switched));
        assertFalse(ServerManagerScreen.compatibleAccountHydration(new ServerScreenHost.AccountIdentity(true, "", "Account", "steve.png"), renamedFallback));
    }
}
