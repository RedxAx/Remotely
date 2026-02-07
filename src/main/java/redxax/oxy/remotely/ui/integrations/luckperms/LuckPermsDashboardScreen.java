package redxax.oxy.remotely.ui.integrations.luckperms;

import redxax.oxy.remotely.data.integrations.luckperms.LuckPermsDTOs.*;
import redxax.oxy.remotely.data.integrations.luckperms.LuckPermsService;
import restudio.rebase.account.Account;
import restudio.rescreen.theme.ThemeManager;
import restudio.rescreen.ui.core.ScreenManager;
import restudio.rescreen.ui.core.Screen;
import restudio.rescreen.ui.rescreen.Container;
import restudio.rescreen.ui.rescreen.ReScreen;
import restudio.rescreen.ui.rescreen.layout.ManagedLayout;
import restudio.rescreen.ui.widgets.*;
import restudio.rescreen.util.Notification;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import java.util.regex.Pattern;
import java.util.regex.Matcher;
import java.util.concurrent.CompletableFuture;
import java.util.*;
import java.awt.image.BufferedImage;

import static restudio.rescreen.config.Config.desktopMode;
import static restudio.rescreen.config.Config.loading;

public class LuckPermsDashboardScreen extends ReScreen {
    private final Screen parent;
    private final LuckPermsService service;
    private Container sidebar;
    private Container contentArea;
    private enum ViewMode { GROUPS, USERS, TRACKS }
    private enum DetailType { NONE, GROUP, USER }
    private ViewMode currentMode = ViewMode.GROUPS;
    private DetailType activeDetailType = DetailType.NONE;
    private String activeDetailId = null;
    private volatile int refreshSeq = 0;
    private boolean initialGroupsRendered = false;
    private boolean initialUsersRendered = false;
    private boolean initialTracksRendered = false;

    public LuckPermsDashboardScreen(Screen parent, LuckPermsService service) {
        this.parent = parent;
        this.service = service;
    }

    public String getDesktopAppId() {
        return "luckperms-dashboard";
    }

    public String getDesktopAppTitle() {
        return "LuckPerms";
    }

    public String getDesktopAppIconPath() {
        return "change.png";
    }

    @Override
    public void init() {
        super.init();
        header().reset();
        if (!desktopMode) {
            header().addRight("close.png", this::close, "");
        }
        header().addRight("reload.png", this::refreshData, "Force Refresh Data").setSearchMode(new SearchMode(true), true).build();
        sidebar = createContainer("lp_sidebar", 5, 35, 160, height - 40);
        sidebar.layout(new ManagedLayout()).verticalSpacing(4).padding(4);
        contentArea = createContainer("lp_content", 170, 35, width - 175, height - 40);
        contentArea.layout(new ManagedLayout()).columns(1).padding(6).scrolling(true);
        contentArea.setSearchMode(new SearchMode(true));
        addDrawableChild(sidebar, contentArea);
        buildSidebar();
        loadView(currentMode);
    }

    private void buildSidebar() {
        sidebar.clearWidgets();
        sidebar.addWidget(createNavButton("Groups", "server.png", ViewMode.GROUPS));
        sidebar.addWidget(createNavButton("Users", "steve.png", ViewMode.USERS));
        sidebar.addWidget(createNavButton("Tracks", "ladder.png", ViewMode.TRACKS));
        sidebar.addWidget(new AnimatedButton.Builder().label(" ").active(false).flat(true).size(0, 4).build());
        IconButton createBtn = new IconButton.Builder().imagePath("create.png").label("Create").onClick(this::showCreatePopup).accentType(ThemeManager.getAccent("nice")).build();
        Container buttonCont = new Container(0, 0, 0, 24);
        buttonCont.layout(new ManagedLayout());
        sidebar.addWidget(createBtn);
        sidebar.updateWidgetPositions();
    }

    private AnimatedWidget createNavButton(String label, String icon, ViewMode mode) {
        return new MountableButtonWidget.Builder(label).hiddenText(mode == currentMode ? "Selected" : "").onClick(() -> loadView(mode)).build();
    }

    private void loadView(ViewMode mode) {
        this.currentMode = mode;
        this.activeDetailType = DetailType.NONE;
        this.activeDetailId = null;
        buildSidebar();
        refreshContent();
    }

    private void refreshData() {
        service.clearCache();
        refreshContent();
        new Notification("Refreshed", "Data cache cleared and reloaded.", Notification.Type.SUCCESS);
    }

    private void refreshContent() {
        int seq = ++refreshSeq;
        double prevScroll = getScrollOffset(contentArea);
        contentArea.clearWidgets();
        loading = true;
        if (activeDetailType == DetailType.GROUP && activeDetailId != null) {
            service.getGroup(activeDetailId).thenAccept(g -> applyIfFresh(seq, () -> {
                loading = false;
                if (g != null) openEditor(g);
                else {
                    activeDetailType = DetailType.NONE;
                    activeDetailId = null;
                    refreshContent();
                }
            }));
            return;
        } else if (activeDetailType == DetailType.USER && activeDetailId != null) {
            try {
                UUID uid = UUID.fromString(activeDetailId);
                service.getClient().getUser(uid).thenAccept(u -> applyIfFresh(seq, () -> {
                    loading = false;
                    if (u != null) openEditor(u);
                    else {
                        activeDetailType = DetailType.NONE;
                        activeDetailId = null;
                        refreshContent();
                    }
                }));
                return;
            } catch (Exception ignored) {
                activeDetailType = DetailType.NONE;
                activeDetailId = null;
            }
        }
        CompletableFuture<?> future;
        switch (currentMode) {
            case USERS -> future = service.getAllUsers().thenCompose(list -> renderUserListSmart(list, seq, prevScroll));
            case GROUPS -> future = service.getAllGroups().thenCompose(list -> renderGroupListSmart(list, seq, prevScroll));
            case TRACKS -> future = service.getAllTracks().thenAccept(list -> applyIfFresh(seq, () -> {
                renderTrackList(list);
                loading = false;
                restoreScroll(contentArea, prevScroll);
            }));
            default -> future = CompletableFuture.completedFuture(null);
        }
        future.exceptionally(e -> {
            applyIfFresh(seq, () -> {
                loading = false;
                contentArea.addWidget(new AnimatedButton.Builder().label("Error loading data: " + e.getMessage()).active(false).accentType(ThemeManager.getAccent("danger")).build());
                contentArea.updateWidgetPositions();
                restoreScroll(contentArea, prevScroll);
            });
            return null;
        });
    }

