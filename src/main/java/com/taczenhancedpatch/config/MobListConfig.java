package com.taczenhancedpatch.config;

import net.minecraftforge.common.ForgeConfigSpec;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 生物持械配置。
 * 每个生物占一条记录, 形如 实体ID=开关, 开关为 true 表示该生物可以持械。
 * 记录同时保存在运行期缓存里, 因此即使服务端配置尚未加载(例如标题界面),
 * 扫描结果与界面依然可用; 配置加载后再把缓存写回文件。
 */
public final class MobListConfig {
    private static final ForgeConfigSpec.Builder BUILDER = new ForgeConfigSpec.Builder();
    private static final String SEPARATOR = "=";

    private static final ForgeConfigSpec.ConfigValue<List<? extends String>> MOBS = BUILDER
            .comment("生物持械配置, 每项形如 实体ID=开关, 例如 minecraft:zombie=true",
                    "进入世界时扫描全部生物写入本表, 新条目默认关闭",
                    "需要调整时在生物持械配置页勾选分组或逐项开关, 不要直接手改")
            .defineListAllowEmpty("mobs", List.of(), element -> element instanceof String);

    public static final ForgeConfigSpec SPEC = BUILDER.build();

    /* 运行期缓存: 配置未加载时界面与判定都读这里, 配置可用时每次读盘后刷新 */
    private static final Map<String, Boolean> CACHE = new LinkedHashMap<>();

    private MobListConfig() {
    }

    /* 读取全部记录, 配置已加载时以文件为准并刷新缓存 */
    public static Map<String, Boolean> states() {
        List<? extends String> raw = readRaw();
        synchronized (CACHE) {
            if (raw != null) {
                CACHE.clear();
                CACHE.putAll(parse(raw));
            }
            return new LinkedHashMap<>(CACHE);
        }
    }

    public static List<String> mobs() {
        return new ArrayList<>(states().keySet());
    }

    public static boolean isEnabled(String id) {
        return Boolean.TRUE.equals(states().get(id));
    }

    /* 整表写回, 先更新缓存再按实体 ID 排序写盘, 配置不可用时只更新缓存 */
    public static void setStates(Map<String, Boolean> states) {
        synchronized (CACHE) {
            CACHE.clear();
            CACHE.putAll(states);
        }
        if (readRaw() == null) {
            return;
        }
        List<String> entries = new ArrayList<>(states.size());
        states.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(entry -> entries.add(entry.getKey() + SEPARATOR + entry.getValue()));
        try {
            MOBS.set(List.copyOf(entries));
        } catch (IllegalStateException ignored) {
        }
    }

    public static void setState(String id, boolean enabled) {
        Map<String, Boolean> states = states();
        states.put(id, enabled);
        setStates(states);
    }

    public static void save() {
        var config = ConfigEvents.mobListConfig();
        if (config != null) {
            config.save();
        }
    }

    @Nullable
    private static List<? extends String> readRaw() {
        try {
            return MOBS.get();
        } catch (IllegalStateException ignored) {
            return null;
        }
    }

    private static Map<String, Boolean> parse(List<? extends String> raw) {
        Map<String, Boolean> states = new LinkedHashMap<>();
        for (String entry : raw) {
            if (entry == null || entry.isBlank()) {
                continue;
            }
            int split = entry.lastIndexOf(SEPARATOR);
            if (split <= 0) {
                /* 兼容只写了实体 ID 的行, 视为开启 */
                states.put(entry.trim(), Boolean.TRUE);
                continue;
            }
            String id = entry.substring(0, split).trim();
            boolean enabled = Boolean.parseBoolean(entry.substring(split + SEPARATOR.length()).trim());
            if (!id.isEmpty()) {
                states.put(id, enabled);
            }
        }
        return states;
    }
}
