package com.example.docextract.service;

import com.example.docextract.config.ApplicationProperties;
import com.example.docextract.strategy.PdfBoxExtractionStrategy;
import com.example.docextract.strategy.Tess4jExtractionStrategy;
import com.example.docextract.strategy.TikaExtractionStrategy;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class StrategyRegistryTest {

    @Test
    void shouldReturnKnownStrategy() {
        ApplicationProperties props = new ApplicationProperties();
        StrategyRegistry registry = new StrategyRegistry(java.util.List.of(
                new PdfBoxExtractionStrategy(props),
                new TikaExtractionStrategy(props),
                new Tess4jExtractionStrategy(props)
        ));

        assertEquals("pdfbox", registry.getRequired("pdfbox").id());
    }

    @Test
    void shouldThrowForUnknownStrategy() {
        ApplicationProperties props = new ApplicationProperties();
        StrategyRegistry registry = new StrategyRegistry(java.util.List.of(new PdfBoxExtractionStrategy(props)));
        assertThrows(RuntimeException.class, () -> registry.getRequired("missing"));
    }
}
