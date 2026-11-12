# FHIR validation

`POST /api/v1/validate/fhir` validates FHIR R4 immunization data against state school-entry rules.
It doesn't add a new engine. It translates the FHIR resources into the same patient model that
`/single` and `/batch` use and passes each patient to the existing validation service unchanged,
so the clinical result is the same as for the equivalent native JSON.

All examples use made-up data.

## Request

```
POST /api/v1/validate/fhir?state=MA&schoolYear=K-6&responseMode=simple
Content-Type: application/fhir+json
```

| Query parameter | Required | Meaning |
|---|---|---|
| `state` | yes | Two-letter state code. Case and surrounding spaces don't matter. Blank is `MISSING_PARAMETER`; a state with no rules is `UNSUPPORTED_STATE`. |
| `schoolYear` | effectively | The state's school-year group, such as `K-6`. FHIR doesn't carry it. Without it, MA results are `undetermined`. |
| `age` | no | Age in years, a whole number. |
| `responseMode` | no | `simple` (default) or `detailed`, as on `/single`. |

Accepted content types: `application/json`, `application/fhir+json`, `application/x-ndjson`,
`application/fhir+ndjson`. Anything else is 415 `UNSUPPORTED_MEDIA_TYPE`. The body is read as UTF-8,
whatever the content type says.

### Accepted input forms

1. An R4 **Bundle** of any type (`collection`, `searchset`, `transaction`, ...). Its entries are used, with their `fullUrl`.
2. A **JSON array** of resources.
3. **NDJSON** (bulk-export style): one resource per line.
4. **One lone resource**, such as a single Patient.

Only `Patient` and `Immunization` resources are used. Every other resource type is ignored and counted.

## Response

Always the same shape, even for one patient. Fields with no value are left out.

```json
{
  "results": [ { "...": "one /single-style result per patient" } ],
  "mapping": [ { "received": 3, "mapped": 1, "excluded": [], "warnings": [] } ],
  "unattributed": [ { "resourceId": "imm-x1", "reason": "REFERS_TO_OTHER_PATIENT" } ],
  "ignoredResourceTypes": { "Condition": 5, "AllergyIntolerance": 2 },
  "message": "Some records could not be processed. Please review the affected records.",
  "failedCount": 1
}
```

| Field | Meaning |
|---|---|
| `results` | One validation result per patient, in input order, exactly as `/single` would return it (`id`, `status`, `valid`, `message`, `warnings`, `metadata`, and in detailed mode `unmetRequirements` and `undeterminedConditions`). A patient that failed has `error` and no `status` or `valid`, as on `/batch`. |
| `mapping` | One report per patient, in the same order as `results`: how many Immunization resources were attributed to the patient (`received`), how many were passed to the validator (`mapped`), each one left out with a reason (`excluded`), and anything worth knowing (`warnings`). |
| `unattributed` | Doses that refer to a Patient who isn't in the input. They count for nobody. |
| `ignoredResourceTypes` | Resource types that were ignored, with counts. They never change the result. |
| `message`, `failedCount` | Only when at least one patient failed, as on `/batch`. |

`warnings` inside `results` come from the validation engine and name records by their position in
the translated list, such as `[immunization[3], ...]`. That position counts only the doses that were
passed on (`mapped`), in input order.

### Example

A made-up patient with one completed DTaP dose, one `not-done` dose and one entered in error
(fixture `03-status-filtering`):

```bash
curl -X POST "http://localhost:8080/api/v1/validate/fhir?state=MA&schoolYear=K-6" \
  -H "Content-Type: application/fhir+json" --data-binary @src/test/resources/fhir/03-status-filtering.json
```
```json
{
  "results": [{
    "status": "invalid", "valid": false,
    "message": "Patient does not meet requirements: 5 requirement(s) not satisfied",
    "metadata": {"validatedAt": "2026-11-12T00:46:14", "state": "MA", "schoolYear": "K-6", "totalRequirements": 5,
                 "satisfiedRequirements": 0, "unsatisfiedRequirements": 5, "undeterminedRequirements": 0,
                 "validatorVersion": "3.1.0"},
    "id": "patient-003"
  }],
  "mapping": [{
    "received": 3, "mapped": 1,
    "excluded": [{"resourceId": "imm-notdone", "reason": "STATUS_NOT_DONE"},
                 {"resourceId": "imm-error", "reason": "STATUS_ENTERED_IN_ERROR"}],
    "warnings": [{"code": "NOT_DONE_NOT_COUNTED", "resourceId": "imm-notdone"}]
  }],
  "unattributed": [],
  "ignoredResourceTypes": {}
}
```

NDJSON must be sent with its line breaks intact: use `curl --data-binary @file.ndjson`, not `-d`.

