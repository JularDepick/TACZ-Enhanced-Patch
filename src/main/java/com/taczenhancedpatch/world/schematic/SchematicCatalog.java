package com.taczenhancedpatch.world.schematic;

import com.taczenhancedpatch.TACZEnhancedPatch;
import net.minecraft.util.RandomSource;
import net.minecraftforge.fml.ModList;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

/**
 * 遗迹建筑目录。
 * 直接扫描模组自身文件里的 assets/tacz_enhanced_patch/ruins 目录, 因此文件名可以随意使用,
 * 不受资源路径命名规则限制; 解析结果按文件名缓存, 同一座建筑只读一次。
 */
public final class SchematicCatalog {
    private static final String[] FOLDER = {"assets", TACZEnhancedPatch.MOD_ID, "ruins"};
    private static final String PREFIX = "assets/" + TACZEnhancedPatch.MOD_ID + "/ruins/";
    private static final String SUFFIX = ".schematic";
    private static final ConcurrentHashMap<String, RuinSchematic> CACHE = new ConcurrentHashMap<>();
    private static volatile List<String> files;

    private SchematicCatalog() {
    }

    public static List<String> files() {
        List<String> cached = files;
        if (cached != null) {
            return cached;
        }
        List<String> found = new ArrayList<>();
        try {
            var modFileInfo = ModList.get().getModFileById(TACZEnhancedPatch.MOD_ID);
            if (modFileInfo == null) {
                files = List.of();
                return files;
            }
            Path root = modFileInfo.getFile().findResource(FOLDER);
            if (!Files.isDirectory(root)) {
                files = List.of();
                return files;
            }
            try (Stream<Path> stream = Files.list(root)) {
                stream.map(path -> path.getFileName().toString())
                        .filter(name -> name.endsWith(SUFFIX))
                        .sorted()
                        .forEach(name -> found.add(PREFIX + name));
            }
        } catch (IOException | RuntimeException exception) {
            TACZEnhancedPatch.LOGGER.error("[ruin] failed to list schematics", exception);
        }
        files = List.copyOf(found);
        return files;
    }

    public static Optional<String> pick(RandomSource random) {
        List<String> all = files();
        if (all.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(all.get(random.nextInt(all.size())));
    }

    /* 读取并缓存一座建筑, 失败时返回空并记错误日志 */
    public static RuinSchematic load(String name) {
        RuinSchematic cached = CACHE.get(name);
        if (cached != null) {
            return cached;
        }
        try {
            var modFileInfo = ModList.get().getModFileById(TACZEnhancedPatch.MOD_ID);
            if (modFileInfo == null) {
                return null;
            }
            Path path = modFileInfo.getFile().findResource(name.split("/"));
            if (!Files.isRegularFile(path)) {
                TACZEnhancedPatch.LOGGER.error("[ruin] schematic not found: {}", name);
                return null;
            }
            try (InputStream stream = Files.newInputStream(path)) {
                RuinSchematic schematic = SchematicReader.read(name, stream);
                CACHE.put(name, schematic);
                return schematic;
            }
        } catch (IOException | RuntimeException exception) {
            TACZEnhancedPatch.LOGGER.error("[ruin] failed to read schematic {}", name, exception);
            return null;
        }
    }

    /* 资源重载后丢弃列表与缓存, 下次生成时重新扫描 */
    public static void invalidate() {
        files = null;
        CACHE.clear();
    }
}
