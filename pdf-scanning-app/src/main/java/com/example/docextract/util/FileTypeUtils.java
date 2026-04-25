package com.example.docextract.util;

import org.springframework.web.multipart.MultipartFile;

import java.util.Locale;
import java.util.Optional;

public final class FileTypeUtils {

    private FileTypeUtils() {
    }

    public static String extension(String filename) {
        if (filename == null || !filename.contains(".")) {
            return "";
        }
        return filename.substring(filename.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT);
    }

    public static boolean isPdf(String extension, String contentType) {
        return "pdf".equalsIgnoreCase(extension) || "application/pdf".equalsIgnoreCase(contentType);
    }

    public static boolean isImage(String extension, String contentType) {
        return switch (extension.toLowerCase(Locale.ROOT)) {
            case "png", "jpg", "jpeg", "tif", "tiff", "bmp", "gif" -> true;
            default -> Optional.ofNullable(contentType).orElse("").toLowerCase(Locale.ROOT).startsWith("image/");
        };
    }

    public static String safeFilename(MultipartFile file) {
        return Optional.ofNullable(file.getOriginalFilename()).orElse("uploaded-file");
    }
}