```bash
curl -X POST "http://localhost:8080/api/v1/validate/fhir?state=MA&schoolYear=K-6" \
  -H "Content-Type: application/x-ndjson" --data-binary @src/test/resources/fhir/12-epic-shaped-bulk.ndjson
```

## Errors

Errors use the shape and codes in [api-errors.md](api-errors.md) and carry a `requestId`. They never
have `status` or `valid`.

| Problem | HTTP | `errorCode` | `field` | `message` |
|---|---|---|---|---|
| Body isn't readable JSON, NDJSON, or a resource | 400 | `MALFORMED_REQUEST` | none | We couldn't read the submitted record. Please check its format. |
| No body | 400 | `MALFORMED_REQUEST` | none | same, with a `details` entry "No record was submitted." |
| No Patient and no Immunization in the input | 400 | `VALIDATION_FAILED` | `entry` | Please check the submitted information. |
| A dose with no usable patient reference | 400 | `VALIDATION_FAILED` | `entry[i].resource.patient` | Please check the submitted information. |
| A Patient with no `id` and no `identifier` value | 400 | `VALIDATION_FAILED` | `id` | Patient identifier is required. |
| `state` missing or blank | 400 | `MISSING_PARAMETER` | `state` | Please select a state. |
| `state` with no rules | 400 | `UNSUPPORTED_STATE` | `state` | Validation is not currently supported for the selected state. |
| `age` not a whole number | 400 | `INVALID_PARAMETER_TYPE` | `age` | Age must be a whole number. |
| Content type not accepted | 415 | `UNSUPPORTED_MEDIA_TYPE` | none | Please submit your record in JSON format. |

Inside a 200 response, a patient can fail on its own without failing the others:

| Item `errorCode` | When | Item message |
|---|---|---|
| `VALIDATION_FAILED` (field `id`) | The Patient's id is blank or only spaces | Patient identifier is required. |
| `RECORD_PROCESSING_FAILED` | The patient crashed processing | This record could not be processed. Please review it. |

The server log records a fixed reason code for each mapping error (for example `NOT_JSON`,
`NOT_A_RESOURCE`, `NO_PATIENT_REFERENCE`); the response carries only the code, field and message above.

## Business rules

From the kit's `FHIR-mapping-business-rules.md` (section 4), unchanged. The design principle:
whenever FHIR data is ambiguous, lean toward **not counting a dose** or **UNDETERMINED**, never toward
a guess that could count one. The adapter only translates. It never interprets clinically and never
changes a state rule.

