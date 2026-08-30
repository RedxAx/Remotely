package redxax.oxy.remotely.config;

import java.util.List;

public record RemotelyGroup(String id, String name, List<String> members) {
    public RemotelyGroup {
        id = id == null ? "" : id;
        name = name == null || name.isBlank() ? id : name;
        members = members == null ? List.of() : List.copyOf(members);
    }
}
