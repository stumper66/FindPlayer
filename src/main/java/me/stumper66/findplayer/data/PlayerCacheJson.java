package me.stumper66.findplayer.data;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;
import java.util.UUID;
import me.stumper66.findplayer.FindPlayer;
import org.jetbrains.annotations.NotNull;

public class PlayerCacheJson extends PlayerCacheBase implements IPlayerCache {

    public PlayerCacheJson(
        final FindPlayer main,
        final File dataDirectory,
        long writeTimeMs,
        final boolean debugEnabled
    ) {
        this.main = main;
        this.useDebug = debugEnabled;
        final GsonBuilder builder = new GsonBuilder();
        builder.registerTypeAdapter(LocalDateTime.class, LocalDateTimeJsonAdapter.INSTANCE);
        gs = builder.create();

        this.dataFile = new File(dataDirectory, "PlayerInfo.json");
        timer = new Timer();
        final TimerTask timerTask = new TimerTask() {

            @Override
            public void run() {
                runWriter();
            }
        };

        timer.schedule(timerTask, 1000L, writeTimeMs);
    }

    private final FindPlayer main;
    private boolean isDirty = false; // used for flat file writes
    private final Gson gs;
    private boolean useDebug;
    private static final Object lockObj = new Object();
    private final Timer timer;

    private void runWriter() {
        synchronized(lockObj) {
            if(!isDirty) {
                return;
            }
        }

        if(useDebug) {
            main.getLogger().info("writer queue has work");
        }

        writeToDisk();
    }

    public void close() {
        timer.cancel();
        if(!isDirty) {
            return;
        }

        writeToDisk();
    }

    public void updateDebug(final boolean useDebug) {
        this.useDebug = useDebug;
    }

    public void purgeData() {
        synchronized(lockObj) {
            this.mapping.clear();
            this.nameMappings.clear();
            this.isDirty = true;
        }
    }

    public void addOrUpdatePlayerInfo(@NotNull final PlayerStoreInfo psi) {
        psi.lastOnline = LocalDateTime.now();

        synchronized(lockObj) {
            this.mapping.put(psi.userId, psi);
            this.nameMappings.put(psi.playerName, psi.userId);
            this.isDirty = true;
        }
    }

    public void writeToDisk() {

        synchronized(lockObj) {
            final String fileJson = gs.toJson(this.mapping);

            try(Writer fr = new OutputStreamWriter(new FileOutputStream(this.dataFile),
                StandardCharsets.UTF_8)) {
                fr.write(fileJson);
                this.isDirty = false;
            } catch(IOException e) {
                main.getLogger().warning("Error writing to disk: " + e.getMessage());
            }
            this.isDirty = false;
        } // end lock
    }

    public void populateData() {
        if(!this.dataFile.exists()) {
            return;
        }

        if(useDebug) {
            main.getLogger().info("About to read: " + dataFile.getAbsolutePath());
        }
        String jsonStr;

        try {
            jsonStr = new String(Files.readAllBytes(Paths.get(dataFile.getPath())),
                StandardCharsets.UTF_8);
        } catch(IOException e) {
            main.getLogger().warning("Error reading json file: " + e.getMessage());
            return;
        }

        final Type useType = new TypeToken<HashMap<UUID, PlayerStoreInfo>>() {
            @SuppressWarnings("unused")
            private static final long serialVersionUID = 1L;
        }.getType();
        this.mapping = gs.fromJson(jsonStr, useType);

        for(final Map.Entry<UUID, PlayerStoreInfo> entry : this.mapping.entrySet()) {
            final UUID k = entry.getKey();
            final PlayerStoreInfo v = entry.getValue();
            nameMappings.put(v.playerName, k);
        }

        if(useDebug) {
            main.getLogger().info("items count: " + mapping.size() + ", name mappings: " +
                nameMappings.size());
        }
    }
}
