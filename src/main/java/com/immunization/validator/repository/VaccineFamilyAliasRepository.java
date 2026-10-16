package com.immunization.validator.repository;

import com.immunization.validator.model.VaccineFamilyAlias;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface VaccineFamilyAliasRepository extends JpaRepository<VaccineFamilyAlias, Integer> {
    Optional<VaccineFamilyAlias> findByRawLabelIgnoreCase(String rawLabel);
}
