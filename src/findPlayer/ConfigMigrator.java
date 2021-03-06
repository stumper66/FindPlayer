package findPlayer;

import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.util.FileUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.annotation.Nonnull;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.util.*;

public class ConfigMigrator {

    final private static int currentConfigVersion = 2;

    public static void checkConfigVersion(final FindPlayer main){

        final int version = main.config.getInt("file-version", 0);

        if (version >= currentConfigVersion) return;

        final File backedupFile = new File(main.getDataFolder(), "config.yml.v" + version);
        final File configFile = new File(main.getDataFolder(), "config.yml");
        FileUtil.copy(configFile, backedupFile);

        main.saveResource(configFile.getName(), true);

        Helpers.logger.info("&fConfig Migrator: &7Migrating &bconfig.yml&7 from old version to new version.");
        migrateConfig(backedupFile, configFile);
    }

    private static void migrateConfig(File from, @NotNull File to){
        final String regexPattern = "^[^':]*:.*";
        final List<String> processedKeys = new LinkedList<>();

        try {
            final List<String> oldConfigLines = Files.readAllLines(from.toPath(), StandardCharsets.UTF_8);
            final List<String> newConfigLines = Files.readAllLines(to.toPath(), StandardCharsets.UTF_8);

            final SortedMap<String, FieldInfo> oldConfigMap = getMapFromConfig(oldConfigLines);
            final SortedMap<String, FieldInfo> newConfigMap = getMapFromConfig(newConfigLines);
            final List<String> currentKey = new LinkedList<>();
            int keysMatched = 0;
            int valuesUpdated = 0;
            int valuesMatched = 0;

            for (int currentLine = 0; currentLine < newConfigLines.size(); currentLine++) {
                String line = newConfigLines.get(currentLine);
                final int depth = getFieldDepth(line);
                if (line.trim().startsWith("#") || line.trim().isEmpty()) continue;

                if (line.matches(regexPattern)) {
                    final int firstColon = line.indexOf(":");
                    String key = line.substring(0, firstColon).replace("\t", "").trim();

                    if (depth == 0)
                        currentKey.clear();
                    else if (currentKey.size() > depth) {
                        while (currentKey.size() > depth) currentKey.remove(currentKey.size() - 1);
                        key = getKeyFromList(currentKey, key);
                    }
                    else
                        key = getKeyFromList(currentKey, key);

                    if (oldConfigMap.containsKey(key)) {
                        keysMatched++;
                        final String value = line.substring(firstColon + 1).trim();
                        final FieldInfo fi = oldConfigMap.get(key);
                        final String migratedValue = fi.simpleValue;

                        if (key.toLowerCase().startsWith("file-version")) continue;

                        final String parentKey = getParentKey(key);
                        if (fi.hasValue && parentKey != null && !processedKeys.contains(parentKey)){
                            // here's where we add values from the old config not present in the new
                            for (final String oldValue : oldConfigMap.keySet()){
                                if (!oldValue.startsWith(parentKey)) continue;
                                if (newConfigMap.containsKey(oldValue)) continue;
                                if (!isEntitySameSubkey(parentKey, oldValue)) continue;

                                FieldInfo fiOld = oldConfigMap.get(oldValue);
                                if (fiOld.isList()) continue;
                                final String padding = " ".repeat(depth * 2);
                                final String newline = padding + getEndingKey(oldValue) + ": " + fiOld.simpleValue;
                                newConfigLines.add(currentLine + 1, newline);
                                Helpers.logger.info("&fConfig Migrator: &7Adding key: &b" + oldValue + "&7, value: &r" + fiOld.simpleValue + "&7.");
                            }
                            processedKeys.add(parentKey);
                        }

                        if (!value.equals(migratedValue)) {
                            if (migratedValue != null) {
                                valuesUpdated++;
                                Helpers.logger.info("&fConfig Migrator: &7Current key: &b" + key + "&7, replacing: &r" + value + "&7, with: &r" + migratedValue + "&7.");
                                line = line.replace(value, migratedValue);
                                newConfigLines.set(currentLine, line);
                            }
                        } else
                            valuesMatched++;
                    }
                }
            } // next line

            Helpers.logger.info(String.format("&fConfig Migrator: &7Keys matched: &b%s&7, values matched: &b%s&7, values updated: &b%s&7.",
                    keysMatched, valuesMatched, valuesUpdated));
            Files.write(to.toPath(), newConfigLines, StandardCharsets.UTF_8, StandardOpenOption.TRUNCATE_EXISTING);
        }
        catch (Exception e){
            Helpers.logger.error("&fConfig Migrator: &7Failed to migrate &b" + to.getName() + "&7! Stack trace:");
            e.printStackTrace();
        }
    }

