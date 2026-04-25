package com.example.docextract.strategy;

import com.example.docextract.config.ApplicationProperties;
import com.example.docextract.exception.ExtractionFailedException;
import com.example.docextract.model.ExtractionResult;
import com.example.docextract.model.ToolDescriptor;
import org.apache.tika.Tika;
import org.apache.tika.exception.TikaException;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@Component
public class TikaExtractionStrategy extends AbstractExtractionStrategy {

    private final ToolDescriptor descriptor;
    private final Tika tika = new Tika();

    public TikaExtractionStrategy(ApplicationProperties properties) {
        this.descriptor = new ToolDescriptor(
                "tika",
                "Apache Tika",
                "General Content Extraction",
                List.of("pdf", "txt"),
                List.of(
                        "Broad file parsing support",
                        "Good fallback parser",
                        "Useful for metadata and text extraction"
                ),
                List.of(
                        "Not purpose-built for OCR-heavy workloads",
                        "Structured extraction is limited"
                ),
                "General text extraction and multi-format document ingestion",
                properties.getPricing().getTikaPerPage(),
                false,
                true,
                true
        );
    }

    @Override
    public String id() {
        return descriptor.id();
    }

    @Override
    public ToolDescriptor describe() {
        return descriptor;
    }

    @Override
    public boolean supports(ExtractionContext context) {
        return context.documentDetails().pdf()
                || "txt".equalsIgnoreCase(context.documentDetails().extension());
    }

    @Override
    public ExtractionResult extract(ExtractionContext context) {
        try {
            String text = tika.parseToString(context.filePath());

            if (text == null || text.isBlank()) {
                return failedResult(
                        descriptor,
                        estimate(context),
                        "Tika returned empty content for this file.",
                        List.of("Use PDFBox for native PDFs or OCR for scanned documents."),
                        false
                );
            }

            return successResult(
                    descriptor,
                    context,
                    estimate(context),
                    text.trim(),
                    Map.of("parser", "Apache Tika"),
                    List.of(),
                    List.of(),
                    List.of("Useful as a broad ingestion parser in a monolith or integration service."),
                    false
            );

        } catch (IOException | TikaException e) {
            throw new ExtractionFailedException("Tika failed to extract content", e);
        }
    }

    private BigDecimal estimate(ExtractionContext context) {
        return descriptor.estimatedCostPerPage()
                .multiply(BigDecimal.valueOf(context.documentDetails().pageCount()));
    }
}