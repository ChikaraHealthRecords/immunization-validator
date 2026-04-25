package com.example.docextract.strategy;

import com.example.docextract.config.ApplicationProperties;
import com.example.docextract.exception.ExtractionFailedException;
import com.example.docextract.model.ExtractionResult;
import com.example.docextract.model.ToolDescriptor;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@Component
public class PdfBoxExtractionStrategy extends AbstractExtractionStrategy {

    private final ToolDescriptor descriptor;

    public PdfBoxExtractionStrategy(ApplicationProperties properties) {
        this.descriptor = new ToolDescriptor(
                "pdfbox",
                "Apache PDFBox",
                "Digital PDF Text Extraction",
                List.of("pdf"),
                List.of("Very fast for digital PDFs", "Java-native and easy to integrate", "No per-page cloud cost"),
                List.of("Does not OCR scanned-image PDFs", "Limited structured extraction"),
                "Native PDFs that already contain selectable text",
                properties.getPricing().getPdfboxPerPage(),
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
        return context.documentDetails().pdf();
    }

    @Override
    public ExtractionResult extract(ExtractionContext context) {
        try (var document = Loader.loadPDF(context.filePath().toFile())) {
            var stripper = new PDFTextStripper();
            String text = stripper.getText(document).trim();
            if (text.isBlank()) {
                return failedResult(descriptor,
                        estimate(context),
                        "No embedded text found. This PDF may be image-based and should be routed to OCR.",
                        List.of("Try Tess4J or a cloud document AI strategy."),
                        false);
            }
            return successResult(descriptor,
                    context,
                    estimate(context),
                    text,
                    Map.of("pageCount", String.valueOf(document.getNumberOfPages())),
                    List.of(),
                    List.of(),
                    List.of("Best used for digital PDFs with existing text."),
                    false);
        } catch (IOException e) {
            throw new ExtractionFailedException("PDFBox failed to extract text", e);
        }
    }

    private BigDecimal estimate(ExtractionContext context) {
        return descriptor.estimatedCostPerPage().multiply(BigDecimal.valueOf(context.documentDetails().pageCount()));
    }
}
