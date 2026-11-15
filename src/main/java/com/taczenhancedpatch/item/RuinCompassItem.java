package com.taczenhancedpatch.item;

import com.taczenhancedpatch.TACZEnhancedPatch;
import com.taczenhancedpatch.config.ServerConfig;
import com.taczenhancedpatch.faction.RuinSites;
import com.taczenhancedpatch.world.structure.RuinSpreadPlacement;
import com.taczenhancedpatch.world.structure.RuinStructure;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureSet;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.util.Mth;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * 遗迹指南针。
 * 右键时在服务端查找当前维度最近的遗迹, 把坐标写进物品, 之后指针始终指向该遗迹;
 * 找到后给出距离提示, 没有遗迹时给出提示且保持空指针。
 */
public class RuinCompassItem extends Item {
    /* 搜索半径, 单位是选址网格的格数; 24 格在默认间距下约合一万六千格 */
    private static final int SEARCH_CELLS = 24;
    private static final ResourceKey<Structure> RUIN_KEY =
            ResourceKey.create(Registries.STRUCTURE, new ResourceLocation(TACZEnhancedPatch.MOD_ID, "ruined_outpost"));
    private static final String TAG_HAS = "RuinFound";
    private static final String TAG_X = "RuinX";
    private static final String TAG_Y = "RuinY";
    private static final String TAG_Z = "RuinZ";
    /* 八方位名称, 与方向标尺同一口径: 北为 0 度, 顺时针递增 */
    private static final String[] DIRECTIONS = {
            "item.tacz_enhanced_patch.ruin_compass.dir.n",
            "item.tacz_enhanced_patch.ruin_compass.dir.ne",
            "item.tacz_enhanced_patch.ruin_compass.dir.e",
            "item.tacz_enhanced_patch.ruin_compass.dir.se",
            "item.tacz_enhanced_patch.ruin_compass.dir.s",
            "item.tacz_enhanced_patch.ruin_compass.dir.sw",
            "item.tacz_enhanced_patch.ruin_compass.dir.w",
            "item.tacz_enhanced_patch.ruin_compass.dir.nw"};

    public RuinCompassItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!level.isClientSide() && level instanceof ServerLevel serverLevel) {
            BlockPos target = nearestRuin(serverLevel, player.blockPosition());
            if (target == null) {
                player.displayClientMessage(Component.translatable("item.tacz_enhanced_patch.ruin_compass.none")
                        .withStyle(ChatFormatting.GRAY), true);
            } else {
                CompoundTag tag = stack.getOrCreateTag();
                tag.putBoolean(TAG_HAS, true);
                tag.putInt(TAG_X, target.getX());
                tag.putInt(TAG_Y, target.getY());
                tag.putInt(TAG_Z, target.getZ());
                int distance = (int) Math.round(Math.sqrt(target.distSqr(player.blockPosition())));
                double dx = target.getX() + 0.5D - player.getX();
                double dz = target.getZ() + 0.5D - player.getZ();
                /* 方位角: 北为 0 度, 顺时针递增; 与客户端方向标尺的读数一致 */
                double bearing = Mth.positiveModulo(Math.toDegrees(Math.atan2(dx, -dz)), 360.0D);
                Component direction = Component.translatable(DIRECTIONS[(int) Math.round(bearing / 45.0D) % 8]);
                player.displayClientMessage(Component.translatable("item.tacz_enhanced_patch.ruin_compass.found",
                                distance, direction, Math.round(bearing))
                        .withStyle(ChatFormatting.GREEN), true);
            }
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
    }

    /* 物品里记录的遗迹中心, 没有记录时返回空 */
    @Nullable
    public static BlockPos target(ItemStack stack) {
        if (stack == null || stack.isEmpty() || !stack.hasTag()) {
            return null;
        }
        CompoundTag tag = stack.getTag();
        if (tag == null || !tag.getBoolean(TAG_HAS)) {
            return null;
        }
        return new BlockPos(tag.getInt(TAG_X), tag.getInt(TAG_Y), tag.getInt(TAG_Z));
    }

    /*
     * 当前维度里距离玩家最近的、尚未被夺旗的遗迹中心。
     * 候选点按选址那套网格数学直接算出来, 因此没去过的遗迹也能指路;
     * 已夺旗的站点跳过, 未登记过的一律视为未夺旗。
     */
    @Nullable
    private static BlockPos nearestRuin(ServerLevel level, BlockPos from) {
        RuinSpreadPlacement placement = placement(level);
        if (placement == null) {
            return null;
        }
        long seed = level.getSeed();
        int minDistance = ServerConfig.ruinMinDistance();
        int spacing = RuinSpreadPlacement.spacingChunks(minDistance);
        ChunkGenerator generator = level.getChunkSource().getGenerator();
        RandomState randomState = level.getChunkSource().randomState();
        int cellX0 = Math.floorDiv(from.getX() >> 4, spacing);
        int cellZ0 = Math.floorDiv(from.getZ() >> 4, spacing);
        List<BlockPos> candidates = new ArrayList<>();
        for (int cellX = cellX0 - SEARCH_CELLS; cellX <= cellX0 + SEARCH_CELLS; cellX++) {
            for (int cellZ = cellZ0 - SEARCH_CELLS; cellZ <= cellZ0 + SEARCH_CELLS; cellZ++) {
                ChunkPos chunk = placement.candidate(seed, minDistance, cellX * spacing, cellZ * spacing);
                int x = chunk.getMiddleBlockX();
                int z = chunk.getMiddleBlockZ();
                int surface = RuinStructure.surfaceAt(generator, level, randomState, x, z);
                if (surface <= generator.getSeaLevel()) {
                    continue;
                }
                candidates.add(new BlockPos(x, surface, z));
            }
        }
        candidates.sort(Comparator.comparingDouble(pos -> pos.distSqr(from)));
        for (BlockPos center : candidates) {
            RuinSites.Site site = RuinSites.find(level, center);
            if (site != null && site.captured()) {
                continue;
            }
            if (!RuinStructure.flatEnough(generator, level, randomState, center.getX(), center.getY(), center.getZ())) {
                continue;
            }
            return center;
        }
        return null;
    }

    /* 数据包里的结构集持有我们的放置类型, 从注册表取回来以便复用同一套候选数学 */
    @Nullable
    private static RuinSpreadPlacement placement(ServerLevel level) {
        for (StructureSet set : level.registryAccess().registryOrThrow(Registries.STRUCTURE_SET)) {
            if (!(set.placement() instanceof RuinSpreadPlacement spread)) {
                continue;
            }
            for (StructureSet.StructureSelectionEntry entry : set.structures()) {
                if (entry.structure().is(RUIN_KEY)) {
                    return spread;
                }
            }
        }
        return null;
    }
}