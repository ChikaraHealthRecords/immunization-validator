package com.example.docextract.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;

public record ExtractionResult(
        String toolId,
        String toolDisplayName,
        String status,
        BigDecimal estimatedCost,
        String extractedText,
        Map<String, String> fields,
        List<Map<String, String>> tableRows,
        List<String> warnings,
        List<String> notes,
        boolean simulated,
        Instant processedAt
) {
}
