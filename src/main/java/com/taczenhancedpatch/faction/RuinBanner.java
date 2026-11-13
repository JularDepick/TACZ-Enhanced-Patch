package com.taczenhancedpatch.faction;

import com.taczenhancedpatch.TACZEnhancedPatch;
import com.taczenhancedpatch.config.ServerConfig;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.BannerBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.Containers;
import net.minecraftforge.event.entity.living.LivingDestroyBlockEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.event.level.ExplosionEvent;
import net.minecraftforge.event.entity.player.ItemTooltipEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 遗迹旗帜。
 * 旗帜是遗迹的视觉标识与领地中心: 夺旗成功前不可破坏, 爆炸也不会炸掉;
 * 夺旗成功后可破坏并掉落自身, 掉落的旗帜携带战绩, 悬浮提示逐行显示。
 */
@Mod.EventBusSubscriber(modid = TACZEnhancedPatch.MOD_ID)
public final class RuinBanner {
    /* 与原版染料顺序一致的十六面旗帜 */
    private static final Block[] BANNERS = {
            Blocks.WHITE_BANNER, Blocks.ORANGE_BANNER, Blocks.MAGENTA_BANNER, Blocks.LIGHT_BLUE_BANNER,
            Blocks.YELLOW_BANNER, Blocks.LIME_BANNER, Blocks.PINK_BANNER, Blocks.GRAY_BANNER,
            Blocks.LIGHT_GRAY_BANNER, Blocks.CYAN_BANNER, Blocks.PURPLE_BANNER, Blocks.BLUE_BANNER,
            Blocks.BROWN_BANNER, Blocks.GREEN_BANNER, Blocks.RED_BANNER, Blocks.BLACK_BANNER};
    /* 纪念品数据挂在物品 NBT 上 */
    public static final String TAG_STATS = "tacz_enhanced_patch_ruin_stats";
    private static final String TAG_LINES = "lines";
    private static final String TAG_KILLS = "kills";
    private static final String TAG_XP = "xp";
    private static final String TAG_NAME = "name";
    private static final String TAG_POS = "pos";

    private RuinBanner() {
    }

    /* 从建筑底面中心向上找第一个空气格, 必要时补一块支撑再放旗帜 */
    public static boolean place(ServerLevel level, RuinSites.Site site, BlockPos center, int baseY, int height,
                                RandomSource random) {
        BlockPos spot = null;
        int limit = Math.min(level.getMaxBuildHeight() - 1, baseY + Math.max(1, height));
        for (int y = baseY; y <= limit; y++) {
            BlockPos candidate = new BlockPos(center.getX(), y, center.getZ());
            if (level.getBlockState(candidate).isAir()) {
                spot = candidate;
                break;
            }
        }
        /* 中心列被整柱占满时退回底面, 保证领地中心一定存在 */
        if (spot == null) {
            spot = new BlockPos(center.getX(), baseY, center.getZ());
        }
        BlockPos below = spot.below();
        if (!level.getBlockState(below).isSolidRender(level, below)) {
            level.setBlock(below, Blocks.STONE_BRICKS.defaultBlockState(), 2);
        }
        DyeColor color = DyeColor.byId(random.nextInt(BANNERS.length));
        BlockState state = BANNERS[color.getId()].defaultBlockState();
        level.setBlock(spot, state, 3);
        site.setBanner(spot, color.getSerializedName());
        return true;
    }

    @Nullable
    public static RuinSites.Site siteAt(ServerLevel level, BlockPos pos) {
        for (RuinSites.Site site : RuinSites.get(level).sites()) {
            if (pos.equals(site.banner())) {
                return site;
            }
        }
        return null;
    }

    @SubscribeEvent
    public static void onBreak(BlockEvent.BreakEvent event) {
        if (!(event.getLevel() instanceof ServerLevel level)) {
            return;
        }
        if (!(event.getState().getBlock() instanceof BannerBlock)) {
            return;
        }
        RuinSites.Site site = siteAt(level, event.getPos());
        if (site == null) {
            return;
        }
        event.setCanceled(true);
        if (!site.captured()) {
            return;
        }
        BlockPos pos = event.getPos();
        level.removeBlock(pos, false);
        ItemStack stack = new ItemStack(event.getState().getBlock());
        writeStats(stack, site);
        /* 名称固定为 颜色阵营旗帜-(x,y,z), 方便玩家按坐标巡回 */
        stack.setHoverName(Component.translatable("tacz_enhanced_patch.ruin.banner_name",
                Component.translatable("color.minecraft." + site.bannerColor()),
                pos.getX(), pos.getY(), pos.getZ()));
        Containers.dropItemStack(level, pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D, stack);
    }

