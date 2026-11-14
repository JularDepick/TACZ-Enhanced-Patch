package com.taczenhancedpatch.world.schematic;

import com.mojang.logging.LogUtils;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.slf4j.Logger;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 蓝图读取器。
 * 两种格式都认: Sponge Schematic 的 .schem, 以及 MCEdit 时代的 Alpha 格式 .schematic;
 * 按根标签结构自动判定, 调用方不需要预先知道格式。
 * Sponge 格式的方块由调色板字符串与变长整数数组描述, 索引顺序为 y 外层, z 次之, x 最内;
 * 版本 1 与 2 的数据在根标签上, 版本 3 收在 Blocks 子标签内。
 * Alpha 格式的方块由 Blocks 与 Data 两个字节数组描述, 旧版数字 ID 由 LegacyBlocks 映射。
 * 实体, 方块实体与生物群系数据一律丢弃, 宝箱由补丁自己放置。
 */
public final class SchematicReader {
    private static final Logger LOGGER = LogUtils.getLogger();
    /* 调色板告警的全局计数, 用于限流 */
    private static final java.util.concurrent.atomic.AtomicInteger PALETTE_WARNINGS =
            new java.util.concurrent.atomic.AtomicInteger();
    /* 单座蓝图的方块上限, 防止异常文件把服务端内存打满 */
    private static final int MAX_VOLUME = 4_000_000;
    private static final int MAX_VERSION = 3;
    private static final HolderGetter<Block> BLOCKS = BuiltInRegistries.BLOCK.asLookup();

    private SchematicReader() {
    }

    public static RuinSchematic read(String id, InputStream stream) throws IOException {
        CompoundTag root = NbtIo.readCompressed(stream);
        if (root.contains("Schematic", Tag.TAG_COMPOUND) || root.contains("Version", Tag.TAG_INT)) {
            return readSponge(id, root);
        }
        if (root.contains("Blocks", Tag.TAG_BYTE_ARRAY) && root.contains("Data", Tag.TAG_BYTE_ARRAY)) {
            return readAlpha(id, root);
        }
        throw new IOException("unknown blueprint format: " + id);
    }

    /* Sponge Schematic: 调色板为方块状态字符串到编号的映射, 方块数据为变长整数数组 */
    private static RuinSchematic readSponge(String id, CompoundTag root) throws IOException {
        CompoundTag schematic = root.contains("Schematic", Tag.TAG_COMPOUND) ? root.getCompound("Schematic") : root;
        int version = schematic.getInt("Version");
        if (version < 1 || version > MAX_VERSION) {
            throw new IOException("unsupported schematic version " + version + ": " + id);
        }
        int width = dimension(schematic, "Width");
        int height = dimension(schematic, "Height");
        int depth = dimension(schematic, "Length");
        int volume = volume(id, width, height, depth);
        CompoundTag container = version >= 3 ? schematic.getCompound("Blocks") : schematic;
        Map<Integer, BlockState> palette = palette(container.getCompound("Palette"));
        byte[] data = container.getByteArray(version >= 3 ? "Data" : "BlockData");
        if (data.length == 0) {
            throw new IOException("empty block data: " + id);
        }

        short[] blocks = new short[volume];
        List<BlockState> states = new ArrayList<>();
        states.add(Blocks.AIR.defaultBlockState());
        Map<BlockState, Short> slots = new HashMap<>();
        int[] cursor = {0};
        for (int index = 0; index < volume; index++) {
            int paletteId = varInt(data, cursor);
            BlockState state = palette.get(paletteId);
            if (state == null) {
                /* 缺调色板条目时最多记五条, 避免大文件刷满日志 */
        if (PALETTE_WARNINGS.incrementAndGet() <= 5) {
            LOGGER.warn("[blueprint] palette id {} missing in {}, treated as air", paletteId, id);
        }
                continue;
            }
            if (state.isAir()) {
                continue;
            }
            Short slot = slots.get(state);
            if (slot == null) {
                if (states.size() > Short.MAX_VALUE) {
                    throw new IOException("too many distinct blocks in palette: " + id);
                }
                slot = (short) states.size();
                states.add(state);
                slots.put(state, slot);
            }
            blocks[index] = slot;
        }
        return new RuinSchematic(id, width, height, depth, blocks, states.toArray(new BlockState[0]));
    }

