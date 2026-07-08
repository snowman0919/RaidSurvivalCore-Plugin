package com.example.raidsurvivalcore.util;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Base64;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.io.BukkitObjectInputStream;
import org.bukkit.util.io.BukkitObjectOutputStream;

public final class ItemStackCodec {
    private ItemStackCodec() {
    }

    public static String encode(ItemStack item) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (BukkitObjectOutputStream out = new BukkitObjectOutputStream(bytes)) {
            out.writeObject(item);
        }
        return Base64.getEncoder().encodeToString(bytes.toByteArray());
    }

    public static ItemStack decode(String raw) throws IOException, ClassNotFoundException {
        byte[] bytes = Base64.getDecoder().decode(raw);
        try (BukkitObjectInputStream in = new BukkitObjectInputStream(new ByteArrayInputStream(bytes))) {
            return (ItemStack) in.readObject();
        }
    }
}
