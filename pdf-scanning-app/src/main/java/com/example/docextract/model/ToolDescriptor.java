package com.example.docextract.model;

import java.math.BigDecimal;
import java.util.List;

public record ToolDescriptor(
        String id,
        String displayName,
        String category,
        List<String> supportedExtensions,
        List<String> advantages,
        List<String> tradeoffs,
        String bestFor,
        BigDecimal estimatedCostPerPage,
        boolean requiresConfiguration,
        boolean configured,
        boolean availableInDemoMode
) {
}
