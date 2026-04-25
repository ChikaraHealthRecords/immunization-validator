package com.example.docextract.controller;

import com.example.docextract.model.ExtractionResponse;
import com.example.docextract.service.DocumentExtractionService;
import com.example.docextract.service.StrategyRegistry;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@Controller
public class WebController {

    private final StrategyRegistry strategyRegistry;
    private final DocumentExtractionService documentExtractionService;

    public WebController(StrategyRegistry strategyRegistry,
                         DocumentExtractionService documentExtractionService) {
        this.strategyRegistry = strategyRegistry;
        this.documentExtractionService = documentExtractionService;
    }

    @GetMapping("/")
    public String index(Model model) {
        model.addAttribute("tools", strategyRegistry.descriptors());
        return "index";
    }

    @PostMapping("/extract")
    public String extract(@RequestParam("file") MultipartFile file,
                          @RequestParam("toolIds") List<String> toolIds,
                          Model model) {
        ExtractionResponse response = documentExtractionService.extract(file, toolIds);
        model.addAttribute("tools", strategyRegistry.descriptors());
        model.addAttribute("response", response);
        model.addAttribute("selectedToolIds", toolIds);
        return "index";
    }
}
