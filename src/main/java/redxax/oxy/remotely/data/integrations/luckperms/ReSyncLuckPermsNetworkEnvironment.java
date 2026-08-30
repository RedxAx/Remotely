package redxax.oxy.remotely.data.integrations.luckperms;

import redxax.oxy.remotely.data.flow.ReSyncFlowClient.ConnectionState;

import java.util.List;
import java.util.Map;
import java.util.Set;

public interface ReSyncLuckPermsNetworkEnvironment {
    record Member(String instanceId, String name, boolean managed, boolean reSyncEnabled, boolean proxy) {
        public Member {
            instanceId = normalize(instanceId);
            name = normalize(name);
        }
    }

    record Network(String networkId, String name, List<Member> members) {
        public Network {
            networkId = normalize(networkId);
            name = normalize(name);
            members = members == null ? List.of() : List.copyOf(members);
        }
    }

    default Network network(String sourceInstanceId) {
        return null;
    }

    default ReSyncLuckPermsClient client(String instanceId) {
        return null;
    }

    default ConnectionState connection(String instanceId) {
        return ConnectionState.DISCONNECTED;
    }

    default Map<String, Map<String, Set<ReSyncLuckPermsNetworkClient.Delivery>>> loadSelections() {
        return Map.of();
    }

    default void saveSelections(Map<String, Map<String, Set<ReSyncLuckPermsNetworkClient.Delivery>>> selections) {
    }

    static ReSyncLuckPermsNetworkEnvironment unavailable() {
        return new ReSyncLuckPermsNetworkEnvironment() {
        };
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim();
    }
}
