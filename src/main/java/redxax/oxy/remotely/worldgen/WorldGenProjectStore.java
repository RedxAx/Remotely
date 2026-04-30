package redxax.oxy.remotely.worldgen;

import redxax.oxy.remotely.worldgen.data.WorldGenProject;
import redxax.oxy.remotely.worldgen.data.WorldGenSerializer;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

final class WorldGenProjectStore {
    private final Map<String, WorldGenProject> activeProjects = new ConcurrentHashMap<>();
    private final Map<String, Map<String, WorldGenProject>> projectCache = new ConcurrentHashMap<>();
    private final Map<String, List<String>> projectLists = new ConcurrentHashMap<>();
    private final Map<String, String> pendingDuplicateIds = new ConcurrentHashMap<>();

    WorldGenProject getOrCreateProject(String serverId, Supplier<WorldGenProject> defaultProjectSupplier) {
        return activeProjects.computeIfAbsent(serverId, id -> defaultProjectSupplier.get());
    }

    void setActiveProject(String serverId, WorldGenProject project) {
        activeProjects.put(serverId, project);
        cacheProject(serverId, project);
    }

    WorldGenProject copyProject(WorldGenProject project, Supplier<WorldGenProject> fallbackSupplier) {
        if (project == null) {
            return fallbackSupplier.get();
        }
        return WorldGenSerializer.deserializeProject(WorldGenSerializer.serializeProject(project));
    }

    WorldGenProject cachedProject(String serverId, String projectId) {
        Map<String, WorldGenProject> serverCache = projectCache.get(serverId);
        return serverCache == null ? null : serverCache.get(projectId);
    }

    void cacheProject(String serverId, WorldGenProject project) {
        if (serverId == null || project == null || project.getId() == null || project.getId().isBlank()) {
            return;
        }
        projectCache.computeIfAbsent(serverId, key -> new ConcurrentHashMap<>()).put(project.getId(), project);
    }

    void removeCachedProject(String serverId, String projectId) {
        Map<String, WorldGenProject> serverCache = projectCache.get(serverId);
        if (serverCache != null) {
            serverCache.remove(projectId);
        }
    }

    void setProjectList(String serverId, List<String> ids) {
        projectLists.put(serverId, List.copyOf(ids != null ? ids : List.of()));
    }

    List<String> getProjectIds(String serverId) {
        return projectLists.getOrDefault(serverId, List.of());
    }

    void setPendingDuplicateId(String serverId, String sourceProjectId, String targetProjectId) {
        pendingDuplicateIds.put(serverId + ":" + sourceProjectId, targetProjectId);
    }

    String removePendingDuplicateId(String serverId, String sourceProjectId) {
        return pendingDuplicateIds.remove(serverId + ":" + sourceProjectId);
    }
}
