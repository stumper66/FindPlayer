package findPlayer;

import java.time.LocalDateTime;
import java.util.HashMap;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.ChatColor;
import org.bukkit.command.PluginCommand;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;

import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.PluginDescriptionFile;
import org.bukkit.configuration.file.FileConfiguration;
import org.jetbrains.annotations.NotNull;

public class FindPlayer extends JavaPlugin implements Listener {
	public FileConfiguration config;
	private LoggingType loggingType = LoggingType.NONE;
	public boolean useDebug;
	private IPlayerCache playerCache;
	@NotNull
	private String playerOnlinePreformedString = "";
	@NotNull
	private String playerOfflinePreformedString = "";
	private String wg_RegionPreformedString;
	private String wg_RegionPostformedString;
	public boolean hasWorldGuard = false;
	
	@Override
    public void onEnable() {
		playerOnlinePreformedString = "";
		saveDefaultConfig();
		config = getConfig();
		
		this.hasWorldGuard = WorldGuardStuff.CheckForWorldGuard();
		processConfig(null);
		playerCache.populateData();

		final PluginCommand cmd = this.getCommand("findp");
		if (cmd != null) cmd.setExecutor(this);
		getServer().getPluginManager().registerEvents(this, this);

		if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null)
			new PAPI_Manager(this).register();

		final PluginDescriptionFile pdf = this.getDescription();
			
