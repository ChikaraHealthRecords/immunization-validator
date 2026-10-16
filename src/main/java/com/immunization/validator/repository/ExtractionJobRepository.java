package com.immunization.validator.repository;

import com.immunization.validator.model.ExtractionJob;
import com.immunization.validator.model.ExtractionJobStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

@Repository
public interface ExtractionJobRepository extends JpaRepository<ExtractionJob, UUID> {
    List<ExtractionJob> findByStatusIn(Collection<ExtractionJobStatus> statuses);
}
