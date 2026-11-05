#!/usr/bin/env bash
# Sends each API error scenario to a running validator and checks the HTTP status and errorCode.
# Exits non-zero if any scenario fails. All patient data is made up.
#
# Usage:  bash scripts/error-smoke-test.sh [BASE_URL]    (default http://localhost:8080)
#
# Not covered: the generic 500 (nothing outside the app can force it safely).
# ApiErrorUnexpectedFailureTest covers that one.

# Tolerate Windows line endings if Git checked this file out with CRLF.
(set -o igncr) 2>/dev/null && set -o igncr # keeps this comment from breaking on a stray CR

BASE_URL="${1:-${BASE_URL:-http://localhost:8080}}"
BASE_URL="${BASE_URL%/}"
SINGLE="$BASE_URL/api/v1/validate/single"
BATCH="$BASE_URL/api/v1/validate/batch"
JSON="Content-Type: application/json"

PATIENT='{"id":"smoke-patient-001","birthDate":"2019-01-01","immunization":[]}'
NO_ID='{"birthDate":"2019-01-01","immunization":[]}'

failures=0
total=0

printf '%-44s | %-8s | %-6s | %-24s | %-24s | %s\n' \
    "scenario" "expected" "actual" "expected errorCode" "actual errorCode" "result"

# check <scenario> <expected HTTP> <expected errorCode, or - for none> <curl args...>
check() {
    local scenario="$1" expected_http="$2" expected_code="$3"
    shift 3

    local response actual_http body actual_code result
    response=$(curl -s -m 30 -w '\n%{http_code}' "$@")
    actual_http=$(printf '%s\n' "$response" | tail -n 1)
    body=$(printf '%s\n' "$response" | sed '$d')
    # First errorCode only: the "details" list repeats the key.
    actual_code=$(printf '%s' "$body" | grep -o '"errorCode":"[A-Z_]*"' | head -n 1 | sed 's/.*:"\(.*\)"/\1/')
    actual_code="${actual_code:--}"

    total=$((total + 1))
    if [ "$actual_http" = "$expected_http" ] && [ "$actual_code" = "$expected_code" ]; then
        result="PASS"
    else
        result="FAIL"
        failures=$((failures + 1))
    fi
    printf '%-44s | %-8s | %-6s | %-24s | %-24s | %s\n' \
        "$scenario" "$expected_http" "$actual_http" "$expected_code" "$actual_code" "$result"
}

# check_header <scenario> <header value that must come back> <curl args...>
check_header() {
    local scenario="$1" expected="$2"
    shift 2

    local actual result
    actual=$(curl -s -m 30 -o /dev/null -D - "$@" | tr -d '\r' \
        | sed -n 's/^[Xx]-[Rr]equest-[Ii][Dd]: *//p' | head -n 1)

    total=$((total + 1))
    if [ "$actual" = "$expected" ]; then
        result="PASS"
    else
        result="FAIL"
        failures=$((failures + 1))
    fi
    printf '%-44s | %-8s | %-6s | %-24s | %-24s | %s\n' \
        "$scenario" "-" "-" "$expected" "${actual:--}" "$result"
}

# check_body <scenario> <expected HTTP> <short label> <regex the body must match> <curl args...>
check_body() {
    local scenario="$1" expected_http="$2" label="$3" pattern="$4"
    shift 4

    local response actual_http body found result
    response=$(curl -s -m 30 -w '\n%{http_code}' "$@")
    actual_http=$(printf '%s\n' "$response" | tail -n 1)
    body=$(printf '%s\n' "$response" | sed '$d')
    if printf '%s' "$body" | grep -Eq "$pattern"; then found="found"; else found="missing"; fi

    total=$((total + 1))
    if [ "$actual_http" = "$expected_http" ] && [ "$found" = "found" ]; then
        result="PASS"
    else
        result="FAIL"
        failures=$((failures + 1))
    fi
    printf '%-44s | %-8s | %-6s | %-24s | %-24s | %s\n' \
        "$scenario" "$expected_http" "$actual_http" "$label" "$found" "$result"
}

# check_absent <scenario> <expected HTTP> <text the body must not contain> <curl args...>
check_absent() {
    local scenario="$1" expected_http="$2" text="$3"
    shift 3

    local response actual_http body found result
    response=$(curl -s -m 30 -w '\n%{http_code}' "$@")
    actual_http=$(printf '%s\n' "$response" | tail -n 1)
    body=$(printf '%s\n' "$response" | sed '$d')
    if printf '%s' "$body" | grep -Fq "$text"; then found="present"; else found="absent"; fi

    total=$((total + 1))
    if [ "$actual_http" = "$expected_http" ] && [ "$found" = "absent" ]; then
        result="PASS"
    else
        result="FAIL"
        failures=$((failures + 1))
    fi
    printf '%-44s | %-8s | %-6s | %-24s | %-24s | %s\n' \
        "$scenario" "$expected_http" "$actual_http" "no $text" "$found" "$result"
}

