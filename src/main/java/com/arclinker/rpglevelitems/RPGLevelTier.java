package com.arclinker.rpglevelitems;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.List;
import java.util.Collection;
import org.bukkit.plugin.java.JavaPlugin;

import com.arclinker.rpglevelitems.gui.GUIListener;
import com.arclinker.rpglevelitems.gui.ItemListGUI;
import com.arclinker.rpglevelitems.itemcreator.RlvpHandler;
import com.arclinker.rpglevelitems.ability.AbilityGUI;
import com.arclinker.rpglevelitems.ability.AbilityItems;
import com.arclinker.rpglevelitems.ability.AbilityListener;
import com.arclinker.rpglevelitems.ability.AbilityManager;
import com.arclinker.rpglevelitems.rank.PlayerStore;
import com.arclinker.rpglevelitems.rank.RankManager;



public class RPGLevelTier extends JavaPlugin {

    /** Espacio de nombres FIJO de las claves de ítems/recetas: no cambia aunque cambie el nombre del plugin (si no, los ítems existentes perderían sus datos). */
    public static final String KEY_NS = "rpgleveltier";
    private static RPGLevelTier instance;
    private ItemProgressionManager manager;
    private AbilityManager abilityManager;
    private AbilityItems abilityItems;
    private AbilityGUI abilityGUI;
    private ClassLevelManager classLevelManager;
    private ClassManager classManager;

// New managers
    private GearSetManager gearSetManager;
    private SubClassManager subClassManager;
    private JobManager jobManager;
    private RarityManager rarityManager;
    private DailyQuestManager dailyQuestManager;
    private LanguageManager languageManager;
    private RlvpHandler rlvpHandler;
    private ItemListGUI itemListGUI;
    private PlayerStore playerStore;
    private RankManager rankManager;
    private com.arclinker.rpglevelitems.station.StationManager stationManager;
    private com.arclinker.rpglevelitems.station.StationGUI stationGUI;
    private com.arclinker.rpglevelitems.utility.UtilityManager utility;
    private com.arclinker.rpglevelitems.itemcreator.RlvpBlockListener rlvpBlocks;
    private RecipeManager recipeManager;
    private com.arclinker.rpglevelitems.stats.StatEngine statEngine;
    private com.arclinker.rpglevelitems.stats.AffixManager affixManager;
    private com.arclinker.rpglevelitems.stats.GemManager gemManager;
    public com.arclinker.rpglevelitems.stats.StatEngine getStatEngine() { return statEngine; }
    public com.arclinker.rpglevelitems.stats.AffixManager getAffixManager() { return affixManager; }
    public com.arclinker.rpglevelitems.stats.GemManager getGemManager() { return gemManager; }
    private com.arclinker.rpglevelitems.economy.EconomyManager economy;
    private com.arclinker.rpglevelitems.mana.ManaManager mana;
    private com.arclinker.rpglevelitems.combat.StatusManager statuses;
    private com.arclinker.rpglevelitems.combat.CombatEngine combat;
    private com.arclinker.rpglevelitems.combat.HotbarListener hotbar;
    private com.arclinker.rpglevelitems.zones.ZoneManager zones;
    private com.arclinker.rpglevelitems.social.PartyManager parties;
    private com.arclinker.rpglevelitems.social.DuelManager duels;
    public com.arclinker.rpglevelitems.economy.EconomyManager getEconomy() { return economy; }
    public com.arclinker.rpglevelitems.mana.ManaManager getMana() { return mana; }
    public com.arclinker.rpglevelitems.combat.StatusManager getStatuses() { return statuses; }
    public com.arclinker.rpglevelitems.combat.CombatEngine getCombat() { return combat; }
    public com.arclinker.rpglevelitems.combat.HotbarListener getHotbar() { return hotbar; }
    public com.arclinker.rpglevelitems.zones.ZoneManager getZones() { return zones; }
    public com.arclinker.rpglevelitems.social.PartyManager getParties() { return parties; }
    public com.arclinker.rpglevelitems.social.DuelManager getDuels() { return duels; }
    private com.arclinker.rpglevelitems.world.MobScaler mobs;
    private com.arclinker.rpglevelitems.world.EventManager events;
    private com.arclinker.rpglevelitems.world.BossManager bosses;
    private com.arclinker.rpglevelitems.world.DungeonManager dungeons;
    private com.arclinker.rpglevelitems.loot.LootManager loot;
    public com.arclinker.rpglevelitems.world.MobScaler getMobs() { return mobs; }
    public com.arclinker.rpglevelitems.world.EventManager getEvents() { return events; }
    public com.arclinker.rpglevelitems.world.BossManager getBosses() { return bosses; }
    public com.arclinker.rpglevelitems.world.DungeonManager getDungeons() { return dungeons; }
    public com.arclinker.rpglevelitems.loot.LootManager getLoot() { return loot; }
    private com.arclinker.rpglevelitems.editor.EditorGUI editor;
    public com.arclinker.rpglevelitems.editor.EditorGUI getEditor() { return editor; }
    private com.arclinker.rpglevelitems.social.GuildManager guilds;
    private com.arclinker.rpglevelitems.social.TradeManager trades;
    private com.arclinker.rpglevelitems.social.AuctionHouse auction;
    public com.arclinker.rpglevelitems.social.GuildManager getGuilds() { return guilds; }
    public com.arclinker.rpglevelitems.social.TradeManager getTrades() { return trades; }
    public com.arclinker.rpglevelitems.social.AuctionHouse getAuction() { return auction; }
    private com.arclinker.rpglevelitems.quests.QuestExtras questExtras;
    public com.arclinker.rpglevelitems.quests.QuestExtras getQuestExtras() { return questExtras; }
    private com.arclinker.rpglevelitems.shop.ShopManager shop;
    private com.arclinker.rpglevelitems.shop.ShopGUI shopGUI;
    public com.arclinker.rpglevelitems.shop.ShopManager getShop() { return shop; }
    public com.arclinker.rpglevelitems.shop.ShopGUI getShopGUI() { return shopGUI; }
    private com.arclinker.rpglevelitems.jobabilities.JobAbilityManager jobAbilities;
    public com.arclinker.rpglevelitems.jobabilities.JobAbilityManager getJobAbilities() { return jobAbilities; }
    private com.arclinker.rpglevelitems.talents.TalentManager talents;
    private com.arclinker.rpglevelitems.talents.TalentGUI talentGUI;
    public com.arclinker.rpglevelitems.talents.TalentManager getTalents() { return talents; }
    public com.arclinker.rpglevelitems.talents.TalentGUI getTalentGUI() { return talentGUI; }
    private com.arclinker.rpglevelitems.prestige.PrestigeManager prestigeManager;
    public com.arclinker.rpglevelitems.prestige.PrestigeManager getPrestigeManager() { return prestigeManager; }
    public com.arclinker.rpglevelitems.achievements.AchievementManager getAchievements() { return achievementManager; }
    private com.arclinker.rpglevelitems.achievements.AchievementGUI achievementGUI;
    public com.arclinker.rpglevelitems.achievements.AchievementGUI getAchievementGUI() { return achievementGUI; }
    private com.arclinker.rpglevelitems.achievements.AchievementManager achievementManager;
    private com.arclinker.rpglevelitems.abuse.AntiAbuse antiAbuse;
    public com.arclinker.rpglevelitems.abuse.AntiAbuse getAntiAbuse() { return antiAbuse; }

