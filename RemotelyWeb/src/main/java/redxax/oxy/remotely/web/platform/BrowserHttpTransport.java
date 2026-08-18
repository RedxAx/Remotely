package redxax.oxy.remotely.web.platform;

import org.teavm.jso.JSBody;
import org.teavm.jso.JSFunctor;
import org.teavm.jso.JSObject;
import restudio.rebase.platform.Async;
import restudio.rebase.platform.http.HttpEndpointPolicy;
import restudio.rebase.platform.http.HttpHeaders;
import restudio.rebase.platform.http.HttpRequest;
import restudio.rebase.platform.http.HttpResponse;
import restudio.rebase.platform.http.HttpTransport;
import restudio.rescreen.platform.browser.BrowserFile;

import java.net.URI;
import java.time.Duration;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;

public final class BrowserHttpTransport implements HttpTransport {
    private static final char[] BASE64_ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/".toCharArray();
    private static final int BASE64_CHUNK_BYTES = 192 * 1024;
    private static final int MAX_PENDING_REQUESTS = 64;
    private static final Object REGISTRY_LOCK = new Object();
    private static final Map<Integer, Pending<?>> PENDING = Collections.synchronizedMap(new HashMap<>());
    private static final Map<Integer, FileUploadPending> FILE_UPLOADS = Collections.synchronizedMap(new HashMap<>());
    private static final Map<Integer, StreamingPending> STREAMING = Collections.synchronizedMap(new HashMap<>());
    private static int nextRequestId = 1;
    private final Set<Integer> ownedRequests = new HashSet<>();
    private final HttpEndpointPolicy endpointPolicy;

    public BrowserHttpTransport() {
        this(new BrowserHttpPolicy());
    }

    public BrowserHttpTransport(HttpEndpointPolicy endpointPolicy) {
        this.endpointPolicy = endpointPolicy == null ? new BrowserHttpPolicy() : endpointPolicy;
    }

    @Override
    public <T> Async<HttpResponse<T>> sendAsync(HttpRequest request, HttpResponse.BodyHandler<T> bodyHandler) {
        Async<HttpResponse<T>> future = Async.pending();
        if (request == null || bodyHandler == null) {
            future.fail(new IllegalArgumentException("HTTP request and body handler are required"));
            return future;
        }
        if (!endpointPolicy.permits(request.uri())) {
            future.fail(new IllegalArgumentException("Browser HTTP Endpoint Is Not Allowed"));
            return future;
        }
        int requestId = nextId();
        Pending<T> pending = new Pending<>(this, future, bodyHandler);
        if (!reservePending(requestId, pending)) {
            future.fail(new IllegalStateException("Browser HTTP Request Budget Is Busy"));
            return future;
        }
        ownedRequests.add(requestId);
        future.onCancel(() -> {
            PENDING.remove(requestId);
            ownedRequests.remove(requestId);
            abort(requestId);
        });
        byte[] serializedBody;
        try {
            serializedBody = requestBody(request);
        } catch (Throwable failure) {
            PENDING.remove(requestId);
            ownedRequests.remove(requestId);
            future.fail(failure);
            return future;
        }
        Async<String> body = encodeBody(serializedBody);
        future.onCancel(body::cancel);
        body.whenComplete((encoded, failure) -> {
            if (PENDING.get(requestId) != pending) return;
            if (failure != null) {
                PENDING.remove(requestId);
                ownedRequests.remove(requestId);
                future.fail(failure);
                return;
            }
            long timeout = request.timeout().map(Duration::toMillis).orElse(30_000L);
            try {
                fetchBrowser(requestId, request.uri().toString(), request.method(), headersJson(request.uri(), request.headers().map()),
                        encoded, timeout, callback(requestId));
            } catch (Throwable requestFailure) {
                PENDING.remove(requestId);
                ownedRequests.remove(requestId);
                future.fail(requestFailure);
            }
        });
        return future;
    }

