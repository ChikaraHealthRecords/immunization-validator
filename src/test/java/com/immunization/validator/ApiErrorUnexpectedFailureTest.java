package com.immunization.validator;

import com.immunization.validator.model.ValidationResponse;
import com.immunization.validator.service.ValidationService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** The generic 500, forced with a ValidationService that always throws. Runs with no profile and no database. */
@SpringBootTest
@AutoConfigureMockMvc
@ExtendWith(OutputCaptureExtension.class)
@DisplayName("API error contract: unexpected failure")
class ApiErrorUnexpectedFailureTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ValidationService validationService;

    @Test
    @DisplayName("Generic 500: friendly body with no internals, full stack trace in the log")
    void unexpectedFailure(CapturedOutput output) throws Exception {
        when(validationService.validate(any(), any(), any(), any(), org.mockito.ArgumentMatchers.anyBoolean()))
                .thenThrow(new IllegalStateException("made-up internal failure text"));

        mockMvc.perform(post("/api/v1/validate/single")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"id\":\"test-patient-200\",\"immunization\":[]}")
                        .param("state", "MA").param("schoolYear", "K-6")
                        .header("X-Request-Id", "generic-500-test"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.httpStatus").value(500))
                .andExpect(jsonPath("$.errorCode").value("INTERNAL_ERROR"))
                .andExpect(jsonPath("$.field").doesNotExist())
                .andExpect(jsonPath("$.message").value(
                        "Something went wrong on our side. Please try again, and quote this request ID if it continues."))
                .andExpect(jsonPath("$.requestId").value("generic-500-test"))
                .andExpect(header().string("X-Request-Id", "generic-500-test"))
                .andExpect(jsonPath("$.status").doesNotExist())
                .andExpect(jsonPath("$.valid").doesNotExist())
                .andExpect(content().string(not(containsString("made-up internal failure text"))))
                .andExpect(content().string(not(containsString("Exception"))))
                .andExpect(content().string(not(containsString("at com."))))
                .andExpect(content().string(not(containsString("java."))));

        assertThat(output.getAll())
                .contains("[generic-500-test] ERROR")
                .contains("java.lang.IllegalStateException: made-up internal failure text")
                .contains("at com.immunization.validator");
    }

    @Test
    @DisplayName("Row 9: a crash on one batch patient fails only that patient, with no internals in the body")
    void batchCrashFailsOnlyThatPatient(CapturedOutput output) throws Exception {
        when(validationService.validate(any(), any(), any(), any(), org.mockito.ArgumentMatchers.anyBoolean()))
                .thenThrow(new IllegalStateException("made-up internal failure text"))
                .thenReturn(ValidationResponse.builder().patientId("test-patient-202").build());

        mockMvc.perform(post("/api/v1/validate/batch")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"state\":\"MA\",\"schoolYear\":\"K-6\",\"responseMode\":\"simple\",\"patients\":["
                                + "{\"id\":\"test-patient-201\",\"immunization\":[]},"
                                + "{\"id\":\"test-patient-202\",\"immunization\":[]}]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.failedCount").value(1))
                .andExpect(jsonPath("$.results[0].id").value("test-patient-201"))
                .andExpect(jsonPath("$.results[0].status").doesNotExist())
                .andExpect(jsonPath("$.results[0].error.errorCode").value("RECORD_PROCESSING_FAILED"))
                .andExpect(jsonPath("$.results[0].error.message").value(
                        "This record could not be processed. Please review it."))
                .andExpect(jsonPath("$.results[1].id").value("test-patient-202"))
                .andExpect(jsonPath("$.results[1].error").doesNotExist())
                .andExpect(content().string(not(containsString("made-up internal failure text"))))
                .andExpect(content().string(not(containsString("Exception"))));

        assertThat(output.getAll())
                .contains("java.lang.IllegalStateException: made-up internal failure text")
                .contains("1 FAILED")
                // The full id must stay out of the logs: it's masked to the first and last four characters.
                .doesNotContain("Patient ID: test-patient-201");
    }
}
