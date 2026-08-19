package redxax.oxy.remotely.web.platform;

import restudio.rescreen.platform.http.HttpEndpointPolicy;

import java.net.URI;
import java.util.Locale;

public final class BrowserHttpPolicy implements HttpEndpointPolicy {
    @Override
    public boolean permits(URI uri) {
        if (uri == null || uri.getScheme() == null || uri.getHost() == null || uri.getUserInfo() != null || uri.getFragment() != null) {
            return false;
        }
        String scheme = uri.getScheme().toLowerCase(Locale.ROOT);
        if ("https".equals(scheme)) {
            return true;
        }
        return "http".equals(scheme) && isLocal(uri.getHost());
    }

    @Override
    public boolean permitsCredentials(URI uri) {
        if (!permits(uri)) {
            return false;
        }
        String backend = BrowserLaunchSession.backendOrigin();
        if (backend == null || backend.isBlank()) {
            return false;
        }
        try {
            URI backendUri = URI.create(backend);
            if (backendUri.getScheme() == null || backendUri.getHost() == null) {
                return false;
            }
            return origin(uri).equalsIgnoreCase(origin(backendUri));
        } catch (IllegalArgumentException ignored) {
            return false;
        }
    }

    static String origin(URI uri) {
        int port = uri.getPort();
        String host = uri.getHost().toLowerCase(Locale.ROOT);
        return uri.getScheme().toLowerCase(Locale.ROOT) + "://" + host + (port < 0 ? "" : ":" + port);
    }

    private static boolean isLocal(String host) {
        String normalized = host.toLowerCase(Locale.ROOT);
        return normalized.equals("localhost") || normalized.equals("127.0.0.1") || normalized.equals("[::1]")
                || normalized.equals("::1");
    }
}
