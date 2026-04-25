package com.example.docextract.strategy;

import com.example.docextract.config.ApplicationProperties;
import com.example.docextract.model.ExtractionResult;
import com.example.docextract.model.ToolDescriptor;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class AzureDocumentIntelligenceStrategy extends CloudDelegatingExtractionStrategy {

    public AzureDocumentIntelligenceStrategy(ApplicationProperties properties, Tess4jExtractionStrategy fallbackStrategy) {
        super(new ToolDescriptor(
                        "azure-doc-intel",
                        "Azure Document Intelligence",
                        "Managed Document AI",
                        List.of("pdf", "png", "jpg", "jpeg", "tif", "tiff"),
                        List.of("Good OCR + field extraction", "Strong fit for key-value pairs", "Enterprise-friendly model options"),
                        List.of("Usage-based cost", "Cloud dependency", "Requires Azure setup outside demo mode"),
                        "OCR and field extraction for structured documents",
                        properties.getPricing().getAzurePerPage(),
                        true,
                        properties.getCloud().getAzure().isEnabled(),
                        properties.getCloud().isDemoMode()),
                properties.getCloud().isDemoMode(),
                properties.getCloud().getAzure().isEnabled(),
                fallbackStrategy);
    }

    @Override
    protected ExtractionResult executeConfigured(ExtractionContext context) {
        throw new UnsupportedOperationException("Wire in the Azure SDK implementation here for production use.");
    }
}