		if (this.useDebug) Helpers.logger.info("Has WorldGuard: " + this.hasWorldGuard);
		final String msg = String.format("%s (%s) has been enabled", pdf.getName(), pdf.getVersion());
		Helpers.logger.info(msg);
	}
	
	@Override
	public void onDisable() {
		if (playerCache != null) playerCache.close();

		final PluginDescriptionFile pdf = this.getDescription();
		if (this.useDebug) Helpers.logger.info(pdf.getName() + " has been disabled");
	}
	
	@Override
	public boolean onCommand(final @NotNull CommandSender sender, final Command cmd, final @NotNull String label, final String[] args) {
		if (!cmd.getName().equalsIgnoreCase("findp"))
			return true;

		if(args.length != 1) {
			final ChatColor g = ChatColor.GREEN;
			final ChatColor y = ChatColor.YELLOW;
			sender.sendMessage(y + "Usage: /findp PlayerName" + g + "|" + y + "reload" + g + "|" + y + "purge");
			return true;
		}

		if (args[0].equalsIgnoreCase("reload")) {
			if (!sender.hasPermission("FindPlayer.reload")) {
				sender.sendMessage(ChatColor.RED + "You don't have permissions for this command");
				return true;
			}

			saveDefaultConfig();
			this.reloadConfig();
			config = getConfig();
			processConfig(sender);
			sender.sendMessage(ChatColor.YELLOW + "Reloaded the config.");
		}
		else if (args[0].equalsIgnoreCase("purge")) {
			if (!sender.hasPermission("FindPlayer.purge")) {
				sender.sendMessage(ChatColor.RED + "You don't have permissions for this command");
				return true;
			}
			playerCache.purgeData();
			sender.sendMessage(ChatColor.YELLOW + "Purged all cached data.");
		}
		else {
			final String sendMsg = getMessageForPlayer(args[0]);
			sender.sendMessage(sendMsg);
		}

		return true;
	}

	@EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
	public void onPlayerQuit(final PlayerQuitEvent event){
		if (this.loggingType == LoggingType.NONE) return;
		
		final Player p = event.getPlayer();
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
				
			if (this.hasWorldGuard)
				psi.regionNames = WorldGuardStuff.GetWorldGuardRegionsForLocation(loc);
				
			playerCache.addOrUpdatePlayerInfo(psi);
			return formulateMessage(this.playerOnlinePreformedString, psi, loc, this.hasWorldGuard);
		}
		
		// player was null.  Let's see if we have the name cached
		psi = playerCache.getPlayerInfo(playerName);
		if (psi == null)
			return preformulateMessage(config.getString("invalid-player-message", ChatColor.RED + "Player name invalid or is offline."));

		return formulateMessage(this.playerOfflinePreformedString, psi, null, this.hasWorldGuard);
	}
		
	private void processConfig(final CommandSender sender) {
		this.useDebug = config.getBoolean("debug", false);
		final long writeTimeMs = config.getLong("json-write-time-ms", 5000L);
		String loggingType = config.getString("player-logging-type");
		if (Helpers.isNullOrEmpty(loggingType)) loggingType = "json";
		
		final boolean isReload = (sender != null);
		
		if (!isReload) {
			// can only change this by restarting server
			
			switch (loggingType.toLowerCase()) {
				case "mysql":
					this.loggingType = LoggingType.MYSQL;
					final PlayerCache_SQL.MySQL_ConfigInfo sconfig = new PlayerCache_SQL.MySQL_ConfigInfo();
					sconfig.database = config.getString("mysql-database");
					sconfig.hostname = config.getString("mysql-hostname");
					sconfig.username = config.getString("mysql-username");
					sconfig.password = config.getString("mysql-password");
					
					final PlayerCache_SQL mysql = new PlayerCache_SQL(sconfig, useDebug);
					playerCache = mysql;
					mysql.openConnection();
					break;
				case "sqlite":
					this.loggingType = LoggingType.SQLITE;
					final PlayerCache_SQL sqlite = new PlayerCache_SQL(this.getDataFolder(), useDebug);
					playerCache = sqlite;
					sqlite.openConnection();
					break;
				case "json":
					this.loggingType = LoggingType.JSON;
					playerCache = new PlayerCache_Json(this.getDataFolder(), writeTimeMs, useDebug);
					break;
				default: 
					this.loggingType = LoggingType.NONE;
					break;
			}
			
			if (this.useDebug) Helpers.logger.info("Using logging type of " + this.loggingType.toString());
		}
		else {
			// is reload
			if (playerCache != null)
				playerCache.updateDebug(useDebug);
			
			if (!this.loggingType.toString().equalsIgnoreCase(loggingType))
				sender.sendMessage(ChatColor.RED + "Warning! You must restart the server to change the logging type.");
		}
		
		final String[] preformedMessages = new String[] {
				config.getString("player-online-message", null),
				config.getString("player-offline-message", null),
				config.getString("worldguard-region-message", null)
		};
		
		final String[] defaultMessages = new String[] {
				"{Color_Aqua}Player {PlayerName} coordinates are: {Color_Blue}{X}, {Y}, {Z}, in: {World}{Color_Blue}{RegionMessage}",
				"{Color_Aqua}Player {PlayerName} coordinates are: {Color_Blue}{X}, {Y}, {Z}\n(in: {World}{Color_Blue})",
				" in region: {WorldGuardRegion}"
		};
		
		for (int i = 0; i < preformedMessages.length; i++) {
			if (i == 2 && !this.hasWorldGuard) {
				this.wg_RegionPreformedString = "";
				continue;
			}
			
			String temp = preformedMessages[i];
			if (Helpers.isNullOrEmpty(temp)) temp = defaultMessages[i];
			
			temp = preformulateMessage(temp);
			
			switch (i) {
				case 0: this.playerOnlinePreformedString = temp; break;
				case 1: this.playerOfflinePreformedString = temp; break;
				case 2: this.wg_RegionPreformedString = temp; break;
			}
		}
		
		this.wg_RegionPostformedString = "";
	}

	@NotNull
	private static String truncateTimeMilliseconds(final LocalDateTime time){
		final String timeStr = time.toString();
		final int lastPeriod = timeStr.lastIndexOf(".");
		if (lastPeriod <= 0)
			return timeStr;
		else
			return timeStr.substring(0, lastPeriod);
	}

	@NotNull
	private String formulateMessage(final @NotNull String str, final PlayerStoreInfo psi, final Location l, final boolean doCheckWG) {
		final HashMap<String, String> v = new HashMap<>();
		v.put("{PlayerName}", psi.playerName);
		v.put("{World}", psi.worldName);
		v.put("{X}", Integer.toString(psi.locationX));
		v.put("{Y}", Integer.toString(psi.locationY));
		v.put("{Z}", Integer.toString(psi.locationZ));
		v.put("{LastSeen}", (truncateTimeMilliseconds(psi.lastOnline)));
		
		String wg_Region = "";
		if (this.hasWorldGuard && str.toLowerCase().contains("{worldguardregion}")) {
			if (l == null) {
				final World world = Bukkit.getServer().getWorld(psi.worldName);
				if (world != null) {
					final Location loc = new Location(world, psi.locationX, psi.locationY, psi.locationZ);
					wg_Region = WorldGuardStuff.GetWorldGuardRegionsForLocation(loc);
				}
			} else {
				wg_Region = WorldGuardStuff.GetWorldGuardRegionsForLocation(l);
				if (wg_Region == null && !doCheckWG) return "";
			}
			
			if (wg_Region == null) wg_Region = "";
		}

		if (doCheckWG) {
			this.wg_RegionPostformedString = formulateMessage(this.wg_RegionPreformedString, psi, l, false);
			v.put("{RegionMessage}", this.wg_RegionPostformedString);
		}

		if (!this.hasWorldGuard) v.put("{RegionMessage}", "");
		v.put("{WorldGuardRegion}", wg_Region);
		
		String formedStr = str;

		for (final String key : v.keySet()) {
			formedStr = Helpers.ReplaceEx(formedStr, key, v.get(key));
		}
		
		return formedStr;
	}

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

		for (final String key : v.keySet()) {
			formedStr = Helpers.ReplaceEx(formedStr, key, v.get(key).toString());
		}
		
		return formedStr;
	}
	
	private enum LoggingType{
		NONE, JSON, MYSQL, SQLITE
	}
}
