package me.stumper66.findplayer.config;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.SortedMap;
import java.util.TreeMap;
import javax.annotation.Nonnull;
import me.stumper66.findplayer.FindPlayer;
import me.stumper66.findplayer.misc.Utils;
import org.bukkit.util.FileUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class ConfigMigrator {

    private final FindPlayer main;
    public ConfigMigrator(final FindPlayer main) {
        this.main = main;
    }

    final private static int currentConfigVersion = 2;

    public void checkConfigVersion() {

        final int version = main.getConfig().getInt("file-version", 0);

        if(version >= currentConfigVersion) {
            return;
        }

        final File backedupFile = new File(main.getDataFolder(), "config.yml.v" + version);
        final File configFile = new File(main.getDataFolder(), "config.yml");
        FileUtil.copy(configFile, backedupFile);

        main.saveResource(configFile.getName(), true);

        main.getLogger().info("Config Migrator: Migrating 'config.yml' from the old version "
            + "to the new version.");
        migrateConfig(backedupFile, configFile);
    }

    private void migrateConfig(File from, @NotNull File to) {
        final String regexPattern = "^[^':]*:.*";
        final List<String> processedKeys = new LinkedList<>();

        try {
            final List<String> oldConfigLines = Files.readAllLines(from.toPath(),
                StandardCharsets.UTF_8);
            final List<String> newConfigLines = Files.readAllLines(to.toPath(),
                StandardCharsets.UTF_8);

            final SortedMap<String, FieldInfo> oldConfigMap = getMapFromConfig(oldConfigLines);
            final SortedMap<String, FieldInfo> newConfigMap = getMapFromConfig(newConfigLines);
            final List<String> currentKey = new LinkedList<>();
            int keysMatched = 0;
            int valuesUpdated = 0;
            int valuesMatched = 0;

            for(int currentLine = 0; currentLine < newConfigLines.size(); currentLine++) {
                String line = newConfigLines.get(currentLine);
                final int depth = getFieldDepth(line);
                if(line.trim().startsWith("#") || line.trim().isEmpty()) {
                    continue;
                }

                if(line.matches(regexPattern)) {
                    final int firstColon = line.indexOf(":");
                    String key = line.substring(0, firstColon).replace("\t", "").trim();

                    if(depth == 0) {
                        currentKey.clear();
                    } else if(currentKey.size() > depth) {
                        while(currentKey.size() > depth) {
                            currentKey.remove(currentKey.size() - 1);
                        }
                        key = getKeyFromList(currentKey, key);
                    } else {
                        key = getKeyFromList(currentKey, key);
                    }

                    if(oldConfigMap.containsKey(key)) {
                        keysMatched++;
                        final String value = line.substring(firstColon + 1).trim();
                        final FieldInfo fi = oldConfigMap.get(key);
                        final String migratedValue = fi.simpleValue;

                        if(key.toLowerCase().startsWith("file-version")) {
                            continue;
                        }

                        final String parentKey = getParentKey(key);
                        if(fi.hasValue && parentKey != null && !processedKeys.contains(parentKey)) {
                            // here's where we add values from the old config not present in the new
                            for(final String oldValue : oldConfigMap.keySet()) {
                                if(!oldValue.startsWith(parentKey)) {
                                    continue;
                                }
                                if(newConfigMap.containsKey(oldValue)) {
                                    continue;
                                }
                                if(!isEntitySameSubkey(parentKey, oldValue)) {
                                    continue;
                                }

                                FieldInfo fiOld = oldConfigMap.get(oldValue);
                                if(fiOld.isList()) {
                                    continue;
                                }
                                final String padding = Utils.repeatString(" ", depth * 2);
                                final String newline =
                                    padding + getEndingKey(oldValue) + ": " + fiOld.simpleValue;
                                newConfigLines.add(currentLine + 1, newline);
                                main.getLogger().info("Config Migrator: Adding key '" + oldValue
                                    + "' with value '" + fiOld.simpleValue + "'.");
                            }
                            processedKeys.add(parentKey);
                        }

                        if(!value.equals(migratedValue)) {
                            if(migratedValue != null) {
                                valuesUpdated++;
                                main.getLogger().info("Config Migrator: Current key: '" + key
                                    + "', replacing: '" + value + "', with: '" + migratedValue
                                    + "'.");
                                line = line.replace(value, migratedValue);
                                newConfigLines.set(currentLine, line);
                            }
                        } else {
                            valuesMatched++;
                        }
                    }
                }
            } // next line

            main.getLogger().info(String.format(
                "Config Migrator: Keys matched: '%s', values matched: '%s', values updated: '%s'.",
                keysMatched, valuesMatched, valuesUpdated));
            Files.write(to.toPath(), newConfigLines, StandardCharsets.UTF_8,
                StandardOpenOption.TRUNCATE_EXISTING);
        } catch(Exception e) {
            main.getLogger().severe(
                "Config Migrator: Failed to migrate '" + to.getName() + "'! Stack trace:");
            e.printStackTrace();
        }
    }

    @Nonnull
    private static SortedMap<String, FieldInfo> getMapFromConfig(@NotNull List<String> input) {
        final SortedMap<String, FieldInfo> configMap = new TreeMap<>();
        final List<String> currentKey = new ArrayList<>();
        final String regexPattern = "^[^':]*:.*";

        for(String line : input) {
            final int depth = getFieldDepth(line);
            line = line.replace("\t", "").trim();
            if(line.startsWith("#") || line.isEmpty()) {
                continue;
            }

            //if (line.contains(":")) {
            if(line.matches(regexPattern)) {
                int firstColon = line.indexOf(":");
                boolean hasValues = line.length() > firstColon + 1;
                String key = line.substring(0, firstColon).replace("\t", "").trim();
                final String origKey = key;

                if(origKey.startsWith("-")) {
                    if(currentKey.size() > depth) {
                        while(currentKey.size() > depth) {
                            currentKey.remove(currentKey.size() - 1);
                        }
                    }
                    String temp = origKey.substring(1).trim();
                    String tempKey;
                    for(int i = 0; i < 100; i++) {
                        tempKey = String.format("%s[%s]", temp, i);
                        final String checkKey = getKeyFromList(currentKey, tempKey);
                        if(!configMap.containsKey(checkKey)) {
                            currentKey.add(tempKey);
                            configMap.put(checkKey, null);
                            break;
                        }
                    }
                    continue;
                }

                if(depth == 0) {
                    currentKey.clear();
                } else {
                    if(currentKey.size() > depth) {
                        while(currentKey.size() > depth) {
                            currentKey.remove(currentKey.size() - 1);
                        }
                    }
                    key = getKeyFromList(currentKey, key);
                }

                if(!hasValues) {
                    currentKey.add(origKey);
                    if(!configMap.containsKey(key)) {
                        configMap.put(key, new FieldInfo(null, depth));
                    }
                } else {
                    final String value = line.substring(firstColon + 1).trim();
                    final FieldInfo fi = new FieldInfo(value, depth);
                    fi.hasValue = true;
                    configMap.put(key, fi);
                }
            } else if(line.startsWith("-")) {
                final String key = getKeyFromList(currentKey, null);
                final String value = line.trim().substring(1).trim();
                if(configMap.containsKey(key)) {
                    FieldInfo fi = configMap.get(key);
                    fi.addListValue(value);
                } else {
                    configMap.put(key, new FieldInfo(value, depth, true));
                }
            }
        }

        return configMap;
    }

    private static boolean isEntitySameSubkey(@NotNull final String key1,
        @NotNull final String key2) {
        final int lastPeriod = key2.lastIndexOf(".");
        final String checkKey = lastPeriod > 0 ? key2.substring(0, lastPeriod) : key2;

        return (key1.equalsIgnoreCase(checkKey));
    }

    @NotNull
    private static String getEndingKey(@NotNull String input) {
        final int lastPeriod = input.lastIndexOf(".");
        if(lastPeriod < 0) {
            return input;
        }

        return input.substring(lastPeriod + 1);
    }

    @Nullable
    private static String getParentKey(@NotNull String input) {
        final int lastPeriod = input.lastIndexOf(".");
        if(lastPeriod < 0) {
            return null;
        }

        return input.substring(0, lastPeriod);
    }

    private static int getFieldDepth(@NotNull String line) {
        int whiteSpace = 0;

        for(int i = 0; i < line.length(); i++) {
            if(line.charAt(i) != ' ') {
                break;
            }
            whiteSpace++;
        }
        return whiteSpace == 0 ? 0 : whiteSpace / 2;
    }

    @NotNull
    private static String getKeyFromList(@NotNull final List<String> list,
        final String currentKey) {
        if(list.size() == 0) {
            return currentKey;
        }

        String result = String.join(".", list);
        if(currentKey != null) {
            result += "." + currentKey;
        }

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
            if(isListValue) {
                addListValue(value);
            } else {
                this.simpleValue = value;
            }
            this.depth = depth;
        }

        public boolean isList() {
            return valueList != null;
        }

        public void addListValue(String value) {
            if(valueList == null) {
                valueList = new ArrayList<>();
            }
            valueList.add(value);
        }

        public String toString() {
            if(this.isList()) {
                if(this.valueList == null || this.valueList.isEmpty()) {
                    return super.toString();
                } else {
                    return String.join(",", this.valueList);
                }
            }

            if(this.simpleValue == null) {
                return super.toString();
            } else {
                return this.simpleValue;
            }
        }
    }
}