| # | Rule | Why | Risk if broken | Needs sign-off |
|---|---|---|---|---|
| 1 | **Only Patient `id` and `birthDate` leave the FHIR Patient.** Name, address, phone and other identifiers are dropped. | Minimum necessary data. The validator doesn't need them, and launch privacy review is open. | PHI in logs and responses | Tyler (privacy) |
| 2 | **Patient id** is `Patient.id`. If it's missing, the first `identifier.value`. If neither exists, 400. | `Patient.id` can be server-local, so the agreed stable identifier is an open question. | Wrong id on the response | Tyler |
| 3 | **Status filter.** Only `completed` counts. `not-done` and `entered-in-error` are never doses. A missing or unknown status is excluded with a warning. | `not-done` means the vaccine was **not** given. | A refused or retracted dose counts, so a child looks compliant | Clinical reviewer |
| 4 | **`not-done` is not an exception.** Exceptions are never created from FHIR data. `exceptions` is always `[]`. | An accepted exception satisfies a requirement outright. Deriving one from a free-text reason is a clinical judgement. | A false VALID | Clinical reviewer |
| 5 | **Subpotent doses** (`isSubpotent: true`) are not counted and carry a warning. | ACIP says a subpotent dose does not count. The engine can't express this, so the adapter excludes it. | A bad dose counts | Clinical reviewer |
| 6 | **Dates are never padded.** `2020-06` and `2020` pass through as sent, and the engine sets the record aside (UNDETERMINED). `occurrenceString` ("June 2020") is not parsed. | A made-up day (the 1st) can flip "on or after the 4th birthday" or an interval check. | A false VALID or INVALID | Clinical reviewer |
| 7 | **Only a full, real birth date (`YYYY-MM-DD`) is sent.** A partial one (`2019-04`) or an impossible one (`2019-02-30`) is withheld, with the warning `BIRTHDATE_PARTIAL` or `BIRTHDATE_INVALID`. The engine then sees no birth date, so date conditions can't be evaluated (same outcome as a partial date, never a guess). | Same reason as rule 6. Also, the engine writes a bad birth date to the server log when it tries to read it, so it must never receive one. | Wrong age-based result; PHI in logs | Tyler |
| 8 | **The CVX code is chosen by coding system**, `http://hl7.org/fhir/sid/cvx` or `urn:oid:2.16.840.1.113883.12.292`, never by array position. Leading zeros are fine (`"08"` is 8). Two different CVX codings, or a non-numeric code, give no code and a warning. | Resources carry NDC, SNOMED and CVX codings side by side, in any order. | The wrong vaccine counts | Tyler |
| 9 | **No guessing from text.** `vaccineCode.text` is never turned into a vaccine. A record with no CVX coding is passed on without a code, and the engine sets it aside. | A compliance engine must not do fuzzy matching on names. | Wrong vaccine counts | Tyler |
| 10 | **Never drop a record that could be a dose, except by rules 3 and 5.** An unreadable record becomes UNDETERMINED through the existing engine rule. | It could be the missing dose. | A silent false INVALID or VALID | Tyler |
| 11 | **Attribution is by reference only.** `Patient/id`, an absolute URL, `Patient/id/_history/n` and `urn:uuid:` (matched to `fullUrl`) are understood. A dose is never attached to a patient by assumption, even if the bundle has one Patient. | Wrong patient is the worst failure. | A dose on the wrong child | Tyler |
| 12 | **A dose with no patient reference fails the request** (400, names the entry). **If the input has no Patient at all** (Tyler's array file), doses are grouped by their reference: the id comes from the reference, there is no `birthDate`, and the warning `NO_PATIENT_RESOURCE` is added. **If the input has Patients and a dose refers to one that isn't there,** the dose is excluded and listed as `unattributed`. | The first is a broken record. The second is honest about missing data. The third belongs to someone else. | A dose dropped or misattributed | Tyler |
| 13 | **Doses count by date, not by source dose number.** `protocolApplied.doseNumberPositiveInt` is carried as `doseNumber`, but the engine counts by sorted dates and, as far as I found, ignores `doseNumber`. | A source's "dose 3" can be wrong or mean something else. | Wrong dose count | Tyler |
| 14 | **State, school year and age come from the request**, as on `/single`. FHIR carries none of them. Age-only requests for MA return UNDETERMINED (existing behavior), so `schoolYear` is effectively required. | School year isn't in the clinical record. | Confusing UNDETERMINED | Tyler |
| 15 | **Input forms: R4 Bundle (any type), JSON array of resources, NDJSON, or one lone resource.** A lone Patient with no doses is accepted with the warning `NO_IMMUNIZATION_RESOURCES`. Input with no Patient and no Immunization is a 400. | Tyler's real samples use arrays and NDJSON. | None | Tyler |
| 16 | **Provenance is preserved, never invented.** `performer` is not the record author. `meta.source` is not the transfer chain. | Legal traceability needs the real chain. | A false audit trail | Tyler, legal |
| 17 | **Every exclusion is reported** in a `mapping` section: counts received and mapped, and each excluded or flagged resource id with a reason code. | Staff must be able to see why a dose didn't count. | Silent data loss | Tyler |
| 18 | **Other resource types are ignored and counted**, for example `{"Condition": 5, "AllergyIntolerance": 2}`. They never change the result. | The validator only needs Patient and Immunization. | Silently using unreviewed clinical data | Tyler |

Everything marked "Clinical reviewer" needs sign-off from someone with medical experience.

Rule 13: the engine was checked and does ignore `doseNumber`; doses are counted and checked by sorted date.

### Field mapping (FHIR R4)

| FHIR | Chikara | Rule |
|---|---|---|
| `Patient.id`, else `Patient.identifier[0].value` | `id` | 2 |
| `Patient.birthDate` | `birthDate` (only if full and real) | 7 |
| `Patient.name`, `address`, `telecom` | not mapped | 1 |
| `Immunization.patient.reference` | which patient the dose belongs to | 11, 12 |
| `Immunization.status` | include or exclude | 3 |
| `Immunization.isSubpotent` | include or exclude | 5 |
| `Immunization.vaccineCode.coding[]` where system is CVX | `cvxCode` (number) | 8, 9 |
| `Immunization.occurrenceDateTime` | `occurrenceDateTime` (as sent) | 6 |
| `Immunization.occurrenceString` | not mapped, warning | 6 |
| `Immunization.lotNumber` | `lotNumber` | none |
| `Immunization.protocolApplied[].doseNumberPositiveInt` | `doseNumber` | 13 |
| `Immunization.performer`, `meta.source` | not mapped in v1, documented | 16 |
| Nothing | `exceptions: []` | 4 |

## Input and mapping details

These cases are not settled by the business rules above. Each was decided and is tested
(`FhirPatientMapperTest`). They are rules of this endpoint.

1. **Birth date (rule 7).** `birthDate` is sent to the engine only if it is exactly `YYYY-MM-DD` and a real calendar day. A partial date (`2019`, `2019-04`) is withheld with the warning `BIRTHDATE_PARTIAL`. Anything else, such as `2019-02-30`, `2019-13-01`, a date-time, or a date with spaces around it, is withheld with `BIRTHDATE_INVALID`. With no birth date, the engine can't check age-based conditions, so the result is never `valid` on the strength of a guessed date. (This also keeps bad birth dates out of the server log: the engine logs a birth date it can't read.)
2. **NDJSON needs two or more non-blank lines.** A body that isn't one JSON value is read as NDJSON only if it has at least two non-blank lines. A single unreadable line is `MALFORMED_REQUEST` (reason `NOT_JSON`). Any unreadable line fails the whole request.
3. **Trailing content fails.** Two JSON values on one line, or anything after a complete JSON value, is `MALFORMED_REQUEST` (`NOT_JSON`). Nothing after the first value is silently dropped.
4. **Array and NDJSON items must be objects.** A JSON array item or NDJSON line that isn't a JSON object, such as `5`, `"text"` or `null`, is `MALFORMED_REQUEST` (`NOT_A_RESOURCE`). So is a Bundle entry, or an entry's `resource`, that isn't an object, or a Bundle `entry` that isn't a list. A body that is a bare number, string or `null` is `NOT_AN_OBJECT`.
5. **Reference ids.** In `Patient/<id>`, an absolute URL ending in `Patient/<id>`, or `Patient/<id>/_history/<n>`, the id must match `[A-Za-z0-9.-]{1,64}`, as in the FHIR id type. Any other reference (and a `urn:uuid:` that matches no `fullUrl` when the input has Patients) is not attributed: no usable reference fails the request; an unmatched one goes to `unattributed`.
6. **Immunizations with no id** are named `entry[i]` in the mapping report, where `i` is the resource's position in the input (Bundle entry, array item or NDJSON line, counting from 0).
7. **Dates are trimmed, nothing else.** Surrounding spaces are removed from `occurrenceDateTime`; the value is otherwise passed on as sent. Partial dates get the warning `PARTIAL_DATE_NOT_PADDED` and the engine sets the dose aside.
8. **Numeric CVX codes are accepted.** `"code": 20` is the same as `"code": "20"`. A code must be plain digits that fit a 32-bit integer: `20.5`, `"2O"` or `"99999999999"` give no code and the warning `NON_NUMERIC_CVX`. The same code twice (`"20"` and `"020"`) is one code, not ambiguous.
9. **A resource with no `resourceType`** is ignored and counted as `"unknown"` in `ignoredResourceTypes`. So is an empty Bundle entry.
10. **`doseNumberPositiveInt`** is carried as `doseNumber` only if it is a whole JSON number above 0 (the first such value across `protocolApplied`). `0`, `-1`, `2.0`, `"3"` and `true` are not carried.
11. **Blank patient id.** A Patient with no `id` and no `identifier` value fails the request (400, field `id`). A Patient whose id is present but blank or only spaces is passed on, and becomes an error item in `results` (`VALIDATION_FAILED`, field `id`); the other patients are still validated.
12. **Byte-order mark.** A leading UTF-8 byte-order mark is ignored.
13. **Broken characters.** A broken character (half of an emoji) in an id or resource type is replaced with `?` in the response, so it can't fail the response.

