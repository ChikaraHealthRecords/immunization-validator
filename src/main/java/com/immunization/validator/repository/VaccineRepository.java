package com.immunization.validator.repository;

import com.immunization.validator.model.Vaccine;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface VaccineRepository extends JpaRepository<Vaccine, Integer> {
    List<Vaccine> findByCvxCode(Integer cvxCode);
    List<Vaccine> findByVaccineGroupNameIgnoreCase(String vaccineGroupName);
}