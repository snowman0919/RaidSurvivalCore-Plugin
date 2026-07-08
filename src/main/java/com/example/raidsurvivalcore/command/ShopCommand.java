package com.example.raidsurvivalcore.command;

import com.example.raidsurvivalcore.economy.CurrencyReason;
import com.example.raidsurvivalcore.economy.EconomyService;
import com.example.raidsurvivalcore.shop.ShopEntry;
import com.example.raidsurvivalcore.shop.ShopService;
import java.util.List;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

public final class ShopCommand implements TabExecutor {
    private final JavaPlugin plugin;
    private final ShopService shop;
    private final EconomyService economy;

    public ShopCommand(JavaPlugin plugin, ShopService shop, EconomyService economy) {
        this.plugin = plugin;
        this.shop = shop;
        this.economy = economy;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("상점은 플레이어만 사용할 수 있습니다.");
            return true;
        }
        if (!shop.enabled()) {
            sender.sendMessage("상점이 비활성화되어 있습니다.");
            return true;
        }
        if (args.length == 0 || args[0].equalsIgnoreCase("list")) return list(player);
        if (args[0].equalsIgnoreCase("info") && args.length >= 2) return info(player, args[1]);
        if (args[0].equalsIgnoreCase("buy") && args.length >= 2) return buy(player, args[1], args.length >= 3 ? args[2] : "1");
        if (args[0].equalsIgnoreCase("sell") && args.length >= 2) return sell(player, args[1], args.length >= 3 ? args[2] : "1");
        sender.sendMessage("사용법: /shop list | /shop buy <품목> [횟수] | /shop sell <품목> [횟수] | /shop info <품목>");
        return true;
    }

    private boolean list(Player player) {
        StringBuilder out = new StringBuilder("상점 품목");
        for (ShopEntry entry : shop.entries()) {
            out.append('\n').append(entry.id())
                .append(" x").append(entry.amount())
                .append(" 구매=").append(entry.canBuy() ? entry.buyPrice() : "불가")
                .append(" 판매=").append(entry.canSell() ? entry.sellPrice() : "불가");
        }
        player.sendMessage(out.toString());
        return true;
    }

    private boolean info(Player player, String id) {
        ShopEntry entry = shop.entry(id);
        if (entry == null) {
            player.sendMessage("상점 품목을 찾을 수 없습니다: " + id);
            return true;
        }
        player.sendMessage(entry.id() + ": " + entry.material() + " x" + entry.amount() + ", 구매 " + (entry.canBuy() ? entry.buyPrice() : "불가") + ", 판매 " + (entry.canSell() ? entry.sellPrice() : "불가") + ", 최대 " + entry.maxPerTransaction() + "회");
        return true;
    }

    private boolean buy(Player player, String id, String rawCount) {
        ShopEntry entry = shop.entry(id);
        Integer count = parseCount(player, rawCount, entry);
        if (entry == null || count == null) return true;
        if (!entry.canBuy()) {
            player.sendMessage("이 품목은 구매할 수 없습니다.");
            return true;
        }
        ItemStack item = entry.item(count);
        if (player.getInventory().firstEmpty() == -1 && !canMerge(player, item)) {
            player.sendMessage("인벤토리에 공간이 부족합니다.");
            return true;
        }
        long cost = entry.buyPrice() * count;
        economy.adminAdjust(null, player.getUniqueId(), cost, CurrencyReason.SHOP_BUY, "remove").thenAccept(ok -> Bukkit.getScheduler().runTask(plugin, () -> {
            if (!ok) {
                player.sendMessage("구매 실패: Crown이 부족하거나 DB 오류가 발생했습니다.");
                return;
            }
            player.getInventory().addItem(item).values().forEach(left -> player.getWorld().dropItemNaturally(player.getLocation(), left));
            player.sendMessage(entry.id() + " " + count + "회 구매 완료: -" + cost + " Crown");
        }));
        return true;
    }

    private boolean sell(Player player, String id, String rawCount) {
        ShopEntry entry = shop.entry(id);
        Integer count = parseCount(player, rawCount, entry);
        if (entry == null || count == null) return true;
        if (!entry.canSell()) {
            player.sendMessage("이 품목은 판매할 수 없습니다.");
            return true;
        }
        int needed = entry.amount() * count;
        if (!hasItems(player, entry, needed)) {
            player.sendMessage("판매할 아이템이 부족합니다.");
            return true;
        }
        removeItems(player, entry, needed);
        long revenue = entry.sellPrice() * count;
        economy.award(player.getUniqueId(), revenue, CurrencyReason.SHOP_SELL).thenAccept(ok -> Bukkit.getScheduler().runTask(plugin, () -> {
            if (!ok) {
                player.getInventory().addItem(new ItemStack(entry.material(), needed)).values().forEach(left -> player.getWorld().dropItemNaturally(player.getLocation(), left));
                player.sendMessage("판매 실패: 보유 한도 또는 DB 오류로 아이템을 되돌렸습니다.");
                return;
            }
            player.sendMessage(entry.id() + " " + count + "회 판매 완료: +" + revenue + " Crown");
        }));
        return true;
    }

    private Integer parseCount(Player player, String raw, ShopEntry entry) {
        if (entry == null) {
            player.sendMessage("상점 품목을 찾을 수 없습니다.");
            return null;
        }
        try {
            int count = Integer.parseInt(raw);
            if (count <= 0 || count > entry.maxPerTransaction()) {
                player.sendMessage("거래 횟수는 1 이상 " + entry.maxPerTransaction() + " 이하로 입력하세요.");
                return null;
            }
            if ((long) entry.amount() * count > entry.material().getMaxStackSize() * 36L) {
                player.sendMessage("한 번에 처리할 아이템 수가 너무 많습니다.");
                return null;
            }
            return count;
        } catch (NumberFormatException e) {
            player.sendMessage("횟수는 숫자로 입력하세요: " + raw);
            return null;
        }
    }

    private boolean canMerge(Player player, ItemStack item) {
        for (ItemStack slot : player.getInventory().getStorageContents()) {
            if (slot != null && slot.isSimilar(item) && slot.getAmount() < slot.getMaxStackSize()) return true;
        }
        return false;
    }

    private boolean hasItems(Player player, ShopEntry entry, int needed) {
        int found = 0;
        for (ItemStack item : player.getInventory().getStorageContents()) {
            if (item == null || item.getType() != entry.material()) continue;
            found += item.getAmount();
            if (found >= needed) return true;
        }
        return false;
    }

    private void removeItems(Player player, ShopEntry entry, int needed) {
        ItemStack[] contents = player.getInventory().getStorageContents();
        int remaining = needed;
        for (int i = 0; i < contents.length && remaining > 0; i++) {
            ItemStack item = contents[i];
            if (item == null || item.getType() != entry.material()) continue;
            int take = Math.min(remaining, item.getAmount());
            item.setAmount(item.getAmount() - take);
            if (item.getAmount() <= 0) contents[i] = null;
            remaining -= take;
        }
        player.getInventory().setStorageContents(contents);
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) return filter(List.of("list", "info", "buy", "sell"), args[0]);
        if (args.length == 2 && List.of("info", "buy", "sell").contains(args[0].toLowerCase())) return filter(shop.entries().stream().map(ShopEntry::id).toList(), args[1]);
        return List.of();
    }

    private List<String> filter(List<String> options, String prefix) {
        String lower = prefix.toLowerCase();
        return options.stream().filter(option -> option.toLowerCase().startsWith(lower)).toList();
    }
}
