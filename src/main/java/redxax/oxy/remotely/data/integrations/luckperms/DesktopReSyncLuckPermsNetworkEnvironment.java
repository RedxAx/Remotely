package redxax.oxy.remotely.data.integrations.luckperms;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.reflect.TypeToken;
import redxax.oxy.remotely.RemotelyClient;
import redxax.oxy.remotely.DesktopRemotelyPaths;
import redxax.oxy.remotely.data.flow.FlowManager;
import redxax.oxy.remotely.data.flow.ReSyncFlowClient;
import redxax.oxy.remotely.data.flow.ReSyncFlowClient.ConnectionState;
import redxax.oxy.remotely.network.NetworkDefinition;
import redxax.oxy.remotely.network.DesktopNetworkAccess;
import redxax.oxy.remotely.network.DesktopNetworkManager;
import redxax.oxy.remotely.network.NetworkMember;
import restudio.rebase.Rebase;
import restudio.rebase.instance.Instance;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

public final class DesktopReSyncLuckPermsNetworkEnvironment implements ReSyncLuckPermsNetworkEnvironment {
    private static final Type SELECTIONS_TYPE = new TypeToken<Map<String, Map<String, Set<ReSyncLuckPermsNetworkClient.Delivery>>>>() {
    }.getType();
    private final FlowManager flowManager;
    private final Gson gson = new Gson();
    private final Path selectionsPath = DesktopRemotelyPaths.appDir().resolve("data").resolve("permissions").resolve("network-targets.json");

    public DesktopReSyncLuckPermsNetworkEnvironment(FlowManager flowManager) {
        this.flowManager = flowManager;
    }

    @Override
    public Network network(String sourceInstanceId) {
        RemotelyClient remotely = RemotelyClient.INSTANCE;
        DesktopNetworkManager manager = DesktopNetworkAccess.manager(remotely);
        NetworkDefinition network = manager == null ? null : manager.getNetworkForInstance(sourceInstanceId).orElse(null);
        if (network == null) return null;
        return new Network(network.networkId(), network.name(), network.members().stream().map(this::member).toList());
    }

    @Override
    public ReSyncLuckPermsClient client(String instanceId) {
        if (flowManager == null) {
            return null;
        }
        ReSyncFlowClient client = flowManager.ensureFlowClient(instanceId);
        return client == null ? null : client.luckPerms();
    }

    @Override
    public ConnectionState connection(String instanceId) {
        return flowManager == null ? ConnectionState.DISCONNECTED : flowManager.getFlowClientConnectionState(instanceId);
    }

    @Override
    public Map<String, Map<String, Set<ReSyncLuckPermsNetworkClient.Delivery>>> loadSelections() {
        if (!Files.isRegularFile(selectionsPath)) return Map.of();
        try (Reader reader = Files.newBufferedReader(selectionsPath)) {
            JsonElement root = JsonParser.parseReader(reader);
            if (!root.isJsonObject()) return Map.of();
            Map<String, Map<String, Set<ReSyncLuckPermsNetworkClient.Delivery>>> selections = new LinkedHashMap<>();
            for (Map.Entry<String, JsonElement> network : root.getAsJsonObject().entrySet()) {
                selections.put(network.getKey(), readSelection(network.getValue()));
            }
            return Map.copyOf(selections);
        } catch (IOException | RuntimeException ignored) {
            return Map.of();
        }
    }

    @Override
    public void saveSelections(Map<String, Map<String, Set<ReSyncLuckPermsNetworkClient.Delivery>>> selections) {
        try {
            Files.createDirectories(selectionsPath.getParent());
            Path temporary = selectionsPath.resolveSibling(selectionsPath.getFileName() + ".tmp");
            try (Writer writer = Files.newBufferedWriter(temporary)) {
                gson.toJson(selections, SELECTIONS_TYPE, writer);
            }
            try {
                Files.move(temporary, selectionsPath, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (IOException exception) {
                Files.move(temporary, selectionsPath, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException exception) {
            throw new IllegalStateException("Network Selection Could Not Be Saved", exception);
        }
    }

    private Member member(NetworkMember member) {
        return new Member(member.instanceId(), name(member.instanceId()), member.isManaged(), member.resyncEnabled(), member.isProxy());
    }

    private String name(String instanceId) {
        try {
            return Rebase.get().getInstanceManager().getAllInstances().stream().filter(instance -> instance != null)
                .filter(instance -> instanceId.equals(instance.getInstanceId())).map(Instance::getName).findFirst().orElse(instanceId);
        } catch (IllegalStateException exception) {
            return instanceId;
        }
    }

    private Map<String, Set<ReSyncLuckPermsNetworkClient.Delivery>> readSelection(JsonElement value) {
        Map<String, Set<ReSyncLuckPermsNetworkClient.Delivery>> migrated = new LinkedHashMap<>();
        if (value != null && value.isJsonArray()) {
            value.getAsJsonArray().forEach(instanceId -> migrated.put(instanceId.getAsString(), ReSyncLuckPermsNetworkClient.Delivery.all()));
            return Map.copyOf(migrated);
        }
        if (value == null || !value.isJsonObject()) return Map.of();
        JsonObject object = value.getAsJsonObject();
        object.entrySet().forEach(entry -> {
            Set<ReSyncLuckPermsNetworkClient.Delivery> deliveries = gson.fromJson(entry.getValue(),
                new TypeToken<Set<ReSyncLuckPermsNetworkClient.Delivery>>() {
                }.getType());
            if (deliveries != null && !deliveries.isEmpty()) migrated.put(entry.getKey(), Set.copyOf(deliveries));
        });
        return Map.copyOf(migrated);
    }
}
