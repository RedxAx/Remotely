package redxax.oxy.remotely.flow.ui.studio;

import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StudioHistoryTest {
    @Test
    void undoKeepsChangesRebasedFromOtherCollaborators() {
        AtomicInteger value = new AtomicInteger();
        StudioScreen.History<Integer> history = new StudioScreen.History<>(value::get, value::set, 20);
        history.capture();
        value.set(1);

        value.addAndGet(10);
        history.rebase(previous -> previous + 10);

        assertTrue(history.undo());
        assertEquals(10, value.get());
        assertTrue(history.redo());
        assertEquals(11, value.get());
    }
}
