package redxax.oxy.remotely.web.platform;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import redxax.oxy.remotely.host.ApplicationHost;
import restudio.rebase.platform.Async;
import restudio.rebase.platform.http.HttpRequest;
import restudio.rebase.platform.http.HttpResponse;
import restudio.rebase.platform.http.HttpTransport;
import restudio.rebase.restudio.ReStudioAccount;
import restudio.rebase.restudio.SessionState;
import restudio.rebase.restudio.api.models.FeedbackModels;
import restudio.rebase.restudio.community.ReStudioAccountManagementProvider;
import restudio.rebase.restudio.community.ReStudioCommunityProvider;
import restudio.rebase.restudio.community.ReStudioCommunityProviders;
import restudio.rescreen.ui.core.ScreenManager;
import restudio.rescreen.util.Identifier;

import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Consumer;

public final class BrowserCommunityProvider implements ReStudioCommunityProvider, ReStudioAccountManagementProvider {
    private final HttpTransport transport;
    private final ApplicationHost host;
    private final Runnable authStateListener = this::refreshState;
    private final List<Runnable> accountChangeListeners = new ArrayList<>();
    private volatile ReStudioAccount account;
    private volatile SessionState state;
    private volatile boolean loginInFlight;
    private volatile boolean closed;
    private long sessionGeneration = 1;
    private long accountRequestGeneration;
    private String sessionSubjectId;

    public BrowserCommunityProvider(HttpTransport transport) {
        this(transport, null);
    }

    public BrowserCommunityProvider(HttpTransport transport, ApplicationHost host) {
        this(transport, host, BrowserLaunchSession.metadata());
    }

    public BrowserCommunityProvider(HttpTransport transport, ApplicationHost host, BrowserLaunchSession.Metadata launch) {
        this.transport = Objects.requireNonNull(transport, "transport");
        this.host = host;
        state = BrowserLaunchSession.authenticated() ? SessionState.AUTHENTICATED : SessionState.SIGNED_OUT;
        account = state == SessionState.AUTHENTICATED ? launchAccount(launch) : null;
        sessionSubjectId = account == null || account.id == null ? "" : account.id;
        BrowserLaunchSession.addAuthStateListener(authStateListener);
    }

    public synchronized void close() {
        if (closed) return;
        closed = true;
        sessionGeneration++;
        accountRequestGeneration++;
        BrowserLaunchSession.removeAuthStateListener(authStateListener);
        account = null;
        loginInFlight = false;
        state = SessionState.SIGNED_OUT;
        sessionSubjectId = "";
        accountChangeListeners.clear();
    }

    private void refreshState() {
        boolean changed;
        synchronized (this) {
            boolean authenticated = BrowserLaunchSession.authenticated();
            String subjectId = authenticated ? BrowserLaunchSession.metadata().subjectId() : "";
            sessionGeneration++;
            state = authenticated ? SessionState.AUTHENTICATED : SessionState.SIGNED_OUT;
            changed = !authenticated || !Objects.equals(sessionSubjectId, subjectId);
            if (changed) account = null;
            sessionSubjectId = subjectId;
            if (!authenticated) loginInFlight = false;
        }
        if (changed) notifyAccountChanged();
    }

    @Override
    public boolean isAuthenticated() {
        return !closed && BrowserLaunchSession.authenticated();
    }

    @Override
    public boolean isAdmin() {
        ReStudioAccount value = account;
        return value != null && "admin".equalsIgnoreCase(value.role);
    }

    @Override
    public String userId() {
        ReStudioAccount value = account;
        return firstNonBlank(value == null ? "" : value.id, BrowserLaunchSession.metadata().subjectId());
    }

    @Override
    public String username() {
        ReStudioAccount value = account;
        return firstNonBlank(value == null ? "" : value.username, BrowserLaunchSession.metadata().username());
    }

    @Override
    public String displayName() {
        ReStudioAccount value = account;
        return firstNonBlank(value == null ? "" : value.displayName, BrowserLaunchSession.metadata().displayName(), username());
    }

    @Override
    public String email() {
        ReStudioAccount value = account;
        return firstNonBlank(value == null ? "" : value.email, BrowserLaunchSession.metadata().email());
    }

