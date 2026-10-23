package com.taczenhancedpatch.hitindicator;

import com.taczenhancedpatch.TACZEnhancedPatch;
import com.taczenhancedpatch.integration.TaczApi;
import com.taczenhancedpatch.network.PatchNetwork;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 枪击来源提示的服务端数据源。
 * 玩家被上游枪械子弹命中时, 把子弹或射手的水平方位发给该玩家;
 * 是否显示由客户端开关决定, 服务端不做额外判定。
 */
@Mod.EventBusSubscriber(modid = TACZEnhancedPatch.MOD_ID)
public final class HitDirectionNotifier {
    private HitDirectionNotifier() {
    }

    @SubscribeEvent
    public static void onLivingHurt(LivingHurtEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        DamageSource source = event.getSource();
        Entity direct = source.getDirectEntity();
        Entity owner = source.getEntity();
        if (!TaczApi.isGunProjectile(direct) && !TaczApi.isGunProjectile(owner)) {
            return;
        }
        Entity origin = direct != null ? direct : owner;
        if (origin == null) {
            return;
        }
        double dx = origin.getX() - player.getX();
        double dz = origin.getZ() - player.getZ();
        if (dx == 0.0D && dz == 0.0D) {
            return;
        }
        float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
        PatchNetwork.sendHitDirection(player, yaw);
    }
}
