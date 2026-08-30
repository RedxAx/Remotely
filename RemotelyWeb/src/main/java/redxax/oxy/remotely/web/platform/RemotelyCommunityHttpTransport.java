package redxax.oxy.remotely.web.platform;

import restudio.rescreen.platform.Async;
import restudio.rescreen.platform.http.HttpRequest;
import restudio.rescreen.platform.http.HttpResponse;
import restudio.rebase.restudio.community.ReStudioCommunityHttpTransport.Response;
import restudio.rescreen.platform.http.HttpTransport;
import restudio.rebase.restudio.community.ReStudioCommunityHttpTransport;

import java.net.URI;
import java.time.Duration;

final class RemotelyCommunityHttpTransport implements ReStudioCommunityHttpTransport {
    private final HttpTransport transport;

    RemotelyCommunityHttpTransport(HttpTransport transport) {
        this.transport = transport;
    }

    @Override
    public Async<Response> send(String method, String path, byte[] body, String contentType) {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(BrowserLaunchSession.apiBaseUrl() + path))
                .header("Accept", "application/json")
                .timeout(Duration.ofSeconds(20));
        if (contentType != null && !contentType.isBlank()) builder.header("Content-Type", contentType);
        builder.method(method, body == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofByteArray(body));
        return transport.sendAsync(builder.build(), HttpResponse.BodyHandlers.ofString())
                .thenApply(response -> new Response(response.statusCode(), response.body()));
    }

    @Override
    public Async<Boolean> refresh() {
        return BrowserLaunchSession.renewAsync().thenApply(metadata -> metadata != null && !metadata.ticket().isBlank());
    }
}
