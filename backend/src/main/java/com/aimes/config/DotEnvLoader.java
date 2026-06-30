package com.aimes.config;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

/**
 * 启动前从项目根目录 .env 加载变量到 System properties，供 Spring 读取。
 * 不覆盖已存在的系统环境变量或 JVM 参数。
 */
public final class DotEnvLoader {

    private DotEnvLoader() {
    }

    public static void load() {
        for (Path path : candidatePaths()) {
            if (!Files.isRegularFile(path)) {
                continue;
            }
            try {
                int count = loadFile(path);
                if (count > 0) {
                    System.out.println("[DotEnv] loaded " + count + " entries from " + path.toAbsolutePath());
                }
                return;
            } catch (IOException ex) {
                System.err.println("[DotEnv] failed to read " + path + ": " + ex.getMessage());
            }
        }
    }

    private static List<Path> candidatePaths() {
        Path cwd = Paths.get(System.getProperty("user.dir")).toAbsolutePath().normalize();
        return List.of(
                cwd.resolve(".env"),
                cwd.getParent() != null ? cwd.getParent().resolve(".env") : cwd.resolve(".env")
        );
    }

    private static int loadFile(Path file) throws IOException {
        int count = 0;
        for (String rawLine : Files.readAllLines(file)) {
            String line = rawLine.trim();
            if (line.isEmpty() || line.startsWith("#")) {
                continue;
            }
            int eq = line.indexOf('=');
            if (eq <= 0) {
                continue;
            }
            String key = line.substring(0, eq).trim();
            String value = line.substring(eq + 1).trim();
            if (key.isEmpty() || value.isEmpty()) {
                continue;
            }
            if (System.getenv(key) != null || System.getProperty(key) != null) {
                continue;
            }
            System.setProperty(key, value);
            count++;
        }
        return count;
    }
}
