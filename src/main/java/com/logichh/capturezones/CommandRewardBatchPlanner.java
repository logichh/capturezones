package com.logichh.capturezones;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

final class CommandRewardBatchPlanner {
    private CommandRewardBatchPlanner() {
    }

    static <T, K> List<List<T>> plan(
        List<T> values,
        Function<T, K> keyFunction,
        int maxRecipients,
        int batchSize
    ) {
        int limit = Math.max(1, maxRecipients);
        int size = Math.max(1, batchSize);
        Map<K, T> unique = new LinkedHashMap<>();
        for (T value : values) {
            if (value == null) {
                continue;
            }
            unique.putIfAbsent(keyFunction.apply(value), value);
            if (unique.size() >= limit) {
                break;
            }
        }
        List<T> limited = new ArrayList<>(unique.values());
        List<List<T>> batches = new ArrayList<>();
        for (int offset = 0; offset < limited.size(); offset += size) {
            batches.add(new ArrayList<>(limited.subList(offset, Math.min(limited.size(), offset + size))));
        }
        return batches;
    }

    static boolean canExecute(String execution, boolean recipientOnline) {
        return !"PLAYER".equalsIgnoreCase(execution) || recipientOnline;
    }

    static boolean isCoolingDown(Long lastRun, long seconds, long now) {
        return seconds > 0L && lastRun != null && now - lastRun < seconds * 1000L;
    }
}
