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
 * 枪械 ID, 口径, 开火模式, 掉落区间与分档耐久属于可个性化调整的设计细节,
 * 全部集中在本类, 调整平衡时只需要改这里。
 * 备弹量不在这里, 由服务端参数"基本弹药量"统一换算为基准值的 0.5 到 2 倍。
 */
public final class GunnerArsenal {
    /* 准度为 0 时的瞄准误差标准差, 单位角度 */
    public static final double MAX_AIM_ERROR_DEGREES = 16.0D;

    /* 和平, 中立与友军生物受击后触发临场武装的生命比例阈值 */
    public static final double RETALIATION_HEALTH_THRESHOLD = 0.5D;

    /* 随机配件每个槽位实际安装的概率 */
    public static final double ATTACHMENT_SLOT_CHANCE = 0.6D;

    /* 备弹量相对基本弹药量的随机下限与上限 */
    public static final double AMMO_MIN_FACTOR = 0.5D;
    public static final double AMMO_MAX_FACTOR = 2.0D;

    /* 高级弹药: 高阶口径, 击杀枪手时按概率额外掉落 */
    public static final int HIGH_TIER_AMMO_MIN = 6;
    public static final int HIGH_TIER_AMMO_MAX = 18;
    private static final List<ResourceLocation> HIGH_TIER_AMMO = List.of(
            Objects.requireNonNull(ResourceLocation.tryParse("tacz:308")),
            Objects.requireNonNull(ResourceLocation.tryParse("tacz:338")),
            Objects.requireNonNull(ResourceLocation.tryParse("tacz:50bmg")));

    public enum Tier {
        LOW,
        MID,
        HIGH
    }

    /* 护甲材质, 顺序即从低到高, 抽取概率按等比递减 */
    public enum ArmorMaterial {
        LEATHER,
        CHAINMAIL,
        IRON,
        GOLD,
        DIAMOND,
        NETHERITE
    }

    /* 相邻材质的概率公比: 下一档材质是上一档的 0.6 倍, 下界合金最稀有 */
    public static final double ARMOR_MATERIAL_RATIO = 0.6D;

    /* 分档耐久: 生命倍率系数与额外护甲韧性, 护甲材质单独按部位随机抽取 */
    public record Durability(double healthFactor, int extraToughness) {
    }

    private static final Map<Tier, Durability> BY_TIER_DURABILITY = new EnumMap<>(Tier.class);

    public record GunProfile(ResourceLocation gunId,
                             ResourceLocation ammoId,
                             Tier tier,
                             TaczApi.FireMode fireMode,
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
        register("glock_17", "9mm", Tier.LOW, TaczApi.FireMode.SEMI, 6, 10);
        register("cz75", "9mm", Tier.LOW, TaczApi.FireMode.AUTO, 6, 10);
        register("m1911", "45acp", Tier.LOW, TaczApi.FireMode.SEMI, 5, 9);
        register("hk_mp5a5", "9mm", Tier.LOW, TaczApi.FireMode.AUTO, 8, 14);
        register("p90", "57x28", Tier.LOW, TaczApi.FireMode.AUTO, 8, 14);
        register("b93r", "9mm", Tier.LOW, TaczApi.FireMode.BURST, 6, 10);

        /* 中阶: 步枪与霰弹枪, 威胁明显, 需要玩家利用掩体 */
        register("ak47", "762x39", Tier.MID, TaczApi.FireMode.AUTO, 10, 18);
        register("m4a1", "556x45", Tier.MID, TaczApi.FireMode.AUTO, 10, 18);
        register("hk416d", "556x45", Tier.MID, TaczApi.FireMode.AUTO, 10, 18);
        register("g36k", "556x45", Tier.MID, TaczApi.FireMode.AUTO, 10, 18);
        register("qbz_95", "58x42", Tier.MID, TaczApi.FireMode.AUTO, 10, 18);
        register("ump45", "45acp", Tier.MID, TaczApi.FireMode.AUTO, 9, 15);
        register("m870", "12g", Tier.MID, TaczApi.FireMode.SEMI, 4, 8);
        register("m1014", "12g", Tier.MID, TaczApi.FireMode.SEMI, 4, 8);

        /* 高阶: 精确射手步枪与轻机枪, 稀有但足以迫使玩家撤退 */
        register("mk14", "308", Tier.HIGH, TaczApi.FireMode.SEMI, 6, 12);
        register("hk_g3", "308", Tier.HIGH, TaczApi.FireMode.AUTO, 8, 14);
        register("fn_fal", "308", Tier.HIGH, TaczApi.FireMode.AUTO, 8, 14);
        register("m249", "556x45", Tier.HIGH, TaczApi.FireMode.AUTO, 14, 24);
        register("m107", "50bmg", Tier.HIGH, TaczApi.FireMode.SEMI, 4, 8);
        register("ai_awp", "338", Tier.HIGH, TaczApi.FireMode.SEMI, 4, 8);
    }

    static {
        BY_TIER_DURABILITY.put(Tier.LOW, new Durability(1.0D, 0));
        BY_TIER_DURABILITY.put(Tier.MID, new Durability(1.15D, 1));
        BY_TIER_DURABILITY.put(Tier.HIGH, new Durability(1.3D, 2));
    }

    private GunnerArsenal() {
    }

    private static void register(String gunPath, String ammoPath, Tier tier, TaczApi.FireMode fireMode,
                                 int ammoDropMin, int ammoDropMax) {
        GunProfile profile = new GunProfile(
                Objects.requireNonNull(ResourceLocation.tryParse("tacz:" + gunPath)),
                Objects.requireNonNull(ResourceLocation.tryParse("tacz:" + ammoPath)),
                tier,
                fireMode,
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

    /**
     * 由基本弹药量生成随机的备弹量。
     * 结果落在基本值的 0.5 到 2 倍之间, 且始终至少为 1, 因此弹药依旧有限。
     */
    public static int randomAmmo(int baseAmmo, RandomSource random) {
        int base = Math.max(1, baseAmmo);
        int low = Math.max(1, (int) Math.round(base * AMMO_MIN_FACTOR));
        int high = Math.max(low, (int) Math.round(base * AMMO_MAX_FACTOR));
        return low >= high ? low : low + random.nextInt(high - low + 1);
    }

    /**
     * 随机抽取一种护甲材质。
     * 材质从皮革到合金按等比递减, 公比为 {@link #ARMOR_MATERIAL_RATIO},
     * 因此低阶护甲常见, 下界合金极稀有; 是否穿戴由调用方按持械概率单独判定。
     */
    public static ArmorMaterial rollArmorMaterial(RandomSource random) {
        ArmorMaterial[] materials = ArmorMaterial.values();
        double[] weights = new double[materials.length];
        double total = 0.0D;
        for (int i = 0; i < materials.length; i++) {
            weights[i] = Math.pow(ARMOR_MATERIAL_RATIO, i);
            total += weights[i];
        }
        double roll = random.nextDouble() * total;
        for (int i = 0; i < weights.length; i++) {
            roll -= weights[i];
            if (roll <= 0.0D) {
                return materials[i];
            }
        }
        return materials[materials.length - 1];
    }

    /* 随机抽取一种高级弹药口径, 池为空时返回 null */
    @Nullable
    public static ResourceLocation pickHighTierAmmo(RandomSource random) {
        if (HIGH_TIER_AMMO.isEmpty()) {
            return null;
        }
        return HIGH_TIER_AMMO.get(random.nextInt(HIGH_TIER_AMMO.size()));
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
