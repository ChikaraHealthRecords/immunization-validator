package com.example.docextract.strategy;

import com.example.docextract.model.ExtractionResult;
import com.example.docextract.model.ToolDescriptor;

public interface ExtractionStrategy {
    String id();
    ToolDescriptor describe();
    boolean supports(ExtractionContext context);
    ExtractionResult extract(ExtractionContext context);
}
