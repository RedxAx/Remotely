package redxax.oxy.remotely.network;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class NetworkPortAllocatorTest {
    private final NetworkPortAllocator allocator = new NetworkPortAllocator();

    @Test
    void allocatesPerHostWithoutGlobalConflicts() {
        List<PortReservation> reservations = List.of(
            new PortReservation("local", 25565, "instance", "one", "One"),
            new PortReservation("ssh:node", 25565, "instance", "two", "Two")
        );

        assertEquals(25566, allocator.allocate("local", 25565, 25565, 25570, reservations));
        assertEquals(25566, allocator.allocate("ssh:node", 25565, 25565, 25570, reservations));
        assertEquals(25565, allocator.allocate("ssh:other", 25565, 25565, 25570, reservations));
    }

    @Test
    void reportsEveryReservationInAConflict() {
        List<PortReservation> reservations = List.of(
            new PortReservation("local", 25565, "instance", "one", "One"),
            new PortReservation("local", 25565, "network", "two", "Two"),
            new PortReservation("ssh:node", 25565, "instance", "three", "Three")
        );

        assertEquals(2, allocator.conflicts(reservations).size());
    }

    @Test
    void failsWhenRangeIsExhausted() {
        List<PortReservation> reservations = List.of(
            new PortReservation("local", 25565, "instance", "one", "One"),
            new PortReservation("local", 25566, "instance", "two", "Two")
        );

        assertThrows(IllegalStateException.class, () -> allocator.allocate("local", 25565, 25565, 25566, reservations));
    }
}
