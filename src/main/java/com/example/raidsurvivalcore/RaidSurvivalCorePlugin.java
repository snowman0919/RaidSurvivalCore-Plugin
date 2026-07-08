package com.example.raidsurvivalcore;

import com.example.raidsurvivalcore.bounty.BountyManager;
import com.example.raidsurvivalcore.api.RaidCoreEconomyApi;
import com.example.raidsurvivalcore.api.RaidCoreEconomyApiImpl;
import com.example.raidsurvivalcore.api.RaidCorePlayerInfoApi;
import com.example.raidsurvivalcore.api.RaidCorePlayerInfoApiImpl;
import com.example.raidsurvivalcore.auction.AuctionService;
import com.example.raidsurvivalcore.combat.CombatLogManager;
import com.example.raidsurvivalcore.combat.CombatManager;
import com.example.raidsurvivalcore.command.AuctionCommand;
import com.example.raidsurvivalcore.command.RaidCoreCommand;
import com.example.raidsurvivalcore.command.MoneyCommand;
import com.example.raidsurvivalcore.command.ShopCommand;
import com.example.raidsurvivalcore.command.TribeCommand;
import com.example.raidsurvivalcore.command.TradeCommand;
import com.example.raidsurvivalcore.chat.TribeChatState;
import com.example.raidsurvivalcore.config.ConfigService;
import com.example.raidsurvivalcore.config.MessageService;
import com.example.raidsurvivalcore.config.RaidCoreConfig;
import com.example.raidsurvivalcore.death.DeathLocationManager;
import com.example.raidsurvivalcore.listener.CombatListener;
import com.example.raidsurvivalcore.listener.HealingRestrictionListener;
import com.example.raidsurvivalcore.listener.PlayerLifecycleListener;
import com.example.raidsurvivalcore.listener.RestrictionListener;
import com.example.raidsurvivalcore.listener.WorldRuleListener;
import com.example.raidsurvivalcore.persistence.DatabaseManager;
import com.example.raidsurvivalcore.persistence.PlayerDataRepository;
import com.example.raidsurvivalcore.protection.NewPlayerProtectionManager;
import com.example.raidsurvivalcore.protection.RespawnProtectionManager;
import com.example.raidsurvivalcore.proximity.ProximityManager;
import com.example.raidsurvivalcore.shop.ShopService;
import com.example.raidsurvivalcore.spawn.RandomSpawnManager;
import com.example.raidsurvivalcore.task.ActionBarTask;
import com.example.raidsurvivalcore.economy.EconomyIncomeManager;
import com.example.raidsurvivalcore.economy.EconomySettings;
import com.example.raidsurvivalcore.economy.EconomyService;
import com.example.raidsurvivalcore.listener.TribeChatListener;
import com.example.raidsurvivalcore.territory.TerritoryService;
import com.example.raidsurvivalcore.tracker.TrackerManager;
import com.example.raidsurvivalcore.tribe.TribeService;
import com.example.raidsurvivalcore.util.ResourceInstaller;
import java.io.File;
import org.bukkit.Bukkit;
import org.bukkit.command.PluginCommand;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.event.HandlerList;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;

public final class RaidSurvivalCorePlugin extends JavaPlugin {
    private ConfigService configService;
    private MessageService messages;
    private DatabaseManager database;
    private PlayerDataRepository playerData;
    private CombatManager combat;
    private CombatLogManager combatLog;
    private NewPlayerProtectionManager newbie;
    private RespawnProtectionManager respawn;
    private ProximityManager proximity;
    private RandomSpawnManager randomSpawn;
    private DeathLocationManager deathLocation;
    private BountyManager bounty;
    private EconomyService economy;
    private EconomyIncomeManager economyIncome;
    private ShopService shop;
    private AuctionService auctions;
    private TribeService tribes;
    private TribeChatState tribeChat;
    private TerritoryService territory;
    private TrackerManager tracker;
    private ActionBarTask actionBars;
    private HealingRestrictionListener healingListener;
    private RestrictionListener restrictionListener;
    private PlayerLifecycleListener lifecycleListener;
    private WorldRuleListener worldRuleListener;
    private int cleanupTask = -1;
    private int autosaveTask = -1;