    Async<HttpResponse<Void>> sendBrowserFile(HttpRequest request, BrowserFile file, BiConsumer<Long, Long> progress) {
        Async<HttpResponse<Void>> future = Async.pending();
        if (request == null || file == null || file.selectionId() <= 0 || file.fileIndex() < 0) {
            future.fail(new IllegalArgumentException("Browser File Handle Is Unavailable"));
            return future;
        }
        if (!endpointPolicy.permits(request.uri())) {
            future.fail(new IllegalArgumentException("Browser HTTP Endpoint Is Not Allowed"));
            return future;
        }
        int requestId = nextId();
        FileUploadPending pending = new FileUploadPending(this, future, progress);
        if (!reserveUpload(requestId, pending)) {
            future.fail(new IllegalStateException("Browser HTTP Request Budget Is Busy"));
            return future;
        }
        ownedRequests.add(requestId);
        future.onCancel(() -> {
            FILE_UPLOADS.remove(requestId);
            ownedRequests.remove(requestId);
            abort(requestId);
        });
        long timeout = request.timeout().map(Duration::toMillis).orElse(30_000L);
        try {
            fetchBrowserFile(requestId, request.uri().toString(), request.method(), headersJson(request.uri(), request.headers().map()),
                    file.selectionId(), file.fileIndex(), timeout, callback(requestId));
        } catch (Throwable failure) {
            FILE_UPLOADS.remove(requestId);
            ownedRequests.remove(requestId);
            future.fail(failure);
        }
        return future;
    }

    @Override
    public Async<HttpResponse<Void>> sendStreaming(HttpRequest request, HttpResponse.ChunkConsumer consumer) {
        if (request == null || consumer == null) {
            return Async.failed(new IllegalArgumentException("HTTP chunk consumer is required"));
        }
        if (!endpointPolicy.permits(request.uri())) {
            return Async.failed(new IllegalArgumentException("Browser HTTP Endpoint Is Not Allowed"));
        }
        Async<HttpResponse<Void>> result = Async.pending();
        int requestId = nextId();
        StreamingPending pending = new StreamingPending(this, result, consumer);
        if (!reserveStreaming(requestId, pending)) {
            result.fail(new IllegalStateException("Browser HTTP Request Budget Is Busy"));
            return result;
        }
        ownedRequests.add(requestId);
        result.onCancel(() -> {
            STREAMING.remove(requestId);
            ownedRequests.remove(requestId);
            abort(requestId);
        });
        String body;
        try {
            byte[] serializedBody = requestBody(request);
            body = serializedBody == null ? null : encodeBase64(serializedBody);
        } catch (Throwable failure) {
            STREAMING.remove(requestId);
            ownedRequests.remove(requestId);
            result.fail(failure);
            return result;
        }
        long timeout = request.timeout().map(Duration::toMillis).orElse(30_000L);
        try {
            fetchStreaming(requestId, request.uri().toString(), request.method(), headersJson(request.uri(), request.headers().map()), body,
                    timeout, callback(requestId));
        } catch (Throwable failure) {
            STREAMING.remove(requestId);
            ownedRequests.remove(requestId);
            result.fail(failure);
        }
        return result;
    }

    public static void streamStart(int requestId, int statusCode, String contentLength, String location, String contentType) {
        streamStart(requestId, statusCode, contentLength, location, contentType, null, null, null);
    }

    public static void streamStart(int requestId, int statusCode, String contentLength, String location, String contentType,
                                   String retryAfter, String rateLimitReset, String rateLimitResetAfter) {
        StreamingPending pending = STREAMING.get(requestId);
        if (pending == null) return;
        pending.statusCode = statusCode;
        pending.headers = responseHeaders(contentLength, location, contentType, retryAfter, rateLimitReset, rateLimitResetAfter);
    }

    public static void streamChunk(int requestId, String body) {
        StreamingPending pending = STREAMING.get(requestId);
        if (pending == null) return;
        try {
            pending.consumer.accept(decodeBase64(body));
        } catch (Exception exception) {
            STREAMING.remove(requestId);
            pending.owner.ownedRequests.remove(requestId);
            abort(requestId);
            pending.future.fail(exception);
        }
    }

    public static void streamComplete(int requestId) {
        StreamingPending pending = STREAMING.remove(requestId);
        if (pending != null) {
            pending.owner.ownedRequests.remove(requestId);
            pending.future.complete(new HttpResponse<>(pending.statusCode, pending.headers, null));
        }
    }

    public static void complete(int requestId, String body, int statusCode, String contentLength, String location, String contentType) {
        complete(requestId, body, statusCode, contentLength, location, contentType, null, null, null);
    }

