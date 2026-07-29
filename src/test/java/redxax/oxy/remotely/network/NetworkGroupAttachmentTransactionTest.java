package redxax.oxy.remotely.network;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
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

        CompletableFuture<Void> transaction = NetworkGroupAttachmentTransaction.execute(List.of("first", "second", "third"), member -> {
            attached.add(member);
            return member.equals("third") ? CompletableFuture.failedFuture(failure) : CompletableFuture.completedFuture(true);
        }, member -> {
            detached.add(member);
            return CompletableFuture.completedFuture(null);
        });

        CompletionException thrown = assertThrows(CompletionException.class, transaction::join);

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
            return CompletableFuture.completedFuture(false);
        }, member -> CompletableFuture.completedFuture(null)).join();

        assertEquals(members.size(), attached.get());
    }
}
