package redxax.oxy.remotely.web.platform;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class BrowserHostedCheckoutSerializationTest {
    @Test
    void preservesCanonicalCreationAndModpackIntentFields() {
        Map<String, Object> pending = Map.of("provider", "modrinth", "projectId", "project", "versionId", "version");
        BrowserRemotelyServerApi.HostedCheckoutRequest request = new BrowserRemotelyServerApi.HostedCheckoutRequest(
                "Server", "starter", 12, Map.of("MAXIMUM_RAM", "95"), Map.of("server.properties", "motd=Hello"),
                pending, "ghcr.io/mcjars/pterodactyl-yolks:java_21", "FABRIC", "1.21.1", "latest",
                "server-name", null);

        Map<String, Object> body = BrowserRemotelyServerApi.hostedCheckoutBody(request);

        assertEquals(12, body.get("eggId"));
        assertEquals("ghcr.io/mcjars/pterodactyl-yolks:java_21", body.get("javaDockerImage"));
        assertEquals("FABRIC", body.get("software"));
        assertEquals("1.21.1", body.get("version"));
        assertEquals("latest", body.get("build"));
        assertEquals(pending, body.get("pendingModpackInstall"));
    }
}
