package com.taczenhancedpatch.world.schematic;

import com.taczenhancedpatch.TACZEnhancedPatch;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.nio.file.Path;

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
        SchematicCatalog.refresh();
        Path root = SchematicCatalog.externalRoot();
        TACZEnhancedPatch.LOGGER.info("[ruin] schematics ready: {} builtin, {} external ({})",
                SchematicCatalog.builtinFiles().size(),
                SchematicCatalog.externalFiles().size(),
                root == null ? "no server config dir" : root.toString());
    }
}
