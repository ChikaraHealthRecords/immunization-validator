package com.immunization.validator;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Starts the whole application with no profile, so it runs in file mode:
 * no Postgres, no datasource. If this passes on a machine with no database, the CI problem is gone.
 */
@SpringBootTest
@AutoConfigureMockMvc
@DisplayName("VaccineController in file mode (no database)")
class VaccineControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("CVX 110 returns three vaccine groups")
    void comboVaccine() throws Exception {
        mockMvc.perform(get("/api/v1/vaccines/cvx/110"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3));
    }

    @Test
    @DisplayName("unknown CVX code returns 404")
    void unknownCode() throws Exception {
        mockMvc.perform(get("/api/v1/vaccines/cvx/999999"))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("group lookup works")
    void group() throws Exception {
        mockMvc.perform(get("/api/v1/vaccines/group/dtap"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("stats count every row")
    void stats() throws Exception {
        mockMvc.perform(get("/api/v1/vaccines/stats"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalRecords").value(256));
    }

    @Test
    @DisplayName("actuator health is UP with no database behind it")
    void health() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }
}
