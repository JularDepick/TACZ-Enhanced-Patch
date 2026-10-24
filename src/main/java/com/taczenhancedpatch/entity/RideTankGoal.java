package com.taczenhancedpatch.entity;

import com.taczenhancedpatch.faction.FactionData;
import com.taczenhancedpatch.faction.Factions;
import net.minecraft.world.entity.ai.goal.Goal;

import javax.annotation.Nullable;
import java.util.EnumSet;

/**
 * 小兵骑乘坦克。
 * 小兵每隔一段时间寻找附近的坦克: 同阵营的坦克, 或者无阵营的坦克;
 * 找到后寻路靠近, 距离进入两格内就骑上去, 由坦克载着移动。
 */
public final class RideTankGoal extends Goal {
    private static final double SEARCH_RADIUS = 24.0D;
    private static final double MOUNT_DISTANCE = 2.0D;
    private static final int SEARCH_INTERVAL = 20;

    private final GunnerGrunt grunt;
    @Nullable
    private TankElite target;

    public RideTankGoal(GunnerGrunt grunt) {
        this.grunt = grunt;
        this.setFlags(EnumSet.of(Goal.Flag.MOVE));
    }

    @Override
    public boolean canUse() {
        if (this.grunt.isPassenger() || this.grunt.tickCount % SEARCH_INTERVAL != 0) {
            return false;
        }
        this.target = findTank();
        return this.target != null;
    }

    @Override
    public boolean canContinueToUse() {
        return this.target != null && this.target.isAlive() && !this.grunt.isPassenger()
                && this.grunt.distanceToSqr(this.target) <= SEARCH_RADIUS * SEARCH_RADIUS;
    }

    @Override
    public void tick() {
        if (this.target == null) {
            return;
        }
        double distance = this.grunt.distanceToSqr(this.target);
        if (distance <= MOUNT_DISTANCE * MOUNT_DISTANCE) {
            this.grunt.startRiding(this.target);
            this.grunt.getNavigation().stop();
            this.target = null;
            return;
        }
        this.grunt.getNavigation().moveTo(this.target, 1.2D);
    }

    @Override
    public void stop() {
        this.grunt.getNavigation().stop();
        this.target = null;
    }

    @Nullable
    private TankElite findTank() {
        TankElite best = null;
        double bestDistance = SEARCH_RADIUS * SEARCH_RADIUS;
        for (TankElite tank : this.grunt.level().getEntitiesOfClass(TankElite.class,
                this.grunt.getBoundingBox().inflate(SEARCH_RADIUS))) {
            if (!tank.isAlive() || tank.isVehicle() && tank.getPassengers().size() >= 4) {
                continue;
            }
            if (!canRide(tank)) {
                continue;
            }
            double distance = this.grunt.distanceToSqr(tank);
            if (distance < bestDistance) {
                bestDistance = distance;
                best = tank;
            }
        }
        return best;
    }

    /* 同阵营或无阵营的坦克都可以骑, 有阵营的小兵骑上无阵营坦克后由坦克加入该阵营 */
    private boolean canRide(TankElite tank) {
        String gruntFaction = FactionData.factionOf(this.grunt);
        String tankFaction = FactionData.factionOf(tank);
        if (tankFaction.isEmpty()) {
            return true;
        }
        return Factions.same(gruntFaction, tankFaction);
    }
}
