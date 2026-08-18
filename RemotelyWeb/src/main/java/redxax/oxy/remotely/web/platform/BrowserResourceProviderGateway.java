package redxax.oxy.remotely.web.platform;

import restudio.rebase.platform.Async;
import restudio.rebase.platform.Sha256;
import restudio.rebase.platform.http.HttpRequest;
import restudio.rebase.platform.http.HttpResponse;
import restudio.rebase.platform.http.HttpTransport;
import restudio.rebase.resource.provider.ResourceProviderException;
import restudio.rebase.resource.provider.ResourceProviderGateway;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Queue;
import java.util.Set;
import java.util.function.Supplier;

final class BrowserResourceProviderGateway implements ResourceProviderGateway {
    private static final int MAX_ACTIVE_REQUESTS = 2;
    private static final int MAX_PENDING_REQUESTS = 24;
    private static final int MAX_COALESCED_REQUESTS = 128;
    private static final int MAX_COOLDOWNS = 128;
    private static final Duration DEFAULT_COOLDOWN = Duration.ofSeconds(5);
    private final HttpTransport transport;
    private final Object lock = new Object();
    private final Queue<Request<?>> pending = new ArrayDeque<>();
    private final Map<String, InFlightRequest<?>> coalesced = new HashMap<>();
    private final Map<String, Cooldown> cooldowns = new HashMap<>();
    private final Set<Request<?>> active = new HashSet<>();
    private final Runnable authStateListener = this::invalidateAuthState;
    private final Runnable ticketListener = this::invalidateAuthState;
    private final Runnable expiryListener = this::invalidateAuthState;
    private long authGeneration = 1L;
    private int activeRequests;
    private boolean closed;

    BrowserResourceProviderGateway(HttpTransport transport) {
        this.transport = Objects.requireNonNull(transport, "transport");
        BrowserLaunchSession.addAuthStateListener(authStateListener);
        BrowserLaunchSession.addTicketListener(ticketListener);
        BrowserLaunchSession.addSessionExpiryListener(expiryListener);
    }

    @Override
    public <T> Async<HttpResponse<T>> send(String provider, HttpRequest request, HttpResponse.BodyHandler<T> bodyHandler) {
        if (request == null || bodyHandler == null) return Async.failed(new IllegalArgumentException("Resource Provider Request Is Required"));
        Async<HttpResponse<T>> result;
        synchronized (lock) {
            if (closed) return Async.failed(new IllegalStateException("Resource Provider Gateway Is Closed"));
            if (pending.size() >= MAX_PENDING_REQUESTS) return cooldownFailure(provider, "Resource Provider Request Budget Is Busy");
            SessionContext context = sessionContext(authGeneration);
            String handlerKey;
            try {
                handlerKey = bodyHandler.coalescingKey();
            } catch (Throwable failure) {
                return Async.failed(failure);
            }
            if (handlerKey == null || handlerKey.isBlank()) {
                result = Async.pending();
                Request<T> operation = new Request<>(provider, requestKey(provider, request, context, ""), result,
                        null, () -> transport.sendAsync(proxyRequest(provider, request, context), bodyHandler), false, context);
                pending.add(operation);
                result.onCancel(() -> cancel(operation));
            } else {
                String key = requestKey(provider, request, context, handlerKey);
                InFlightRequest<?> existing = coalesced.get(key);
                if (existing != null) return subscribe(existing);
                if (coalesced.size() >= MAX_COALESCED_REQUESTS) {
                    return cooldownFailure(provider, "Resource Provider Request Coalescing Budget Is Busy");
                }
                InFlightRequest<T> inFlight = new InFlightRequest<>();
                Request<T> operation = new Request<>(provider, key, inFlight.result,
                        inFlight, () -> transport.sendAsync(proxyRequest(provider, request, context), bodyHandler), true, context);
                inFlight.operation = operation;
                coalesced.put(key, inFlight);
                pending.add(operation);
                result = subscribe(inFlight);
            }
        }
        drain();
        return result;
    }

    @Override
    public Async<HttpResponse<Void>> sendStreaming(String provider, HttpRequest request, HttpResponse.ChunkConsumer consumer) {
        if (request == null || consumer == null) return Async.failed(new IllegalArgumentException("Resource Provider Request Is Required"));
        Async<HttpResponse<Void>> result;
        synchronized (lock) {
            if (closed) return Async.failed(new IllegalStateException("Resource Provider Gateway Is Closed"));
            if (pending.size() >= MAX_PENDING_REQUESTS) return cooldownFailure(provider, "Resource Provider Request Budget Is Busy");
            SessionContext context = sessionContext(authGeneration);
            result = Async.pending();
            Request<Void> operation = new Request<>(provider, requestKey(provider, request, context, "streaming"), result,
                    null, () -> transport.sendStreaming(proxyRequest(provider, request, context), consumer), false, context);
            pending.add(operation);
            result.onCancel(() -> cancel(operation));
        }
        drain();
        return result;
    }

