package redxax.oxy.remotely.web.platform;

import org.teavm.jso.JSBody;
import restudio.rebase.platform.Async;
import restudio.rebase.platform.websocket.BinaryWebSocket;
import restudio.rebase.platform.websocket.BinaryWebSocketListener;
import restudio.rebase.platform.websocket.WebSocketOptions;
import restudio.rebase.platform.websocket.WebSocketTransport;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class BrowserWebSocketTransport implements WebSocketTransport {
    private static final Map<Integer, PendingSocket> PENDING = new HashMap<>();
    private static final Map<Integer, BrowserWebSocket> ACTIVE = new HashMap<>();
    private static int nextSocketId = 1;
    private final Set<Integer> ownedSockets = new HashSet<>();

    @Override
    public Async<BinaryWebSocket> connectAsync(String uri, Map<String, String> headers, BinaryWebSocketListener listener) {
        return connectAsync(uri, WebSocketOptions.headers(headers), listener);
    }

    @Override
    public Async<BinaryWebSocket> connectAsync(String uri, WebSocketOptions options, BinaryWebSocketListener listener) {
        Async<BinaryWebSocket> future = Async.pending();
        if (uri == null || uri.isBlank()) {
            future.fail(new IllegalArgumentException("WebSocket URI is required"));
            return future;
        }
        WebSocketOptions resolved = options == null ? WebSocketOptions.empty() : options;
        if (!resolved.headers().isEmpty()) {
            future.fail(new UnsupportedOperationException("Browser WebSocket Request Headers Are Unavailable"));
            return future;
        }
        int socketId = nextId();
        PENDING.put(socketId, new PendingSocket(this, future, listener));
        ownedSockets.add(socketId);
        future.onCancel(() -> cancelPending(socketId, future));
        open(socketId, originSafeUri(uri, secureOrigin()), protocolsJson(resolved.subprotocols()));
        return future;
    }

    static String originSafeUri(String uri, boolean secureOrigin) {
        if (!secureOrigin || uri == null || !uri.regionMatches(true, 0, "ws://", 0, 5)) {
            return uri;
        }
        return "wss://" + uri.substring(5);
    }

    public static void opened(int socketId) {
        PendingSocket pending = PENDING.remove(socketId);
        if (pending == null) {
            return;
        }
        BrowserWebSocket socket = new BrowserWebSocket(pending.owner, socketId, pending.listener);
        ACTIVE.put(socketId, socket);
        pending.future.complete(socket);
        if (pending.listener != null) {
            pending.listener.onOpen(socket);
        }
    }

    public static void text(int socketId, String value) {
        BrowserWebSocket socket = ACTIVE.get(socketId);
        if (socket != null && socket.open) {
            socket.listener.onText(value == null ? "" : value);
        }
    }

    public static void binary(int socketId, String value) {
        BrowserWebSocket socket = ACTIVE.get(socketId);
        if (socket != null && socket.open) {
            try {
                socket.listener.onBinary(BrowserHttpTransport.decodeBase64(value));
            } catch (RuntimeException exception) {
                socket.listener.onError(exception);
            }
        }
    }

    public static void closed(int socketId, int statusCode, String reason) {
        BrowserWebSocket socket = ACTIVE.remove(socketId);
        if (socket != null) {
            socket.owner.ownedSockets.remove(socketId);
            socket.open = false;
            socket.listener.onClose(statusCode, reason == null ? "" : reason);
            return;
        }
        PendingSocket pending = PENDING.remove(socketId);
        if (pending != null) {
            pending.owner.ownedSockets.remove(socketId);
            pending.future.fail(new IllegalStateException(reason == null ? "WebSocket closed before opening" : reason));
        }
    }

    public static void failed(int socketId, String message) {
        BrowserWebSocket socket = ACTIVE.remove(socketId);
        if (socket != null) {
            socket.owner.ownedSockets.remove(socketId);
            boolean wasOpen = socket.open;
            socket.open = false;
            String reason = message == null || message.isBlank() ? "WebSocket error" : message;
            try {
                close(socketId, 1000, reason);
            } catch (RuntimeException ignored) {
            }
            if (wasOpen) {
                IllegalStateException failure = new IllegalStateException(reason);
                try {
                    socket.listener.onError(failure);
                } finally {
                    socket.listener.onClose(1006, reason);
                }
            }
            return;
        }
        PendingSocket pending = PENDING.remove(socketId);
        if (pending != null) {
            pending.owner.ownedSockets.remove(socketId);
            String reason = message == null || message.isBlank() ? "WebSocket connection failed" : message;
            try {
                close(socketId, 1000, reason);
            } catch (RuntimeException ignored) {
            }
            pending.future.fail(new IllegalStateException(reason));
        }
    }

    public void closeAll() {
        for (Integer socketId : Set.copyOf(ownedSockets)) {
            BrowserWebSocket socket = ACTIVE.remove(socketId);
            PendingSocket pending = PENDING.remove(socketId);
            ownedSockets.remove(socketId);
            if (socket != null) {
                socket.open = false;
                close(socket.socketId, 1000, "Browser Runtime Closed");
            }
            if (pending != null) {
                pending.future.fail(new IllegalStateException("Browser Runtime Closed"));
            }
        }
    }

    private static int nextId() {
        int socketId = nextSocketId++;
        if (socketId <= 0) {
            nextSocketId = 2;
            socketId = 1;
        }
        return socketId;
    }

    private static void cancelPending(int socketId, Async<BinaryWebSocket> future) {
        PendingSocket pending = PENDING.get(socketId);
        if (pending == null || pending.future != future) {
            return;
        }
        PENDING.remove(socketId);
        pending.owner.ownedSockets.remove(socketId);
        close(socketId, 1000, "WebSocket Connection Cancelled");
    }

    @JSBody(params = {"socketId", "uri", "protocolsJson"}, script = "try { var protocols = protocolsJson ? JSON.parse(protocolsJson) : []; var socket = protocols.length ? new WebSocket(uri, protocols) : new WebSocket(uri); socket.binaryType = 'arraybuffer'; var root = window.__remotelyWebSockets || (window.__remotelyWebSockets = {}); var key = String(socketId); root[key] = socket; socket.onopen = function() { javaMethods.get('redxax.oxy.remotely.web.platform.BrowserWebSocketTransport.opened(I)V').invoke(socketId); }; socket.onmessage = function(event) { if (typeof event.data === 'string') { javaMethods.get('redxax.oxy.remotely.web.platform.BrowserWebSocketTransport.text(ILjava/lang/String;)V').invoke(socketId, event.data); return; } var bytes = new Uint8Array(event.data); var raw = ''; var chunk = 32768; for (var index = 0; index < bytes.length; index += chunk) raw += String.fromCharCode.apply(null, bytes.subarray(index, Math.min(index + chunk, bytes.length))); javaMethods.get('redxax.oxy.remotely.web.platform.BrowserWebSocketTransport.binary(ILjava/lang/String;)V').invoke(socketId, btoa(raw)); }; socket.onclose = function(event) { delete root[key]; javaMethods.get('redxax.oxy.remotely.web.platform.BrowserWebSocketTransport.closed(IILjava/lang/String;)V').invoke(socketId, event.code || 1000, event.reason || ''); }; socket.onerror = function() { javaMethods.get('redxax.oxy.remotely.web.platform.BrowserWebSocketTransport.failed(ILjava/lang/String;)V').invoke(socketId, 'WebSocket error'); }; } catch (error) { javaMethods.get('redxax.oxy.remotely.web.platform.BrowserWebSocketTransport.failed(ILjava/lang/String;)V').invoke(socketId, String(error && (error.message || error))); }")
    private static native void open(int socketId, String uri, String protocolsJson);

    @JSBody(script = "return window.location.protocol === 'https:';")
    private static native boolean secureOrigin();

    private static String protocolsJson(List<String> protocols) {
        if (protocols == null || protocols.isEmpty()) {
            return "[]";
        }
        StringBuilder json = new StringBuilder("[");
        for (String protocol : protocols) {
            if (protocol == null || protocol.isBlank()) {
                continue;
            }
            if (json.length() > 1) {
                json.append(',');
            }
            json.append('"');
            for (int index = 0; index < protocol.length(); index++) {
                char value = protocol.charAt(index);
                if (value == '\\' || value == '"') {
                    json.append('\\');
                }
                json.append(value);
            }
            json.append('"');
        }
        return json.append(']').toString();
    }

    @JSBody(params = {"socketId", "value"}, script = "const socket = window.__remotelyWebSockets && window.__remotelyWebSockets[String(socketId)]; if (socket && socket.readyState === WebSocket.OPEN) socket.send(value);")
    private static native void sendText(int socketId, String value);

    @JSBody(params = {"socketId", "value"}, script = "const socket = window.__remotelyWebSockets && window.__remotelyWebSockets[String(socketId)]; if (!socket || socket.readyState !== WebSocket.OPEN) return; const raw = atob(value || ''); const bytes = new Uint8Array(raw.length); for (let index = 0; index < raw.length; index++) bytes[index] = raw.charCodeAt(index); socket.send(bytes);")
    private static native void sendBinary(int socketId, String value);

    @JSBody(params = {"socketId", "statusCode", "reason"}, script = "const socket = window.__remotelyWebSockets && window.__remotelyWebSockets[String(socketId)]; if (socket) socket.close(statusCode, reason || '');")
    private static native void close(int socketId, int statusCode, String reason);

    private static Async<Void> failedFuture(String message) {
        return Async.failed(new IllegalStateException(message));
    }

    private record PendingSocket(BrowserWebSocketTransport owner, Async<BinaryWebSocket> future, BinaryWebSocketListener listener) {
    }

    private static final class BrowserWebSocket implements BinaryWebSocket {
        private final BrowserWebSocketTransport owner;
        private final int socketId;
        private final BinaryWebSocketListener listener;
        private boolean open = true;

        private BrowserWebSocket(BrowserWebSocketTransport owner, int socketId, BinaryWebSocketListener listener) {
            this.owner = owner;
            this.socketId = socketId;
            this.listener = listener == null ? new BinaryWebSocketListener() {
                @Override
                public void onOpen(BinaryWebSocket socket) {
                }

                @Override
                public void onText(String text) {
                }

                @Override
                public void onClose(int statusCode, String reason) {
                }

                @Override
                public void onError(Throwable error) {
                }
            } : listener;
        }

        @Override
        public Async<Void> sendText(String text) {
            if (!open) {
                return failedFuture("WebSocket is closed");
            }
            try {
                BrowserWebSocketTransport.sendText(socketId, text == null ? "" : text);
                return Async.completed(null);
            } catch (RuntimeException exception) {
                return Async.failed(exception);
            }
        }

        @Override
        public Async<Void> sendBinary(byte[] bytes) {
            if (!open) {
                return failedFuture("WebSocket is closed");
            }
            try {
                BrowserWebSocketTransport.sendBinary(socketId, BrowserHttpTransport.encodeBase64(bytes == null ? new byte[0] : bytes));
                return Async.completed(null);
            } catch (RuntimeException exception) {
                return Async.failed(exception);
            }
        }

        @Override
        public Async<Void> close(int statusCode, String reason) {
            if (!open) {
                return Async.completed(null);
            }
            open = false;
            BrowserWebSocketTransport.close(socketId, statusCode, reason == null ? "" : reason);
            return Async.completed(null);
        }

        @Override
        public boolean isOpen() {
            return open;
        }
    }
}