    public com.arclinker.rpglevelitems.station.StationManager getStationManager() { return stationManager; }
    public com.arclinker.rpglevelitems.station.StationGUI getStationGUI() { return stationGUI; }
    public com.arclinker.rpglevelitems.utility.UtilityManager getUtility() { return utility; }
    public com.arclinker.rpglevelitems.itemcreator.RlvpBlockListener getRlvpBlocks() { return rlvpBlocks; }
    public RecipeManager getRecipeManager() { return recipeManager; }

    public AbilityManager getAbilityManager() { return abilityManager; }
    public AbilityItems getAbilityItems() { return abilityItems; }
    public AbilityGUI getAbilityGUI() { return abilityGUI; }
    public ClassLevelManager getClassLevelManager() { return classLevelManager; }
    public PlayerStore getPlayerStore() { return playerStore; }
    public RankManager getRankManager() { return rankManager; }

    public ItemListGUI getItemListGUI() {
        return itemListGUI;
    }

    public RlvpHandler getRlvpHandler() {
        return rlvpHandler;
    }

    @Override
    public void onLoad() {
        // El plugin pasó de llamarse RPGLevelTier a RPGLeVeL: si existe la carpeta antigua y no la nueva, se mueve entera (config, data, jugadores).
        java.io.File dir = getDataFolder();
        java.io.File old = new java.io.File(dir.getParentFile(), "RPGLevelTier");
        if (!dir.exists() && old.isDirectory()) {
            try {
                java.nio.file.Files.move(old.toPath(), dir.toPath());
                getLogger().info("Carpeta de datos migrada: plugins/RPGLevelTier -> plugins/" + dir.getName());
            } catch (java.io.IOException e) {
                getLogger().warning("No se pudo migrar plugins/RPGLevelTier: " + e.getMessage() + " (cópiala a mano a plugins/" + dir.getName() + ")");
            }
        }
    }

