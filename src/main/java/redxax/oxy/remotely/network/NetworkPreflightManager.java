package redxax.oxy.remotely.network;

import restudio.rebase.instance.Instance;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import restudio.rebase.platform.Async;



public class NetworkPreflightManager {
    private final NetworkPreflightRepository repository;
    private final NetworkPreflightService service;
    private final Map<String, NetworkPreflightReport> reports = new LinkedHashMap<>();
    private volatile String loadError = "";

    public NetworkPreflightManager(Path applicationDirectory, NetworkPreflightService service) {
        repository = new NetworkPreflightRepository(applicationDirectory);
        this.service = service;
        reload();
    }

    public synchronized void reload() {
        try {
            reports.clear();
            for (NetworkPreflightReport report : repository.loadAll()) {
                NetworkPreflightReport loaded = report.status() == NetworkPreflightStatus.RUNNING ? report.interrupted() : report;
                reports.put(loaded.reportId(), loaded);
                if (loaded != report) {
                    repository.save(loaded);
                }
            }
            loadError = "";
        } catch (NetworkPersistenceException exception) {
            loadError = exception.getMessage() == null ? "Failed to load network preflight reports" : exception.getMessage();
        }
    }

    public synchronized List<NetworkPreflightReport> getReports() {
        return reports.values().stream().sorted(Comparator.comparingLong(NetworkPreflightReport::startedAt).reversed()).toList();
    }

    public synchronized List<NetworkPreflightReport> getReports(String networkId) {
        return getReports().stream().filter(report -> report.networkId().equals(networkId)).toList();
    }

    public Async<NetworkPreflightReport> run(NetworkDefinition network, Collection<Instance> instances, Collection<NetworkDefinition> networks) {
        NetworkPreflightReport running = NetworkPreflightReport.running(network);
        synchronized (this) {
            persist(running);
        }
        return service.run(network, instances, networks).handle((checks, throwable) -> {
            List<NetworkPreflightCheck> completedChecks = checks;
            if (throwable != null) {
                completedChecks = new ArrayList<>();
                completedChecks.add(NetworkPreflightCheck.failed("preflight", network.networkId(), "Join Preflight", rootMessage(throwable)));
            }
            NetworkPreflightReport completed = running.completed(completedChecks);
            synchronized (this) {
                persist(completed);
            }
            return completed;
        });
    }

    public String getLoadError() {
        return loadError;
    }

    private synchronized void persist(NetworkPreflightReport report) {
        repository.save(report);
        reports.put(report.reportId(), report);
    }

    private String rootMessage(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null) {
            current = current.getCause();
        }
        return current.getMessage() == null ? current.getClass().getSimpleName() : current.getMessage();
    }
}
