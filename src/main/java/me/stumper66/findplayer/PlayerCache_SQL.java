package me.stumper66.findplayer;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

public class PlayerCache_SQL extends PlayerCache_Base implements IPlayerCache {

    public PlayerCache_SQL(final MySQL_ConfigInfo config, final boolean debugEnabled) {
        // since this constructor was used we are using mysql
        this.useDebug = debugEnabled;
        this.config = config;
        this.isSqlLite = false;
    }

    public PlayerCache_SQL(final File dataDirectory, final boolean debugEnabled) {
        // if no constructor is used, then this is sqlite
        this.useDebug = debugEnabled;
        this.isSqlLite = true;
        this.whichSQL = "sqlite";
        this.dataFile = new File(dataDirectory, "PlayerInfo.db");
    }

    private MySQL_ConfigInfo config;
    private Connection connection;
    private boolean isReady = false;
    private boolean writerIsWorking;
    private WriterClass writer;
    private boolean useDebug;
    private final boolean isSqlLite;
    private String whichSQL = "mysql";

    public void openConnection() {
        try {
            if(connection != null && !connection.isClosed()) {
                return;
            }
        } catch(SQLException e) {
            Helpers.logger.warning("Error checking " + whichSQL + " connection. " + e.getMessage());
            return;
        }

        String connString = null;
        String createStatement;

        if(this.isSqlLite) {
            createStatement = "CREATE TABLE IF NOT EXISTS \"playerLocations\" ("
                + "	\"userId\"	TEXT NOT NULL UNIQUE,"
                + "	\"playerName\"	TEXT NOT NULL,"
                + "	\"locationX\"	INTEGER NOT NULL,"
                + "	\"locationY\"	INTEGER NOT NULL,"
                + "	\"locationZ\"	INTEGER NOT NULL,"
                + "	\"playerWorld\"	TEXT NOT NULL,"
                + "	\"lastSeen\"	TEXT NOT NULL,"
                + "	\"wgRegions\"	TEXT,"
                + "	PRIMARY KEY(\"userId\"))";
        } else {
            createStatement = "CREATE TABLE IF NOT EXISTS playerLocations ("
                + "userId VARCHAR(255) PRIMARY KEY,"
                + "playerName VARCHAR(255),"
                + "locationX INT,"
                + "locationY INT,"
                + "locationZ INT,"
                + "playerWorld VARCHAR(255),"
                + "lastSeen TIMESTAMP,"
                + "wgRegions VARCHAR(255)"
                + ");";

            final int port = 3306;
            connString = String.format(
                "jdbc:mysql://%s:%s/%s?useSSL=false",
                config.hostname, port, config.database);
        }

        try {
            if(this.isSqlLite) {
                Class.forName("org.sqlite.JDBC");
                connection = DriverManager.getConnection("jdbc:sqlite:" + this.dataFile.getPath());
            } else {
                Class.forName("com.mysql.jdbc.Driver");
                connection = DriverManager.getConnection(connString, config.username,
                    config.password);
            }
        } catch(Exception e) {
            Helpers.logger.warning("Unable to open " + whichSQL + ". " + e.getMessage());
            return;
        }

        try {
            openConnection();
            final Statement statement = connection.createStatement();
            statement.execute(createStatement);
        } catch(SQLException e) {
            Helpers.logger.warning("Error executing " + whichSQL + " query. " + e.getMessage());
            return;
        }

        writerIsWorking = true;
        writer = new WriterClass();
        final Thread thread = new Thread(writer);
        thread.start();
    }

    public void close() {
        if(writer != null && writerIsWorking) {
            writer.doLoop = false;
            // wait up to 100 milliseconds to complete
            try {
                for(int i = 0; i < 50; i++) {
                    Thread.sleep(2);
                    if(!writerIsWorking) {
                        break;
                    }
                }
            } catch(InterruptedException ignored) {
            }
        }

        try {
            if(connection != null && !connection.isClosed()) {
                connection.close();
            }
        } catch(SQLException ignored) {
        }
    }

    public void updateDebug(final boolean useDebug) {
        this.useDebug = useDebug;
    }

    public void purgeData() {
        if(writer != null) {
            writer.purgeQueue();
        }
        this.mapping.clear();
        this.nameMappings.clear();

        try {
            final Statement query = connection.createStatement();
            query.execute("DELETE FROM playerLocations");
        } catch(SQLException e) {
            Helpers.logger.warning("Error deleting from " + whichSQL + ". " + e.getMessage());
        }
    }

    public void addOrUpdatePlayerInfo(final PlayerStoreInfo psi) {
        if(!isReady) {
            return;
        }

        psi.lastOnline = LocalDateTime.now();
        writer.addItem(psi);
    }

    public PlayerStoreInfo getPlayerInfo(final String playername) {
        if(!isReady) {
            return null;
        }

        return queryDB(null, playername);
    }

    public PlayerStoreInfo getPlayerInfo(final UUID userId) {
        if(!isReady) {
            return null;
        }

        return queryDB(userId.toString(), null);
    }

    private PlayerStoreInfo queryDB(String userId, String playerName) {
        //                          1        2            3           4          5          6           7         8
        final String queryPre =
            "SELECT userId, playerName, locationX, locationY, locationZ, playerWorld, lastSeen, wgRegions "
                +
                "FROM playerLocations WHERE ";
        final String queryId = "userId = ?";
        final String queryPlayer = "playerName = ?";
        String queryMain;
        boolean useId = false;

        if(Helpers.isNullOrEmpty(playerName)) {
            queryMain = queryPre + queryId;
            useId = true;
        } else {
            queryMain = queryPre + queryPlayer;
        }

        try {
            final PreparedStatement query = connection.prepareStatement(queryMain);
            if(useId) {
                query.setString(1, userId);
            } else {
                query.setString(1, playerName);
            }

            final ResultSet result = query.executeQuery();
            if(!result.next()) {
                return null;
            }

            return getPlayerInfoFromQuery(result);
        } catch(SQLException e) {
            Helpers.logger.warning("Error querying " + whichSQL + ". " + e.getMessage());
        }

        return null;
    }

