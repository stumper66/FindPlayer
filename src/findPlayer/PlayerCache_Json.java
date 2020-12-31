package findPlayer;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.UUID;

import com.google.common.reflect.TypeToken;
import com.google.gson.Gson;

public class PlayerCache_Json extends PlayerCache_Base implements IPlayerCache {
	public PlayerCache_Json(File dataDirectory, long writeTimeMs, Boolean debugEnabled) {
		this.useDebug = debugEnabled;
		gs = new Gson();
		this.dataFile = new File(dataDirectory, "PlayerInfo.json");
		this.writeTimeMs = writeTimeMs;
		writer = new WriterClass();
		Thread thread = new Thread(writer);
	    thread.start();
	}
	
	private Boolean _isDirty = false; // used for flat file writes
	private Gson gs;
	private WriterClass writer;
	private long writeTimeMs;
	private Boolean useDebug;
	private final Object lockObj = new Object();
	
	public Boolean getIsDirty(){
		return _isDirty;
	}
	
	public void Close() {
		if (writer != null) writer.doLoop = false;
		if (!_isDirty) return;
		
		//TODO: once there is a disk writer queue then we'll need to make sure all is written here
		WriteToDisk();
	}
	
	public void UpdateDebug(Boolean useDebug) {
		this.useDebug = useDebug;
	}
	
	public void UpdateFileWriteTime(long fileWriteTimeMs) {
		this.writeTimeMs = fileWriteTimeMs;
	}
	
	public void PurgeData() {
		synchronized(lockObj) {
			this.Mapping.clear();
			this.NameMappings.clear();
			this._isDirty = true;
		}
	}
		
	public void AddOrUpdatePlayerInfo(PlayerStoreInfo psi) {
		psi.lastOnline = LocalDateTime.now();
		
		synchronized(lockObj) {
			Mapping.put(psi.userId, psi);
			NameMappings.put(psi.playerName, psi.userId);
			this._isDirty = true;
		}
	}
			
	public void WriteToDisk() {
		
		synchronized(lockObj) {
			String fileJson = gs.toJson(this.Mapping);
        
			Writer fr = null;
			try {
				fr = new OutputStreamWriter(new FileOutputStream(this.dataFile), StandardCharsets.UTF_8);
				fr.write(fileJson);
				this._isDirty = false;
			} catch (IOException e) {
				logger.warning("Error writing to disk. " + e.getMessage());
			}finally{
				try {
					if (fr != null) fr.close();
				} catch (IOException e) { }
			}
			this._isDirty = false;
		} // end lock
	}
	
	public void PopulateData() {
		if (!this.dataFile.exists()) return;
		
		if (useDebug) logger.info("about to read: " + dataFile.getAbsolutePath());
		String jsonStr = null;
		
		try {
			jsonStr = new String(Files.readAllBytes(Paths.get(dataFile.getPath())), StandardCharsets.UTF_8);
		} catch (IOException e) {
			logger.warning("error reading json file. " + e.getMessage());
			return;
		}
		
		final Type useType = new TypeToken<HashMap<UUID, PlayerStoreInfo>>(){
			private static final long serialVersionUID = 1L;}.getType();
		this.Mapping = gs.fromJson(jsonStr, useType);
		
		this.Mapping.forEach((k,v) -> {
			NameMappings.put(v.playerName, k);
		});
		
		if (useDebug) logger.info("items count: " + Mapping.size() + ", name mappings: " + NameMappings.size());
	}
	
private class WriterClass implements Runnable{
		
		private volatile Boolean needsWrite = false;
		public Boolean doLoop = true;
				
		public void run() {
			Instant starts = Instant.now();
			//Instant ends = Instant.now();
			//System.out.println(Duration.between(starts, ends));
			
			if (useDebug) logger.info("json writer ready");
			
			try {
				// --------------------- begin writer loop -----------------------
				while (doLoop) {
					if (!needsWrite) {
						Thread.sleep(50);
						
						Instant ends = Instant.now();
						Duration dur = Duration.between(starts, ends);
						if (dur.toMillis() < writeTimeMs) continue;
					}
					// loop above here if no work
					
					synchronized(lockObj){
						if (!_isDirty) {
							needsWrite = false;
							starts = Instant.now();		
							continue;
						}
					}
				
					if (useDebug) logger.info("writer queue has work");
					
					WriteToDisk();
					needsWrite = false;
					starts = Instant.now();
				}
				// 	--------------------- end writer loop ----------------------
			}
			catch (InterruptedException e) {
				return;
			}
			
		} // end run 
	}
}
