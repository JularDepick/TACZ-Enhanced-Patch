package com.taczenhancedpatch.faction;

import com.taczenhancedpatch.TACZEnhancedPatch;
import com.taczenhancedpatch.gunner.GunnerMarker;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 遗迹战斗统计的事件入口。
 * 玩家击杀遗迹生物时累计击杀记录, 造成伤害时累计伤害贡献;
 * 两者都写进该遗迹的统计, 与正常的死亡掉落互不影响。
 */
@Mod.EventBusSubscriber(modid = TACZEnhancedPatch.MOD_ID)
public final class RuinCombatStats {
    private RuinCombatStats() {
    }

    @SubscribeEvent
    public static void onDeath(LivingDeathEvent event) {
        if (!(event.getEntity() instanceof Mob dead) || !RuinTerritory.isRuinMob(dead)) {
            return;
        }
        if (!(dead.level() instanceof ServerLevel level)) {
            return;
        }
        Entity source = event.getSource().getEntity();
        if (!(source instanceof ServerPlayer player) || !RuinTerritory.participates(player)) {
            return;
        }
        RuinSites.Site site = siteOf(level, dead);
        if (site == null || site.stats() == null) {
            return;
        }
        GunnerMarker.Loadout loadout = GunnerMarker.loadout(dead);
        ItemStack weapon = GunnerMarker.storedGun(dead);
        if (weapon.isEmpty()) {
            weapon = dead.getMainHandItem().copy();
        }
        Map<String, Integer> ammo = new HashMap<>();
        if (loadout != null) {
            ammo.put(loadout.ammoId().toString(), loadout.ammoPool());
        }
        site.stats().addKill(new RuinStats.Kill(player.getUUID(), player.getGameProfile().getName(),
                RuinLoot.weaponValue(loadout == null ? null : loadout.tier()), weapon, ammo));
        RuinSites.get(level).setDirty();
        if (com.taczenhancedpatch.config.ServerConfig.debugEvents()) {
            TACZEnhancedPatch.LOGGER.info("[ruin] kill by {} at {}", player.getGameProfile().getName(), site.origin());
        }
    }

    @SubscribeEvent
    public static void onHurt(LivingHurtEvent event) {
        if (!(event.getEntity() instanceof Mob mob) || !RuinTerritory.isRuinMob(mob)) {
            return;
        }
        if (!(mob.level() instanceof ServerLevel level)) {
            return;
        }
        if (!(event.getSource().getEntity() instanceof ServerPlayer player)
                || !RuinTerritory.participates(player)) {
            return;
        }
        RuinSites.Site site = siteOf(level, mob);
        if (site == null || site.stats() == null) {
            return;
        }
        site.stats().addDamage(player.getUUID(), player.getGameProfile().getName(), event.getAmount());
        RuinSites.get(level).setDirty();
    }

    private static RuinSites.Site siteOf(ServerLevel level, Mob mob) {
        BlockPos home = FactionData.homeOf(mob);
        return home == null ? null : RuinSites.find(level, home);
    }

    /* 快照: 领地内全部遗迹生物的武器价值与按口径弹药量 */
    public static int snapshotWeaponValue(ServerLevel level, RuinSites.Site site) {
        int value = 0;
        for (Mob mob : RuinTerritory.mobs(level, site)) {
            GunnerMarker.Loadout loadout = GunnerMarker.loadout(mob);
            if (loadout != null) {
                value += RuinLoot.weaponValue(loadout.tier());
            }
        }
        return value;
    }

    public static Map<String, Integer> snapshotAmmo(ServerLevel level, RuinSites.Site site) {
        Map<String, Integer> ammo = new HashMap<>();
        for (Mob mob : RuinTerritory.mobs(level, site)) {
            GunnerMarker.Loadout loadout = GunnerMarker.loadout(mob);
            if (loadout != null) {
                ammo.merge(loadout.ammoId().toString(), loadout.ammoPool(), Integer::sum);
            }
        }
        return ammo;
    }
}