    public void populateData() {
        try {
            final Statement query = connection.createStatement();
            final ResultSet result = query.executeQuery(
                "SELECT userId, playerName, locationX, locationY, locationZ, playerWorld, lastSeen, wgRegions "
                    +
                    "FROM playerLocations");

            while(result.next()) {
                final PlayerStoreInfo psi = getPlayerInfoFromQuery(result);
                this.mapping.put(psi.userId, psi);
            }
        } catch(SQLException e) {
            Helpers.logger.warning("Unable to query " + whichSQL + "." + e.getMessage());
            if(writer != null) {
                writer.doLoop = false;
            }
            isReady = false;
            return;
        }

        for(final Map.Entry<UUID, PlayerStoreInfo> entry : this.mapping.entrySet()) {
            final UUID k = entry.getKey();
            final PlayerStoreInfo v = entry.getValue();
            nameMappings.put(v.playerName, k);
        }

        if(useDebug) {
            Helpers.logger.info(
                "items count: " + mapping.size() + ", name mappings: " + nameMappings.size());
        }
    }

    private PlayerStoreInfo getPlayerInfoFromQuery(final ResultSet result) throws SQLException {
        final UUID id = UUID.fromString(result.getString(1));
        final PlayerStoreInfo psi = new PlayerStoreInfo(id);
        psi.playerName = result.getString(2);
        psi.locationX = result.getInt(3);
        psi.locationY = result.getInt(4);
        psi.locationZ = result.getInt(5);
        psi.worldName = result.getString(6);
        if(this.isSqlLite) {
            psi.lastOnline = LocalDateTime.parse(result.getString(7));
        } else {
            final Timestamp ts = (Timestamp) result.getObject(7);
            psi.lastOnline = ts.toLocalDateTime();
        }

        psi.regionNames = result.getString(8);

        return psi;
    }

    private class WriterClass implements Runnable {

        public boolean doLoop = true;
        private LinkedBlockingQueue<PlayerStoreInfo> queue;

        public void addItem(final PlayerStoreInfo psi) {
            queue.add(psi);
        }

        public void purgeQueue() {
            queue.clear();
        }

        public void run() {
            queue = new LinkedBlockingQueue<>();

            //                                                1       2           3          4           5          6            7          8
            String cmdText =
                "INSERT INTO playerLocations (userId, playerName, locationX, locationY, locationZ, playerWorld, lastSeen, wgRegions)"
                    //           1  2  3  4  5  6  7  8
                    + "VALUES (?, ?, ?, ?, ?, ?, ?, ?) "
                    //                                      9              10             11             12               13
                    + "ON DUPLICATE KEY UPDATE playerName = ?, locationX = ?, locationY = ?, locationZ = ?, playerWorld = ?"
                    +
                    //            14             15
                    ", lastSeen = ?, wgRegions = ?";

            if(isSqlLite) {
                cmdText = cmdText.replace("ON DUPLICATE KEY UPDATE",
                    "ON CONFLICT(userId) DO UPDATE SET");
            }

            PreparedStatement statement;
            try {
                statement = connection.prepareStatement(cmdText);
            } catch(SQLException e) {
                Helpers.logger.warning("Error creating SQL writer statement. " + e.getMessage());
                return;
            }

            isReady = true;
            if(useDebug) {
                Helpers.logger.info("sql ready for updates");
            }

            try {
                // --------------------- begin writer loop -----------------------
                while(doLoop) {
                    final PlayerStoreInfo item = queue.poll(200, TimeUnit.MILLISECONDS);
                    if(item == null) {
                        continue;
                    }

                    processItem(item, statement);
                }
                // 	--------------------- end writer loop ----------------------
            } catch(SQLException | InterruptedException e) {
                Helpers.logger.warning("Error updating SQL (writer queue). " + e.getMessage());
            }
        } // end run
    }

    private void processItem(final PlayerStoreInfo item, final PreparedStatement statement)
        throws SQLException {
        if(useDebug) {
            Helpers.logger.info("writer queue has work");
        }

        // has a valid item needing to write to SQL
        statement.setString(1, item.userId.toString());
        statement.setString(2, item.playerName);
        statement.setInt(3, item.locationX);
        statement.setInt(4, item.locationY);
        statement.setInt(5, item.locationZ);
        statement.setString(6, item.worldName);
        if(isSqlLite) {
            statement.setString(7, item.lastOnline.toString());
        } else {
            statement.setObject(7, item.lastOnline);
        }
        statement.setString(8, item.regionNames);

        // now (mostly) duplicate the values for the update statement (good old java eh?)
        statement.setString(9, item.playerName);
        statement.setInt(10, item.locationX);
        statement.setInt(11, item.locationY);
        statement.setInt(12, item.locationZ);
        statement.setString(13, item.worldName);
        if(isSqlLite) {
            statement.setString(14, item.lastOnline.toString());
        } else {
            statement.setObject(14, item.lastOnline);
        }
        statement.setString(15, item.regionNames);

        statement.execute();
        if(useDebug) {
            Helpers.logger.info("inserted or updated entry to sql");
        }
    }

    public static class MySQL_ConfigInfo {

        public String hostname;
        public String username;
        public String password;
        public String database;
    }
}