    private void cancel(Request<?> operation) {
        Async<?> active = null;
        boolean pendingOperation = false;
        synchronized (lock) {
            if (!operation.started) {
                pending.remove(operation);
                removeCoalesced(operation);
                operation.finished = true;
                pendingOperation = true;
            } else {
                operation.cancelled = true;
                active = operation.active;
            }
        }
        if (active != null) active.cancel();
        if (pendingOperation) drain();
    }

    private void drain() {
        while (true) {
            Request<?> operation;
            synchronized (lock) {
                if (activeRequests >= MAX_ACTIVE_REQUESTS || pending.isEmpty()) return;
                operation = pending.poll();
                if (operation == null) return;
                if (closed || !currentContext(operation.context)) {
                    removeCoalesced(operation);
                    operation.finished = true;
                    operation.result.fail(new IllegalStateException("Resource Provider Session Changed"));
                    continue;
                }
                Cooldown cooldown = cooldowns.get(cooldownKey(operation.provider, operation.context));
                long now = System.currentTimeMillis();
                if (cooldown != null && cooldown.until > now) {
                    removeCoalesced(operation);
                    operation.result.fail(ResourceProviderException.retry(operation.provider, cooldown.reason,
                            Instant.ofEpochMilli(cooldown.until), null));
                    continue;
                }
                operation.started = true;
                activeRequests++;
                active.add(operation);
            }
            start(operation);
        }
    }

    private <T> void start(Request<T> operation) {
        synchronized (lock) {
            if (operation.cancelled) {
                finish(operation, null, operation.invalidatedFailure == null ? new Async.Cancellation() : operation.invalidatedFailure);
                return;
            }
        }
        Async<HttpResponse<T>> active;
        try {
            active = retryUnauthorized(operation);
            if (active == null) throw new IllegalStateException("Resource Provider Transport Returned No Result");
        } catch (Throwable failure) {
            finish(operation, null, failure);
            return;
        }
        boolean cancelled;
        synchronized (lock) {
            operation.active = active;
            cancelled = operation.cancelled;
        }
        if (cancelled) {
            active.cancel();
            finish(operation, null, operation.invalidatedFailure == null ? new Async.Cancellation() : operation.invalidatedFailure);
            return;
        }
        active.whenComplete((response, failure) -> finish(operation, response, failure));
    }

    private <T> Async<HttpResponse<T>> retryUnauthorized(Request<T> request) {
        Async<HttpResponse<T>> first;
        try {
            first = request.operation.get();
            if (first == null) throw new IllegalStateException("Resource Provider Transport Returned No Result");
        } catch (Throwable failure) {
            return Async.failed(failure);
        }
        return first.thenCompose(response -> {
            if (response == null || response.statusCode() != 401 || !BrowserLaunchSession.authenticated()) {
                return Async.completed(response);
            }
            return BrowserLaunchSession.renewAsync().thenCompose(ignored -> {
                synchronized (lock) {
                    if (!currentContext(request.context)) {
                        return Async.failed(new IllegalStateException("Resource Provider Session Changed"));
                    }
                }
                return request.operation.get();
            });
        });
    }

    private <T> void finish(Request<T> operation, HttpResponse<T> response, Throwable failure) {
        boolean cancelResult;
        Throwable terminalFailure;
        synchronized (lock) {
            if (operation.finished) return;
            operation.finished = true;
            activeRequests = Math.max(0, activeRequests - 1);
            active.remove(operation);
            removeCoalesced(operation);
            if (operation.invalidatedFailure == null) recordCooldown(operation, response);
            cancelResult = operation.cancelled && operation.inFlight != null && operation.inFlight.owners == 0;
            terminalFailure = operation.invalidatedFailure;
        }
        if (terminalFailure != null) {
            operation.result.fail(terminalFailure);
        } else if (cancelResult) {
            operation.result.cancel();
        } else if (!operation.cancelled) {
            if (failure == null) operation.result.complete(response);
            else operation.result.fail(failure);
        }
        drain();
    }

    private void removeCoalesced(Request<?> operation) {
        if (!operation.coalesced) return;
        InFlightRequest<?> current = coalesced.get(operation.key);
        if (current == operation.inFlight) coalesced.remove(operation.key);
    }

