# Contributing

Thanks for helping. The Immunization Validator checks student immunization
records against state school-entry requirements. It's free and open source,
and it gets better every time someone adds a state, corrects a rule or
translates the website.

You don't need to write code to help:

| You want to… | Do this |
|---|---|
| Add your state, without coding | Open a [new state request](../../issues/new?template=new-state-request.yml) and link the regulation. We'll turn it into rules. |
| Fix a rule that looks wrong | Open a [rule correction](../../issues/new?template=rule-correction.yml). |
| Add a state yourself | Read [How state rules are structured](#how-state-rules-are-structured) and open a pull request. |
| Translate the website | See [Translations](#translations). |

Please don't put real patient information in issues, pull requests or test
data. Use made-up identifiers and dates.

## How state rules are structured

All rules live in [`src/main/resources/application.yml`](src/main/resources/application.yml)
under `immunization.requirements.states`. Each state is keyed by its
two-letter code and lists requirements by school-year group:

```yaml
immunization:
  requirements:
    states:
      MA:
        schoolYear:
          - schoolYear: "K-6"          # the group this rule applies to
            vaccineCode: "DTaP"        # which vaccine (case-sensitive)
            minDoses: 5                # doses needed by default
            description: "DTaP - 5 doses required; 4 doses acceptable if 4th given on or after 4th birthday"
            dateConditions:            # optional: age at a given dose
              - "1st dose on or after 1st birthday"
            intervalConditions:        # optional: spacing between doses
              - "at least 28 days between doses"
            alternateRequirements:     # optional: other ways to meet the rule
              - minDoses: 4
                description: "4 doses acceptable if 4th dose given on or after 4th birthday"
                dateConditions:
                  - "4th dose on or after 4th birthday"
            acceptedExceptions:        # exceptions that count as meeting the rule
              - "MEDICAL_CONTRAINDICATION"
              - "RELIGIOUS_EXEMPTION"
            notes: "Anything a reviewer should know"
```

A requirement is met when **any** of these is true:

- the record has at least `minDoses` doses of `vaccineCode`, and every
  `dateConditions` and `intervalConditions` entry is satisfied;
- one of the `alternateRequirements` is satisfied in the same way;
- the patient has an exception for that vaccine that's listed in
  `acceptedExceptions`.

### School-year groups

Use the group names the state's regulation uses. Massachusetts uses
`preschool`, `K-6`, `7-10`, `11-12` and `college`. Clients send one of these as
the `schoolYear` request parameter. Say in your pull request which grades each
group covers, so the maintainers can add the state to the
[website](https://validator.chikarahealthrecords.com)'s grade list.

### Vaccine codes

Use the existing names so doses given as CDC CVX codes map correctly:
`DTaP`, `Tdap`, `Polio`, `HepB`, `Hib`, `MMR`, `Varicella`, `MenACWY`.
CVX-to-vaccine mappings are in
[`database/init-scripts/vaccines.sql`](database/init-scripts/vaccines.sql).

### Condition phrases

Conditions are written as short phrases. Only these patterns are understood;
anything else makes the requirement come back as "undetermined":

| Kind | Pattern | Example |
|---|---|---|
| Age at a dose | `Nth dose on or after Mth birthday` | `4th dose on or after 4th birthday` |
| Age at a dose | `Nth dose on or after Mth month` | `1st dose on or after 15th month` |
| Spacing | `at least X days/weeks/months/years between doses` | `at least 28 days between doses` |
| Spacing | `at least X … between last two doses` | `at least 6 months between last two doses` |
| Spacing | `at least X … between Nth and Mth dose` | `at least 8 weeks between 1st and 2nd dose` |

If a regulation needs a rule these patterns can't express, describe it in the
`notes` field and mention it in your pull request. We'd rather extend the
evaluator than write a rule that's subtly wrong.

### Exception types

`MEDICAL_CONTRAINDICATION`, `RELIGIOUS_EXEMPTION`, `LABORATORY_EVIDENCE`,
`RELIABLE_HISTORY_CHICKENPOX`, `SIGNED_WAIVER`. If your state needs a new type,
add it and explain where it comes from in the regulation.

## Adding a state: checklist

1. Find the current regulation and the state health department's published
   requirements. Note the citation and the date the state published them.
2. Add a block for the state in `application.yml`, with a comment at the top
   giving the source, citation and publication date (see the Massachusetts
   block). The website shows results as "from rules published <date>", so this
   date matters.
3. Add tests for each school-year group, including at least one record that
   meets the rules and one that doesn't. Edge cases (a dose given exactly on a
   birthday) are especially useful. See [`ValidationServiceTest.java`](src/test/java/com/immunization/validator/ValidationServiceTest.java).
4. Run the tests with `mvn test`.
5. Open a pull request that links the regulation. A second person should check
   the rules against the source before it's merged.

## Translations

The [website](https://validator.chikarahealthrecords.com) is maintained by
Chikara in a separate, private repository, and its text isn't set up for
translation yet. If you'd like to translate it, open an issue in this
repository saying which language, and we'll share the text to translate.

## Code contributions

- Java 17 and Maven. Run `mvn test` before opening a pull request.
- Keep logging free of patient data: only timestamps, request source, masked
  ids, response modes and errors.
- Small, focused pull requests are easiest to review.
