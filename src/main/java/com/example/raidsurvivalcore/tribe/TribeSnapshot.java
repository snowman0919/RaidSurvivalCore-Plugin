package com.example.raidsurvivalcore.tribe;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public record TribeSnapshot(Map<UUID, TribeMember> membersByPlayer, Map<Long, Tribe> tribesById, Map<String, Long> normalizedNameToId) {
    public Optional<TribeMember> member(UUID uuid) {
        return Optional.ofNullable(membersByPlayer.get(uuid));
    }

    public Optional<Tribe> tribe(long id) {
        return Optional.ofNullable(tribesById.get(id));
    }

    public boolean sameTribe(UUID a, UUID b) {
        TribeMember ma = membersByPlayer.get(a);
        TribeMember mb = membersByPlayer.get(b);
        return ma != null && mb != null && ma.tribeId() == mb.tribeId();
    }
}