    private void recordCooldown(Request<?> operation, HttpResponse<?> response) {
        if (response == null || response.statusCode() != 429 && response.statusCode() != 503) return;
        String provider = operation.provider;
        String body = response.body() instanceof String value ? value : null;
        ResourceProviderException failure = ResourceProviderException.fromResponse(provider, response.statusCode(), response.headers(), body);
        long until = failure.getRetryAt().map(Instant::toEpochMilli)
                .orElseGet(() -> System.currentTimeMillis() + DEFAULT_COOLDOWN.toMillis());
        String key = cooldownKey(provider, operation.context);
        Cooldown previous = cooldowns.get(key);
        if (previous == null || until > previous.until) {
            if (previous == null && cooldowns.size() >= MAX_COOLDOWNS) {
                cooldowns.remove(cooldowns.keySet().iterator().next());
            }
            cooldowns.put(key, new Cooldown(until, failure.getReason()));
        }
    }

    private static <T> Async<HttpResponse<T>> cooldownFailure(String provider, String message) {
        return Async.failed(ResourceProviderException.retry(provider, ResourceProviderException.Reason.RATE_LIMIT,
                Instant.ofEpochMilli(System.currentTimeMillis() + DEFAULT_COOLDOWN.toMillis()),
                new IllegalStateException(message)));
    }

    private String requestKey(String provider, HttpRequest request, SessionContext context, String handlerKey) {
        byte[] body = request.bodyPublisher().map(HttpRequest.BodyPublisher::bytes).orElse(null);
        String bodyKey = body == null ? "null" : body.length + ":" + Sha256.hex(body);
        return keyPart(context.generation()) + "|" + keyPart(context.subject()) + "|"
                + keyPart(context.ticket()) + "|" + keyPart(context.host()) + "|" + keyPart(providerKey(provider)) + "|"
                + keyPart(request.method()) + "|" + keyPart(request.uri().toString())
                + "|" + headersKey(request.headers().map()) + "|" + bodyKey + "|handler=" + keyPart(handlerKey);
    }

    private static String headersKey(Map<String, List<String>> headers) {
        StringBuilder key = new StringBuilder();
        headers.keySet().stream().sorted(String.CASE_INSENSITIVE_ORDER).forEach(name -> {
            List<String> values = headers.getOrDefault(name, List.of());
            String normalizedName = name.toLowerCase(Locale.ROOT);
            appendKeyPart(key, normalizedName);
            key.append(values.size()).append(':');
            values.forEach(value -> appendKeyPart(key, value));
        });
        return key.toString();
    }

    private static void appendKeyPart(StringBuilder key, String value) {
        String safeValue = value == null ? "" : value;
        key.append(safeValue.length()).append(':').append(safeValue);
    }

    private static String keyPart(Object value) {
        String text = value == null ? "" : String.valueOf(value);
        return text.length() + ":" + text;
    }

    private static String providerKey(String provider) {
        return provider == null ? "" : provider.strip();
    }

    private static String cooldownKey(String provider, SessionContext context) {
        return keyPart(providerKey(provider)) + "|" + keyPart(context.generation()) + "|" + keyPart(context.subject())
                + "|" + keyPart(context.ticket()) + "|" + keyPart(context.host());
    }

