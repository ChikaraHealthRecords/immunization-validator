package com.example.docextract.strategy;

import com.example.docextract.config.ApplicationProperties;
import com.example.docextract.model.ExtractionResult;
import com.example.docextract.model.ToolDescriptor;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class GoogleDocumentAiStrategy extends CloudDelegatingExtractionStrategy {

    public GoogleDocumentAiStrategy(ApplicationProperties properties, Tess4jExtractionStrategy fallbackStrategy) {
        super(new ToolDescriptor(
                        "google-doc-ai",
                        "Google Document AI",
                        "Managed Document AI",
                        List.of("pdf", "png", "jpg", "jpeg", "tif", "tiff"),
                        List.of("Good OCR and document understanding", "Strong for enterprise pipelines", "Useful for classification and extraction workflows"),
                        List.of("Usage-based cost", "Cloud dependency", "Requires Google Cloud setup outside demo mode"),
                        "Enterprise OCR and document pipelines",
                        properties.getPricing().getGooglePerPage(),
                        true,
                        properties.getCloud().getGoogle().isEnabled(),
                        properties.getCloud().isDemoMode()),
                properties.getCloud().isDemoMode(),
                properties.getCloud().getGoogle().isEnabled(),
                fallbackStrategy);
    }

    @Override
    protected ExtractionResult executeConfigured(ExtractionContext context) {
        throw new UnsupportedOperationException("Wire in the Google Document AI SDK implementation here for production use.");
    }
}
