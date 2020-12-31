package findPlayer;

import java.util.HashMap;
import java.util.Iterator;
import java.util.logging.Logger;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.ChatColor;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;

import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.PluginDescriptionFile;
import org.bukkit.configuration.file.FileConfiguration;

public class FindPlayer extends JavaPlugin implements Listener {
	private final Logger logger = Logger.getLogger("Minecraft");
	public FileConfiguration config;
	private LoggingType loggingType = LoggingType.None;
	public Boolean useDebug;
	private IPlayerCache playerCache;
	private String playerOnlinePreformedString;
	private String playerOfflinePreformedString;
	private String WG_RegionPreformedString;
	private String WG_RegionPostformedString;
	public Boolean hasWorldGuard = false;
	
	@Override
    public void onEnable() {
		saveDefaultConfig();
		config = getConfig();
		
		this.hasWorldGuard = WorldGuardStuff.CheckForWorldGuard();
		processConfig(null);
		playerCache.PopulateData();
			
		this.getCommand("findp").setExecutor(this);
		getServer().getPluginManager().registerEvents(this, this);
		
		PluginDescriptionFile pdf = this.getDescription();
			
		if (this.useDebug) logger.info("Has WorldGuard: " + this.hasWorldGuard.toString());
		String msg = String.format("%s (%s) has been enabled", pdf.getName(), pdf.getVersion());
		logger.info(msg);
	}
	
	@Override
	public void onDisable() {
		PluginDescriptionFile pdf = this.getDescription();
		if (this.useDebug) logger.info(pdf.getName() + " has been disabled");
	}
	
	@Override
	public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
		if (cmd.getName().equalsIgnoreCase("findp")) {
          	if(args.length == 1) {
          		if (args[0].equalsIgnoreCase("reload")) {
          			if (!sender.hasPermission("FindPlayer.reload")) {
          				sender.sendMessage(ChatColor.RED + "You don't have permisisons for this command");
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
          				sender.sendMessage(ChatColor.RED + "You don't have permisisons for this command");
          				return true;
          			}
          			playerCache.PurgeData();
          			sender.sendMessage(ChatColor.YELLOW + "Purged all cached data.");
          		}
          		else {
          			String SendMsg = getMessageForPlayer(args[0]);
          			sender.sendMessage(SendMsg);
          		}
           	} else {
           		ChatColor g = ChatColor.GREEN;
           		ChatColor y = ChatColor.YELLOW;
           		sender.sendMessage(y + "Usage: /findp PlayerName" + g + "|" + y + "reload" + g + "|" + y + "purge");
           	}
        }
		
