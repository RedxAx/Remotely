package redxax.oxy.remotely.servers;

import redxax.oxy.remotely.RemotelyClient;
import restudio.rebase.instance.Instance;
import restudio.rebase.restudio.ReStudio;
import restudio.rebase.restudio.api.models.ServerModels;
import restudio.rescreen.util.Notification;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

public class ReProxyManager {
    private static final byte FRAME_AUTH_OK = 2;
    private static final byte FRAME_AUTH_ERROR = 3;
    private static final byte FRAME_PONG = 5;
    private static final byte FRAME_OPEN_STREAM = 6;
    private static final byte FRAME_STREAM_DATA = 7;
    private static final byte FRAME_CLOSE_STREAM = 8;
    private static final byte FRAME_STREAM_ERROR = 9;
    private static final int MAX_RECONNECT_ATTEMPTS = 5;
    private static final String PREFERRED_DOMAIN_KEY = "reproxy.preferredDomainId";
    private static final Map<Integer, ReProxySession> activeSessions = new ConcurrentHashMap<>();

    private record ReProxySession(Instance instance, String tunnelId, String domain, int localPort, WebSocket webSocket, Map<Long, Socket> streams, AtomicBoolean closing, AtomicInteger reconnects) {
    }

    public static void start(Instance instance, Runnable onComplete) {
        start(instance, onComplete, new AtomicInteger());
    }

    private static void start(Instance instance, Runnable onComplete, AtomicInteger reconnects) {
        if (instance == null || instance.getPort() <= 0 || instance.getPort() > 65535) {
            new Notification("Invalid Server Port", Notification.Type.ERROR);
            if (onComplete != null) onComplete.run();
            return;
        }
        if (activeSessions.containsKey(instance.getPort())) {
            stop(instance.getPort(), onComplete);
            return;
        }
        if (!ReStudio.getInstance().isAuthenticated()) {
            new Notification("ReStudio Login Required", Notification.Type.WARN);
            if (onComplete != null) onComplete.run();
            return;
        }
        Notification notification = new Notification.Builder().message("Starting ReProxy").type(Notification.Type.INFO).loading(true).autoSlideOut(false).build();
        ReStudio.getInstance().getApi().listReProxyDomains()
                .thenCompose(domains -> resolveDomain(instance, domains))
                .thenCompose(domain -> ReStudio.getInstance().getApi().startReProxyTunnel(domain.id, instance.getPort(), "MINECRAFT_JAVA_TCP"))
                .thenAccept(response -> connect(instance, response, notification, onComplete, reconnects))
                .exceptionally(ex -> {
                    notification.change("ReProxy Unavailable", cleanMessage(ex), Notification.Type.ERROR, null);
                    notification.loading = false;
                    if (onComplete != null) onComplete.run();
                    return null;
                });
    }

    public static void stop(int localPort, Runnable onComplete) {
        ReProxySession session = activeSessions.remove(localPort);
        if (session == null) {
            new Notification("ReProxy Offline", Notification.Type.INFO);
            if (onComplete != null) onComplete.run();
            return;
        }
        for (Socket socket : session.streams().values()) {
            try {
                socket.close();
            } catch (Exception ignored) {
            }
        }
        session.closing().set(true);
        session.webSocket().abort();
        ReStudio.getInstance().getApi().stopReProxyTunnel(session.tunnelId()).whenComplete((ignored, ex) -> {
            new Notification("ReProxy Offline", Notification.Type.INFO);
            if (onComplete != null) onComplete.run();
        });
    }

    public static void stopAll() {
        List<Integer> ports = new ArrayList<>(activeSessions.keySet());
        for (int port : ports) {
            stop(port, null);
        }
    }

    public static boolean isForwarded(Instance instance) {
        return instance != null && activeSessions.containsKey(instance.getPort());
    }

    public static List<String> listActiveTunnels() {
        return activeSessions.values().stream().map(ReProxySession::domain).toList();
    }

