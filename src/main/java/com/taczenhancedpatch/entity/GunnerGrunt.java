package com.taczenhancedpatch.entity;

import com.taczenhancedpatch.config.ServerConfig;
import com.taczenhancedpatch.gunner.GunnerArming;
import com.taczenhancedpatch.TACZEnhancedPatch;
import com.taczenhancedpatch.faction.FactionData;
import com.taczenhancedpatch.gunner.GunnerArsenal;
import com.taczenhancedpatch.integration.TaczApi;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.Difficulty;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.PanicGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;

import javax.annotation.Nullable;

/**
 * 持枪小兵。
 * 中立生物, 不主动攻击, 只会像羊一样在野地游荡, 受击时按原版动物方式处理。
 * 生成时必定装配一把随机枪械, 该行为不受枪手系统开关影响, 也不进入枪手战斗逻辑。
 * 击杀后由独立战利品逻辑发放枪械, 弹药, 食物与杂物, 数值由服务端配置决定。
 */
public class GunnerGrunt extends Animal {
    public GunnerGrunt(EntityType<? extends GunnerGrunt> type, Level level) {
        super(type, level);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 20.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.25D)
                .add(Attributes.FOLLOW_RANGE, 16.0D);
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(0, new FloatGoal(this));
        this.goalSelector.addGoal(1, new PanicGoal(this, 1.25D));
        this.goalSelector.addGoal(3, new RideTankGoal(this));
        this.goalSelector.addGoal(5, new WaterAvoidingRandomStrollGoal(this, 1.0D));
        this.goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 8.0F));
        this.goalSelector.addGoal(7, new RandomLookAroundGoal(this));
    }

    @Override
    @Nullable
    public SpawnGroupData finalizeSpawn(ServerLevelAccessor level, DifficultyInstance difficulty,
                                        MobSpawnType spawnType, @Nullable SpawnGroupData spawnGroupData,
                                        @Nullable CompoundTag tag) {
        SpawnGroupData data = super.finalizeSpawn(level, difficulty, spawnType, spawnGroupData, tag);
        if (!level.getLevel().isClientSide()) {
            /* 上游枪械数据异常时只放弃武装, 不让生成失败 */
            try {
                arm(this, difficulty.getDifficulty());
            } catch (RuntimeException exception) {
                TACZEnhancedPatch.LOGGER.error("[grunt] failed to arm {}", this.getUUID(), exception);
            }
            var maxHealth = this.getAttribute(Attributes.MAX_HEALTH);
            if (maxHealth != null) {
                maxHealth.setBaseValue(CombatPreset.gruntHealth(difficulty.getDifficulty()));
                this.setHealth(this.getMaxHealth());
            }
            var armor = this.getAttribute(Attributes.ARMOR);
            if (armor != null) {
                armor.setBaseValue(CombatPreset.gruntArmor(difficulty.getDifficulty()));
            }
            /* 有阵营的守军不再逃跑, 改为近身还击; 无阵营个体保持原版动物反应 */
            if (!FactionData.factionOf(this).isEmpty()) {
                this.goalSelector.removeAllGoals(goal -> goal instanceof PanicGoal);
                this.goalSelector.addGoal(2, new MeleeAttackGoal(this, 1.1D, true));
            }
        }
        return data;
    }

    /* 必定持械: 不复用枪手身份标记, 只借用军械库的分档抽枪与随机备弹 */
    private static void arm(GunnerGrunt grunt, Difficulty difficulty) {
        if (TaczApi.isGun(grunt.getMainHandItem())) {
            return;
        }
        GunnerArming.Arming arming = GunnerArming.roll(grunt.getRandom(), difficulty);
        if (arming == null) {
            return;
        }
        GunnerArsenal.GunProfile profile = arming.profile();
        ItemStack gun = TaczApi.createGun(profile.gunId(), profile.fireMode(), arming.ammoPool());
        if (gun.isEmpty()) {
            return;
        }
        int slots = ServerConfig.gunnerRandomAttachmentSlots();
        for (int i = 0; i < slots; i++) {
            if (grunt.getRandom().nextFloat() < GunnerArsenal.ATTACHMENT_SLOT_CHANCE) {
                TaczApi.installRandomAttachment(gun, grunt.getRandom());
            }
        }
        grunt.setItemSlot(EquipmentSlot.MAINHAND, gun);
        /* 枪械掉落由独立战利品逻辑接管, 关闭原版手持掉落 */
        grunt.setDropChance(EquipmentSlot.MAINHAND, 0.0F);
    }

    @Override
    public boolean isFood(ItemStack stack) {
        return false;
    }

    @Override
    @Nullable
    public AgeableMob getBreedOffspring(ServerLevel level, AgeableMob otherParent) {
        return null;
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return SoundEvents.VILLAGER_AMBIENT;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return SoundEvents.VILLAGER_HURT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.VILLAGER_DEATH;
    }
}