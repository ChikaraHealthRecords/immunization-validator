package com.immunization.validator.service;

import com.immunization.validator.model.Immunization;
import com.immunization.validator.model.UndeterminedCondition;

import java.util.List;
import java.util.Set;

/**
 * CVX codes whose CDC vaccine group doesn't line up with what the state
 * rules ask for. CDC's groups are broader than school requirements, so a few
 * codes need handling by code rather than by group.
 */
final class CvxGroupRules {

    private CvxGroupRules() {
    }

    // ─── MenACWY ─────────────────────────────────────────────────────────────

    /**
     * CDC puts every meningococcal vaccine except MenB in the "MENING" group.
     * Only these meet a MenACWY requirement:
     *   108 meningococcal ACWY, unspecified formulation
     *   114 MenACWY-D (Menactra)
     *   136 MenACWY-CRM (Menveo)
     *   147 meningococcal MCV4, unspecified formulation
     *   203 MenACWY-TT (MenQuadfi)
     *   316 MenABCWY: CDC "meningococcal polysaccharide (MenACWY-TT conjugate), (MenB)"
     *   328 MenABCWY: CDC "meningococcal oligosaccharide (MenACWY), (MenB)"
     * The rest of MENING doesn't: 32 MPSV4 (polysaccharide), 103 MenC only,
     * 148 Hib-MenCY (no A or W; it still counts for Hib through its HIB row).
     */
    static final Set<Integer> MENACWY_CVX = Set.of(108, 114, 136, 147, 203, 316, 328);

    static final String MENING_GROUP = "MENING";
    static final String MENACWY = "MenACWY";

    /**
     * @return the rule vaccine code for a MENING-group row, or null if the code
     *         doesn't meet MenACWY (so the dose counts toward no requirement)
     */
    static String menACWYCodeFor(Integer cvxCode) {
        return cvxCode != null && MENACWY_CVX.contains(cvxCode) ? MENACWY : null;
    }

    // ─── DT counted toward DTaP ──────────────────────────────────────────────

    /** Pediatric DT: diphtheria and tetanus, no pertussis. CDC groups it with DTaP. */
    static final int DT_PEDIATRIC_CVX = 28;
    static final String DTAP = "DTaP";

    /**
     * Doses that count toward a requirement only in some circumstances. Today
     * that's pediatric DT counted toward DTaP: it lacks the pertussis
     * component, so it's acceptable only with a documented medical reason not
     * to give pertussis vaccine.
     */
    static boolean isConditionalDose(String requirementVaccineCode, Immunization dose) {
        return DTAP.equals(requirementVaccineCode)
                && dose.getCvxCode() != null
                && dose.getCvxCode() == DT_PEDIATRIC_CVX;
    }

    /**
     * The "needs review" explanation when a DTaP requirement is met only by
     * counting DT doses.
     */
    static UndeterminedCondition dtNeedsReview(String requirementDescription,
                                               int dtDoses, int dosesWithoutDt, int requiredDoses) {
        String doses = dtDoses == 1 ? "1 dose was" : dtDoses + " doses were";
        return UndeterminedCondition.builder()
                .vaccineCode(DTAP)
                .condition(requirementDescription)
                .reason("DT given instead of DTaP")
                .details(String.format(
                        "%s recorded as pediatric DT (CVX 28). DT protects against diphtheria and tetanus "
                                + "but not pertussis (whooping cough), so it counts toward the DTaP requirement only "
                                + "when a medical reason not to give pertussis vaccine is documented. "
                                + "Counting DT, the requirement is met; without it, %d of %d DTaP doses are recorded.",
                        doses, dosesWithoutDt, requiredDoses))
                .suggestion("Check whether a medical contraindication to pertussis vaccine is on file. If it is, "
                        + "the DT doses count. If not, the student needs DTaP (or Tdap, depending on age) to catch up.")
                .build();
    }

    static String dtWarning(int dtDoses) {
        return String.format(
                "%s DTaP dose%s recorded as pediatric DT (CVX 28) %s included in the DTaP count. "
                        + "DT counts toward DTaP only with a documented medical contraindication to pertussis vaccine.",
                dtDoses, dtDoses == 1 ? "" : "s", dtDoses == 1 ? "is" : "are");
    }

    static List<Immunization> conditionalDoses(String requirementVaccineCode, List<Immunization> doses) {
        return doses.stream().filter(d -> isConditionalDose(requirementVaccineCode, d)).toList();
    }
}
