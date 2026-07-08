package com.example.raidsurvivalcore.auction;

import com.example.raidsurvivalcore.economy.EconomyRules;
import com.example.raidsurvivalcore.economy.EconomyService;
import com.example.raidsurvivalcore.persistence.DatabaseManager;
import com.example.raidsurvivalcore.util.ItemStackCodec;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Logger;
import org.bukkit.inventory.ItemStack;

public final class AuctionService {
    private final DatabaseManager database;
    private final EconomyService economy;
    private final Logger logger;

    public AuctionService(DatabaseManager database, EconomyService economy, Logger logger) {
        this.database = database;
        this.economy = economy;
        this.logger = logger;
    }

    public CompletableFuture<CreateResult> create(UUID seller, ItemStack item, long price) {
        return CompletableFuture.supplyAsync(() -> {
            if (price <= 0 || item == null || item.getType().isAir()) return CreateResult.failed("invalid");
            try (var c = database.connection(); PreparedStatement ps = c.prepareStatement("INSERT INTO auction_listings(seller_uuid,item_blob,price,status,created_at) VALUES(?,?,?,?,?)", Statement.RETURN_GENERATED_KEYS)) {
                ps.setString(1, seller.toString());
                ps.setString(2, ItemStackCodec.encode(item));
                ps.setLong(3, price);
                ps.setString(4, "ACTIVE");
                ps.setLong(5, Instant.now().toEpochMilli());
                ps.executeUpdate();
                try (ResultSet rs = ps.getGeneratedKeys()) {
                    return rs.next() ? CreateResult.success(rs.getLong(1)) : CreateResult.failed("no id");
                }
            } catch (Exception e) {
                logger.warning("RaidSurvivalCore auction create failed: " + e.getMessage());
                return CreateResult.failed("db");
            }
        }, database.executor());
    }

