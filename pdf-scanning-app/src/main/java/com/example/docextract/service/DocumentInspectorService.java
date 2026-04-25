package com.example.docextract.service;

import com.example.docextract.model.DocumentDetails;
import com.example.docextract.util.FileTypeUtils;
import com.example.docextract.util.PageCountUtils;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

@Service
public class DocumentInspectorService {

    public DocumentDetails inspect(MultipartFile file, Path storedPath) {
        String filename = FileTypeUtils.safeFilename(file);
        String extension = FileTypeUtils.extension(filename);
        String contentType = file.getContentType();
        boolean pdf = FileTypeUtils.isPdf(extension, contentType);
        boolean image = FileTypeUtils.isImage(extension, contentType);
        int pageCount = PageCountUtils.detectPageCount(storedPath, pdf);
        List<String> warnings = new ArrayList<>();
        if (!pdf && !image && !"txt".equalsIgnoreCase(extension)) {
            warnings.add("This file type may not be supported by all strategies.");
        }
        if (image) {
            warnings.add("Image OCR quality depends on resolution, contrast, and skew." );
        }
        return new DocumentDetails(filename, extension, contentType, file.getSize(), pageCount, pdf, image, warnings);
    }
}
