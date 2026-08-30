package redxax.oxy.remotely.network;

import redxax.oxy.remotely.util.TaskSchedulers;

import redxax.oxy.remotely.util.AsyncTools;

import restudio.rebase.api.unified.InstanceApi;
import restudio.rebase.backend.ExecutionProvider;
import restudio.rebase.instance.Instance;
import restudio.rebase.instance.InstanceState;
import restudio.rebase.localcontrol.LifecycleManager;
import restudio.rebase.localcontrol.LocalServerControllerClient;
import restudio.resync.network.NetworkNodeStatus;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import restudio.rescreen.platform.Async;
import restudio.rebase.platform.jvm.JvmAsyncBridge;



import java.util.function.Supplier;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class NetworkLifecycleJobManager {
    private static final Duration START_TIMEOUT = Duration.ofMinutes(5);
    private static final Duration STOP_TIMEOUT = Duration.ofMinutes(4);
    private static final Duration DRAIN_TIMEOUT = Duration.ofMinutes(5);
    private static final Duration HEALTH_TIMEOUT = Duration.ofMinutes(2);
    private static final long POLL_DELAY_MILLIS = 500;
    private static final String ACCEPTED_EULA = "#By changing the setting below to TRUE you are indicating your agreement to our EULA (https://aka.ms/MinecraftEULA).\neula=true\n";
    private static final Pattern ACCEPTED_EULA_SETTING = Pattern.compile("(?im)^[\\t ]*eula[\\t ]*=[\\t ]*true[\\t ]*\\r?$");
    private static final Pattern EULA_SETTING = Pattern.compile("(?im)^[\\t ]*eula[\\t ]*=[\\t ]*(?:true|false)[\\t ]*\\r?$");
    private final NetworkLifecycleJobRepository repository;
    private final NetworkRuntimeMonitor runtimeMonitor;
    private final Map<String, NetworkLifecycleJob> jobs = new LinkedHashMap<>();
    private final Object admissionGuard = new Object();
    private final Map<String, String> activeNetworkJobs = new HashMap<>();
    private final Set<String> activeJobIds = new HashSet<>();

    public NetworkLifecycleJobManager(Path applicationDirectory) {
        this(applicationDirectory, null);
    }

    public NetworkLifecycleJobManager(Path applicationDirectory, NetworkRuntimeMonitor runtimeMonitor) {
        repository = new NetworkLifecycleJobRepository(applicationDirectory);
        this.runtimeMonitor = runtimeMonitor;
        reload();
    }

    public synchronized void reload() {
        jobs.clear();
        for (NetworkLifecycleJob loaded : repository.loadAll()) {
            NetworkLifecycleJob recovered = loaded.status() == NetworkLifecycleStatus.RUNNING ? loaded.withStatus(NetworkLifecycleStatus.INTERRUPTED, "Remotely closed during the network operation") : loaded;
            jobs.put(recovered.jobId(), recovered);
            if (recovered != loaded) {
                repository.save(recovered);
            }
        }
    }

    public synchronized List<NetworkLifecycleJob> getJobs() {
        return jobs.values().stream().sorted(Comparator.comparingLong(NetworkLifecycleJob::updatedAt).reversed()).toList();
    }

    public synchronized List<NetworkLifecycleJob> getJobs(String networkId) {
        return getJobs().stream().filter(job -> job.networkId().equals(networkId)).toList();
    }

    public synchronized Optional<NetworkLifecycleJob> getJob(String jobId) {
        return Optional.ofNullable(jobs.get(jobId));
    }

    public Async<NetworkLifecycleJob> execute(NetworkDefinition network, Collection<Instance> instances, NetworkLifecycleOperation operation, String initiator) {
        Objects.requireNonNull(network, "Network is required");
        Map<String, Instance> instancesById = indexInstances(instances);
        List<NetworkLifecycleStep> steps = plan(network, operation);
        for (NetworkLifecycleStep step : steps) {
            if (!instancesById.containsKey(step.instanceId())) {
                return Async.failed(new IllegalStateException("Network server is unavailable: " + step.routeName()));
            }
        }
        NetworkLifecycleJob job = NetworkLifecycleJob.create(network, operation, initiator, steps);
        return runAdmitted(job.networkId(), job.jobId(), () -> {
            persist(job);
            return continueJob(job.startingAttempt(), network, instancesById);
        });
    }

    public Async<NetworkLifecycleJob> executeMember(NetworkDefinition network, NetworkMember member, Instance instance, NetworkLifecycleOperation operation, String initiator) {
        if (network == null || member == null || instance == null) {
            return Async.failed(new IllegalArgumentException("Server is unavailable"));
        }
        if (!network.members().contains(member) || !member.isManaged() || !member.instanceId().equals(instance.getInstanceId())) {
            return Async.failed(new IllegalArgumentException("Server does not belong to this network"));
        }
        NetworkLifecycleAction action = switch (operation) {
            case START -> NetworkLifecycleAction.START;
            case STOP -> NetworkLifecycleAction.STOP;
            default -> throw new IllegalArgumentException("Individual servers can only be started or stopped");
        };
        NetworkLifecycleJob job = NetworkLifecycleJob.create(network, operation, initiator, List.of(NetworkLifecycleStep.pending(member, action, 0)));
        return runAdmitted(job.networkId(), job.jobId(), () -> {
            persist(job);
            return continueJob(job.startingAttempt(), network, Map.of(instance.getInstanceId(), instance));
        });
    }

    public Async<NetworkLifecycleJob> resume(String jobId, NetworkDefinition network, Collection<Instance> instances) {
        Objects.requireNonNull(network, "Network is required");
        NetworkLifecycleJob job;
        synchronized (this) {
            job = jobs.get(jobId);
        }
        if (job == null) {
            return Async.failed(new IllegalArgumentException("Network lifecycle job does not exist: " + jobId));
        }
        return runAdmitted(job.networkId(), job.jobId(), () -> {
            if (!job.canResume()) {
                throw new IllegalStateException("Network lifecycle job cannot be resumed from " + job.status());
            }
            if (!job.networkId().equals(network.networkId()) || job.networkRevision() != network.revision()) {
                throw new IllegalStateException("Network changed after this lifecycle job was created");
            }
            Map<String, Instance> instancesById = indexInstances(instances);
            for (NetworkLifecycleStep step : job.steps()) {
                if (!step.complete() && !instancesById.containsKey(step.instanceId())) {
                    throw new IllegalStateException("Network server is unavailable: " + step.routeName());
                }
            }
            return continueJob(job.startingAttempt(), network, instancesById);
        });
    }

    public Path getDirectory() {
        return repository.getDirectory();
    }

    public List<NetworkLifecycleStep> plan(NetworkDefinition network, NetworkLifecycleOperation operation) {
        if (network == null || operation == null) {
            return List.of();
        }
        return buildSteps(network, operation);
    }

    private Async<NetworkLifecycleJob> continueJob(NetworkLifecycleJob job, NetworkDefinition network, Map<String, Instance> instancesById) {
        persist(job);
        NetworkLifecycleStep next = job.steps().stream().filter(step -> !step.complete()).findFirst().orElse(null);
        if (next == null) {
            NetworkLifecycleJob completed = job.withStatus(NetworkLifecycleStatus.SUCCEEDED, successMessage(job.operation()));
            persist(completed);
            return Async.completed(completed);
        }
        if ((job.operation() == NetworkLifecycleOperation.START || job.operation() == NetworkLifecycleOperation.RESTART) && next.action() == NetworkLifecycleAction.START) {
            return continueParallelStarts(job, network, instancesById);
        }
        NetworkLifecycleStep runningStep = next.running();
        NetworkLifecycleJob runningJob = job.withStep(runningStep);
        persist(runningJob);
        Instance instance = instancesById.get(runningStep.instanceId());
        return executeStep(network, instance, runningStep, runningJob.operation()).handle((outcome, throwable) -> {
            if (throwable != null) {
                String message = rootMessage(throwable);
                NetworkLifecycleJob failed = runningJob.withStep(runningStep.failed(message)).withStatus(NetworkLifecycleStatus.FAILED, message);
                persist(failed);
                return Async.completed(failed);
            }
            NetworkLifecycleJob checkpoint = runningJob.withStep(runningStep.succeeded(outcome.skipped(), outcome.message()));
            persist(checkpoint);
            return continueJob(checkpoint, network, instancesById);
        }).thenCompose(future -> future);
    }

    private Async<NetworkLifecycleJob> continueParallelStarts(NetworkLifecycleJob job, NetworkDefinition network, Map<String, Instance> instancesById) {
        List<NetworkLifecycleStep> pending = job.steps().stream().filter(step -> !step.complete() && step.action() == NetworkLifecycleAction.START).toList();
        NetworkLifecycleJob runningJob = job;
        List<NetworkLifecycleStep> runningSteps = new ArrayList<>();
        for (NetworkLifecycleStep step : pending) {
            NetworkLifecycleStep running = step.running();
            runningSteps.add(running);
            runningJob = runningJob.withStep(running);
        }
        persist(runningJob);
        NetworkLifecycleJob batchJob = runningJob;
        List<Async<ParallelStepOutcome>> starts = runningSteps.stream().map(step -> executeStep(network, instancesById.get(step.instanceId()), step, batchJob.operation()).handle((outcome, throwable) -> new ParallelStepOutcome(step, outcome, throwable)).thenApply(result -> {
            persistParallelOutcome(batchJob.jobId(), result);
            return result;
        })).toList();
        return Async.allOf(starts.toArray(Async[]::new)).thenCompose(unused -> {
            NetworkLifecycleJob checkpoint;
            synchronized (this) {
                checkpoint = jobs.getOrDefault(batchJob.jobId(), batchJob);
            }
            String failure = "";
            for (Async<ParallelStepOutcome> start : starts) {
                ParallelStepOutcome result = start.join();
                if (result.failure() != null) {
                    String message = rootMessage(result.failure());
                    if (failure.isBlank()) failure = message;
                }
            }
            if (!failure.isBlank()) {
                NetworkLifecycleJob failed = checkpoint.withStatus(NetworkLifecycleStatus.FAILED, failure);
                persist(failed);
                return Async.completed(failed);
            }
            persist(checkpoint);
            return continueJob(checkpoint, network, instancesById);
        });
    }

    private synchronized void persistParallelOutcome(String jobId, ParallelStepOutcome result) {
        NetworkLifecycleJob current = jobs.get(jobId);
        if (current == null) return;
        NetworkLifecycleStep completed = result.failure() == null ? result.step().succeeded(result.outcome().skipped(), result.outcome().message()) : result.step().failed(rootMessage(result.failure()));
        persist(current.withStep(completed));
    }

    private Async<StepOutcome> executeStep(NetworkDefinition network, Instance instance, NetworkLifecycleStep step, NetworkLifecycleOperation operation) {
        return switch (step.action()) {
            case START -> start(network, instance, step);
            case STOP -> stop(instance);
            case DRAIN -> drain(network, instance, step, operation == NetworkLifecycleOperation.ROLLING_RESTART);
            case CAPACITY_GATE -> capacityGate(network, step);
            case MAINTENANCE -> runtimeMode(network, step, NetworkRuntimeNodeStatus.MAINTENANCE);
            case HEALTH_GATE -> healthGate(network, step);
            case RESUME -> runtimeMode(network, step, NetworkRuntimeNodeStatus.ONLINE);
        };
    }

    private Async<StepOutcome> start(NetworkDefinition network, Instance instance, NetworkLifecycleStep step) {
        NetworkMember member = member(network, step.nodeId());
        Async<Void> eula = member != null && member.isManaged() && !member.isProxy() ? acceptEula(instance) : Async.completed(null);
        return eula.thenCompose(unused -> status(instance)).thenCompose(observed -> {
            if (observed.ready()) {
                return Async.completed(new StepOutcome(true, instance.getName() + " is already ready"));
            }
            Async<?> request;
            String startOperationId = LifecycleManager.requestStart(instance);
            instance.setState(InstanceState.STARTING);
            if (isLocal(instance)) {
                request = AsyncTools.run(TaskSchedulers.current(), () -> {
                    try {
                        LocalServerControllerClient.start(instance);
                    } catch (IOException exception) {
                        throw new IllegalStateException(exception);
                    }
                });
            } else {
                request = JvmAsyncBridge.fromFuture(InstanceApi.of(instance).console().startServer());
            }
            long deadline = System.currentTimeMillis() + START_TIMEOUT.toMillis();
            return request.thenCompose(unused -> await(instance, true, deadline, startOperationId)).thenApply(status -> new StepOutcome(false, instance.getName() + " is ready")).whenComplete((ignored, error) -> {
                if (error != null) {
                    LifecycleManager.fail(instance, startOperationId, InstanceState.CRASHED, rootMessage(error));
                }
            });
        });
    }

    private Async<Void> acceptEula(Instance instance) {
        InstanceApi.FilesApi files = InstanceApi.of(instance).files();
        Path path = Path.of(instance.getPath()).resolve("eula.txt");
        return JvmAsyncBridge.fromFuture(files.exists(path)).thenCompose(exists -> {
            if (!exists) {
                return JvmAsyncBridge.fromFuture(files.write(path, ACCEPTED_EULA));
            }
            return JvmAsyncBridge.fromFuture(files.read(path)).thenCompose(content -> {
                String accepted = ensureEulaAccepted(content);
                return accepted.equals(content) ? Async.completed(null) : JvmAsyncBridge.fromFuture(files.write(path, accepted));
            });
        });
    }

    static String ensureEulaAccepted(String content) {
        String source = content == null ? "" : content;
        if (ACCEPTED_EULA_SETTING.matcher(source).find()) {
            return source;
        }
        if (EULA_SETTING.matcher(source).find()) {
            return EULA_SETTING.matcher(source).replaceFirst("eula=true");
        }
        if (source.isBlank()) {
            return ACCEPTED_EULA;
        }
        String separator = source.contains("\r\n") ? "\r\n" : "\n";
        return source + (source.endsWith("\n") || source.endsWith("\r") ? "" : separator) + "eula=true" + separator;
    }

    private Async<StepOutcome> stop(Instance instance) {
        return status(instance).thenCompose(observed -> {
            if (stopped(observed.state())) {
                LifecycleManager.complete(instance, LifecycleManager.activeOperationId(instance), observed.state());
                return Async.completed(new StepOutcome(true, instance.getName() + " is already stopped"));
            }
            String stopOperationId = LifecycleManager.requestStop(instance);
            instance.setState(InstanceState.STOPPING);
            long deadline = System.currentTimeMillis() + STOP_TIMEOUT.toMillis();
            return JvmAsyncBridge.fromFuture(InstanceApi.of(instance).console().stopServer()).thenCompose(unused -> await(instance, false, deadline, stopOperationId)).thenApply(status -> new StepOutcome(false, instance.getName() + " stopped")).whenComplete((ignored, error) -> {
                if (error != null) {
                    LifecycleManager.restoreRunning(instance, stopOperationId, rootMessage(error));
                }
            });
        });
    }

    private Async<StepOutcome> drain(NetworkDefinition network, Instance instance, NetworkLifecycleStep step, boolean awaitRuntime) {
        NetworkMember member = member(network, step.nodeId());
        if (awaitRuntime && member != null && member.resyncEnabled() && network.runtime().enabled()) {
            return awaitRuntimePlayers(network.networkId(), step.nodeId(), System.currentTimeMillis() + DRAIN_TIMEOUT.toMillis()).thenApply(unused -> new StepOutcome(false, instance.getName() + " has no active players"));
        }
        return status(instance).thenCompose(observed -> {
            if (stopped(observed.state())) {
                return Async.completed(new StepOutcome(true, instance.getName() + " is stopped"));
            }
            return JvmAsyncBridge.fromFuture(InstanceApi.of(instance).players().getOnline()).thenCompose(players -> {
                if (!players.isEmpty()) {
                    return Async.failed(new IllegalStateException(instance.getName() + " still has " + players.size() + " players and no safe transfer route is active"));
                }
                return Async.completed(new StepOutcome(false, instance.getName() + " has no active players"));
            });
        });
    }

    private Async<StepOutcome> capacityGate(NetworkDefinition network, NetworkLifecycleStep step) {
        if (runtimeMonitor == null) {
            return Async.failed(new IllegalStateException("ReSync runtime is unavailable"));
        }
        NetworkRuntimeSnapshot snapshot = runtimeMonitor.snapshot(network.networkId());
        if (!snapshot.connected()) {
            return Async.failed(new IllegalStateException("ReSync runtime is required for a rolling restart"));
        }
        NetworkRuntimeNodePresence target = snapshot.node(step.nodeId()).orElse(null);
        if (target == null || target.status() != NetworkRuntimeNodeStatus.ONLINE) {
            return Async.failed(new IllegalStateException(step.routeName() + " is not healthy enough to restart"));
        }
        List<String> backendNodes = network.members().stream().filter(member -> !member.isProxy() && member.isManaged() && member.resyncEnabled()).map(NetworkMember::nodeId).toList();
        long healthyBackends = snapshot.nodes().values().stream().filter(presence -> backendNodes.contains(presence.nodeId()) && !presence.nodeId().equals(step.nodeId()) && presence.status() == NetworkRuntimeNodeStatus.ONLINE).count();
        if (healthyBackends == 0) {
            return Async.failed(new IllegalStateException("No other healthy backend can carry players during the restart"));
        }
        int availableSlots = snapshot.nodes().values().stream().filter(presence -> backendNodes.contains(presence.nodeId()) && !presence.nodeId().equals(step.nodeId()) && presence.status() == NetworkRuntimeNodeStatus.ONLINE).mapToInt(presence -> Math.max(0, presence.capacity() - presence.players())).sum();
        if (availableSlots < target.players()) {
            return Async.failed(new IllegalStateException("Healthy backends have " + availableSlots + " free slots but " + step.routeName() + " has " + target.players() + " players"));
        }
        return Async.completed(new StepOutcome(false, availableSlots + " healthy slots remain available"));
    }

    private Async<StepOutcome> runtimeMode(NetworkDefinition network, NetworkLifecycleStep step, NetworkRuntimeNodeStatus status) {
        NetworkMember member = member(network, step.nodeId());
        if (member == null || !member.resyncEnabled() || !network.runtime().enabled()) {
            return Async.completed(new StepOutcome(true, step.routeName() + " does not use ReSync runtime"));
        }
        if (runtimeMonitor == null) {
            return Async.failed(new IllegalStateException("ReSync runtime is unavailable"));
        }
        return runtimeMonitor.setNodeMode(network.networkId(), step.nodeId(), NetworkNodeStatus.valueOf(status.name())).thenApply(unused -> new StepOutcome(false, step.routeName() + " is " + status.name().toLowerCase(Locale.ROOT)));
    }

    private Async<StepOutcome> healthGate(NetworkDefinition network, NetworkLifecycleStep step) {
        NetworkMember member = member(network, step.nodeId());
        if (member == null || !member.resyncEnabled() || !network.runtime().enabled()) {
            return Async.completed(new StepOutcome(true, step.routeName() + " passed server readiness"));
        }
        if (runtimeMonitor == null) {
            return Async.failed(new IllegalStateException("ReSync runtime is unavailable"));
        }
        return awaitRuntimeHealth(network.networkId(), step.nodeId(), System.currentTimeMillis() + HEALTH_TIMEOUT.toMillis()).thenApply(unused -> new StepOutcome(false, step.routeName() + " is healthy"));
    }

    private Async<Void> awaitRuntimePlayers(String networkId, String nodeId, long deadline) {
        if (runtimeMonitor == null) {
            return Async.failed(new IllegalStateException("ReSync runtime is unavailable"));
        }
        NetworkRuntimeSnapshot snapshot = runtimeMonitor.snapshot(networkId);
        NetworkRuntimeNodePresence presence = snapshot.connected() ? snapshot.node(nodeId).orElse(null) : null;
        if (presence != null && presence.players() == 0) {
            return Async.completed(null);
        }
        if (System.currentTimeMillis() >= deadline) {
            int players = presence == null ? -1 : presence.players();
            return Async.failed(new IllegalStateException(players < 0 ? "ReSync runtime did not report drain completion" : players + " players remain after the drain timeout"));
        }
        return AsyncTools.delay(TaskSchedulers.current(), Duration.ofSeconds(1)).thenCompose(unused -> awaitRuntimePlayers(networkId, nodeId, deadline));
    }

    private Async<Void> awaitRuntimeHealth(String networkId, String nodeId, long deadline) {
        if (runtimeMonitor == null) {
            return Async.failed(new IllegalStateException("ReSync runtime is unavailable"));
        }
        NetworkRuntimeSnapshot snapshot = runtimeMonitor.snapshot(networkId);
        NetworkRuntimeNodePresence presence = snapshot.connected() ? snapshot.node(nodeId).orElse(null) : null;
        if (presence != null && (presence.status() == NetworkRuntimeNodeStatus.ONLINE || presence.status() == NetworkRuntimeNodeStatus.MAINTENANCE) && System.currentTimeMillis() - presence.observedAt() <= 20_000) {
            return Async.completed(null);
        }
        if (System.currentTimeMillis() >= deadline) {
            return Async.failed(new IllegalStateException("Backend did not return to live ReSync health before the timeout"));
        }
        return AsyncTools.delay(TaskSchedulers.current(), Duration.ofSeconds(1)).thenCompose(unused -> awaitRuntimeHealth(networkId, nodeId, deadline));
    }

    private NetworkMember member(NetworkDefinition network, String nodeId) {
        return network.members().stream().filter(candidate -> candidate.nodeId().equals(nodeId)).findFirst().orElse(null);
    }

    private Async<ExecutionProvider.ExecutionStatus> await(Instance instance, boolean ready, long deadline, String operationId) {
        return status(instance).thenCompose(observed -> {
            boolean complete = ready ? observed.ready() : stopped(observed.state());
            boolean failedStart = ready && (observed.state() == InstanceState.CRASHED || observed.state() == InstanceState.STOPPED);
            if (complete) {
                if (ready) {
                    LifecycleManager.markReady(instance, operationId);
                } else {
                    LifecycleManager.complete(instance, operationId, InstanceState.STOPPED);
                }
            } else if (failedStart) {
                String detail = observed.detail().isBlank() || "crashed".equalsIgnoreCase(observed.detail()) ? "" : " • " + observed.detail();
                String outcome = observed.state() == InstanceState.STOPPED || observed.detail().toLowerCase(Locale.ROOT).contains("code 0") ? " stopped while starting" : " crashed while starting";
                LifecycleManager.fail(instance, operationId, InstanceState.CRASHED, instance.getName() + outcome + detail);
            }
            if (complete) {
                instance.setState(observed.state());
                return Async.completed(observed);
            }
            if (failedStart) {
                return Async.failed(new IllegalStateException(instance.getName() + " did not become ready"));
            }
            if (System.currentTimeMillis() >= deadline) {
                String target = ready ? "ready" : "stopped";
                String message = instance.getName() + " did not become " + target + " before the timeout";
                if (ready) {
                    LifecycleManager.fail(instance, operationId, InstanceState.CRASHED, message);
                } else {
                    LifecycleManager.restoreRunning(instance, operationId, message);
                }
                return Async.failed(new IllegalStateException(message));
            }
            instance.setState(observed.state());
            return AsyncTools.delay(TaskSchedulers.current(), Duration.ofMillis(POLL_DELAY_MILLIS)).thenCompose(unused -> await(instance, ready, deadline, operationId));
        });
    }

    private Async<ExecutionProvider.ExecutionStatus> status(Instance instance) {
        return JvmAsyncBridge.fromFuture(InstanceApi.of(instance).console().getStatus());
    }

    private List<NetworkLifecycleStep> buildSteps(NetworkDefinition network, NetworkLifecycleOperation operation) {
        List<NetworkMember> startOrder = startOrder(network);
        List<NetworkMember> stopOrder = new ArrayList<>(startOrder);
        stopOrder.removeIf(NetworkMember::isProxy);
        Collections.reverse(stopOrder);
        stopOrder.addFirst(network.proxyMember());
        List<NetworkLifecycleStep> steps = new ArrayList<>();
        switch (operation) {
            case START -> addSteps(steps, startOrder, NetworkLifecycleAction.START);
            case STOP -> addSteps(steps, stopOrder, NetworkLifecycleAction.STOP);
            case RESTART -> {
                addSteps(steps, stopOrder, NetworkLifecycleAction.STOP);
                addSteps(steps, startOrder, NetworkLifecycleAction.START);
            }
            case ROLLING_RESTART -> {
                List<NetworkMember> backends = new ArrayList<>(startOrder.stream().filter(member -> !member.isProxy()).toList());
                Collections.reverse(backends);
                if (!network.runtime().enabled() || backends.stream().anyMatch(member -> !member.resyncEnabled())) {
                    throw new IllegalStateException("A rolling restart requires ReSync on every managed backend");
                }
                if (backends.size() < 2) {
                    throw new IllegalStateException("A rolling restart requires at least two backends");
                }
                for (NetworkMember member : backends) {
                    addSteps(steps, List.of(member), NetworkLifecycleAction.CAPACITY_GATE);
                    addSteps(steps, List.of(member), NetworkLifecycleAction.MAINTENANCE);
                    addSteps(steps, List.of(member), NetworkLifecycleAction.DRAIN);
                    addSteps(steps, List.of(member), NetworkLifecycleAction.STOP);
                    addSteps(steps, List.of(member), NetworkLifecycleAction.START);
                    addSteps(steps, List.of(member), NetworkLifecycleAction.HEALTH_GATE);
                    addSteps(steps, List.of(member), NetworkLifecycleAction.RESUME);
                }
            }
            case DRAIN -> addSteps(steps, startOrder.stream().filter(member -> !member.isProxy()).toList(), NetworkLifecycleAction.DRAIN);
        }
        return List.copyOf(steps);
    }

    private List<NetworkMember> startOrder(NetworkDefinition network) {
        Map<String, NetworkMember> membersByNode = network.members().stream().collect(Collectors.toMap(NetworkMember::nodeId, member -> member));
        LinkedHashSet<NetworkMember> ordered = new LinkedHashSet<>();
        network.routingGroups().stream().filter(group -> group.id().equals("fallback")).flatMap(group -> group.nodeIds().stream()).map(membersByNode::get).filter(member -> member != null && member.isManaged() && !member.isProxy()).forEach(ordered::add);
        network.members().stream().filter(NetworkMember::isManaged).filter(member -> !member.isProxy()).sorted(Comparator.comparing(NetworkMember::routeName, String.CASE_INSENSITIVE_ORDER)).forEach(ordered::add);
        ordered.add(network.proxyMember());
        return ordered.stream().filter(member -> member != null).toList();
    }

    private void addSteps(List<NetworkLifecycleStep> steps, List<NetworkMember> members, NetworkLifecycleAction action) {
        for (NetworkMember member : members) {
            steps.add(NetworkLifecycleStep.pending(member, action, steps.size()));
        }
    }

    private Map<String, Instance> indexInstances(Collection<Instance> instances) {
        Map<String, Instance> indexed = new LinkedHashMap<>();
        if (instances != null) {
            instances.stream().filter(instance -> instance != null && instance.getInstanceId() != null).forEach(instance -> indexed.put(instance.getInstanceId(), instance));
        }
        return indexed;
    }

    private boolean isLocal(Instance instance) {
        return instance.getBackendConfig() == null || "LOCAL".equalsIgnoreCase(instance.getBackendConfig().type);
    }

    private boolean stopped(InstanceState state) {
        return state == InstanceState.STOPPED || state == InstanceState.CRASHED;
    }

    private synchronized void persist(NetworkLifecycleJob job) {
        repository.save(job);
        jobs.put(job.jobId(), job);
    }

    private Async<NetworkLifecycleJob> runAdmitted(String networkId, String jobId, Supplier<Async<NetworkLifecycleJob>> operation) {
        Admission admission;
        synchronized (admissionGuard) {
            if (activeJobIds.contains(jobId)) {
                return Async.failed(new IllegalStateException("Network lifecycle job already has an active execution: " + jobId));
            }
            if (activeNetworkJobs.containsKey(networkId)) {
                return Async.failed(new IllegalStateException("Network has an active lifecycle operation: " + networkId));
            }
            activeNetworkJobs.put(networkId, jobId);
            activeJobIds.add(jobId);
            admission = new Admission(networkId, jobId);
        }
        try {
            Async<NetworkLifecycleJob> future = Objects.requireNonNull(operation.get(), "Lifecycle operation did not return a future");
            return future.whenComplete((ignored, throwable) -> release(admission));
        } catch (RuntimeException exception) {
            release(admission);
            return Async.failed(exception);
        }
    }

    private void release(Admission admission) {
        synchronized (admissionGuard) {
            if (Objects.equals(activeNetworkJobs.get(admission.networkId()), admission.jobId())) {
                activeNetworkJobs.remove(admission.networkId());
            }
            activeJobIds.remove(admission.jobId());
        }
    }

    private String successMessage(NetworkLifecycleOperation operation) {
        return switch (operation) {
            case START -> "Network is ready";
            case STOP -> "Network is stopped";
            case RESTART -> "Network restarted";
            case ROLLING_RESTART -> "Network backends rolled without losing healthy capacity";
            case DRAIN -> "Network has no active backend players";
        };
    }

    private String rootMessage(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null) {
            current = current.getCause();
        }
        return current.getMessage() == null ? current.getClass().getSimpleName() : current.getMessage();
    }

    private record StepOutcome(boolean skipped, String message) {
    }

    private record ParallelStepOutcome(NetworkLifecycleStep step, StepOutcome outcome, Throwable failure) {
    }

    private record Admission(String networkId, String jobId) {
    }
}