		return true;
	}
	
	@EventHandler
	public void onPlayerQuit(PlayerQuitEvent event){
		if (this.loggingType == LoggingType.None) return;
		
		Player p = event.getPlayer();
		if (p == null) return;
		Location loc = p.getLocation();
		  
		PlayerStoreInfo psi = new PlayerStoreInfo(p.getUniqueId(), p.getName(), 
					p.getWorld().getName(), loc);
		
		playerCache.AddOrUpdatePlayerInfo(psi);
	}
	
	private String getMessageForPlayer(String playerName) {
		// try to find player entity from name.  Will only work if they are online
		Player p = Bukkit.getPlayer(playerName);
		PlayerStoreInfo psi = null;
		
		if(p != null) {
			// player is online
			Location loc = p.getLocation();
			psi = new PlayerStoreInfo(p.getUniqueId(), p.getName(), 
				p.getWorld().getName(), loc);
				
			if (this.hasWorldGuard)
				psi.regionNames = WorldGuardStuff.GetWorldGuardRegionsForLocation(loc);
				
			playerCache.AddOrUpdatePlayerInfo(psi);
			return formulateMessage(this.playerOnlinePreformedString, psi, loc, this.hasWorldGuard);
		}
		
		// player was null.  Let's see if we have the name cached
		psi = playerCache.GetPlayerInfo(playerName);
		if (psi == null) {
			return ChatColor.RED.toString() + "Player name invalid or is offline.";
		}
		
		return formulateMessage(this.playerOfflinePreformedString, psi, null, this.hasWorldGuard);
	}
		
	private void processConfig(CommandSender sender) {
		this.useDebug = config.getBoolean("debug", false);
		long writeTimeMs = config.getLong("json-write-time-ms", 5000L);
		String loggingType = config.getString("player-logging-type");
		if (Helpers.isNullOrEmpty(loggingType)) loggingType = "json";
		
		Boolean isReload = (sender != null);
		
		if (!isReload) {
			// can only change this by restarting server
			
			switch (loggingType.toLowerCase()) {
				case "mysql":
					this.loggingType = LoggingType.Mysql;
					PlayerCache_SQL.MySQL_ConfigInfo sconfig = new PlayerCache_SQL.MySQL_ConfigInfo();
					sconfig.database = config.getString("mysql-database");
					sconfig.hostname = config.getString("mysql-hostname");
					sconfig.username = config.getString("mysql-username");
					sconfig.password = config.getString("mysql-password");
					
					PlayerCache_SQL mysql = new PlayerCache_SQL(sconfig, useDebug);
					playerCache = mysql;
					mysql.openConnection();
					break;
				case "sqlite":
					this.loggingType = LoggingType.Sqlite;
					PlayerCache_SQL sqlite = new PlayerCache_SQL(this.getDataFolder(), useDebug);
					playerCache = sqlite;
					sqlite.openConnection();
					break;
				case "json":
					this.loggingType = LoggingType.Json;
					playerCache = new PlayerCache_Json(this.getDataFolder(), writeTimeMs, useDebug);
					break;
				default: 
					this.loggingType = LoggingType.None;
					break;
			}
			
			if (this.useDebug) logger.info("Using logging type of " + this.loggingType.toString());
		}
		else {
			// is reload
			if (playerCache != null) {
				playerCache.UpdateDebug(useDebug);
				playerCache.UpdateFileWriteTime(writeTimeMs);
			}
			
			if (this.loggingType.toString().toLowerCase() != loggingType) {
				sender.sendMessage(ChatColor.RED + "Warning! You must restart the server to change the logging type.");
			}
		}
		
		String[] PreformedMessages = new String[] {
				config.getString("player-online-message", null),
				config.getString("player-offline-message", null),
				config.getString("worldguard-region-message", null)
		};
		
		String[] DefaultMessages = new String[] {
				"{Color_Aqua}Player {PlayerName} coordinates are: {Color_Blue}{X}, {Y}, {Z}, in: {World}{Color_Blue}{RegionMessage}",
				"{Color_Aqua}Player {PlayerName} coordinates are: {Color_Blue}{X}, {Y}, {Z}\n(in: {World}{Color_Blue})",
				" in region: {WorldGuardRegion}"
		};
		
		for (int i = 0; i < PreformedMessages.length; i++) {
			if (i == 2 && !this.hasWorldGuard) {
				this.WG_RegionPreformedString = "";
				continue;
			}
			
			String temp = PreformedMessages[i];
			if (Helpers.isNullOrEmpty(temp)) temp = DefaultMessages[i];
			
			temp = preformulateMessage(temp);
			
			switch (i) {
				case 0: this.playerOnlinePreformedString = temp; break;
				case 1: this.playerOfflinePreformedString = temp; break;
				case 2: this.WG_RegionPreformedString = temp; break;
			}
		}
		
		this.WG_RegionPostformedString = "";
	}
	
	private String formulateMessage(String str, PlayerStoreInfo psi, Location l, Boolean doCheckWG) {
		if (str == null) {
			logger.warning("formulateMessage was called with a null string");
			return null;
		}
		
		HashMap<String, String> v = new HashMap<String, String>();
		v.put("{PlayerName}", psi.playerName);
		v.put("{World}", psi.worldName);
		v.put("{X}", Integer.toString(psi.locationX));
		v.put("{Y}", Integer.toString(psi.locationY));
		v.put("{Z}", Integer.toString(psi.locationZ));
		v.put("{LastSeen}", psi.lastOnline.toString());
		
		String WG_Region = "";
		if (this.hasWorldGuard && str.toLowerCase().indexOf("{worldguardregion}") >= 0) {
			if (l == null) {
				World world = Bukkit.getServer().getWorld(psi.worldName);
				if (world != null) {
					Location loc = new Location(world, Double.valueOf(psi.locationX), Double.valueOf(psi.locationY), Double.valueOf(psi.locationZ));
					WG_Region = WorldGuardStuff.GetWorldGuardRegionsForLocation(loc);
				}
			} else {
				WG_Region = WorldGuardStuff.GetWorldGuardRegionsForLocation(l);
				if (WG_Region == null && !doCheckWG) return "";
			}
			
			if (WG_Region == null) WG_Region = "";
		}

		if (doCheckWG) {
			this.WG_RegionPostformedString = formulateMessage(this.WG_RegionPreformedString, psi, l, false);
			v.put("{RegionMessage}", this.WG_RegionPostformedString); 
		}
		
		v.put("{WorldGuardRegion}", WG_Region);
		
		String formedStr = str;
		Iterator<String> iterator = v.keySet().iterator();
		
		while (iterator.hasNext()) {
			String key = iterator.next();
			String temp = Helpers.ReplaceEx(formedStr, key, v.get(key).toString());
			formedStr = temp;
		}
		
		return formedStr;
	}
	
	private String preformulateMessage(String str) {
		HashMap<String, ChatColor> v = new HashMap<String, ChatColor>();
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
		Iterator<String> iterator = v.keySet().iterator();
		
		while (iterator.hasNext()) {
			String key = iterator.next();
			String temp = Helpers.ReplaceEx(formedStr, key, v.get(key).toString());
			formedStr = temp;
		}
		
		return formedStr;
	}
	
	private enum LoggingType{
		None, Json, Mysql, Sqlite
	}
}
