package redxax.oxy.remotely.network;

import redxax.oxy.remotely.network.config.NetworkConfigurationAdapters;
import restudio.rebase.backend.FileSystemProvider;
import restudio.rebase.backend.ServerBackend;
import restudio.rebase.instance.Instance;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;

public class NetworkConfigurationTransaction {
    private final NetworkMutationEngine mutationEngine;

    public NetworkConfigurationTransaction() {
        this.mutationEngine = new NetworkMutationEngine(new NetworkConfigurationAdapters());
    }

    public CompletableFuture<NetworkPreparedPlan> prepare(NetworkReconciliationPlan plan, Collection<Instance> instances) {
        Objects.requireNonNull(plan, "Plan is required");
        Map<String, Instance> instancesById = indexInstances(instances);
        Map<NetworkConfigDocumentKey, List<NetworkConfigMutation>> groups = groupMutations(plan.mutations());
        Map<NetworkConfigDocumentKey, NetworkDocumentSnapshot> snapshots = Collections.synchronizedMap(new LinkedHashMap<>());
        List<CompletableFuture<Void>> reads = new ArrayList<>();
        for (NetworkConfigDocumentKey key : groups.keySet()) {
            Instance instance = requireInstance(instancesById, key.instanceId());
            FileSystemProvider fileSystem = requireFileSystem(instance);
            Path target = resolve(instance, key.path());
            reads.add(fileSystem.exists(target).thenCompose(exists -> {
                if (!exists) {
                    snapshots.put(key, new NetworkDocumentSnapshot(key, "", false));
                    return CompletableFuture.completedFuture(null);
                }
                return fileSystem.read(target).thenAccept(content -> snapshots.put(key, new NetworkDocumentSnapshot(key, content, true)));
            }));
        }
        return CompletableFuture.allOf(reads.toArray(CompletableFuture[]::new)).thenApply(unused -> {
            Map<NetworkConfigDocumentKey, String> documents = new LinkedHashMap<>();
            snapshots.forEach((key, snapshot) -> documents.put(key, snapshot.content()));
            NetworkReconciliationPlan resolved = mutationEngine.resolveCurrentValues(plan, documents);
            return new NetworkPreparedPlan(resolved, snapshots);
        });
    }

    public CompletableFuture<NetworkApplyResult> apply(NetworkPreparedPlan prepared, NetworkDefinition currentNetwork, Collection<Instance> instances) {
        return apply(prepared, currentNetwork, instances, NetworkTransactionListener.NONE, List.of());
    }

    public CompletableFuture<NetworkApplyResult> apply(NetworkPreparedPlan prepared, NetworkDefinition currentNetwork, Collection<Instance> instances, NetworkTransactionListener listener, Collection<NetworkJobDocument> recoveryDocuments) {
        Objects.requireNonNull(prepared, "Prepared plan is required");
        Objects.requireNonNull(currentNetwork, "Current network is required");
        NetworkTransactionListener resolvedListener = listener == null ? NetworkTransactionListener.NONE : listener;
        NetworkReconciliationPlan plan = prepared.plan();
        if (!plan.canApply()) {
            return CompletableFuture.failedFuture(new IllegalStateException("Network plan has blocking issues"));
        }
        if (!plan.networkId().equals(currentNetwork.networkId()) || plan.networkRevision() != currentNetwork.revision()) {
            return CompletableFuture.failedFuture(new IllegalStateException("Network changed after this plan was created"));
        }
        Map<String, Instance> instancesById = indexInstances(instances);
        Map<NetworkConfigDocumentKey, List<NetworkConfigMutation>> grouped = groupMutations(plan.changes());
        List<DocumentOperation> operations = grouped.entrySet().stream().map(entry -> operation(entry.getKey(), entry.getValue(), prepared, instancesById, currentNetwork)).sorted(operationOrder(plan.strategy())).toList();
        Map<NetworkConfigDocumentKey, NetworkJobDocument> recoveryByKey = indexRecoveryDocuments(recoveryDocuments);
        List<DocumentOperation> applied = Collections.synchronizedList(new ArrayList<>());
        CompletableFuture<Void> execution = CompletableFuture.completedFuture(null);
        for (DocumentOperation operation : operations) {
            execution = execution.thenCompose(unused -> applyOperation(plan.planId(), operation, recoveryByKey.get(operation.key())).thenRun(() -> {
                applied.add(operation);
                notifyListener(() -> resolvedListener.onDocumentApplied(operation.key()));
            }));
        }
        return execution.handle((unused, throwable) -> {
            if (throwable == null) {
                synchronizeInstanceState(operations);
                return CompletableFuture.completedFuture(new NetworkApplyResult(plan.planId(), true, false, applied.stream().map(DocumentOperation::key).toList(), "Network configuration applied"));
            }
            notifyListener(resolvedListener::onRollbackStarted);
            return rollback(applied, resolvedListener).handle((rollbackUnused, rollbackError) -> {
                String message = rootMessage(throwable);
                if (rollbackError != null) {
                    message += "; rollback failed: " + rootMessage(rollbackError);
                }
                return new NetworkApplyResult(plan.planId(), false, rollbackError == null, applied.stream().map(DocumentOperation::key).toList(), message);
            });
        }).thenCompose(result -> result);
    }

