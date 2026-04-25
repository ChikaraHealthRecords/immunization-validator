package com.example.docextract.strategy;

import com.example.docextract.exception.ToolNotConfiguredException;
import com.example.docextract.model.ExtractionResult;
import com.example.docextract.model.ToolDescriptor;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public abstract class CloudDelegatingExtractionStrategy extends AbstractExtractionStrategy {

    private final ToolDescriptor descriptor;
    private final boolean demoMode;
    private final boolean configured;
    private final ExtractionStrategy fallbackStrategy;

    protected CloudDelegatingExtractionStrategy(ToolDescriptor descriptor,
                                                boolean demoMode,
                                                boolean configured,
                                                ExtractionStrategy fallbackStrategy) {
        this.descriptor = descriptor;
        this.demoMode = demoMode;
        this.configured = configured;
        this.fallbackStrategy = fallbackStrategy;
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
        return context.documentDetails().pdf() || context.documentDetails().image();
    }

    @Override
    public ExtractionResult extract(ExtractionContext context) {
        if (configured) {
            return executeConfigured(context);
        }
        if (!demoMode) {
            throw new ToolNotConfiguredException(descriptor.displayName() + " is not configured. Set provider credentials or enable demo mode.");
        }
        ExtractionResult delegated = fallbackStrategy.extract(context);
        Map<String, String> fields = new LinkedHashMap<>(delegated.fields());
        fields.put("simulatedProvider", descriptor.displayName());
        return successResult(descriptor,
                context,
                estimate(context),
                delegated.extractedText(),
                fields,
                delegated.tableRows(),
                List.of("Demo mode simulated this provider using a local extractor."),
                List.of(
                        "This lets you demo the Strategy pattern without live cloud credentials.",
                        "Replace this method with the provider SDK or API call for production."
                ),
                true);
    }

    protected abstract ExtractionResult executeConfigured(ExtractionContext context);

    protected BigDecimal estimate(ExtractionContext context) {
        return descriptor.estimatedCostPerPage().multiply(BigDecimal.valueOf(context.documentDetails().pageCount()));
    }
}