    @Override
    public String avatarUrl() {
        ReStudioAccount current = account;
        String avatarUrl = firstNonBlank(current == null ? "" : current.avatarUrl, BrowserLaunchSession.metadata().avatarUrl());
        return avatarUrl.isBlank() ? null : avatarUrl;
    }

    @Override
    public synchronized void addAccountChangeListener(Runnable listener) {
        if (listener != null && !closed && !accountChangeListeners.contains(listener)) accountChangeListeners.add(listener);
    }

    @Override
    public synchronized void removeAccountChangeListener(Runnable listener) {
        accountChangeListeners.remove(listener);
    }

    @Override
    public SessionState sessionState() {
        if (closed) return SessionState.SIGNED_OUT;
        if (loginInFlight) return SessionState.RESTORING;
        if (BrowserLaunchSession.authenticated()) {
            state = SessionState.AUTHENTICATED;
        } else if (state == SessionState.AUTHENTICATED) {
            state = SessionState.SIGNED_OUT;
        }
        return state;
    }

    @Override
    public synchronized void login(boolean forceLogin, Runnable onSuccess, Consumer<Throwable> onFailure) {
        if (loginInFlight) return;
        if (BrowserLaunchSession.authenticated()) {
            state = SessionState.AUTHENTICATED;
            if (onSuccess != null) onSuccess.run();
            return;
        }
        loginInFlight = true;
        state = SessionState.RESTORING;
        BrowserLaunchSession.Callback callback = new BrowserLaunchSession.Callback() {
            @Override
            public void ready(BrowserLaunchSession.Metadata metadata) {
                loginInFlight = false;
                if (metadata != null && metadata.ticket() != null && !metadata.ticket().isBlank()
                        && BrowserLaunchSession.authenticated()) {
                    state = SessionState.AUTHENTICATED;
                    if (onSuccess != null) onSuccess.run();
                    return;
                }
                state = SessionState.SIGNED_OUT;
                if (onFailure != null) onFailure.accept(new IllegalStateException("ReStudio Login Was Not Completed"));
            }

            @Override
            public void failed(String message) {
                loginInFlight = false;
                state = SessionState.CONNECTION_LOST;
                if (onFailure != null) onFailure.accept(new IllegalStateException(
                        message == null || message.isBlank() ? "ReStudio Login Failed" : message));
            }
        };
        if (!BrowserLaunchSession.beginInteractiveLogin(forceLogin, callback)) {
            loginInFlight = false;
            state = SessionState.SIGNED_OUT;
            if (onFailure != null) onFailure.accept(new IllegalStateException("Sign In Window Could Not Be Opened"));
        }
    }

    @Override
    public Async<Boolean> retryConnection() {
        if (isAuthenticated()) return Async.completed(true);
        Async<Boolean> result = Async.pending();
        login(false, () -> result.complete(true), ignored -> result.complete(false));
        return result;
    }

    @Override
    public void cancelLogin() {
        loginInFlight = false;
        BrowserLaunchSession.cancelLaunch();
        refreshState();
    }

    @Override
    public void setProfileSetupVisible(boolean visible) {
    }

    @Override
    public Async<ReStudioAccount> getAccount() {
        AccountFence fence = captureAccountFence();
        return request("GET", "/remotely-web/account", null).thenApply(value -> account(value, fence));
    }

    @Override
    public Async<ReStudioAccount> uploadAvatar(byte[] bytes, String contentType, String filename) {
        if (bytes == null || bytes.length == 0) {
            return Async.failed(new IllegalArgumentException("Avatar File Is Empty"));
        }
        MultipartBody body = multipart("file", filename, contentType, bytes);
        AccountFence fence = captureAccountFence();
        return requestBytes("POST", "/account/avatar", body.bytes(), "multipart/form-data; boundary=" + body.boundary())
                .thenApply(value -> account(value, fence));
    }

    @Override
    public Async<ReStudioAccount> deleteAvatar() {
        AccountFence fence = captureAccountFence();
        return request("DELETE", "/account/avatar", null).thenApply(value -> account(value, fence));
    }

