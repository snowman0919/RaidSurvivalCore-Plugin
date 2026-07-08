package com.example.raidsurvivalcore.shop;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

public record ShopEntry(String id, Material material, int amount, long buyPrice, long sellPrice, int maxPerTransaction) {
    public boolean canBuy() {
        return buyPrice > 0;
    }

    public boolean canSell() {
        return sellPrice > 0;
    }

    public ItemStack item(int transactions) {
        return new ItemStack(material, amount * transactions);
    }
}