    public CompletableFuture<List<AuctionListing>> active(int limit) {
        return CompletableFuture.supplyAsync(() -> {
            List<AuctionListing> listings = new ArrayList<>();
            try (var c = database.connection(); PreparedStatement ps = c.prepareStatement("SELECT id,seller_uuid,item_blob,price,status FROM auction_listings WHERE status='ACTIVE' ORDER BY id DESC LIMIT ?")) {
                ps.setInt(1, Math.max(1, limit));
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) listings.add(read(rs));
                }
            } catch (Exception e) {
                logger.warning("RaidSurvivalCore auction list failed: " + e.getMessage());
            }
            return listings;
        }, database.executor());
    }

    public CompletableFuture<Optional<AuctionListing>> find(long id) {
        return CompletableFuture.supplyAsync(() -> {
            try (var c = database.connection(); PreparedStatement ps = c.prepareStatement("SELECT id,seller_uuid,item_blob,price,status FROM auction_listings WHERE id=?")) {
                ps.setLong(1, id);
                try (ResultSet rs = ps.executeQuery()) {
                    return rs.next() ? Optional.of(read(rs)) : Optional.<AuctionListing>empty();
                }
            } catch (Exception e) {
                logger.warning("RaidSurvivalCore auction lookup failed: " + e.getMessage());
                return Optional.empty();
            }
        }, database.executor());
    }

    public CompletableFuture<BuyResult> buy(UUID buyer, long id) {
        return CompletableFuture.supplyAsync(() -> {
            try (var c = database.connection()) {
                c.setAutoCommit(false);
                AuctionListing listing;
                try (PreparedStatement ps = c.prepareStatement("SELECT id,seller_uuid,item_blob,price,status FROM auction_listings WHERE id=?")) {
                    ps.setLong(1, id);
                    try (ResultSet rs = ps.executeQuery()) {
                        if (!rs.next()) {
                            c.rollback();
                            return BuyResult.failed("not found");
                        }
                        listing = read(rs);
                    }
                }
                if (!"ACTIVE".equals(listing.status()) || listing.sellerUuid().equals(buyer)) {
                    c.rollback();
                    return BuyResult.failed("not active");
                }
                String buyerAccount = economy.personalAccount(buyer);
                String sellerAccount = economy.personalAccount(listing.sellerUuid());
                economy.ensureAccount(c, buyerAccount, "PLAYER");
                economy.ensureAccount(c, sellerAccount, "PLAYER");
                long buyerBalance = economy.balanceSync(c, buyerAccount);
                long sellerBalance = economy.balanceSync(c, sellerAccount);
                economy.setBalance(c, buyerAccount, EconomyRules.checkedSubtract(buyerBalance, listing.price()));
                economy.setBalance(c, sellerAccount, EconomyRules.checkedAdd(sellerBalance, listing.price(), economy.settings().maxPersonalBalance()));
                economy.insertTx(c, UUID.randomUUID().toString(), buyer.toString(), buyerAccount, sellerAccount, listing.price(), "AUCTION_BUY", 0);
                try (PreparedStatement ps = c.prepareStatement("UPDATE auction_listings SET status='SOLD', buyer_uuid=?, sold_at=? WHERE id=? AND status='ACTIVE'")) {
                    ps.setString(1, buyer.toString());
                    ps.setLong(2, Instant.now().toEpochMilli());
                    ps.setLong(3, id);
                    if (ps.executeUpdate() == 0) {
                        c.rollback();
                        return BuyResult.failed("race");
                    }
                }
                c.commit();
                return BuyResult.success(listing);
            } catch (Exception e) {
                logger.warning("RaidSurvivalCore auction buy failed: " + e.getMessage());
                return BuyResult.failed("db");
            }
        }, database.executor());
    }

    public CompletableFuture<CancelResult> cancel(UUID seller, long id) {
        return CompletableFuture.supplyAsync(() -> {
            try (var c = database.connection()) {
                c.setAutoCommit(false);
                AuctionListing listing;
                try (PreparedStatement ps = c.prepareStatement("SELECT id,seller_uuid,item_blob,price,status FROM auction_listings WHERE id=?")) {
                    ps.setLong(1, id);
                    try (ResultSet rs = ps.executeQuery()) {
                        if (!rs.next()) {
                            c.rollback();
                            return CancelResult.failed("not found");
                        }
                        listing = read(rs);
                    }
                }
                if (!listing.sellerUuid().equals(seller) || !"ACTIVE".equals(listing.status())) {
                    c.rollback();
                    return CancelResult.failed("not owner");
                }
                try (PreparedStatement ps = c.prepareStatement("UPDATE auction_listings SET status='CANCELLED' WHERE id=? AND status='ACTIVE'")) {
                    ps.setLong(1, id);
                    if (ps.executeUpdate() == 0) {
                        c.rollback();
                        return CancelResult.failed("race");
                    }
                }
                c.commit();
                return CancelResult.success(listing);
            } catch (Exception e) {
                logger.warning("RaidSurvivalCore auction cancel failed: " + e.getMessage());
                return CancelResult.failed("db");
            }
        }, database.executor());
    }

    private AuctionListing read(ResultSet rs) throws Exception {
        return new AuctionListing(
            rs.getLong("id"),
            UUID.fromString(rs.getString("seller_uuid")),
            ItemStackCodec.decode(rs.getString("item_blob")),
            rs.getLong("price"),
            rs.getString("status")
        );
    }

    public record CreateResult(boolean success, long id, String reason) {
        public static CreateResult success(long id) {
            return new CreateResult(true, id, "");
        }

        public static CreateResult failed(String reason) {
            return new CreateResult(false, -1, reason);
        }
    }

    public record BuyResult(boolean success, AuctionListing listing, String reason) {
        public static BuyResult success(AuctionListing listing) {
            return new BuyResult(true, listing, "");
        }

        public static BuyResult failed(String reason) {
            return new BuyResult(false, null, reason);
        }
    }

    public record CancelResult(boolean success, AuctionListing listing, String reason) {
        public static CancelResult success(AuctionListing listing) {
            return new CancelResult(true, listing, "");
        }

        public static CancelResult failed(String reason) {
            return new CancelResult(false, null, reason);
        }
    }
}
