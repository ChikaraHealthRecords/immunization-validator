# API errors

For the frontend team. Covers every endpoint, including `/api/v1/vaccines/**`.

**Keep two things separate:**

- A **clinical result** (`valid`, `invalid`, `undetermined`) comes back as a 200 with a `status` field. It describes the patient.
- An **error** comes back in the shape below. It describes the request or the system, and it never has a `status` or `valid` field.

## The error shape

```json
{
  "httpStatus": 400,
  "errorCode": "MISSING_PARAMETER",
  "field": "state",
  "expectedType": "...",
  "rejectedValue": "...",
  "message": "Please select a state.",
  "requestId": "3f2b8c1e-7a14-4c55-9a55-0e2f6d1b7c10",
  "details": [ { "field": "...", "errorCode": "...", "message": "..." } ]
}
```

Only `httpStatus`, `errorCode`, `message` and `requestId` are always present. The others appear only when they apply.

| Field | Meaning |
|---|---|
| `httpStatus` | Same as the HTTP status. It isn't called `status` because that means the clinical result on validation responses. |
| `errorCode` | Stable code to branch on. See the table below. |
| `field` | The first failing field or parameter. Left out when the error isn't about a field. |
| `expectedType` | The type the value needed, such as `integer`. Present on `INVALID_PARAMETER_TYPE` errors for `age` and `cvxCode`. |
| `rejectedValue` | The state code that was sent, cut to 10 characters. Present on `UNSUPPORTED_STATE`. **It comes straight from the client, so escape it before displaying it.** |
| `message` | Text you can show the customer as written. |
| `requestId` | Quote this when reporting a problem. It finds the matching lines in the server logs. |
| `details` | Every failing field when more than one failed, or one entry explaining why a record couldn't be read. Otherwise left out. |

Failing fields are listed in alphabetical order, so `field` is predictable. A `details` entry explaining an unreadable record has no `field`.

## Request id

Every response has an `X-Request-Id` header, including successful ones.

You can send your own `X-Request-Id` to tie a request to your logs. It's used only if it matches `^[A-Za-z0-9._-]{1,64}$`; otherwise the server generates one.

Browser code can't currently read this header on cross-origin calls, because it isn't in the CORS exposed headers. On errors, use `requestId` from the body.

## Error codes

| errorCode | HTTP | When |
|---|---|---|
| `VALIDATION_FAILED` | 400 | A field in the JSON body is missing or invalid |
| `MISSING_PARAMETER` | 400 | A required query parameter is missing |
| `INVALID_PARAMETER_TYPE` | 400 | A value has the wrong type, such as `age=five` |
| `MALFORMED_REQUEST` | 400 | The body is missing or isn't readable JSON |
| `UNSUPPORTED_STATE` | 400 | There are no rules for that state |
| `NOT_FOUND` | 404 | The address doesn't exist |
| `METHOD_NOT_ALLOWED` | 405 | Wrong HTTP method for that address |
| `UNSUPPORTED_MEDIA_TYPE` | 415 | The body isn't sent as JSON |
| `REQUEST_NOT_SUPPORTED` | 4xx | Any other client error, such as a 406 |
| `INTERNAL_ERROR` | 500 | Something failed on the server |
| `RECORD_PROCESSING_FAILED` | 200 | Only inside a `/batch` result's `error`: that patient's record crashed processing. |

## Supported states

**Only `MA` has rules today, so every other state returns 400 `UNSUPPORTED_STATE`.**

Matching is exact: `ma` and `Ma` are unsupported too, so send the code in uppercase.

A supported state with no rules for the given school year or age is different. It still returns 200 `undetermined` with the reason "No requirements found".

## Examples

These examples use made-up data. Each one assumes:

```
BASE=http://localhost:8080/api/v1/validate
PATIENT='{"id":"test-patient-001","birthDate":"2019-01-01","immunization":[]}'
```

### Missing state

```bash
curl -X POST "$BASE/single" -H "Content-Type: application/json" -d "$PATIENT"
```
```json
{"httpStatus":400,"errorCode":"MISSING_PARAMETER","field":"state","message":"Please select a state.","requestId":"..."}
```

An empty `state=` gets the same response. On `/batch`, `state` is a body field, so the code is `VALIDATION_FAILED`:

```bash
curl -X POST "$BASE/batch" -H "Content-Type: application/json" \
  -d "{\"responseMode\":\"simple\",\"patients\":[$PATIENT]}"
```
```json
{"httpStatus":400,"errorCode":"VALIDATION_FAILED","field":"state","message":"Please select a state.","requestId":"..."}
```

### Missing patient id

```bash
curl -X POST "$BASE/single?state=MA&schoolYear=K-6" -H "Content-Type: application/json" \
  -d '{"birthDate":"2019-01-01","immunization":[]}'
```
```json
{"httpStatus":400,"errorCode":"VALIDATION_FAILED","field":"id","message":"Patient identifier is required.","requestId":"..."}
```

