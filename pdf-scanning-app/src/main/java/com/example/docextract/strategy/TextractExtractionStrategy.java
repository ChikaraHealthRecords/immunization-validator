package com.example.docextract.strategy;

import com.example.docextract.config.ApplicationProperties;
import com.example.docextract.model.ExtractionResult;
import com.example.docextract.model.ToolDescriptor;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class TextractExtractionStrategy extends CloudDelegatingExtractionStrategy {

    public TextractExtractionStrategy(ApplicationProperties properties, Tess4jExtractionStrategy fallbackStrategy) {
        super(new ToolDescriptor(
                        "textract",
                        "Amazon Textract",
                        "Managed Document AI",
                        List.of("pdf", "png", "jpg", "jpeg", "tif", "tiff"),
                        List.of("Strong for forms and tables", "Good production fit for scanned business documents", "Less custom parsing than plain OCR"),
                        List.of("Usage-based cost", "Cloud dependency", "Requires AWS account and credentials outside demo mode"),
                        "Receipts, invoices, forms, and scanned PDFs",
                        properties.getPricing().getTextractPerPage(),
                        true,
                        properties.getCloud().getTextract().isEnabled(),
                        properties.getCloud().isDemoMode()),
                properties.getCloud().isDemoMode(),
                properties.getCloud().getTextract().isEnabled(),
                fallbackStrategy);
    }

    @Override
    protected ExtractionResult executeConfigured(ExtractionContext context) {
        throw new UnsupportedOperationException("Wire in the AWS SDK implementation here for production use.");
    }
}