    public static void complete(int requestId, String body, int statusCode, String contentLength, String location, String contentType,
                                String retryAfter, String rateLimitReset, String rateLimitResetAfter) {
        Pending<?> pending = PENDING.remove(requestId);
        if (pending == null) {
            return;
        }
        pending.owner.ownedRequests.remove(requestId);
        try {
            HttpHeaders headers = responseHeaders(contentLength, location, contentType, retryAfter, rateLimitReset, rateLimitResetAfter);
            byte[] bytes = decodeBase64(body);
            completePending(pending, statusCode, headers, bytes);
        } catch (Exception exception) {
            pending.future.fail(exception);
        }
    }

    public static void complete(int requestId, String body, int statusCode, String contentLength, String location, String contentType, String ignored) {
        complete(requestId, body, statusCode, contentLength, location, contentType);
    }

    public static void progress(int requestId, long loaded, long total) {
        FileUploadPending pending = FILE_UPLOADS.get(requestId);
        if (pending != null && pending.progress != null) pending.progress.accept(Math.max(0L, loaded), Math.max(0L, total));
    }

    public static void completeFileUpload(int requestId, int statusCode, String contentLength, String location, String contentType) {
        completeFileUpload(requestId, statusCode, contentLength, location, contentType, null, null, null);
    }

    public static void completeFileUpload(int requestId, int statusCode, String contentLength, String location, String contentType,
                                          String retryAfter, String rateLimitReset, String rateLimitResetAfter) {
        FileUploadPending pending = FILE_UPLOADS.remove(requestId);
        if (pending == null) return;
        pending.owner.ownedRequests.remove(requestId);
        HttpHeaders headers = responseHeaders(contentLength, location, contentType, retryAfter, rateLimitReset, rateLimitResetAfter);
        pending.future.complete(new HttpResponse<>(statusCode, headers, null));
    }

    public static void fail(int requestId, String message) {
        Pending<?> pending = PENDING.remove(requestId);
        if (pending != null) {
            pending.owner.ownedRequests.remove(requestId);
            pending.future.fail(new IllegalStateException(message == null ? "Browser request failed" : message));
            return;
        }
        StreamingPending streaming = STREAMING.remove(requestId);
        if (streaming != null) {
            streaming.owner.ownedRequests.remove(requestId);
            streaming.future.fail(new IllegalStateException(message == null ? "Browser request failed" : message));
            return;
        }
        FileUploadPending upload = FILE_UPLOADS.remove(requestId);
        if (upload != null) {
            upload.owner.ownedRequests.remove(requestId);
            upload.future.fail(new IllegalStateException(message == null ? "Browser request failed" : message));
        }
    }

    public void cancelAll() {
        for (Integer requestId : Set.copyOf(ownedRequests)) {
            Pending<?> pending = PENDING.remove(requestId);
            StreamingPending streaming = STREAMING.remove(requestId);
            FileUploadPending upload = FILE_UPLOADS.remove(requestId);
            ownedRequests.remove(requestId);
            abort(requestId);
            if (pending != null) {
                pending.future.fail(new IllegalStateException("Browser Runtime Closed"));
            }
            if (streaming != null) {
                streaming.future.fail(new IllegalStateException("Browser Runtime Closed"));
            }
            if (upload != null) {
                upload.future.fail(new IllegalStateException("Browser Runtime Closed"));
            }
        }
    }

    @SuppressWarnings("unchecked")
    private static <T> void completePending(Pending<?> pending, int statusCode, HttpHeaders headers, byte[] bytes) throws Exception {
        Pending<T> typed = (Pending<T>) pending;
        T body = typed.bodyHandler.apply(bytes);
        typed.future.complete(new HttpResponse<>(statusCode, headers, body));
    }

    private static int nextId() {
        int requestId = nextRequestId++;
        if (requestId <= 0) {
            nextRequestId = 2;
            requestId = 1;
        }
        return requestId;
    }

    private static boolean reservePending(int requestId, Pending<?> pending) {
        synchronized (REGISTRY_LOCK) {
            if (pendingRequestCount() >= MAX_PENDING_REQUESTS) return false;
            PENDING.put(requestId, pending);
            return true;
        }
    }

    private static boolean reserveUpload(int requestId, FileUploadPending pending) {
        synchronized (REGISTRY_LOCK) {
            if (pendingRequestCount() >= MAX_PENDING_REQUESTS) return false;
            FILE_UPLOADS.put(requestId, pending);
            return true;
        }
    }

    private static boolean reserveStreaming(int requestId, StreamingPending pending) {
        synchronized (REGISTRY_LOCK) {
            if (pendingRequestCount() >= MAX_PENDING_REQUESTS) return false;
            STREAMING.put(requestId, pending);
            return true;
        }
    }

