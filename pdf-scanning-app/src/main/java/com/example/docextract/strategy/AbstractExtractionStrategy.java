package com.example.docextract.strategy;

import com.example.docextract.model.ExtractionResult;
import com.example.docextract.model.ToolDescriptor;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.List;
import java.util.Map;

public abstract class AbstractExtractionStrategy implements ExtractionStrategy {

    protected ExtractionResult successResult(ToolDescriptor descriptor,
                                             ExtractionContext context,
                                             BigDecimal estimatedCost,
                                             String extractedText,
                                             Map<String, String> fields,
                                             List<Map<String, String>> tableRows,
                                             List<String> warnings,
                                             List<String> notes,
                                             boolean simulated) {
        return new ExtractionResult(
                descriptor.id(),
                descriptor.displayName(),
                "SUCCESS",
                estimatedCost.setScale(4, RoundingMode.HALF_UP),
                extractedText,
                fields,
                tableRows,
                warnings,
                notes,
                simulated,
                Instant.now()
        );
    }

    protected ExtractionResult failedResult(ToolDescriptor descriptor,
                                            BigDecimal estimatedCost,
                                            String message,
                                            List<String> notes,
                                            boolean simulated) {
        return new ExtractionResult(
                descriptor.id(),
                descriptor.displayName(),
                "FAILED",
                estimatedCost.setScale(4, RoundingMode.HALF_UP),
                "",
                Map.of(),
                List.of(),
                List.of(message),
                notes,
                simulated,
                Instant.now()
        );
    }
}