### Invalid age

```bash
curl -X POST "$BASE/single?state=MA&age=five" -H "Content-Type: application/json" -d "$PATIENT"
```
```json
{"httpStatus":400,"errorCode":"INVALID_PARAMETER_TYPE","field":"age","expectedType":"integer","message":"Age must be a whole number.","requestId":"..."}
```

`"age":"five"` in a `/batch` body gets the same response.

### Malformed JSON

```bash
curl -X POST "$BASE/single?state=MA" -H "Content-Type: application/json" -d '{invalid-json'
```
```json
{"httpStatus":400,"errorCode":"MALFORMED_REQUEST","message":"We couldn't read the submitted record. Please check its format.","requestId":"...","details":[{"errorCode":"MALFORMED_REQUEST","message":"The record is not valid JSON (line 1, column 3)."}]}
```

The top-level `message` is always the same. The `details` entry is one of these, with the line and column added when the parser reports them:

| Explanation | When |
|---|---|
| The record is not valid JSON (line L, column C). | The JSON syntax is broken. The position is where the parser stopped, which can be just past the mistake. |
| The record is not in the expected format (line L, column C). | The JSON is valid, but a value has the wrong type, such as text in `cvxCode`. |
| No record was submitted. | The request has no body. |

It never quotes the submitted content or any server internals.

### Unsupported Content-Type

```bash
curl -X POST "$BASE/single?state=MA" -H "Content-Type: text/plain" -d "$PATIENT"
```
```json
{"httpStatus":415,"errorCode":"UNSUPPORTED_MEDIA_TYPE","message":"Please submit your record in JSON format.","requestId":"..."}
```

### Unsupported state

```bash
curl -X POST "$BASE/single?state=ZZ&schoolYear=K-6" -H "Content-Type: application/json" -d "$PATIENT"
```
```json
{"httpStatus":400,"errorCode":"UNSUPPORTED_STATE","field":"state","rejectedValue":"ZZ","message":"Validation is not currently supported for the selected state.","requestId":"..."}
```

`rejectedValue` is cut to 10 characters, so `state=ABCDEFGHIJKLMNOP` comes back as `"ABCDEFGHIJ"`. A broken character in the value comes back as `?`.

On `/batch` this fails the whole request, and no `results` come back.

### Several fields failing

```bash
curl -X POST "$BASE/batch" -H "Content-Type: application/json" -d '{}'
```
```json
{
  "httpStatus": 400,
  "errorCode": "VALIDATION_FAILED",
  "field": "patients",
  "message": "At least one patient record is required.",
  "requestId": "...",
  "details": [
    {"field":"patients","errorCode":"VALIDATION_FAILED","message":"At least one patient record is required."},
    {"field":"responseMode","errorCode":"VALIDATION_FAILED","message":"Response mode is required."},
    {"field":"state","errorCode":"VALIDATION_FAILED","message":"Please select a state."}
  ]
}
```

### Wrong method or address

```bash
curl "$BASE/single"
```
```json
{"httpStatus":405,"errorCode":"METHOD_NOT_ALLOWED","message":"This action isn't available at this address.","requestId":"..."}
```

```bash
curl "http://localhost:8080/api/v1/no-such-path"
```
```json
{"httpStatus":404,"errorCode":"NOT_FOUND","message":"We couldn't find what you were looking for. Please check the address.","requestId":"..."}
```

### Vaccine lookups

```bash
curl "http://localhost:8080/api/v1/vaccines/cvx/abc"
```
```json
{"httpStatus":400,"errorCode":"INVALID_PARAMETER_TYPE","field":"cvxCode","expectedType":"integer","message":"CVX code must be a whole number.","requestId":"..."}
```

```bash
curl "http://localhost:8080/api/v1/vaccines/search"
```
```json
{"httpStatus":400,"errorCode":"MISSING_PARAMETER","field":"q","message":"Please enter a search term.","requestId":"..."}
```

**One exception to the shape:** looking up a well-formed CVX code or group that doesn't exist, such as `/vaccines/cvx/999999`, still returns a 404 with an empty body.

### Unexpected server error

```json
{"httpStatus":500,"errorCode":"INTERNAL_ERROR","message":"Something went wrong on our side. Please try again, and quote this request ID if it continues.","requestId":"..."}
```

The body never contains exception text. The full detail is in the server log under the same request id.

## Limits

Errors raised before a request reaches the application, such as an invalid URL rejected by the web server, can use a different shape.

## Records that can't be used

These are clinical results, not errors: the response is a 200 with `status: "undetermined"`.

A record is set aside when it has:

- no usable vaccine: neither a `vaccineCode` nor a `cvxCode` that resolves to a vaccine group
- a date that is present but can't be read, such as `03/01/2019`
- nothing at all (`null` in the `immunization` list)

