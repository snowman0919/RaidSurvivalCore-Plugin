package com.example.raidsurvivalcore;

import com.example.raidsurvivalcore.bounty.AntiFarmingRules;
import com.example.raidsurvivalcore.bounty.BountyLedger;
import com.example.raidsurvivalcore.combat.CombatLogGrace;
import com.example.raidsurvivalcore.combat.CombatState;
import com.example.raidsurvivalcore.combat.CommandBlockRules;
import com.example.raidsurvivalcore.combat.HealingRules;
import com.example.raidsurvivalcore.model.PlayerPair;
import com.example.raidsurvivalcore.protection.PlaytimeAccumulator;
import com.example.raidsurvivalcore.proximity.ProximityRules;
import com.example.raidsurvivalcore.spawn.RandomSpawnRules;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.Set;
import java.util.SplittableRandom;
import java.util.UUID;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class CoreRulesTest {
    @Test
    void linearDamageCalculation() {
        double damage = ProximityRules.damage(Duration.ofSeconds(30), Duration.ofSeconds(20), Duration.ofSeconds(5), 1.0, 0.5, 8.0);
        assertEquals(2.0, damage);
    }

    @Test
    void maxDamageCap() {
        double damage = ProximityRules.damage(Duration.ofMinutes(5), Duration.ofSeconds(20), Duration.ofSeconds(5), 1.0, 0.5, 8.0);
        assertEquals(8.0, damage);
    }

    @Test
    void playerPairNormalizesOrder() {
        UUID a = UUID.fromString("00000000-0000-0000-0000-000000000002");
        UUID b = UUID.fromString("00000000-0000-0000-0000-000000000001");
        PlayerPair pair = new PlayerPair(a, b);
        assertEquals(b, pair.first());
        assertEquals(a, pair.second());
    }

    @Test
    void attackExemptionCalculation() {
        Instant now = Instant.parse("2026-07-06T00:00:10Z");
        assertTrue(ProximityRules.attackExempt(now.minusSeconds(10), Duration.ofSeconds(15), now));
        assertFalse(ProximityRules.attackExempt(now.minusSeconds(16), Duration.ofSeconds(15), now));
    }

    @Test
    void separatedStateResetCalculation() {
        Instant now = Instant.parse("2026-07-06T00:00:11Z");
        assertTrue(ProximityRules.shouldResetAfterSeparation(now.minusSeconds(10), Duration.ofSeconds(10), now));
    }

    @Test
    void combatTagExpires() {
        CombatState state = new CombatState(Instant.parse("2026-07-06T00:00:10Z"), null);
        assertTrue(state.active(Instant.parse("2026-07-06T00:00:09Z")));
        assertFalse(state.active(Instant.parse("2026-07-06T00:00:10Z")));
    }

    @Test
    void healingMultiplier() {
        assertEquals(3.0, HealingRules.adjustedAmount(6.0, 0.5));
        assertEquals(0.0, HealingRules.adjustedAmount(6.0, 0.0));
    }

    @Test
    void randomSpawnRadiusInRange() {
        SplittableRandom random = new SplittableRandom(1);
        for (int i = 0; i < 1000; i++) {
            RandomSpawnRules.Offset offset = RandomSpawnRules.uniformOffset(random, 500, 5000);
            assertTrue(offset.radius() >= 500);
            assertTrue(offset.radius() <= 5000);
        }
    }

    @Test
    void antiFarmingRewardDecrease() {
        assertEquals(1.0, AntiFarmingRules.rewardMultiplier(0, 2, 5, 0.5));
        assertEquals(0.5, AntiFarmingRules.rewardMultiplier(2, 2, 5, 0.5));
        assertEquals(0.0, AntiFarmingRules.rewardMultiplier(4, 2, 5, 0.5));
    }

    @Test
    void bountyAccumulatesAndPays() {
        BountyLedger ledger = new BountyLedger();
        UUID target = UUID.randomUUID();
        ledger.add(target, BigDecimal.TEN);
        ledger.add(target, BigDecimal.valueOf(15));
        assertEquals(BigDecimal.valueOf(25), ledger.get(target));
        assertEquals(0, BigDecimal.valueOf(12.5).compareTo(ledger.claim(target, 0.5)));
        assertEquals(BigDecimal.ZERO, ledger.get(target));
    }

    @Test
    void commandNamespaceBypassBlocked() {
        Set<String> blocked = Set.of("tp", "home");
        assertTrue(CommandBlockRules.blocked("  /minecraft:tp player 0 0 0", blocked, Set.of()));
        assertTrue(CommandBlockRules.blocked("/Essentials:HOME", blocked, Set.of()));
        assertFalse(CommandBlockRules.blocked("/msg hello", blocked, Set.of()));
    }

    @Test
    void playtimeAccumulatesActualSessionTime() {
        PlaytimeAccumulator acc = new PlaytimeAccumulator(Duration.ofMinutes(1));
        Instant start = Instant.parse("2026-07-06T00:00:00Z");
        acc.join(start);
        assertEquals(Duration.ofMinutes(6), acc.current(start.plus(Duration.ofMinutes(5))));
        acc.leave(start.plus(Duration.ofMinutes(5)));
        assertEquals(Duration.ofMinutes(6), acc.current(start.plus(Duration.ofMinutes(10))));
    }

    @Test
    void combatLogGraceExpiresAfterWindow() {
        Instant start = Instant.parse("2026-07-06T00:00:00Z");
        CombatLogGrace grace = new CombatLogGrace(start, Duration.ofSeconds(3));
        assertFalse(grace.expired(start.plusSeconds(2)));
        assertTrue(grace.expired(start.plusSeconds(3)));
    }
}
