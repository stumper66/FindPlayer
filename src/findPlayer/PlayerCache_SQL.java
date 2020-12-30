package findPlayer;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.sql.PreparedStatement;
import java.util.logging.Logger;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;

public class PlayerCache_SQL extends PlayerCache_Base implements IPlayerCache {
	public PlayerCache_SQL(SQL_ConfigInfo config) {
		this.config = config;
	}
	
	private SQL_ConfigInfo config;
	private Connection connection;
	private Boolean isReady = false;
	private Boolean writerIsWorking;
	private final int port = 3306;
	private final Logger logger = Logger.getLogger("Minecraft");
	private WriterClass writer;
	
	public Boolean openConnection() {
	    try {
			if (connection != null && !connection.isClosed()) return true;
		} catch (SQLException e) {
			logger.warning("Error checking mysql connection. " + e.getMessage());
			return false;
		}
		
		String connString = String.format(
				"jdbc:mysql://%s:%s/%s?useSSL=false",
				config.hostname, this.port, config.database);
		
	    try {    
	        Class.forName("com.mysql.jdbc.Driver");
	        connection = DriverManager.getConnection(connString, config.username, config.password);
	    } catch (Exception e) {
	        logger.warning("Unable to open mysql. " + e.getMessage());
	        return false;
	    }
	    
	    try {    
	        openConnection();
	        Statement statement = connection.createStatement();
	        statement.execute("CREATE TABLE IF NOT EXISTS playerLocations ("
	        		+ "userId VARCHAR(255) PRIMARY KEY,"
	        		+ "playerName VARCHAR(255),"
	        		+ "locationX INT,"
	        		+ "locationY INT,"
	        		+ "locationZ INT,"
	        		+ "playerWorld VARCHAR(255),"
	        		+ "lastSeen TIMESTAMP,"
	        		+ "wgRegions VARCHAR(255)"
	        		+ ");");
	    } catch (SQLException e) {
	        logger.warning("Error executing mysql query. " + e.getMessage());
	        return false;
	    }
	    
	    writerIsWorking = true;
	    writer = new WriterClass();
	    Thread thread = new Thread(writer);
	    thread.start();
	    
	    return true;
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
			logger.warning("Error querying mysql. " + e.getMessage());
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
			logger.warning("Unable to query mysql." + e.getMessage());
			if (writer != null) writer.doLoop = false;
			isReady = false;
			return;
		}
		
		this.Mapping.forEach((k,v) -> {
			NameMappings.put(v.playerName, k);
		});
		
		logger.info("items count: " + Mapping.size() + ", name mappings: " + NameMappings.size());
	}
	
	private static PlayerStoreInfo getPlayerInfoFromQuery(ResultSet result) throws SQLException {
		UUID id = UUID.fromString(result.getString(1));
		PlayerStoreInfo psi = new PlayerStoreInfo(id);
		psi.playerName = result.getString(2);
		psi.locationX = result.getInt(3);
		psi.locationY = result.getInt(4);
		psi.locationZ = result.getInt(5);
		psi.worldName = result.getString(6);
		Timestamp ts = (Timestamp)result.getObject(7);
		psi.lastOnline = ts.toLocalDateTime();
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
		
		public void run() {
			queue = new ConcurrentLinkedQueue<PlayerStoreInfo>();
			
			//                                                1       2           3          4           5          6            7          8
			String cmdText = "INSERT INTO playerLocations (userId, playerName, locationX, locationY, locationZ, playerWorld, lastSeen, wgRegions)"
					//           1  2  3  4  5  6  7  8
					  + "VALUES (?, ?, ?, ?, ?, ?, ?, ?) "
					  //                                  9               10             11             12             13               14
					  + "ON DUPLICATE KEY UPDATE userId = ?, playerName = ?, locationX = ?, locationY = ?, locationZ = ?, playerWorld = ?" +
					  //            15             16
					  ", lastSeen = ?, wgRegions = ?";
			
			try {
				statement = connection.prepareStatement(cmdText);
			}
			catch (SQLException e) {
				logger.warning("Error creating SQL writer statement. " + e.getMessage());
				return;
			}
			
			isReady = true;
			logger.info("mysql ready for updates");
			
			try {
				// --------------------- begin writer loop -----------------------
				while (doLoop) {
					if (!hasWork) {
						Thread.sleep(10);
						continue;
					}
					// loop above here if no work
				
					logger.info("writer queue has work");
					
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
					statement.setObject(7, item.lastOnline);
					statement.setString(8, item.regionNames);
					
					// now duplicate the values for the update statement (good old java eh?)
					statement.setString(9, item.userId.toString());
					statement.setString(10, item.playerName);
					statement.setInt(11, item.locationX);
					statement.setInt(12, item.locationY);
					statement.setInt(13, item.locationZ);
					statement.setString(14, item.worldName);
					statement.setObject(15, item.lastOnline);
					statement.setString(16, item.regionNames);
					
					statement.execute();
					logger.info("inserted or updated entry to mysql");
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
	
	public void closeConnection() {
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
	
	public static class SQL_ConfigInfo{
		public String hostname;
		public String username;
		public String password;
		public String database;
	}
}
