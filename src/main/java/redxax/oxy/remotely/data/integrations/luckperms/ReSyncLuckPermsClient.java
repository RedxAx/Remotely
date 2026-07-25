package redxax.oxy.remotely.data.integrations.luckperms;

import com.google.gson.Gson;
import redxax.oxy.remotely.data.flow.ReSyncFlowClient;
import restudio.resync.permissions.LuckPermsManagementContract;
import restudio.resync.permissions.LuckPermsManagementContract.Action;
import restudio.resync.permissions.LuckPermsManagementContract.ChangeSet;
import restudio.resync.permissions.LuckPermsManagementContract.EffectivePreview;
import restudio.resync.permissions.LuckPermsManagementContract.GroupPage;
import restudio.resync.permissions.LuckPermsManagementContract.Invalidation;
import restudio.resync.permissions.LuckPermsManagementContract.Overview;
import restudio.resync.permissions.LuckPermsManagementContract.PageRequest;
import restudio.resync.permissions.LuckPermsManagementContract.PreviewRequest;
import restudio.resync.permissions.LuckPermsManagementContract.Request;
import restudio.resync.permissions.LuckPermsManagementContract.Response;
import restudio.resync.permissions.LuckPermsManagementContract.SaveResult;
import restudio.resync.permissions.LuckPermsManagementContract.SubjectDetail;
import restudio.resync.permissions.LuckPermsManagementContract.SubjectRef;
import restudio.resync.permissions.LuckPermsManagementContract.TrackDetail;
import restudio.resync.permissions.LuckPermsManagementContract.UserPage;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;

public final class ReSyncLuckPermsClient implements AutoCloseable {
    public interface Listener {
        default void onInvalidated(Invalidation invalidation) {
        }

        default void onAvailability(boolean available, String message) {
        }
    }

    @FunctionalInterface
    public interface Subscription extends AutoCloseable {
        @Override
        void close();
    }

    private static final long REQUEST_TIMEOUT_SECONDS = 15;
    private final ReSyncFlowClient flowClient;
    private final Gson gson = new Gson();
    private final Map<String, CompletableFuture<Response>> pending = new ConcurrentHashMap<>();
    private final Map<String, byte[]> outbound = new ConcurrentHashMap<>();
    private final Set<Listener> listeners = new CopyOnWriteArraySet<>();
    private final AtomicBoolean active = new AtomicBoolean();
    private volatile ReSyncLuckPermsNetworkClient networkClient;
    private final ReSyncFlowClient.PluginChannelListener channelListener = new ReSyncFlowClient.PluginChannelListener() {
        @Override
        public void onData(String channelId, byte[] payload) {
            handle(payload);
        }

        @Override
        public void onAvailable(String channelId) {
            notifyAvailability(true, "Connected");
            flush();
        }

        @Override
        public void onRemoved(String channelId) {
            notifyAvailability(false, "LuckPerms Management Is Unavailable");
            failPending("LuckPerms Management Is Unavailable");
        }
    };

    public ReSyncLuckPermsClient(ReSyncFlowClient flowClient) {
        this.flowClient = flowClient;
    }

    public Subscription subscribe(Listener listener) {
        if (listener == null) {
            return () -> {
            };
        }
        listeners.add(listener);
        activate();
        boolean available = flowClient.isPluginChannelAvailable(LuckPermsManagementContract.CHANNEL_ID);
        listener.onAvailability(available, available ? "Connected" : "Waiting For ReSync");
        return () -> listeners.remove(listener);
    }

    public CompletableFuture<Overview> overview() {
        return request(Action.OVERVIEW, null, null, null, null, Response::overview);
    }

    public CompletableFuture<UserPage> users(PageRequest page) {
        return request(Action.USERS, page, null, null, null, Response::users);
    }

    public CompletableFuture<GroupPage> groups(PageRequest page) {
        return request(Action.GROUPS, page, null, null, null, Response::groups);
    }

    public CompletableFuture<List<TrackDetail>> tracks() {
        return request(Action.TRACKS, null, null, null, null, Response::tracks);
    }

    public CompletableFuture<SubjectDetail> subject(SubjectRef subject) {
        return request(Action.SUBJECT, null, subject, null, null, Response::subject);
    }

