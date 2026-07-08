package com.example.raidsurvivalcore.command;

import com.example.raidsurvivalcore.auction.AuctionListing;
import com.example.raidsurvivalcore.auction.AuctionService;
import java.util.List;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

public final class AuctionCommand implements TabExecutor {
    private final JavaPlugin plugin;
    private final AuctionService auctions;

    public AuctionCommand(JavaPlugin plugin, AuctionService auctions) {
        this.plugin = plugin;
        this.auctions = auctions;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("경매장은 플레이어만 사용할 수 있습니다.");
            return true;
        }
        if (args.length == 0 || args[0].equalsIgnoreCase("list")) return list(player);
        return switch (args[0].toLowerCase()) {
            case "sell" -> sell(player, args);
            case "buy" -> buy(player, args);
            case "cancel" -> cancel(player, args);
            case "info" -> info(player, args);
            default -> usage(player);
        };
    }

    private boolean list(Player player) {
        auctions.active(10).thenAccept(listings -> Bukkit.getScheduler().runTask(plugin, () -> {
            if (listings.isEmpty()) {
                player.sendMessage("등록된 경매가 없습니다.");
                return;
            }
            StringBuilder out = new StringBuilder("경매장 목록");
            for (AuctionListing listing : listings) {
                OfflinePlayer seller = Bukkit.getOfflinePlayer(listing.sellerUuid());
                out.append('\n')
                    .append("#").append(listing.id()).append(" ")
                    .append(listing.item().getType()).append(" x").append(listing.item().getAmount())
                    .append(" | ").append(listing.price()).append(" Crown")
                    .append(" | 판매자: ").append(seller.getName() == null ? listing.sellerUuid() : seller.getName());
            }
            player.sendMessage(out.toString());
        }));
        return true;
    }

    private boolean info(Player player, String[] args) {
        if (args.length < 2) return usage(player);
        Long id = parseLong(player, args[1]);
        if (id == null) return true;
        auctions.find(id).thenAccept(found -> Bukkit.getScheduler().runTask(plugin, () -> {
            if (found.isEmpty()) {
                player.sendMessage("경매를 찾을 수 없습니다.");
                return;
            }
            AuctionListing listing = found.get();
            OfflinePlayer seller = Bukkit.getOfflinePlayer(listing.sellerUuid());
            player.sendMessage("#" + listing.id() + " " + listing.item().getType() + " x" + listing.item().getAmount() + ", " + listing.price() + " Crown, 상태=" + listing.status() + ", 판매자=" + (seller.getName() == null ? listing.sellerUuid() : seller.getName()));
        }));
        return true;
    }

    private boolean sell(Player player, String[] args) {
        if (args.length < 2) return usage(player);
        Long price = parseLong(player, args[1]);
        if (price == null) return true;
        int amount = args.length >= 3 ? parseInt(player, args[2], 64) : player.getInventory().getItemInMainHand().getAmount();
        if (amount <= 0) return true;
        ItemStack hand = player.getInventory().getItemInMainHand();
        if (hand.getType().isAir() || hand.getAmount() < amount) {
            player.sendMessage("경매에 올릴 아이템을 손에 들고 수량을 확인하세요.");
            return true;
        }
        ItemStack listingItem = hand.clone();
        listingItem.setAmount(amount);
        hand.setAmount(hand.getAmount() - amount);
        player.getInventory().setItemInMainHand(hand.getAmount() <= 0 ? null : hand);
        auctions.create(player.getUniqueId(), listingItem, price).thenAccept(result -> Bukkit.getScheduler().runTask(plugin, () -> {
            if (!result.success()) {
                player.getInventory().addItem(listingItem).values().forEach(left -> player.getWorld().dropItemNaturally(player.getLocation(), left));
                player.sendMessage("경매 등록 실패: 아이템을 되돌렸습니다.");
                return;
            }
            player.sendMessage("경매 등록 완료: #" + result.id() + " " + listingItem.getType() + " x" + listingItem.getAmount() + " / " + price + " Crown");
        }));
        return true;
    }

    private boolean buy(Player player, String[] args) {
        if (args.length < 2) return usage(player);
        Long id = parseLong(player, args[1]);
        if (id == null) return true;
        auctions.buy(player.getUniqueId(), id).thenAccept(result -> Bukkit.getScheduler().runTask(plugin, () -> {
            if (!result.success()) {
                player.sendMessage("구매 실패: 잔액 부족, 판매 완료, 자기 경매 구매, 또는 DB 오류입니다.");
                return;
            }
            AuctionListing listing = result.listing();
            player.getInventory().addItem(listing.item()).values().forEach(left -> player.getWorld().dropItemNaturally(player.getLocation(), left));
            player.sendMessage("경매 #" + listing.id() + " 구매 완료: -" + listing.price() + " Crown");
            Player seller = Bukkit.getPlayer(listing.sellerUuid());
            if (seller != null) seller.sendMessage("경매 #" + listing.id() + " 판매 완료: +" + listing.price() + " Crown");
        }));
        return true;
    }

    private boolean cancel(Player player, String[] args) {
        if (args.length < 2) return usage(player);
        Long id = parseLong(player, args[1]);
        if (id == null) return true;
        auctions.cancel(player.getUniqueId(), id).thenAccept(result -> Bukkit.getScheduler().runTask(plugin, () -> {
            if (!result.success()) {
                player.sendMessage("취소 실패: 본인의 활성 경매인지 확인하세요.");
                return;
            }
            player.getInventory().addItem(result.listing().item()).values().forEach(left -> player.getWorld().dropItemNaturally(player.getLocation(), left));
            player.sendMessage("경매 #" + result.listing().id() + "를 취소하고 아이템을 회수했습니다.");
        }));
        return true;
    }

    private Long parseLong(Player player, String raw) {
        try {
            long value = Long.parseLong(raw);
            if (value <= 0) {
                player.sendMessage("값은 1 이상이어야 합니다.");
                return null;
            }
            return value;
        } catch (NumberFormatException e) {
            player.sendMessage("숫자로 입력하세요: " + raw);
            return null;
        }
    }

    private int parseInt(Player player, String raw, int max) {
        Long value = parseLong(player, raw);
        if (value == null) return -1;
        if (value > max) {
            player.sendMessage("수량은 " + max + " 이하로 입력하세요.");
            return -1;
        }
        return value.intValue();
    }

    private boolean usage(Player player) {
        player.sendMessage("사용법: /auction list | /auction sell <가격> [수량] | /auction buy <번호> | /auction cancel <번호> | /auction info <번호>");
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) return filter(List.of("list", "sell", "buy", "cancel", "info"), args[0]);
        return List.of();
    }

    private List<String> filter(List<String> options, String prefix) {
        String lower = prefix.toLowerCase();
        return options.stream().filter(option -> option.startsWith(lower)).toList();
    }
}