# ─── Approved table ───────────────────────────────────────────────────────────

check "Row 1: /single, no state" 400 MISSING_PARAMETER \
    -X POST "$SINGLE" -H "$JSON" -d "$PATIENT"
check "Row 1: /single, empty state" 400 MISSING_PARAMETER \
    -X POST "$SINGLE?state=" -H "$JSON" -d "$PATIENT"
check "Row 1: /batch, no state" 400 VALIDATION_FAILED \
    -X POST "$BATCH" -H "$JSON" -d "{\"responseMode\":\"simple\",\"patients\":[$PATIENT]}"
check "Row 2: /single, no patient id" 400 VALIDATION_FAILED \
    -X POST "$SINGLE?state=MA&schoolYear=K-6" -H "$JSON" -d "$NO_ID"
check "Row 3: /single, age=five" 400 INVALID_PARAMETER_TYPE \
    -X POST "$SINGLE?state=MA&age=five" -H "$JSON" -d "$PATIENT"
check "Row 3: /batch, age is text" 400 INVALID_PARAMETER_TYPE \
    -X POST "$BATCH" -H "$JSON" -d "{\"state\":\"MA\",\"age\":\"five\",\"responseMode\":\"simple\",\"patients\":[$PATIENT]}"
check "Row 4: malformed JSON" 400 MALFORMED_REQUEST \
    -X POST "$SINGLE?state=MA" -H "$JSON" -d '{invalid-json'
check "Row 5: text/plain body" 415 UNSUPPORTED_MEDIA_TYPE \
    -X POST "$SINGLE?state=MA" -H "Content-Type: text/plain" -d "$PATIENT"
check "Row 8: /single, unsupported state" 400 UNSUPPORTED_STATE \
    -X POST "$SINGLE?state=ZZ&schoolYear=K-6" -H "$JSON" -d "$PATIENT"
check "Row 8: /single, lowercase ma is accepted" 200 - \
    -X POST "$SINGLE?state=ma&schoolYear=K-6" -H "$JSON" -d "$PATIENT"
check "Row 8: /batch, unsupported state" 400 UNSUPPORTED_STATE \
    -X POST "$BATCH" -H "$JSON" -d "{\"state\":\"ZZ\",\"responseMode\":\"simple\",\"patients\":[$PATIENT]}"

# ─── Extra fields on rows 3, 4 and 8 ──────────────────────────────────────────

check_body "Row 3: /single, expectedType" 400 "expectedType=integer" '"expectedType":"integer"' \
    -X POST "$SINGLE?state=MA&age=five" -H "$JSON" -d "$PATIENT"
check_body "Row 3: /batch, expectedType" 400 "expectedType=integer" '"expectedType":"integer"' \
    -X POST "$BATCH" -H "$JSON" -d "{\"state\":\"MA\",\"age\":\"five\",\"responseMode\":\"simple\",\"patients\":[$PATIENT]}"
check_body "Row 3: /vaccines/cvx/abc, expectedType" 400 "expectedType=integer" '"expectedType":"integer"' \
    "$BASE_URL/api/v1/vaccines/cvx/abc"
check_body "Row 4: explanation with line and column" 400 "line and column" \
    '"details":\[\{"errorCode":"MALFORMED_REQUEST","message":"The record is not valid JSON \(line 1, column [0-9]+\)\."\}\]' \
    -X POST "$SINGLE?state=MA" -H "$JSON" -d '{invalid-json'
check_body "Row 4: no body" 400 "no-record phrase" '"message":"No record was submitted\."' \
    -X POST "$SINGLE?state=MA" -H "$JSON"
check_absent "Row 4: submitted content not echoed" 400 "MADE-UP-MARKER" \
    -X POST "$SINGLE?state=MA" -H "$JSON" -d '{"id":"MADE-UP-MARKER", broken'
check_body "Row 8: rejectedValue" 400 "rejectedValue=ZZ" '"rejectedValue":"ZZ"' \
    -X POST "$SINGLE?state=ZZ&schoolYear=K-6" -H "$JSON" -d "$PATIENT"
check_body "Row 8: rejectedValue cut to 10 characters" 400 "rejectedValue=ABCDEFGHIJ" '"rejectedValue":"ABCDEFGHIJ"' \
    -X POST "$SINGLE?state=ABCDEFGHIJKLMNOP&schoolYear=K-6" -H "$JSON" -d "$PATIENT"
check_body "Row 8: /batch, rejectedValue" 400 "rejectedValue=ZZ" '"rejectedValue":"ZZ"' \
    -X POST "$BATCH" -H "$JSON" -d "{\"state\":\"ZZ\",\"responseMode\":\"simple\",\"patients\":[$PATIENT]}"

