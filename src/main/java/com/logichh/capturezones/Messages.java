package com.logichh.capturezones;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;
import org.bukkit.ChatColor;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.logging.Level;

public class Messages {

    private static final String LANG_DIR = "lang";
    private static final String DEFAULT_LANG = "en";

    private static final Gson GSON = new Gson();

    private static JavaPlugin plugin;
    private static String activeLanguage;

    private static Map<String, Map<String, Object>> languageCache = new HashMap<>();
    private static Map<String, Object> fallbackCache;

    public static void init(JavaPlugin plugin) {
        Messages.plugin = plugin;
        loadLanguages();
    }

    public static void reload() {
        languageCache.clear();
        fallbackCache = null;
        loadLanguages();
    }

    private static void loadLanguages() {
        File langDir = new File(plugin.getDataFolder(), LANG_DIR);
        if (!langDir.exists()) {
            langDir.mkdirs();
            copyDefaultLanguageFiles();
        }

        File[] langFiles = langDir.listFiles((dir, name) -> name.endsWith(".json"));
        if (langFiles != null) {
            for (File file : langFiles) {
                String langCode = file.getName().replace(".json", "");
                try (FileReader reader = new FileReader(file, StandardCharsets.UTF_8)) {
                    JsonObject json = GSON.fromJson(reader, JsonObject.class);
                    if (json != null) {
                        Map<String, Object> translations = parseJsonObject(json);
                        Map<String, Object> bundled = loadBundledLanguage(langCode);
                        if (bundled != null) {
                            bundled.putAll(translations);
                            languageCache.put(langCode, bundled);
                        } else {
                            languageCache.put(langCode, translations);
                        }
                        plugin.getLogger().info("Loaded language: " + langCode);
                    }
                } catch (IOException | JsonSyntaxException e) {
                    plugin.getLogger().log(Level.WARNING, "Failed to load language file: " + file.getName(), e);
                }
            }
        }

        if (!languageCache.containsKey(DEFAULT_LANG)) {
            loadDefaultEnglish();
        }

        fallbackCache = languageCache.get(DEFAULT_LANG);

        String configLang = plugin.getConfig().getString("settings.language", DEFAULT_LANG);
        setActiveLanguage(configLang);
    }

    private static void copyDefaultLanguageFiles() {
        copyDefaultFile("lang/en.json");
    }

    private static void copyDefaultFile(String resourcePath) {
        try (InputStream in = plugin.getResource(resourcePath)) {
            if (in != null) {
                File outFile = new File(plugin.getDataFolder(), resourcePath);
                outFile.getParentFile().mkdirs();
                java.nio.file.Files.copy(in, outFile.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            plugin.getLogger().log(Level.WARNING, "Failed to copy default language file: " + resourcePath, e);
        }
    }

    private static void loadDefaultEnglish() {
        Map<String, Object> translations = loadBundledLanguage(DEFAULT_LANG);
        if (translations != null) {
            languageCache.put(DEFAULT_LANG, translations);
            plugin.getLogger().info("Loaded default English language");
            return;
        }
        plugin.getLogger().log(Level.SEVERE, "Failed to load default English language file");
    }

    private static Map<String, Object> loadBundledLanguage(String langCode) {
        String resourcePath = LANG_DIR + "/" + langCode + ".json";
        try (InputStream in = plugin.getResource(resourcePath)) {
            if (in == null) {
                return null;
            }
            try (InputStreamReader reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
                JsonObject json = GSON.fromJson(reader, JsonObject.class);
                if (json != null) {
                    return parseJsonObject(json);
                }
            }
        } catch (IOException | JsonSyntaxException e) {
            plugin.getLogger().log(Level.WARNING, "Failed to load bundled language file: " + resourcePath, e);
        }
        return null;
    }

    private static Map<String, Object> parseJsonObject(JsonObject json) {
        Map<String, Object> map = new HashMap<>();
        for (Map.Entry<String, JsonElement> entry : json.entrySet()) {
            map.put(entry.getKey(), parseJsonElement(entry.getValue()));
        }
        return map;
    }

    private static Object parseJsonElement(JsonElement element) {
        if (element.isJsonPrimitive()) {
            return element.getAsString();
        } else if (element.isJsonArray()) {
            List<Object> list = new ArrayList<>();
            for (JsonElement item : element.getAsJsonArray()) {
                list.add(parseJsonElement(item));
            }
            return list;
        } else if (element.isJsonObject()) {
            return parseJsonObject(element.getAsJsonObject());
        }
        return element.toString();
    }

    public static void setActiveLanguage(String language) {
        if (languageCache.containsKey(language)) {
            activeLanguage = language;
        } else if (language.contains("_") && languageCache.containsKey(language.split("_")[0])) {
            activeLanguage = language.split("_")[0];
            plugin.getLogger().info("Language '" + language + "' not found, using '" + activeLanguage + "'");
        } else {
            activeLanguage = DEFAULT_LANG;
            plugin.getLogger().warning("Language '" + language + "' not found, falling back to '" + DEFAULT_LANG + "'");
        }
    }

    public static String getActiveLanguage() {
        return activeLanguage;
    }

    public static Set<String> getAvailableLanguages() {
        return new TreeSet<>(languageCache.keySet());
    }

    public static String get(String key) {
        return get(key, (Map<String, String>) null);
    }

    public static String get(String key, Map<String, String> placeholders) {
        String message = getRaw(key);
        if (placeholders != null) {
            for (Map.Entry<String, String> entry : placeholders.entrySet()) {
                message = message.replace("{" + entry.getKey() + "}", entry.getValue());
            }
        }
        return ChatColor.translateAlternateColorCodes('&', message);
    }

    public static String get(String key, Object... args) {
        String message = getRaw(key);
        for (int i = 0; i < args.length; i++) {
            message = message.replace("{" + i + "}", String.valueOf(args[i]));
        }
        return ChatColor.translateAlternateColorCodes('&', message);
    }

    public static List<String> getList(String key) {
        return getList(key, null);
    }

    public static List<String> getList(String key, Map<String, String> placeholders) {
        Object value = getRawObject(key);
        if (!(value instanceof List)) {
            return new ArrayList<>();
        }

        List<String> result = new ArrayList<>();
        for (Object item : (List<?>) value) {
            String message = String.valueOf(item);
            if (placeholders != null) {
                for (Map.Entry<String, String> entry : placeholders.entrySet()) {
                    message = message.replace("{" + entry.getKey() + "}", entry.getValue());
                }
            }
            result.add(ChatColor.translateAlternateColorCodes('&', message));
        }
        return result;
    }

    private static String getRaw(String key) {
        Object value = getRawObject(key);
        return value != null ? String.valueOf(value) : key;
    }

    private static Object getRawObject(String key) {
        Map<String, Object> activeMap = languageCache.get(activeLanguage);
        if (activeMap != null && activeMap.containsKey(key)) {
            return activeMap.get(key);
        }

        if (fallbackCache != null && fallbackCache.containsKey(key)) {
            return fallbackCache.get(key);
        }

        return key;
    }
}