    private static int pendingRequestCount() {
        return PENDING.size() + FILE_UPLOADS.size() + STREAMING.size();
    }

    private String headersJson(URI uri, Map<String, List<String>> headers) {
        StringBuilder json = new StringBuilder("{");
        boolean first = true;
        for (Map.Entry<String, List<String>> entry : headers.entrySet()) {
            if (isBrowserForbiddenHeader(entry.getKey())) {
                continue;
            }
            for (String value : entry.getValue()) {
                if (!first) {
                    json.append(',');
                }
                first = false;
                json.append('"').append(escapeJson(entry.getKey())).append('"').append(':');
                json.append('"').append(escapeJson(value)).append('"');
            }
        }
        if (endpointPolicy.permitsCredentials(uri) && !containsHeader(headers, "X-Remotely-Web-Ticket")) {
            String ticket = BrowserLaunchSession.ticket();
            if (ticket != null && !ticket.isBlank()) {
                if (!first) {
                    json.append(',');
                }
                json.append('"').append("X-Remotely-Web-Ticket").append('"').append(':');
                json.append('"').append(escapeJson(ticket)).append('"');
            }
        }
        return json.append('}').toString();
    }

    private static boolean containsHeader(Map<String, List<String>> headers, String name) {
        return headers.keySet().stream().anyMatch(value -> value != null && value.equalsIgnoreCase(name));
    }

    private static boolean isBrowserForbiddenHeader(String name) {
        return "Authorization".equalsIgnoreCase(name)
                || "X-Api-Key".equalsIgnoreCase(name)
                || "User-Agent".equalsIgnoreCase(name)
                || "Content-Length".equalsIgnoreCase(name)
                || "Host".equalsIgnoreCase(name)
                || "Origin".equalsIgnoreCase(name)
                || "Referer".equalsIgnoreCase(name);
    }

