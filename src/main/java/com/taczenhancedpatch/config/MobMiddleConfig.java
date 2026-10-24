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
import java.util.List;
import java.util.Map;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/**
 * 生物持械分组开关。
 * 与生物名单共用一套独立 JSON 存储, 不依赖 Forge 配置的加载时机;
 * 分组开关只作为名单的批量编辑器, 具体名单由 {@link MobListConfig} 记录。
 */
public final class MobMiddleConfig {
    private static final String FILE_NAME = "tacz_enhanced_patch-mobgroups.json";
    private static final String ROOT_KEY = "groups";
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final List<String> KEYS = List.of(
            "undead", "illager", "arthropod", "creeper", "nether", "humanoid", "non_humanoid");

    private static Map<String, Boolean> groups;

    /* 分组开关: key 供中间层匹配生物, label 是界面文本键 */
    public record GroupSwitch(String key, String label, BooleanSupplier getter, Consumer<Boolean> setter) {
    }

    private MobMiddleConfig() {
    }

    public static List<GroupSwitch> groups() {
        return List.of(
                new GroupSwitch("undead", "group.undead", () -> value("undead"), enabled -> set("undead", enabled)),
                new GroupSwitch("illager", "group.illager", () -> value("illager"), enabled -> set("illager", enabled)),
                new GroupSwitch("arthropod", "group.arthropod", () -> value("arthropod"), enabled -> set("arthropod", enabled)),
                new GroupSwitch("creeper", "group.creeper", () -> value("creeper"), enabled -> set("creeper", enabled)),
                new GroupSwitch("nether", "group.nether", () -> value("nether"), enabled -> set("nether", enabled)),
                new GroupSwitch("humanoid", "group.humanoid", () -> value("humanoid"), enabled -> set("humanoid", enabled)),
                new GroupSwitch("non_humanoid", "group.non_humanoid", () -> value("non_humanoid"),
                        enabled -> set("non_humanoid", enabled)));
    }

    public static boolean anyGroupEnabled() {
        for (GroupSwitch group : groups()) {
            if (group.getter().getAsBoolean()) {
                return true;
            }
        }
        return false;
    }

    private static boolean value(String key) {
        return Boolean.TRUE.equals(load().get(key));
    }

    private static void set(String key, boolean enabled) {
        load().put(key, enabled);
        save();
    }

    /* 分组状态立即写盘, 与名单文件分开存放 */
    public static void save() {
        JsonObject root = new JsonObject();
        JsonObject data = new JsonObject();
        for (Map.Entry<String, Boolean> entry : new TreeMapView(load()).sorted().entrySet()) {
            data.addProperty(entry.getKey(), Boolean.TRUE.equals(entry.getValue()));
        }
        root.add(ROOT_KEY, data);
        Path file = file();
        try {
            Files.createDirectories(file.getParent());
            try (Writer writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
                GSON.toJson(root, writer);
            }
        } catch (IOException exception) {
            TACZEnhancedPatch.LOGGER.error("[mobgroups] failed to write {}", file, exception);
        }
    }

    private static Map<String, Boolean> load() {
        if (groups != null) {
            return groups;
        }
        groups = new LinkedHashMap<>();
        for (String key : KEYS) {
            groups.put(key, false);
        }
        Path file = file();
        if (!Files.isRegularFile(file)) {
            return groups;
        }
        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            JsonElement parsed = JsonParser.parseReader(reader);
            if (parsed == null || !parsed.isJsonObject()) {
                return groups;
            }
            JsonElement data = parsed.getAsJsonObject().get(ROOT_KEY);
            if (data == null || !data.isJsonObject()) {
                return groups;
            }
            for (Map.Entry<String, JsonElement> entry : data.getAsJsonObject().entrySet()) {
                if (KEYS.contains(entry.getKey()) && entry.getValue() != null && entry.getValue().isJsonPrimitive()) {
                    groups.put(entry.getKey(), entry.getValue().getAsBoolean());
                }
            }
        } catch (IOException | RuntimeException exception) {
            TACZEnhancedPatch.LOGGER.error("[mobgroups] failed to read {}", file, exception);
        }
        return groups;
    }

    private static Path file() {
        return FMLPaths.CONFIGDIR.get().resolve(FILE_NAME);
    }

    /* 简单包装: 按 key 排序后再写盘, 保证文件稳定 */
    private record TreeMapView(Map<String, Boolean> source) {
        Map<String, Boolean> sorted() {
            return new java.util.TreeMap<>(source);
        }
    }
}
