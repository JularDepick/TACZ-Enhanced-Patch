package com.taczenhancedpatch.faction;

import com.taczenhancedpatch.TACZEnhancedPatch;
import com.taczenhancedpatch.entity.GruntRegistry;
import com.taczenhancedpatch.entity.TankRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 遗迹守军生成。
 * 站点在区块加载且玩家靠近时补齐守军: 若干持枪小兵加一辆坦克,
 * 全部绑定该遗迹的阵营并记住遗迹中心, 作为领地判定与共享敌对的依据。
 */
@Mod.EventBusSubscriber(modid = TACZEnhancedPatch.MOD_ID)
public final class RuinGuardSpawner {
    private static final int CHECK_INTERVAL = 20;
    private static final int ACTIVATION_DISTANCE = 96;
    private static int tick;

    private RuinGuardSpawner() {
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        if (++tick % CHECK_INTERVAL != 0) {
            return;
        }
        event.getServer().getAllLevels().forEach(RuinGuardSpawner::tickLevel);
    }

    private static void tickLevel(ServerLevel level) {
        if (level.players().isEmpty()) {
            return;
        }
        RuinSites data = RuinSites.get(level);
        data.drainPending();
        for (RuinSites.Site site : data.sites()) {
            if (site.seeded() || !level.isLoaded(site.origin())) {
                continue;
            }
            boolean near = level.players().stream()
                    .anyMatch(player -> player.blockPosition().distSqr(site.origin())
                            <= ACTIVATION_DISTANCE * ACTIVATION_DISTANCE);
            if (!near) {
                continue;
            }
            spawnSquad(level, site);
            data.markSeeded(site);
        }
    }

    private static void spawnSquad(ServerLevel level, RuinSites.Site site) {
        RandomSource random = level.getRandom();
        DifficultyInstance difficulty = level.getCurrentDifficultyAt(site.origin());
        String faction = FactionData.factionOfRuin(site.origin());
        int grunts = 2 + random.nextInt(3);
        for (int i = 0; i < grunts; i++) {
            spawn(level, GruntRegistry.GUNNER_GRUNT.get(), site, faction, difficulty, random);
        }
        spawn(level, TankRegistry.TANK_ELITE.get(), site, faction, difficulty, random);
    }

    private static void spawn(ServerLevel level, EntityType<?> type, RuinSites.Site site, String faction,
                              DifficultyInstance difficulty, RandomSource random) {
        if (!(type.create(level) instanceof Mob mob)) {
            return;
        }
        BlockPos pos = surfaceNear(level, site, random);
        mob.moveTo(pos.getX() + 0.5D, pos.getY(), pos.getZ() + 0.5D, random.nextFloat() * 360.0F, 0.0F);
        FactionData.setFaction(mob, faction);
        FactionData.setHome(mob, site.origin());
        FactionData.setReviveDepth(mob, 0);
        mob.finalizeSpawn(level, difficulty, MobSpawnType.STRUCTURE, null, null);
        mob.setPersistenceRequired();
        level.addFreshEntity(mob);
    }

    private static BlockPos surfaceNear(ServerLevel level, RuinSites.Site site, RandomSource random) {
        BlockPos origin = site.origin();
        for (int attempt = 0; attempt < 12; attempt++) {
            double angle = random.nextDouble() * Math.PI * 2.0D;
            int radius = 4 + random.nextInt(9);
            int x = origin.getX() + (int) Math.round(Math.cos(angle) * radius);
            int z = origin.getZ() + (int) Math.round(Math.sin(angle) * radius);
            BlockPos surface = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, new BlockPos(x, origin.getY(), z));
            if (Math.abs(surface.getY() - origin.getY()) <= 6) {
                return surface;
            }
        }
        return level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, origin);
    }
}
