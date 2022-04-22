package me.stumper66.findplayer;

import org.jetbrains.annotations.NotNull;

import java.util.List;

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
