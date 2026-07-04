package com.aimes.service;

import com.aimes.common.BusinessException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.net.MalformedURLException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Set;
import java.util.UUID;

@Slf4j
@Service
public class FileStorageService {

    private static final Set<String> ALLOWED_EXTENSIONS = Set.of(
            "pdf", "png", "jpg", "jpeg", "gif", "webp", "mp4", "webm", "doc", "docx", "txt"
    );
    private static final long MAX_SOP_SIZE = 20 * 1024 * 1024L;

    @Value("${aimes.upload-dir:uploads}")
    private String uploadDir;

    public String storeSop(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException("请选择要上传的文件");
        }
        if (file.getSize() > MAX_SOP_SIZE) {
            throw new BusinessException("文件大小不能超过 20MB");
        }
        String originalName = StringUtils.cleanPath(file.getOriginalFilename() == null ? "file" : file.getOriginalFilename());
        String ext = extension(originalName);
        if (!ALLOWED_EXTENSIONS.contains(ext)) {
            throw new BusinessException("不支持的文件类型：" + ext);
        }
        try {
            Path sopDir = resolveUploadRoot().resolve("sop");
            Files.createDirectories(sopDir);
            String storedName = UUID.randomUUID() + "." + ext;
            Path target = sopDir.resolve(storedName);
            Files.copy(file.getInputStream(), target, StandardCopyOption.REPLACE_EXISTING);
            return "sop/" + storedName;
        } catch (IOException ex) {
            log.error("SOP upload failed", ex);
            throw new BusinessException("文件上传失败");
        }
    }

    public Resource loadAsResource(String relativePath) {
        try {
            Path file = resolveUploadRoot().resolve(relativePath).normalize();
            if (!file.startsWith(resolveUploadRoot())) {
                throw new BusinessException("非法文件路径");
            }
            Resource resource = new UrlResource(file.toUri());
            if (!resource.exists() || !resource.isReadable()) {
                throw new BusinessException("文件不存在");
            }
            return resource;
        } catch (MalformedURLException ex) {
            throw new BusinessException("文件路径无效");
        }
    }

    public String copySop(String sourceRelativePath) {
        if (!StringUtils.hasText(sourceRelativePath)) {
            throw new BusinessException("源文件路径无效");
        }
        try {
            Path source = resolveUploadRoot().resolve(sourceRelativePath).normalize();
            if (!source.startsWith(resolveUploadRoot()) || !Files.exists(source)) {
                throw new BusinessException("源文件不存在");
            }
            Path sopDir = resolveUploadRoot().resolve("sop");
            Files.createDirectories(sopDir);
            String originalName = source.getFileName().toString();
            String ext = extension(originalName);
            String storedName = UUID.randomUUID() + (ext.isEmpty() ? "" : "." + ext);
            Path target = sopDir.resolve(storedName);
            Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING);
            return "sop/" + storedName;
        } catch (IOException ex) {
            log.error("SOP copy failed", ex);
            throw new BusinessException("文件复制失败");
        }
    }

    public void delete(String relativePath) {
        if (!StringUtils.hasText(relativePath)) {
            return;
        }
        try {
            Path file = resolveUploadRoot().resolve(relativePath).normalize();
            if (file.startsWith(resolveUploadRoot())) {
                Files.deleteIfExists(file);
            }
        } catch (IOException ex) {
            log.warn("Failed to delete file: {}", relativePath, ex);
        }
    }

    public String detectFileType(String fileName) {
        String ext = extension(fileName);
        return switch (ext) {
            case "pdf" -> "pdf";
            case "png", "jpg", "jpeg", "gif", "webp" -> "image";
            case "mp4", "webm" -> "video";
            default -> "doc";
        };
    }

    private Path resolveUploadRoot() {
        return Paths.get(uploadDir).toAbsolutePath().normalize();
    }

    private String extension(String fileName) {
        int idx = fileName.lastIndexOf('.');
        if (idx < 0) {
            return "";
        }
        return fileName.substring(idx + 1).toLowerCase();
    }
}
