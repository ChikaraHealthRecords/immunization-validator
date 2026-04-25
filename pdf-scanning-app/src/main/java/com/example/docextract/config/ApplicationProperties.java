package com.example.docextract.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@ConfigurationProperties(prefix = "app")
public class ApplicationProperties {

    private final Storage storage = new Storage();
    private final Cloud cloud = new Cloud();
    private final Pricing pricing = new Pricing();
    private final Ocr ocr = new Ocr();

    public Storage getStorage() {
        return storage;
    }

    public Cloud getCloud() {
        return cloud;
    }

    public Pricing getPricing() {
        return pricing;
    }

    public Ocr getOcr() {
        return ocr;
    }

    public static class Storage {
        private int maxFileSizeMb = 15;
        private List<String> allowedExtensions = new ArrayList<>();

        public int getMaxFileSizeMb() {
            return maxFileSizeMb;
        }

        public void setMaxFileSizeMb(int maxFileSizeMb) {
            this.maxFileSizeMb = maxFileSizeMb;
        }

        public List<String> getAllowedExtensions() {
            return allowedExtensions;
        }

        public void setAllowedExtensions(List<String> allowedExtensions) {
            this.allowedExtensions = allowedExtensions;
        }
    }

    public static class Cloud {
        private boolean demoMode = true;
        private final Provider textract = new Provider();
        private final Provider azure = new Provider();
        private final Provider google = new Provider();

        public boolean isDemoMode() {
            return demoMode;
        }

        public void setDemoMode(boolean demoMode) {
            this.demoMode = demoMode;
        }

        public Provider getTextract() {
            return textract;
        }

        public Provider getAzure() {
            return azure;
        }

        public Provider getGoogle() {
            return google;
        }
    }

    public static class Provider {
        private boolean enabled = false;
        private String endpoint = "demo";

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getEndpoint() {
            return endpoint;
        }

        public void setEndpoint(String endpoint) {
            this.endpoint = endpoint;
        }
    }

    public static class Pricing {
        private BigDecimal pdfboxPerPage = new BigDecimal("0.0000");
        private BigDecimal tikaPerPage = new BigDecimal("0.0000");
        private BigDecimal tess4jPerPage = new BigDecimal("0.0020");
        private BigDecimal textractPerPage = new BigDecimal("0.0150");
        private BigDecimal azurePerPage = new BigDecimal("0.0120");
        private BigDecimal googlePerPage = new BigDecimal("0.0100");

        public BigDecimal getPdfboxPerPage() {
            return pdfboxPerPage;
        }

        public void setPdfboxPerPage(BigDecimal pdfboxPerPage) {
            this.pdfboxPerPage = pdfboxPerPage;
        }

        public BigDecimal getTikaPerPage() {
            return tikaPerPage;
        }

        public void setTikaPerPage(BigDecimal tikaPerPage) {
            this.tikaPerPage = tikaPerPage;
        }

        public BigDecimal getTess4jPerPage() {
            return tess4jPerPage;
        }

        public void setTess4jPerPage(BigDecimal tess4jPerPage) {
            this.tess4jPerPage = tess4jPerPage;
        }

        public BigDecimal getTextractPerPage() {
            return textractPerPage;
        }

        public void setTextractPerPage(BigDecimal textractPerPage) {
            this.textractPerPage = textractPerPage;
        }

        public BigDecimal getAzurePerPage() {
            return azurePerPage;
        }

        public void setAzurePerPage(BigDecimal azurePerPage) {
            this.azurePerPage = azurePerPage;
        }

        public BigDecimal getGooglePerPage() {
            return googlePerPage;
        }

        public void setGooglePerPage(BigDecimal googlePerPage) {
            this.googlePerPage = googlePerPage;
        }
    }

    public static class Ocr {
        private String tessdataPath = "C:/Program Files/Tesseract-OCR/tessdata";
        private String language = "eng";

        public String getTessdataPath() {
            return tessdataPath;
        }

        public void setTessdataPath(String tessdataPath) {
            this.tessdataPath = tessdataPath;
        }

        public String getLanguage() {
            return language;
        }

        public void setLanguage(String language) {
            this.language = language;
        }
    }
}