    private static String escapeJson(String value) {
        if (value == null) {
            return "";
        }
        StringBuilder escaped = new StringBuilder(value.length());
        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            switch (character) {
                case '\\' -> escaped.append("\\\\");
                case '"' -> escaped.append("\\\"");
                case '\n' -> escaped.append("\\n");
                case '\r' -> escaped.append("\\r");
                case '\t' -> escaped.append("\\t");
                default -> escaped.append(character);
            }
        }
        return escaped.toString();
    }

    static String encodeBase64(byte[] bytes) {
        StringBuilder result = new StringBuilder((bytes.length + 2) / 3 * 4);
        appendBase64(bytes, 0, bytes.length, result);
        return result.toString();
    }

    private static byte[] requestBody(HttpRequest request) {
        if (request == null) return null;
        return request.bodyPublisher().map(HttpRequest.BodyPublisher::bytes).orElse(null);
    }

    private static Async<String> encodeBody(byte[] bytes) {
        return bytes == null ? Async.completed(null) : encodeBase64Async(bytes);
    }

    private static Async<String> encodeBase64Async(byte[] bytes) {
        Async<String> result = Async.pending();
        encodeBase64Chunk(bytes, 0, new StringBuilder((bytes.length + 2) / 3 * 4), result);
        return result;
    }

    private static void encodeBase64Chunk(byte[] bytes, int offset, StringBuilder result, Async<String> future) {
        if (future.isDone()) return;
        Async<Void> turn;
        try {
            turn = Async.supplyAsync(() -> null);
        } catch (Throwable failure) {
            future.fail(failure);
            return;
        }
        future.onCancel(turn::cancel);
        turn.whenComplete((ignored, failure) -> {
            if (future.isDone()) return;
            if (failure != null) {
                future.fail(failure);
                return;
            }
            try {
                int end = Math.min(offset + BASE64_CHUNK_BYTES, bytes.length);
                appendBase64(bytes, offset, end, result);
                if (end >= bytes.length) future.complete(result.toString());
                else encodeBase64Chunk(bytes, end, result, future);
            } catch (Throwable encodingFailure) {
                future.fail(encodingFailure);
            }
        });
    }

    private static void appendBase64(byte[] bytes, int start, int end, StringBuilder result) {
        for (int index = start; index < end; index += 3) {
            int first = bytes[index] & 0xff;
            int second = index + 1 < end ? bytes[index + 1] & 0xff : 0;
            int third = index + 2 < end ? bytes[index + 2] & 0xff : 0;
            result.append(BASE64_ALPHABET[first >>> 2]);
            result.append(BASE64_ALPHABET[((first & 3) << 4) | (second >>> 4)]);
            result.append(index + 1 < end ? BASE64_ALPHABET[((second & 15) << 2) | (third >>> 6)] : '=');
            result.append(index + 2 < end ? BASE64_ALPHABET[third & 63] : '=');
        }
    }

    static byte[] decodeBase64(String value) {
        if (value == null || value.isEmpty()) {
            return new byte[0];
        }
        int padding = value.endsWith("==") ? 2 : value.endsWith("=") ? 1 : 0;
        int length = value.length() / 4 * 3 - padding;
        byte[] result = new byte[length];
        int resultIndex = 0;
        for (int index = 0; index < value.length(); index += 4) {
            int first = base64Value(value.charAt(index));
            int second = base64Value(value.charAt(index + 1));
            int third = value.charAt(index + 2) == '=' ? 0 : base64Value(value.charAt(index + 2));
            int fourth = value.charAt(index + 3) == '=' ? 0 : base64Value(value.charAt(index + 3));
            if (resultIndex < result.length) {
                result[resultIndex++] = (byte) ((first << 2) | (second >>> 4));
            }
            if (resultIndex < result.length) {
                result[resultIndex++] = (byte) ((second << 4) | (third >>> 2));
            }
            if (resultIndex < result.length) {
                result[resultIndex++] = (byte) ((third << 6) | fourth);
            }
        }
        return result;
    }

    private static int base64Value(char value) {
        if (value >= 'A' && value <= 'Z') return value - 'A';
        if (value >= 'a' && value <= 'z') return value - 'a' + 26;
        if (value >= '0' && value <= '9') return value - '0' + 52;
        if (value == '+') return 62;
        if (value == '/') return 63;
        throw new IllegalArgumentException("Invalid Base64 response");
    }

    private static BrowserCallback callback(int requestId) {
        return event -> dispatch(requestId, event);
    }

    private static void dispatch(int requestId, BrowserResponseEvent event) {
        if (event == null) return;
        switch (eventType(event)) {
            case "complete" -> complete(requestId, eventBody(event), eventStatus(event), eventContentLength(event), eventLocation(event),
                    eventContentType(event), eventRetryAfter(event), eventRateLimitReset(event), eventRateLimitResetAfter(event));
            case "complete-file" -> completeFileUpload(requestId, eventStatus(event), eventContentLength(event), eventLocation(event),
                    eventContentType(event), eventRetryAfter(event), eventRateLimitReset(event), eventRateLimitResetAfter(event));
            case "fail" -> fail(requestId, eventMessage(event));
            case "progress" -> progressValue(requestId, eventLoaded(event), eventTotal(event));
            case "stream-start" -> streamStart(requestId, eventStatus(event), eventContentLength(event), eventLocation(event), eventContentType(event),
                    eventRetryAfter(event), eventRateLimitReset(event), eventRateLimitResetAfter(event));
            case "stream-chunk" -> streamChunk(requestId, eventBody(event));
            case "stream-complete" -> streamComplete(requestId);
            default -> {
            }
        }
    }

    private static void progressValue(int requestId, String loaded, String total) {
        progress(requestId, parseByteValue(loaded), parseByteValue(total));
    }

    private static long parseByteValue(String value) {
        if (value == null || value.isBlank()) return 0L;
        try {
            return Math.max(0L, Long.parseLong(value.strip()));
        } catch (NumberFormatException ignored) {
            return 0L;
        }
    }

    private static HttpHeaders responseHeaders(String contentLength, String location, String contentType,
                                               String retryAfter, String rateLimitReset, String rateLimitResetAfter) {
        HttpHeaders headers = HttpHeaders.empty();
        if (contentLength != null && !contentLength.isEmpty()) headers = headers.with("Content-Length", contentLength);
        if (location != null && !location.isEmpty()) headers = headers.with("Location", location);
        if (contentType != null && !contentType.isEmpty()) headers = headers.with("Content-Type", contentType);
        if (retryAfter != null && !retryAfter.isEmpty()) headers = headers.with("Retry-After", retryAfter);
        if (rateLimitReset != null && !rateLimitReset.isEmpty()) headers = headers.with("RateLimit-Reset", rateLimitReset);
        if (rateLimitResetAfter != null && !rateLimitResetAfter.isEmpty()) {
            headers = headers.with("X-RateLimit-Reset-After", rateLimitResetAfter);
        }
        return headers;
    }

    @JSBody(params = {"event"}, script = "return event && event.type || '';" )
    private static native String eventType(BrowserResponseEvent event);

    @JSBody(params = {"event"}, script = "return event && event.body || '';" )
    private static native String eventBody(BrowserResponseEvent event);

    @JSBody(params = {"event"}, script = "return Number(event && event.status || 0);" )
    private static native int eventStatus(BrowserResponseEvent event);

    @JSBody(params = {"event"}, script = "return event && event.contentLength || '';" )
    private static native String eventContentLength(BrowserResponseEvent event);

    @JSBody(params = {"event"}, script = "return event && event.location || '';" )
    private static native String eventLocation(BrowserResponseEvent event);

    @JSBody(params = {"event"}, script = "return event && event.contentType || '';" )
    private static native String eventContentType(BrowserResponseEvent event);

    @JSBody(params = {"event"}, script = "return event && event.message || '';" )
    private static native String eventMessage(BrowserResponseEvent event);

    @JSBody(params = {"event"}, script = "return !event || event.loaded === undefined || event.loaded === null ? '0' : String(event.loaded);" )
    private static native String eventLoaded(BrowserResponseEvent event);

    @JSBody(params = {"event"}, script = "return !event || event.total === undefined || event.total === null ? '0' : String(event.total);" )
    private static native String eventTotal(BrowserResponseEvent event);

    @JSBody(params = {"event"}, script = "return event && event.retryAfter || '';" )
    private static native String eventRetryAfter(BrowserResponseEvent event);

    @JSBody(params = {"event"}, script = "return event && event.rateLimitReset || '';" )
    private static native String eventRateLimitReset(BrowserResponseEvent event);

    @JSBody(params = {"event"}, script = "return event && event.rateLimitResetAfter || '';" )
    private static native String eventRateLimitResetAfter(BrowserResponseEvent event);

    @JSBody(params = {"requestId", "url", "method", "headersJson", "selectionId", "fileIndex", "timeout", "callback"}, script = """
            var controllers = window.__remotelyFetchControllers || (window.__remotelyFetchControllers = {});
            var key = String(requestId);
            var target = new URL(url, window.location.href);
            var backendValue = window.__remotelyBackendOrigin || 'https://restudiomc.net';
            var backend = '';
            try { backend = new URL(backendValue, window.location.href).origin; } catch (error) { backend = ''; }
            var selections = window.__reScreenFileSelections;
            var files = selections && selections[String(selectionId)];
            var file = files && files[fileIndex];
            if (!file) {
                callback({type: 'fail', message: 'Browser file handle is unavailable'});
            } else {
                var xhr = new XMLHttpRequest();
                controllers[key] = xhr;
                xhr.open(method, target.toString(), true);
                xhr.withCredentials = target.origin === backend;
                if (timeout > 0) xhr.timeout = Number(timeout);
                var headers = {};
                try { headers = JSON.parse(headersJson || '{}'); } catch (error) { headers = {}; }
                Object.keys(headers).forEach(function(name) { try { xhr.setRequestHeader(name, headers[name]); } catch (error) {} });
                xhr.upload.onprogress = function(event) {
                    var total = event.lengthComputable ? event.total : Number(file.size || 0);
                    var byteValue = function(value) {
                        var numeric = Number(value);
                        return Number.isFinite(numeric) && numeric > 0 ? BigInt(Math.floor(numeric)) : BigInt(0);
                    };
                    callback({type: 'progress', loaded: byteValue(event.loaded), total: byteValue(total)});
                };
                var finished = false;
                var finish = function() {
                    if (finished) return false;
                    finished = true;
                    if (controllers[key] === xhr) delete controllers[key];
                    return true;
                };
                xhr.onload = function() {
                    if (!finish()) return;
                    callback({type: 'complete-file', status: xhr.status, contentLength: xhr.getResponseHeader('content-length') || '', location: xhr.getResponseHeader('location') || '', contentType: xhr.getResponseHeader('content-type') || '', retryAfter: xhr.getResponseHeader('retry-after') || '', rateLimitReset: xhr.getResponseHeader('ratelimit-reset') || xhr.getResponseHeader('x-ratelimit-reset') || xhr.getResponseHeader('x-rate-limit-reset') || '', rateLimitResetAfter: xhr.getResponseHeader('x-ratelimit-reset-after') || xhr.getResponseHeader('x-rate-limit-reset-after') || ''});
                };
                xhr.onerror = function() { if (finish()) callback({type: 'fail', message: 'Browser file upload failed'}); };
                xhr.ontimeout = function() { if (finish()) callback({type: 'fail', message: 'Browser file upload timed out'}); };
                xhr.onabort = function() { if (finish()) callback({type: 'fail', message: 'Browser file upload cancelled'}); };
                xhr.send(file);
            }
            """)
    private static native void fetchBrowserFile(int requestId, String url, String method, String headersJson,
                                                int selectionId, int fileIndex, long timeout, BrowserCallback callback);

    @JSBody(params = {"requestId", "url", "method", "headersJson", "body", "timeout", "callback"}, script = """
            var controllers = window.__remotelyFetchControllers || (window.__remotelyFetchControllers = {});
            var key = String(requestId);
            var target = new URL(url, window.location.href);
            var backendValue = window.__remotelyBackendOrigin || 'https://restudiomc.net';
            var backend = '';
            try { backend = new URL(backendValue, window.location.href).origin; } catch (error) { backend = ''; }
            var options = {method: method, headers: JSON.parse(headersJson || '{}'), credentials: target.origin === backend ? 'include' : 'omit'};
            var controller = typeof AbortController === 'undefined' ? null : new AbortController();
            if (controller) {
                options.signal = controller.signal;
                controllers[key] = controller;
            }
            var timer = 0;
            if (timeout > 0) timer = window.setTimeout(function() {
                if (controller) {
                    controller.abort();
                }
                finish();
                callback({type: 'fail', message: 'Browser request timed out'});
            }, Number(timeout));
            function finish() {
                if (timer) window.clearTimeout(timer);
                if (controllers[key] === controller) delete controllers[key];
            }
            function decodeBody() {
                if (body === null || body === undefined) return Promise.resolve();
                var parts = [];
                var offset = 0;
                var chunkSize = 262144;
                return new Promise(function(resolve, reject) {
                    function next() {
                        if (controller && controller.signal.aborted) {
                            reject(new Error('Browser request cancelled'));
                            return;
                        }
                        try {
                            var end = Math.min(offset + chunkSize, body.length);
                            var raw = atob(body.substring(offset, end));
                            var bytes = new Uint8Array(raw.length);
                            for (var index = 0; index < raw.length; index++) bytes[index] = raw.charCodeAt(index);
                            parts.push(bytes);
                            offset = end;
                            if (offset >= body.length) {
                                options.body = new Blob(parts);
                                resolve();
                            } else {
                                window.setTimeout(next, 0);
                            }
                        } catch (error) {
                            reject(error);
                        }
                    }
                    window.setTimeout(next, 0);
                });
            }
            decodeBody().then(function() { return fetch(target.toString(), options); }).then(function(response) {
                return response.arrayBuffer().then(function(buffer) {
                    var bytes = new Uint8Array(buffer);
                    var raw = '';
                    var chunk = 32768;
                    for (var index = 0; index < bytes.length; index += chunk) raw += String.fromCharCode.apply(null, bytes.subarray(index, Math.min(index + chunk, bytes.length)));
                    finish();
                    callback({type: 'complete', body: btoa(raw), status: response.status, contentLength: response.headers.get('content-length') || '', location: response.headers.get('location') || '', contentType: response.headers.get('content-type') || '', retryAfter: response.headers.get('retry-after') || '', rateLimitReset: response.headers.get('ratelimit-reset') || response.headers.get('x-ratelimit-reset') || response.headers.get('x-rate-limit-reset') || '', rateLimitResetAfter: response.headers.get('x-ratelimit-reset-after') || response.headers.get('x-rate-limit-reset-after') || ''});
                });
            }).catch(function(error) {
                finish();
                callback({type: 'fail', message: String(error && (error.message || error))});
            });
            """)
    private static native void fetchBrowser(int requestId, String url, String method, String headersJson, String body, long timeout,
                                            BrowserCallback callback);

    @JSBody(params = {"requestId", "url", "method", "headersJson", "body", "timeout", "callback"}, script = """
            var controllers = window.__remotelyFetchControllers || (window.__remotelyFetchControllers = {});
            var key = String(requestId);
            var target = new URL(url, window.location.href);
            var backendValue = window.__remotelyBackendOrigin || 'https://restudiomc.net';
            var backend = '';
            try { backend = new URL(backendValue, window.location.href).origin; } catch (error) { backend = ''; }
            var options = {method: method, headers: JSON.parse(headersJson || '{}'), credentials: target.origin === backend ? 'include' : 'omit'};
            if (body !== null && body !== undefined) {
                var bodyRaw = atob(body);
                var bodyBytes = new Uint8Array(bodyRaw.length);
                for (var bodyIndex = 0; bodyIndex < bodyRaw.length; bodyIndex++) bodyBytes[bodyIndex] = bodyRaw.charCodeAt(bodyIndex);
                options.body = bodyBytes;
            }
            var controller = typeof AbortController === 'undefined' ? null : new AbortController();
            if (controller) {
                options.signal = controller.signal;
                controllers[key] = controller;
            }
            function encode(bytes) {
                var raw = '';
                var chunk = 32768;
                for (var index = 0; index < bytes.length; index += chunk) raw += String.fromCharCode.apply(null, bytes.subarray(index, Math.min(index + chunk, bytes.length)));
                return btoa(raw);
            }
            function finish() {
                if (timer) window.clearTimeout(timer);
                if (controllers[key] === controller) delete controllers[key];
                callback({type: 'stream-complete'});
            }
            var timer = 0;
            if (timeout > 0) timer = window.setTimeout(function() {
                if (controller) {
                    controller.abort();
                }
                if (controllers[key] === controller) delete controllers[key];
                callback({type: 'fail', message: 'Browser request timed out'});
            }, Number(timeout));
            Promise.resolve().then(function() { return fetch(target.toString(), options); }).then(function(response) {
                callback({type: 'stream-start', status: response.status, contentLength: response.headers.get('content-length') || '', location: response.headers.get('location') || '', contentType: response.headers.get('content-type') || '', retryAfter: response.headers.get('retry-after') || '', rateLimitReset: response.headers.get('ratelimit-reset') || response.headers.get('x-ratelimit-reset') || response.headers.get('x-rate-limit-reset') || '', rateLimitResetAfter: response.headers.get('x-ratelimit-reset-after') || response.headers.get('x-rate-limit-reset-after') || ''});
                var reader = response.body && response.body.getReader ? response.body.getReader() : null;
                if (!reader) return response.arrayBuffer().then(function(buffer) {
                    callback({type: 'stream-chunk', body: encode(new Uint8Array(buffer))});
                });
                function pump() {
                    return reader.read().then(function(part) {
                        if (part.done) return null;
                        callback({type: 'stream-chunk', body: encode(part.value)});
                        return pump();
                    });
                }
                return pump();
            }).then(function() {
                finish();
            }).catch(function(error) {
                if (timer) window.clearTimeout(timer);
                if (controllers[key] === controller) delete controllers[key];
                callback({type: 'fail', message: String(error && (error.message || error))});
            });
            """)
    private static native void fetchStreaming(int requestId, String url, String method, String headersJson, String body, long timeout,
                                               BrowserCallback callback);

    @JSBody(params = {"requestId"}, script = "var controllers = window.__remotelyFetchControllers; if (controllers) { var key = String(requestId); var controller = controllers[key]; if (controller) controller.abort(); delete controllers[key]; }")
    private static native void abort(int requestId);

    private record Pending<T>(BrowserHttpTransport owner, Async<HttpResponse<T>> future, HttpResponse.BodyHandler<T> bodyHandler) {
    }

    @JSFunctor
    public interface BrowserCallback extends JSObject {
        void accept(BrowserResponseEvent event);
    }

    public interface BrowserResponseEvent extends JSObject {
    }

    private static final class FileUploadPending {
        private final BrowserHttpTransport owner;
        private final Async<HttpResponse<Void>> future;
        private final BiConsumer<Long, Long> progress;

        private FileUploadPending(BrowserHttpTransport owner, Async<HttpResponse<Void>> future, BiConsumer<Long, Long> progress) {
            this.owner = owner;
            this.future = future;
            this.progress = progress;
        }
    }

    private static final class StreamingPending {
        private final BrowserHttpTransport owner;
        private final Async<HttpResponse<Void>> future;
        private final HttpResponse.ChunkConsumer consumer;
        private int statusCode;
        private HttpHeaders headers = HttpHeaders.empty();

        private StreamingPending(BrowserHttpTransport owner, Async<HttpResponse<Void>> future, HttpResponse.ChunkConsumer consumer) {
            this.owner = owner;
            this.future = future;
            this.consumer = consumer;
        }
    }
}
