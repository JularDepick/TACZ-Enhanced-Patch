package com.taczenhancedpatch.runtime;

import com.taczenhancedpatch.TACZEnhancedPatch;
import com.taczenhancedpatch.entity.TankCrew;
import com.taczenhancedpatch.faction.RuinExperience;
import com.taczenhancedpatch.faction.RuinGarrison;
import com.taczenhancedpatch.faction.RuinSites;
import com.taczenhancedpatch.gunner.GunnerCombat;
import com.taczenhancedpatch.world.schematic.RuinPasteHandler;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 进程级状态的清理。
 * 模组把若干运行期状态放在静态集合里, 服务端停止时统一清空,
 * 否则同一进程内切换存档会把旧世界的任务, 在线状态与离线欠账带进新世界。
 */
@Mod.EventBusSubscriber(modid = TACZEnhancedPatch.MOD_ID)
public final class RuntimeStateCleaner {
    private RuntimeStateCleaner() {
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        RuinSites.clearPending();
        RuinPasteHandler.clearJobs();
        RuinGarrison.clearPresence();
        RuinExperience.clearPending();
        TankCrew.clearAll();
        GunnerCombat.clearStates();
    }
}