    private static HttpRequest proxyRequest(String provider, HttpRequest request, SessionContext context) {
        String target = encode(request.uri().toString());
        String endpoint = context.host() + "/remotely-web/resource-providers/"
                + encode(provider) + "/proxy?target=" + target;
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(endpoint))
                .header("Accept", request.headers().firstValue("Accept").orElse("application/json"))
                .timeout(request.timeout().orElse(Duration.ofSeconds(45)));
        if (context.ticket() != null && !context.ticket().isBlank()) {
            builder.header("X-Remotely-Web-Ticket", context.ticket());
        }
        request.headers().firstValue("Content-Type").ifPresent(value -> builder.header("Content-Type", value));
        byte[] body = request.bodyPublisher().map(HttpRequest.BodyPublisher::bytes).orElse(null);
        builder.method(request.method(), body == null ? HttpRequest.BodyPublishers.noBody()
                : HttpRequest.BodyPublishers.ofByteArray(body));
        return builder.build();
    }

    private static String encode(String value) {
        return URLEncoder.encode(value == null ? "" : value, StandardCharsets.UTF_8).replace("+", "%20");
    }

    private <T> Async<HttpResponse<T>> subscribe(InFlightRequest<?> request) {
        InFlightRequest<T> typed = typed(request);
        Async<HttpResponse<T>> view = Async.pending();
        typed.owners++;
        typed.result.whenComplete((response, failure) -> {
            if (view.isCancelled()) return;
            if (typed.result.isCancelled()) {
                view.fail(new Async.Cancellation());
                return;
            }
            if (failure == null) view.complete(response);
            else view.fail(failure);
        });
        view.onCancel(() -> release(typed));
        return view;
    }

    private void release(InFlightRequest<?> request) {
        Request<?> operation;
        Async<?> active = null;
        boolean pendingOperation = false;
        synchronized (lock) {
            if (request.owners <= 0) return;
            request.owners--;
            if (request.owners != 0 || request.operation == null || request.operation.finished) return;
            operation = request.operation;
            operation.cancelled = true;
            removeCoalesced(operation);
            request.result.cancel();
            if (!operation.started) {
                pending.remove(operation);
                operation.finished = true;
                pendingOperation = true;
            } else {
                active = operation.active;
            }
        }
        if (active != null) active.cancel();
        if (pendingOperation) {
            drain();
        }
    }

    void close() {
        BrowserLaunchSession.removeAuthStateListener(authStateListener);
        BrowserLaunchSession.removeTicketListener(ticketListener);
        BrowserLaunchSession.removeSessionExpiryListener(expiryListener);
        invalidateState("Resource Provider Gateway Is Closed", true);
    }

    private void invalidateAuthState() {
        invalidateState("Resource Provider Session Changed", false);
    }

    private void invalidateState(String message, boolean closing) {
        List<Request<?>> queued;
        List<Async<?>> activeRequestsToCancel = new ArrayList<>();
        IllegalStateException failure = new IllegalStateException(message);
        synchronized (lock) {
            if (closing) {
                if (closed) return;
                closed = true;
            } else if (closed) {
                return;
            }
            authGeneration++;
            coalesced.clear();
            cooldowns.clear();
            queued = new ArrayList<>(pending);
            pending.clear();
            for (Request<?> operation : queued) {
                operation.finished = true;
                operation.cancelled = true;
                operation.invalidatedFailure = failure;
            }
            for (Request<?> operation : active) {
                if (operation.finished) continue;
                operation.cancelled = true;
                operation.invalidatedFailure = failure;
                if (operation.active != null) activeRequestsToCancel.add(operation.active);
            }
        }
        for (Request<?> operation : queued) operation.result.fail(failure);
        activeRequestsToCancel.forEach(request -> request.cancel());
        drain();
    }

    private SessionContext sessionContext(long generation) {
        BrowserLaunchSession.Metadata metadata = BrowserLaunchSession.metadata();
        return new SessionContext(generation, metadata.subjectId(), BrowserLaunchSession.ticket(), BrowserLaunchSession.apiBaseUrl());
    }

    private boolean currentContext(SessionContext context) {
        if (closed || context.generation() != authGeneration) return false;
        BrowserLaunchSession.Metadata metadata = BrowserLaunchSession.metadata();
        return Objects.equals(context.subject(), metadata.subjectId())
                && Objects.equals(context.ticket(), BrowserLaunchSession.ticket())
                && Objects.equals(context.host(), BrowserLaunchSession.apiBaseUrl());
    }

    @SuppressWarnings("unchecked")
    private static <T> InFlightRequest<T> typed(InFlightRequest<?> request) {
        return (InFlightRequest<T>) request;
    }

    private record Cooldown(long until, ResourceProviderException.Reason reason) {
    }

    private static final class InFlightRequest<T> {
        private final Async<HttpResponse<T>> result;
        private Request<T> operation;
        private int owners;

        private InFlightRequest() {
            result = Async.pending();
        }
    }

    private static final class Request<T> {
        private final String provider;
        private final String key;
        private final Async<HttpResponse<T>> result;
        private final InFlightRequest<T> inFlight;
        private final Supplier<Async<HttpResponse<T>>> operation;
        private final boolean coalesced;
        private final SessionContext context;
        private Async<HttpResponse<T>> active;
        private boolean started;
        private boolean cancelled;
        private boolean finished;
        private Throwable invalidatedFailure;

        private Request(String provider, String key, Async<HttpResponse<T>> result, InFlightRequest<T> inFlight,
                        Supplier<Async<HttpResponse<T>>> operation, boolean coalesced, SessionContext context) {
            this.provider = provider;
            this.key = key;
            this.result = result;
            this.inFlight = inFlight;
            this.operation = operation;
            this.coalesced = coalesced;
            this.context = context;
        }
    }

    private record SessionContext(long generation, String subject, String ticket, String host) {
    }
}