    /* Alpha: 旧版数字 ID 与数据值, 只用于外部目录里的旧文件 */
    private static RuinSchematic readAlpha(String id, CompoundTag root) throws IOException {
        int width = dimension(root, "Width");
        int height = dimension(root, "Height");
        int depth = dimension(root, "Length");
        int volume = volume(id, width, height, depth);
        byte[] legacyIds = root.getByteArray("Blocks");
        byte[] meta = root.getByteArray("Data");
        byte[] addIds = root.contains("AddBlocks", Tag.TAG_BYTE_ARRAY) ? root.getByteArray("AddBlocks") : new byte[0];
        if (legacyIds.length < volume || meta.length < volume) {
            throw new IOException("block array shorter than size: " + id);
        }

        short[] blocks = new short[volume];
        List<BlockState> states = new ArrayList<>();
        states.add(Blocks.AIR.defaultBlockState());
        Map<Integer, Short> slots = new HashMap<>();
        for (int index = 0; index < volume; index++) {
            int legacyId = legacyIds[index] & 0xFF;
            if (addIds.length > 0 && (index >> 1) < addIds.length) {
                int high = addIds[index >> 1] & 0xFF;
                legacyId |= (index & 1) == 0 ? (high & 0x0F) << 8 : (high & 0xF0) << 4;
            }
            int data = meta[index] & 0xFF;
            BlockState state = LegacyBlocks.state(legacyId, data);
            if (state == null || state.isAir()) {
                continue;
            }
            int key = (legacyId << 8) | data;
            Short slot = slots.get(key);
            if (slot == null) {
                if (states.size() > Short.MAX_VALUE) {
                    throw new IOException("too many distinct blocks in palette: " + id);
                }
                slot = (short) states.size();
                states.add(state);
                slots.put(key, slot);
            }
            blocks[index] = slot;
        }
        return new RuinSchematic(id, width, height, depth, blocks, states.toArray(new BlockState[0]));
    }

    /* 调色板: 方块状态字符串到编号, 字符串形如 minecraft:oak_stairs[facing=north,half=bottom] */
    private static Map<Integer, BlockState> palette(CompoundTag paletteTag) throws IOException {
        Map<Integer, BlockState> palette = new HashMap<>();
        for (String text : paletteTag.getAllKeys()) {
            if (!paletteTag.contains(text, Tag.TAG_INT)) {
                throw new IOException("palette entry is not an int: " + text);
            }
            palette.put(paletteTag.getInt(text), state(text));
        }
        if (palette.isEmpty()) {
            throw new IOException("empty palette");
        }
        return palette;
    }

    private static BlockState state(String text) {
        int bracket = text.indexOf('[');
        String name = bracket < 0 ? text : text.substring(0, bracket);
        if (ResourceLocation.tryParse(name) == null) {
            LOGGER.warn("[blueprint] invalid block name in palette: {}", text);
            return Blocks.AIR.defaultBlockState();
        }
        CompoundTag tag = new CompoundTag();
        tag.putString("Name", name);
        if (bracket >= 0 && text.endsWith("]")) {
            CompoundTag properties = new CompoundTag();
            for (String pair : text.substring(bracket + 1, text.length() - 1).split(",")) {
                int equals = pair.indexOf('=');
                if (equals <= 0) {
                    continue;
                }
                properties.putString(pair.substring(0, equals).trim(), pair.substring(equals + 1).trim());
            }
            if (!properties.isEmpty()) {
                tag.put("Properties", properties);
            }
        }
        return NbtUtils.readBlockState(BLOCKS, tag);
    }

    private static int volume(String id, int width, int height, int depth) throws IOException {
        if (width <= 0 || height <= 0 || depth <= 0) {
            throw new IOException("bad size " + width + "x" + height + "x" + depth + ": " + id);
        }
        /* 三个维度相乘可能溢出 int, 先用 long 算完再收紧到上限, 避免绕过体积限制 */
        long volume = (long) width * (long) height * (long) depth;
        if (volume <= 0L || volume > MAX_VOLUME) {
            throw new IOException("too large " + volume + " blocks: " + id);
        }
        return (int) volume;
    }

    /* 尺寸在标准里是短整型, 少数导出工具写整型, 两种都接受 */
    private static int dimension(CompoundTag tag, String key) throws IOException {
        if (tag.contains(key, Tag.TAG_SHORT)) {
            return tag.getShort(key) & 0xFFFF;
        }
        if (tag.contains(key, Tag.TAG_INT)) {
            return tag.getInt(key);
        }
        throw new IOException("missing " + key + " in schematic");
    }

    /* 方块编号为 LEB128 变长整数, 与调色板编号一一对应 */
    private static int varInt(byte[] data, int[] cursor) throws IOException {
        int value = 0;
        int shift = 0;
        while (true) {
            if (cursor[0] >= data.length) {
                throw new IOException("truncated block data");
            }
            byte current = data[cursor[0]++];
            value |= (current & 0x7F) << shift;
            if ((current & 0x80) == 0) {
                return value;
            }
            shift += 7;
            if (shift > 28) {
                throw new IOException("malformed varint in block data");
            }
        }
    }
}