    @Override
    public void onEnable() {
        ResourceInstaller.saveIfMissing(this, "config.yml");
        ResourceInstaller.saveIfMissing(this, "tribes.yml");
        ResourceInstaller.saveIfMissing(this, "territory.yml");
        ResourceInstaller.saveIfMissing(this, "siege.yml");
        ResourceInstaller.saveIfMissing(this, "economy.yml");
        ResourceInstaller.saveIfMissing(this, "shop.yml");
        ResourceInstaller.saveIfMissing(this, "chat.yml");
        messages = new MessageService(this);
        messages.reload();
        configService = new ConfigService(this);
        configService.reload();
        RaidCoreConfig cfg = configService.get();
        if (!ResourceInstaller.verifyWritableDataPath(this, getLogger(), cfg.database().file())) {
            getLogger().severe("RaidSurvivalCore disabled because its own plugin data path is not writable. This plugin will not modify vanilla world player data files.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        database = new DatabaseManager(this);
        database.start(cfg.database().file());
        playerData = new PlayerDataRepository(database, getLogger(), cfg.bounty().internalStartingTokens());
        economy = new EconomyService(database, getLogger(), loadEconomySettings());
        economyIncome = new EconomyIncomeManager(this, economy);
        shop = new ShopService(this);
        shop.reload();
        auctions = new AuctionService(database, economy, getLogger());
        tribes = new TribeService(database, economy, getLogger(), 5000L);
        tribeChat = new TribeChatState();
        territory = new TerritoryService(database, getLogger());
        tribes.reloadSnapshot();
        territory.reloadSnapshot();
        combat = new CombatManager(cfg, messages);
        getServer().getServicesManager().register(RaidCoreEconomyApi.class, new RaidCoreEconomyApiImpl(economy), this, ServicePriority.Normal);
        getServer().getServicesManager().register(RaidCorePlayerInfoApi.class, new RaidCorePlayerInfoApiImpl(economy, tribes, combat), this, ServicePriority.Normal);
        combatLog = new CombatLogManager(this, combat, cfg);
        newbie = new NewPlayerProtectionManager(playerData, cfg);
        respawn = new RespawnProtectionManager(cfg);
        randomSpawn = new RandomSpawnManager(this, cfg, messages);
        deathLocation = new DeathLocationManager(playerData, messages, cfg);
        bounty = new BountyManager(database, economy, messages, getLogger(), cfg);
        proximity = new ProximityManager(this, cfg, combat, newbie, messages);
        proximity.setTribeService(tribes);
        randomSpawn.setTerritoryService(territory);
        tracker = new TrackerManager(this, cfg, newbie, playerData);
        actionBars = new ActionBarTask(this, combat, newbie, respawn, messages);
        registerEverything();
        getLogger().info("RaidSurvivalCore enabled.");
    }

    @Override
    public void onDisable() {
        if (combatLog != null) combatLog.markShutdown();
        getServer().getServicesManager().unregisterAll(this);
        stopTasks();
        HandlerList.unregisterAll(this);
        if (playerData != null) playerData.flushAll();
        if (database != null) database.shutdown();
        getLogger().info("RaidSurvivalCore disabled.");
    }

    public void reloadRaidCore() {
        stopTasks();
        HandlerList.unregisterAll(this);
        reloadConfig();
        messages.reload();
        configService.reload();
        RaidCoreConfig cfg = configService.get();
        combat.reload(cfg);
        combatLog.reload(cfg);
        newbie.reload(cfg);
        respawn.reload(cfg);
        randomSpawn.reload(cfg);
        deathLocation.reload(cfg);
        bounty.reload(cfg);
        economy.reload(loadEconomySettings());
        shop.reload();
        proximity.reload(cfg);
        tracker.reload(cfg);
        tribes.reloadSnapshot();
        territory.reloadSnapshot();
        if (healingListener != null) healingListener.reload(cfg);
        if (restrictionListener != null) restrictionListener.reload(cfg);
        if (lifecycleListener != null) lifecycleListener.reload(cfg);
        if (worldRuleListener != null) worldRuleListener.reload(cfg);
        registerEverything();
    }

    private void registerEverything() {
        RaidCoreConfig cfg = configService.get();
        proximity.start();
        tracker.start();
        actionBars.start(cfg.performance().actionbarTicks());
        economyIncome.start();
        cleanupTask = Bukkit.getScheduler().runTaskTimer(this, () -> {
            combat.cleanupAndNotify();
            respawn.cleanup();
        }, 20L, cfg.performance().cleanupInterval().toSeconds() * 20L).getTaskId();
        autosaveTask = Bukkit.getScheduler().runTaskTimerAsynchronously(this, playerData::saveDirty, cfg.database().autosave().toSeconds() * 20L, cfg.database().autosave().toSeconds() * 20L).getTaskId();

        healingListener = new HealingRestrictionListener(this, combat, cfg);
        restrictionListener = new RestrictionListener(combat, messages, cfg);
        lifecycleListener = new PlayerLifecycleListener(this, newbie, respawn, combatLog, randomSpawn, cfg);
        worldRuleListener = new WorldRuleListener(cfg);
        worldRuleListener.applyAll();
        Bukkit.getPluginManager().registerEvents(new CombatListener(combat, proximity, newbie, respawn, messages, deathLocation, bounty, tribes), this);
        Bukkit.getPluginManager().registerEvents(healingListener, this);
        Bukkit.getPluginManager().registerEvents(restrictionListener, this);
        Bukkit.getPluginManager().registerEvents(lifecycleListener, this);
        Bukkit.getPluginManager().registerEvents(worldRuleListener, this);
        Bukkit.getPluginManager().registerEvents(new TribeChatListener(tribes, tribeChat, getLogger()), this);
        Bukkit.getPluginManager().registerEvents(economyIncome, this);

        RaidCoreCommand executor = new RaidCoreCommand(this::reloadRaidCore, combat, proximity, randomSpawn, bounty, newbie, messages, economy);
        PluginCommand raidcore = getCommand("raidcore");
        if (raidcore != null) raidcore.setExecutor(executor);
        PluginCommand bountyCommand = getCommand("bounty");
        if (bountyCommand != null) bountyCommand.setExecutor(executor);
        TribeCommand tribeCommand = new TribeCommand(this, tribes, tribeChat, messages, combat);
        PluginCommand tribe = getCommand("tribe");
        if (tribe != null) tribe.setExecutor(tribeCommand);
        PluginCommand tc = getCommand("tc");
        if (tc != null) tc.setExecutor(tribeCommand);
        MoneyCommand moneyCommand = new MoneyCommand(this, economy, messages);
        PluginCommand money = getCommand("money");
        if (money != null) money.setExecutor(moneyCommand);
        ShopCommand shopCommand = new ShopCommand(this, shop, economy);
        PluginCommand shop = getCommand("shop");
        if (shop != null) shop.setExecutor(shopCommand);
        TradeCommand tradeCommand = new TradeCommand(this, economy);
        PluginCommand trade = getCommand("trade");
        if (trade != null) trade.setExecutor(tradeCommand);
        AuctionCommand auctionCommand = new AuctionCommand(this, auctions);
        PluginCommand auction = getCommand("auction");
        if (auction != null) auction.setExecutor(auctionCommand);
        PluginCommand ah = getCommand("ah");
        if (ah != null) ah.setExecutor(auctionCommand);
    }

    private void stopTasks() {
        if (proximity != null) proximity.stop();
        if (tracker != null) tracker.stop();
        if (actionBars != null) actionBars.stop();
        if (economyIncome != null) economyIncome.stop();
        if (cleanupTask != -1) Bukkit.getScheduler().cancelTask(cleanupTask);
        if (autosaveTask != -1) Bukkit.getScheduler().cancelTask(autosaveTask);
        cleanupTask = -1;
        autosaveTask = -1;
    }

    private EconomySettings loadEconomySettings() {
        ResourceInstaller.saveIfMissing(this, "economy.yml");
        YamlConfiguration yml = YamlConfiguration.loadConfiguration(new File(getDataFolder(), "economy.yml"));
        long maxPersonal = Math.max(0, yml.getLong("currency.max-personal-balance", 9_000_000_000_000L));
        double tax = Math.max(0.0, Math.min(1.0, yml.getDouble("currency.pay-tax-rate", 0.02)));
        long starting = Math.max(0, yml.getLong("currency.new-account-starting-balance", 5000L));
        long hourlyMin = Math.max(0, yml.getLong("income.hourly-general-target-min", 300L));
        long hourlyMax = Math.max(hourlyMin, yml.getLong("income.hourly-general-target-max", 800L));
        long playtimeInterval = Math.max(1, yml.getLong("income.playtime-interval-minutes", 20L));
        long mobMin = Math.max(0, yml.getLong("income.mob-low-roll-min", 1L));
        long mobMax = Math.max(mobMin, yml.getLong("income.mob-low-roll-max", 5L));
        long advancementTask = Math.max(0, yml.getLong("income.advancement-task-reward", 100L));
        long advancementGoal = Math.max(0, yml.getLong("income.advancement-goal-reward", 300L));
        long advancementChallenge = Math.max(0, yml.getLong("income.advancement-challenge-reward", 1000L));
        return new EconomySettings(maxPersonal, tax, starting, hourlyMin, hourlyMax, playtimeInterval, mobMin, mobMax, advancementTask, advancementGoal, advancementChallenge);
    }
}
