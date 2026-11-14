package com.taczenhancedpatch.world.schematic;

import com.taczenhancedpatch.TACZEnhancedPatch;
import com.taczenhancedpatch.faction.RuinSites;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * 世界加载时重扫遗迹建筑目录。
 * 外部目录位于存档服务端配置目录下, 进存档时扫描一次即可覆盖玩家在两次游戏之间增删的文件;
 * 配置重新读取时的再次重扫由配置生命周期触发, 见 ConfigEvents。
 */
@Mod.EventBusSubscriber(modid = TACZEnhancedPatch.MOD_ID)
public final class SchematicCatalogRefreshHandler {
    private SchematicCatalogRefreshHandler() {
    }

    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent event) {
        /* 把存档里已有的遗迹中心灌进间距登记表, 新遗迹据此避让 */
        for (ServerLevel level : event.getServer().getAllLevels()) {
            List<BlockPos> origins = new ArrayList<>();
            for (RuinSites.Site site : RuinSites.get(level).sites()) {
                origins.add(site.origin());
            }
            RuinSites.seed(level.dimension(), origins);
        }
        SchematicCatalog.refresh();
        Path root = SchematicCatalog.externalRoot();
        TACZEnhancedPatch.LOGGER.info("[ruin] blueprints ready: {} builtin, {} external ({})",
                SchematicCatalog.builtinFiles().size(),
                SchematicCatalog.externalFiles().size(),
                root == null ? "no server config dir" : root.toString());
    }
}
