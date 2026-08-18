package redxax.oxy.remotely.network;

import org.junit.jupiter.api.Test;
import restudio.rebase.platform.Async;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class NetworkGroupAttachmentTransactionTest {
    @Test
    void laterFailureDetachesChangedMembersInReverseOrder() {
        List<String> attached = new ArrayList<>();
        List<String> detached = new ArrayList<>();
        IllegalStateException failure = new IllegalStateException("third failed");

        Async<Void> transaction = NetworkGroupAttachmentTransaction.execute(List.of("first", "second", "third"), member -> {
            attached.add(member);
            return member.equals("third") ? Async.failed(failure) : Async.completed(true);
        }, member -> {
            detached.add(member);
            return Async.completed(null);
        });

        IllegalStateException thrown = assertThrows(IllegalStateException.class, transaction::join);

        assertSame(failure, thrown.getCause());
        assertEquals(List.of("first", "second", "third"), attached);
        assertEquals(List.of("second", "first"), detached);
    }

    @Test
    void completedActionsDoNotGrowTheCallStack() {
        List<Integer> members = IntStream.range(0, 20_000).boxed().toList();
        AtomicInteger attached = new AtomicInteger();

        NetworkGroupAttachmentTransaction.execute(members, member -> {
            attached.incrementAndGet();
            return Async.completed(false);
        }, member -> Async.completed(null)).join();

        assertEquals(members.size(), attached.get());
    }
}
