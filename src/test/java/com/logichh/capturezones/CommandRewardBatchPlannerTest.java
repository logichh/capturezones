package com.logichh.capturezones;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CommandRewardBatchPlannerTest {
    @Test
    void removesDuplicatesBeforeApplyingTheRecipientLimit() {
        List<Recipient> recipients = List.of(
            new Recipient("one"),
            new Recipient("one"),
            new Recipient("two"),
            new Recipient("three")
        );

        List<List<Recipient>> batches = CommandRewardBatchPlanner.plan(
            recipients,
            Recipient::key,
            2,
            25
        );

        assertEquals(1, batches.size());
        assertEquals(List.of("one", "two"), batches.get(0).stream().map(Recipient::key).toList());
    }

    @Test
    void splitsRecipientsIntoOneBatchPerConfiguredSize() {
        List<Recipient> recipients = List.of(
            new Recipient("one"),
            new Recipient("two"),
            new Recipient("three"),
            new Recipient("four"),
            new Recipient("five")
        );

        List<List<Recipient>> batches = CommandRewardBatchPlanner.plan(
            recipients,
            Recipient::key,
            10,
            2
        );

        assertEquals(List.of(2, 2, 1), batches.stream().map(List::size).toList());
    }

    @Test
    void playerExecutionRejectsOfflineRecipientsButConsoleExecutionAllowsThem() {
        assertFalse(CommandRewardBatchPlanner.canExecute("PLAYER", false));
        assertTrue(CommandRewardBatchPlanner.canExecute("PLAYER", true));
        assertTrue(CommandRewardBatchPlanner.canExecute("CONSOLE", false));
    }

    @Test
    void cooldownExpiresAtTheConfiguredBoundary() {
        long now = 20_000L;
        assertTrue(CommandRewardBatchPlanner.isCoolingDown(15_001L, 5L, now));
        assertFalse(CommandRewardBatchPlanner.isCoolingDown(15_000L, 5L, now));
        assertFalse(CommandRewardBatchPlanner.isCoolingDown(null, 5L, now));
        assertFalse(CommandRewardBatchPlanner.isCoolingDown(19_999L, 0L, now));
    }

    private record Recipient(String key) {
    }
}
