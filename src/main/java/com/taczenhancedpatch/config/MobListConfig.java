package com.taczenhancedpatch.config;

import net.minecraftforge.common.ForgeConfigSpec;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 持械生物列表。
 * 每个生物占一条记录, 形如 实体ID=开关, 开关为 true 表示该生物可以持械。
 * 世界加载时由中间层扫描全部生物写入本表, 新条目默认关闭; 分组开关与逐项开关都会直接改写这里的开关状态。
 */
public final class MobListConfig {
    private static final ForgeConfigSpec.Builder BUILDER = new ForgeConfigSpec.Builder();
    private static final String SEPARATOR = "=";

    private static final ForgeConfigSpec.ConfigValue<List<? extends String>> MOBS = BUILDER
            .comment("持械生物列表, 每项形如 实体ID=开关, 例如 minecraft:zombie=true",
                    "世界加载时扫描全部生物写入本表, 新条目默认关闭",
                    "需要调整时在持械生物列表页勾选分组或逐项开关, 不要直接手改")
            .defineListAllowEmpty("mobs", List.of(), element -> element instanceof String);

    public static final ForgeConfigSpec SPEC = BUILDER.build();

    private MobListConfig() {
    }

    /* 读取全部记录, 保持文件中的顺序 */
    public static Map<String, Boolean> states() {
        Map<String, Boolean> states = new LinkedHashMap<>();
        List<? extends String> raw;
        try {
            raw = MOBS.get();
        } catch (IllegalStateException ignored) {
            return states;
        }
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

    public static List<String> mobs() {
        return new ArrayList<>(states().keySet());
    }

    public static boolean isEnabled(String id) {
        return Boolean.TRUE.equals(states().get(id));
    }

    /* 整表写回, 按实体 ID 排序保证文件稳定 */
    public static void setStates(Map<String, Boolean> states) {
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
}
