package redxax.oxy.remotely.network;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NetworkJobRepositoryTest {
    @TempDir
    Path directory;

    @Test
    void roundTripsJobWithoutConfigurationContents() throws Exception {
        NetworkDefinition network = NetworkValidatorTest.validNetwork();
        NetworkReconciliationPlan plan = new NetworkReconciliationPlan("", network.networkId(), network.revision(), 0, List.of(), List.of());
        NetworkJobDocument document = new NetworkJobDocument(new NetworkConfigDocumentKey(network.proxyInstanceId(), "velocity.toml"), 0, true, "original-hash", "desired-hash", NetworkJobDocumentState.APPLIED);
        NetworkJob job = NetworkJob.create(plan, NetworkJobType.RECONCILE, "Test").prepared(List.of(document)).startingAttempt();
        NetworkJobRepository repository = new NetworkJobRepository(directory);

        repository.save(job);
        NetworkJob loaded = repository.loadAll().getFirst();

        assertEquals(job, loaded);
        String persisted = Files.readString(repository.getDirectory().resolve(job.jobId() + ".json"));
        assertFalse(persisted.contains("forwarding-secret"));
        assertTrue(persisted.contains("desired-hash"));
    }

    @Test
    void marksRunningJobInterruptedAfterRestart() {
        NetworkDefinition network = NetworkValidatorTest.validNetwork();
        NetworkReconciliationPlan plan = new NetworkReconciliationPlan("", network.networkId(), network.revision(), 0, List.of(), List.of());
        NetworkJob job = NetworkJob.create(plan, NetworkJobType.RECONCILE, "Test").prepared(List.of()).startingAttempt();
        new NetworkJobRepository(directory).save(job);

        NetworkJobManager manager = new NetworkJobManager(directory, new NetworkConfigurationTransaction());

        assertEquals(NetworkJobStatus.INTERRUPTED, manager.getJob(job.jobId()).orElseThrow().status());
    }

    @Test
    void persistsSecretRotationRecoveryReferencesWithoutSecretValue() throws Exception {
        NetworkDefinition network = NetworkValidatorTest.validNetwork();
        NetworkReconciliationPlan plan = new NetworkReconciliationPlan("", network.networkId(), network.revision() + 1, 0, List.of(), List.of());
        NetworkJob job = NetworkJob.create(plan, NetworkJobType.ROTATE_SECRET, "Security", Map.of("network", "candidate", "oldSecretReference", "old-reference", "newSecretReference", "new-reference"));
        NetworkJobRepository repository = new NetworkJobRepository(directory);

        repository.save(job);
        NetworkJob loaded = repository.loadAll().getFirst();

        assertEquals(NetworkJobType.ROTATE_SECRET, loaded.type());
        assertEquals("new-reference", loaded.context().get("newSecretReference"));
        assertFalse(Files.readString(repository.getDirectory().resolve(job.jobId() + ".json")).contains("generated-secret-value"));
    }
}
