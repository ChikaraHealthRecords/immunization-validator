package com.immunization.validator.service;

import com.immunization.validator.model.VaccineFamilyAlias;
import com.immunization.validator.repository.VaccineFamilyAliasRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

/**
 * Maps raw vaccine family labels from LlamaExtract output (e.g. "DTAP/DT",
 * "Pneumopcv") to the canonical vaccine group names used by RequirementsService
 * (e.g. "DTaP", "PneumoPCV"), via the vaccine_family_aliases lookup table.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class VaccineFamilyMappingService {

    private final VaccineFamilyAliasRepository vaccineFamilyAliasRepository;

    /**
     * Look up the canonical vaccine group name for a raw label.
     * Unmapped labels are passed through unchanged so they simply won't match
     * any requirement, rather than dropping the record or throwing.
     */
    @Cacheable("vaccineFamilyAliases")
    public String mapToCanonicalGroup(String rawLabel) {
        if (rawLabel == null || rawLabel.isBlank()) {
            return rawLabel;
        }

        return vaccineFamilyAliasRepository.findByRawLabelIgnoreCase(rawLabel.trim())
                .map(VaccineFamilyAlias::getCanonicalGroup)
                .orElseGet(() -> {
                    log.warn("No canonical vaccine group mapping found for raw label: '{}'", rawLabel);
                    return rawLabel;
                });
    }
}