    public List<NetworkJobDocument> describe(NetworkPreparedPlan prepared, NetworkDefinition currentNetwork, Collection<Instance> instances) {
        Objects.requireNonNull(prepared, "Prepared plan is required");
        Objects.requireNonNull(currentNetwork, "Current network is required");
        Map<String, Instance> instancesById = indexInstances(instances);
        Map<NetworkConfigDocumentKey, List<NetworkConfigMutation>> grouped = groupMutations(prepared.plan().changes());
        List<DocumentOperation> operations = prepared.documents().keySet().stream().map(key -> operation(key, grouped.getOrDefault(key, List.of()), prepared, instancesById, currentNetwork)).sorted(operationOrder(prepared.plan().strategy())).toList();
        List<NetworkJobDocument> documents = new ArrayList<>(operations.size());
        for (int index = 0; index < operations.size(); index++) {
            DocumentOperation operation = operations.get(index);
            String desired = mutationEngine.apply(operation.snapshot().content(), operation.mutations());
            String originalHash = NetworkDocumentFingerprint.sha256(operation.snapshot().content());
            String desiredHash = NetworkDocumentFingerprint.sha256(desired);
            NetworkJobDocumentState state = originalHash.equals(desiredHash) ? NetworkJobDocumentState.UNCHANGED : NetworkJobDocumentState.PENDING;
            documents.add(new NetworkJobDocument(operation.key(), index, operation.snapshot().exists(), originalHash, desiredHash, state));
        }
        return List.copyOf(documents);
    }

    public CompletableFuture<Void> rollback(String planId, Collection<NetworkJobDocument> documents, Collection<Instance> instances, NetworkTransactionListener listener) {
        if (planId == null || planId.isBlank()) {
            return CompletableFuture.failedFuture(new IllegalArgumentException("Plan ID is required"));
        }
        Map<String, Instance> instancesById = indexInstances(instances);
        NetworkTransactionListener resolvedListener = listener == null ? NetworkTransactionListener.NONE : listener;
        List<NetworkJobDocument> ordered = documents == null ? List.of() : documents.stream().filter(NetworkJobDocument::changed).sorted(Comparator.comparingInt(NetworkJobDocument::applyOrder).reversed()).toList();
        CompletableFuture<Void> rollback = CompletableFuture.completedFuture(null);
        for (NetworkJobDocument document : ordered) {
            rollback = rollback.thenCompose(unused -> rollbackDocument(planId, document, instancesById).thenRun(() -> notifyListener(() -> resolvedListener.onDocumentRolledBack(document.key()))));
        }
        return rollback.thenCompose(unused -> refreshServerProperties(ordered, instancesById));
    }

    private void synchronizeInstanceState(List<DocumentOperation> operations) {
        for (DocumentOperation operation : operations) {
            if (!operation.key().path().equals("server.properties")) {
                continue;
            }
            for (NetworkConfigMutation mutation : operation.mutations()) {
                operation.instance().getServerProperties().setProperty(mutation.key(), mutation.desiredValue());
            }
        }
    }

    private CompletableFuture<Void> applyOperation(String planId, DocumentOperation operation, NetworkJobDocument recovery) {
        return operation.fileSystem().exists(operation.target()).thenCompose(exists -> {
            CompletableFuture<String> current = exists ? operation.fileSystem().read(operation.target()) : CompletableFuture.completedFuture("");
            return current.thenCompose(content -> {
                if (exists != operation.snapshot().exists() || !content.equals(operation.snapshot().content())) {
                    return CompletableFuture.failedFuture(new IllegalStateException("Configuration changed after plan review: " + operation.key().path()));
                }
                String updated = mutationEngine.apply(content, operation.mutations());
                Path backup = backupPath(operation.instance(), planId, operation.key().path());
                CompletableFuture<Void> backupWrite = recovery == null ? writeBackup(operation, backup) : writeRecoveryBackup(operation, backup, recovery, updated);
                return backupWrite.thenCompose(unused -> createParent(operation.fileSystem(), operation.target())).thenCompose(unused -> operation.fileSystem().writeAtomic(operation.target(), updated));
            });
        });
    }

    private CompletableFuture<Void> writeBackup(DocumentOperation operation, Path backup) {
        return operation.snapshot().exists() ? createParent(operation.fileSystem(), backup).thenCompose(unused -> operation.fileSystem().writeAtomic(backup, operation.snapshot().content())) : CompletableFuture.completedFuture(null);
    }

