package findPlayer;

import java.util.HashMap;
import java.util.TreeMap;
import java.util.UUID;
import java.util.logging.Logger;

public class PlayerCache_Base {
	public PlayerCache_Base() {
		logger = Logger.getLogger("Minecraft");
		this.Mapping = new HashMap<UUID, PlayerStoreInfo>();
		this.NameMappings = new TreeMap<String, UUID>(String.CASE_INSENSITIVE_ORDER);
	}
	
	public final Logger logger;
	public HashMap<UUID, PlayerStoreInfo> Mapping;
	public TreeMap<String, UUID> NameMappings;
	
	public PlayerStoreInfo GetPlayerInfo(String Playername) {
		if (!NameMappings.containsKey(Playername)) return null;
		UUID userId = NameMappings.get(Playername);
		
		return this.GetPlayerInfo(userId);
	}
	
	public PlayerStoreInfo GetPlayerInfo(UUID UserId) {
		if (!Mapping.containsKey(UserId)) return null;
		
		return Mapping.get(UserId);
	}
}
