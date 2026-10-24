package com.taczenhancedpatch.gunner;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.Set;

/**
 * 生物分组判定。
 * 上游的实体类型接口不暴露实体类, 已确认按类判定的方式并不可靠,
 * 因此这里改为按实体 ID 判定: 原版生物使用固定名单, 模组生物归入人形与非人形之外的常规分组。
 * 只用于界面上的批量开关, 不影响具体某一类生物的持械判定。
 */
public final class MobGroups {
    private static final String NS = "minecraft:";

    private static final Set<String> UNDEAD = Set.of(
            NS + "zombie", NS + "husk", NS + "drowned", NS + "zombie_villager",
            NS + "zombified_piglin", NS + "skeleton", NS + "stray", NS + "wither_skeleton",
            NS + "skeleton_horse", NS + "zombie_horse", NS + "zoglin", NS + "phantom",
            NS + "wither");

    private static final Set<String> ILLAGER = Set.of(
            NS + "pillager", NS + "vindicator", NS + "evoker", NS + "illusioner",
            NS + "ravager", NS + "witch");

    private static final Set<String> ARTHROPOD = Set.of(
            NS + "spider", NS + "cave_spider", NS + "silverfish", NS + "endermite");

    private static final Set<String> CREEPER = Set.of(NS + "creeper");

    private static final Set<String> NETHER = Set.of(
            NS + "blaze", NS + "ghast", NS + "magma_cube", NS + "piglin",
            NS + "piglin_brute", NS + "zombified_piglin", NS + "hoglin",
            NS + "zoglin", NS + "wither_skeleton", NS + "wither");

    /* 没有手部挂载部位的生物, 拿枪会悬空, 归入非人形 */
    private static final Set<String> HANDLESS = Set.of(
            NS + "slime", NS + "magma_cube", NS + "ghast", NS + "phantom", NS + "creeper",
            NS + "spider", NS + "cave_spider", NS + "silverfish", NS + "endermite",
            NS + "blaze", NS + "shulker", NS + "guardian", NS + "elder_guardian");

    private MobGroups() {
    }

    /* 只扫描能成为枪手的生物, 即分类不是 MISC 的实体类型 */
    public static boolean isMob(EntityType<?> type) {
        return type.getCategory() != MobCategory.MISC;
    }

    public static boolean matches(String key, EntityType<?> type) {
        ResourceLocation location = ForgeRegistries.ENTITY_TYPES.getKey(type);
        String id = location == null ? "" : location.toString();
        return switch (key) {
            case "undead" -> UNDEAD.contains(id);
            case "illager" -> ILLAGER.contains(id);
            case "arthropod" -> ARTHROPOD.contains(id);
            case "creeper" -> CREEPER.contains(id);
            case "nether" -> NETHER.contains(id);
            case "humanoid" -> !HANDLESS.contains(id);
            default -> HANDLESS.contains(id);
        };
    }
}
