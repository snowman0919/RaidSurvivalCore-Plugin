package com.example.raidsurvivalcore.model;

import org.bukkit.Location;

public record RegionBox(String world, double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {
    public boolean contains(Location location) {
        if (location == null || location.getWorld() == null) return false;
        if (!world.equalsIgnoreCase(location.getWorld().getName())) return false;
        double x = location.getX();
        double y = location.getY();
        double z = location.getZ();
        return x >= Math.min(minX, maxX) && x <= Math.max(minX, maxX)
            && y >= Math.min(minY, maxY) && y <= Math.max(minY, maxY)
            && z >= Math.min(minZ, maxZ) && z <= Math.max(minZ, maxZ);
    }
}