## Privacy

- Only `Patient.id` (or the first identifier value) and a full, real `birthDate` leave the FHIR Patient. Name, address, telecom, other identifiers and extensions are never read into the model or returned.
- The request body is never logged. Logs carry counts (patients, immunizations received, mapped, excluded, warnings, unattributed, ignored), result status counts, fixed reason codes and masked patient ids (first 4 and last 4 characters).
- Allergies, conditions and every other resource type are counted, never read.

## Limits

- **No request size limit** beyond the web server's defaults. A very large bulk export is read into memory in one piece.
- **R4 only**, and only the fields in the mapping table. `performer`, `meta.source`, `statusReason`, `reasonCode` and `reaction` are not used.
- **Provenance** isn't carried (rule 16).
- **Unknown CVX codes** (not in `vaccines.csv`) make the result `undetermined`, as for native JSON. EHR feeds carry many codes, so keep `vaccines.csv` current.
- **Only the request's state and school year** are used. FHIR carries neither.
- **One request is one validation run.** Patients aren't merged across requests, and two Patient resources with the same id in one input are two patients (doses attach to the later one).
- Validated against made-up fixtures and two Epic sample files only. Other sources (Cerner, Fasten, 1upHealth) are untested.

## Testing

- `FhirPatientMapperTest`: the 16 kit fixtures in `src/test/resources/fhir/` against their `*.expected.json`, plus the details above.
- `FhirValidationControllerTest`: parity with native JSON on `/single`, Epic-shaped input, doses that must not count, errors, isolation and log privacy.
- `scripts/fhir-smoke-test.sh [BASE_URL]`: the same fixtures against a running server.
