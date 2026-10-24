package com.taczenhancedpatch.gunner;

import com.taczenhancedpatch.TACZEnhancedPatch;
import com.taczenhancedpatch.config.ServerConfig;
import com.taczenhancedpatch.integration.TaczApi;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.NeutralMob;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * 临场武装。
 * 和平生物, 中立生物与友军生物被打到生命一半以下时, 按配置概率临场获得一把随机枪械,
 * 随后与枪手走同一套索敌, 射击与掉落逻辑, 持续攻击造成伤害的来源。
 * 每只生物只判定一次, 判定失败则保持原版行为。
 */
@Mod.EventBusSubscriber(modid = TACZEnhancedPatch.MOD_ID)
public final class GunnerRetaliation {
    private GunnerRetaliation() {
    }

    @SubscribeEvent
    public static void onLivingDamage(LivingDamageEvent event) {
        if (event.getEntity().level().isClientSide() || !ServerConfig.gunnerEnabled()) {
            return;
        }
        if (!(event.getEntity() instanceof Mob mob) || mob.isBaby()) {
            return;
        }
        if (GunnerMarker.isGunner(mob) || TaczApi.isGun(mob.getMainHandItem())) {
            return;
        }
        if (!isCandidate(mob) || GunnerMarker.isRetaliationChecked(mob)) {
            return;
        }
        /* 用受伤后的预计生命值判定, 必须活着且已降到配置的阈值以下 */
        float projected = mob.getHealth() - event.getAmount();
        if (projected <= 0.0F || projected > mob.getMaxHealth() * ServerConfig.gunnerRetaliationThreshold()) {
            return;
        }
        GunnerMarker.markRetaliationChecked(mob);
        LivingEntity attacker = event.getSource().getEntity() instanceof LivingEntity living ? living : null;
        if (attacker != null && !GunnerCombat.shouldRetaliate(mob, attacker)) {
            return;
        }
        boolean humanoid = GunnerMobRegistry.matchesGroup("humanoid", mob.getType());
        if (mob.getRandom().nextDouble() >= ServerConfig.gunnerRetaliationChance(humanoid)) {
            return;
        }
        Difficulty difficulty = mob.level().getCurrentDifficultyAt(mob.blockPosition()).getDifficulty();
        GunnerArming.Arming arming = GunnerArming.roll(mob.getRandom(), difficulty);
        if (arming == null) {
            return;
        }
        GunnerMarker.mark(mob, arming.profile(), arming.ammoPool(), true);
        GunnerArming.equip(mob, arming.profile().gunId(), arming.profile().fireMode(), arming.ammoPool(), mob.getRandom());
        if (attacker != null) {
            mob.setTarget(attacker);
            mob.setLastHurtByMob(attacker);
        }
        GunnerCombat.retaliate(mob, attacker);
    }

    /* 非敌对生物与中立生物才有临场武装的机会, 敌对生物走生成路径; 生物持械配置里的开关同样约束本路径 */
    private static boolean isCandidate(Mob mob) {
        if (mob instanceof com.taczenhancedpatch.entity.GunnerGrunt
                || mob instanceof com.taczenhancedpatch.entity.TankElite) {
            return false;
        }
        if (!GunnerMobRegistry.isEmpty() && !GunnerMobRegistry.isEnabled(mob.getType())) {
            return false;
        }
        if (!(mob instanceof Enemy) || mob instanceof NeutralMob) {
            ResourceLocation id = ForgeRegistries.ENTITY_TYPES.getKey(mob.getType());
            return id == null || !ServerConfig.gunnerMobBlacklist().contains(id.toString());
        }
        return false;
    }
}