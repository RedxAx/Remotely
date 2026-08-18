package redxax.oxy.remotely.web.platform;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class BrowserHostedSettingsSerializationTest {
    @Test
    void permissionCatalogRetainsCategoriesKeysAndDescriptions() {
        var permissions = BrowserRemotelyServerApi.systemPermissions(BrowserJson.object("{\"permissions\":{\"control\":{\"description\":\"Server control\",\"keys\":{\"console\":\"Send commands\"}}}}"));

        assertEquals("Server control", permissions.permissions.get("control").description);
        assertEquals("Send commands", permissions.permissions.get("control").keys.get("console"));
    }

    @Test
    void userSearchRetainsCanonicalIdentity() {
        var user = BrowserRemotelyServerApi.userInfo(BrowserJson.object("{\"id\":\"user-id\",\"username\":\"alex\",\"displayName\":\"Alex\",\"avatarUrl\":\"https://cdn/avatar.png\"}"));

        assertEquals("user-id", user.id);
        assertEquals("alex", user.username);
        assertEquals("Alex", user.displayName);
        assertEquals("https://cdn/avatar.png", user.avatarUrl);
    }
}