    @Override
    public void onEnable() {
        instance = this;
        // Ensure the data subfolder exists
        java.io.File dataFolder = new java.io.File(getDataFolder(), "data");
        if (!dataFolder.exists()) {
            dataFolder.mkdirs();
        }
        // Old single-file configs were replaced by one folder per section (data/<section>/)
        for (String legacy : new String[]{"data/classes.yml", "data/subclasses.yml", "data/jobs.yml", "data/quests.yml",
                "data/class_stats.yml", "data/gear_sets.yml", "rpg_abilities.yml"}) {
            DataFiles.migrateLegacy(this, legacy);
        }
        for (String section : new String[]{"classes", "subclasses", "jobs", "quests", "abilities", "stats", "gear_sets", "recipes", "affixes", "gems", "achievements", "loot", "bosses", "dungeons", "talents", "job_abilities", "shop", "utility"}) {
            DataFiles.extract(this, section, ".yml");
        }
        saveDefaultConfig();

        // Gems/affixes first: the .rlvp loader builds lore with them
        // Load .rlvp items first so nothing else can prevent it
        try {
            this.rlvpHandler = new RlvpHandler(this);
        } catch (Exception e) {
            getLogger().severe("Error cargando items .rlvp: " + e);
            e.printStackTrace();
        }

        this.playerStore = new PlayerStore(this);
        this.rankManager = new RankManager(this, playerStore);

        this.manager = new ItemProgressionManager(this);
        // Initialize new managers
        this.classManager = new ClassManager(this);
        this.gearSetManager = new GearSetManager(this);
        // Work stations + recipes of the vanilla stations (stations and forge materials)
        this.stationManager = new com.arclinker.rpglevelitems.station.StationManager(this);
        this.recipeManager = new RecipeManager(this);

        this.subClassManager = new SubClassManager(this);
        this.jobManager = new JobManager(this);
        this.classLevelManager = new ClassLevelManager(this);
        // Stats, economy, combat systems (ability manager needs mana / zones / combat)
        this.economy = new com.arclinker.rpglevelitems.economy.EconomyManager(this);
        this.affixManager = new com.arclinker.rpglevelitems.stats.AffixManager(this);
        this.gemManager = new com.arclinker.rpglevelitems.stats.GemManager(this);
        this.statEngine = new com.arclinker.rpglevelitems.stats.StatEngine(this);
        new com.arclinker.rpglevelitems.stats.ArmorGuard(this);
        this.jobManager.registerPerks();
        this.prestigeManager = new com.arclinker.rpglevelitems.prestige.PrestigeManager(this);
        this.achievementManager = new com.arclinker.rpglevelitems.achievements.AchievementManager(this);
        this.talents = new com.arclinker.rpglevelitems.talents.TalentManager(this);
        this.talentGUI = new com.arclinker.rpglevelitems.talents.TalentGUI(this);
        this.jobAbilities = new com.arclinker.rpglevelitems.jobabilities.JobAbilityManager(this);
        getServer().getPluginManager().registerEvents(new com.arclinker.rpglevelitems.jobabilities.JobAbilityListener(this, jobAbilities), this);
        this.mana = new com.arclinker.rpglevelitems.mana.ManaManager(this);
        this.statuses = new com.arclinker.rpglevelitems.combat.StatusManager(this);
        this.zones = new com.arclinker.rpglevelitems.zones.ZoneManager(this);
        this.parties = new com.arclinker.rpglevelitems.social.PartyManager(this);
        this.duels = new com.arclinker.rpglevelitems.social.DuelManager(this);
        this.guilds = new com.arclinker.rpglevelitems.social.GuildManager(this);
        this.trades = new com.arclinker.rpglevelitems.social.TradeManager(this);
        this.auction = new com.arclinker.rpglevelitems.social.AuctionHouse(this);
        this.shop = new com.arclinker.rpglevelitems.shop.ShopManager(this);
        this.shopGUI = new com.arclinker.rpglevelitems.shop.ShopGUI(this);
        this.mobs = new com.arclinker.rpglevelitems.world.MobScaler(this);
        getServer().getPluginManager().registerEvents(mobs, this);
        this.events = new com.arclinker.rpglevelitems.world.EventManager(this);
        this.bosses = new com.arclinker.rpglevelitems.world.BossManager(this);
        this.abilityManager = new AbilityManager(this);
        this.abilityItems = new AbilityItems(this);
        this.rarityManager = new RarityManager(this);
        this.dailyQuestManager = new DailyQuestManager(this);
        this.questExtras = new com.arclinker.rpglevelitems.quests.QuestExtras(this);
        this.languageManager = new LanguageManager(this);

        this.loot = new com.arclinker.rpglevelitems.loot.LootManager(this);
        this.dungeons = new com.arclinker.rpglevelitems.world.DungeonManager(this);
        this.combat = new com.arclinker.rpglevelitems.combat.CombatEngine(this);
        this.hotbar = new com.arclinker.rpglevelitems.combat.HotbarListener(this);
        getServer().getPluginManager().registerEvents(hotbar, this);
        getServer().getPluginManager().registerEvents(new LevelCombatListener(this, manager), this);

        // Register commands
        registerCommand("rpglevel", new LevelCommand(this, manager));
        registerCommand("class", new ClassCommand(this));
        registerCommand("subclass", new SubClassCommand(this));
        registerCommand("jobs", new JobCommand(this));
        registerCommand("quest", new QuestCommand(this));
        registerCommand("quests", new QuestCommand(this)); // alias
        registerCommand("rank", new RankCommand(this));
        registerCommand("ability", new AbilityCommand(this));
        registerCommand("station", new StationCommand(this));
        MiscCommands misc = new MiscCommands(this);
        for (String c : new String[]{"stats", "coins", "pay", "title", "prestige", "talent"}) registerCommand(c, misc);
        SocialCommands social = new SocialCommands(this);
        for (String c : new String[]{"party", "pc", "duel", "achievements", "dungeon", "guild", "gc", "trade", "ah", "shop"}) registerCommand(c, social);
        // Register language command
        registerCommand("language", new LanguageCommand(this));

        // Register GUI listener for inventory interactions
        new GUIListener(this);
        this.itemListGUI = new ItemListGUI(this);
        this.abilityGUI = new AbilityGUI(this);
        getServer().getPluginManager().registerEvents(new AbilityListener(this), this);
        this.rlvpBlocks = new com.arclinker.rpglevelitems.itemcreator.RlvpBlockListener(this);
        getServer().getPluginManager().registerEvents(rlvpBlocks, this);
        this.stationGUI = new com.arclinker.rpglevelitems.station.StationGUI(this);
        this.utility = new com.arclinker.rpglevelitems.utility.UtilityManager(this);
        getServer().getPluginManager().registerEvents(utility, this);
        this.achievementGUI = new com.arclinker.rpglevelitems.achievements.AchievementGUI(this);
        getServer().getPluginManager().registerEvents(new JoinListener(this), this);
        getServer().getPluginManager().registerEvents(new ChatListener(this), this);
        getServer().getPluginManager().registerEvents(new com.arclinker.rpglevelitems.stats.CraftGuard(this), this);
        this.editor = new com.arclinker.rpglevelitems.editor.EditorGUI(this);
        com.arclinker.rpglevelitems.hook.Hooks.registerAll(this);
        this.antiAbuse = new com.arclinker.rpglevelitems.abuse.AntiAbuse(this);
        getServer().getPluginManager().registerEvents(antiAbuse, this);
        getServer().getPluginManager().registerEvents(new ActivityListener(this), this);
        // Persist player data every 5 minutes (only writes when something changed)
        getServer().getScheduler().runTaskTimer(this, playerStore::save, 6000L, 6000L);
        getServer().getScheduler().runTaskTimer(this, playerStore::backup, 72000L, 72000L);
        // End the season when its time is up (checked every minute)
        getServer().getScheduler().runTaskTimer(this, rankManager::tick, 1200L, 1200L);


        getLogger().info("RPGLeVeL " + getDescription().getVersion() + " habilitado correctamente! (Sistema de 30 Niveles y 10 Tiers)");
    }

