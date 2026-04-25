package com.example.docextract.model;

import jakarta.validation.constraints.NotEmpty;

import java.util.List;

public record ExtractionRequest(@NotEmpty(message = "Select at least one tool") List<String> toolIds) {
}
