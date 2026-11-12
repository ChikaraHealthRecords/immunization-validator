#!/usr/bin/env bash
# Sends the made-up FHIR fixtures (src/test/resources/fhir/) to a running validator and checks the
# HTTP status and errorCode of each, plus the endpoint's parameter and content-type errors.
# Exits non-zero if any check fails. All patient data is made up.
#
# Usage:  bash scripts/fhir-smoke-test.sh [BASE_URL]    (default http://localhost:8080)

# Tolerate Windows line endings if Git checked this file out with CRLF.
(set -o igncr) 2>/dev/null && set -o igncr # keeps this comment from breaking on a stray CR

BASE_URL="${1:-${BASE_URL:-http://localhost:8080}}"
BASE_URL="${BASE_URL%/}"
FHIR="$BASE_URL/api/v1/validate/fhir"
K6="$FHIR?state=MA&schoolYear=K-6"
FX="$(cd "$(dirname "$0")/.." && pwd)/src/test/resources/fhir"
FHIR_JSON="Content-Type: application/fhir+json"
NDJSON="Content-Type: application/x-ndjson"

failures=0
total=0

printf '%-44s | %-8s | %-6s | %-26s | %-26s | %s\n' \
    "scenario" "expected" "actual" "expected" "actual" "result"

# record <scenario> <expected HTTP> <expected label> <actual HTTP> <actual label> <pass?>
record() {
    total=$((total + 1))
    local result="PASS"
    if [ "$6" != "yes" ]; then
        result="FAIL"
        failures=$((failures + 1))
    fi
    printf '%-44s | %-8s | %-6s | %-26s | %-26s | %s\n' "$1" "$2" "$4" "$3" "$5" "$result"
}

# send <curl args...>: sets http and body
send() {
    local response
    response=$(curl -s -m 30 -w '\n%{http_code}' "$@")
    http=$(printf '%s\n' "$response" | tail -n 1 | tr -d '\r')
    body=$(printf '%s\n' "$response" | sed '$d')
}

# check <scenario> <expected HTTP> <expected errorCode, or - for none> <curl args...>
check() {
    local scenario="$1" expected_http="$2" expected_code="$3"
    shift 3
    send "$@"
    # First errorCode only: the "details" list repeats the key.
    local code
    code=$(printf '%s' "$body" | grep -o '"errorCode":"[A-Z_]*"' | head -n 1 | sed 's/.*:"\(.*\)"/\1/')
    code="${code:--}"
    local ok=no
    [ "$http" = "$expected_http" ] && [ "$code" = "$expected_code" ] && ok=yes
    record "$scenario" "$expected_http" "$expected_code" "$http" "$code" "$ok"
}

# check_body <scenario> <expected HTTP> <label> <regex the body must match> <curl args...>
check_body() {
    local scenario="$1" expected_http="$2" label="$3" pattern="$4"
    shift 4
    send "$@"
    local found=missing ok=no
    printf '%s' "$body" | grep -Eq "$pattern" && found=found
    [ "$http" = "$expected_http" ] && [ "$found" = "found" ] && ok=yes
    record "$scenario" "$expected_http" "$label" "$http" "$found" "$ok"
}

# check_absent <scenario> <expected HTTP> <text the body must not contain> <curl args...>
check_absent() {
    local scenario="$1" expected_http="$2" text="$3"
    shift 3
    send "$@"
    local found=absent ok=no
    printf '%s' "$body" | grep -Fq "$text" && found=present
    [ "$http" = "$expected_http" ] && [ "$found" = "absent" ] && ok=yes
    record "$scenario" "$expected_http" "no $text" "$http" "$found" "$ok"
}

# fixture <name> <expected HTTP> <expected errorCode>: posts a fixture file byte for byte
fixture() {
    local name="$1" file type
    if [ -f "$FX/$name.ndjson" ]; then file="$FX/$name.ndjson"; type="$NDJSON"; else file="$FX/$name.json"; type="$FHIR_JSON"; fi
    check "$name" "$2" "$3" -X POST "$K6" -H "$type" --data-binary "@$file"
}

if [ ! -d "$FX" ]; then
    echo "Fixtures not found at $FX" >&2
    exit 2
fi

# ─── The kit's fixtures ───────────────────────────────────────────────────────

