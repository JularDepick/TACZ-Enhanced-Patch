package com.taczenhancedpatch.world.schematic;

import com.mojang.logging.LogUtils;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.SlabType;
import org.slf4j.Logger;

import java.util.HashSet;
import java.util.Set;

/**
 * 旧版数字方块 ID 到现代方块状态的映射。
 * .schematic 用 Blocks 与 Data 两个字节数组描述方块, 这里覆盖建筑常用的方块族,
 * 未收录的 ID 退回石头并只记一条调试日志, 避免个别方块缺失导致整座建筑粘贴失败。
 */
public final class LegacyBlocks {
    private static final Logger LOGGER = LogUtils.getLogger();
    /* id 与 data 各八位, 直接按组合值缓存, 一张表最多 65536 项 */
    private static final BlockState[] CACHE = new BlockState[1 << 16];
    private static final Set<Integer> UNKNOWN = new HashSet<>();
    private static final String[] COLORS = {
            "white", "orange", "magenta", "light_blue", "yellow", "lime", "pink", "gray",
            "light_gray", "cyan", "purple", "blue", "brown", "green", "red", "black"};
    private static final String[] WOODS = {"oak", "spruce", "birch", "jungle", "acacia", "dark_oak"};

    private LegacyBlocks() {
    }

    public static BlockState state(int id, int data) {
        int key = ((id & 0xFF) << 8) | (data & 0xFF);
        BlockState cached = CACHE[key];
        if (cached != null) {
            return cached;
        }
        BlockState resolved = resolve(id & 0xFF, data & 0xFF);
        CACHE[key] = resolved;
        return resolved;
    }

