package com.taczenhancedpatch.gunner;

import com.taczenhancedpatch.integration.TaczApi;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 枪手军械库。
 * 枪械 ID, 口径, 开火模式, 备弹量与掉落区间属于可个性化调整的设计细节,
 * 全部集中在本类, 调整平衡时只需要改这里。
 */
public final class GunnerArsenal {
    /* 准度为 0 时的瞄准误差标准差, 单位角度 */
    public static final double MAX_AIM_ERROR_DEGREES = 16.0D;

    public enum Tier {
        LOW,
        MID,
        HIGH
    }

    /* 分档护甲材质, 只决定穿戴的四件护甲, 具体数值由原版物品决定 */
    public enum ArmorTier {
        LEATHER,
        CHAIN,
        IRON
    }

    /* 分档耐久: 护甲材质, 生命倍率系数, 额外护甲韧性 */
    public record Durability(ArmorTier armorTier, double healthFactor, int extraToughness) {
    }

    private static final Map<Tier, Durability> BY_TIER_DURABILITY = new EnumMap<>(Tier.class);

    public record GunProfile(ResourceLocation gunId,
                             ResourceLocation ammoId,
                             Tier tier,
                             TaczApi.FireMode fireMode,
                             int ammoPool,
                             int ammoDropMin,
                             int ammoDropMax) {
    }

    private static final Map<ResourceLocation, GunProfile> BY_GUN_ID = new LinkedHashMap<>();
    private static final Map<Tier, List<GunProfile>> BY_TIER = new EnumMap<>(Tier.class);

    static {
        for (Tier tier : Tier.values()) {
            BY_TIER.put(tier, new ArrayList<>());
        }

        /* 低阶: 手枪与冲锋枪, 威胁低, 生成权重高 */
        register("glock_17", "9mm", Tier.LOW, TaczApi.FireMode.SEMI, 34, 6, 10);
        register("cz75", "9mm", Tier.LOW, TaczApi.FireMode.AUTO, 32, 6, 10);
        register("m1911", "45acp", Tier.LOW, TaczApi.FireMode.SEMI, 21, 5, 9);
        register("hk_mp5a5", "9mm", Tier.LOW, TaczApi.FireMode.AUTO, 60, 8, 14);
        register("p90", "57x28", Tier.LOW, TaczApi.FireMode.AUTO, 80, 8, 14);
        register("b93r", "9mm", Tier.LOW, TaczApi.FireMode.BURST, 40, 6, 10);

        /* 中阶: 步枪与霰弹枪, 威胁明显, 需要玩家利用掩体 */
        register("ak47", "762x39", Tier.MID, TaczApi.FireMode.AUTO, 90, 10, 18);
        register("m4a1", "556x45", Tier.MID, TaczApi.FireMode.AUTO, 90, 10, 18);
        register("hk416d", "556x45", Tier.MID, TaczApi.FireMode.AUTO, 90, 10, 18);
        register("g36k", "556x45", Tier.MID, TaczApi.FireMode.AUTO, 90, 10, 18);
        register("qbz_95", "58x42", Tier.MID, TaczApi.FireMode.AUTO, 90, 10, 18);
        register("ump45", "45acp", Tier.MID, TaczApi.FireMode.AUTO, 75, 9, 15);
        register("m870", "12g", Tier.MID, TaczApi.FireMode.SEMI, 20, 4, 8);
        register("m1014", "12g", Tier.MID, TaczApi.FireMode.SEMI, 24, 4, 8);

        /* 高阶: 精确射手步枪与轻机枪, 稀有但足以迫使玩家撤退 */
        register("mk14", "308", Tier.HIGH, TaczApi.FireMode.SEMI, 30, 6, 12);
        register("hk_g3", "308", Tier.HIGH, TaczApi.FireMode.AUTO, 40, 8, 14);
        register("fn_fal", "308", Tier.HIGH, TaczApi.FireMode.AUTO, 40, 8, 14);
        register("m249", "556x45", Tier.HIGH, TaczApi.FireMode.AUTO, 200, 14, 24);
        register("m107", "50bmg", Tier.HIGH, TaczApi.FireMode.SEMI, 20, 4, 8);
        register("ai_awp", "338", Tier.HIGH, TaczApi.FireMode.SEMI, 15, 4, 8);
    }

    static {
        BY_TIER_DURABILITY.put(Tier.LOW, new Durability(ArmorTier.LEATHER, 1.0D, 0));
        BY_TIER_DURABILITY.put(Tier.MID, new Durability(ArmorTier.CHAIN, 1.15D, 1));
        BY_TIER_DURABILITY.put(Tier.HIGH, new Durability(ArmorTier.IRON, 1.3D, 2));
    }

    private GunnerArsenal() {
    }

    private static void register(String gunPath, String ammoPath, Tier tier, TaczApi.FireMode fireMode,
                                 int ammoPool, int ammoDropMin, int ammoDropMax) {
        GunProfile profile = new GunProfile(
                Objects.requireNonNull(ResourceLocation.tryParse("tacz:" + gunPath)),
                Objects.requireNonNull(ResourceLocation.tryParse("tacz:" + ammoPath)),
                tier,
                fireMode,
                ammoPool,
                ammoDropMin,
                ammoDropMax
        );
        BY_GUN_ID.put(profile.gunId(), profile);
        BY_TIER.get(tier).add(profile);
    }

    @Nullable
    public static GunProfile byGunId(@Nullable ResourceLocation gunId) {
        return gunId == null ? null : BY_GUN_ID.get(gunId);
    }

    @Nullable
    public static GunProfile pick(Tier tier, RandomSource random) {
        List<GunProfile> pool = BY_TIER.get(tier);
        if (pool == null || pool.isEmpty()) {
            return null;
        }
        return pool.get(random.nextInt(pool.size()));
    }

    public static Durability durability(Tier tier) {
        Durability durability = BY_TIER_DURABILITY.get(tier);
        return durability == null ? BY_TIER_DURABILITY.get(Tier.LOW) : durability;
    }

    public static Tier pickTier(RandomSource random, int lowWeight, int midWeight, int highWeight) {
        int low = Math.max(0, lowWeight);
        int mid = Math.max(0, midWeight);
        int high = Math.max(0, highWeight);
        int total = low + mid + high;
        if (total <= 0) {
            return Tier.LOW;
        }
        int roll = random.nextInt(total);
        if (roll < low) {
            return Tier.LOW;
        }
        roll -= low;
        if (roll < mid) {
            return Tier.MID;
        }
        return Tier.HIGH;
    }
}
