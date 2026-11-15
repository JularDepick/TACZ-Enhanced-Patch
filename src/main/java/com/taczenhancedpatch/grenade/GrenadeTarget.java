package com.taczenhancedpatch.grenade;

import com.taczenhancedpatch.config.ConfigOption;

/*
 * 手雷的杀伤对象档位。
 * 三个开关分别表示是否破坏地形, 是否杀伤玩家, 是否杀伤生物; 七个档位是它们的全部组合,
 * 按键切换与界面展示都按声明顺序循环。
 */
public enum GrenadeTarget implements ConfigOption.Labeled {
    TERRAIN_ONLY(true, false, false, "terrain_only"),
    PLAYER_ONLY(false, true, false, "player_only"),
    MOB_ONLY(false, false, true, "mob_only"),
    NO_TERRAIN(false, true, true, "no_terrain"),
    NO_PLAYER(true, false, true, "no_player"),
    NO_MOB(true, true, false, "no_mob"),
    ALL(true, true, true, "all");

    private final boolean terrain;
    private final boolean players;
    private final boolean mobs;
    private final String id;

    GrenadeTarget(boolean terrain, boolean players, boolean mobs, String id) {
        this.terrain = terrain;
        this.players = players;
        this.mobs = mobs;
        this.id = id;
    }

    public boolean terrain() {
        return terrain;
    }

    public boolean players() {
        return players;
    }

    public boolean mobs() {
        return mobs;
    }

    public String id() {
        return id;
    }

    @Override
    public String labelKey() {
        return "grenade.target." + id;
    }

    public static GrenadeTarget byId(String id, GrenadeTarget fallback) {
        for (GrenadeTarget value : values()) {
            if (value.id.equals(id)) {
                return value;
            }
        }
        return fallback;
    }

    public GrenadeTarget next(boolean forward) {
        GrenadeTarget[] all = values();
        int index = ordinal();
        return all[(index + (forward ? 1 : all.length - 1)) % all.length];
    }
}
