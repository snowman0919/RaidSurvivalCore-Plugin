package com.example.raidsurvivalcore.auction;

import java.util.UUID;
import org.bukkit.inventory.ItemStack;

public record AuctionListing(long id, UUID sellerUuid, ItemStack item, long price, String status) {
}