Each one adds a warning that starts with the customer message and ends with the record it refers to. The usable records are still evaluated and counted in `metadata`.

**Any set-aside record makes the overall result `undetermined`, even if the patient would otherwise be `valid` or `invalid`,** because the unreadable record could be the missing dose.

```bash
curl -X POST "$BASE/single?state=MA&schoolYear=K-6" -H "Content-Type: application/json" \
  -d '{"id":"test-patient-001","birthDate":"2019-01-01","immunization":[{"cvxCode":99999,"occurrenceDateTime":"2020-01-01"}]}'
```
```json
{
  "status": "undetermined",
  "valid": false,
  "message": "Cannot determine compliance: 1 record(s) could not be read",
  "warnings": ["We couldn't identify one of the vaccines. Please check the vaccine information. [immunization[0], cvxCode 99999]"],
  "metadata": {"state":"MA","schoolYear":"K-6","totalRequirements":5,"satisfiedRequirements":0,"unsatisfiedRequirements":5,"undeterminedRequirements":0,"validatorVersion":"3.1.0"},
  "id": "test-patient-001"
}
```

| Problem | Warning |
|---|---|
| Unknown CVX code | We couldn't identify one of the vaccines. Please check the vaccine information. [immunization[0], cvxCode 99999] |
| No vaccine code | We couldn't identify one of the vaccines. Please check the vaccine information. [immunization[0], no vaccine code] |
| `null` record | We couldn't identify one of the vaccines. Please check the vaccine information. [immunization[0], empty record] |
| Unreadable date | Please provide or correct the vaccination date. [immunization[0], occurrenceDateTime 03/01/2019] |

The text after the first sentence comes from the client, cut to 40 characters, so escape it before displaying it. In detailed mode, `undeterminedConditions` also has one entry per record, with the sentence in `reason` and the bracketed part in `details`.

A free-text `vaccineCode` such as `XYZ` is not set aside. It's a code the rules don't ask for, so it's ignored as before.

A `null` entry in `exceptions`, or one with no `vaccineCode`, is ignored with a warning and does not change the result:

```
We couldn't read one of the vaccine exceptions, so it was ignored. [exceptions[0]]
```

### Missing vaccination date

A missing or blank date is an error rather than a clinical result:

```bash
curl -X POST "$BASE/single?state=MA&schoolYear=K-6" -H "Content-Type: application/json" \
  -d '{"id":"test-patient-001","immunization":[{"vaccineCode":"DTaP","occurrenceDateTime":"2019-03-01"},{"vaccineCode":"DTaP"}]}'
```
```json
{"httpStatus":400,"errorCode":"VALIDATION_FAILED","field":"immunization[1].occurrenceDateTime","message":"Please provide or correct the vaccination date.","requestId":"..."}
```

If several dates are missing, `details` lists them all.

## Batch validation

One bad patient doesn't fail the others. **The response is a 200 whenever the request itself is usable, even if every record fails,** so check `failedCount`.

Problems with the request as a whole are still a 400 in the error shape, with no `results`: missing or unsupported state, missing or empty `patients`, missing `responseMode`.

Results stay in the order they were sent. A failed record has `error` and no `status` or `valid`. `id` is left out when the patient had none.

```json
{
  "results": [
    {"status":"valid","valid":true,"message":"Patient meets all immunization requirements","metadata":{"...":"..."},"id":"batch-good-1"},
    {"status":"undetermined","valid":false,"message":"Cannot determine compliance: 1 record(s) could not be read","warnings":["Please provide or correct the vaccination date. [immunization[16], occurrenceDateTime 03/01/2019]"],"metadata":{"...":"..."},"id":"batch-bad-date"},
    {"error":{"field":"id","errorCode":"VALIDATION_FAILED","message":"Patient identifier is required."},"id":"   "},
    {"status":"invalid","valid":false,"message":"Patient does not meet requirements: 1 requirement(s) not satisfied","metadata":{"...":"..."},"id":"batch-good-2"}
  ],
  "message": "Some records could not be processed. Please review the affected records.",
  "failedCount": 1
}
```

`message` and `failedCount` appear only when at least one record failed. An `undetermined` result is a clinical result, so it doesn't count as failed.

| Item `errorCode` | When | Item message |
|---|---|---|
| `VALIDATION_FAILED` | Blank or missing id, a missing date, or a `null` patient | The field's message, such as "Patient identifier is required." Only the first failing field is reported. |
| `RECORD_PROCESSING_FAILED` | The record crashed processing | This record could not be processed. Please review it. |

## What these changes replaced

| Scenario | Behavior before |
|---|---|
| Missing vaccination date | HTTP 500 only when a vaccine has a dated and an undated dose. A lone undated or unreadable dose returned 200 and counted toward the total, so the result could be `valid`. |
| Unknown vaccine code | HTTP 500 |
| Invalid patient in a batch | HTTP 500 for the whole batch only if a record crashed. A blank id was not checked and got a normal result. |
