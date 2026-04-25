package com.example.docextract.model;

import java.util.List;

public record DocumentDetails(
        String originalFilename,
        String extension,
        String contentType,
        long sizeBytes,
        int pageCount,
        boolean pdf,
        boolean image,
        List<String> warnings
) {
}
