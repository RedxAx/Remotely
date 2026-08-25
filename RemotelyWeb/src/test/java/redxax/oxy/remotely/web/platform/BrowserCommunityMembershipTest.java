package redxax.oxy.remotely.web.platform;

import org.junit.jupiter.api.Test;
import restudio.rebase.restudio.ReStudioMembership;
import restudio.rebase.restudio.membership.ReStudioMembershipCodec;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

class BrowserCommunityMembershipTest {
    @Test
    void parsesMembershipResponse() {
        UUID id = UUID.randomUUID();
        ReStudioMembership membership = ReStudioMembershipCodec.membership("""
                {
                  "id": "%s",
                  "planName": "Reactor Pro",
                  "serverIdentifier": "reactor-1",
                  "billingMode": "WHOP",
                  "status": "Cancellation Scheduled",
                  "providerStatus": "canceling",
                  "currentPeriodStart": "2026-08-01T00:00:00Z",
                  "currentPeriodEnd": "2026-09-01T00:00:00Z",
                  "cancelAtPeriodEnd": true,
                  "paymentCollectionPaused": false,
                  "manageUrl": "https://whop.com/hub/example",
                  "createdAt": "2026-08-01T00:00:00Z",
                  "synchronizedAt": "2026-08-23T00:00:00Z",
                  "canCancelRenewal": false,
                  "canKeepRenewal": true,
                  "canRefresh": true
                }
                """.formatted(id));

        assertEquals(id, membership.id);
        assertEquals("Reactor Pro", membership.planName);
        assertEquals("reactor-1", membership.serverIdentifier);
        assertEquals("WHOP", membership.billingMode);
        assertEquals("Cancellation Scheduled", membership.status);
        assertEquals("canceling", membership.providerStatus);
        assertEquals("2026-08-01T00:00:00Z", membership.currentPeriodStart);
        assertEquals("2026-09-01T00:00:00Z", membership.currentPeriodEnd);
        assertTrue(membership.cancelAtPeriodEnd);
        assertFalse(membership.paymentCollectionPaused);
        assertEquals("https://whop.com/hub/example", membership.manageUrl);
        assertTrue(membership.canKeepRenewal);
        assertTrue(membership.canRefresh);
        assertFalse(membership.canCancelRenewal);
    }

    @Test
    void rejectsInvalidMembershipIdentityAndStatus() {
        String membership = """
                {"id":"not-a-uuid","planName":"Reactor","serverIdentifier":"reactor-1","billingMode":"WHOP",
                "status":"Future Status","providerStatus":"future","currentPeriodStart":"","currentPeriodEnd":"",
                "cancelAtPeriodEnd":false,"paymentCollectionPaused":false,"manageUrl":"","createdAt":"",
                "synchronizedAt":"","canCancelRenewal":false,"canKeepRenewal":false,"canRefresh":true}
                """;

        assertThrows(IllegalStateException.class, () -> ReStudioMembershipCodec.membership(membership));
    }

    @Test
    void rejectsMembershipCallbackAfterAccountSwitch() {
        assertTrue(BrowserCommunityProvider.membershipFenceCurrent(4, "user-one", 4, "user-one", true));
        assertFalse(BrowserCommunityProvider.membershipFenceCurrent(4, "user-one", 5, "user-two", true));
        assertFalse(BrowserCommunityProvider.membershipFenceCurrent(4, "user-one", 4, "user-two", true));
        assertFalse(BrowserCommunityProvider.membershipFenceCurrent(4, "user-one", 4, "user-one", false));
    }
}
