package com.immunization.validator;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.immunization.validator.model.*;
import com.immunization.validator.repository.ExtractionJobRepository;
import com.immunization.validator.repository.VaccineFamilyAliasRepository;
import com.immunization.validator.service.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.mock.web.MockMultipartFile;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * Exercises the full PENDING -> SUBMITTED -> COMPLETED transition and a FAILED
 * path for DocumentExtractionService, with a stubbed LlamaCloudExtractionClient
 * (no real DB, no real LlamaCloud call -- matches this repo's convention of
 * mocking dependencies rather than booting a Spring context, since CI's
 * `mvn test` runs without a database).
 *
 * The COMPLETED-path assertions use the Xavier Hains fixture (see
 * ExtractedRecordToImmunizationMapperTest) run through a hand-built MA
 * RequirementsService mirroring the real application.yml school-year
 * requirements, so the tri-state verdicts per grade level reflect real
 * validation logic rather than a toy stub.
 */
class DocumentExtractionServiceTest {

    @Mock
    private ExtractionJobRepository extractionJobRepository;

    @Mock
    private LlamaCloudExtractionClient llamaCloudExtractionClient;

    @Mock
    private VaccineFamilyAliasRepository vaccineFamilyAliasRepository;

    private DocumentExtractionService documentExtractionService;
    private final Map<UUID, ExtractionJob> store = new HashMap<>();

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        store.clear();

        when(extractionJobRepository.save(any())).thenAnswer(inv -> {
            ExtractionJob job = inv.getArgument(0);
            store.put(job.getId(), job);
            return job;
        });
        when(extractionJobRepository.findById(any())).thenAnswer(inv -> {
            UUID id = inv.getArgument(0);
            return Optional.ofNullable(store.get(id));
        });

        stubAlias("DTAP/DT", "DTaP");
        stubAlias("Hib", "Hib");
        stubAlias("Polio", "Polio");
        stubAlias("Pneumopcv", "PneumoPCV");
        stubAlias("Hepatitis B", "HepB");
        stubAlias("MMR", "MMR");
        stubAlias("Varicella", "Varicella");

        ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
        ExtractedRecordToImmunizationMapper mapper = new ExtractedRecordToImmunizationMapper();
        VaccineFamilyMappingService vaccineFamilyMappingService =
                new VaccineFamilyMappingService(vaccineFamilyAliasRepository);

        DateConditionEvaluator dateConditionEvaluator = new DateConditionEvaluator();
        IntervalConditionEvaluator intervalConditionEvaluator = new IntervalConditionEvaluator();
        VaccineLookupService vaccineLookupService = new VaccineLookupService(null);
        ValidationService validationService = new ValidationService(
                buildMaRequirementsService(), dateConditionEvaluator, intervalConditionEvaluator, vaccineLookupService);

