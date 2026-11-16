package com.taczenhancedpatch.mortar;

import com.taczenhancedpatch.config.ServerConfig;
import com.taczenhancedpatch.shell.ShellExplosion;
import com.taczenhancedpatch.shell.ShellProjectile;
import com.taczenhancedpatch.shell.ShellSettings;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.List;

/*
 * 迫击炮炮弹投射物。
 * 不套用原版投掷物的阻力, 按配置的重力自行积分, 与面板上的无阻力抛体公式保持一致。
 * 每 tick 分小步推进并逐轴判定碰撞, 命中后交给基类的引信与粘性规则。
 */
public class MortarShellProjectile extends ShellProjectile {
    private static final double TICK_SECONDS = 1.0D / 20.0D;
    /* 子步取半格, 最高初速下每 tick 最多四十八步, 薄墙与栅栏不会被穿透 */
    private static final double SUBSTEP = 0.5D;
    private static final int MAX_SUBSTEPS = 48;

    public MortarShellProjectile(EntityType<? extends MortarShellProjectile> type, Level level) {
        super(type, level);
        this.setNoGravity(true);
    }

    public MortarShellProjectile(Level level, Vec3 position, Vec3 velocity, ShellSettings settings,
                                 @Nullable Entity owner, int fuseTicks) {
        super(MortarRegistry.MORTAR_SHELL_ENTITY.get(), level, position, velocity, settings, owner, fuseTicks);
        this.setNoGravity(true);
    }

    @Override
    public void tick() {
        if (sharedTick()) {
            return;
        }
        if (this.level().isClientSide()) {
            this.tickCount++;
            this.trail();
            return;
        }
        double gravity = ServerConfig.mortarGravity() * TICK_SECONDS;
        Vec3 velocity = this.getDeltaMovement().add(0.0D, -gravity, 0.0D);
        this.setDeltaMovement(velocity);
        double distance = velocity.length();
        int steps = (int) Math.min(MAX_SUBSTEPS, Math.max(1, Math.ceil(distance / SUBSTEP)));
        Vec3 step = velocity.scale(1.0D / steps);
        for (int i = 0; i < steps; i++) {
            if (this.moveStep(step)) {
                return;
            }
        }
        this.tickCount++;
    }

    /*
     * 烟尾迹。
     * 炮弹飞行速度很高, 单帧很难看清实体本身, 客户端在同步到的位置补烟与火星, 飞过去会留下一条可见的轨迹。
     */
    private void trail() {
        Vec3 motion = this.getDeltaMovement();
        double speed = motion.length();
        Vec3 back = speed > 0.01D ? this.position().subtract(motion.normalize().scale(0.35D)) : this.position();
        this.level().addParticle(net.minecraft.core.particles.ParticleTypes.LARGE_SMOKE,
                back.x, back.y, back.z, 0.0D, 0.02D, 0.0D);
        this.level().addParticle(net.minecraft.core.particles.ParticleTypes.SMOKE,
                this.getX(), this.getY(), this.getZ(), 0.0D, 0.01D, 0.0D);
        if (this.tickCount % 2 == 0) {
            this.level().addParticle(net.minecraft.core.particles.ParticleTypes.FLAME,
                    back.x, back.y, back.z, 0.0D, 0.0D, 0.0D);
        }
    }

    private boolean moveStep(Vec3 step) {
        Vec3 position = this.position();
        boolean hit = false;
        Direction direction = null;
        if (step.x != 0.0D) {
            if (this.level().noCollision(this, this.getBoundingBox().move(step.x, 0.0D, 0.0D))) {
                position = position.add(step.x, 0.0D, 0.0D);
            } else {
                hit = true;
                direction = step.x > 0 ? Direction.EAST : Direction.WEST;
            }
        }
        this.setPos(position.x, position.y, position.z);
        if (step.y != 0.0D) {
            if (this.level().noCollision(this, this.getBoundingBox().move(0.0D, step.y, 0.0D))) {
                position = position.add(0.0D, step.y, 0.0D);
            } else if (!hit) {
                hit = true;
                direction = step.y > 0 ? Direction.UP : Direction.DOWN;
            }
        }
        this.setPos(position.x, position.y, position.z);
        if (step.z != 0.0D) {
            if (this.level().noCollision(this, this.getBoundingBox().move(0.0D, 0.0D, step.z))) {
                position = position.add(0.0D, 0.0D, step.z);
            } else if (!hit) {
                hit = true;
                direction = step.z > 0 ? Direction.SOUTH : Direction.NORTH;
            }
        }
        this.setPos(position.x, position.y, position.z);
        if (this.hitEntity()) {
            return true;
        }
        if (hit && direction != null) {
            BlockPos pos = BlockPos.containing(this.position().add(direction.getStepX() * -0.05D,
                    direction.getStepY() * -0.05D, direction.getStepZ() * -0.05D));
            this.onHitBlock(new BlockHitResult(this.position(), direction, pos, false));
            return true;
        }
        return false;
    }

    private boolean hitEntity() {
        AABB area = this.getBoundingBox().inflate(0.3D);
        List<Entity> hits = this.level().getEntities(this, area,
                entity -> entity instanceof net.minecraft.world.entity.LivingEntity && this.canHitEntity(entity));
        if (hits.isEmpty()) {
            return false;
        }
        this.onHitEntity(new EntityHitResult(hits.get(0)));
        /* 定时引信且不粘的炮弹只是擦过生物, 应当继续飞; 只有粘附或起爆才停下 */
        return this.resting() || this.isRemoved();
    }

    @Override
    protected void explode(ServerLevel level) {
        ShellExplosion.detonate(level, this.position(), this.settings, this.getOwner(), 0.6D);
    }
}
