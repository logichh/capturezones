package com.logichh.capturezones;

import java.util.Objects;
import java.util.UUID;

public final class OwnerMember {
    private final UUID uniqueId;
    private final String name;

    public OwnerMember(UUID uniqueId, String name) {
        this.uniqueId = Objects.requireNonNull(uniqueId, "uniqueId");
        this.name = Objects.requireNonNull(name, "name").trim();
        if (this.name.isEmpty()) {
            throw new IllegalArgumentException("name cannot be blank");
        }
    }

    public UUID getUniqueId() {
        return uniqueId;
    }

    public String getName() {
        return name;
    }
}
