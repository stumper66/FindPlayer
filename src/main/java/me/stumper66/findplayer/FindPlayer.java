package me.stumper66.findplayer;

import co.aikar.commands.PaperCommandManager;
import java.time.LocalDateTime;
import java.util.HashMap;
import me.stumper66.findplayer.command.AcfCommands;
import me.stumper66.findplayer.command.FilterOption;
import me.stumper66.findplayer.config.ConfigMigrator;
import me.stumper66.findplayer.data.IPlayerCache;
import me.stumper66.findplayer.data.PlayerCacheJson;
import me.stumper66.findplayer.data.PlayerCacheSQL;
import me.stumper66.findplayer.data.PlayerStoreInfo;
import me.stumper66.findplayer.integration.PlaceholderApiHandler;
import me.stumper66.findplayer.integration.WorldGuardHandler;
import me.stumper66.findplayer.misc.Helpers;
import me.stumper66.findplayer.misc.Utils;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.PluginDescriptionFile;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;

public class FindPlayer extends JavaPlugin implements Listener {

    public LoggingType loggingType = LoggingType.NONE;
    public boolean useDebug;
    public IPlayerCache playerCache;
    @NotNull private String playerOnlinePreformedString = "";
    @NotNull private String playerOfflinePreformedString = "";
    private String wgRegionPreformedString;
    private String wgRegionPostformedString;
    public FilterOption defaultFilter;
    public PaperCommandManager commandManager;

    @Override
    public void onEnable() {
        playerOnlinePreformedString = "";
        defaultFilter = FilterOption.ALL;
        saveDefaultConfig();

        processConfig(null);
        if(playerCache != null) {
            playerCache.populateData();
        }

        registerCommands();
        getServer().getPluginManager().registerEvents(this, this);

        if(PlaceholderApiHandler.hasPlaceholderApi()) {
            new PlaceholderApiHandler(this).register();
        }

        Helpers.logger.info("Start-up complete.");
    }

    private void registerCommands() {
        this.commandManager = new PaperCommandManager(this);
        this.commandManager.registerCommand(new AcfCommands(this));
    }

