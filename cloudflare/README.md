# Demo deployment (Cloudflare Containers)

Runs the validator API at **https://validator-api.chikarahealthrecords.info**
for the demo website. Production will run on AWS
(`.github/workflows/backend.yml`, not in use yet).

## How it works
- `src/index.js` is a small Worker that sends every request to one container
  built from the repo's `Dockerfile`.
- The container runs with no Spring profile, so vaccine data comes from
  `vaccines.csv` and **no database is needed**. Outbound internet is off.
- `ALLOWED_ORIGIN` in `wrangler.jsonc` is passed to the API as
  `IMMUNIZATION_CORS_ALLOWED_ORIGINS`, so only the demo website can call it
  from a browser.
- Instance type `standard-1` (1/2 vCPU, 4 GiB). The container sleeps after 15
  idle minutes; a cron ping keeps it warm on weekdays, about 7am–8pm US
  Eastern. Outside those hours the first request waits for Spring Boot to
  start (roughly 10–20 seconds).

## Deploying
`.github/workflows/demo.yml` runs the tests on every pull request and, on
`main`, deploys with `npx wrangler deploy`. It needs repository secrets
`CLOUDFLARE_API_TOKEN` and `CLOUDFLARE_ACCOUNT_ID`, and a Workers Paid plan.

To deploy by hand you need Docker running (wrangler builds the image):

```bash
cd cloudflare
npm ci
npx wrangler deploy
```

## Costs
Containers bill for memory and disk while the container is awake, and for CPU
only while it's busy. The keep-warm hours are the main cost lever; change or
remove `triggers.crons` in `wrangler.jsonc` to adjust them.