    @Override
    public Async<List<ApplicationSession>> getApplicationSessions() {
        return request("GET", "/auth/sessions", null).thenApply(value -> {
            List<ApplicationSession> result = new ArrayList<>();
            BrowserJson.array(value).forEach(element -> {
                if (element != null && element.isJsonObject()) result.add(session(element.getAsJsonObject()));
            });
            return List.copyOf(result);
        });
    }

    @Override
    public Async<Void> revokeApplicationSession(UUID sessionId) {
        if (sessionId == null) {
            return Async.failed(new IllegalArgumentException("Application Session Is Required"));
        }
        return request("DELETE", "/auth/sessions/" + path(sessionId.toString()), null).thenApply(ignored -> null);
    }

    @Override
    public Async<Void> revokeOtherApplicationSessions() {
        return request("DELETE", "/auth/sessions/others", null).thenApply(ignored -> null);
    }

    @Override
    public Async<Identifier> loadAvatarId(String userId, String avatarUrl) {
        if (avatarUrl == null || avatarUrl.isBlank()) {
            return Async.completed(Identifier.icon("steve.png"));
        }
        Identifier avatar = ScreenManager.getInstance().imageAssets().registerRemoteImage(avatarUrl);
        return Async.completed(avatar == null ? Identifier.icon("steve.png") : avatar);
    }

    @Override
    public synchronized void applyAccount(ReStudioAccount account) {
        if (account == null || account != this.account || !providerCurrent()) return;
        BrowserLaunchSession.Metadata metadata = BrowserLaunchSession.metadata();
        if (!metadata.subjectId().isBlank() && account.id != null && !account.id.isBlank()
                && !metadata.subjectId().equals(account.id)) return;
        BrowserLaunchSession.replaceAccountMetadata(account.id, account.username, account.displayName, account.email,
                account.avatarUrl, metadata.sessionLabel());
    }

    @Override
    public Async<Boolean> isUsernameAvailable(String username) {
        return request("GET", "/remotely-web/account/availability?username=" + path(username), null)
                .thenApply(value -> BrowserJson.bool(BrowserJson.object(value), "available", false));
    }

    @Override
    public Async<ReStudioAccount> updateAccount(String username, String displayName) {
        JsonObject body = new JsonObject();
        BrowserJson.put(body, "username", username);
        BrowserJson.put(body, "displayName", displayName);
        AccountFence fence = captureAccountFence();
        return request("PUT", "/remotely-web/account", body).thenApply(value -> account(value, fence));
    }

    @Override
    public Async<List<FeedbackModels.Channel>> getChannels() {
        return request("GET", "/remotely-web/feedback/channels", null).thenApply(value -> objects(value).stream().map(this::channel).toList());
    }

    @Override
    public Async<List<FeedbackModels.Category>> getCategories(String channelId) {
        return request("GET", "/remotely-web/feedback/channels/" + path(channelId) + "/categories", null)
                .thenApply(value -> objects(value).stream().map(this::category).toList());
    }

    @Override
    public Async<List<FeedbackModels.Post>> getPosts(String categoryId) {
        return request("GET", "/remotely-web/feedback/categories/" + path(categoryId) + "/posts", null)
                .thenApply(value -> objects(value).stream().map(this::post).toList());
    }

    @Override
    public Async<List<FeedbackModels.Comment>> getComments(String postId) {
        return request("GET", "/remotely-web/feedback/posts/" + path(postId) + "/comments", null)
                .thenApply(value -> objects(value).stream().map(this::comment).toList());
    }

    @Override
    public Async<Void> createPost(String categoryId, String title, String content, String rating) {
        JsonObject body = new JsonObject();
        BrowserJson.put(body, "title", title);
        BrowserJson.put(body, "content", content);
        BrowserJson.put(body, "rating", rating);
        return request("POST", "/remotely-web/feedback/categories/" + path(categoryId) + "/posts", body).thenApply(ignored -> null);
    }

    @Override
    public Async<Void> createComment(String postId, String content) {
        JsonObject body = new JsonObject();
        BrowserJson.put(body, "content", content);
        return request("POST", "/remotely-web/feedback/posts/" + path(postId) + "/comments", body).thenApply(ignored -> null);
    }

