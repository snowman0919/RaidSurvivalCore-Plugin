package com.example.raidsurvivalcore.territory;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class TerritoryIndex {
    private final Map<UUID, Map<Long, List<TribeCore>>> byWorldChunk = new HashMap<>();

    public static long chunkKey(int chunkX, int chunkZ) {
        return (((long) chunkX) << 32) ^ (chunkZ & 0xffffffffL);
    }

    public void rebuild(Collection<TribeCore> cores) {
        byWorldChunk.clear();
        for (TribeCore core : cores) add(core);
    }

    public void add(TribeCore core) {
        int minChunkX = Math.floorDiv(core.x() - core.radius(), 16);
        int maxChunkX = Math.floorDiv(core.x() + core.radius(), 16);
        int minChunkZ = Math.floorDiv(core.z() - core.radius(), 16);
        int maxChunkZ = Math.floorDiv(core.z() + core.radius(), 16);
        Map<Long, List<TribeCore>> world = byWorldChunk.computeIfAbsent(core.worldUuid(), k -> new HashMap<>());
        for (int cx = minChunkX; cx <= maxChunkX; cx++) {
            for (int cz = minChunkZ; cz <= maxChunkZ; cz++) {
                world.computeIfAbsent(chunkKey(cx, cz), k -> new ArrayList<>()).add(core);
            }
        }
    }

    public List<TribeCore> candidates(UUID worldUuid, int blockX, int blockZ) {
        Map<Long, List<TribeCore>> world = byWorldChunk.get(worldUuid);
        if (world == null) return List.of();
        return world.getOrDefault(chunkKey(Math.floorDiv(blockX, 16), Math.floorDiv(blockZ, 16)), List.of());
    }

    public List<TribeCore> coresAt(UUID worldUuid, int blockX, int blockZ) {
        List<TribeCore> result = new ArrayList<>();
        for (TribeCore core : candidates(worldUuid, blockX, blockZ)) {
            long dx = (long) blockX - core.x();
            long dz = (long) blockZ - core.z();
            if (dx * dx + dz * dz <= (long) core.radius() * core.radius()) result.add(core);
        }
        return result;
    }
}
