# Document Extraction App

A production-oriented Spring Boot monolith that demonstrates a **Strategy-pattern-based document extraction platform**.

## What it does
- Upload a PDF or image from the web UI or REST API
- Let users choose one or more tools based on **cost, advantage, and fit**
- Run extraction through a common strategy interface
- Return normalized results so the app can later be reused by other systems

## Included strategies
- Apache PDFBox
- Apache Tika
- Tess4J / Tesseract OCR
- Amazon Textract (demo-mode wrapper until cloud SDK is wired)
- Azure Document Intelligence (demo-mode wrapper until cloud SDK is wired)
- Google Document AI (demo-mode wrapper until cloud SDK is wired)

## Architecture
- **Monolith**: Spring Boot MVC + REST + Thymeleaf
- **Pattern**: Strategy Pattern for extractor plug-ins
- **Integration path**: other services can call `/api/tools` and `/api/extract`
- **Packaging**: executable JAR or OCI image with Spring Boot buildpacks

## Run locally

### Requirements
- Java 21
- Maven 3.6+
- For live OCR using Tess4J: Tesseract/tessdata available and `TESSDATA_PREFIX` configured

### Start
```bash
mvn spring-boot:run
```

Open:
- Web UI: `http://localhost:8080`
- Health: `http://localhost:8080/actuator/health`

### Build executable JAR
```bash
mvn clean package
java -jar target/doc-extraction-app-1.0.0.jar
```

### Build OCI image
```bash
mvn spring-boot:build-image
```

## API examples
### List tools
```bash
curl http://localhost:8080/api/tools
```

### Extract using multiple tools
```bash
curl -X POST http://localhost:8080/api/extract \
  -F "file=@sample.pdf" \
  -F "toolIds=pdfbox" \
  -F "toolIds=tess4j" \
  -F "toolIds=textract"
```

## Demo mode vs production mode
By default, cloud providers run in **demo mode**. That means their strategies are selectable and will simulate output using a local OCR/text strategy so the full UI and Strategy pattern can be demonstrated without cloud credentials.

To move toward production:
1. Disable `app.cloud.demo-mode`
2. Configure the provider under `app.cloud.*`
3. Replace `executeConfigured(...)` in each cloud strategy with the provider SDK/API call
4. Add secure credential handling and storage

## Edge cases handled
- Empty file uploads
- Unsupported file extensions
- Maximum file size
- Strategy/file compatibility checks
- Missing embedded text in image-only PDFs
- Missing tessdata configuration warning
- Temp-file cleanup
- Clear HTTP error responses

## Recommended next production steps
- Add persistence for extraction history and audit events
- Add authentication/authorization
- Add async processing for large files
- Add object storage and queue-based execution
- Add cloud provider SDK integrations behind the existing strategy contracts
