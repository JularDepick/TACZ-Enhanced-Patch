package com.taczenhancedpatch.world.schematic;

import com.mojang.logging.LogUtils;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.Tag;
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
 * .schematic 读取器。
 * 只支持 MCEdit 时代的 Alpha 格式: gzip 压缩的 NBT, 方块以 Blocks 与 Data 两个字节数组
 * 按 y * width * length + z * width + x 排列, AddBlocks 可选, 用于补齐高位方块 ID。
 * 实体与方块实体数据一律丢弃, 遗迹自带的宝箱与刷怪笼由补丁自己放置。
 */
public final class SchematicReader {
    private static final Logger LOGGER = LogUtils.getLogger();
    /* 单座建筑的方块上限, 防止异常文件把服务端内存打满 */
    private static final int MAX_VOLUME = 4_000_000;

    private SchematicReader() {
    }

    public static RuinSchematic read(String id, InputStream stream) throws IOException {
        CompoundTag root = NbtIo.readCompressed(stream);
        if (!root.contains("Blocks", Tag.TAG_BYTE_ARRAY) || !root.contains("Data", Tag.TAG_BYTE_ARRAY)) {
            throw new IOException("not an Alpha schematic: " + id);
        }
        int width = root.getInt("Width");
        int height = root.getInt("Height");
        int depth = root.getInt("Length");
        if (width <= 0 || height <= 0 || depth <= 0) {
            throw new IOException("bad size " + width + "x" + height + "x" + depth + ": " + id);
        }
        int volume = width * height * depth;
        if (volume > MAX_VOLUME) {
            throw new IOException("too large " + volume + " blocks: " + id);
        }
        byte[] legacyIds = root.getByteArray("Blocks");
        byte[] meta = root.getByteArray("Data");
        byte[] addIds = root.contains("AddBlocks", Tag.TAG_BYTE_ARRAY) ? root.getByteArray("AddBlocks") : new byte[0];
        if (legacyIds.length < volume || meta.length < volume) {
            throw new IOException("block array shorter than size: " + id);
        }

        List<BlockState> palette = new ArrayList<>();
        palette.add(Blocks.AIR.defaultBlockState());
        Map<Integer, Short> slots = new HashMap<>();
        short[] blocks = new short[volume];
        int solid = 0;
        for (int index = 0; index < volume; index++) {
            int legacyId = legacyIds[index] & 0xFF;
            if (addIds.length > 0 && (index >> 1) < addIds.length) {
                int high = addIds[index >> 1] & 0xFF;
                legacyId |= (index & 1) == 0 ? (high & 0x0F) << 8 : (high & 0xF0) << 4;
            }
            int data = meta[index] & 0xFF;
            BlockState state = LegacyBlocks.state(legacyId, data);
            if (state.isAir()) {
                continue;
            }
            int key = (legacyId << 8) | data;
            Short slot = slots.get(key);
            if (slot == null) {
                slot = (short) palette.size();
                palette.add(state);
                slots.put(key, slot);
            }
            blocks[index] = slot;
            solid++;
        }
        LOGGER.debug("[ruin] read schematic {} {}x{}x{}, {} solid blocks, {} palette entries",
                id, width, height, depth, solid, palette.size() - 1);
        return new RuinSchematic(id, width, height, depth, blocks, palette.toArray(new BlockState[0]), solid);
    }
}
