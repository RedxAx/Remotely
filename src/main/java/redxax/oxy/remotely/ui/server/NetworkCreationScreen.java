package redxax.oxy.remotely.ui.server;

import redxax.oxy.remotely.network.NetworkMemberRole;
import restudio.rebase.restudio.api.models.ServerModels;
import restudio.rescreen.platform.Async;
import restudio.rescreen.platform.input.ReKey;
import restudio.rescreen.platform.input.ReKeyEvent;
import restudio.rescreen.platform.input.ReMouseEvent;
import restudio.rescreen.platform.input.ReTextInputEvent;
import restudio.rescreen.ui.core.Screen;
import restudio.rescreen.ui.core.ScreenManager;
import restudio.rescreen.ui.desktop.DesktopWindowBehaviorProvider;
import restudio.rescreen.ui.rescreen.Container;
import restudio.rescreen.ui.rescreen.ReScreen;
import restudio.rescreen.ui.rescreen.TabsManager;
import restudio.rescreen.ui.rescreen.layout.ManagedLayout;
import restudio.rescreen.ui.settings.Setting;
import restudio.rescreen.ui.settings.options.ConfigOption;
import restudio.rescreen.ui.widgets.AnimatedWidget;
import restudio.rescreen.ui.widgets.IconButton;
import restudio.rescreen.ui.widgets.MountableButtonWidget;
import restudio.rescreen.util.Identifier;
import restudio.rescreen.util.Notification;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class NetworkCreationScreen extends ReScreen implements DesktopWindowBehaviorProvider {
    private static final List<NetworkMemberRole> BACKEND_ROLES = List.of(
        NetworkMemberRole.LOBBY,
        NetworkMemberRole.FALLBACK,
        NetworkMemberRole.GAMEPLAY,
        NetworkMemberRole.RESTRICTED,
        NetworkMemberRole.MAINTENANCE,
        NetworkMemberRole.CUSTOM
    );

    private final Screen parent;
    private final ServerScreenHost host;
    private final List<ServerModels.ClientServerView> selected;
    private final List<Member> members = new ArrayList<>();
    private final List<Setting> networkSections = new ArrayList<>();
    private final SearchMode search = new SearchMode(false);
    private Container networkContainer;
    private ConfigOption<String> nameOption;
    private ConfigOption<Integer> entryPortOption;
    private ConfigOption<Boolean> reSyncOption;
    private IconButton cancelButton;
    private IconButton createButton;
    private boolean bootstrapped;
    private boolean creating;
    private boolean closed;
    private long generation;

    public NetworkCreationScreen(Screen parent, ServerScreenHost host, List<ServerModels.ClientServerView> selected) {
        this.parent = parent;
        this.host = host;
        this.selected = selected == null ? List.of() : selected.stream().filter(server -> server != null && !serverId(server).isBlank()).toList();
    }

    @Override
    public String getDesktopAppId() {
        return "network-creation";
    }

    @Override
    public String getDesktopAppTitle() {
        return "Create Network";
    }

    @Override
    public String getDesktopAppIconPath() {
        return "network.png";
    }

    @Override
    public DesktopWindowBehavior getDesktopWindowBehavior() {
        return DesktopWindowBehavior.SINGLETON;
    }

    @Override
    public boolean closeThroughDesktopOverlay() {
        return !creating;
    }

    @Override
    public void init() {
        super.init();
        closed = false;
        cancelButton = new IconButton.Builder().size(18, 18).label("Cancel").imagePath("goback.png").autoWidthOnTextChange(true).onClick(this::cancel).build();
        createButton = new IconButton.Builder().size(18, 18).label("Create Network").imagePath("checkmark.png").autoWidthOnTextChange(true).onClick(this::create).build();
        header().addLeft(cancelButton).addRight(createButton).setSearchMode(search, true).searchDebounce(100).build();
        tabs().builder()
            .allowAdd(true)
            .allowClose(true)
            .allowRename(false)
            .allowReorder(true)
            .position(6, 36)
            .size(width - 12, 18)
            .onTabSelected(this::selectTab)
            .onPlusButtonClicked(() -> addNewMember(false, true))
            .onTabCloseRequested(this::canCloseTab)
            .onTabClosed(this::tabClosed)
            .build();
        search.setPlaceholder("Search Network Setup");
        search.setOnTextChange(this::filterActiveTab);
        networkContainer = createContentContainer();
        tabs().addTab("Network", networkContainer, "network.png");
        if (!bootstrapped) {
            bootstrapped = true;
            createNetworkOptions();
            bootstrapMembers();
        } else {
            for (Member member : members) attachMember(member, false);
        }
        rebuildNetwork();
        tabs().setActiveTab(networkContainer);
        refreshHeader();
    }

    private Container createContentContainer() {
        Container container = createContainer(6, 60, width - 12, Math.max(80, height - 66));
        container.layout(new ManagedLayout()).columns(1).padding(4).verticalSpacing(6).scrolling(true).backgroundDrawing(false).setSearchMode(search);
        return container;
    }

    private void createNetworkOptions() {
        nameOption = ConfigOption.<String>builder("Network Name")
            .description("The Name Shown For This Network In Remotely")
            .bind(() -> "New Network", value -> {})
            .defaultValue("New Network")
            .resettable(false)
            .build();
        entryPortOption = ConfigOption.<Integer>builder("Player Port")
            .description("The Public Port Players Use To Connect To The Proxy")
            .bind(() -> 25565, value -> {})
            .defaultValue(25565)
            .resettable(false)
            .build();
        reSyncOption = ConfigOption.<Boolean>builder("Install ReSync")
            .description("Install ReSync Across Every New Network Server")
            .bind(() -> true, value -> {})
            .defaultValue(true)
            .resettable(false)
            .build();
        networkSections.clear();
        networkSections.add(new Setting.Builder("Network")
            .addOption(nameOption)
            .addOption(entryPortOption)
            .addOption(reSyncOption)
            .build());
    }

    private void bootstrapMembers() {
        ServerModels.ClientServerView proxy = selected.stream().filter(NetworkCreationScreen::isProxy).findFirst().orElse(null);
        if (proxy == null) addNewMember(true, false);
        else addExistingMember(proxy, true);
        selected.stream().filter(server -> !isProxy(server)).forEach(server -> addExistingMember(server, false));
        if (members.stream().noneMatch(member -> !member.proxy)) addNewMember(false, false);
    }

    private void addExistingMember(ServerModels.ClientServerView server, boolean proxy) {
        Member member = new Member(proxy, server, proxy ? "proxy" : route(displayName(server)), proxy ? NetworkMemberRole.PROXY : NetworkMemberRole.GAMEPLAY);
        members.add(member);
        attachMember(member, false);
    }

    private void addNewMember(boolean proxy, boolean select) {
        if (closed || creating || proxy && members.stream().anyMatch(member -> member.proxy)) return;
        int number = proxy ? 1 : Math.max(1, (int) members.stream().filter(member -> !member.proxy).count() + 1);
        String name = proxy ? "Proxy" : "Backend " + number;
        Member member = new Member(proxy, null, proxy ? "proxy" : route(name), proxy ? NetworkMemberRole.PROXY : members.stream().noneMatch(current -> !current.proxy) ? NetworkMemberRole.LOBBY : NetworkMemberRole.GAMEPLAY);
        members.add(member);
        attachMember(member, select);
        rebuildNetwork();
        refreshHeader();
        long requestGeneration = generation;
        ServerConfigurationDraft.create(this, host, proxy ? "VELOCITY" : "PAPER", name).whenComplete((draft, error) -> ScreenManager.getInstance().execute(() -> {
            if (closed || requestGeneration != generation || !members.contains(member)) {
                if (draft != null) draft.close();
                return;
            }
            if (error != null) member.failure = rootMessage(error);
            else member.draft = draft;
            buildMember(member);
            refreshHeader();
        }));
    }

    private void attachMember(Member member, boolean select) {
        member.container = createContentContainer();
        member.tab = tabs().addTab(member.title(), member.container, member.proxy ? "network.png" : "server.png");
        member.tab.setData(member);
        buildMember(member);
        if (select) tabs().setActiveTab(member.container);
    }

    private void buildMember(Member member) {
        if (member.container == null) return;
        member.container.clearWidgets();
        if (!member.proxy) {
            Setting.Builder networking = new Setting.Builder("Networking");
            networking.addOption(member.route);
            networking.addOption(member.role);
            member.container.addWidget(networking.build());
        }
        if (member.existing != null) {
            Setting.Builder existing = new Setting.Builder("Server");
            existing.addRow("", message(displayName(member.existing), serverDescription(member.existing)));
            member.container.addWidget(existing.build());
        } else if (member.draft == null) {
            Setting.Builder status = new Setting.Builder(member.failure.isBlank() ? "Loading Server Configuration" : "Configuration Unavailable");
            status.addRow("", message(member.failure.isBlank() ? "Preparing " + member.title() : "Could Not Prepare " + member.title(),
                member.failure.isBlank() ? "Loading The Server Software And Resource Options" : member.failure));
            member.container.addWidget(status.build());
        } else {
            for (Setting section : member.draft.sections()) {
                section.fitContentHeight();
                member.container.addWidget(section);
            }
        }
        filter(member.container, searchText());
        member.container.updateWidgetPositions();
    }

    private void rebuildNetwork() {
        if (networkContainer == null) return;
        networkContainer.clearWidgets();
        for (Setting section : networkSections) {
            section.fitContentHeight();
            networkContainer.addWidget(section);
        }
        Setting.Builder topology = new Setting.Builder("Servers");
        for (Member member : members) {
            String description = member.proxy ? "Velocity Proxy" : roleName(member.role.get()) + " • " + member.route.get();
            topology.addRow("", message(member.title(), description));
        }
        networkContainer.addWidget(topology.build());
        Setting.Builder transaction = new Setting.Builder("Creation");
        transaction.addRow("", message("All Or Nothing", "Create Every New Server And The Network Together, Or Remove Every New Server If Setup Fails"));
        networkContainer.addWidget(transaction.build());
        filter(networkContainer, searchText());
        networkContainer.updateWidgetPositions();
    }

    private void selectTab(TabsManager.Tab tab) {
        if (tab == null) return;
        setActiveContainer(tab.getContainer());
        filter(tab.getContainer(), searchText());
    }

    private boolean canCloseTab(TabsManager.Tab tab) {
        return !creating && tab != null && tab.getData() instanceof Member member && !member.proxy;
    }

    private void tabClosed(TabsManager.Tab tab) {
        if (tab == null || !(tab.getData() instanceof Member member)) return;
        members.remove(member);
        member.close();
        rebuildNetwork();
        refreshHeader();
    }

    private void filterActiveTab(String query) {
        TabsManager.Tab tab = tabs().getActiveTab();
        if (tab != null) filter(tab.getContainer(), query);
    }

    private void filter(Container container, String query) {
        if (container == null) return;
        for (AnimatedWidget widget : container.getWidgets()) {
            if (widget instanceof Setting setting) setting.filter(query);
        }
        container.updateWidgetPositions();
        container.resetScroll();
    }

    private String searchText() {
        return header().searchBox == null ? "" : header().searchBox.getText();
    }

    private void create() {
        if (creating || closed) return;
        NetworkCreationPlan plan;
        try {
            plan = plan();
        } catch (RuntimeException error) {
            fail(rootMessage(error));
            return;
        }
        creating = true;
        refreshHeader();
        long requestGeneration = ++generation;
        Notification notification = new Notification.Builder()
            .message("Creating Network")
            .description(plan.name())
            .type(Notification.Type.INFO)
            .loading(true)
            .autoSlideOut(false)
            .build();
        Async<Void> request;
        try {
            request = host.createNetwork(plan);
        } catch (RuntimeException error) {
            request = Async.failed(error);
        }
        request.whenComplete((ignored, error) -> ScreenManager.getInstance().execute(() -> {
            if (error == null) {
                notification.update().message("Network Created").description(plan.name()).type(Notification.Type.SUCCESS).loading(false).autoSlideOut(true).commit();
            } else {
                notification.update().message("Network Creation Failed").description(rootMessage(error)).type(Notification.Type.ERROR).loading(false).autoSlideOut(true).commit();
            }
            if (closed || requestGeneration != generation) return;
            creating = false;
            if (error != null) {
                fail(rootMessage(error));
                refreshHeader();
                return;
            }
            closeDrafts();
            closed = true;
            host.reloadInstances();
            host.application().openParentScreen(this, parent);
        }));
    }

    private NetworkCreationPlan plan() {
        if (members.stream().filter(member -> member.proxy).count() != 1) throw new IllegalArgumentException("One Proxy Is Required");
        if (members.stream().noneMatch(member -> !member.proxy)) throw new IllegalArgumentException("Add At Least One Backend");
        if (members.stream().anyMatch(member -> member.existing == null && member.draft == null)) throw new IllegalStateException("Wait For Every Server Configuration To Load");
        String name = nameOption.get() == null ? "" : nameOption.get().trim();
        if (name.isBlank()) throw new IllegalArgumentException("Network Name Is Required");
        int entryPort = entryPortOption.get();
        if (entryPort < 1 || entryPort > 65535) throw new IllegalArgumentException("Player Port Must Be Between 1 And 65535");
        Set<String> routes = new LinkedHashSet<>();
        for (Member member : members) {
            if (member.draft != null) member.draft.apply();
            if (member.proxy) {
                String loader = member.existing == null ? String.valueOf(member.draft.target().modLoader()) : member.existing.loader;
                if (!"VELOCITY".equalsIgnoreCase(loader)) throw new IllegalArgumentException("The Proxy Must Use Velocity");
            } else {
                String routeName = member.route.get() == null ? "" : member.route.get().trim();
                if (routeName.isBlank()) throw new IllegalArgumentException("Every Backend Needs A Route Name");
                if (!routes.add(routeName.toLowerCase(Locale.ROOT))) throw new IllegalArgumentException("Backend Route Names Must Be Unique");
            }
        }
        boolean installReSync = reSyncOption.get();
        List<NetworkCreationPlan.Server> servers = members.stream().map(member -> member.plan(installReSync)).toList();
        return new NetworkCreationPlan(name, entryPort, servers);
    }

    private void refreshHeader() {
        if (createButton == null || cancelButton == null) return;
        boolean ready = !creating && members.stream().anyMatch(member -> member.proxy) && members.stream().anyMatch(member -> !member.proxy)
            && members.stream().noneMatch(member -> member.existing == null && member.draft == null);
        createButton.setMessage(creating ? "Creating Network" : "Create Network");
        createButton.setIcon(creating ? Identifier.animatedIcon("loadingGreen.png") : Identifier.icon("checkmark.png"));
        createButton.setActive(ready);
        cancelButton.setActive(!creating);
    }

    private void fail(String message) {
        new Notification("Network Setup", message, Notification.Type.ERROR);
    }

    private void cancel() {
        if (creating || closed) return;
        close();
    }

    @Override
    public void close() {
        if (creating || closed) return;
        closed = true;
        generation++;
        closeDrafts();
        host.application().openParentScreen(this, parent);
    }

    @Override
    public void onDesktopWindowClosing() {
        if (creating) return;
        closed = true;
        generation++;
        closeDrafts();
    }

    private void closeDrafts() {
        members.forEach(Member::close);
    }

    @Override
    public boolean keyPressed(ReKeyEvent event) {
        if (creating) return true;
        if (event.key() == ReKey.ESCAPE) {
            cancel();
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public boolean mouseClicked(ReMouseEvent event) {
        return creating || super.mouseClicked(event);
    }

    @Override
    public boolean textInput(ReTextInputEvent event) {
        return creating || super.textInput(event);
    }

    @Override
    public void removed() {
        if (!closed) {
            closed = true;
            generation++;
            closeDrafts();
        }
        super.removed();
    }

    private MountableButtonWidget message(String title, String description) {
        MountableButtonWidget row = new MountableButtonWidget.Builder(title).description(description).build();
        row.setHeight(30);
        return row;
    }

    private static String serverId(ServerModels.ClientServerView server) {
        if (server == null) return "";
        if (server.identifier != null && !server.identifier.isBlank()) return server.identifier;
        return server.uuid == null ? "" : server.uuid;
    }

    private static boolean isProxy(ServerModels.ClientServerView server) {
        return server != null && "VELOCITY".equalsIgnoreCase(server.loader);
    }

    private static String displayName(ServerModels.ClientServerView server) {
        return server == null || server.name == null || server.name.isBlank() ? "Server" : server.name;
    }

    private static String serverDescription(ServerModels.ClientServerView server) {
        String software = server.loader == null || server.loader.isBlank() ? "Minecraft Server" : server.loader;
        return software + (server.version == null || server.version.isBlank() ? "" : " • " + server.version);
    }

    private static String route(String name) {
        String route = name == null ? "server" : name.trim().toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_-]+", "-");
        return route.isBlank() ? "server" : route;
    }

    private static String roleName(NetworkMemberRole role) {
        String value = role == null ? "Gameplay" : role.name().toLowerCase(Locale.ROOT).replace('_', ' ');
        return Character.toUpperCase(value.charAt(0)) + value.substring(1);
    }

    private static String rootMessage(Throwable error) {
        Throwable current = error;
        while (current.getCause() != null) current = current.getCause();
        return current.getMessage() == null || current.getMessage().isBlank() ? "Network Creation Failed" : current.getMessage();
    }

    private final class Member {
        private final boolean proxy;
        private final ServerModels.ClientServerView existing;
        private final ConfigOption<String> route;
        private final ConfigOption<NetworkMemberRole> role;
        private ServerConfigurationDraft draft;
        private Container container;
        private TabsManager.Tab tab;
        private String failure = "";

        private Member(boolean proxy, ServerModels.ClientServerView existing, String route, NetworkMemberRole role) {
            this.proxy = proxy;
            this.existing = existing;
            this.route = ConfigOption.<String>builder("Route Name")
                .description("The Short Name Used By The Proxy And Routing Rules")
                .bind(() -> route, value -> {})
                .defaultValue(route)
                .resettable(false)
                .build();
            this.role = ConfigOption.<NetworkMemberRole>builder("Network Role")
                .description("How This Backend Participates In Player Routing")
                .bind(() -> role, value -> {})
                .options(BACKEND_ROLES)
                .display(NetworkCreationScreen::roleName)
                .defaultValue(role)
                .resettable(false)
                .build();
        }

        private String title() {
            if (existing != null) return displayName(existing);
            if (draft != null && draft.target().name() != null && !draft.target().name().isBlank()) return draft.target().name();
            return proxy ? "Proxy" : routeName();
        }

        private String routeName() {
            String value = route.get();
            if (value == null || value.isBlank()) return "Backend";
            String display = value.replace('-', ' ').replace('_', ' ');
            return Character.toUpperCase(display.charAt(0)) + display.substring(1);
        }

        private NetworkCreationPlan.Server plan(boolean installReSync) {
            String id = existing == null ? "" : serverId(existing);
            Object template = draft == null ? null : draft.target().raw();
            String location = draft == null ? "" : draft.location();
            return new NetworkCreationPlan.Server(id, template, null, location, draft == null ? null : draft.settings(), proxy,
                proxy ? "proxy" : route.get(), proxy ? NetworkMemberRole.PROXY : role.get(), 0, installReSync && existing == null);
        }

        private void close() {
            if (draft != null) {
                draft.close();
                draft = null;
            }
        }
    }
}
