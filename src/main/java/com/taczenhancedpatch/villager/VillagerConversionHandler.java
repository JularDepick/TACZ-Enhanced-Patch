package com.taczenhancedpatch.villager;

import com.taczenhancedpatch.TACZEnhancedPatch;
import com.taczenhancedpatch.gunner.GunnerArsenal;
import com.taczenhancedpatch.gunner.GunnerArming;
import com.taczenhancedpatch.gunner.GunnerMarker;
import com.taczenhancedpatch.integration.TaczApi;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Containers;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.entity.living.LivingConversionEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.List;

/**
 * 持械村民被感染时的库存去向。
 * 感染产生的是一个新的僵尸村民, 它照常走怪物持械概率判定:
 * 命中则沿用村民原本的枪械与弹药, 未命中则把库存掉在原地, 不会凭空消失。
 */
@Mod.EventBusSubscriber(modid = TACZEnhancedPatch.MOD_ID)
public final class VillagerConversionHandler {
    private VillagerConversionHandler() {
    }

    @SubscribeEvent
    public static void onConverted(LivingConversionEvent.Post event) {
        if (!(event.getEntity() instanceof Villager villager) || !ArmedVillager.isArmed(villager)) {
            return;
        }
        if (!(villager.level() instanceof ServerLevel level)) {
            return;
        }
        List<ItemStack> stock = ArmedVillager.takeStock(villager);
        if (stock.isEmpty()) {
            return;
        }
        if (event.getOutcome() instanceof Mob zombie && GunnerMarker.isGunner(zombie) && inherit(zombie, stock)) {
            return;
        }
        for (ItemStack stack : stock) {
            Containers.dropItemStack(level, villager.getX(), villager.getY() + 0.5D, villager.getZ(), stack);
        }
        TACZEnhancedPatch.LOGGER.debug("[villager] stock dropped after conversion at {}", villager.blockPosition());
    }

    /* 命中持械时把村民的枪械与弹药转交给新的僵尸村民 */
    private static boolean inherit(Mob zombie, List<ItemStack> stock) {
        ItemStack gun = ItemStack.EMPTY;
        int rounds = 0;
        List<ItemStack> rest = new ArrayList<>();
        for (ItemStack stack : stock) {
            if (gun.isEmpty() && TaczApi.isGun(stack)) {
                gun = stack;
                continue;
            }
            if (rounds == 0 && isAmmo(stack)) {
                rounds = stack.getCount();
                continue;
            }
            rest.add(stack);
        }
        GunnerArsenal.GunProfile profile = gun.isEmpty() ? null
                : GunnerArsenal.byGunId(TaczApi.getGunId(gun));
        if (profile == null) {
            return false;
        }
        int pool = Math.max(1, rounds);
        GunnerMarker.mark(zombie, profile, pool, false);
        GunnerArming.equip(zombie, profile.gunId(), profile.fireMode(), pool, zombie.getRandom());
        for (ItemStack stack : rest) {
            Containers.dropItemStack(zombie.level(), zombie.getX(), zombie.getY() + 0.5D, zombie.getZ(), stack);
        }
        return true;
    }

    private static boolean isAmmo(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        return tag != null && tag.contains("AmmoId");
    }
}