    private static BlockState resolve(int id, int data) {
        switch (id) {
            case 0:
                return Blocks.AIR.defaultBlockState();
            case 1:
                return block(switch (data & 7) {
                    case 1 -> "granite";
                    case 2 -> "polished_granite";
                    case 3 -> "diorite";
                    case 4 -> "polished_diorite";
                    case 5 -> "andesite";
                    case 6 -> "polished_andesite";
                    default -> "stone";
                });
            case 2:
                return block("grass_block");
            case 3:
                return block(switch (data & 3) {
                    case 1 -> "coarse_dirt";
                    case 2 -> "podzol";
                    default -> "dirt";
                });
            case 4:
                return block("cobblestone");
            case 5:
                return block(WOODS[Math.min(data & 7, WOODS.length - 1)] + "_planks");
            case 6:
                return block("oak_sapling");
            case 7:
                return block("bedrock");
            case 8:
            case 9:
                return block("water");
            case 10:
            case 11:
                return block("lava");
            case 12:
                return block((data & 1) == 0 ? "sand" : "red_sand");
            case 13:
                return block("gravel");
            case 14:
                return block("gold_ore");
            case 15:
                return block("iron_ore");
            case 16:
                return block("coal_ore");
            case 17:
                return log(WOODS[Math.min(data & 3, 3)]);
            case 18:
                return leaves(WOODS[Math.min(data & 3, 3)]);
            case 19:
                return block("sponge");
            case 20:
                return block("glass");
            case 21:
                return block("lapis_ore");
            case 22:
                return block("lapis_block");
            case 23:
                return block("dispenser");
            case 24:
                return block(switch (data & 3) {
                    case 1 -> "chiseled_sandstone";
                    case 2 -> "cut_sandstone";
                    default -> "sandstone";
                });
            case 25:
                return block("note_block");
            case 27:
                return block("rail");
            case 28:
                return block("detector_rail");
            case 29:
                return block("sticky_piston");
            case 30:
                return block("cobweb");
            case 31:
                return block("grass");
            case 32:
                return block("dead_bush");
            case 33:
                return block("piston");
            case 35:
                return block(COLORS[data & 15] + "_wool");
            case 37:
                return block("dandelion");
            case 38:
                return block("poppy");
            case 39:
                return block("brown_mushroom");
            case 40:
                return block("red_mushroom");
            case 41:
                return block("gold_block");
            case 42:
                return block("iron_block");
            case 43:
                return block("smooth_stone");
            case 44:
                return slab(switch (data & 7) {
                    case 1 -> block("sandstone");
                    case 2 -> block("oak_planks");
                    case 3 -> block("cobblestone");
                    case 4 -> block("bricks");
                    case 5 -> block("stone_bricks");
                    case 6 -> block("nether_bricks");
                    case 7 -> block("quartz_block");
                    default -> block("smooth_stone");
                }, data);
            case 45:
                return block("bricks");
            case 46:
                return block("tnt");
            case 47:
                return block("bookshelf");
            case 48:
                return block("mossy_cobblestone");
            case 49:
                return block("obsidian");
            case 50:
                return block("torch");
            case 51:
                return block("fire");
            case 52:
                return block("spawner");
            case 53:
                return stairs("oak_stairs", data);
            case 54:
                return block("chest");
            case 55:
                return block("redstone_wire");
            case 56:
                return block("diamond_ore");
            case 57:
                return block("diamond_block");
            case 58:
                return block("crafting_table");
            case 59:
                return block("wheat");
            case 60:
                return block("farmland");
            case 61:
            case 62:
                return block("furnace");
            case 63:
            case 68:
                return block("oak_sign");
            case 64:
                return block("oak_door");
            case 65:
                return block("ladder");
            case 66:
                return block("rail");
            case 67:
                return stairs("stone_stairs", data);
            case 69:
                return block("lever");
            case 70:
                return block("stone_pressure_plate");
            case 71:
                return block("iron_door");
            case 72:
                return block("oak_pressure_plate");
            case 73:
            case 74:
                return block("redstone_ore");
            case 75:
            case 76:
                return block("redstone_torch");
            case 77:
                return block("stone_button");
            case 78:
                return block("snow");
            case 79:
                return block("ice");
            case 80:
                return block("snow_block");
            case 81:
                return block("cactus");
            case 82:
                return block("clay");
            case 83:
                return block("sugar_cane");
            case 84:
                return block("jukebox");
            case 85:
                return block("oak_fence");
            case 86:
                return block("pumpkin");
            case 87:
                return block("netherrack");
            case 88:
                return block("soul_sand");
            case 89:
                return block("glowstone");
            case 90:
                return block("nether_portal");
            case 91:
                return block("jack_o_lantern");
            case 92:
                return block("cake");
            case 93:
            case 94:
                return block("repeater");
            case 95:
                return block(COLORS[data & 15] + "_stained_glass");
            case 96:
                return block("oak_trapdoor");
            case 97:
                return block("infested_stone");
            case 98:
                return block(switch (data & 3) {
                    case 1 -> "mossy_stone_bricks";
                    case 2 -> "cracked_stone_bricks";
                    case 3 -> "chiseled_stone_bricks";
                    default -> "stone_bricks";
                });
            case 99:
                return block("brown_mushroom_block");
            case 100:
                return block("red_mushroom_block");
            case 101:
                return block("iron_bars");
            case 102:
                return block("glass_pane");
            case 103:
                return block("melon");
            case 106:
                return block("vine");
            case 107:
                return block("oak_fence_gate");
            case 108:
                return stairs("brick_stairs", data);
            case 109:
                return stairs("stone_brick_stairs", data);
            case 110:
                return block("mycelium");
            case 111:
                return block("lily_pad");
            case 112:
                return block("nether_bricks");
            case 113:
                return block("nether_brick_fence");
            case 114:
                return stairs("nether_brick_stairs", data);
            case 115:
                return block("nether_wart");
            case 116:
                return block("enchanting_table");
            case 117:
                return block("brewing_stand");
            case 118:
                return block("cauldron");
            case 119:
                return block("end_portal");
            case 120:
                return block("end_portal_frame");
            case 121:
                return block("end_stone");
            case 122:
                return block("dragon_egg");
            case 123:
            case 124:
                return block("redstone_lamp");
            case 125:
                return block("oak_planks");
            case 126:
                return slab(block(WOODS[Math.min(data & 7, 5)] + "_planks"), data);
            case 127:
                return block("cocoa");
            case 128:
                return stairs("sandstone_stairs", data);
            case 129:
                return block("emerald_ore");
            case 130:
                return block("ender_chest");
            case 131:
                return block("tripwire_hook");
            case 132:
                return block("tripwire");
            case 133:
                return block("emerald_block");
            case 134:
                return stairs("spruce_stairs", data);
            case 135:
                return stairs("birch_stairs", data);
            case 136:
                return stairs("jungle_stairs", data);
            case 137:
                return block("command_block");
            case 138:
                return block("beacon");
            case 139:
                return block("cobblestone_wall");
            case 140:
                return block("flower_pot");
            case 141:
                return block("carrots");
            case 142:
                return block("potatoes");
            case 143:
                return block("oak_button");
            case 144:
                return block("skeleton_skull");
            case 145:
                return block("anvil");
            case 146:
                return block("trapped_chest");
            case 147:
                return block("light_weighted_pressure_plate");
            case 148:
                return block("heavy_weighted_pressure_plate");
            case 149:
            case 150:
                return block("comparator");
            case 151:
            case 178:
                return block("daylight_detector");
            case 152:
                return block("redstone_block");
            case 153:
                return block("nether_quartz_ore");
            case 154:
                return block("hopper");
            case 155:
                return block(switch (data & 3) {
                    case 1 -> "chiseled_quartz_block";
                    case 2 -> "quartz_pillar";
                    default -> "quartz_block";
                });
            case 156:
                return stairs("quartz_stairs", data);
            case 157:
                return block("activator_rail");
            case 158:
                return block("dropper");
            case 159:
                return block(COLORS[data & 15] + "_terracotta");
            case 160:
                return block(COLORS[data & 15] + "_stained_glass_pane");
            case 161:
                return leaves((data & 1) == 0 ? "acacia" : "dark_oak");
            case 162:
                return log((data & 1) == 0 ? "acacia" : "dark_oak");
            case 163:
                return stairs("acacia_stairs", data);
            case 164:
                return stairs("dark_oak_stairs", data);
            case 165:
                return block("slime_block");
            case 166:
                return block("barrier");
            case 167:
                return block("iron_trapdoor");
            case 168:
                return block(switch (data & 3) {
                    case 1 -> "prismarine_bricks";
                    case 2 -> "dark_prismarine";
                    default -> "prismarine";
                });
            case 169:
                return block("sea_lantern");
            case 170:
                return block("hay_block");
            case 171:
                return block(COLORS[data & 15] + "_carpet");
            case 172:
                return block("terracotta");
            case 173:
                return block("coal_block");
            case 174:
                return block("packed_ice");
            case 175:
                return block("sunflower");
            case 176:
                return block(COLORS[data & 15] + "_banner");
            case 177:
                return block(COLORS[data & 15] + "_wall_banner");
            case 179:
                return block(switch (data & 3) {
                    case 1 -> "chiseled_red_sandstone";
                    case 2 -> "cut_red_sandstone";
                    default -> "red_sandstone";
                });
            case 180:
                return stairs("red_sandstone_stairs", data);
            case 181:
                return block("smooth_red_sandstone");
            case 182:
                return slab(block("red_sandstone"), data);
            case 183:
                return block("spruce_fence_gate");
            case 184:
                return block("birch_fence_gate");
            case 185:
                return block("jungle_fence_gate");
            case 186:
                return block("dark_oak_fence_gate");
            case 187:
                return block("acacia_fence_gate");
            case 188:
                return block("spruce_fence");
            case 189:
                return block("birch_fence");
            case 190:
                return block("jungle_fence");
            case 191:
                return block("dark_oak_fence");
            case 192:
                return block("acacia_fence");
            case 193:
                return block("spruce_door");
            case 194:
                return block("birch_door");
            case 195:
                return block("jungle_door");
            case 196:
                return block("acacia_door");
            case 197:
                return block("dark_oak_door");
            case 198:
                return block("end_rod");
            case 199:
                return block("chorus_plant");
            case 200:
                return block("chorus_flower");
            case 201:
                return block("purpur_block");
            case 202:
                return block("purpur_pillar");
            case 203:
                return stairs("purpur_stairs", data);
            case 204:
                return slab(block("purpur_block"), data);
            case 205:
                return block("end_stone_bricks");
            case 206:
                return block("end_stone_bricks");
            case 208:
                return block("dirt_path");
            case 209:
                return block("end_gateway");
            case 210:
                return block("repeating_command_block");
            case 211:
                return block("chain_command_block");
            case 212:
                return block("frosted_ice");
            case 213:
                return block("magma_block");
            case 214:
                return block("nether_wart_block");
            case 215:
                return block("red_nether_bricks");
            case 216:
                return block("bone_block");
            case 217:
                return block("structure_void");
            case 218:
                return block("observer");
            case 251:
                return block(COLORS[data & 15] + "_concrete");
            case 252:
                return block(COLORS[data & 15] + "_concrete_powder");
            case 255:
                return block("structure_block");
            default:
                if (id >= 219 && id <= 234) {
                    return block(COLORS[(id - 219) & 15] + "_shulker_box");
                }
                if (id >= 235 && id <= 250) {
                    return block(COLORS[(id - 235) & 15] + "_glazed_terracotta");
                }
                if (UNKNOWN.add(id)) {
                    LOGGER.debug("[ruin] unmapped legacy block id {}, fallback to stone", id);
                }
                return Blocks.STONE.defaultBlockState();
        }
    }