    private CompletableFuture<Void> writeRecoveryBackup(DocumentOperation operation, Path backup, NetworkJobDocument recovery, String updated) {
        String currentHash = NetworkDocumentFingerprint.sha256(operation.snapshot().content());
        String desiredHash = NetworkDocumentFingerprint.sha256(updated);
        if (!desiredHash.equals(recovery.desiredHash())) {
            return CompletableFuture.failedFuture(new IllegalStateException("Desired configuration changed while recovering " + operation.key().path()));
        }
        if (operation.snapshot().exists() != recovery.originalExists() || !currentHash.equals(recovery.originalHash())) {
            return CompletableFuture.failedFuture(new IllegalStateException("Configuration drift prevents recovery of " + operation.key().path()));
        }
        if (!recovery.originalExists()) {
            return CompletableFuture.completedFuture(null);
        }
        return operation.fileSystem().exists(backup).thenCompose(exists -> {
            if (!exists) {
                return createParent(operation.fileSystem(), backup).thenCompose(unused -> operation.fileSystem().writeAtomic(backup, operation.snapshot().content()));
            }
            return operation.fileSystem().read(backup).thenCompose(content -> NetworkDocumentFingerprint.sha256(content).equals(recovery.originalHash()) ? CompletableFuture.completedFuture(null) : CompletableFuture.failedFuture(new IllegalStateException("Recovery backup changed for " + operation.key().path())));
        });
    }

    private CompletableFuture<Void> rollback(List<DocumentOperation> applied, NetworkTransactionListener listener) {
        List<DocumentOperation> reverse = new ArrayList<>(applied);
        Collections.reverse(reverse);
        CompletableFuture<Void> rollback = CompletableFuture.completedFuture(null);
        for (DocumentOperation operation : reverse) {
            rollback = rollback.thenCompose(unused -> {
                CompletableFuture<Void> restoration;
                if (operation.snapshot().exists()) {
                    restoration = operation.fileSystem().writeAtomic(operation.target(), operation.snapshot().content());
                } else {
                    restoration = operation.fileSystem().delete(List.of(operation.target()));
                }
                return restoration.thenRun(() -> notifyListener(() -> listener.onDocumentRolledBack(operation.key())));
            });
        }
        return rollback;
    }

    private CompletableFuture<Void> rollbackDocument(String planId, NetworkJobDocument document, Map<String, Instance> instances) {
        Instance instance = requireInstance(instances, document.key().instanceId());
        FileSystemProvider fileSystem = requireFileSystem(instance);
        Path target = resolve(instance, document.key().path());
        Path backup = backupPath(instance, planId, document.key().path());
        return fileSystem.exists(target).thenCompose(exists -> {
            CompletableFuture<String> current = exists ? fileSystem.read(target) : CompletableFuture.completedFuture("");
            return current.thenCompose(content -> {
                String currentHash = NetworkDocumentFingerprint.sha256(content);
                if (document.originalExists()) {
                    if (exists && currentHash.equals(document.originalHash())) {
                        return CompletableFuture.completedFuture(null);
                    }
                    if (!exists || !currentHash.equals(document.desiredHash())) {
                        return CompletableFuture.failedFuture(new IllegalStateException("Configuration drift prevents rollback of " + document.key().path()));
                    }
                    return fileSystem.exists(backup).thenCompose(backupExists -> {
                        if (!backupExists) {
                            return CompletableFuture.failedFuture(new IllegalStateException("Recovery backup is missing for " + document.key().path()));
                        }
                        return fileSystem.read(backup).thenCompose(original -> {
                            if (!NetworkDocumentFingerprint.sha256(original).equals(document.originalHash())) {
                                return CompletableFuture.failedFuture(new IllegalStateException("Recovery backup changed for " + document.key().path()));
                            }
                            return createParent(fileSystem, target).thenCompose(unused -> fileSystem.writeAtomic(target, original));
                        });
                    });
                }
                if (!exists) {
                    return CompletableFuture.completedFuture(null);
                }
                if (!currentHash.equals(document.desiredHash())) {
                    return CompletableFuture.failedFuture(new IllegalStateException("Configuration drift prevents rollback of " + document.key().path()));
                }
                return fileSystem.delete(List.of(target));
            });
        });
    }

    private DocumentOperation operation(NetworkConfigDocumentKey key, List<NetworkConfigMutation> mutations, NetworkPreparedPlan prepared, Map<String, Instance> instances, NetworkDefinition network) {
        Instance instance = requireInstance(instances, key.instanceId());
        NetworkDocumentSnapshot snapshot = prepared.documents().get(key);
        if (snapshot == null) {
            throw new IllegalArgumentException("Plan was not prepared with " + key.path());
        }
        boolean proxy = network.proxyInstanceId().equals(key.instanceId());
        return new DocumentOperation(key, instance, requireFileSystem(instance), resolve(instance, key.path()), snapshot, mutations, proxy);
    }

