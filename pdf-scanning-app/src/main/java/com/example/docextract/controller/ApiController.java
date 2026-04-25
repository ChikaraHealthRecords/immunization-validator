package com.example.docextract.controller;

import com.example.docextract.model.ExtractionResponse;
import com.example.docextract.model.ToolDescriptor;
import com.example.docextract.service.DocumentExtractionService;
import com.example.docextract.service.StrategyRegistry;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api")
public class ApiController {

    private final StrategyRegistry strategyRegistry;
    private final DocumentExtractionService documentExtractionService;

    public ApiController(StrategyRegistry strategyRegistry,
                         DocumentExtractionService documentExtractionService) {
        this.strategyRegistry = strategyRegistry;
        this.documentExtractionService = documentExtractionService;
    }

    @GetMapping("/tools")
    public List<ToolDescriptor> tools() {
        return strategyRegistry.descriptors();
    }

    @PostMapping(value = "/extract", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ExtractionResponse extract(@RequestParam("file") MultipartFile file,
                                      @RequestParam("toolIds") List<String> toolIds) {
        return documentExtractionService.extract(file, toolIds);
    }
}
