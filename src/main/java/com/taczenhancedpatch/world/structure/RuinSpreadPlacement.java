package com.taczenhancedpatch.world.structure;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.taczenhancedpatch.config.ServerConfig;
import net.minecraft.core.Vec3i;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.ChunkGeneratorStructureState;
import net.minecraft.world.level.levelgen.LegacyRandomSource;
import net.minecraft.world.level.levelgen.WorldgenRandom;
import net.minecraft.world.level.levelgen.structure.placement.StructurePlacement;
import net.minecraft.world.level.levelgen.structure.placement.StructurePlacementType;

import java.util.Optional;

/*
 * 遗迹选址。
 * 与原版 random_spread 同一套数学: 世界按 spacing 个区块划格, 每格内在收窄后的窗口里随机取一个候选块;
 * 区别只在参数由配置换算, 最小直线距离因此仍可调, 并且完全由种子决定, 生成期不读写存档。
 * 相邻格的两个候选在单轴上至少相隔 separation 加一个区块, 这就是保底距离的来源。
 */
public class RuinSpreadPlacement extends StructurePlacement {
    public static final Codec<RuinSpreadPlacement> CODEC = RecordCodecBuilder.create(instance ->
            placementCodec(instance).apply(instance, RuinSpreadPlacement::new));
    /* 抖动窗口, 让相邻遗迹不再处处等距 */
    public static final int JITTER_CHUNKS = 4;

    public RuinSpreadPlacement(Vec3i locateOffset, FrequencyReductionMethod method, float frequency, int salt,
                               Optional<ExclusionZone> zone) {
        super(locateOffset, method, frequency, salt, zone);
    }

    /* 保底距离是 separation 加一 个区块, 因此按配置换算时要先减一 */
    public static int separationChunks(int minDistance) {
        return Math.max(0, (int) Math.ceil(minDistance / 16.0D) - 1);
    }

    public static int spacingChunks(int minDistance) {
        return separationChunks(minDistance) + JITTER_CHUNKS;
    }

    /* 候选块: 与原版一样按格算, 格内偏移落在收窄后的窗口里 */
    public static ChunkPos candidateChunk(long seed, int salt, int minDistance, int chunkX, int chunkZ) {
        int spacing = spacingChunks(minDistance);
        int window = Math.max(1, spacing - separationChunks(minDistance));
        int cellX = Math.floorDiv(chunkX, spacing);
        int cellZ = Math.floorDiv(chunkZ, spacing);
        WorldgenRandom random = new WorldgenRandom(new LegacyRandomSource(0L));
        random.setLargeFeatureWithSalt(seed, cellX, cellZ, salt);
        int offsetX = random.nextInt(window);
        int offsetZ = random.nextInt(window);
        return new ChunkPos(cellX * spacing + offsetX, cellZ * spacing + offsetZ);
    }

    /* 供指南针按同一套数学枚举候选, 不需要区块已经生成 */
    public ChunkPos candidate(long seed, int minDistance, int chunkX, int chunkZ) {
        return candidateChunk(seed, this.salt(), minDistance, chunkX, chunkZ);
    }

    @Override
    protected boolean isPlacementChunk(ChunkGeneratorStructureState state, int chunkX, int chunkZ) {
        ChunkPos candidate = candidateChunk(state.getLevelSeed(), this.salt(),
                ServerConfig.ruinMinDistance(), chunkX, chunkZ);
        return candidate.x == chunkX && candidate.z == chunkZ;
    }

    @Override
    public StructurePlacementType<?> type() {
        return RuinStructures.RUIN_SPREAD.get();
    }
}