    @Override
    public void onDisable() {
        if(playerCache != null) {
            // save all online players
            if(this.loggingType != LoggingType.NONE) {
                for(final Player player : Bukkit.getOnlinePlayers()) {
                    savePlayerInfo(player);
                }
            }
            playerCache.close();
        }

        final PluginDescriptionFile pdf = this.getDescription();
        if(this.useDebug) {
            Helpers.logger.info(pdf.getName() + " has been disabled");
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public void onPlayerQuit(final PlayerQuitEvent event) {
        if(this.loggingType == LoggingType.NONE) {
            return;
        }

        savePlayerInfo(event.getPlayer());
    }

    private void savePlayerInfo(final Player p) {
        final Location loc = p.getLocation();

        final PlayerStoreInfo psi = new PlayerStoreInfo(p.getUniqueId(), p.getName(),
            p.getWorld().getName(), loc);

        playerCache.addOrUpdatePlayerInfo(psi);
    }

    @NotNull
    public String getMessageForPlayer(final String playerName) {
        // try to find player entity from name.  Will only work if they are online
        final Player p = Bukkit.getPlayer(playerName);
        PlayerStoreInfo psi;

        if(p != null) {
            // player is online
            final Location loc = p.getLocation();
            psi = new PlayerStoreInfo(p.getUniqueId(), p.getName(),
                p.getWorld().getName(), loc);

            if(WorldGuardHandler.hasWorldGuard()) {
                psi.regionNames = WorldGuardHandler.getWorldGuardRegionsForLocation(loc);
            }

            playerCache.addOrUpdatePlayerInfo(psi);
            return formulateMessage(this.playerOnlinePreformedString, psi, loc, WorldGuardHandler.hasWorldGuard());
        }

        // player was null.  Let's see if we have the name cached
        psi = playerCache.getPlayerInfo(playerName);
        if(psi == null) {
            return preformulateMessage(getConfig().getString("invalid-player-message",
                ChatColor.RED + "Player name invalid or is offline."));
        }

        return formulateMessage(this.playerOfflinePreformedString, psi, null, WorldGuardHandler.hasWorldGuard());
    }

    public void processConfig(final CommandSender sender) {
        ConfigMigrator.checkConfigVersion(this);

        this.useDebug = getConfig().getBoolean("debug", false);
        final long writeTimeMs = getConfig().getLong("json-write-time-ms", 5000L);
        String loggingType = getConfig().getString("player-logging-type");
        if(Utils.isNullOrEmpty(loggingType)) {
            loggingType = "json";
        }

        final boolean isReload = (sender != null);

        if(!isReload) {
            // can only change this by restarting server

            switch(loggingType.toLowerCase()) {
                case "mysql":
                    this.loggingType = LoggingType.MYSQL;
                    final PlayerCacheSQL.MySQL_ConfigInfo sconfig = new PlayerCacheSQL.MySQL_ConfigInfo();
                    sconfig.database = getConfig().getString("mysql-database");
                    sconfig.hostname = getConfig().getString("mysql-hostname");
                    sconfig.username = getConfig().getString("mysql-username");
                    sconfig.password = getConfig().getString("mysql-password");

                    final PlayerCacheSQL mysql = new PlayerCacheSQL(sconfig, useDebug);
                    playerCache = mysql;
                    mysql.openConnection();
                    break;
                case "sqlite":
                    this.loggingType = LoggingType.SQLITE;
                    final PlayerCacheSQL sqlite = new PlayerCacheSQL(this.getDataFolder(),
                        useDebug);
                    playerCache = sqlite;
                    sqlite.openConnection();
                    break;
                case "json":
                    this.loggingType = LoggingType.JSON;
                    playerCache = new PlayerCacheJson(this.getDataFolder(), writeTimeMs, useDebug);
                    break;
                default:
                    this.loggingType = LoggingType.NONE;
                    break;
            }

            if(this.useDebug) {
                Helpers.logger.info("Using logging type of " + this.loggingType.toString());
            }
        } else {
            // is reload
            if(playerCache != null) {
                playerCache.updateDebug(useDebug);
            }

            if(!this.loggingType.toString().equalsIgnoreCase(loggingType)) {
                sender.sendMessage(ChatColor.RED
                    + "Warning! You must restart the server to change the logging type.");
            }
        }

        final String[] preformedMessages = new String[]{
            getConfig().getString("player-online-message", null),
            getConfig().getString("player-offline-message", null),
            getConfig().getString("worldguard-region-message", null)
        };

        final String[] defaultMessages = new String[]{
            "{Color_Aqua}Player {PlayerName} coordinates are: {Color_Blue}{X}, {Y}, {Z}, in: {World}{Color_Blue}{RegionMessage}",
            "{Color_Aqua}Player {PlayerName} coordinates are: {Color_Blue}{X}, {Y}, {Z}\n(in: {World}{Color_Blue})",
            " in region: {WorldGuardRegion}"
        };

        for(int i = 0; i < preformedMessages.length; i++) {
            if(i == 2 && !WorldGuardHandler.hasWorldGuard()) {
                this.wgRegionPreformedString = "";
                continue;
            }

            String temp = preformedMessages[i];
            if(Utils.isNullOrEmpty(temp)) {
                temp = defaultMessages[i];
            }

            temp = preformulateMessage(temp);

            switch(i) {
                case 0:
                    this.playerOnlinePreformedString = temp;
                    break;
                case 1:
                    this.playerOfflinePreformedString = temp;
                    break;
                case 2:
                    this.wgRegionPreformedString = temp;
                    break;
            }
        }

        try {
            this.defaultFilter = FilterOption.valueOf(
                getConfig().getString("default-name-filter", "ALL").toUpperCase());
        } catch(Exception ignored) {
            Helpers.logger.warning("Invalid value for default-name-filter");
        }

        this.wgRegionPostformedString = "";
    }

    @NotNull
    private String formulateMessage(final @NotNull String str, final PlayerStoreInfo psi,
        final Location l, final boolean doCheckWG) {
        final HashMap<String, String> v = new HashMap<>();
        v.put("{PlayerName}", psi.playerName);
        v.put("{World}", psi.worldName);
        v.put("{X}", Integer.toString(psi.locationX));
        v.put("{Y}", Integer.toString(psi.locationY));
        v.put("{Z}", Integer.toString(psi.locationZ));
        v.put("{LastSeen}", (formatDateTime(psi.lastOnline)));

        String wg_Region = "";
        if(WorldGuardHandler.hasWorldGuard() && str.toLowerCase().contains("{worldguardregion}")) {
            if(l == null) {
                final World world = Bukkit.getServer().getWorld(psi.worldName);
                if(world != null) {
                    final Location loc = new Location(world, psi.locationX, psi.locationY,
                        psi.locationZ);
                    wg_Region = WorldGuardHandler.getWorldGuardRegionsForLocation(loc);
                }
            } else {
                wg_Region = WorldGuardHandler.getWorldGuardRegionsForLocation(l);
                if(wg_Region == null && !doCheckWG) {
                    return "";
                }
            }

            if(wg_Region == null) {
                wg_Region = "";
            }
        }

        if(doCheckWG) {
            this.wgRegionPostformedString = formulateMessage(this.wgRegionPreformedString, psi, l,
                false);
            v.put("{RegionMessage}", this.wgRegionPostformedString);
        }

        if(!WorldGuardHandler.hasWorldGuard()) {
            v.put("{RegionMessage}", "");
        }
        v.put("{WorldGuardRegion}", wg_Region);

        String formedStr = str;

        for(final String key : v.keySet()) {
            formedStr = Helpers.replaceIgnoreCase(formedStr, key, v.get(key));
        }

        return formedStr;
    }

    //TODO remove: Java has its own inbuilt date time formatting system
    private String formatDateTime(final LocalDateTime time) {
        final HashMap<String, Object> v = new HashMap<>();
        v.put("{day}", time.getDayOfMonth());
        v.put("{month}", time.getMonthValue());
        v.put("{year}", time.getYear());
        v.put("{hour}", time.getHour() < 10 ? "0" + time.getHour() : time.getHour());
        v.put("{minute}", time.getMinute() < 10 ? "0" + time.getMinute() : time.getMinute());
        v.put("{second}", time.getSecond() < 10 ? "0" + time.getSecond() : time.getSecond());

        String formedStr = getConfig().getString("datetime-format", "");

        for(final String key : v.keySet()) {
            formedStr = Helpers.replaceIgnoreCase(formedStr, key, v.get(key).toString());
        }

        if(formedStr.isEmpty()) {
            formedStr = time.toString();
        }

        return formedStr;
    }

    //TODO remove: redundant due to legacy color code support
    @NotNull
    private String preformulateMessage(final @NotNull String str) {
        final HashMap<String, ChatColor> v = new HashMap<>();
        v.put("{AQUA}", ChatColor.AQUA);
        v.put("{BLACK}", ChatColor.BLACK);
        v.put("{BLUE}", ChatColor.BLUE);
        v.put("{BOLD}", ChatColor.BOLD);
        v.put("{DARK_AQUA}", ChatColor.DARK_AQUA);
        v.put("{DARK_BLUE}", ChatColor.DARK_BLUE);
        v.put("{DARK_GRAY}", ChatColor.DARK_GRAY);
        v.put("{DARK_GREEN}", ChatColor.DARK_GREEN);
        v.put("{DARK_PURPLE}", ChatColor.DARK_PURPLE);
        v.put("{DARK_RED}", ChatColor.DARK_RED);
        v.put("{GOLD}", ChatColor.GOLD);
        v.put("{GRAY}", ChatColor.GRAY);
        v.put("{GREEN}", ChatColor.GREEN);
        v.put("{ITALIC}", ChatColor.ITALIC);
        v.put("{LIGHT_PURPLE}", ChatColor.LIGHT_PURPLE);
        v.put("{MAGIC}", ChatColor.MAGIC);
        v.put("{RED}", ChatColor.RED);
        v.put("{RESET}", ChatColor.RESET);
        v.put("{STRIKETHROUGH}", ChatColor.STRIKETHROUGH);
        v.put("{UNDERLINE}", ChatColor.UNDERLINE);
        v.put("{WHITE}", ChatColor.WHITE);
        v.put("{YELLOW}", ChatColor.YELLOW);

        String formedStr = str;

        for(final String key : v.keySet()) {
            formedStr = Helpers.replaceIgnoreCase(formedStr, key, v.get(key).toString());
        }

        return formedStr;
    }

    private enum LoggingType {
        NONE, JSON, MYSQL, SQLITE
    }
}