# ─── Rows 6 and 7: bad records ────────────────────────────────────────────────

dose() { printf '{"vaccineCode":"%s","occurrenceDateTime":"%s"}' "$1" "$2"; }
record() { printf '{"id":"%s","birthDate":"2019-01-01","immunization":[%s]}' "$1" "$2"; }

# Everything MA requires for K-6, for a child born 2019-01-01, apart from DTaP.
OTHERS="$(dose Polio 2019-03-01),$(dose Polio 2019-05-01),$(dose Polio 2019-07-01),$(dose Polio 2023-02-01)"
OTHERS="$OTHERS,$(dose HepB 2019-01-02),$(dose HepB 2019-03-01),$(dose HepB 2019-07-01)"
OTHERS="$OTHERS,$(dose MMR 2020-02-01),$(dose MMR 2023-02-01),$(dose Varicella 2020-02-01),$(dose Varicella 2023-02-01)"
VALID_K6="$(dose DTaP 2019-03-01),$(dose DTaP 2019-05-01),$(dose DTaP 2019-07-01),$(dose DTaP 2020-04-01),$(dose DTaP 2023-02-01),$OTHERS"
INVALID_K6="$(dose DTaP 2019-03-01),$(dose DTaP 2019-05-01),$(dose DTaP 2019-07-01),$(dose DTaP 2022-11-01),$OTHERS"
K6="$SINGLE?state=MA&schoolYear=K-6"

UNKNOWN="We couldn't identify one of the vaccines\. Please check the vaccine information\."
BAD_DATE="Please provide or correct the vaccination date\."

check_body "Regression: complete record stays valid" 200 "status=valid" '"status":"valid"' \
    -X POST "$K6" -H "$JSON" -d "$(record smoke-valid "$VALID_K6")"
check_body "Regression: early 4th DTaP stays invalid" 200 "status=invalid" '"status":"invalid"' \
    -X POST "$K6" -H "$JSON" -d "$(record smoke-invalid "$INVALID_K6")"
check_body "Row 7: unknown CVX" 200 "warning, undetermined" \
    "\"status\":\"undetermined\".*$UNKNOWN \[immunization\[0\], cvxCode 99999\]" \
    -X POST "$K6" -H "$JSON" -d "$(record smoke-cvx '{"cvxCode":99999,"occurrenceDateTime":"2020-01-01"}')"
check_body "Row 7: no vaccine code at all" 200 "warning, undetermined" \
    "\"status\":\"undetermined\".*$UNKNOWN \[immunization\[0\], no vaccine code\]" \
    -X POST "$K6" -H "$JSON" -d "$(record smoke-nocode '{"occurrenceDateTime":"2020-01-01"}')"
check_body "Row 7: null in immunization list" 200 "warning, undetermined" \
    "\"status\":\"undetermined\".*$UNKNOWN \[immunization\[0\], empty record\]" \
    -X POST "$K6" -H "$JSON" -d "$(record smoke-null null)"
check_body "Row 6: unreadable date" 200 "warning, undetermined" \
    "\"status\":\"undetermined\".*$BAD_DATE \[immunization\[0\], occurrenceDateTime 03/01/2019\]" \
    -X POST "$K6" -H "$JSON" -d "$(record smoke-date "$(dose DTaP 03/01/2019)")"
check_body "Row 6: unreadable date on a complete record" 200 "1 record not read" \
    'Cannot determine compliance: 1 record\(s\) could not be read' \
    -X POST "$K6" -H "$JSON" -d "$(record smoke-date2 "$VALID_K6,$(dose HepB bad-date)")"
check_body "Row 6: missing date on a required vaccine" 200 "warning, undetermined" \
    "\"status\":\"undetermined\".*$BAD_DATE \[immunization\[1\], no occurrenceDateTime\]" \
    -X POST "$K6" -H "$JSON" -d "$(record smoke-nodate "$(dose DTaP 2019-03-01),{\"vaccineCode\":\"DTaP\"}")"
check_body "Not required: flu and COVID-19 with bad or no dates" 200 "still valid" '"status":"valid"' \
    -X POST "$K6" -H "$JSON" -d "$(record smoke-flu "$VALID_K6,{\"cvxCode\":150,\"occurrenceDateTime\":\"10/01/2024\"},{\"cvxCode\":208}")"
check_body "Not required: CVX 998, no vaccine administered" 200 "still valid" '"status":"valid"' \
    -X POST "$K6" -H "$JSON" -d "$(record smoke-998 "$VALID_K6,{\"cvxCode\":998}")"
