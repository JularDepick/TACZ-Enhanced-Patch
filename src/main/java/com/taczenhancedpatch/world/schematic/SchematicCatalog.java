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
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Stream;

/**
 * 遗迹建筑目录。
 * 直接扫描模组自身文件里的 assets/tacz_enhanced_patch/ruins 目录, 因此文件名可以随意使用;
 * 解析在后台单线程进行, 服务端线程只取缓存, 避免百万方块级的解析卡住主线程。
 */
public final class SchematicCatalog {
    private static final String[] FOLDER = {"assets", TACZEnhancedPatch.MOD_ID, "ruins"};
    private static final String PREFIX = "assets/" + TACZEnhancedPatch.MOD_ID + "/ruins/";
    private static final String SUFFIX = ".schematic";
    private static final ConcurrentHashMap<String, RuinSchematic> CACHE = new ConcurrentHashMap<>();
    private static final Set<String> LOADING = ConcurrentHashMap.newKeySet();
    private static final Set<String> FAILED = ConcurrentHashMap.newKeySet();
    private static final ExecutorService PARSER = Executors.newSingleThreadExecutor(task -> {
        Thread thread = new Thread(task, "tacz-enhanced-patch-ruin-parser");
        thread.setDaemon(true);
        return thread;
    });
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

    /* 已缓存则直接返回; 未缓存时丢给后台线程, 本次返回空, 由调用方下一 tick 再取 */
    public static RuinSchematic load(String name) {
        RuinSchematic cached = CACHE.get(name);
        if (cached != null) {
            return cached;
        }
        if (FAILED.contains(name) || !LOADING.add(name)) {
            return null;
        }
        PARSER.execute(() -> {
            try {
                RuinSchematic parsed = read(name);
                if (parsed != null) {
                    CACHE.put(name, parsed);
                } else {
                    FAILED.add(name);
                }
            } finally {
                LOADING.remove(name);
            }
        });
        return null;
    }

    /* 是否仍在后台解析, 调用方据此区分"再等等"和"这个文件坏了" */
    public static boolean isPending(String name) {
        return LOADING.contains(name);
    }

    private static RuinSchematic read(String name) {
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
                return SchematicReader.read(name, stream);
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
        FAILED.clear();
    }
}
