package com.taczenhancedpatch.world.schematic;

import com.taczenhancedpatch.TACZEnhancedPatch;
import com.taczenhancedpatch.config.ConfigEvents;
import com.taczenhancedpatch.config.ServerConfig;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.server.ServerLifecycleHooks;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Stream;

/**
 * 遗迹建筑目录。
 * 建筑有两个来源: 模组内置的 assets/tacz_enhanced_patch/ruins, 以及存档服务端配置目录下的
 * tacz_enhanced_patch_ruins, 两者在选择时平权。
 * 站点里保存的是完整名字, 外部来源带 external: 前缀, 因此运行期可以反查来源;
 * 内置名单取自模组文件系统, 文件名不受资源命名规则限制; 外部目录递归扫描并限制深度。
 * 解析在后台单线程进行, 服务端线程只取缓存, 避免百万方块级的解析卡住主线程。
 */
public final class SchematicCatalog {
    private static final String[] FOLDER = {"assets", TACZEnhancedPatch.MOD_ID, "ruins"};
    private static final String BUILTIN_PREFIX = "assets/" + TACZEnhancedPatch.MOD_ID + "/ruins/";
    /* 外部来源前缀, 与站点里保存的名字一起构成来源判定 */
    private static final String EXTERNAL_PREFIX = "external:";
    private static final String EXTERNAL_FOLDER = "tacz_enhanced_patch_ruins";
    private static final String SERVERCONFIG_FOLDER = "serverconfig";
    private static final String SUFFIX = ".schematic";
    /* 外部目录的递归深度上限, 避免误放深层目录时扫出巨量文件 */
    private static final int MAX_DEPTH = 4;
    private static final ConcurrentHashMap<String, RuinSchematic> CACHE = new ConcurrentHashMap<>();
    private static final Set<String> LOADING = ConcurrentHashMap.newKeySet();
    private static final Set<String> FAILED = ConcurrentHashMap.newKeySet();
    private static final ExecutorService PARSER = Executors.newSingleThreadExecutor(task -> {
        Thread thread = new Thread(task, "tacz-enhanced-patch-ruin-parser");
        thread.setDaemon(true);
        return thread;
    });
    private static volatile List<String> builtin;
    private static volatile List<String> external;

    private SchematicCatalog() {
    }

    /* 合并两个来源: 内置模型关闭时只取外部目录, 否则两者平权参与随机 */
    public static List<String> files() {
        List<String> outside = externalFiles();
        if (!ServerConfig.ruinsBuiltinModels()) {
            return outside;
        }
        List<String> inside = builtinFiles();
        if (outside.isEmpty()) {
            return inside;
        }
        List<String> combined = new ArrayList<>(inside.size() + outside.size());
        combined.addAll(inside);
        combined.addAll(outside);
        return List.copyOf(combined);
    }

    public static List<String> builtinFiles() {
        List<String> cached = builtin;
        if (cached != null) {
            return cached;
        }
        List<String> found = new ArrayList<>();
        try {
            var modFileInfo = ModList.get().getModFileById(TACZEnhancedPatch.MOD_ID);
            if (modFileInfo == null) {
                builtin = List.of();
                return builtin;
            }
            Path root = modFileInfo.getFile().findResource(FOLDER);
            if (!Files.isDirectory(root)) {
                builtin = List.of();
                return builtin;
            }
            try (Stream<Path> stream = Files.list(root)) {
                stream.map(path -> path.getFileName().toString())
                        .filter(name -> name.toLowerCase(Locale.ROOT).endsWith(SUFFIX))
                        .sorted()
                        .forEach(name -> found.add(BUILTIN_PREFIX + name));
            }
        } catch (IOException | RuntimeException exception) {
            TACZEnhancedPatch.LOGGER.error("[ruin] failed to list builtin schematics", exception);
        }
        builtin = List.copyOf(found);
        return builtin;
    }

    public static List<String> externalFiles() {
        List<String> cached = external;
        if (cached != null) {
            return cached;
        }
        List<String> found = new ArrayList<>();
        Path root = externalRoot();
        if (root != null && Files.isDirectory(root)) {
            try (Stream<Path> stream = Files.walk(root, MAX_DEPTH)) {
                stream.filter(Files::isRegularFile)
                        .filter(path -> path.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(SUFFIX))
                        .sorted()
                        .forEach(path -> found.add(EXTERNAL_PREFIX
                                + root.relativize(path).toString().replace('\\', '/')));
            } catch (IOException | RuntimeException exception) {
                TACZEnhancedPatch.LOGGER.error("[ruin] failed to list external schematics under {}", root, exception);
            }
        }
        external = List.copyOf(found);
        return external;
    }

    /* 外部目录: 存档服务端配置目录下的固定子目录, 不可配置 */
    public static Path externalRoot() {
        Path base = serverConfigDir();
        return base == null ? null : base.resolve(EXTERNAL_FOLDER);
    }

    /*
     * 服务端配置目录。
     * 优先取服务端配置文件的真实位置, 单人存档因此落在该存档的 serverconfig 下;
     * 配置尚未加载时退回存档根目录下的 serverconfig。
     */
    private static Path serverConfigDir() {
        ModConfig config = ConfigEvents.serverConfig();
        if (config != null) {
            Path path = config.getFullPath();
            if (path != null && path.getParent() != null) {
                return path.getParent();
            }
        }
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        return server == null ? null : server.getWorldPath(LevelResource.ROOT).resolve(SERVERCONFIG_FOLDER);
    }

    /* 随机选一座建筑, 已判定为不可用的文件不再参与 */
    public static Optional<String> pick(RandomSource random) {
        return pickOther(null, random);
    }

    /* 排除指定名字后再随机选一座, 用于替换不可用的文件 */
    public static Optional<String> pickOther(String exclude, RandomSource random) {
        List<String> all = new ArrayList<>();
        for (String name : files()) {
            if (name.equals(exclude) || FAILED.contains(name)) {
                continue;
            }
            all.add(name);
        }
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
            if (name.startsWith(EXTERNAL_PREFIX)) {
                return readExternal(name);
            }
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

    private static RuinSchematic readExternal(String name) throws IOException {
        Path root = externalRoot();
        if (root == null) {
            TACZEnhancedPatch.LOGGER.error("[ruin] external schematic dir unavailable: {}", name);
            return null;
        }
        Path path = root.resolve(name.substring(EXTERNAL_PREFIX.length()));
        if (!Files.isRegularFile(path)) {
            TACZEnhancedPatch.LOGGER.error("[ruin] external schematic not found: {}", path);
            return null;
        }
        try (InputStream stream = Files.newInputStream(path)) {
            return SchematicReader.read(name, stream);
        }
    }

    /*
     * 重新扫描外部目录。
     * 内置名单来自模组文件, 运行期不会变化, 因此只丢弃外部来源的列表与解析结果,
     * 让增删改过的外部文件重新解析; 已解析的内置模型继续复用。
     */
    public static void refresh() {
        external = null;
        CACHE.keySet().removeIf(name -> name.startsWith(EXTERNAL_PREFIX));
        FAILED.removeIf(name -> name.startsWith(EXTERNAL_PREFIX));
    }

    /* 资源重载后丢弃全部列表与缓存, 下次生成时重新扫描 */
    public static void invalidate() {
        builtin = null;
        external = null;
        CACHE.clear();
        FAILED.clear();
    }
}