    @Override
    public Async<Void> updatePostStatus(String postId, String status) {
        JsonObject body = new JsonObject();
        BrowserJson.put(body, "status", status);
        return request("PUT", "/remotely-web/feedback/posts/" + path(postId) + "/status", body).thenApply(ignored -> null);
    }

    @Override
    public Async<Void> deletePost(String postId) {
        return request("DELETE", "/remotely-web/feedback/posts/" + path(postId), null).thenApply(ignored -> null);
    }

    @Override
    public Async<Void> deleteComment(String commentId) {
        return request("DELETE", "/remotely-web/feedback/comments/" + path(commentId), null).thenApply(ignored -> null);
    }

    @Override
    public Async<Void> banUser(String userId, String reason) {
        JsonObject body = new JsonObject();
        BrowserJson.put(body, "reason", reason);
        return request("POST", "/remotely-web/feedback/users/" + path(userId) + "/ban", body).thenApply(ignored -> null);
    }

    @Override
    public Async<List<FeedbackModels.Notification>> getNotifications() {
        return request("GET", "/remotely-web/notifications", null).thenApply(value -> objects(value).stream().map(this::notification).toList());
    }

    @Override
    public Async<Void> markNotificationRead(String id) {
        return request("POST", "/remotely-web/notifications/" + path(id) + "/read", new JsonObject()).thenApply(ignored -> null);
    }

    private Async<String> request(String method, String endpoint, JsonElement body) {
        return request(method, endpoint, body, true);
    }

    private Async<String> request(String method, String endpoint, JsonElement body, boolean retry) {
        String serialized = body == null ? null : BrowserJson.write(body);
        return requestBytes(method, endpoint, serialized == null ? null : serialized.getBytes(StandardCharsets.UTF_8),
                serialized == null ? null : "application/json", retry);
    }

    private Async<String> requestBytes(String method, String endpoint, byte[] body, String contentType) {
        return requestBytes(method, endpoint, body, contentType, true);
    }