fixture 01-valid-collection-bundle        200 -
fixture 02-urn-uuid-searchset             200 -
fixture 03-status-filtering               200 -
fixture 04-partial-dates                  200 -
fixture 05-two-patients-and-outsider      200 -
fixture 06-unidentifiable-vaccines        200 -
fixture 07-subpotent-and-missing-status   200 -
fixture 08-immunization-only-bundle       200 -
fixture 09-error-no-patient-reference     400 VALIDATION_FAILED
fixture 10-lone-patient                   200 -
fixture 11-error-malformed-json           400 MALFORMED_REQUEST
fixture 12-epic-shaped-bulk               200 -
fixture 13-immunization-only-array        200 -
fixture 14-error-nothing-usable           400 VALIDATION_FAILED
fixture 15-error-ndjson-bad-line          400 MALFORMED_REQUEST
fixture 16-invalid-birthdate              200 -

# ─── What the fixtures must show ──────────────────────────────────────────────

check_body "03: not-done excluded" 200 "STATUS_NOT_DONE" '"reason":"STATUS_NOT_DONE"' \
    -X POST "$K6" -H "$FHIR_JSON" --data-binary "@$FX/03-status-filtering.json"
check_body "05: outsider dose unattributed" 200 "REFERS_TO_OTHER_PATIENT" '"unattributed":\[\{"resourceId":"imm-x1"' \
    -X POST "$K6" -H "$FHIR_JSON" --data-binary "@$FX/05-two-patients-and-outsider.json"
check_body "12: other resource types ignored" 200 "AllergyIntolerance:1" '"AllergyIntolerance":1' \
    -X POST "$K6" -H "$NDJSON" --data-binary "@$FX/12-epic-shaped-bulk.ndjson"
check_body "13: no Patient resource" 200 "NO_PATIENT_RESOURCE" '"code":"NO_PATIENT_RESOURCE"' \
    -X POST "$K6" -H "$FHIR_JSON" --data-binary "@$FX/13-immunization-only-array.json"
check_absent "13: no Patient resource, never valid" 200 '"status":"valid"' \
    -X POST "$K6" -H "$FHIR_JSON" --data-binary "@$FX/13-immunization-only-array.json"
check_body "16: impossible birth date withheld" 200 "BIRTHDATE_INVALID" '"code":"BIRTHDATE_INVALID"' \
    -X POST "$K6" -H "$FHIR_JSON" --data-binary "@$FX/16-invalid-birthdate.json"
check_absent "01: name not returned" 200 "Testcase" \
    -X POST "$K6" -H "$FHIR_JSON" --data-binary "@$FX/01-valid-collection-bundle.json"
check_absent "01: address not returned" 200 "Example Street" \
    -X POST "$K6" -H "$FHIR_JSON" --data-binary "@$FX/01-valid-collection-bundle.json"
check_absent "12: phone not returned" 200 "555-0100" \
    -X POST "$K6" -H "$NDJSON" --data-binary "@$FX/12-epic-shaped-bulk.ndjson"

# ─── Parameters and content types ─────────────────────────────────────────────

LONE="$FX/10-lone-patient.json"
check "No state" 400 MISSING_PARAMETER -X POST "$FHIR" -H "$FHIR_JSON" --data-binary "@$LONE"
check "Empty state" 400 MISSING_PARAMETER -X POST "$FHIR?state=" -H "$FHIR_JSON" --data-binary "@$LONE"
check "Unsupported state" 400 UNSUPPORTED_STATE -X POST "$FHIR?state=ZZ" -H "$FHIR_JSON" --data-binary "@$LONE"
check "Lowercase ma is accepted" 200 - -X POST "$FHIR?state=ma&schoolYear=K-6" -H "$FHIR_JSON" --data-binary "@$LONE"
check "age=five" 400 INVALID_PARAMETER_TYPE -X POST "$FHIR?state=MA&age=five" -H "$FHIR_JSON" --data-binary "@$LONE"
check "application/json accepted" 200 - -X POST "$K6" -H "Content-Type: application/json" --data-binary "@$LONE"
check "application/fhir+ndjson accepted" 200 - -X POST "$K6" -H "Content-Type: application/fhir+ndjson" \
    --data-binary "@$FX/12-epic-shaped-bulk.ndjson"
check "text/plain body" 415 UNSUPPORTED_MEDIA_TYPE -X POST "$K6" -H "Content-Type: text/plain" --data-binary "@$LONE"
check "No body" 400 MALFORMED_REQUEST -X POST "$K6" -H "$FHIR_JSON"
check "GET on the FHIR path" 405 METHOD_NOT_ALLOWED "$K6"
check_absent "Error has no status" 400 '"status"' -X POST "$FHIR?state=ZZ" -H "$FHIR_JSON" --data-binary "@$LONE"
check_body "Error has a requestId" 400 "requestId" '"requestId":"[^"]+"' \
    -X POST "$K6" -H "$FHIR_JSON" --data-binary "@$FX/11-error-malformed-json.json"

echo
echo "$((total - failures)) of $total passed against $BASE_URL"
[ "$failures" -eq 0 ]
