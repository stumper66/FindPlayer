package findPlayer;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.sql.PreparedStatement;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;

public class PlayerCache_SQL extends PlayerCache_Base implements IPlayerCache {
	public PlayerCache_SQL(MySQL_ConfigInfo config, Boolean debugEnabled) {
		// since this constructor was used we are using mysql
		this.useDebug = debugEnabled;
		this.config = config;
		this.isSqlLite = false;
	}
	
	public PlayerCache_SQL(File dataDirectory, Boolean debugEnabled) {
		// if no constructor is used, then this is sqlite
		this.useDebug = debugEnabled;
		this.isSqlLite = true;
		this.whichSQL = "sqlite";
		this.dataFile = new File(dataDirectory, "PlayerInfo.db");
	}
	
	private MySQL_ConfigInfo config;
	private Connection connection;
	private Boolean isReady = false;
	private Boolean writerIsWorking;
	private final int port = 3306;
	private WriterClass writer;
	private Boolean useDebug;
	private final Boolean isSqlLite;
	private String whichSQL = "mysql";
	
	public Boolean openConnection() {
	    try {
			if (connection != null && !connection.isClosed()) return true;
		} catch (SQLException e) {
			logger.warning("Error checking " + whichSQL + " connection. " + e.getMessage());
			return false;
		}
		
	    String connString = null;
	    String createStatement = null;
	    
	    if (this.isSqlLite) {
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
	    	
			connString = String.format(
					"jdbc:mysql://%s:%s/%s?useSSL=false",
					config.hostname, this.port, config.database);
	    }
		
	    try {
	    	if (this.isSqlLite) {
	    		Class.forName("org.sqlite.JDBC");
			    connection = DriverManager.getConnection("jdbc:sqlite:" + this.dataFile.getPath());
	    	} else {
		        Class.forName("com.mysql.jdbc.Driver");
		        connection = DriverManager.getConnection(connString, config.username, config.password);	    		
	    	}
	    } catch (Exception e) {
	        logger.warning("Unable to open " + whichSQL + ". " + e.getMessage());
	        return false;
	    }
	    
	    try {    
	        openConnection();
	        Statement statement = connection.createStatement();
	        statement.execute(createStatement);
	    } catch (SQLException e) {
	        logger.warning("Error executing " + whichSQL +" query. " + e.getMessage());
	        return false;
	    }
	    
	    writerIsWorking = true;
	    writer = new WriterClass();
	    Thread thread = new Thread(writer);
	    thread.start();
	    
	    return true;
	}
	
	public void Close(){
		if (writer != null && writerIsWorking) {
			writer.doLoop = false;
			// wait up to 100 milliseconds to complete
			try {
				for (int i = 0; i < 50; i++) {
					Thread.sleep(2);
					if (!writerIsWorking) break;
				}
			}
			catch (InterruptedException e) { }
		}
		
		try {
			if (connection != null && !connection.isClosed())
				connection.close();
		} catch(SQLException e) { }
	}
	
	public void UpdateDebug(Boolean useDebug) {
		this.useDebug = useDebug;
	}
	
	public void UpdateFileWriteTime(long fileWriteTimeMs) {
		// not used in this class
	}
	
	public void PurgeData() {
		if (writer != null) writer.PurgeQueue();
		this.Mapping.clear();
		this.NameMappings.clear();
		
		try {
			Statement query = connection.createStatement();
			query.execute("DELETE FROM playerLocations");
		}
		catch (SQLException e) {
			logger.warning("Error deleting from " + whichSQL + ". " + e.getMessage());
		}
	}

	public void AddOrUpdatePlayerInfo(PlayerStoreInfo psi) {
		if (!isReady) return;

		psi.lastOnline = LocalDateTime.now();
		writer.addItem(psi);
	}
	
	public PlayerStoreInfo GetPlayerInfo(String playerName) {
		if (!isReady) return null;
		
		return QueryDB(null, playerName);
	}
	
	public PlayerStoreInfo GetPlayerInfo(UUID userId) {
		if (!isReady) return null;
		
		return QueryDB(userId.toString(), null);
	}
	
	private PlayerStoreInfo QueryDB(String userId, String playerName) {
		//                          1        2            3           4          5          6           7         8
		String queryPre = "SELECT userId, playerName, locationX, locationY, locationZ, playerWorld, lastSeen, wgRegions " +
						"FROM playerLocations WHERE ";
		String queryId  = "userId = ?";
		String queryPlayer  = "playerName = ?";
		String queryMain = null;
		Boolean useId = false;
		
		if (Helpers.isNullOrEmpty(playerName)) {
			queryMain = queryPre + queryId;
			useId = true;
		}
		else {
			queryMain = queryPre + queryPlayer;
		}
		
		try {
			PreparedStatement query = connection.prepareStatement(queryMain);
			if (useId)
				query.setString(1, userId);
			else
				query.setString(1, playerName);
		
			ResultSet result = query.executeQuery();
			if (!result.next()) return null;
			
			PlayerStoreInfo psi = getPlayerInfoFromQuery(result);
			
			return psi;
		}
		catch (SQLException e) {
			logger.warning("Error querying " + whichSQL + ". " + e.getMessage());
		}
		
		return null;
	}
	
