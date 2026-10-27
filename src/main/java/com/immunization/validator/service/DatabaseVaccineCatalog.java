package com.immunization.validator.service;

import com.immunization.validator.model.Vaccine;
import com.immunization.validator.repository.VaccineRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Vaccine catalog backed by the vaccines table.
 * Active only when immunization.vaccines.source = database
 * (the local, docker and prod profiles set this, together with the datasource settings).
 */
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "immunization.vaccines.source", havingValue = "database")
public class DatabaseVaccineCatalog implements VaccineCatalog {

    private final VaccineRepository vaccineRepository;

    @Override
    public List<Vaccine> findByCvxCode(Integer cvxCode) {
        return cvxCode == null ? List.of() : vaccineRepository.findByCvxCode(cvxCode);
    }

    @Override
    public List<Vaccine> findByGroupName(String groupName) {
        return groupName == null ? List.of() : vaccineRepository.findByVaccineGroupNameIgnoreCase(groupName);
    }

    @Override
    public List<Vaccine> findAll() {
        return vaccineRepository.findAll();
    }
}
