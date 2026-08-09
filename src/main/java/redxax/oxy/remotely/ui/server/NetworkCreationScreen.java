package redxax.oxy.remotely.ui.server;

import redxax.oxy.remotely.RemotelyClient;
import redxax.oxy.remotely.network.NetworkCreationMember;
import redxax.oxy.remotely.network.NetworkCreationRequest;
import redxax.oxy.remotely.network.NetworkHostScope;
import redxax.oxy.remotely.network.NetworkJob;
import redxax.oxy.remotely.network.NetworkJobStatus;
import redxax.oxy.remotely.network.NetworkMemberManagement;
import redxax.oxy.remotely.network.NetworkMemberRole;
import restudio.rebase.Rebase;
import restudio.rebase.backend.impl.PteroBackend;
import restudio.rebase.instance.Instance;
import restudio.rebase.instance.loaders.ModLoader;
import restudio.rebase.util.Executors;
import restudio.rescreen.theme.ThemeManager;
import restudio.rescreen.ui.core.Screen;
import restudio.rescreen.ui.core.ScreenManager;
import restudio.rescreen.ui.rescreen.Container;
import restudio.rescreen.ui.rescreen.ReScreen;
import restudio.rescreen.ui.rescreen.layout.ManagedLayout;
import restudio.rescreen.ui.widgets.AnimatedButton;
import restudio.rescreen.ui.widgets.IconButton;
import restudio.rescreen.ui.widgets.PopupWidget;
import restudio.rescreen.ui.widgets.TextInputWidget;
import restudio.rescreen.util.Notification;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class NetworkCreationScreen extends ReScreen {
    private final Screen parent;
    private final RemotelyClient remotelyClient;
    private final Instance proxy;
    private final List<Instance> backends;
    private final List<NetworkCreationMember> initialMembers;
    private final String initialName;
    private final String initialPort;
    private final boolean initialFirewallVerified;
    private List<NetworkCreationMember> members;
    private TextInputWidget nameInput;
    private TextInputWidget portInput;
    private boolean firewallVerified;
    private boolean preparing;

    public NetworkCreationScreen(Screen parent, RemotelyClient remotelyClient, Instance proxy, List<Instance> backends) {
        this(parent, remotelyClient, proxy, backends, defaultMembers(proxy, backends), proxy.getName() + " Network", String.valueOf(observedPort(proxy, 25565)), false);
    }

    private NetworkCreationScreen(Screen parent, RemotelyClient remotelyClient, Instance proxy, List<Instance> backends, List<NetworkCreationMember> members, String name, String port, boolean firewallVerified) {
        this.parent = parent;
        this.remotelyClient = remotelyClient;
        this.proxy = proxy;
        this.backends = List.copyOf(backends);
        this.initialMembers = List.copyOf(members);
        this.initialName = name;
        this.initialPort = port;
        this.initialFirewallVerified = firewallVerified;
    }

    public String getDesktopAppId() {
        return "network-creation";
    }

    public String getDesktopAppTitle() {
        return "New Network";
    }

    public String getDesktopAppIconPath() {
        return "network.png";
    }

    @Override
    public void init() {
        super.init();
        members = initialMembers;
        firewallVerified = initialFirewallVerified;
        if (remotelyClient.getNetworkManager().getNetworkForInstance(proxy.getInstanceId()).isPresent()) {
            openCreatedNetwork(remotelyClient.getNetworkManager().getNetworkForInstance(proxy.getInstanceId()).orElseThrow().networkId());
            return;
        }
        header().addLeft("close.png", () -> client.setScreen(parent), "Back").addRight("checkmark.png", this::review, "Review Network").build();
        Container creation = createContainer("network_creation", 6, 38, width - 12, Math.max(80, height - 44)).columns(1).padding(8).layout(new ManagedLayout()).scrolling(true).backgroundDrawing(false);
        populate(creation);
        setActiveContainer(creation);
    }

    private void populate(Container container) {
        nameInput = new TextInputWidget.Builder().size(Math.max(220, width - 44), 22).text(initialName).placeholder("Network Name").build();
        portInput = new TextInputWidget.Builder().size(Math.max(220, width - 44), 22).text(initialPort).placeholder("Entry Port").build();
        container.addWidget(nameInput);
        container.addWidget(portInput);
        remotelyClient.getNetworkManager().getRecoverableCreationJob(proxy.getInstanceId()).ifPresent(job -> {
            container.addWidget(new IconButton.Builder().size(Math.max(220, width - 44), 24).label("Resume Network Creation").hint(job.message()).imagePath("reload.png").accentType(ThemeManager.getDefaultAccent()).onClick(() -> resume(job)).build());
            container.addWidget(new IconButton.Builder().size(Math.max(220, width - 44), 22).label("Rollback Network Creation").hint("Restore Configuration Backups").imagePath("history.png").accentType(ThemeManager.getAccent("danger")).onClick(() -> rollback(job)).build());
        });
        container.addWidget(summary("Velocity • " + proxy.getName(), NetworkHostScope.resolve(proxy), "calm"));
        long hostCount = Stream.concat(Stream.of(proxy), backends.stream()).map(NetworkHostScope::resolve).distinct().count();
        container.addWidget(summary((backends.size() + 1) + " Servers • " + hostCount + " Hosts", "Selected Topology", "calm"));
        NetworkServerCreationContext creationContext = NetworkServerCreationContext.forInstance(proxy);
        container.addWidget(new IconButton.Builder().size(Math.max(220, width - 44), 24).label("Create Backend").hint(creationContext.supported() ? "Default Host • " + creationContext.hostLabel() : "Choose A Local Or SSH Host").imagePath("newFile.png").accentType(ThemeManager.getAccent("nice")).onClick(this::createBackend).build());
        container.addWidget(new IconButton.Builder().size(Math.max(220, width - 44), 24).label("Add Existing Server").hint("Add An Unassigned Backend").imagePath("merge.png").accentType(ThemeManager.getAccent("calm")).onClick(this::addExistingBackend).build());
        if (backends.isEmpty()) {
            container.addWidget(summary("No Backends Yet", "Create Or Add At Least One Backend", "warning"));
        }
        for (NetworkCreationMember member : members) {
            Instance instance = backend(member.instanceId());
            String label = member.routeName() + " • " + titleCase(member.role().name());
            String endpoint = providerManaged(instance) ? "Provider Allocation" : member.management() == NetworkMemberManagement.EXTERNAL ? (member.address().isBlank() ? "External Address" : member.address()) + ":" + member.preferredPort() : "Automatic Address And Port";
            String hint = instance.getName() + " • " + endpoint + " • ReSync " + (member.resyncEnabled() ? "On" : "Off");
            container.addWidget(new IconButton.Builder().size(Math.max(220, width - 44), 30).label(label).hint(hint).imagePath("server.png").accentType(ThemeManager.getAccent(member.role() == NetworkMemberRole.LOBBY ? "nice" : "calm")).onClick(() -> editMember(member)).build());
        }
        boolean crossHost = backends.stream().anyMatch(backend -> providerManaged(backend) || !NetworkHostScope.resolve(backend).equals(NetworkHostScope.resolve(proxy)));
        if (crossHost) {
            container.addWidget(new IconButton.Builder().size(Math.max(220, width - 44), 24).label(firewallVerified ? "Network Protection Verified" : "Verify Network Protection").hint("Private Network Or Firewall Rules").imagePath(firewallVerified ? "checkmark.png" : "report.png").accentType(ThemeManager.getAccent(firewallVerified ? "nice" : "danger")).onClick(() -> {
                firewallVerified = !firewallVerified;
                refreshDraft();
            }).build());
        }
        container.addWidget(new IconButton.Builder().size(Math.max(220, width - 44), 24).label("Review Network").hint("Ports, Forwarding, ReSync, And Routes").imagePath("checkmark.png").accentType(ThemeManager.getAccent("nice")).onClick(this::review).build());
    }

    private AnimatedButton summary(String label, String hint, String accent) {
        return new AnimatedButton.Builder().size(Math.max(220, width - 44), 22).label(label).hint(hint).accentType(ThemeManager.getAccent(accent)).build();
    }

    private void editMember(NetworkCreationMember existing) {
        String[] route = {existing.routeName()};
        NetworkMemberRole[] role = {existing.role()};
        String[] address = {existing.address()};
        String[] port = {String.valueOf(existing.preferredPort())};
        String[] capacity = {String.valueOf(existing.capacity())};
        Boolean[] resync = {existing.resyncEnabled()};
        PopupWidget[] popup = new PopupWidget[1];
        AnimatedButton save = new AnimatedButton.Builder().size(90, 20).label("Save Server").accentType(ThemeManager.getAccent("nice")).onClick(() -> {
            try {
                int resolvedPort = existing.management() == NetworkMemberManagement.EXTERNAL ? parsePort(port[0], "Backend Port") : 0;
                NetworkCreationMember updated = new NetworkCreationMember(existing.instanceId(), route[0], role[0], address[0], resolvedPort, parseNonNegative(capacity[0], "Capacity"), resync[0], existing.management());
                members = members.stream().map(member -> member.equals(existing) ? updated : member).toList();
                popup[0].hide();
                refreshDraft();
            } catch (RuntimeException exception) {
                new Notification("Server Settings Invalid", rootMessage(exception), Notification.Type.ERROR);
            }
        }).build();
        AnimatedButton remove = new AnimatedButton.Builder().size(90, 20).label("Remove Server").accentType(ThemeManager.getAccent("danger")).onClick(() -> {
            popup[0].hide();
            removeBackend(existing.instanceId());
        }).build();
        PopupWidget.Builder builder = new PopupWidget.Builder("Configure " + backend(existing.instanceId()).getName()).size(420, 305).setResizable(true).setExpandWithDropdowns(true).onClose(() -> popup[0].hide());
        builder.addTextField("Route", route[0], value -> route[0] = value);
        builder.addDropdown("Role", Arrays.asList(NetworkMemberRole.LOBBY, NetworkMemberRole.FALLBACK, NetworkMemberRole.GAMEPLAY, NetworkMemberRole.RESTRICTED, NetworkMemberRole.MAINTENANCE, NetworkMemberRole.CUSTOM), role[0], value -> titleCase(value.name()), value -> role[0] = value);
        builder.addTextField("Address", address[0], value -> address[0] = value);
        if (existing.management() == NetworkMemberManagement.EXTERNAL) {
            builder.addTextField("Port", port[0], value -> port[0] = value);
        }
        builder.addTextField("Capacity", capacity[0], value -> capacity[0] = value);
        builder.addDropdown("ReSync", List.of(Boolean.TRUE, Boolean.FALSE), resync[0], value -> value ? "Enabled" : "Disabled", value -> resync[0] = value);
        builder.addTitleAction("Save", () -> save.onClick(0, 0, 0), PopupWidget.TitleActionRole.PRIMARY);
        builder.addTitleAction("Remove", () -> remove.onClick(0, 0, 0), PopupWidget.TitleActionRole.DESTRUCTIVE);
        popup[0] = builder.build();
        popup[0].setX((width - popup[0].getWidth()) / 2);
        popup[0].setY((height - popup[0].getHeight()) / 2);
        addDrawableChild(popup[0]);
        popup[0].show();
    }

    private void review() {
        if (preparing) {
            return;
        }
        String name = nameInput == null ? initialName : nameInput.getText();
        String port = portInput == null ? initialPort : portInput.getText();
        if (members.isEmpty()) {
            new Notification("Backend Required", "Create Or Add At Least One Backend", Notification.Type.ERROR);
            return;
        }
        NetworkCreationRequest request;
        try {
            request = new NetworkCreationRequest(name, proxy.getInstanceId(), parsePort(port, "Entry Port"), members, firewallVerified);
        } catch (RuntimeException exception) {
            new Notification("Network Settings Invalid", rootMessage(exception), Notification.Type.ERROR);
            return;
        }
        if (request.backends().stream().noneMatch(NetworkCreationMember::resyncEnabled)) {
            prepareReview(request, false);
            return;
        }
        PopupWidget[] popup = new PopupWidget[1];
        PopupWidget.Builder builder = new PopupWidget.Builder("Install ReSync").width(420);
        builder.addRow(new PopupWidget.PopupRow.Builder("Add Live Network Features").id("resync").description("Install The Latest ReSync On The Proxy And Enabled Backends For Player Controls, Shared Chat, Content, Events, And Live Status.").build());
        builder.addTitleAction("Continue Without ReSync", () -> {
            popup[0].hide();
            List<NetworkCreationMember> disabled = request.backends().stream().map(member -> new NetworkCreationMember(member.instanceId(), member.routeName(), member.role(), member.address(), member.preferredPort(), member.capacity(), false, member.management())).toList();
            prepareReview(new NetworkCreationRequest(request.name(), request.proxyInstanceId(), request.entryPort(), disabled, request.firewallVerified(), request.fallbackRoutes(), request.forcedHosts()), false);
        }, PopupWidget.TitleActionRole.SECONDARY);
        builder.addTitleAction("Install ReSync", () -> {
            popup[0].hide();
            prepareReview(request, true);
        }, PopupWidget.TitleActionRole.PRIMARY);
        popup[0] = builder.build();
        popup[0].setX((width - popup[0].getWidth()) / 2);
        popup[0].setY((height - popup[0].getHeight()) / 2);
        addDrawableChild(popup[0]);
        popup[0].show();
    }

    private void prepareReview(NetworkCreationRequest request, boolean installReSync) {
        preparing = true;
        List<Instance> instances = Rebase.get().getInstanceManager().getAllInstances();
        Map<String, Instance> instancesById = instances.stream().collect(Collectors.toMap(Instance::getInstanceId, instance -> instance));
        List<Instance> targets = Stream.concat(Stream.of(proxy), request.backends().stream().filter(NetworkCreationMember::resyncEnabled).map(member -> instancesById.get(member.instanceId())).filter(instance -> instance != null)).distinct().toList();
        Notification notification = new Notification.Builder().message(installReSync ? "Installing ReSync" : "Preparing Network").description(request.name()).type(Notification.Type.INFO).loading(true).autoSlideOut(false).build();
        CompletableFuture<Void> setup = installReSync ? CompletableFuture.supplyAsync(() -> NetworkReSyncSetup.installLatest(targets), Executors.IO).thenApply(result -> {
            if (!result.successful()) {
                throw new CompletionException(new IllegalStateException(result.failureMessage()));
            }
            return null;
        }) : CompletableFuture.completedFuture(null);
        setup.thenCompose(unused -> remotelyClient.getNetworkManager().prepareCreation(request, instances, List.of())).whenComplete((prepared, throwable) -> ScreenManager.getInstance().execute(() -> {
            preparing = false;
            if (throwable != null) {
                notification.update().message("Network Review Failed").description(rootMessage(throwable)).type(Notification.Type.ERROR).loading(false).autoSlideOut(true).commit();
                return;
            }
            notification.update().message("Network Review Ready").description(prepared.prepared().plan().changes().size() + " Changes").type(Notification.Type.SUCCESS).loading(false).autoSlideOut(true).commit();
            client.setScreen(new NetworkPlanReviewScreen(this, remotelyClient, prepared));
        }));
    }

    private void resume(NetworkJob job) {
        Notification notification = operationNotification("Resuming Network", job.message());
        remotelyClient.getNetworkManager().resumeJob(job.jobId(), Rebase.get().getInstanceManager().getAllInstances(), List.of()).whenComplete((updated, throwable) -> ScreenManager.getInstance().execute(() -> {
            if (throwable != null || updated == null || updated.status() != NetworkJobStatus.SUCCEEDED) {
                notification.update().message("Network Needs Attention").description(throwable == null ? updated == null ? "Creation job did not finish" : updated.message() : rootMessage(throwable)).type(Notification.Type.ERROR).loading(false).autoSlideOut(true).commit();
                return;
            }
            notification.update().message("Network Ready").description(updated.message()).type(Notification.Type.SUCCESS).loading(false).autoSlideOut(true).commit();
            openCreatedNetwork(updated.networkId());
        }));
    }

    void openCreatedNetwork(String networkId) {
        client.setScreen(parent);
        if (parent instanceof ServerManagerScreen serverManager) {
            serverManager.showNetworkSettings(networkId);
        }
    }

    private void rollback(NetworkJob job) {
        Notification notification = operationNotification("Rolling Back Network", job.message());
        remotelyClient.getNetworkManager().rollbackJob(job.jobId(), Rebase.get().getInstanceManager().getAllInstances()).whenComplete((updated, throwable) -> ScreenManager.getInstance().execute(() -> {
            if (throwable != null || updated == null || updated.status() != NetworkJobStatus.ROLLED_BACK) {
                notification.update().message("Rollback Failed").description(throwable == null ? updated == null ? "Rollback did not finish" : updated.message() : rootMessage(throwable)).type(Notification.Type.ERROR).loading(false).autoSlideOut(true).commit();
                return;
            }
            notification.update().message("Network Rolled Back").description(updated.message()).type(Notification.Type.SUCCESS).loading(false).autoSlideOut(true).commit();
            refreshDraft();
        }));
    }

    private Notification operationNotification(String message, String description) {
        return new Notification.Builder().message(message).description(description).type(Notification.Type.INFO).loading(true).autoSlideOut(false).build();
    }

    private void refreshDraft() {
        client.setScreen(currentDraft());
    }

    private NetworkCreationScreen currentDraft() {
        String name = nameInput == null ? initialName : nameInput.getText();
        String port = portInput == null ? initialPort : portInput.getText();
        return new NetworkCreationScreen(parent, remotelyClient, proxy, backends, members, name, port, firewallVerified);
    }

    private void createBackend() {
        List<NetworkServerCreationContext> contexts = NetworkServerCreationContext.available();
        NetworkServerCreationContext preferred = NetworkServerCreationContext.forInstance(proxy);
        NetworkServerCreationContext[] context = {contexts.stream().filter(candidate -> candidate.hostLabel().equals(preferred.hostLabel())).findFirst().orElse(contexts.getFirst())};
        ModLoader[] software = {ModLoader.PAPER};
        PopupWidget[] popup = new PopupWidget[1];
        AnimatedButton create = new AnimatedButton.Builder().size(100, 20).label("Create Backend").accentType(ThemeManager.getAccent("nice")).onClick(() -> {
            popup[0].hide();
            NetworkCreationScreen draft = currentDraft();
            client.setScreen(new ServerConfigurationScreen(draft, context[0].remoteHost(), remotelyClient, software[0], instance -> client.setScreen(draft.withBackend(instance))));
        }).build();
        PopupWidget.Builder builder = new PopupWidget.Builder("Create Backend").width(390).setExpandWithDropdowns(true).onClose(() -> popup[0].hide());
        builder.addDropdown("Host", contexts, context[0], NetworkServerCreationContext::hostLabel, value -> context[0] = value);
        builder.addDropdown("Software", backendSoftware(), software[0], ModLoader::toString, value -> software[0] = value);
        builder.addTitleAction("Create", () -> create.onClick(0, 0, 0), PopupWidget.TitleActionRole.PRIMARY);
        popup[0] = builder.build();
        popup[0].setX((width - popup[0].getWidth()) / 2);
        popup[0].setY((height - popup[0].getHeight()) / 2);
        addDrawableChild(popup[0]);
        popup[0].show();
    }

    private void addExistingBackend() {
        List<Instance> candidates = Rebase.get().getInstanceManager().getAllInstances().stream().filter(instance -> !instance.getInstanceId().equals(proxy.getInstanceId())).filter(instance -> !instance.isProxyServer()).filter(instance -> backends.stream().noneMatch(backend -> backend.getInstanceId().equals(instance.getInstanceId()))).filter(instance -> remotelyClient.getNetworkManager().getNetworkForInstance(instance.getInstanceId()).isEmpty()).toList();
        if (candidates.isEmpty()) {
            new Notification("No Available Servers", "Create A Backend Or Detach One First", Notification.Type.WARN);
            return;
        }
        Instance[] selection = {candidates.getFirst()};
        PopupWidget[] popup = new PopupWidget[1];
        AnimatedButton add = new AnimatedButton.Builder().size(100, 20).label("Add Server").accentType(ThemeManager.getAccent("nice")).onClick(() -> {
            popup[0].hide();
            client.setScreen(currentDraft().withBackend(selection[0]));
        }).build();
        PopupWidget.Builder builder = new PopupWidget.Builder("Add Existing Server").width(380).setExpandWithDropdowns(true).onClose(() -> popup[0].hide());
        builder.addDropdown("Server", candidates, selection[0], instance -> instance.getName() + " • " + NetworkHostScope.resolve(instance), instance -> selection[0] = instance);
        builder.addTitleAction("Add", () -> add.onClick(0, 0, 0), PopupWidget.TitleActionRole.PRIMARY);
        popup[0] = builder.build();
        popup[0].setX((width - popup[0].getWidth()) / 2);
        popup[0].setY((height - popup[0].getHeight()) / 2);
        addDrawableChild(popup[0]);
        popup[0].show();
    }

    private NetworkCreationScreen withBackend(Instance instance) {
        if (backends.stream().anyMatch(backend -> backend.getInstanceId().equals(instance.getInstanceId()))) {
            return this;
        }
        List<NetworkCreationMember> sourceMembers = members == null ? initialMembers : members;
        List<Instance> updatedBackends = Stream.concat(backends.stream(), Stream.of(instance)).toList();
        List<NetworkCreationMember> updatedMembers = Stream.concat(sourceMembers.stream(), Stream.of(defaultMember(proxy, instance, sourceMembers))).toList();
        return new NetworkCreationScreen(parent, remotelyClient, proxy, updatedBackends, updatedMembers, initialName, initialPort, initialFirewallVerified);
    }

    private void removeBackend(String instanceId) {
        List<Instance> updatedBackends = backends.stream().filter(instance -> !instance.getInstanceId().equals(instanceId)).toList();
        List<NetworkCreationMember> updatedMembers = members.stream().filter(member -> !member.instanceId().equals(instanceId)).toList();
        String name = nameInput == null ? initialName : nameInput.getText();
        String port = portInput == null ? initialPort : portInput.getText();
        client.setScreen(new NetworkCreationScreen(parent, remotelyClient, proxy, updatedBackends, updatedMembers, name, port, firewallVerified));
    }

    private Instance backend(String instanceId) {
        return backends.stream().filter(instance -> instance.getInstanceId().equals(instanceId)).findFirst().orElseThrow(() -> new IllegalStateException("Backend Is Unavailable"));
    }

    private int parsePort(String value, String label) {
        try {
            int port = Integer.parseInt(value == null ? "" : value.trim());
            if (port < 1 || port > 65535) {
                throw new IllegalArgumentException(label + " Must Be Between 1 And 65535");
            }
            return port;
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException(label + " Must Be A Number", exception);
        }
    }

    private int parseNonNegative(String value, String label) {
        try {
            int number = Integer.parseInt(value == null ? "" : value.trim());
            if (number < 0) {
                throw new IllegalArgumentException(label + " Cannot Be Negative");
            }
            return number;
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException(label + " Must Be A Number", exception);
        }
    }

    private String titleCase(String value) {
        String normalized = value == null ? "" : value.toLowerCase(Locale.ROOT).replace('_', ' ');
        StringBuilder result = new StringBuilder(normalized.length());
        boolean capitalize = true;
        for (char character : normalized.toCharArray()) {
            result.append(capitalize ? Character.toUpperCase(character) : character);
            capitalize = character == ' ';
        }
        return result.toString();
    }

    private String rootMessage(Throwable throwable) {
        Throwable current = throwable;
        while ((current instanceof CompletionException || current instanceof ExecutionException) && current.getCause() != null) {
            current = current.getCause();
        }
        return current.getMessage() == null ? current.getClass().getSimpleName() : current.getMessage();
    }

    private static List<NetworkCreationMember> defaultMembers(Instance proxy, List<Instance> backends) {
        Map<String, Integer> names = new LinkedHashMap<>();
        return backends.stream().map(backend -> {
            String baseRoute = normalizeRoute(backend.getName());
            int occurrence = names.merge(baseRoute, 1, Integer::sum);
            String route = occurrence == 1 ? baseRoute : baseRoute + "-" + occurrence;
            String address = NetworkHostScope.resolve(proxy).equals(NetworkHostScope.resolve(backend)) ? "" : backend.getBackendConfig() == null ? "" : backend.getBackendConfig().credentials.getOrDefault("host", "");
            NetworkMemberRole role = names.values().stream().mapToInt(Integer::intValue).sum() == 1 ? NetworkMemberRole.LOBBY : NetworkMemberRole.GAMEPLAY;
            return new NetworkCreationMember(backend.getInstanceId(), route, role, address, 0, 0, true);
        }).toList();
    }

    private static NetworkCreationMember defaultMember(Instance proxy, Instance backend, List<NetworkCreationMember> existing) {
        String baseRoute = normalizeRoute(backend.getName());
        String route = baseRoute;
        int suffix = 2;
        while (routeTaken(existing, route)) {
            route = baseRoute + "-" + suffix++;
        }
        String address = NetworkHostScope.resolve(proxy).equals(NetworkHostScope.resolve(backend)) ? "" : backend.getBackendConfig() == null || backend.getBackendConfig().credentials == null ? "" : backend.getBackendConfig().credentials.getOrDefault("host", "");
        NetworkMemberRole role = existing.isEmpty() ? NetworkMemberRole.LOBBY : NetworkMemberRole.GAMEPLAY;
        return new NetworkCreationMember(backend.getInstanceId(), route, role, address, 0, 0, true);
    }

    private static boolean routeTaken(List<NetworkCreationMember> members, String route) {
        return members.stream().anyMatch(member -> member.routeName().equalsIgnoreCase(route));
    }

    private static List<ModLoader> backendSoftware() {
        return List.of(ModLoader.PAPER, ModLoader.PURPUR, ModLoader.FOLIA, ModLoader.LEAF, ModLoader.FABRIC, ModLoader.QUILT, ModLoader.NEOFORGE, ModLoader.FORGE, ModLoader.SPIGOT, ModLoader.VANILLA);
    }

    private static int observedPort(Instance instance, int fallback) {
        try {
            int port = Integer.parseInt(instance.getServerProperties().getProperty("server-port", String.valueOf(fallback)).trim());
            return port >= 1 && port <= 65535 ? port : fallback;
        } catch (NumberFormatException exception) {
            return fallback;
        }
    }

    private static String normalizeRoute(String value) {
        String route = value == null ? "server" : value.trim().toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_-]+", "-");
        return route.isBlank() ? "server" : route;
    }

    private static boolean providerManaged(Instance instance) {
        return instance != null && instance.getBackendConfig() != null && instance.getBackendConfig().type != null && (PteroBackend.isPanelType(instance.getBackendConfig().type) || "RESTUDIO".equalsIgnoreCase(instance.getBackendConfig().type));
    }
}
