# Immunization Validator Service

A Spring Boot REST API service for validating immunization records against configurable state requirements. The service is designed with privacy in mind, requiring no authentication and logging only non-PII information.

## Features

- **Configurable Requirements**: State-specific and age/school year-based immunization requirements
- **Single & Batch Validation**: Validate individual patients or process multiple patients at once
- **Two Response Modes**: 
  - Simple: Returns patient ID and validation status
  - Detailed: Includes list of unmet requirements
- **Privacy-Focused**: No authentication required, no PII stored, masked patient identifiers in logs
- **FHIR-Based**: Uses FHIR-standard data models for patient and immunization records
- **Containerized**: Ready to run in Docker containers

## Requirements

- Java 17 or higher
- Maven 3.6+ (for building)
- Docker (optional, for containerized deployment)

## Building the Application

### Using Maven

```bash
mvn clean package
```

This will create a JAR file in the `target/` directory.

### Using Docker

```bash
docker build -t immunization-validator:latest .
```

## Running the Application

### Local Development

```bash
mvn spring-boot:run
```

Or run the JAR directly:

```bash
java -jar target/immunization-validator-1.0.0.jar
```

The service will start on port 8080 by default.

### Docker

```bash
docker run -p 8080:8080 immunization-validator:latest
```

## API Endpoints

### Health Check

```
GET /api/v1/validate/health
```

Returns: `OK`

### Single Patient Validation

```
POST /api/v1/validate/single?state={STATE}&age={AGE}&responseMode={MODE}
```

**Request Body:**
```json
{
  "id": "patient-123",
  "birthDate": "2015-05-15",
  "immunization": [
    {
      "vaccineCode": "DTaP",
      "occurrenceDateTime": "2016-06-01",
      "doseNumber": 1
    },
    {
      "vaccineCode": "MMR",
      "occurrenceDateTime": "2018-09-15",
      "doseNumber": 1
    }
  ]
}
```

**Query Parameters:**
- `state` (required): State code (e.g., "CA", "NY", "TX")
- `age` (optional): Patient age in years (used if birthDate not provided)
- `schoolYear` (optional): School year identifier (alternative to age)
- `responseMode` (optional): "simple" or "detailed" (default: "simple")

**Response (Simple Mode):**
```json
{
  "id": "patient-123",
  "valid": true
}
```

**Response (Detailed Mode):**
```json
{
  "id": "patient-123",
  "valid": false,
  "unmetRequirements": [
    {
      "description": "Insufficient doses of DTaP: required 5, found 1",
      "vaccineCode": "DTaP",
      "requiredDoses": 5,
      "foundDoses": 1
    }
  ]
}
```

### Batch Patient Validation

```
POST /api/v1/validate/batch
```

**Request Body:**
```json
{
  "state": "CA",
  "age": 5,
  "responseMode": "detailed",
  "patients": [
    {
      "id": "patient-123",
      "birthDate": "2015-05-15",
      "immunization": [
        {
          "vaccineCode": "DTaP",
          "occurrenceDateTime": "2016-06-01"
        }
      ]
      },
    {
      "id": "patient-456",
      "birthDate": "2014-03-20",
      "immunization": [
        {
          "vaccineCode": "MMR",
          "occurrenceDateTime": "2018-09-15"
        }
      ]
    }
  ]
}
```

**Response:**
```json
{
  "results": [
    {
      "id": "patient-123",
      "valid": false,
      "unmetRequirements": [...]
    },
    {
      "id": "patient-456",
      "valid": true
    }
  ]
}
```

## Configuration

### State Requirements

Each state's requirements live in their own file in
`src/main/resources/rules/states/`, named by state code (`ma.yml` for
Massachusetts). Every file there is loaded at startup and checked strictly, so
adding a state means adding a file. The format, and a checklist for adding a
state, are in [CONTRIBUTING.md](CONTRIBUTING.md#how-state-rules-are-structured).

```yaml
state: MA
name: Massachusetts
rulesPublished: March 2025
schoolYear:
  - schoolYear: "K-6"
    vaccineCode: "MMR"
    minDoses: 2
    description: "MMR - 2 doses; 1st on/after 1st birthday"
    dateConditions:
      - "1st dose on or after 1st birthday"
```

Requirements can be specified by:
- **School Year**: requirements for the state's school-year groups (`schoolYear:`)
- **Age**: requirements for specific ages in years (`age:`)

To load rule files from outside the jar, set `immunization.requirements.location`
to a resource pattern, for example `file:/etc/validator/rules/*.yml`.

### Environment Variables

You can override configuration using environment variables:

- `SERVER_PORT`: Server port (default: 8080)
- `SPRING_PROFILES_ACTIVE`: Spring profile to use

## Privacy & Security

- **No Authentication**: Service requires no authentication or authorization
- **No PII Storage**: Patient data is processed in-memory only, never persisted
- **Privacy-Focused Logging**: 
  - Only logs timestamps, request source IPs, masked patient IDs, response modes, and errors
  - Patient identifiers are masked (first 4 + last 4 characters shown)
  - No personally identifiable information is logged

## Testing

Run tests with Maven:

```bash
mvn test
```

## Architecture

- **Models**: FHIR-based data models for patients and immunizations
- **Configuration**: Flexible configuration for state/age-based requirements
- **Service Layer**: Validation logic**
- **Controllers**: REST endpoints for validation

## License

This project is provided as-is for demonstration purposes.

## Frontend UI (React)

A lightweight React UI is available for demoing and QA-testing the
Massachusetts immunization validator.

**Repository:**  
https://github.com/SaakaD-hub/ma-validator-react-app

### Local setup

```bash
git clone https://github.com/SaakaD-hub/ma-validator-react-app
cd ma-validator-react-app
npm install
cp .env.example .env
npm start