    private static BlockState block(String name) {
        ResourceLocation location = ResourceLocation.tryParse("minecraft:" + name);
        if (location == null) {
            return Blocks.AIR.defaultBlockState();
        }
        Block block = BuiltInRegistries.BLOCK.get(location);
        return block == null ? Blocks.AIR.defaultBlockState() : block.defaultBlockState();
    }

    private static BlockState log(String wood) {
        BlockState state = block(wood + "_log");
        return state.hasProperty(BlockStateProperties.AXIS)
                ? state.setValue(BlockStateProperties.AXIS, Direction.Axis.Y) : state;
    }

    private static BlockState leaves(String wood) {
        BlockState state = block(wood + "_leaves");
        return state.hasProperty(LeavesBlock.PERSISTENT) ? state.setValue(LeavesBlock.PERSISTENT, true) : state;
    }

    private static BlockState slab(BlockState base, int data) {
        if (!base.hasProperty(BlockStateProperties.SLAB_TYPE)) {
            return base;
        }
        return base.setValue(BlockStateProperties.SLAB_TYPE,
                (data & 8) != 0 ? SlabType.TOP : SlabType.BOTTOM);
    }

    private static BlockState stairs(String name, int data) {
        BlockState state = block(name);
        if (state.hasProperty(BlockStateProperties.HORIZONTAL_FACING)) {
            state = state.setValue(BlockStateProperties.HORIZONTAL_FACING, switch (data & 3) {
                case 0 -> Direction.EAST;
                case 1 -> Direction.WEST;
                case 2 -> Direction.SOUTH;
                default -> Direction.NORTH;
            });
        }
        if (state.hasProperty(BlockStateProperties.HALF)) {
            state = state.setValue(BlockStateProperties.HALF, (data & 4) != 0 ? Half.TOP : Half.BOTTOM);
        }
        return state;
    }
}