    private Async<String> requestBytes(String method, String endpoint, byte[] body, String contentType, boolean retry) {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(BrowserLaunchSession.apiBaseUrl() + endpoint))
                .header("Accept", "application/json")
                .timeout(Duration.ofSeconds(20));
        if (contentType != null && !contentType.isBlank()) builder.header("Content-Type", contentType);
        builder.method(method, body == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofByteArray(body));
        return transport.sendAsync(builder.build(), HttpResponse.BodyHandlers.ofString()).thenCompose(response -> {
            if (response.statusCode() == 401) {
                if (retry && BrowserLaunchSession.authenticated()) {
                    return BrowserLaunchSession.renewAsync()
                        .exceptionallyCompose(failure -> {
                            if (BrowserLaunchSession.isAuthenticationFailure(failure)) {
                                BrowserLaunchSession.expireSession();
                                if (host != null) host.signIn(host.getCurrentScreen());
                                return Async.failed(new IllegalStateException("ReStudio Community Is Unavailable For This Browser Session", failure));
                            }
                            return Async.failed(failure);
                        })
                        .thenCompose(ignored -> requestBytes(method, endpoint, body, contentType, false));
                }
                BrowserLaunchSession.expireSession();
                if (host != null) host.signIn(host.getCurrentScreen());
                return Async.failed(new IllegalStateException("ReStudio Community Is Unavailable For This Browser Session"));
            }
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                String message = error(response.body());
                return Async.failed(new IllegalStateException(message.isBlank() ? "ReStudio Community Failed With Status " + response.statusCode() : message));
            }
            return Async.completed(response.body() == null ? "" : response.body());
        });
    }

    private List<JsonObject> objects(String value) {
        List<JsonObject> result = new ArrayList<>();
        BrowserJson.array(value).forEach(item -> {
            if (item != null && item.isJsonObject()) result.add(item.getAsJsonObject());
        });
        return List.copyOf(result);
    }

    private FeedbackModels.Channel channel(JsonObject value) {
        FeedbackModels.Channel result = new FeedbackModels.Channel();
        result.id = BrowserJson.string(value, "id");
        result.name = BrowserJson.string(value, "name");
        result.projectKey = BrowserJson.string(value, "projectKey");
        result.description = BrowserJson.string(value, "description");
        return result;
    }

    private FeedbackModels.Category category(JsonObject value) {
        FeedbackModels.Category result = new FeedbackModels.Category();
        result.id = BrowserJson.string(value, "id");
        result.name = BrowserJson.string(value, "name");
        result.ratingLabel = BrowserJson.string(value, "ratingLabel");
        result.ratingOptionsJson = BrowserJson.string(value, "ratingOptionsJson", "[]");
        result.statusOptionsJson = BrowserJson.string(value, "statusOptionsJson", "[]");
        return result;
    }

    private FeedbackModels.Post post(JsonObject value) {
        FeedbackModels.Post result = new FeedbackModels.Post();
        result.id = BrowserJson.string(value, "id");
        result.user = user(BrowserJson.object(BrowserJson.element(value, "user")));
        result.title = BrowserJson.string(value, "title");
        result.content = BrowserJson.string(value, "content");
        result.rating = BrowserJson.string(value, "rating");
        result.status = BrowserJson.string(value, "status");
        result.createdAt = BrowserJson.string(value, "createdAt");
        return result;
    }

    private FeedbackModels.Comment comment(JsonObject value) {
        FeedbackModels.Comment result = new FeedbackModels.Comment();
        result.id = BrowserJson.string(value, "id");
        result.user = user(BrowserJson.object(BrowserJson.element(value, "user")));
        result.content = BrowserJson.string(value, "content");
        result.createdAt = BrowserJson.string(value, "createdAt");
        return result;
    }

    private FeedbackModels.UserSummary user(JsonObject value) {
        if (value.entrySet().isEmpty()) return null;
        FeedbackModels.UserSummary result = new FeedbackModels.UserSummary();
        result.id = BrowserJson.string(value, "id");
        result.username = BrowserJson.string(value, "username");
        result.displayName = BrowserJson.string(value, "displayName");
        result.avatarUrl = BrowserJson.string(value, "avatarUrl");
        result.role = BrowserJson.string(value, "role");
        return result;
    }

    private FeedbackModels.Notification notification(JsonObject value) {
        FeedbackModels.Notification result = new FeedbackModels.Notification();
        result.id = BrowserJson.string(value, "id");
        result.title = BrowserJson.string(value, "title");
        result.content = BrowserJson.string(value, "content");
        result.actionType = BrowserJson.string(value, "actionType");
        result.actionTarget = BrowserJson.string(value, "actionTarget");
        result.read = BrowserJson.bool(value, "read", false);
        result.createdAt = BrowserJson.string(value, "createdAt");
        return result;
    }

    private ReStudioAccount account(String value, AccountFence fence) {
        JsonObject source = BrowserJson.object(value);
        ReStudioAccount result = new ReStudioAccount();
        result.id = BrowserJson.string(source, "id");
        result.email = BrowserJson.string(source, "email");
        result.username = BrowserJson.string(source, "username");
        result.usernameNormalized = BrowserJson.string(source, "usernameNormalized");
        result.displayName = BrowserJson.string(source, "displayName");
        result.avatarUrl = BrowserJson.string(source, "avatarUrl");
        result.role = BrowserJson.string(source, "role");
        result.profileComplete = BrowserJson.bool(source, "profileComplete", false);
        if (!applyAccountResponse(result, fence)) return null;
        notifyAccountChanged();
        return result;
    }

    private synchronized AccountFence captureAccountFence() {
        return new AccountFence(sessionGeneration, ++accountRequestGeneration, sessionSubjectId);
    }

    private synchronized boolean applyAccountResponse(ReStudioAccount result, AccountFence fence) {
        if (!current(fence)) return false;
        BrowserLaunchSession.Metadata metadata = BrowserLaunchSession.metadata();
        String resultSubjectId = result.id == null ? "" : result.id;
        if (!fence.subjectId().isBlank() && !resultSubjectId.equals(fence.subjectId())) return false;
        BrowserLaunchSession.replaceAccountMetadata(result.id, result.username, result.displayName, result.email,
                result.avatarUrl, metadata.sessionLabel());
        if (!providerCurrent() || fence.requestGeneration() != accountRequestGeneration) return false;
        String currentSubjectId = BrowserLaunchSession.metadata().subjectId();
        if (!resultSubjectId.isBlank() && !resultSubjectId.equals(currentSubjectId)) return false;
        account = result;
        sessionSubjectId = currentSubjectId;
        return true;
    }

    private boolean current(AccountFence fence) {
        return fence != null && fence.sessionGeneration() == sessionGeneration
                && fence.requestGeneration() == accountRequestGeneration
                && Objects.equals(fence.subjectId(), sessionSubjectId) && providerCurrent();
    }

    private boolean providerCurrent() {
        return !closed && BrowserLaunchSession.authenticated() && ReStudioCommunityProviders.current() == this;
    }

    private ReStudioAccount launchAccount(BrowserLaunchSession.Metadata launch) {
        if (launch == null) return null;
        ReStudioAccount result = new ReStudioAccount();
        result.id = launch.subjectId();
        result.username = launch.username();
        result.displayName = launch.displayName();
        result.email = launch.email();
        result.avatarUrl = launch.avatarUrl();
        return result;
    }

    private void notifyAccountChanged() {
        List<Runnable> listeners;
        synchronized (this) {
            if (closed) return;
            listeners = List.copyOf(accountChangeListeners);
        }
        for (Runnable listener : listeners) {
            try {
                listener.run();
            } catch (RuntimeException ignored) {
            }
        }
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) return value;
        }
        return "";
    }

    private ReStudioAccountManagementProvider.ApplicationSession session(JsonObject source) {
        UUID id = null;
        String rawId = BrowserJson.string(source, "id");
        if (!rawId.isBlank()) {
            try {
                id = UUID.fromString(rawId);
            } catch (IllegalArgumentException ignored) {
            }
        }
        return new ReStudioAccountManagementProvider.ApplicationSession(id,
                BrowserJson.string(source, "clientId"), BrowserJson.string(source, "deviceName"),
                BrowserJson.string(source, "createdAt"), BrowserJson.string(source, "lastUsedAt"),
                BrowserJson.string(source, "expiresAt"), BrowserJson.string(source, "absoluteExpiresAt"),
                BrowserJson.string(source, "revokedAt"), BrowserJson.string(source, "revocationReason"),
                BrowserJson.bool(source, "current", false));
    }

    private MultipartBody multipart(String field, String filename, String contentType, byte[] bytes) {
        String boundary = "----RemotelyAvatar" + UUID.randomUUID().toString().replace("-", "");
        String safeFilename = filename == null || filename.isBlank() ? "avatar.png" : filename.replace("\\", "_").replace("\"", "_");
        String safeContentType = contentType == null || contentType.isBlank() ? "application/octet-stream" : contentType;
        String header = "--" + boundary + "\r\n"
                + "Content-Disposition: form-data; name=\"" + field + "\"; filename=\"" + safeFilename + "\"\r\n"
                + "Content-Type: " + safeContentType + "\r\n\r\n";
        String footer = "\r\n--" + boundary + "--\r\n";
        byte[] headerBytes = header.getBytes(StandardCharsets.UTF_8);
        byte[] footerBytes = footer.getBytes(StandardCharsets.UTF_8);
        ByteArrayOutputStream output = new ByteArrayOutputStream(headerBytes.length + bytes.length + footerBytes.length);
        output.write(headerBytes, 0, headerBytes.length);
        output.write(bytes, 0, bytes.length);
        output.write(footerBytes, 0, footerBytes.length);
        return new MultipartBody(boundary, output.toByteArray());
    }

    private String error(String value) {
        try {
            JsonObject object = BrowserJson.object(value);
            String message = BrowserJson.string(object, "message");
            return message.isBlank() ? BrowserJson.string(object, "error") : message;
        } catch (RuntimeException ignored) {
            return "";
        }
    }

    private String path(String value) {
        return URLEncoder.encode(value == null ? "" : value, StandardCharsets.UTF_8).replace("+", "%20");
    }

    private record MultipartBody(String boundary, byte[] bytes) {
    }

    private record AccountFence(long sessionGeneration, long requestGeneration, String subjectId) {
    }
}