check_body "Exceptions: null entry is ignored" 200 "warning, still valid" \
    '"status":"valid".*vaccine exceptions, so it was ignored\. \[exceptions\[0\]\]' \
    -X POST "$K6" -H "$JSON" -d "{\"id\":\"smoke-exc\",\"birthDate\":\"2019-01-01\",\"immunization\":[$VALID_K6],\"exceptions\":[null]}"

# ─── Row 9: batch ─────────────────────────────────────────────────────────────

batch() { printf '{"state":"MA","schoolYear":"K-6","responseMode":"simple","patients":[%s]}' "$1"; }
SOME_FAILED='"message":"Some records could not be processed\. Please review the affected records\."'

MIXED="$(record batch-good-1 "$VALID_K6"),$(record batch-bad-date "$VALID_K6,$(dose HepB 03/01/2019)"),$(record '   ' "$VALID_K6"),$(record batch-good-2 "$INVALID_K6")"
check_body "Row 9: mixed, order and error item" 200 "4 results in order" \
    '"status":"valid".*"id":"batch-good-1".*"status":"undetermined".*"id":"batch-bad-date".*"error":\{[^}]*\},"id":"   ".*"status":"invalid".*"id":"batch-good-2"' \
    -X POST "$BATCH" -H "$JSON" -d "$(batch "$MIXED")"
check_body "Row 9: mixed, failedCount" 200 "failedCount=1" '"failedCount":1' \
    -X POST "$BATCH" -H "$JSON" -d "$(batch "$MIXED")"
check_body "Row 9: mixed, message" 200 "approved message" "$SOME_FAILED" \
    -X POST "$BATCH" -H "$JSON" -d "$(batch "$MIXED")"
check_body "Row 9: every record fails, still 200" 200 "failedCount=2" '"failedCount":2' \
    -X POST "$BATCH" -H "$JSON" -d "$(batch "$(record '' "$VALID_K6"),null")"
check_absent "Row 9: all good, no failedCount" 200 "failedCount" \
    -X POST "$BATCH" -H "$JSON" -d "$(batch "$(record batch-good-3 "$VALID_K6")")"
# The only record here fails, so "status" must not appear anywhere in the body.
check_absent "Row 9: error item has no status" 200 '"status"' \
    -X POST "$BATCH" -H "$JSON" -d "$(batch "$(record '' "$VALID_K6")")"
check_absent "Row 9: error item has no valid" 200 '"valid"' \
    -X POST "$BATCH" -H "$JSON" -d "$(batch "$(record '' "$VALID_K6")")"

# ─── Rest of the contract ─────────────────────────────────────────────────────

check "/batch, empty patient list" 400 VALIDATION_FAILED \
    -X POST "$BATCH" -H "$JSON" -d '{"state":"MA","responseMode":"simple","patients":[]}'
check "GET on a POST-only path" 405 METHOD_NOT_ALLOWED "$SINGLE"
check "Unknown path" 404 NOT_FOUND "$BASE_URL/api/v1/no-such-path"
check "/vaccines/cvx/abc" 400 INVALID_PARAMETER_TYPE "$BASE_URL/api/v1/vaccines/cvx/abc"
check "/vaccines/search, no query" 400 MISSING_PARAMETER "$BASE_URL/api/v1/vaccines/search"

# ─── Must not have changed ────────────────────────────────────────────────────

check "MA, unknown school year: still 200" 200 - \
    -X POST "$SINGLE?state=MA&schoolYear=no-such-year" -H "$JSON" -d "$PATIENT"
check "MA, normal record: still 200" 200 - \
    -X POST "$SINGLE?state=MA&schoolYear=K-6" -H "$JSON" -d "$PATIENT"
check "/vaccines/cvx/999999: still an empty 404" 404 - "$BASE_URL/api/v1/vaccines/cvx/999999"

# ─── Request id ───────────────────────────────────────────────────────────────

check_header "Valid X-Request-Id is echoed" "smoke-id_1.2" \
    -H "X-Request-Id: smoke-id_1.2" "$BASE_URL/api/v1/validate/health"
# An invalid id must not come back. The replacement is random, so check it directly.
replaced=$(curl -s -m 30 -o /dev/null -D - -H "X-Request-Id: bad id;" "$BASE_URL/api/v1/validate/health" \
    | tr -d '\r' | sed -n 's/^[Xx]-[Rr]equest-[Ii][Dd]: *//p' | head -n 1)
total=$((total + 1))
if [ -n "$replaced" ] && [ "$replaced" != "bad id;" ]; then
    result="PASS"
else
    result="FAIL"
    failures=$((failures + 1))
fi
printf '%-44s | %-8s | %-6s | %-24s | %-24s | %s\n' \
    "Invalid X-Request-Id is replaced" "-" "-" "(a generated id)" "${replaced:0:24}" "$result"

echo
echo "$((total - failures)) of $total passed against $BASE_URL"
[ "$failures" -eq 0 ]