    private Comparator<DocumentOperation> operationOrder(NetworkPlanStrategy strategy) {
        Comparator<DocumentOperation> hostOrder = strategy == NetworkPlanStrategy.DETACH ? Comparator.comparing((DocumentOperation operation) -> !operation.proxy()) : Comparator.comparing(DocumentOperation::proxy);
        return hostOrder.thenComparingInt(operation -> operation.key().path().equals("forwarding.secret") ? 0 : operation.key().path().equals("velocity.toml") ? 2 : 1).thenComparing(operation -> operation.key().path());
    }

    private Map<NetworkConfigDocumentKey, List<NetworkConfigMutation>> groupMutations(List<NetworkConfigMutation> mutations) {
        Map<NetworkConfigDocumentKey, List<NetworkConfigMutation>> grouped = new LinkedHashMap<>();
        for (NetworkConfigMutation mutation : mutations) {
            NetworkConfigDocumentKey key = new NetworkConfigDocumentKey(mutation.instanceId(), mutation.path());
            grouped.computeIfAbsent(key, ignored -> new ArrayList<>()).add(mutation);
        }
        return grouped;
    }

    private Map<NetworkConfigDocumentKey, NetworkJobDocument> indexRecoveryDocuments(Collection<NetworkJobDocument> documents) {
        Map<NetworkConfigDocumentKey, NetworkJobDocument> indexed = new LinkedHashMap<>();
        if (documents != null) {
            documents.forEach(document -> indexed.put(document.key(), document));
        }
        return indexed;
    }

    private Map<String, Instance> indexInstances(Collection<Instance> instances) {
        Map<String, Instance> indexed = new LinkedHashMap<>();
        if (instances != null) {
            instances.stream().filter(Objects::nonNull).forEach(instance -> indexed.put(instance.getInstanceId(), instance));
        }
        return indexed;
    }

    private CompletableFuture<Void> refreshServerProperties(Collection<NetworkJobDocument> documents, Map<String, Instance> instances) {
        List<CompletableFuture<Void>> refreshes = documents.stream().filter(document -> document.key().path().equals("server.properties")).map(document -> instances.get(document.key().instanceId())).filter(Objects::nonNull).distinct().map(instance -> {
            if (instance.getBackend() != null) {
                return instance.loadRemoteServerProperties(true);
            }
            instance.loadServerProperties();
            return CompletableFuture.<Void>completedFuture(null);
        }).toList();
        return CompletableFuture.allOf(refreshes.toArray(CompletableFuture[]::new));
    }

    private Instance requireInstance(Map<String, Instance> instances, String instanceId) {
        Instance instance = instances.get(instanceId);
        if (instance == null) {
            throw new IllegalArgumentException("Instance is unavailable: " + instanceId);
        }
        return instance;
    }

    private FileSystemProvider requireFileSystem(Instance instance) {
        ServerBackend backend = instance.getBackend();
        if (backend == null || backend.getFileSystem() == null) {
            throw new IllegalStateException("File access is unavailable for " + instance.getName());
        }
        return backend.getFileSystem();
    }

    private Path resolve(Instance instance, String relativePath) {
        Path root = Path.of(instance.getPath()).toAbsolutePath().normalize();
        Path target = root.resolve(relativePath).normalize();
        if (!target.startsWith(root)) {
            throw new IllegalArgumentException("Configuration path escapes the instance: " + relativePath);
        }
        return target;
    }

    private Path backupPath(Instance instance, String planId, String relativePath) {
        return resolve(instance, ".remotely/network-backups/" + planId + "/" + relativePath);
    }

    private CompletableFuture<Void> createParent(FileSystemProvider fileSystem, Path path) {
        Path parent = path.getParent();
        return parent == null ? CompletableFuture.completedFuture(null) : fileSystem.createDirectory(parent);
    }

    private String rootMessage(Throwable throwable) {
        Throwable current = throwable;
        while ((current instanceof CompletionException || current instanceof ExecutionException) && current.getCause() != null) {
            current = current.getCause();
        }
        return current.getMessage() == null ? current.getClass().getSimpleName() : current.getMessage();
    }

    private void notifyListener(Runnable notification) {
        try {
            notification.run();
        } catch (RuntimeException ignored) {
        }
    }

    private record DocumentOperation(NetworkConfigDocumentKey key, Instance instance, FileSystemProvider fileSystem, Path target, NetworkDocumentSnapshot snapshot, List<NetworkConfigMutation> mutations, boolean proxy) {
    }
}