    /** Reloads config.yml and every data-driven system (used by /rlv reload and the in-game editor). */
    public void reloadAll() {
        reloadConfig();
        rankManager.reload();
        if (rlvpHandler != null) rlvpHandler.reload();
        stationManager.reload();
        if (utility != null) utility.reload();
        recipeManager.reload();
        statEngine.reload();
        affixManager.reload();
        gemManager.reload();
        gearSetManager.reload();
        achievementManager.reload();
        talents.reload();
        jobAbilities.reload();
        shop.reload();
        questExtras.reload();
        loot.reload();
        bosses.reload();
        dungeons.reload();
        events.reload();
    }

    private void registerCommand(String name, org.bukkit.command.CommandExecutor executor) {
        if (getCommand(name) != null) {
            // user commands accept "@<player>" for admins (see AdminLookup); /rpglevel has its own userinfo
            org.bukkit.command.CommandExecutor wrapped = name.equals("rpglevel") ? executor : new AdminLookup(this, executor);
            getCommand(name).setExecutor(wrapped);
            if (wrapped instanceof org.bukkit.command.TabCompleter) {
                getCommand(name).setTabCompleter((org.bukkit.command.TabCompleter) wrapped);
            }
        }
    }

    @Override
    public void onDisable() {
        if (recipeManager != null) recipeManager.unregister();
        if (gemManager != null) gemManager.unregister();
        if (bosses != null) bosses.killAll();
        if (trades != null) trades.cancelAll();
        if (duels != null) duels.shutdown();
        if (dungeons != null) dungeons.shutdown();
        if (statuses != null) statuses.shutdown();
        if (guilds != null) guilds.save();
        if (shop != null) shop.save();
        if (utility != null) utility.shutdown();
        if (playerStore != null) playerStore.saveNow();
        getLogger().info("RPGLeVeL deshabilitado.");
    }

