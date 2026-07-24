package redxax.oxy.remotely.flow.ui.studio;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StudioMutationTransactionTest {
    @Test
    void commitsAllStepsInOrder() {
        List<String> events = new ArrayList<>();

        boolean committed = new StudioMutationTransaction()
            .add(() -> events.add("apply-one"), () -> events.add("rollback-one"))
            .add(() -> events.add("apply-two"), () -> events.add("rollback-two"))
            .execute();

        assertTrue(committed);
        assertEquals(List.of("apply-one", "apply-two"), events);
    }

    @Test
    void compensatesTheFailedStepAndEarlierStepsInReverseOrder() {
        List<String> events = new ArrayList<>();

        boolean committed = new StudioMutationTransaction()
            .add(() -> events.add("apply-one"), () -> events.add("rollback-one"))
            .add(() -> {
                events.add("apply-two");
                return false;
            }, () -> events.add("rollback-two"))
            .execute();

        assertFalse(committed);
        assertEquals(List.of("apply-one", "apply-two", "rollback-two", "rollback-one"), events);
    }
}