    private static java.util.concurrent.CompletableFuture<ServerModels.ReProxyDomain> resolveDomain(Instance instance, List<ServerModels.ReProxyDomain> domains) {
        String preferredDomainId = instance.getSettings().getProperty(PREFERRED_DOMAIN_KEY, "");
        if (!preferredDomainId.isBlank()) {
            ServerModels.ReProxyDomain preferred = domains.stream()
                    .filter(domain -> Objects.equals(domain.id, preferredDomainId))
                    .filter(domain -> "ACTIVE".equalsIgnoreCase(domain.status))
                    .findFirst()
                    .orElse(null);
            if (preferred != null) {
                return CompletableFuture.completedFuture(preferred);
            }
        }
        List<ServerModels.ReProxyDomain> activeDomains = domains.stream()
                .filter(domain -> "ACTIVE".equalsIgnoreCase(domain.status))
                .sorted(Comparator.comparing(domain -> domain.subdomain))
                .toList();
        if (!activeDomains.isEmpty()) {
            ServerModels.ReProxyDomain domain = activeDomains.getFirst();
            if (activeDomains.size() > 1 && preferredDomainId.isBlank()) {
                new Notification("Manage Domains", Notification.Type.INFO);
            }
            persistPreferredDomain(instance, domain.id);
            return CompletableFuture.completedFuture(domain);
        }
        return createDomainWithFallbacks(instance, sanitizeSubdomain(instance.getName()), 0);
    }

    private static CompletableFuture<ServerModels.ReProxyDomain> createDomainWithFallbacks(Instance instance, String base, int attempt) {
        List<String> candidates = domainCandidates(base);
        if (attempt >= candidates.size()) {
            new Notification("Domain Taken", Notification.Type.WARN);
            return CompletableFuture.failedFuture(new IllegalStateException("Domain Taken"));
        }
        return ReStudio.getInstance().getApi().createReProxyDomain(candidates.get(attempt))
                .thenApply(domain -> {
                    persistPreferredDomain(instance, domain.id);
                    return domain;
                })
                .exceptionallyCompose(error -> {
                    String message = cleanMessage(error).toLowerCase(Locale.ROOT);
                    if (message.contains("taken") || message.contains("conflict") || message.contains("domain failed")) {
                        return createDomainWithFallbacks(instance, base, attempt + 1);
                    }
                    return CompletableFuture.failedFuture(error);
                });
    }

    private static List<String> domainCandidates(String base) {
        String random = UUID.randomUUID().toString().replace("-", "").substring(0, 4);
        return List.of(trimDomain(base), trimDomain(base + "-2"), trimDomain(base + "-3"), trimDomain(base + "-" + random), trimDomain(base + "-" + UUID.randomUUID().toString().replace("-", "").substring(0, 4)));
    }

    private static String trimDomain(String value) {
        String trimmed = value.length() > 32 ? value.substring(0, 32) : value;
        return trimmed.replaceAll("-$", "");
    }

    public static void setPreferredDomain(Instance instance, String domainId) {
        persistPreferredDomain(instance, domainId);
    }

    public static String getPreferredDomainId(Instance instance) {
        return instance == null ? "" : instance.getSettings().getProperty(PREFERRED_DOMAIN_KEY, "");
    }

    private static void persistPreferredDomain(Instance instance, String domainId) {
        if (instance != null && domainId != null && !domainId.isBlank()) {
            instance.getSettings().setProperty(PREFERRED_DOMAIN_KEY, domainId);
        }
    }

