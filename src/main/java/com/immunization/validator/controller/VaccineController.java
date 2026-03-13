package com.immunization.validator.controller;

import com.immunization.validator.model.Vaccine;
import com.immunization.validator.repository.VaccineRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * REST API for vaccine database lookups.
 *
 * Provides endpoints to:
 * - Look up vaccines by CVX code
 * - Look up vaccines by vaccine group name
 * - Browse all vaccines
 * - Get vaccine statistics
 *
 * These are utility endpoints for debugging, frontend integration,
 * and administrative purposes. The validation flow uses
 * VaccineLookupService directly without calling these endpoints.
 *
 * @author Saakad
 * @since 2026-02-22
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/vaccines")
@RequiredArgsConstructor
public class VaccineController {

    private final VaccineRepository vaccineRepository;

    /**
     * Get all vaccines for a specific CVX code.
     *
     * Example: GET /api/v1/vaccines/cvx/110
     * Returns: All vaccine groups that CVX 110 satisfies (DTaP, HepB, Polio)
     *
     * Use case:
     * - Verify what a CVX code maps to
     * - Debugging combo vaccines
     * - Frontend CVX code validation
     */
    @GetMapping("/cvx/{cvxCode}")
    public ResponseEntity<List<Vaccine>> getVaccinesByCvxCode(@PathVariable Integer cvxCode) {
        log.info("Looking up CVX code: {}", cvxCode);
        List<Vaccine> vaccines = vaccineRepository.findByCvxCode(cvxCode);

        if (vaccines.isEmpty()) {
            log.warn("No vaccines found for CVX code: {}", cvxCode);
            return ResponseEntity.notFound().build();
        }

        log.info("Found {} vaccine group(s) for CVX {}: {}",
                vaccines.size(), cvxCode,
                vaccines.stream().map(Vaccine::getVaccineGroupName).collect(Collectors.toList()));
        return ResponseEntity.ok(vaccines);
    }

    /**
     * Get all vaccines for a vaccine group.
     *
     * Example: GET /api/v1/vaccines/group/DTAP
     * Returns: All CVX codes that map to DTaP group
     *
     * Use case:
     * - Find all CVX codes for a vaccine type
     * - Training and documentation
     * - Administrative review
     */
    @GetMapping("/group/{groupName}")
    public ResponseEntity<List<Vaccine>> getVaccinesByGroup(@PathVariable String groupName) {
        log.info("Looking up vaccine group: {}", groupName);
        List<Vaccine> vaccines = vaccineRepository.findByVaccineGroupNameIgnoreCase(groupName);

        if (vaccines.isEmpty()) {
            log.warn("No vaccines found for group: {}", groupName);
            return ResponseEntity.notFound().build();
        }

        log.info("Found {} vaccine(s) in group {}", vaccines.size(), groupName);
        return ResponseEntity.ok(vaccines);
    }

    /**
     * Get all vaccines in the database.
     *
     * Example: GET /api/v1/vaccines
     * Returns: All 256 vaccine records
     *
     * Use case:
     * - Administrative interface
     * - Database verification
     * - Generate vaccine list for documentation
     */
    @GetMapping
    public ResponseEntity<List<Vaccine>> getAllVaccines() {
        log.info("Fetching all vaccines");
        List<Vaccine> vaccines = vaccineRepository.findAll();
        log.info("Returning {} vaccines", vaccines.size());
        return ResponseEntity.ok(vaccines);
    }

    /**
     * Get vaccine statistics.
     *
     * Example: GET /api/v1/vaccines/stats
     * Returns: Summary of database content
     *
     * Use case:
     * - Health check for vaccine database
     * - Administrative dashboard
     * - Verify database loaded correctly
     */
    @GetMapping("/stats")
    public ResponseEntity<Map<String, Object>> getVaccineStats() {
        log.info("Generating vaccine statistics");

        List<Vaccine> allVaccines = vaccineRepository.findAll();

        // Count unique CVX codes
        long uniqueCvxCodes = allVaccines.stream()
                .map(Vaccine::getCvxCode)
                .distinct()
                .count();

        // Count unique vaccine groups
        long uniqueGroups = allVaccines.stream()
                .map(Vaccine::getVaccineGroupName)
                .distinct()
                .count();

        // Count by status
        Map<String, Long> statusCounts = allVaccines.stream()
                .collect(Collectors.groupingBy(
                        Vaccine::getVaccineStatus,
                        Collectors.counting()
                ));

        // Group counts by vaccine group
        Map<String, Long> groupCounts = allVaccines.stream()
                .collect(Collectors.groupingBy(
                        Vaccine::getVaccineGroupName,
                        Collectors.counting()
                ));

        Map<String, Object> stats = Map.of(
                "totalRecords", allVaccines.size(),
                "uniqueCvxCodes", uniqueCvxCodes,
                "uniqueVaccineGroups", uniqueGroups,
                "statusBreakdown", statusCounts,
                "topVaccineGroups", groupCounts.entrySet().stream()
                        .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                        .limit(10)
                        .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue))
        );

        log.info("Vaccine stats: {} records, {} unique CVX codes, {} groups",
                allVaccines.size(), uniqueCvxCodes, uniqueGroups);

        return ResponseEntity.ok(stats);
    }

    /**
     * Search vaccines by description.
     *
     * Example: GET /api/v1/vaccines/search?q=pediarix
     * Returns: All vaccines matching "pediarix" in description
     *
     * Use case:
     * - Frontend autocomplete
     * - User-friendly vaccine lookup
     * - Training interface
     */
    @GetMapping("/search")
    public ResponseEntity<List<Vaccine>> searchVaccines(@RequestParam String q) {
        log.info("Searching vaccines with query: {}", q);

        List<Vaccine> results = vaccineRepository.findAll().stream()
                .filter(v -> v.getShortDesc().toLowerCase().contains(q.toLowerCase()) ||
                        v.getVaccineGroupName().toLowerCase().contains(q.toLowerCase()))
                .collect(Collectors.toList());

        log.info("Found {} results for query: {}", results.size(), q);
        return ResponseEntity.ok(results);
    }
}