    public CompletableFuture<EffectivePreview> preview(PreviewRequest preview) {
        return request(Action.PREVIEW, null, null, preview, null, Response::preview);
    }

    public CompletableFuture<SaveResult> save(ChangeSet changes) {
        return request(Action.SAVE, null, null, null, changes, Response::save);
    }

    public boolean isAvailable() {
        return flowClient.isPluginChannelAvailable(LuckPermsManagementContract.CHANNEL_ID);
    }

    public String serverId() {
        return flowClient.getServerId();
    }

    public ReSyncLuckPermsNetworkClient network() {
        ReSyncLuckPermsNetworkClient current = networkClient;
        if (current != null) {
            return current;
        }
        synchronized (this) {
            if (networkClient == null) {
                networkClient = new ReSyncLuckPermsNetworkClient(this);
            }
            return networkClient;
        }
    }

    private <T> CompletableFuture<T> request(Action action, PageRequest page, SubjectRef subject, PreviewRequest preview, ChangeSet changes,
                                              Function<Response, T> result) {
        activate();
        String requestId = UUID.randomUUID().toString();
        Request request = new Request(LuckPermsManagementContract.VERSION, requestId, action, page, subject, preview, changes);
        CompletableFuture<Response> responseFuture = new CompletableFuture<>();
        pending.put(requestId, responseFuture);
        byte[] payload = gson.toJson(request).getBytes(StandardCharsets.UTF_8);
        outbound.put(requestId, payload);
        send(requestId);
        return responseFuture.orTimeout(REQUEST_TIMEOUT_SECONDS, TimeUnit.SECONDS).whenComplete((response, error) -> {
            pending.remove(requestId);
            outbound.remove(requestId);
        })
            .thenApply(response -> {
                if (!response.success()) {
                    throw new IllegalStateException(response.message().isBlank() ? "LuckPerms Request Failed" : response.message());
                }
                T value = result.apply(response);
                if (value == null) {
                    throw new IllegalStateException("LuckPerms Returned No Data");
                }
                return value;
            });
    }

    private void activate() {
        if (active.compareAndSet(false, true)) {
            flowClient.addPluginChannelListener(LuckPermsManagementContract.CHANNEL_ID, channelListener);
            flowClient.subscribePluginChannel(LuckPermsManagementContract.CHANNEL_ID);
        }
    }

    private void send(String requestId) {
        byte[] payload = outbound.get(requestId);
        if (payload != null && flowClient.sendPluginData(LuckPermsManagementContract.CHANNEL_ID, payload)) {
            outbound.remove(requestId, payload);
        }
    }

    private void flush() {
        for (String requestId : List.copyOf(outbound.keySet())) {
            send(requestId);
        }
    }

    private void handle(byte[] payload) {
        try {
            Response response = gson.fromJson(new String(payload, StandardCharsets.UTF_8), Response.class);
            if (response == null) {
                return;
            }
            if (response.invalidation() != null) {
                for (Listener listener : listeners) {
                    listener.onInvalidated(response.invalidation());
                }
            }
            CompletableFuture<Response> future = pending.remove(response.requestId());
            if (future != null) {
                future.complete(response);
            }
            notifyAvailability(true, response.message().isBlank() ? "Connected" : response.message());
        } catch (RuntimeException exception) {
            notifyAvailability(false, "LuckPerms Response Could Not Be Read");
        }
    }

    private void notifyAvailability(boolean available, String message) {
        for (Listener listener : listeners) {
            listener.onAvailability(available, message);
        }
    }

    private void failPending(String message) {
        IllegalStateException failure = new IllegalStateException(message);
        for (CompletableFuture<Response> future : pending.values()) {
            future.completeExceptionally(failure);
        }
        pending.clear();
        outbound.clear();
    }

    @Override
    public void close() {
        if (active.compareAndSet(true, false)) {
            flowClient.removePluginChannelListener(LuckPermsManagementContract.CHANNEL_ID, channelListener);
            flowClient.unsubscribePluginChannel(LuckPermsManagementContract.CHANNEL_ID);
        }
        listeners.clear();
        failPending("LuckPerms Management Was Closed");
    }
}
