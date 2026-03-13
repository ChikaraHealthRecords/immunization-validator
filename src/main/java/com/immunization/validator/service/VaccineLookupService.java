package com.immunization.validator.service;

import com.immunization.validator.model.Vaccine;
import com.immunization.validator.repository.VaccineRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class VaccineLookupService {

    private final VaccineRepository vaccineRepository;

    /**
     * Look up vaccine group names for a given CVX code
     * Example: CVX 110 → ["DTAP", "HepB", "POLIO"]
     * Example: CVX 94 → ["MMR", "VARICELLA"]
     */
    public List<String> getVaccineGroupsForCvx(Integer cvxCode) {
        if (cvxCode == null) {
            log.warn("Null CVX code provided");
            return List.of();
        }

        List<Vaccine> vaccines = vaccineRepository.findByCvxCode(cvxCode);
        
        if (vaccines.isEmpty()) {
            log.warn("No vaccine groups found for CVX code: {}", cvxCode);
            return List.of();
        }

        List<String> groups = vaccines.stream()
                .map(Vaccine::getVaccineGroupName)
                .distinct()
                .collect(Collectors.toList());
        
        log.debug("CVX {} maps to groups: {}", cvxCode, groups);
        return groups;
    }
}