    public static RPGLevelTier getInstance() {
        return instance;
    }

    public ItemProgressionManager getProgressionManager() {
        return manager;
    }

    // Getters for new managers
    public ClassManager getClassManager() { return classManager; }
    public SubClassManager getSubClassManager() { return subClassManager; }
    public JobManager getJobManager() { return jobManager; }
    public RarityManager getRarityManager() { return rarityManager; }
    public DailyQuestManager getDailyQuestManager() { return dailyQuestManager; }
    public LanguageManager getLanguageManager() { return languageManager; }
    public GearSetManager getGearSetManager() { return gearSetManager; }

    // ClassManager methods
    public String getPlayerClassId(Player player) {
        return classManager.getClassId(player);
    }

    public void setPlayerClass(Player player, String classId) {
        classManager.setClass(player, classId);
    }

    // GearSetManager methods
    public GearSetManager.GearSet getGearSet(String setId) {
        return gearSetManager.getSet(setId);
    }

    public Collection<GearSetManager.GearSet> getAllGearSets() {
        return gearSetManager.getAllSets();
    }

    // SubClassManager methods
    public String getPlayerSubClassId(Player player) {
        return subClassManager.getSubClassId(player);
    }

    public void setPlayerSubClass(Player player, String subClassId) {
        subClassManager.setSubClass(player, subClassId);
    }

    // JobManager methods
    public String getPlayerJobClass(Player player) {
        return jobManager.getClassJob(player);
    }

    public String getPlayerJob1(Player player) {
        return jobManager.getGlobalJob(player, 1);
    }

    public String getPlayerJob2(Player player) {
        return jobManager.getGlobalJob(player, 2);
    }

    // RarityManager methods
    public int getRarityLevel(String rarityName) {
        return rarityManager.getRarityLevel(rarityName);
    }

    public double getRarityMultiplier(String rarityName) {
        return rarityManager.getMultiplier(rarityName);
    }

    // DailyQuestManager methods
    public void assignDailyQuests(Player player) {
        dailyQuestManager.assignDailyQuests(player);
    }

    public List<String> getTodaysQuests(Player player) {
        return dailyQuestManager.getTodaysQuests(player);
    }

    // LanguageManager methods
    public String getMessage(String key) {
        return languageManager.get(key);
    }

    public String getCurrentLanguage() {
        return languageManager.getCurrentLanguage();
    }

    // ItemProgressionManager methods
    public boolean isRpgItem(ItemStack item) {
        return manager.isRpgItem(item);
    }

    public int getPlayerLevel(ItemMeta meta) {
        return manager.getLevel(meta);
    }

    public int getPlayerTier(ItemMeta meta) {
        return manager.getTier(meta);
    }

    public long getPlayerXP(ItemMeta meta) {
        return manager.getXp(meta);
    }
}

