package com.immunization.validator.controller;

import com.immunization.validator.error.ApiRequestException;
import com.immunization.validator.error.ErrorCode;
import com.immunization.validator.error.ErrorMessages;
import com.immunization.validator.service.RequirementsService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Locale;

/**
 * Request handling shared by the validation controllers: the state parameter, and the
 * privacy-safe forms of client values used in log lines.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RequestParameters {

    private final RequirementsService requirementsService;

    /**
     * The state query parameter of /single and /fhir. state= (present but empty) is the same
     * mistake as leaving it out.
     *
     * @return the state code as the rule files spell it
     */
    public String requireState(String state) {
        if (state.isBlank()) {
            throw new ApiRequestException(ErrorCode.MISSING_PARAMETER, "state", ErrorMessages.SELECT_STATE);
        }
        return requireSupportedState(state);
    }

    /**
     * Rejects a state with no rule file. Case and surrounding spaces don't matter, so " ma" is "MA".
     * Runs before anything else logs the state, because the value comes straight from the client.
     *
     * @return the state code as the rule files spell it
     */
    public String requireSupportedState(String state) {
        String code = state.trim().toUpperCase(Locale.ROOT);
        if (!requirementsService.hasRequirements(code)) {
            log.warn("Unsupported state rejected: {}", safeForLog(state));
            throw new ApiRequestException(ErrorCode.UNSUPPORTED_STATE, "state", ErrorMessages.UNSUPPORTED_STATE, state);
        }
        return code;
    }

    /** Keeps a client-supplied value from forging log lines: no line breaks or odd characters, short. */
    public static String safeForLog(String value) {
        String cleaned = value.replaceAll("[^A-Za-z0-9._-]", "_");
        return cleaned.length() <= 32 ? cleaned : cleaned.substring(0, 32) + "...";
    }

    /** First 4 and last 4 characters; ids of 8 characters or fewer are fully masked. */
    public static String maskPatientId(String patientId) {
        if (patientId == null || patientId.length() <= 8) return "****";
        return patientId.substring(0, 4) + "****" + patientId.substring(patientId.length() - 4);
    }

    public static String clientIp(HttpServletRequest request) {
        String xff = request.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isEmpty()) return xff.split(",")[0].trim();
        String xri = request.getHeader("X-Real-IP");
        if (xri != null && !xri.isEmpty()) return xri;
        return request.getRemoteAddr();
    }
}
