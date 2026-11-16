package com.taczenhancedpatch.mortar;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

import javax.annotation.Nullable;

/*
 * 迫击炮方块。
 * 占一格, 必须放在实体方块上; 基座固定, 炮筒由方块实体渲染并随角度转动。
 * 右键装填, 已装填时右键发射, 空手右键进入驾驶; 左键弹出炮弹或调节角度。
 */
public class MortarBlock extends BaseEntityBlock {
    private static final VoxelShape SHAPE = Block.box(2.0D, 0.0D, 2.0D, 14.0D, 12.0D, 14.0D);

    public MortarBlock(Properties properties) {
        super(properties);
    }

    /*
     * 基座交给普通方块模型渲染。
     * 写成 ENTITYBLOCK_ANIMATED 时整块方块模型都会被跳过, 只剩方块实体画的炮筒, 基座因此看不见。
     */
    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new MortarBlockEntity(pos, state);
    }

    /* 发射倒计时与下方支撑检查都在方块实体的 tick 里, 不注册 ticker 就永远不推进 */
    @Override
    @Nullable
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide()) {
            return null;
        }
        return createTickerHelper(type, MortarRegistry.MORTAR_ENTITY.get(), MortarBlockEntity::tick);
    }

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand,
                                 BlockHitResult hit) {
        if (level.isClientSide()) {
            return InteractionResult.SUCCESS;
        }
        if (!(level.getBlockEntity(pos) instanceof MortarBlockEntity mortar)) {
            return InteractionResult.PASS;
        }
        /* 蹲下右键只开关信息面板, 面板在客户端处理 */
        if (player.isShiftKeyDown()) {
            return InteractionResult.CONSUME;
        }
        ItemStack held = player.getMainHandItem();
        if (held.getItem() instanceof MortarShellItem) {
            if (mortar.loadShell(held)) {
                if (!player.getAbilities().instabuild) {
                    held.shrink(1);
                }
            } else {
                mortar.startFire();
            }
            return InteractionResult.CONSUME;
        }
        if (mortar.loadedShell()) {
            mortar.startFire();
            return InteractionResult.CONSUME;
        }
        mortar.mount(player);
        return InteractionResult.CONSUME;
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!state.is(newState.getBlock()) && !level.isClientSide()
                && level.getBlockEntity(pos) instanceof MortarBlockEntity mortar && !mortar.migrating()) {
            mortar.dismount();
            ItemStack loaded = mortar.ejectShell();
            if (!loaded.isEmpty()) {
                Block.popResource(level, pos, loaded);
            }
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }

    /* 左键: 有炮弹时弹出, 空手时调角; 返回真表示这次点击被本方块消费掉 */
    public static boolean attack(Level level, BlockPos pos, Player player) {
        if (level.isClientSide() || !(level.getBlockEntity(pos) instanceof MortarBlockEntity mortar)) {
            return false;
        }
        if (mortar.loadedShell()) {
            ItemStack ejected = mortar.ejectShell();
            if (!ejected.isEmpty()) {
                Block.popResource(level, pos.above(), ejected);
            }
            return true;
        }
        if (!player.getMainHandItem().isEmpty()) {
            return false;
        }
        if (player.isShiftKeyDown()) {
            float next = mortar.pitchDeg() + 5.0F;
            mortar.setPitch(next > MortarBlockEntity.PITCH_MAX ? 0.0F : next);
        } else {
            mortar.setYaw(mortar.yawDeg() + 5.0F);
        }
        return true;
    }
}
