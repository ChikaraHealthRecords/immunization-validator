package com.example.docextract.service;

import com.example.docextract.exception.BadRequestException;
import com.example.docextract.model.ToolDescriptor;
import com.example.docextract.strategy.ExtractionStrategy;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class StrategyRegistry {

    private final Map<String, ExtractionStrategy> strategyMap;

    public StrategyRegistry(List<ExtractionStrategy> strategies) {
        this.strategyMap = strategies.stream().collect(Collectors.toUnmodifiableMap(ExtractionStrategy::id, Function.identity()));
    }

    public ExtractionStrategy getRequired(String id) {
        ExtractionStrategy strategy = strategyMap.get(id);
        if (strategy == null) {
            throw new BadRequestException("Unknown tool selected: " + id);
        }
        return strategy;
    }

    public List<ToolDescriptor> descriptors() {
        return strategyMap.values().stream()
                .map(ExtractionStrategy::describe)
                .sorted(Comparator.comparing(ToolDescriptor::category).thenComparing(ToolDescriptor::displayName))
                .toList();
    }
}
