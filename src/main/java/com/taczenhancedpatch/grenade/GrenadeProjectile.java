package com.taczenhancedpatch.grenade;

import com.taczenhancedpatch.shell.ShellExplosion;
import com.taczenhancedpatch.shell.ShellProjectile;
import com.taczenhancedpatch.shell.ShellSettings;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;

/*
 * 手雷投掷物。
 * 运动交给原版投掷物逻辑, 本类只补上爆炸派发。
 */
public class GrenadeProjectile extends ShellProjectile {
    public GrenadeProjectile(EntityType<? extends GrenadeProjectile> type, Level level) {
        super(type, level);
    }

    public GrenadeProjectile(Level level, Vec3 position, Vec3 velocity, ShellSettings settings,
                             @Nullable Entity owner, int fuseTicks) {
        super(GrenadeRegistry.GRENADE.get(), level, position, velocity, settings, owner, fuseTicks);
    }

    @Override
    protected void explode(ServerLevel level) {
        ShellExplosion.detonate(level, this.position(), this.settings, this.getOwner(), 0.4D);
    }
}
