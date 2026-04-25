package com.example.docextract.strategy;

import com.example.docextract.config.ApplicationProperties;
import com.example.docextract.model.ExtractionResult;
import com.example.docextract.model.ToolDescriptor;
import net.sourceforge.tess4j.Tesseract;
import net.sourceforge.tess4j.TesseractException;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

@Component
public class Tess4jExtractionStrategy extends AbstractExtractionStrategy {

    private final ToolDescriptor descriptor;
    private final ApplicationProperties properties;

    public Tess4jExtractionStrategy(ApplicationProperties properties) {
        this.properties = properties;
        this.descriptor = new ToolDescriptor(
                "tess4j",
                "Tess4J / Tesseract OCR",
                "OCR for scanned documents and images",
                List.of("png", "jpg", "jpeg", "tif", "tiff", "bmp", "gif", "pdf"),
                List.of(
                        "Low-cost OCR baseline",
                        "Java-friendly wrapper",
                        "Good for images and scanned pages"
                ),
                List.of(
                        "Needs tessdata configuration in real environments",
                        "More cleanup may be needed",
                        "Weaker structure recovery than document AI services"
                ),
                "Scanned PDFs, screenshots, and phone images",
                properties.getPricing().getTess4jPerPage(),
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
        String ext = context.documentDetails().extension();
        return List.of("png", "jpg", "jpeg", "tif", "tiff", "bmp", "gif", "pdf")
                .contains(ext.toLowerCase());
    }

    @Override
    public ExtractionResult extract(ExtractionContext context) {
        String tessdataPath = resolveTessdataPath();
        String language = resolveLanguage();

        if (tessdataPath == null || tessdataPath.isBlank()) {
            return failedResult(
                    descriptor,
                    estimate(context),
                    "Tesseract tessdata path is not configured.",
                    List.of(
                            "Set app.ocr.tessdata-path in application.yml",
                            "Or set TESSDATA_PREFIX to your tessdata directory"
                    ),
                    false
            );
        }

        Path trainedData = Path.of(tessdataPath, language + ".traineddata");
        if (!Files.exists(trainedData)) {
            return failedResult(
                    descriptor,
                    estimate(context),
                    "Missing OCR language data file: " + trainedData,
                    List.of(
                            "Make sure " + language + ".traineddata exists in tessdata",
                            "Example path: C:/Program Files/Tesseract-OCR/tessdata"
                    ),
                    false
            );
        }

        try {
            Tesseract tesseract = new Tesseract();
            tesseract.setDatapath(tessdataPath);
            tesseract.setLanguage(language);

            String text = tesseract.doOCR(context.filePath().toFile());

            if (text == null || text.isBlank()) {
                return failedResult(
                        descriptor,
                        estimate(context),
                        "OCR completed but no readable text was found.",
                        List.of(
                                "Try a clearer image",
                                "Increase image resolution",
                                "Use a higher-contrast scan"
                        ),
                        false
                );
            }

            return successResult(
                    descriptor,
                    context,
                    estimate(context),
                    text.trim(),
                    Map.of(
                            "engine", "Tess4J/Tesseract",
                            "language", language,
                            "tessdataPath", tessdataPath
                    ),
                    List.of(),
                    List.of(),
                    List.of("OCR quality depends on resolution, contrast, and skew."),
                    false
            );

        } catch (TesseractException | IllegalArgumentException e) {
            return failedResult(
                    descriptor,
                    estimate(context),
                    "Tesseract OCR failed: " + e.getMessage(),
                    List.of(
                            "Verify Tesseract installation",
                            "Verify tessdata path and language packs",
                            "Check that the uploaded image is readable"
                    ),
                    false
            );
        }
    }

    private String resolveTessdataPath() {
        String configured = properties.getOcr().getTessdataPath();
        if (configured != null && !configured.isBlank()) {
            return configured;
        }
        return System.getenv("TESSDATA_PREFIX");
    }

    private String resolveLanguage() {
        String language = properties.getOcr().getLanguage();
        return (language == null || language.isBlank()) ? "eng" : language;
    }

    private BigDecimal estimate(ExtractionContext context) {
        return descriptor.estimatedCostPerPage()
                .multiply(BigDecimal.valueOf(context.documentDetails().pageCount()));
    }
}