	public void PopulateData() {
		try {
			Statement query = connection.createStatement();
			ResultSet result = query.executeQuery("SELECT userId, playerName, locationX, locationY, locationZ, playerWorld, lastSeen, wgRegions " +
					"FROM playerLocations");
			
			while (result.next()) {
				PlayerStoreInfo psi = getPlayerInfoFromQuery(result);
				
				this.Mapping.put(psi.userId, psi);
			}
		}
		catch (SQLException e) {
			logger.warning("Unable to query " + whichSQL + "." + e.getMessage());
			if (writer != null) writer.doLoop = false;
			isReady = false;
			return;
		}
		
		this.Mapping.forEach((k,v) -> {
			NameMappings.put(v.playerName, k);
		});
		
		if (useDebug) logger.info("items count: " + Mapping.size() + ", name mappings: " + NameMappings.size());
	}
	
	private PlayerStoreInfo getPlayerInfoFromQuery(ResultSet result) throws SQLException {
		UUID id = UUID.fromString(result.getString(1));
		PlayerStoreInfo psi = new PlayerStoreInfo(id);
		psi.playerName = result.getString(2);
		psi.locationX = result.getInt(3);
		psi.locationY = result.getInt(4);
		psi.locationZ = result.getInt(5);
		psi.worldName = result.getString(6);
		if (this.isSqlLite) {
			psi.lastOnline = LocalDateTime.parse(result.getString(7));
		} else {
			Timestamp ts = (Timestamp)result.getObject(7);
			psi.lastOnline = ts.toLocalDateTime();			
		}

		psi.regionNames = result.getString(8);
		
		return psi;
	}
	
	private class WriterClass implements Runnable{
		
		private PreparedStatement statement;
		private volatile Boolean hasWork = false;
		public Boolean doLoop = true;
		private ConcurrentLinkedQueue<PlayerStoreInfo> queue;
		private final Object lockObj = new Object();
		
		public void addItem(PlayerStoreInfo psi) {
			synchronized(lockObj) {
				queue.add(psi);
				hasWork = true;
			}
		}
		
		public void PurgeQueue() {
			synchronized(lockObj) {
				queue.clear();
				hasWork = false;
			}
		}
		
		public void run() {
			queue = new ConcurrentLinkedQueue<PlayerStoreInfo>();
			
			//                                                1       2           3          4           5          6            7          8
			String cmdText = "INSERT INTO playerLocations (userId, playerName, locationX, locationY, locationZ, playerWorld, lastSeen, wgRegions)"
					//           1  2  3  4  5  6  7  8
					  + "VALUES (?, ?, ?, ?, ?, ?, ?, ?) "
					  //                                      9              10             11             12               13
					  + "ON DUPLICATE KEY UPDATE playerName = ?, locationX = ?, locationY = ?, locationZ = ?, playerWorld = ?" +
					  //            14             15
					  ", lastSeen = ?, wgRegions = ?";
			
			if (isSqlLite) {
				cmdText = cmdText.replace("ON DUPLICATE KEY UPDATE", "ON CONFLICT(userId) DO UPDATE SET");
			}
			
			try {
				statement = connection.prepareStatement(cmdText);
			}
			catch (SQLException e) {
				logger.warning("Error creating SQL writer statement. " + e.getMessage());
				return;
			}
			
			isReady = true;
			if (useDebug) logger.info("sql ready for updates");
			
			try {
				// --------------------- begin writer loop -----------------------
				while (doLoop) {
					if (!hasWork) {
						Thread.sleep(10);
						continue;
					}
					// loop above here if no work
				
					if (useDebug) logger.info("writer queue has work");
					
					PlayerStoreInfo item = null;
				
					synchronized(lockObj) {
						item = queue.poll();
						hasWork = !queue.isEmpty();
					}

					if (item == null) continue;
				
					// has a valid item needing to write to SQL
					statement.setString(1, item.userId.toString());
					statement.setString(2, item.playerName);
					statement.setInt(3, item.locationX);
					statement.setInt(4, item.locationY);
					statement.setInt(5, item.locationZ);
					statement.setString(6, item.worldName);
					if (isSqlLite) statement.setString(7, item.lastOnline.toString());
					else statement.setObject(7, item.lastOnline);
					statement.setString(8, item.regionNames);
					
					// now (mostly) duplicate the values for the update statement (good old java eh?)
					statement.setString(9, item.playerName);
					statement.setInt(10, item.locationX);
					statement.setInt(11, item.locationY);
					statement.setInt(12, item.locationZ);
					statement.setString(13, item.worldName);
					if (isSqlLite) statement.setString(14, item.lastOnline.toString());
					else statement.setObject(14, item.lastOnline);
					statement.setString(15, item.regionNames);
					
					statement.execute();
					if (useDebug) logger.info("inserted or updated entry to sql");
				}
				// 	--------------------- end writer loop ----------------------
			}
			catch (SQLException e) {
				logger.warning("Error updating SQL (writer queue). " + e.getMessage());
			}
			catch (InterruptedException e) {
				return;
			}
			
		} // end run 
	}
		
	public static class MySQL_ConfigInfo{
		public String hostname;
		public String username;
		public String password;
		public String database;
	}
}
