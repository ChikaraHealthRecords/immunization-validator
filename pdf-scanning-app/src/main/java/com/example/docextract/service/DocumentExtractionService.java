package com.example.docextract.service;

import com.example.docextract.exception.BadRequestException;
import com.example.docextract.model.DocumentDetails;
import com.example.docextract.model.ExtractionResponse;
import com.example.docextract.model.ExtractionResult;
import com.example.docextract.strategy.ExtractionContext;
import com.example.docextract.strategy.ExtractionStrategy;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Service
public class DocumentExtractionService {

    private final FileStorageService fileStorageService;
    private final DocumentInspectorService documentInspectorService;
    private final StrategyRegistry strategyRegistry;

    public DocumentExtractionService(FileStorageService fileStorageService,
                                     DocumentInspectorService documentInspectorService,
                                     StrategyRegistry strategyRegistry) {
        this.fileStorageService = fileStorageService;
        this.documentInspectorService = documentInspectorService;
        this.strategyRegistry = strategyRegistry;
    }

    public ExtractionResponse extract(MultipartFile file, List<String> toolIds) {
        if (toolIds == null || toolIds.isEmpty()) {
            throw new BadRequestException("Select at least one tool");
        }

        Path storedFile = fileStorageService.store(file);
        try {
            DocumentDetails details = documentInspectorService.inspect(file, storedFile);
            ExtractionContext context = new ExtractionContext(storedFile, details);

            List<ExtractionResult> results = new ArrayList<>();
            for (String toolId : toolIds) {
                ExtractionStrategy strategy = strategyRegistry.getRequired(toolId);
                if (!strategy.supports(context)) {
                    results.add(new ExtractionResult(
                            strategy.describe().id(),
                            strategy.describe().displayName(),
                            "SKIPPED",
                            strategy.describe().estimatedCostPerPage().multiply(java.math.BigDecimal.valueOf(details.pageCount())),
                            "",
                            java.util.Map.of(),
                            List.of(),
                            List.of("Selected file type is not supported by this strategy."),
                            List.of("Choose a different tool or upload a compatible document."),
                            false,
                            Instant.now()));
                    continue;
                }
                results.add(strategy.extract(context));
            }
            return new ExtractionResponse(details, results, Instant.now());
        } finally {
            try {
                Files.deleteIfExists(storedFile);
                Path parent = storedFile.getParent();
                if (parent != null) {
                    Files.deleteIfExists(parent);
                }
            } catch (IOException ignored) {
                // Intentionally ignore temp cleanup failures.
            }
        }
    }
}