    private static void connect(Instance instance, ServerModels.ReProxyStartTunnelResponse response, Notification notification, Runnable onComplete, AtomicInteger reconnects) {
        int localPort = instance.getPort();
        String host = response.assignedNode.tunnelHost != null ? response.assignedNode.tunnelHost : response.assignedNode.fqdn;
        String scheme = response.assignedNode.tunnelScheme != null && !response.assignedNode.tunnelScheme.isBlank()
                ? response.assignedNode.tunnelScheme
                : host.startsWith("localhost") || host.startsWith("127.0.0.1") ? "ws" : "wss";
        URI uri = URI.create(scheme + "://" + host + ":" + response.assignedNode.tunnelPort + "/reproxy/tunnel?tunnelId=" + response.tunnelId + "&token=" + response.token);
        String displayUri = scheme + "://" + host + ":" + response.assignedNode.tunnelPort + "/reproxy/tunnel";
        String reachabilityError = tunnelReachabilityError(host, response.assignedNode.tunnelPort);
        if (reachabilityError != null) {
            notification.change("ReProxy Unavailable", displayUri + " - " + reachabilityError, Notification.Type.ERROR, null);
            notification.loading = false;
            if (onComplete != null) onComplete.run();
            return;
        }
        Map<Long, Socket> streams = new ConcurrentHashMap<>();
        AtomicBoolean closing = new AtomicBoolean(false);
        Listener listener = new Listener(instance, response, streams, notification, onComplete, closing, reconnects);
        HttpClient.newHttpClient().newWebSocketBuilder().buildAsync(uri, listener)
                .thenAccept(webSocket -> activeSessions.put(localPort, new ReProxySession(instance, response.tunnelId, response.domain, localPort, webSocket, streams, closing, reconnects)))
                .exceptionally(ex -> {
                    notification.change("ReProxy Unavailable", displayUri + " - " + cleanMessage(ex), Notification.Type.ERROR, null);
                    notification.loading = false;
                    if (onComplete != null) onComplete.run();
                    return null;
                });
    }

    private static void reconnect(Instance instance, AtomicInteger reconnects) {
        int attempt = reconnects.incrementAndGet();
        if (attempt > MAX_RECONNECT_ATTEMPTS) {
            new Notification("ReProxy Offline", "Reconnect Failed", Notification.Type.ERROR);
            return;
        }
        CompletableFuture.delayedExecutor(Math.min(30, attempt * 3L), TimeUnit.SECONDS).execute(() -> start(instance, null, reconnects));
    }