    @Nonnull
    private static SortedMap<String, FieldInfo> getMapFromConfig(@NotNull List<String> input) {
        final SortedMap<String, FieldInfo> configMap = new TreeMap<>();
        final List<String> currentKey = new ArrayList<>();
        final String regexPattern = "^[^':]*:.*";

        for (String line : input) {
            final int depth = getFieldDepth(line);
            line = line.replace("\t", "").trim();
            if (line.startsWith("#") || line.isEmpty()) continue;

            //if (line.contains(":")) {
            if (line.matches(regexPattern)) {
                int firstColon = line.indexOf(":");
                boolean hasValues = line.length() > firstColon + 1;
                String key = line.substring(0, firstColon).replace("\t", "").trim();
                final String origKey = key;

                if (origKey.startsWith("-")) {
                    if (currentKey.size() > depth)
                        while (currentKey.size() > depth) currentKey.remove(currentKey.size() - 1);
                    String temp = origKey.substring(1).trim();
                    String tempKey;
                    for (int i = 0; i < 100; i++) {
                        tempKey = String.format("%s[%s]", temp, i);
                        final String checkKey = getKeyFromList(currentKey, tempKey);
                        if (!configMap.containsKey(checkKey)) {
                            currentKey.add(tempKey);
                            configMap.put(checkKey, null);
                            break;
                        }
                    }
                    continue;
                }

                if (depth == 0)
                    currentKey.clear();
                else {
                    if (currentKey.size() > depth)
                        while (currentKey.size() > depth) currentKey.remove(currentKey.size() - 1);
                    key = getKeyFromList(currentKey, key);
                }

                if (!hasValues) {
                    currentKey.add(origKey);
                    if (!configMap.containsKey(key)) configMap.put(key, new FieldInfo(null, depth));
                } else {
                    final String value = line.substring(firstColon + 1).trim();
                    final FieldInfo fi = new FieldInfo(value, depth);
                    fi.hasValue = true;
                    configMap.put(key, fi);
                }
            } else if (line.startsWith("-")) {
                final String key = getKeyFromList(currentKey, null);
                final String value = line.trim().substring(1).trim();
                if (configMap.containsKey(key)) {
                    FieldInfo fi = configMap.get(key);
                    fi.addListValue(value);
                } else
                    configMap.put(key, new FieldInfo(value, depth, true));
            }
        }

        return configMap;
    }

    private static boolean isEntitySameSubkey(@NotNull final String key1, @NotNull final String key2){
        final int lastPeriod = key2.lastIndexOf(".");
        final String checkKey = lastPeriod > 0 ? key2.substring(0, lastPeriod) : key2;

        return (key1.equalsIgnoreCase(checkKey));
    }

    @NotNull
    private static String getEndingKey(@NotNull String input){
        final int lastPeriod = input.lastIndexOf(".");
        if (lastPeriod < 0) return input;

        return input.substring(lastPeriod + 1);
    }

    @Nullable
    private static String getParentKey(@NotNull String input){
        final int lastPeriod = input.lastIndexOf(".");
        if (lastPeriod < 0) return null;

        return input.substring(0, lastPeriod);
    }

    private static int getFieldDepth(@NotNull String line) {
        int whiteSpace = 0;

        for (int i = 0; i < line.length(); i++) {
            if (line.charAt(i) != ' ') break;
            whiteSpace++;
        }
        return whiteSpace == 0 ? 0 : whiteSpace / 2;
    }

    @NotNull
    private static String getKeyFromList(@NotNull final List<String> list, final String currentKey){
        if (list.size() == 0) return currentKey;

        String result = String.join(".", list);
        if (currentKey != null) result += "." + currentKey;

        return result;
    }

    private static class FieldInfo {
        public String simpleValue;
        public List<String> valueList;
        public final int depth;
        public boolean hasValue;

        public FieldInfo(String value, int depth) {
            this.simpleValue = value;
            this.depth = depth;
        }

        public FieldInfo(String value, int depth, boolean isListValue) {
            if (isListValue) addListValue(value);
            else this.simpleValue = value;
            this.depth = depth;
        }

        public boolean isList(){
            return valueList != null;
        }

        public void addListValue(String value){
            if (valueList == null) valueList = new ArrayList<>();
            valueList.add(value);
        }

        public String toString() {
            if (this.isList()){
                if (this.valueList == null || this.valueList.isEmpty())
                    return super.toString();
                else
                    return String.join(",", this.valueList);
            }

            if (this.simpleValue == null)
                return super.toString();
            else
                return this.simpleValue;
        }
    }
}
