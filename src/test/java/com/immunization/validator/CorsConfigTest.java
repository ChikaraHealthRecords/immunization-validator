package com.immunization.validator;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Browser access is limited to the origins in immunization.cors.allowed-origins. */
@SpringBootTest(properties = "immunization.cors.allowed-origins=https://validator.example,https://other.example")
@AutoConfigureMockMvc
class CorsConfigTest {

    @Autowired
    private MockMvc mvc;

    private org.springframework.test.web.servlet.ResultActions preflight(String origin, String method) throws Exception {
        return mvc.perform(options("/api/v1/validate/single")
                .header(HttpHeaders.ORIGIN, origin)
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, method)
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, "content-type"));
    }

    @Test
    void allowsConfiguredOrigins() throws Exception {
        preflight("https://validator.example", "POST")
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "https://validator.example"))
                .andExpect(header().doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS));
        preflight("https://other.example", "POST").andExpect(status().isOk());
    }

    @Test
    void rejectsOtherOrigins() throws Exception {
        preflight("https://evil.example", "POST").andExpect(status().isForbidden());
        // The localhost defaults no longer apply once origins are configured.
        preflight("http://localhost:5173", "POST").andExpect(status().isForbidden());
    }

    @Test
    void allowsOnlyGetAndPost() throws Exception {
        preflight("https://validator.example", "DELETE").andExpect(status().isForbidden());
    }
}
