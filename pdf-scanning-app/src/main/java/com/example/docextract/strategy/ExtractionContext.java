package com.example.docextract.strategy;

import com.example.docextract.model.DocumentDetails;

import java.nio.file.Path;

public record ExtractionContext(Path filePath, DocumentDetails documentDetails) {
}
