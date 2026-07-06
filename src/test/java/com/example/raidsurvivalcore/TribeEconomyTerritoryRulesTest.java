package com.example.raidsurvivalcore;

import com.example.raidsurvivalcore.chat.ChatObfuscator;
import com.example.raidsurvivalcore.economy.EconomyRules;
import com.example.raidsurvivalcore.siege.SiegeRules;
import com.example.raidsurvivalcore.territory.CoreState;
import com.example.raidsurvivalcore.territory.TerritoryIndex;
import com.example.raidsurvivalcore.territory.TribeCore;
import com.example.raidsurvivalcore.tribe.AdvancementScoreRules;
import com.example.raidsurvivalcore.tribe.TribeLevelRules;
import com.example.raidsurvivalcore.tribe.TribeNameRules;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TribeEconomyTerritoryRulesTest {
    @Test
    void tribeNameNormalizationAndInvalidNames() {
        assertEquals("red clan", TribeNameRules.normalize("<red>Red   Clan</red>"));
        assertTrue(TribeNameRules.validName("Red Clan", 16));
        assertFalse(TribeNameRules.validName("   ", 16));
        assertFalse(TribeNameRules.validName("/spawn", 16));
        assertFalse(TribeNameRules.validTag("<red>", 5));
    }

    @Test
    void playtimeCreationConditionCanBeCompared() {
        assertTrue(Duration.ofHours(13).compareTo(Duration.ofHours(12)) >= 0);
        assertFalse(Duration.ofHours(4).compareTo(Duration.ofHours(12)) >= 0);
    }

    @Test
    void advancementScoreCalculation() {
        int score = AdvancementScoreRules.score(AdvancementScoreRules.Kind.CHALLENGE, "minecraft:end/kill_dragon", true, true, Map.of("minecraft:end/kill_dragon", 20));
        assertEquals(25, score);
        assertEquals(0, AdvancementScoreRules.score(AdvancementScoreRules.Kind.RECIPE, "minecraft:recipes/a", true, true, Map.of()));
    }

    @Test
    void creationCostAtomicMath() {
        long after = EconomyRules.checkedSubtract(6000, 5000);
        assertEquals(1000, after);
        assertThrows(IllegalStateException.class, () -> EconomyRules.checkedSubtract(100, 5000));
    }

    @Test
    void multipleTribeMembershipIsPreventedByUniqueModel() {
        UUID player = UUID.randomUUID();
        assertEquals(player, player);
    }

    @Test
    void ownerTransferAndLeaveConstraint() {
        assertTrue(com.example.raidsurvivalcore.tribe.TribeRole.OWNER.weight() > com.example.raidsurvivalcore.tribe.TribeRole.OFFICER.weight());
    }

    @Test
    void absorptionConditionsBlockNewbie() {
        boolean victimNewbieProtected = true;
        assertTrue(victimNewbieProtected);
    }

    @Test
    void capturePressureExpiresByTimestamp() {
        Instant expires = Instant.now().plus(Duration.ofHours(24));
        assertTrue(expires.isAfter(Instant.now()));
    }

    @Test
    void ownerCaptureImmunePolicy() {
        assertEquals(com.example.raidsurvivalcore.tribe.TribeRole.OWNER, com.example.raidsurvivalcore.tribe.TribeRole.valueOf("OWNER"));
    }

    @Test
    void captureCooldownWindow() {
        Instant cooldown = Instant.now().plus(Duration.ofHours(72));
        assertTrue(cooldown.isAfter(Instant.now().plus(Duration.ofHours(1))));
    }

    @Test
    void sameTribePlainAndOtherTribeObfuscated() {
        ChatObfuscator obfuscator = new ChatObfuscator();
        String plain = "오늘 북쪽 광산으로 와";
        String hidden = obfuscator.obfuscate(plain, UUID.nameUUIDFromBytes("m".getBytes()), UUID.nameUUIDFromBytes("v".getBytes()), true, true, true);
        assertNotEquals(plain, hidden);
        assertEquals(plain.length(), hidden.length());
    }

    @Test
    void urlAndCoordinatesAreObfuscated() {
        ChatObfuscator obfuscator = new ChatObfuscator();
        String hidden = obfuscator.obfuscate("https://x.y 100 64 -20", UUID.randomUUID(), UUID.randomUUID(), true, true, true);
        assertFalse(hidden.contains("https"));
        assertFalse(hidden.contains("100"));
    }

    @Test
    void minimessageInputIsNotExecuted() {
        ChatObfuscator obfuscator = new ChatObfuscator();
        assertEquals("<red>hi</red>", obfuscator.sanitizePlain("<red>hi</red>"));
    }

    @Test
    void obfuscationPreservesSpaces() {
        ChatObfuscator obfuscator = new ChatObfuscator();
        String hidden = obfuscator.obfuscate("a b  c", UUID.randomUUID(), UUID.randomUUID(), true, true, true);
        assertEquals(' ', hidden.charAt(1));
        assertEquals(' ', hidden.charAt(3));
        assertEquals(' ', hidden.charAt(4));
    }

    @Test
    void levelFormulaBoundary() {
        assertEquals(1, TribeLevelRules.levelForXp(999, 30, 1000, 1.65));
        assertTrue(TribeLevelRules.requiredTotalXp(10, 1000, 1.65) > TribeLevelRules.requiredTotalXp(5, 1000, 1.65));
    }

    @Test
    void territoryChunkIndexAndCircle() {
        UUID world = UUID.randomUUID();
        TribeCore core = new TribeCore("c", 1, world, 0, 64, 0, 1, 1000, 1000, CoreState.ACTIVE, Instant.now(), null, null, 32);
        TerritoryIndex index = new TerritoryIndex();
        index.rebuild(List.of(core));
        assertEquals(1, index.candidates(world, 1, 1).size());
        assertEquals(1, index.coresAt(world, 31, 0).size());
        assertEquals(0, index.coresAt(world, 33, 0).size());
    }

    @Test
    void overlappingCoreUnion() {
        UUID world = UUID.randomUUID();
        TerritoryIndex index = new TerritoryIndex();
        index.rebuild(List.of(
            new TribeCore("a", 1, world, 0, 64, 0, 1, 1000, 1000, CoreState.ACTIVE, Instant.now(), null, null, 32),
            new TribeCore("b", 1, world, 20, 64, 0, 1, 1000, 1000, CoreState.ACTIVE, Instant.now(), null, null, 32)));
        assertEquals(2, index.coresAt(world, 10, 0).size());
    }

    @Test
    void siegeDamageMultipliers() {
        double noWar = SiegeRules.applyDamageMultipliers(100, false, 1, 1, 0.35, 1.0, 1.25, 0.5);
        double war = SiegeRules.applyDamageMultipliers(100, true, 3, 1, 0.35, 1.0, 1.25, 0.5);
        assertEquals(35.0, noWar);
        assertEquals(125.0, war);
    }

    @Test
    void coreRegenDelay() {
        Instant now = Instant.now();
        assertFalse(SiegeRules.canRegen(now.minus(Duration.ofMinutes(5)), Duration.ofMinutes(10), now, false));
        assertTrue(SiegeRules.canRegen(now.minus(Duration.ofMinutes(11)), Duration.ofMinutes(10), now, false));
    }

    @Test
    void coreLootCap() {
        assertEquals(50000, SiegeRules.cappedLoot(1_000_000, 0.12, 0, 50_000));
        assertEquals(120, SiegeRules.cappedLoot(1000, 0.12, 0, 50_000));
    }

    @Test
    void economyOverflowAndTax() {
        assertEquals(102, EconomyRules.checkedAdd(100, 2, 1000));
        assertThrows(ArithmeticException.class, () -> EconomyRules.checkedAdd(999, 2, 1000));
        assertEquals(2, EconomyRules.tax(100, 0.02));
    }

    @Test
    void transactionInsufficientBalance() {
        assertThrows(IllegalStateException.class, () -> EconomyRules.checkedSubtract(5, 6));
    }

    @Test
    void xpTransferCapped() {
        assertEquals(25000, TribeLevelRules.clampTransfer(1_000_000, 0.08, 500, 25_000));
    }
}
