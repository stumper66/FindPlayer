package me.stumper66.findplayer;

import java.util.List;
import org.jetbrains.annotations.NotNull;

public interface IPlayerCache {

    void addOrUpdatePlayerInfo(final PlayerStoreInfo psi);

    PlayerStoreInfo getPlayerInfo(final String playername);

    @NotNull
    List<String> getAllPlayers();

    void populateData();

    void purgeData();

    void updateDebug(boolean useDebug);

    void close();
}
