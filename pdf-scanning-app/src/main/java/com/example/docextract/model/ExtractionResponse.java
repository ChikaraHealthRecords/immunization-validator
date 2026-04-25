package com.example.docextract.model;

import java.time.Instant;
import java.util.List;

public record ExtractionResponse(
        DocumentDetails document,
        List<ExtractionResult> results,
        Instant processedAt
) {
}