    private static String sanitizeSubdomain(String name) {
        String value = name == null ? "server" : name.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9-]", "-").replaceAll("-+", "-").replaceAll("^-|-$", "");
        if (value.length() < 3) {
            value = "server-" + value;
        }
        if (value.length() > 32) {
            value = value.substring(0, 32).replaceAll("-$", "");
        }
        return value;
    }

    private static ByteBuffer encode(byte type, long streamId, byte[] payload) {
        ByteBuffer buffer = ByteBuffer.allocate(13 + payload.length);
        buffer.put(type);
        buffer.putLong(streamId);
        buffer.putInt(payload.length);
        buffer.put(payload);
        buffer.flip();
        return buffer;
    }

    private static String cleanMessage(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null) {
            current = current.getCause();
        }
        String message = current.getMessage();
        if (message == null || message.isBlank()) {
            message = current.getClass().getSimpleName();
        }
        return message == null || message.isBlank() ? "Request Failed" : message;
    }

    private static String tunnelReachabilityError(String host, int port) {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(host, port), 4000);
            return null;
        } catch (Exception e) {
            return "Cannot Connect To " + host + ":" + port + " (" + cleanMessage(e) + ")";
        }
    }

    private static class Listener implements WebSocket.Listener {
        private final int localPort;
        private final Instance instance;
        private final ServerModels.ReProxyStartTunnelResponse response;
        private final Map<Long, Socket> streams;
        private final Notification notification;
        private final Runnable onComplete;
        private final AtomicBoolean closing;
        private final AtomicInteger reconnects;
        private final Object sendLock = new Object();

        private Listener(Instance instance, ServerModels.ReProxyStartTunnelResponse response, Map<Long, Socket> streams, Notification notification, Runnable onComplete, AtomicBoolean closing, AtomicInteger reconnects) {
            this.instance = instance;
            this.localPort = instance.getPort();
            this.response = response;
            this.streams = streams;
            this.notification = notification;
            this.onComplete = onComplete;
            this.closing = closing;
            this.reconnects = reconnects;
        }

        @Override
        public void onOpen(WebSocket webSocket) {
            webSocket.request(1);
        }

        @Override
        public CompletionStage<?> onBinary(WebSocket webSocket, ByteBuffer data, boolean last) {
            if (data.remaining() < 13) {
                return WebSocket.Listener.super.onBinary(webSocket, data, last);
            }
            byte type = data.get();
            long streamId = data.getLong();
            int length = data.getInt();
            if (length < 0 || length > data.remaining()) {
                return WebSocket.Listener.super.onBinary(webSocket, data, last);
            }
            byte[] payload = new byte[length];
            data.get(payload);
            if (type == FRAME_AUTH_OK) {
                notification.change("ReProxy Online", "Copy Address", Notification.Type.SUCCESS, () -> RemotelyClient.INSTANCE.getHost().setClipboard(response.domain));
                notification.loading = false;
                if (onComplete != null) onComplete.run();
            } else if (type == FRAME_AUTH_ERROR) {
                closing.set(true);
                activeSessions.remove(localPort);
                notification.change("ReProxy Unavailable", new String(payload, StandardCharsets.UTF_8), Notification.Type.ERROR, null);
                notification.loading = false;
                webSocket.abort();
                if (onComplete != null) onComplete.run();
            } else if (type == FRAME_OPEN_STREAM) {
                openStream(webSocket, streamId);
            } else if (type == FRAME_STREAM_DATA) {
                writeStream(webSocket, streamId, payload);
            } else if (type == FRAME_CLOSE_STREAM || type == FRAME_STREAM_ERROR) {
                closeStream(streamId);
            }
            webSocket.request(1);
            return null;
        }

        private void openStream(WebSocket webSocket, long streamId) {
            try {
                Socket socket = new Socket("127.0.0.1", localPort);
                streams.put(streamId, socket);
                Thread.ofVirtual().name("ReProxy Stream " + streamId).start(() -> readLocal(webSocket, streamId, socket));
            } catch (Exception e) {
                sendFrame(webSocket, FRAME_STREAM_ERROR, streamId, e.getMessage().getBytes(StandardCharsets.UTF_8));
            }
        }

        private void readLocal(WebSocket webSocket, long streamId, Socket socket) {
            byte[] buffer = new byte[32 * 1024];
            try (InputStream input = socket.getInputStream()) {
                int read;
                while ((read = input.read(buffer)) >= 0) {
                    byte[] chunk = new byte[read];
                    System.arraycopy(buffer, 0, chunk, 0, read);
                    sendFrame(webSocket, FRAME_STREAM_DATA, streamId, chunk);
                }
            } catch (Exception ignored) {
            } finally {
                sendFrame(webSocket, FRAME_CLOSE_STREAM, streamId, new byte[0]);
                closeStream(streamId);
            }
        }

        private void writeStream(WebSocket webSocket, long streamId, byte[] payload) {
            Socket socket = streams.get(streamId);
            if (socket == null) {
                return;
            }
            try {
                OutputStream output = socket.getOutputStream();
                output.write(payload);
                output.flush();
            } catch (Exception e) {
                sendFrame(webSocket, FRAME_STREAM_ERROR, streamId, e.getMessage().getBytes(StandardCharsets.UTF_8));
                closeStream(streamId);
            }
        }

        private void sendFrame(WebSocket webSocket, byte type, long streamId, byte[] payload) {
            synchronized (sendLock) {
                webSocket.sendBinary(encode(type, streamId, payload), true).join();
            }
        }

        private void closeStream(long streamId) {
            Socket socket = streams.remove(streamId);
            if (socket != null) {
                try {
                    socket.close();
                } catch (Exception ignored) {
                }
            }
        }

        @Override
        public void onError(WebSocket webSocket, Throwable error) {
            activeSessions.remove(localPort);
            if (closing.compareAndSet(false, true)) {
                notification.change("ReProxy Reconnecting", cleanMessage(error), Notification.Type.WARN, null);
                reconnect(instance, reconnects);
            } else if (reconnects.get() == 0) {
                notification.change("ReProxy Offline", cleanMessage(error), Notification.Type.WARN, null);
            }
            notification.loading = false;
            if (onComplete != null) onComplete.run();
        }

        @Override
        public CompletionStage<?> onClose(WebSocket webSocket, int statusCode, String reason) {
            activeSessions.remove(localPort);
            for (Socket socket : streams.values()) {
                try {
                    socket.close();
                } catch (Exception ignored) {
                }
            }
            if (closing.compareAndSet(false, true)) {
                new Notification("ReProxy Reconnecting", Notification.Type.WARN);
                reconnect(instance, reconnects);
            }
            return WebSocket.Listener.super.onClose(webSocket, statusCode, reason);
        }
    }
}
