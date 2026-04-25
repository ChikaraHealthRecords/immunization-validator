package com.example.docextract.service;

import com.example.docextract.config.ApplicationProperties;
import com.example.docextract.exception.BadRequestException;
import com.example.docextract.util.FileTypeUtils;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.UUID;

@Service
public class FileStorageService {

    private final ApplicationProperties properties;

    public FileStorageService(ApplicationProperties properties) {
        this.properties = properties;
    }

    public Path store(MultipartFile file) {
        validate(file);
        String extension = FileTypeUtils.extension(FileTypeUtils.safeFilename(file));
        try {
            Path directory = Files.createTempDirectory("doc-extract-upload-");
            Path target = directory.resolve(UUID.randomUUID() + (extension.isBlank() ? "" : "." + extension));
            Files.copy(file.getInputStream(), target, StandardCopyOption.REPLACE_EXISTING);
            return target;
        } catch (IOException e) {
            throw new BadRequestException("Failed to store uploaded file");
        }
    }

    private void validate(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BadRequestException("A non-empty file is required");
        }
        long maxBytes = properties.getStorage().getMaxFileSizeMb() * 1024L * 1024L;
        if (file.getSize() > maxBytes) {
            throw new BadRequestException("File exceeds maximum size of " + properties.getStorage().getMaxFileSizeMb() + " MB");
        }
        String extension = FileTypeUtils.extension(FileTypeUtils.safeFilename(file));
        if (!properties.getStorage().getAllowedExtensions().contains(extension)) {
            throw new BadRequestException("Unsupported file type: ." + extension);
        }
    }
}
