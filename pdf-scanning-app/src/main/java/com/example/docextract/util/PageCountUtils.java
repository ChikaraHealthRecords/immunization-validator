package com.example.docextract.util;

import com.example.docextract.exception.ExtractionFailedException;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;

import java.io.IOException;
import java.nio.file.Path;

public final class PageCountUtils {

    private PageCountUtils() {
    }

    public static int detectPageCount(Path filePath, boolean pdf) {
        if (!pdf) {
            return 1;
        }
        try (PDDocument document = Loader.loadPDF(filePath.toFile())) {
            return Math.max(document.getNumberOfPages(), 1);
        } catch (IOException e) {
            throw new ExtractionFailedException("Failed to count PDF pages", e);
        }
    }
}