        documentExtractionService = new DocumentExtractionService(
                extractionJobRepository,
                llamaCloudExtractionClient,
                mapper,
                vaccineFamilyMappingService,
                validationService,
                buildMaRequirementsService(),
                objectMapper);
        setField(documentExtractionService, "defaultState", "MA");
    }

    private void stubAlias(String rawLabel, String canonicalGroup) {
        when(vaccineFamilyAliasRepository.findByRawLabelIgnoreCase(rawLabel))
                .thenReturn(Optional.of(VaccineFamilyAlias.builder()
                        .rawLabel(rawLabel).canonicalGroup(canonicalGroup).build()));
    }

    private void setField(Object target, String fieldName, Object value) {
        try {
            var field = target.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            field.set(target, value);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    @DisplayName("submitJob persists PENDING then transitions to SUBMITTED on LlamaCloud success")
    void testSubmitJob_TransitionsToSubmitted() {
        when(llamaCloudExtractionClient.submitJob(any())).thenReturn("llama-job-1");

        MockMultipartFile file = new MockMultipartFile("file", "xavier-hains.pdf", "application/pdf", "content".getBytes());
        ExtractionJob job = documentExtractionService.submitJob(file);

        assertEquals(ExtractionJobStatus.SUBMITTED, job.getStatus());
        assertEquals("llama-job-1", job.getLlamaExtractJobId());
        assertEquals("xavier-hains.pdf", job.getSourceFileName());
    }

    @Test
    @DisplayName("submitJob marks FAILED when LlamaCloud submission throws")
    void testSubmitJob_FailsCleanlyOnClientError() {
        when(llamaCloudExtractionClient.submitJob(any()))
                .thenThrow(new LlamaCloudClientException("LLAMA_CLOUD_API_KEY is not configured"));

        MockMultipartFile file = new MockMultipartFile("file", "xavier-hains.pdf", "application/pdf", "content".getBytes());
        ExtractionJob job = documentExtractionService.submitJob(file);

        assertEquals(ExtractionJobStatus.FAILED, job.getStatus());
        assertEquals("LLAMA_CLOUD_API_KEY is not configured", job.getErrorMessageInternal());
        assertNotNull(job.getErrorMessageUser());
    }

    @Test
    @DisplayName("pollAndProcess marks FAILED when LlamaCloud reports an ERROR state")
    void testPollAndProcess_FailedPath() throws Exception {
        ExtractionJob job = ExtractionJob.builder()
                .id(UUID.randomUUID())
                .status(ExtractionJobStatus.SUBMITTED)
                .llamaExtractJobId("llama-job-2")
                .uploadedAt(java.time.ZonedDateTime.now())
                .build();
        store.put(job.getId(), job);

        when(llamaCloudExtractionClient.getJobStatus("llama-job-2")).thenReturn(
                LlamaCloudJobResult.builder().state(LlamaCloudJobState.ERROR).errorMessage("document unreadable").build());

        documentExtractionService.pollAndProcess(job.getId());

        ExtractionJob updated = store.get(job.getId());
        assertEquals(ExtractionJobStatus.FAILED, updated.getStatus());
        assertEquals("document unreadable", updated.getErrorMessageInternal());
        assertEquals("Document extraction failed. Please verify the document is readable and try again.",
                updated.getErrorMessageUser());
    }

    @Test
    @DisplayName("pollAndProcess completes the job and produces correct tri-state verdicts per grade level for the Xavier Hains fixture")
    void testPollAndProcess_CompletedPath_TriStateVerdicts() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
        String resultJson = objectMapper.writeValueAsString(
                ExtractedRecordToImmunizationMapperTest.xavierHainsRecord());

        ExtractionJob job = ExtractionJob.builder()
                .id(UUID.randomUUID())
                .status(ExtractionJobStatus.SUBMITTED)
                .llamaExtractJobId("llama-job-3")
                .uploadedAt(java.time.ZonedDateTime.now())
                .build();
        store.put(job.getId(), job);

        when(llamaCloudExtractionClient.getJobStatus("llama-job-3")).thenReturn(
                LlamaCloudJobResult.builder().state(LlamaCloudJobState.SUCCESS).resultJson(resultJson).build());

        documentExtractionService.pollAndProcess(job.getId());

        ExtractionJob completed = store.get(job.getId());
        assertEquals(ExtractionJobStatus.COMPLETED, completed.getStatus());
        assertEquals("10505940", completed.getPatientIdentifier());
        assertNotNull(completed.getValidationResult());

        Map<String, ValidationResponse> verdicts = objectMapper.readValue(
                completed.getValidationResult(),
                objectMapper.getTypeFactory().constructMapType(Map.class, String.class, ValidationResponse.class));

        assertEquals(ComplianceStatus.VALID, verdicts.get("preschool").getStatus(),
                "Hib(4)/DTaP(4+)/Polio(3+)/HepB(3)/MMR(1)/Varicella(1) all satisfied");
        assertEquals(ComplianceStatus.VALID, verdicts.get("K-6").getStatus(),
                "DTaP(5)/Polio(4, 4th after 4th bday + 6mo interval)/HepB(3)/MMR(2)/Varicella(2) all satisfied");
        assertEquals(ComplianceStatus.INVALID, verdicts.get("7-10").getStatus(),
                "fixture has no Tdap or MenACWY doses at all");
        assertEquals(ComplianceStatus.INVALID, verdicts.get("11-12").getStatus(),
                "fixture has no Tdap or MenACWY doses at all");
        assertEquals(ComplianceStatus.INVALID, verdicts.get("college").getStatus(),
                "fixture has no Tdap dose at all");
    }

    /**
     * Hand-built MA requirements mirroring application.yml's school-year
     * requirements for the vaccine groups exercised by this test (Hib, DTaP,
     * Polio, HepB, MMR, Varicella, Tdap, MenACWY). Built directly rather than
     * loaded from YAML, matching this repo's existing test convention
     * (see ValidationServiceTest / EnhancedYamlFeaturesTest).
     */
    private static RequirementsService buildMaRequirementsService() {
        Map<String, List<ValidationRequirement>> bySchoolYear = new HashMap<>();

        bySchoolYear.put("preschool", List.of(
                ValidationRequirement.builder().vaccineCode("Hib").minDoses(4)
                        .description("Hib - 4-dose primary series").build(),
                ValidationRequirement.builder().vaccineCode("DTaP").minDoses(4)
                        .description("DTaP - 4 doses required for preschool").build(),
                ValidationRequirement.builder().vaccineCode("Polio").minDoses(3)
                        .description("Polio - 3 doses required for preschool").build(),
                ValidationRequirement.builder().vaccineCode("HepB").minDoses(3)
                        .description("Hepatitis B - 3 doses required").build(),
                ValidationRequirement.builder().vaccineCode("MMR").minDoses(1)
                        .description("MMR - 1 dose on or after 1st birthday")
                        .dateConditions(List.of("1st dose on or after 1st birthday")).build(),
                ValidationRequirement.builder().vaccineCode("Varicella").minDoses(1)
                        .description("Varicella - 1 dose on or after 1st birthday")
                        .dateConditions(List.of("1st dose on or after 1st birthday")).build()
        ));

        bySchoolYear.put("K-6", List.of(
                ValidationRequirement.builder().vaccineCode("DTaP").minDoses(5)
                        .description("DTaP - 5 doses required")
                        .alternateRequirements(List.of(AlternateRequirement.builder()
                                .minDoses(4).description("4 doses acceptable if 4th on/after 4th birthday")
                                .dateConditions(List.of("4th dose on or after 4th birthday")).build()))
                        .build(),
                ValidationRequirement.builder().vaccineCode("Polio").minDoses(4)
                        .description("Polio - 4 doses; 4th on/after 4th birthday and >=6mo after 3rd")
                        .dateConditions(List.of("4th dose on or after 4th birthday"))
                        .intervalConditions(List.of("at least 6 months between last two doses"))
                        .alternateRequirements(List.of(AlternateRequirement.builder()
                                .minDoses(3).description("3 doses acceptable if 3rd on/after 4th birthday and >=6mo after 2nd")
                                .dateConditions(List.of("3rd dose on or after 4th birthday"))
                                .intervalConditions(List.of("at least 6 months between last two doses"))
                                .build()))
                        .build(),
                ValidationRequirement.builder().vaccineCode("HepB").minDoses(3)
                        .description("Hepatitis B - 3 doses required").build(),
                ValidationRequirement.builder().vaccineCode("MMR").minDoses(2)
                        .description("MMR - 2 doses; 1st on/after 1st birthday; 2nd >=28 days after 1st")
                        .dateConditions(List.of("1st dose on or after 1st birthday"))
                        .intervalConditions(List.of("at least 28 days between doses")).build(),
                ValidationRequirement.builder().vaccineCode("Varicella").minDoses(2)
                        .description("Varicella - 2 doses; 1st on/after 1st birthday; 2nd >=28 days after 1st")
                        .dateConditions(List.of("1st dose on or after 1st birthday"))
                        .intervalConditions(List.of("at least 28 days between doses")).build()
        ));

        bySchoolYear.put("7-10", List.of(
                ValidationRequirement.builder().vaccineCode("Tdap").minDoses(1)
                        .description("Tdap - 1 dose required")
                        .dateConditions(List.of("1st dose on or after 7th birthday")).build(),
                ValidationRequirement.builder().vaccineCode("Polio").minDoses(4)
                        .description("Polio - 4 doses")
                        .dateConditions(List.of("4th dose on or after 4th birthday"))
                        .intervalConditions(List.of("at least 6 months between last two doses"))
                        .alternateRequirements(List.of(AlternateRequirement.builder()
                                .minDoses(3).description("3 doses acceptable")
                                .dateConditions(List.of("3rd dose on or after 4th birthday"))
                                .intervalConditions(List.of("at least 6 months between last two doses"))
                                .build()))
                        .build(),
                ValidationRequirement.builder().vaccineCode("HepB").minDoses(3)
                        .description("Hepatitis B - 3 doses required").build(),
                ValidationRequirement.builder().vaccineCode("MMR").minDoses(2)
                        .description("MMR - 2 doses")
                        .dateConditions(List.of("1st dose on or after 1st birthday"))
                        .intervalConditions(List.of("at least 28 days between doses")).build(),
                ValidationRequirement.builder().vaccineCode("Varicella").minDoses(2)
                        .description("Varicella - 2 doses")
                        .dateConditions(List.of("1st dose on or after 1st birthday"))
                        .intervalConditions(List.of("at least 28 days between doses")).build(),
                ValidationRequirement.builder().vaccineCode("MenACWY").minDoses(1)
                        .description("MenACWY - 1 dose on or after 10th birthday")
                        .dateConditions(List.of("1st dose on or after 10th birthday")).build()
        ));

        bySchoolYear.put("11-12", List.of(
                ValidationRequirement.builder().vaccineCode("Tdap").minDoses(1)
                        .description("Tdap - 1 dose required")
                        .dateConditions(List.of("1st dose on or after 7th birthday")).build(),
                ValidationRequirement.builder().vaccineCode("HepB").minDoses(3)
                        .description("Hepatitis B - 3 doses required").build(),
                ValidationRequirement.builder().vaccineCode("MMR").minDoses(2)
                        .description("MMR - 2 doses")
                        .dateConditions(List.of("1st dose on or after 1st birthday"))
                        .intervalConditions(List.of("at least 28 days between doses")).build(),
                ValidationRequirement.builder().vaccineCode("Varicella").minDoses(2)
                        .description("Varicella - 2 doses")
                        .dateConditions(List.of("1st dose on or after 1st birthday"))
                        .intervalConditions(List.of("at least 28 days between doses")).build(),
                ValidationRequirement.builder().vaccineCode("MenACWY").minDoses(2)
                        .description("MenACWY - 2 doses")
                        .dateConditions(List.of("2nd dose on or after 16th birthday"))
                        .intervalConditions(List.of("at least 8 weeks between doses"))
                        .alternateRequirements(List.of(AlternateRequirement.builder()
                                .minDoses(1).description("1 dose acceptable if on/after 16th birthday")
                                .dateConditions(List.of("1st dose on or after 16th birthday")).build()))
                        .build()
        ));

        bySchoolYear.put("college", List.of(
                ValidationRequirement.builder().vaccineCode("Tdap").minDoses(1)
                        .description("Tdap - 1 dose required")
                        .dateConditions(List.of("1st dose on or after 7th birthday")).build(),
                ValidationRequirement.builder().vaccineCode("HepB").minDoses(3)
                        .description("Hepatitis B - 3 doses required").build(),
                ValidationRequirement.builder().vaccineCode("MMR").minDoses(2)
                        .description("MMR - 2 doses")
                        .dateConditions(List.of("1st dose on or after 1st birthday"))
                        .intervalConditions(List.of("at least 28 days between doses")).build(),
                ValidationRequirement.builder().vaccineCode("Varicella").minDoses(2)
                        .description("Varicella - 2 doses")
                        .dateConditions(List.of("1st dose on or after 1st birthday"))
                        .intervalConditions(List.of("at least 28 days between doses")).build()
        ));

        StateRequirements ma = StateRequirements.builder()
                .stateCode("MA")
                .requirementsBySchoolYear(bySchoolYear)
                .requirementsByAge(new HashMap<>())
                .build();

        return new RequirementsService(new HashMap<>(Map.of("MA", ma)));
    }
}