    /* 生物搬走方块的机制同样拦截, 例如末影人一类会主动取方块的存在 */
    @SubscribeEvent
    public static void onLivingDestroy(LivingDestroyBlockEvent event) {
        if (!(event.getEntity().level() instanceof ServerLevel level)) {
            return;
        }
        RuinSites.Site site = siteAt(level, event.getPos());
        if (site != null && !site.captured()) {
            event.setCanceled(true);
        }
    }

    /* 未夺旗的旗帜与它脚下的支撑块都不会被爆炸移除, 支撑块没了旗帜会自行掉落 */
    @SubscribeEvent
    public static void onExplosion(ExplosionEvent.Detonate event) {
        if (!(event.getLevel() instanceof ServerLevel level)) {
            return;
        }
        event.getAffectedBlocks().removeIf(pos -> {
            RuinSites.Site site = siteAt(level, pos);
            if (site != null && !site.captured()) {
                return true;
            }
            RuinSites.Site support = siteAt(level, pos.above());
            return support != null && !support.captured();
        });
    }

    /* 兜底还原: 命令, 其他模组的直接写方块, 支撑块被移除导致旗帜掉落等途径都在这里收口 */
    public static void restore(ServerLevel level, RuinSites.Site site) {
        BlockPos banner = site.banner();
        if (banner == null || site.captured() || !level.isLoaded(banner)) {
            return;
        }
        BlockPos support = banner.below();
        if (level.isLoaded(support) && !level.getBlockState(support).isSolidRender(level, support)) {
            level.setBlock(support, Blocks.STONE_BRICKS.defaultBlockState(), 2);
        }
        /* 连颜色一起校验, 防止别人在原位塞一面别的旗子冒充 */
        if (level.getBlockState(banner).getBlock() == blockFor(site.bannerColor())) {
            return;
        }
        level.setBlock(banner, blockFor(site.bannerColor()).defaultBlockState(), 3);
        TACZEnhancedPatch.LOGGER.warn("[ruin] banner restored at {}", banner);
    }

    /* 颜色名到旗帜方块的映射, 记录里存的是原版染料序列名 */
    public static Block blockFor(String colorName) {
        for (DyeColor color : DyeColor.values()) {
            if (color.getSerializedName().equals(colorName)) {
                return BANNERS[color.getId()];
            }
        }
        return BANNERS[0];
    }

    @SubscribeEvent
    public static void onTooltip(ItemTooltipEvent event) {
        CompoundTag tag = event.getItemStack().getTag();
        if (tag == null || !tag.contains(TAG_STATS, Tag.TAG_COMPOUND)) {
            return;
        }
        CompoundTag stats = tag.getCompound(TAG_STATS);
        event.getToolTip().add(Component.translatable("tacz_enhanced_patch.ruin.banner_header")
                .withStyle(ChatFormatting.GOLD));
        if (stats.contains(TAG_POS, Tag.TAG_INT_ARRAY)) {
            int[] pos = stats.getIntArray(TAG_POS);
            event.getToolTip().add(Component.translatable("tacz_enhanced_patch.ruin.banner_coord",
                    pos[0], pos[1], pos[2]).withStyle(ChatFormatting.GRAY));
        }
        ListTag lines = stats.getList(TAG_LINES, Tag.TAG_COMPOUND);
        for (int i = 0; i < lines.size(); i++) {
            CompoundTag line = lines.getCompound(i);
            event.getToolTip().add(Component.translatable("tacz_enhanced_patch.ruin.banner_line",
                    line.getString(TAG_NAME), line.getInt(TAG_KILLS), line.getInt(TAG_XP))
                    .withStyle(ChatFormatting.GRAY));
        }
    }

    /* 把战绩摘要写进掉落的旗帜 */
    private static void writeStats(ItemStack stack, RuinSites.Site site) {
        RuinStats stats = site.stats();
        if (stats == null) {
            return;
        }
        CompoundTag root = new CompoundTag();
        ListTag lines = new ListTag();
        List<UUID> players = new ArrayList<>(stats.names().keySet());
        for (UUID player : players) {
            CompoundTag line = new CompoundTag();
            line.putString(TAG_NAME, stats.names().getOrDefault(player, player.toString()));
            line.putInt(TAG_KILLS, stats.killCount(player));
            line.putInt(TAG_XP, RuinLoot.experienceFor(stats, player));
            lines.add(line);
        }
        root.put(TAG_LINES, lines);
        BlockPos banner = site.banner();
        if (banner != null) {
            root.putIntArray(TAG_POS, new int[]{banner.getX(), banner.getY(), banner.getZ()});
        }
        stack.addTagElement(TAG_STATS, root);
        if (ServerConfig.debugEvents()) {
            TACZEnhancedPatch.LOGGER.info("[ruin] banner souvenir with {} entries from {}", lines.size(), site.origin());
        }
    }

    /* 供统计使用: 当前站点旗帜是否存在 */
    public static boolean present(RuinSites.Site site) {
        return site.banner() != null;
    }
}
