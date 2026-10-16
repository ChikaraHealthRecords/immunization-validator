package com.immunization.validator;

import com.immunization.validator.model.VaccineFamilyAlias;
import com.immunization.validator.repository.VaccineFamilyAliasRepository;
import com.immunization.validator.service.VaccineFamilyMappingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

class VaccineFamilyMappingServiceTest {

    @Mock
    private VaccineFamilyAliasRepository vaccineFamilyAliasRepository;

    private VaccineFamilyMappingService vaccineFamilyMappingService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        vaccineFamilyMappingService = new VaccineFamilyMappingService(vaccineFamilyAliasRepository);
    }

    @Test
    @DisplayName("Known raw label maps to canonical group")
    void testKnownLabel_MapsToCanonicalGroup() {
        when(vaccineFamilyAliasRepository.findByRawLabelIgnoreCase("DTAP/DT"))
                .thenReturn(Optional.of(VaccineFamilyAlias.builder()
                        .rawLabel("DTAP/DT").canonicalGroup("DTaP").build()));

        assertEquals("DTaP", vaccineFamilyMappingService.mapToCanonicalGroup("DTAP/DT"));
    }

    @Test
    @DisplayName("Lookup is case-insensitive via repository")
    void testLookup_IsCaseInsensitive() {
        when(vaccineFamilyAliasRepository.findByRawLabelIgnoreCase("pneumopcv"))
                .thenReturn(Optional.of(VaccineFamilyAlias.builder()
                        .rawLabel("Pneumopcv").canonicalGroup("PneumoPCV").build()));

        assertEquals("PneumoPCV", vaccineFamilyMappingService.mapToCanonicalGroup("pneumopcv"));
    }

    @Test
    @DisplayName("Unmapped label passes through unchanged instead of throwing")
    void testUnmappedLabel_PassesThroughUnchanged() {
        when(vaccineFamilyAliasRepository.findByRawLabelIgnoreCase("COVID-19 (Pre-10/23) Pfizer Vaccine Ped 5-11"))
                .thenReturn(Optional.empty());

        assertEquals("COVID-19 (Pre-10/23) Pfizer Vaccine Ped 5-11",
                vaccineFamilyMappingService.mapToCanonicalGroup("COVID-19 (Pre-10/23) Pfizer Vaccine Ped 5-11"));
    }

    @Test
    @DisplayName("Null/blank label returns unchanged without calling repository")
    void testBlankLabel_ReturnsUnchanged() {
        assertEquals(null, vaccineFamilyMappingService.mapToCanonicalGroup(null));
        assertEquals("", vaccineFamilyMappingService.mapToCanonicalGroup(""));
    }
}
