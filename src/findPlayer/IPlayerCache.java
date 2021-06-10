package findPlayer;

public interface IPlayerCache {
	void addOrUpdatePlayerInfo(final PlayerStoreInfo psi);
	
	PlayerStoreInfo getPlayerInfo(final String playername);

	void populateData();
	void purgeData();
	void updateDebug(boolean useDebug);
	void close();
}
