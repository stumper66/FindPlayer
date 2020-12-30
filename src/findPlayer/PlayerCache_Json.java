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
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.UUID;

import com.google.common.reflect.TypeToken;
import com.google.gson.Gson;

public class PlayerCache_Json extends PlayerCache_Base implements IPlayerCache {
	public PlayerCache_Json(File dataDirectory) {
		gs = new Gson();
	}
	
	private Boolean _isDirty; // used for flat file writes
	private File dataFile;
	private Gson gs;
	
	public Boolean getIsDirty(){
		return _isDirty;
	}
		
	public void AddOrUpdatePlayerInfo(PlayerStoreInfo psi) {
		psi.lastOnline = LocalDateTime.now();
		Mapping.put(psi.userId, psi);
		NameMappings.put(psi.playerName, psi.userId);
		
		this._isDirty = true;
		
		// TODO: queue the disk writes instead of writing on each add entry
		try {
			WriteToDisk();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
			
	public void WriteToDisk() throws IOException {
		String fileJson = gs.toJson(this.Mapping);
        
		Writer fr = null;
        try {
        	fr = new OutputStreamWriter(new FileOutputStream(this.dataFile), StandardCharsets.UTF_8);
            fr.write(fileJson);
            this._isDirty = false;
        } catch (IOException e) {
            e.printStackTrace();
        }finally{
            try {
                if (fr != null) fr.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
	}
	
	public void PopulateData() {
		if (!this.dataFile.exists()) return;
		
		//logger.info("about to read: " + dataFile.getAbsolutePath());
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
		
		//logger.info("items count: " + Mapping.size() + ", name mappings: " + NameMappings.size());
	}
}
