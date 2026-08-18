package redxax.oxy.remotely.data.integrations.luckperms;

import redxax.oxy.remotely.util.AsyncTools;
import redxax.oxy.remotely.util.BrowserSafeState;

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

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import restudio.rebase.platform.Async;

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
    private final ReSyncLuckPermsNetworkEnvironment networkEnvironment;
    private final ReSyncLuckPermsCodec codec;
    private final Map<String, Async<Response>> pending = BrowserSafeState.map();
    private final Map<String, byte[]> outbound = BrowserSafeState.map();
    private final Set<Listener> listeners = BrowserSafeState.set();
    private final BrowserSafeState.BooleanValue active = new BrowserSafeState.BooleanValue();
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
        this(flowClient, ReSyncLuckPermsNetworkEnvironment.unavailable(), ReSyncLuckPermsCodec.unavailable());
    }

    public ReSyncLuckPermsClient(ReSyncFlowClient flowClient, ReSyncLuckPermsNetworkEnvironment networkEnvironment) {
        this(flowClient, networkEnvironment, ReSyncLuckPermsCodec.unavailable());
    }

    public ReSyncLuckPermsClient(ReSyncFlowClient flowClient, ReSyncLuckPermsNetworkEnvironment networkEnvironment, ReSyncLuckPermsCodec codec) {
        this.flowClient = flowClient;
        this.networkEnvironment = networkEnvironment == null ? ReSyncLuckPermsNetworkEnvironment.unavailable() : networkEnvironment;
        this.codec = codec == null ? ReSyncLuckPermsCodec.unavailable() : codec;
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

    public Async<Overview> overview() {
        return request(Action.OVERVIEW, null, null, null, null, Response::overview);
    }

    public Async<UserPage> users(PageRequest page) {
        return request(Action.USERS, page, null, null, null, Response::users);
    }

    public Async<GroupPage> groups(PageRequest page) {
        return request(Action.GROUPS, page, null, null, null, Response::groups);
    }

    public Async<List<TrackDetail>> tracks() {
        return request(Action.TRACKS, null, null, null, null, Response::tracks);
    }

    public Async<SubjectDetail> subject(SubjectRef subject) {
        return request(Action.SUBJECT, null, subject, null, null, Response::subject);
    }

    public Async<EffectivePreview> preview(PreviewRequest preview) {
        return request(Action.PREVIEW, null, null, preview, null, Response::preview);
    }

    public Async<SaveResult> save(ChangeSet changes) {
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
                networkClient = new ReSyncLuckPermsNetworkClient(this, networkEnvironment);
            }
            return networkClient;
        }
    }

    private <T> Async<T> request(Action action, PageRequest page, SubjectRef subject, PreviewRequest preview, ChangeSet changes,
                                              Function<Response, T> result) {
        activate();
        String requestId = UUID.randomUUID().toString();
        Request request = new Request(LuckPermsManagementContract.VERSION, requestId, action, page, subject, preview, changes);
        Async<Response> responseFuture = Async.pending();
        pending.put(requestId, responseFuture);
        byte[] payload = codec.encode(request);
        outbound.put(requestId, payload);
        send(requestId);
        return AsyncTools.withTimeout(responseFuture, flowClient.scheduler(), Duration.ofSeconds(REQUEST_TIMEOUT_SECONDS)).whenComplete((response, error) -> {
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
            Response response = codec.decode(payload);
            if (response == null) {
                return;
            }
            if (response.invalidation() != null) {
                for (Listener listener : listeners) {
                    listener.onInvalidated(response.invalidation());
                }
            }
            Async<Response> future = pending.remove(response.requestId());
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
        for (Async<Response> future : pending.values()) {
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
