package redxax.oxy.remotely.network;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NetworkPreflightRepositoryTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void recoversInterruptedPreflightAsFailedReport() {
        NetworkPreflightRepository repository = new NetworkPreflightRepository(temporaryDirectory);
        NetworkPreflightReport running = NetworkPreflightReport.running(NetworkValidatorTest.validNetwork());
        repository.save(running);

        NetworkPreflightManager manager = new NetworkPreflightManager(temporaryDirectory, null);
        NetworkPreflightReport recovered = manager.getReports().getFirst();

        assertEquals(NetworkPreflightStatus.FAILED, recovered.status());
        assertTrue(recovered.checks().stream().anyMatch(check -> check.id().equals("interrupted")));
    }
}
