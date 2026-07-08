package com.example.raidsurvivalcore.api;

import com.example.raidsurvivalcore.combat.CombatManager;
import com.example.raidsurvivalcore.economy.EconomyService;
import com.example.raidsurvivalcore.tribe.Tribe;
import com.example.raidsurvivalcore.tribe.TribeMember;
import com.example.raidsurvivalcore.tribe.TribeService;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public final class RaidCorePlayerInfoApiImpl implements RaidCorePlayerInfoApi {
    private final EconomyService economy;
    private final TribeService tribes;
    private final CombatManager combat;

    public RaidCorePlayerInfoApiImpl(EconomyService economy, TribeService tribes, CombatManager combat) {
        this.economy = economy;
        this.tribes = tribes;
        this.combat = combat;
    }

    @Override
    public CompletableFuture<RaidCorePlayerInfo> info(UUID player) {
        return economy.balance(player).thenApply(balance -> {
            Optional<TribeMember> member = tribes.snapshot().member(player);
            Optional<Tribe> tribe = member.flatMap(m -> tribes.snapshot().tribe(m.tribeId()));
            return new RaidCorePlayerInfo(
                balance,
                tribe.map(Tribe::name).orElse("없음"),
                tribe.map(Tribe::tag).orElse("-"),
                member.map(m -> m.role().name()).orElse("-"),
                combat.isTagged(player),
                combat.remainingSeconds(player)
            );
        });
    }
}
