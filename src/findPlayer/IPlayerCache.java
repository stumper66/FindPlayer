package findPlayer;

import java.util.UUID;

public interface IPlayerCache {
	void AddOrUpdatePlayerInfo(PlayerStoreInfo psi);
	
	PlayerStoreInfo GetPlayerInfo(String playername);
	
	PlayerStoreInfo GetPlayerInfo(UUID userId);
	
	void PopulateData();
}