    private void applyIfFresh(int seq, Runnable r) {
        if (seq == refreshSeq) ScreenManager.getInstance().execute(r);
    }

    private CompletableFuture<Void> renderGroupListSmart(List<String> groups, int seq, double prevScroll) {
        List<CompletableFuture<Group>> futures = new ArrayList<>();
        for (String g : groups) futures.add(service.getGroup(g));
        return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).thenRun(() -> {
            List<Group> detailed = futures.stream().map(CompletableFuture::join).filter(Objects::nonNull).sorted(Comparator.comparing((Group g) -> g.weight == null ? 0 : g.weight).reversed().thenComparing(g -> g.name)).collect(Collectors.toList());
            applyIfFresh(seq, () -> {
                for (Group g : detailed) {
                    String weight = g.weight != null ? "Weight: " + g.weight : "Weight: 0";
                    String display = g.displayName != null ? g.displayName : g.name;
                    MountableButtonWidget widget = new MountableButtonWidget.Builder(display).hiddenText(g.name.equals(display) ? "" : g.name).description(weight).onClick(() -> openEditor(g)).build();
                    setEntranceAnimation(widget, !initialGroupsRendered);
                    widget.mountedWidgets.add(new SquareButtonWidget.Builder().imagePath("delete.png").accentType(ThemeManager.getAccent("danger")).onClick(() -> confirmDeleteGroup(g)).build());
                    widget.mountedWidgets.add(new SquareButtonWidget.Builder().imagePath("edit.png").onClick(() -> openEditor(g)).build());
                    contentArea.addWidget(widget);
                }
                contentArea.updateWidgetPositions();
                loading = false;
                restoreScroll(contentArea, prevScroll);
                initialGroupsRendered = true;
            });
        });
    }

    private CompletableFuture<Void> renderUserListSmart(List<String> uuids, int seq, double prevScroll) {
        CompletableFuture<List<String>> allGroupsFuture = service.getAllGroups();
        return allGroupsFuture.thenCompose(allGroups -> {
            List<CompletableFuture<Metadata>> metaFutures = new ArrayList<>();
            for (String u : uuids) metaFutures.add(service.getUserMetadata(UUID.fromString(u)));
            return CompletableFuture.allOf(metaFutures.toArray(new CompletableFuture[0])).thenRun(() -> {
                Map<String, Integer> weights = new HashMap<>();
                List<CompletableFuture<Group>> weightFetches = new ArrayList<>();
                for (String g : allGroups) weightFetches.add(service.getGroup(g));
                CompletableFuture.allOf(weightFetches.toArray(new CompletableFuture[0])).join();
                for (CompletableFuture<Group> gf : weightFetches) {
                    Group g = gf.join();
                    if (g != null) weights.put(g.name, g.weight != null ? g.weight : 0);
                }
                List<UserItem> items = new ArrayList<>();
                for (int i = 0; i < uuids.size(); i++) {
                    String uuid = uuids.get(i);
                    Metadata m = metaFutures.get(i).join();
                    String pg = m != null ? m.primaryGroup : null;
                    int w = pg != null ? weights.getOrDefault(pg, 0) : 0;
                    items.add(new UserItem(uuid, w));
                }
                items.sort(Comparator.comparingInt((UserItem it) -> it.weight).reversed().thenComparing(it -> it.uuid));
                applyIfFresh(seq, () -> {
                    for (UserItem it : items) {
                        MountableButtonWidget widget = new MountableButtonWidget.Builder(it.uuid).description("Loading...").onClick(() -> openUserEditor(it.uuid)).build();
                        setEntranceAnimation(widget, !initialUsersRendered);
                        UUID uid;
                        try {
                            uid = UUID.fromString(it.uuid);
                        } catch (Exception ex) {
                            uid = null;
                        }
                        if (uid != null) {
                            UUID finalUid = uid;
                            service.getClient().getUser(uid).thenAccept(user -> applyIfFresh(seq, () -> {
                                String name = user != null && user.username != null && !user.username.isBlank() ? user.username : it.uuid;
                                widget.name = name;
                                widget.hiddenText = it.uuid;
                                widget.description = "Click to manage permissions";

                                CompletableFuture.runAsync(() -> {
                                    Account tempAccount = new Account(name, finalUid.toString(), null, 0);
                                    BufferedImage face = tempAccount.getFace();
                                    if (face != null) {
                                        widget.icon = face;
                                    }
                                });

                            }));
                        } else {
                            widget.name = it.uuid;
                            widget.hiddenText = it.uuid;
                            widget.description = "Click to manage permissions";
                        }
                        contentArea.addWidget(widget);
                    }
                    contentArea.updateWidgetPositions();
                    loading = false;
                    restoreScroll(contentArea, prevScroll);
                    initialUsersRendered = true;
                });
            });
        });
    }

    private void renderTrackList(List<String> tracks) {
        for (String track : tracks) {
            MountableButtonWidget widget = new MountableButtonWidget.Builder(track).description("Track").onClick(() -> openTrackEditor(track)).build();
            setEntranceAnimation(widget, !initialTracksRendered);
            widget.mountedWidgets.add(new SquareButtonWidget.Builder().imagePath("edit.png").onClick(() -> openTrackEditor(track)).build());
            widget.mountedWidgets.add(new SquareButtonWidget.Builder().imagePath("delete.png").accentType(ThemeManager.getAccent("danger")).onClick(() -> confirmDeleteTrack(track)).build());
            contentArea.addWidget(widget);
        }
        contentArea.updateWidgetPositions();
        initialTracksRendered = true;
    }

    private void openTrackEditor(String trackName) {
        int seq = ++refreshSeq;
        loading = true;
        CompletableFuture<Track> tF = service.getClient().getTrack(trackName);
        CompletableFuture<List<String>> groupsF = service.getAllGroups();
        CompletableFuture.allOf(tF, groupsF).thenAccept(v -> applyIfFresh(seq, () -> {
            loading = false;
            Track track = tF.join();
            List<String> allGroups = new ArrayList<>(groupsF.join());
            if (track == null) {
                new Notification("Error", "Track not found", Notification.Type.ERROR);
                return;
            }
            PopupWidget.Builder b = new PopupWidget.Builder("Edit Track: " + track.name).size(420, 340).setResizable(true);
            List<String> order = new ArrayList<>(track.groups != null ? track.groups : new ArrayList<>());
            Container list = new Container(0, 0, 400, 200);
            list.layout(new ManagedLayout()).columns(1).padding(2).scrolling(true);
            AtomicReference<Runnable> rebuild = new AtomicReference<>();
            rebuild.set(() -> {
                list.clearWidgets();
                for (int i = 0; i < order.size(); i++) {
                    String g = order.get(i);
                    MountableButtonWidget row = new MountableButtonWidget.Builder(g).description("Position " + (i + 1)).build();
                    int idx = i;
                    row.mountedWidgets.add(new SquareButtonWidget.Builder().imagePath("arrow_up.png").onClick(() -> {
                        if (idx > 0) {
                            Collections.swap(order, idx, idx - 1);
                            Runnable r = rebuild.get();
                            if (r != null) r.run();
                        }
                    }).build());
                    row.mountedWidgets.add(new SquareButtonWidget.Builder().imagePath("arrow_down.png").onClick(() -> {
                        if (idx < order.size() - 1) {
                            Collections.swap(order, idx, idx + 1);
                            Runnable r = rebuild.get();
                            if (r != null) r.run();
                        }
                    }).build());
                    row.mountedWidgets.add(new SquareButtonWidget.Builder().imagePath("delete.png").accentType(ThemeManager.getAccent("danger")).onClick(() -> {
                        order.remove(idx);
                        Runnable r = rebuild.get();
                        if (r != null) r.run();
                    }).build());
                    list.addWidget(row);
                }
                list.updateWidgetPositions();
            });
            rebuild.get().run();
            b.addRow("Groups", true, 220, list);
            List<String> selectable = allGroups.stream().filter(g -> !order.contains(g)).sorted().toList();
            DropDownWidget<String> addDd = new DropDownWidget.Builder<>(selectable).size(280, 20).build();
            b.addRow("Add Group", true, 24, addDd);
            b.addTitleButton(() -> {
                String sel = addDd.getSelectedItem();
                if (sel != null && !sel.isEmpty() && !order.contains(sel)) {
                    order.add(sel);
                    List<String> newOptions = allGroups.stream().filter(g -> !order.contains(g)).sorted().toList();
                    String keep = addDd.getSelectedItem();
                    addDd.setItems(newOptions, newOptions.contains(keep) ? keep : (newOptions.isEmpty() ? null : newOptions.get(0)));
                    Runnable r = rebuild.get();
                    if (r != null) r.run();
                }
            }, "Add", ThemeManager.getAccent("calm"));
            b.addTitleButton(() -> {
                Track t = new Track();
                t.name = track.name;
                t.groups = new ArrayList<>(order);
                service.getClient().updateTrack(t).thenRun(() -> new Notification("Saved", "Track updated.", Notification.Type.SUCCESS)).exceptionally(e -> {
                    new Notification("Error", e.getMessage(), Notification.Type.ERROR);
                    return null;
                });
            }, "Save", ThemeManager.getAccent("nice"));
            addDrawableChild(b.build()).show();
        }));
    }

    private void openUserEditor(String uuidStr) {
        int seq = ++refreshSeq;
        loading = true;
        service.getClient().getUser(UUID.fromString(uuidStr)).thenAccept(user -> applyIfFresh(seq, () -> {
            loading = false;
            if (user != null) openEditor(user);
            else new Notification("Error", "User not found", Notification.Type.ERROR);
        }));
    }

    private void openEditor(PermissionHolder holder) {
        if (holder instanceof Group g0) {
            activeDetailType = DetailType.GROUP;
            activeDetailId = g0.name;
        } else if (holder instanceof User u0) {
            activeDetailType = DetailType.USER;
            activeDetailId = u0.uniqueId;
        } else {
            activeDetailType = DetailType.NONE;
            activeDetailId = null;
        }
        contentArea.clearWidgets();
        String title = holder instanceof Group ? "Group: " + holder.name : "User: " + holder.username;
        contentArea.addWidget(new AnimatedButton.Builder().label(title).active(false).accentType(ThemeManager.getAccent("nice")).build());

        if (holder instanceof Group g) {
            PopupWidget.Builder general = new PopupWidget.Builder("General Settings").enableCollapseOnClose(true).setExpandWithDropdowns(true);
            general.addRow("", true, 30, new MountableButtonWidget.Builder("Display Name").description(g.displayName != null ? g.displayName : "unset").addButton(new SquareButtonWidget.Builder().imagePath("edit.png").onClick(() -> showRenamePopup(g)).build()).build());
            general.addRow("", true, 30, new MountableButtonWidget.Builder("Weight").description(g.weight != null ? String.valueOf(g.weight) : "unset").addButton(new SquareButtonWidget.Builder().imagePath("edit.png").onClick(() -> showWeightPopup(g)).build()).build());
            PopupWidget generalWidget = general.build();
            generalWidget.setLayer(0);
            contentArea.addWidget(generalWidget);
        }

        PopupWidget.Builder parentsBuilder = new PopupWidget.Builder("Parents (Inheritance)").enableCollapseOnClose(true).setExpandWithDropdowns(true);
        List<Node> parents = holder.nodes != null ? holder.nodes.stream().filter(n -> n.key.startsWith("group.")).toList() : new ArrayList<>();
        for (Node n : parents) {
            parentsBuilder.addRow("", true, 30, createNodeWidget(n, holder, "inheritance"));
        }
        parentsBuilder.addRow("", true, 30, new IconButton.Builder().imagePath("create.png").label("Add Parent Group").onClick(() -> showAddParentPopup(holder)).build());
        PopupWidget parentsWidget = parentsBuilder.build();
        parentsWidget.setLayer(0);
        contentArea.addWidget(parentsWidget);

        if (holder instanceof Group gg) {
            PopupWidget.Builder inhByBuilder = new PopupWidget.Builder("Inherited By Groups").enableCollapseOnClose(true).setExpandWithDropdowns(true);
            Container inhBy = new Container(0, 0, contentArea.getWidth() - 40, 100);
            inhBy.layout(new ManagedLayout()).columns(1).padding(2).scrolling(true);
            inhByBuilder.addRow("", true, 110, inhBy);
            service.getClient().searchGroupsByParent(gg.name).thenAccept(results -> ScreenManager.getInstance().execute(() -> {
                inhBy.clearWidgets();
                for (redxax.oxy.remotely.data.integrations.luckperms.LuckPermsDTOs.GroupSearchResult r : results) {
                    MountableButtonWidget w = new MountableButtonWidget.Builder(r.name).description("inherits " + gg.name).onClick(() -> service.getGroup(r.name).thenAccept(gd -> ScreenManager.getInstance().execute(() -> openEditor(gd)))).build();
                    w.mountedWidgets.add(new SquareButtonWidget.Builder().imagePath("external.png").onClick(() -> service.getGroup(r.name).thenAccept(gd -> ScreenManager.getInstance().execute(() -> openEditor(gd)))).build());
                    inhBy.addWidget(w);
                }
                inhBy.updateWidgetPositions();
                contentArea.updateWidgetPositions();
            }));
            PopupWidget inhByWidget = inhByBuilder.build();
            inhByWidget.setLayer(0);
            contentArea.addWidget(inhByWidget);

            PopupWidget.Builder membersBuilder = new PopupWidget.Builder("Members (Users)").enableCollapseOnClose(true).setExpandWithDropdowns(true);
            Container members = new Container(0, 0, contentArea.getWidth() - 40, 140);
            members.layout(new ManagedLayout()).columns(1).padding(2).scrolling(true);
            membersBuilder.addRow("", true, 150, members);
            service.getClient().searchUsersByGroup(gg.name).thenAccept(results -> ScreenManager.getInstance().execute(() -> {
                members.clearWidgets();
                for (redxax.oxy.remotely.data.integrations.luckperms.LuckPermsDTOs.UserSearchResult r : results) {
                    String uid = r.uniqueId;
                    MountableButtonWidget w = new MountableButtonWidget.Builder(uid).description("Click to open user").onClick(() -> openUserEditor(uid)).build();
                    w.mountedWidgets.add(new SquareButtonWidget.Builder().imagePath("external.png").onClick(() -> openUserEditor(uid)).build());
                    try {
                        UUID u = UUID.fromString(uid);
                        service.getClient().getUser(u).thenAccept(user -> ScreenManager.getInstance().execute(() -> {
                            if (user != null && user.username != null && !user.username.isBlank()) {
                                w.name = user.username;
                                w.hiddenText = uid;
                            } else {
                                w.name = uid;
                                w.hiddenText = uid;
                            }
                        }));
                    } catch (Exception ex) {
                        w.name = uid;
                        w.hiddenText = uid;
                    }
                    members.addWidget(w);
                }
                members.updateWidgetPositions();
                contentArea.updateWidgetPositions();
            }));
            PopupWidget membersWidget = membersBuilder.build();
            membersWidget.setLayer(0);
            contentArea.addWidget(membersWidget);
        }

        PopupWidget.Builder metaBuilder = new PopupWidget.Builder("Metadata").enableCollapseOnClose(true).setExpandWithDropdowns(true);
        List<Node> metaNodes = holder.nodes != null ? holder.nodes.stream().filter(n -> (n.key.startsWith("prefix.") || n.key.startsWith("suffix.") || n.key.startsWith("meta."))).collect(Collectors.toList()) : new ArrayList<>();
        for (Node n : metaNodes) {
            metaBuilder.addRow("", true, 30, createNodeWidget(n, holder, "meta"));
        }
        metaBuilder.addRow("", true, 30, new IconButton.Builder().imagePath("create.png").label("Add Metadata").onClick(() -> showAddMetaPopup(holder)).build());
        PopupWidget metaWidget = metaBuilder.build();
        metaWidget.setLayer(0);
        contentArea.addWidget(metaWidget);

        PopupWidget.Builder permsBuilder = new PopupWidget.Builder("Permissions").enableCollapseOnClose(true).setExpandWithDropdowns(true);
        List<Node> perms = holder.nodes != null ? holder.nodes.stream().filter(n -> !n.key.startsWith("group.") && !n.key.startsWith("prefix.") && !n.key.startsWith("suffix.") && !n.key.startsWith("meta.") && !n.key.startsWith("displayname.") && !n.key.startsWith("weight.")).collect(Collectors.toList()) : new ArrayList<>();
        for (Node n : perms) {
            permsBuilder.addRow("", true, 30, createNodeWidget(n, holder, "permission"));
        }
        permsBuilder.addRow("", true, 30, new IconButton.Builder().imagePath("create.png").label("Add Permission").onClick(() -> showAddPermPopup(holder)).build());
        PopupWidget permsWidget = permsBuilder.build();
        permsWidget.setLayer(0);
        contentArea.addWidget(permsWidget);

        contentArea.updateWidgetPositions();
    }

    private MountableButtonWidget createNodeWidget(Node node, PermissionHolder holder, String type) {
        String label = node.key;
        String desc = "";
        if (node.key.startsWith("group.")) label = node.key.substring(6);
        if (node.key.startsWith("prefix.")) {
            String[] parts = node.key.split("\\.", 3);
            if (parts.length == 3) {
                label = "Prefix: " + parts[2].replace("&", "§");
                desc = "Priority: " + parts[1];
            }
        }
        if (node.key.startsWith("suffix.")) {
            String[] parts = node.key.split("\\.", 3);
            if (parts.length == 3) {
                label = "Suffix: " + parts[2].replace("&", "§");
                desc = "Priority: " + parts[1];
            }
        }
        if (!desc.isEmpty()) desc += " | ";
        desc += "Value: " + node.value;
        if (node.expiry != null) desc += " | Expires: " + node.expiry;
        MountableButtonWidget w = new MountableButtonWidget.Builder(label).description(desc).build();
        if (node.key.startsWith("group.")) {
            String groupName = node.key.substring(6);
            w.mountedWidgets.add(new SquareButtonWidget.Builder().imagePath("external.png").hint("Open Group").onClick(() -> service.getGroup(groupName).thenAccept(gd -> ScreenManager.getInstance().execute(() -> openEditor(gd)))).build());
        }
        w.mountedWidgets.add(new SquareButtonWidget.Builder().imagePath("delete.png").accentType(ThemeManager.getAccent("danger")).onClick(() -> {
            holder.nodes.remove(node);
            saveHolder(holder);
        }).build());
        w.mountedWidgets.add(new SquareButtonWidget.Builder().imagePath("edit.png").onClick(() -> {
            if (node.key.startsWith("prefix.") || node.key.startsWith("suffix.")) showEditMetaPopup(node, holder);
            else showEditNodePopup(node, holder);
        }).build());
        return w;
    }

    private void showRenamePopup(Group group) {
        PopupWidget.Builder b = new PopupWidget.Builder("Rename Display Name").size(320, 140);
        TextInputWidget input = new TextInputWidget.Builder().text(group.displayName != null ? group.displayName.replace("§", "&") : "").build();
        b.addRow("Display Name", true, 30, input);
        b.addTitleButton(() -> {
            String v = input.getText();
            group.displayName = v != null && !v.isBlank() ? v.replace("&", "§") : null;
            saveHolder(group);
            b.getWidget().setVisible(false);
        }, "Save", ThemeManager.getAccent("nice"));
        addDrawableChild(b.build()).show();
    }

    private void showWeightPopup(Group group) {
        PopupWidget.Builder b = new PopupWidget.Builder("Set Group Weight").size(300, 120);
        String initial = group.weight != null ? String.valueOf(group.weight) : "";
        TextInputWidget input = new TextInputWidget.Builder().text(initial).build();
        b.addRow("Weight", true, 30, input);
        b.addTitleButton(() -> {
            try {
                String t = input.getText();
                if (t == null || t.isBlank()) {
                    group.weight = null;
                } else {
                    int w = Integer.parseInt(t);
                    group.weight = w > 0 ? w : null;
                }
                saveHolder(group);
                b.getWidget().setVisible(false);
            } catch (Exception e) {
                new Notification("Error", "Invalid Number", Notification.Type.ERROR);
            }
        }, "Save", ThemeManager.getAccent("nice"));
        addDrawableChild(b.build()).show();
    }

    private void showAddPermPopup(PermissionHolder holder) {
        PopupWidget.Builder b = new PopupWidget.Builder("Add Permission").size(350, 200);
        TextInputWidget key = new TextInputWidget.Builder().placeholder("e.g. minecraft.command.op").build();
        ToggleWidget val = new ToggleWidget.Builder().toggled(true).build();
        TextInputWidget expiry = new TextInputWidget.Builder().placeholder("Duration (e.g. 30d) or epoch seconds or empty").build();
        b.addRow("Permission Key", true, 30, key);
        b.addRow("Value (True/False)", false, 20, val);
        b.addRow("Duration", true, 30, expiry);
        b.addTitleButton(() -> {
            if (key.getText().isEmpty()) return;
            Node n = new Node();
            n.key = key.getText();
            n.value = val.getValue();
            n.type = "permission";
            n.expiry = parseDurationToEpochSeconds(expiry.getText());
            if (holder.nodes == null) holder.nodes = new ArrayList<>();
            holder.nodes.add(n);
            saveHolder(holder);
            b.getWidget().setVisible(false);
        }, "Add", ThemeManager.getAccent("nice"));
        addDrawableChild(b.build()).show();
    }

    private void showAddMetaPopup(PermissionHolder holder) {
        PopupWidget.Builder b = new PopupWidget.Builder("Add Metadata").size(380, 260);
        DropDownWidget<String> type = new DropDownWidget.Builder<>(List.of("Prefix", "Suffix")).size(140, 20).build();
        TextInputWidget priority = new TextInputWidget.Builder().text("100").placeholder("Priority").build();
        TextInputWidget value = new TextInputWidget.Builder().placeholder("Value (use & for colors)").build();
        b.addRow("Type", true, 24, type);
        b.addRow("Priority", true, 30, priority);
        b.addRow("Value", true, 30, value);
        b.addTitleButton(() -> {
            String t = type.getSelectedItem() != null ? type.getSelectedItem().toLowerCase() : "prefix";
            String p = priority.getText();
            String v = value.getText();
            if (p.isEmpty() || v.isEmpty()) return;
            Node n = new Node();
            n.key = t + "." + p + "." + v.replace("&", "§");
            n.value = true;
            n.type = t;
            if (holder.nodes == null) holder.nodes = new ArrayList<>();
            holder.nodes.add(n);
            saveHolder(holder);
            b.getWidget().setVisible(false);
        }, "Add", ThemeManager.getAccent("nice"));
        addDrawableChild(b.build()).show();
    }

    private void showEditMetaPopup(Node node, PermissionHolder holder) {
        String[] parts = node.key.split("\\.", 3);
        if (parts.length < 3) return;
        PopupWidget.Builder b = new PopupWidget.Builder("Edit " + parts[0]).size(350, 200);
        TextInputWidget priority = new TextInputWidget.Builder().text(parts[1]).build();
        TextInputWidget value = new TextInputWidget.Builder().text(parts[2].replace("§", "&")).build();
        b.addRow("Priority", true, 30, priority);
        b.addRow("Value", true, 30, value);
        b.addTitleButton(() -> {
            node.key = parts[0] + "." + priority.getText() + "." + value.getText().replace("&", "§");
            saveHolder(holder);
            b.getWidget().setVisible(false);
        }, "Save", ThemeManager.getAccent("nice"));
        addDrawableChild(b.build()).show();
    }

    private void showEditNodePopup(Node node, PermissionHolder holder) {
        PopupWidget.Builder b = new PopupWidget.Builder("Edit Node").size(350, 180);
        ToggleWidget val = new ToggleWidget.Builder().toggled(node.value).build();
        TextInputWidget expiry = new TextInputWidget.Builder().text(node.expiry != null ? String.valueOf(node.expiry) : "").placeholder("Epoch Seconds").build();
        b.addRow("Node Key", false, 20, new AnimatedButton.Builder().label(node.key).active(false).build());
        b.addRow("Value", false, 20, val);
        b.addRow("Expiry", true, 20, expiry);
        b.addTitleButton(() -> {
            node.value = val.getValue();
            if (!expiry.getText().isEmpty()) {
                try {
                    node.expiry = Long.parseLong(expiry.getText());
                } catch (Exception ignored) {
                }
            } else {
                node.expiry = null;
            }
            saveHolder(holder);
            b.getWidget().setVisible(false);
        }, "Save", ThemeManager.getAccent("nice"));
        addDrawableChild(b.build()).show();
    }

    private void showAddParentPopup(PermissionHolder holder) {
        PopupWidget.Builder b = new PopupWidget.Builder("Add Parent Group").size(360, 150);
        CompletableFuture<List<String>> groupsF = service.getAllGroups();
        groupsF.thenAccept(groups -> ScreenManager.getInstance().execute(() -> {
            List<String> options = new ArrayList<>(groups);
            if (holder instanceof Group g) options.removeIf(s -> s.equalsIgnoreCase(g.name));
            Set<String> existing = getExistingParentGroups(holder);
            options.removeIf(existing::contains);
            options.sort(String::compareToIgnoreCase);
            DropDownWidget<String> dd = new DropDownWidget.Builder<String>(options).size(240, 20).build();
            b.addRow("Group", true, 20, dd);
            b.addTitleButton(() -> {
                String gsel = dd.getSelectedItem();
                if (gsel == null || gsel.isEmpty()) return;
                Node n = new Node();
                n.key = "group." + gsel;
                n.value = true;
                n.type = "inheritance";
                if (holder.nodes == null) holder.nodes = new ArrayList<>();
                holder.nodes.add(n);
                saveHolder(holder);
                b.getWidget().setVisible(false);
            }, "Add", ThemeManager.getAccent("nice"));
            addDrawableChild(b.build()).show();
        }));
    }

    private Set<String> getExistingParentGroups(PermissionHolder holder) {
        Set<String> s = new HashSet<>();
        if (holder.nodes != null) {
            for (Node n : holder.nodes) {
                if (n != null && n.key != null && n.key.startsWith("group.") && Boolean.TRUE.equals(n.value)) {
                    s.add(n.key.substring(6));
                }
            }
        }
        return s;
    }

    private void showCreatePopup() {
        PopupWidget.Builder b = new PopupWidget.Builder("Create New").size(420, 320);
        DropDownWidget<String> kind = new DropDownWidget.Builder<String>(List.of("Group", "Track", "User")).size(220, 20).build();
        TextInputWidget idInput = new TextInputWidget.Builder().placeholder("Name / UUID").build();
        TextInputWidget displayNameInput = new TextInputWidget.Builder().placeholder("Group Display Name (optional, use & for colors)").build();
        TextInputWidget weightInput = new TextInputWidget.Builder().placeholder("Weight (optional)").build();
        TextInputWidget prefixPrio = new TextInputWidget.Builder().placeholder("Prefix Priority").build();
        TextInputWidget prefixValue = new TextInputWidget.Builder().placeholder("Prefix Value (use & for colors)").build();
        TextInputWidget suffixPrio = new TextInputWidget.Builder().placeholder("Suffix Priority").build();
        TextInputWidget suffixValue = new TextInputWidget.Builder().placeholder("Suffix Value (use & for colors)").build();

        b.addRow("Type", true, 20, kind);

        PopupWidget widget = b.build();

        kind.setOnSelectionChanged(selected -> {
            boolean isGroup = "Group".equalsIgnoreCase(selected);

            widget.setRowVisibility("identifier", true);
            widget.setRowVisibility("displayname", isGroup);
            widget.setRowVisibility("weight", isGroup);
            widget.setRowVisibility("prefix", isGroup);
            widget.setRowVisibility("suffix", isGroup);
        });

        b.addRow("identifier", "Identifier", true, 20, idInput);
        b.addRow("displayname", "Display Name", true, 20, displayNameInput);
        b.addRow("weight", "Weight", true, 20, weightInput);
        b.addRow("prefix", "Prefix", true, 20, prefixPrio, prefixValue);
        b.addRow("suffix", "Suffix", true, 20, suffixPrio, suffixValue);

        b.addTitleButton(() -> {
            String type = kind.getSelectedItem();
            String id = idInput.getText().trim();
            if (type == null || id.isEmpty()) return;
            loading = true;
            if ("Group".equalsIgnoreCase(type)) {
                service.getClient().createGroup(id).thenCompose(v -> {
                    List<Node> nodes = new ArrayList<>();
                    String dn = displayNameInput.getText() != null ? displayNameInput.getText().trim() : "";
                    if (!dn.isEmpty()) {
                        Node n = new Node();
                        n.key = "displayname." + dn.replace("&", "§");
                        n.value = true;
                        n.type = "display_name";
                        nodes.add(n);
                    }
                    String w = weightInput.getText() != null ? weightInput.getText().trim() : "";
                    if (!w.isEmpty()) {
                        try {
                            int wi = Integer.parseInt(w);
                            if (wi > 0) {
                                Node n = new Node();
                                n.key = "weight." + wi;
                                n.value = true;
                                n.type = "weight";
                                nodes.add(n);
                            }
                        } catch (Exception ignored) {
                        }
                    }
                    String pp = prefixPrio.getText() != null ? prefixPrio.getText().trim() : "";
                    String pv = prefixValue.getText() != null ? prefixValue.getText().trim() : "";
                    if (!pp.isEmpty() && !pv.isEmpty()) {
                        Node n = new Node();
                        n.key = "prefix." + pp + "." + pv.replace("&", "§");
                        n.value = true;
                        n.type = "prefix";
                        nodes.add(n);
                    }
                    String sp = suffixPrio.getText() != null ? suffixPrio.getText().trim() : "";
                    String sv = suffixValue.getText() != null ? suffixValue.getText().trim() : "";
                    if (!sp.isEmpty() && !sv.isEmpty()) {
                        Node n = new Node();
                        n.key = "suffix." + sp + "." + sv.replace("&", "§");
                        n.value = true;
                        n.type = "suffix";
                        nodes.add(n);
                    }
                    if (nodes.isEmpty()) return CompletableFuture.completedFuture(null);
                    return service.getClient().updateGroupNodes(id, nodes);
                }).thenRun(() -> {
                    loading = false;
                    new Notification("Saved", "Group created.", Notification.Type.SUCCESS);
                    refreshData();
                }).exceptionally(e -> {
                    loading = false;
                    new Notification("Error", e.getMessage(), Notification.Type.ERROR);
                    return null;
                });
            } else if ("Track".equalsIgnoreCase(type)) {
                service.getClient().createTrack(id).thenRun(() -> {
                    loading = false;
                    refreshData();
                }).exceptionally(e -> {
                    loading = false;
                    new Notification("Error", e.getMessage(), Notification.Type.ERROR);
                    return null;
                });
            } else {
                try {
                    UUID u = UUID.fromString(id);
                    service.getClient().createUser(u, "unknown").thenRun(() -> {
                        loading = false;
                        refreshData();
                    }).exceptionally(e -> {
                        loading = false;
                        new Notification("Error", e.getMessage(), Notification.Type.ERROR);
                        return null;
                    });
                } catch (Exception e) {
                    loading = false;
                    new Notification("Error", "Invalid UUID", Notification.Type.ERROR);
                }
            }
        }, "Create", ThemeManager.getAccent("nice"));

        kind.setSelectedItem("Group");

        addDrawableChild(widget).show();
    }

    private void confirmDeleteGroup(Group g) {
        PopupWidget.Builder b = new PopupWidget.Builder("Confirm Deletion").size(300, 120);
        b.addRow("Are you sure you want to delete group '" + g.name + "'?", true, 30);
        b.addTitleButton(() -> {
            service.getClient().deleteGroup(g.name).thenRun(() -> {
                b.getWidget().setVisible(false);
                refreshData();
            });
        }, "Delete", ThemeManager.getAccent("danger"));
        addDrawableChild(b.build()).show();
    }

    private void confirmDeleteTrack(String name) {
        PopupWidget.Builder b = new PopupWidget.Builder("Confirm Deletion").size(300, 120);
        b.addRow("Are you sure you want to delete track '" + name + "'?", true, 30);
        b.addTitleButton(() -> {
            service.getClient().deleteTrack(name).thenRun(() -> {
                b.getWidget().setVisible(false);
                refreshData();
            });
        }, "Delete", ThemeManager.getAccent("danger"));
        addDrawableChild(b.build()).show();
    }

    private void saveHolder(PermissionHolder holder) {
        loading = true;
        CompletableFuture<Void> future;
        if (holder instanceof Group g) {
            applyGroupGeneralSettingsToNodes(g);
            future = service.getClient().updateGroupNodes(g.name, g.nodes);
        } else {
            User u = (User) holder;
            future = service.getClient().updateUserNodes(UUID.fromString(u.uniqueId), u.nodes);
        }
        future.thenRun(() -> {
            openEditor(holder);
            loading = false;
            new Notification("Saved", "Changes applied.", Notification.Type.SUCCESS);
        }).exceptionally(e -> {
            loading = false;
            new Notification("Error", e.getMessage(), Notification.Type.ERROR);
            return null;
        });
    }

    private void applyGroupGeneralSettingsToNodes(Group g) {
        List<Node> nodes = g.nodes != null ? new ArrayList<>(g.nodes) : new ArrayList<>();
        nodes.removeIf(n -> n != null && n.key != null && (n.key.startsWith("displayname.") || n.key.startsWith("display_name.") || n.key.startsWith("weight.")));
        if (g.displayName != null && !g.displayName.isBlank()) {
            Node dn = new Node();
            dn.key = "displayname." + g.displayName.replace("&", "§");
            dn.value = true;
            dn.type = "display_name";
            nodes.add(dn);
        }
        if (g.weight != null && g.weight > 0) {
            Node wn = new Node();
            wn.key = "weight." + g.weight;
            wn.value = true;
            wn.type = "weight";
            nodes.add(wn);
        }
        g.nodes = nodes;
    }

    private Long parseDurationToEpochSeconds(String text) {
        if (text == null) return null;
        String t = text.trim();
        if (t.isEmpty()) return null;
        long now = System.currentTimeMillis() / 1000L;
        long seconds = 0L;
        Matcher m = Pattern.compile("(\\d+)([smhdw])", Pattern.CASE_INSENSITIVE).matcher(t);
        while (m.find()) {
            long v = Long.parseLong(m.group(1));
            char u = Character.toLowerCase(m.group(2).charAt(0));
            if (u == 's') seconds += v;
            else if (u == 'm') seconds += v * 60L;
            else if (u == 'h') seconds += v * 3600L;
            else if (u == 'd') seconds += v * 86400L;
            else if (u == 'w') seconds += v * 604800L;
        }
        if (seconds > 0L) return now + seconds;
        try {
            return Long.parseLong(t);
        } catch (Exception e) {
            return null;
        }
    }

    private void setEntranceAnimation(AnimatedWidget w, boolean enabled) {
        try {
            Field f = AnimatedWidget.class.getDeclaredField("entranceAnimationEnabled");
            f.setAccessible(true);
            f.setBoolean(w, enabled);
        } catch (Exception ignored) {
        }
    }

    private double getScrollOffset(Container c) {
        try {
            Method m = c.getClass().getMethod("getScrollOffset");
            Object v = m.invoke(c);
            if (v instanceof Number) return ((Number) v).doubleValue();
        } catch (Exception ignored) {
        }
        try {
            Method m = c.getClass().getMethod("getScrollY");
            Object v = m.invoke(c);
            if (v instanceof Number) return ((Number) v).doubleValue();
        } catch (Exception ignored) {
        }
        try {
            Field f = c.getClass().getDeclaredField("scrollOffset");
            f.setAccessible(true);
            Object v = f.get(c);
            if (v instanceof Number) return ((Number) v).doubleValue();
        } catch (Exception ignored) {
        }
        return 0;
    }

    private void setScrollOffset(Container c, double value) {
        try {
            Method m = c.getClass().getMethod("setScrollOffset", double.class);
            m.invoke(c, value);
            return;
        } catch (Exception ignored) {
        }
        try {
            Method m = c.getClass().getMethod("setScrollY", double.class);
            m.invoke(c, value);
            return;
        } catch (Exception ignored) {
        }
        try {
            Field f = c.getClass().getDeclaredField("scrollOffset");
            f.setAccessible(true);
            f.set(c, value);
        } catch (Exception ignored) {
        }
    }

    private void restoreScroll(Container c, double value) {
        setScrollOffset(c, value);
        ScreenManager.getInstance().execute(() -> setScrollOffset(c, value));
    }

    @Override
    public void close() {
        ScreenManager.getInstance().setScreen(parent);
    }

    private record UserItem(String uuid, int weight) {}
}
