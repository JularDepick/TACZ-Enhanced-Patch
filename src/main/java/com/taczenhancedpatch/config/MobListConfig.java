package com.taczenhancedpatch.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.taczenhancedpatch.TACZEnhancedPatch;
import net.minecraftforge.fml.loading.FMLPaths;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;

/**
 * 生物持械配置。
 * 数据写在 config 目录下的独立 JSON 文件里, 不依赖 Forge 配置的加载时机,
 * 因此无论配置界面在标题界面还是存档内打开, 都能读到完整名单并立即写盘。
 * 文件形如 {"mobs": {"minecraft:zombie": true, ...}}, 键是实体 ID, 值是开关。
 */
public final class MobListConfig {
    private static final String FILE_NAME = "tacz_enhanced_patch-moblist.json";
    private static final String ROOT_KEY = "mobs";
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private static Map<String, Boolean> states;

    private MobListConfig() {
    }

    public static synchronized Map<String, Boolean> states() {
        ensureLoaded();
        return new LinkedHashMap<>(states);
    }

    public static synchronized boolean isEmpty() {
        ensureLoaded();
        return states.isEmpty();
    }

    public static synchronized boolean isEnabled(String id) {
        ensureLoaded();
        return Boolean.TRUE.equals(states.get(id));
    }

    public static synchronized void setStates(Map<String, Boolean> next) {
        ensureLoaded();
        states.clear();
        states.putAll(next);
        save();
    }

    public static synchronized void setState(String id, boolean enabled) {
        ensureLoaded();
        states.put(id, enabled);
        save();
    }

    /* 立即写盘, 失败只记日志, 不静默吞掉 */
    public static synchronized void save() {
        ensureLoaded();
        JsonObject root = new JsonObject();
        JsonObject mobs = new JsonObject();
        for (Map.Entry<String, Boolean> entry : new TreeMap<>(states).entrySet()) {
            mobs.addProperty(entry.getKey(), Boolean.TRUE.equals(entry.getValue()));
        }
        root.add(ROOT_KEY, mobs);
        Path file = file();
        try {
            Files.createDirectories(file.getParent());
            try (Writer writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
                GSON.toJson(root, writer);
            }
        } catch (IOException exception) {
            TACZEnhancedPatch.LOGGER.error("[moblist] failed to write {}", file, exception);
        }
    }

    private static void ensureLoaded() {
        if (states != null) {
            return;
        }
        states = new LinkedHashMap<>();
        Path file = file();
        if (!Files.isRegularFile(file)) {
            return;
        }
        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            JsonElement parsed = JsonParser.parseReader(reader);
            if (parsed == null || !parsed.isJsonObject()) {
                return;
            }
            JsonElement mobs = parsed.getAsJsonObject().get(ROOT_KEY);
            if (mobs == null || !mobs.isJsonObject()) {
                return;
            }
            for (Map.Entry<String, JsonElement> entry : mobs.getAsJsonObject().entrySet()) {
                JsonElement value = entry.getValue();
                if (value != null && value.isJsonPrimitive()) {
                    states.put(entry.getKey(), value.getAsBoolean());
                }
            }
        } catch (IOException | RuntimeException exception) {
            TACZEnhancedPatch.LOGGER.error("[moblist] failed to read {}", file, exception);
            states.clear();
        }
    }

    private static Path file() {
        return FMLPaths.CONFIGDIR.get().resolve(FILE_NAME);
    }
}
