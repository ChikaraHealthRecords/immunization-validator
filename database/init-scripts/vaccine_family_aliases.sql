-- Maps raw vaccine family labels as they appear in LlamaExtract output
-- (which mirrors the labels used by source EHR "Immunization Summary" exports,
-- e.g. "DTAP/DT", "Pneumopcv") to the canonical vaccine group names used by
-- RequirementsService / application.yml (e.g. "DTaP", "PneumoPCV").
--
-- This is distinct from the CVX-code `vaccines` table: that maps numeric CVX
-- codes to groups, this maps free-text document labels to groups.

CREATE TABLE IF NOT EXISTS vaccine_family_aliases (
    id SERIAL PRIMARY KEY,
    raw_label TEXT NOT NULL UNIQUE,
    canonical_group TEXT NOT NULL
);

INSERT INTO vaccine_family_aliases (raw_label, canonical_group) VALUES
    ('DTAP/DT', 'DTaP'),
    ('DTaP', 'DTaP'),
    ('DT', 'DTaP'),
    ('Hib', 'Hib'),
    ('Polio', 'Polio'),
    ('Pneumopcv', 'PneumoPCV'),
    ('MMR', 'MMR'),
    ('Varicella', 'Varicella'),
    ('Hepatitis A', 'HepA'),
    ('Hepatitis B', 'HepB'),
    ('Influenza', 'Influenza'),
    ('Tdap', 'Tdap'),
    ('Td', 'Td'),
    ('MenACWY', 'MenACWY'),
    ('Meningococcal', 'MenACWY')
ON CONFLICT (raw_label) DO NOTHING;

COMMENT ON TABLE vaccine_family_aliases IS 'Raw LlamaExtract vaccine family label -> canonical vaccine group name used by RequirementsService';
