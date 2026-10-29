// Demo deployment of the validator API on Cloudflare Containers.
//
// This Worker sends every request to one container built from the repo's
// Dockerfile. The container runs the API with no Spring profile, so vaccine
// data comes from vaccines.csv and no database is needed.

import { Container, getContainer } from '@cloudflare/containers'

export class ValidatorApi extends Container {
  defaultPort = 8080 // requests wait until Spring Boot is listening here
  sleepAfter = '15m' // stop after 15 idle minutes (see the keep-warm cron)
  enableInternet = false // the API makes no outbound calls

  constructor (ctx, env) {
    super(ctx, env)
    this.envVars = {
      // Which website may call the API from a browser (WebConfig).
      IMMUNIZATION_CORS_ALLOWED_ORIGINS: env.ALLOWED_ORIGIN,
      // Keep the JVM inside the instance's memory.
      JAVA_TOOL_OPTIONS: '-XX:MaxRAMPercentage=75'
    }
  }
}

// One shared instance: the API holds no per-user state.
const api = env => getContainer(env.VALIDATOR_API)

export default {
  async fetch (request, env) {
    return api(env).fetch(request)
  },

  // Keep-warm ping (wrangler.jsonc "triggers"): Spring Boot takes a while to
  // start, so during demo hours a request every few minutes stops the
  // container from going to sleep.
  async scheduled (controller, env, ctx) {
    ctx.waitUntil(api(env).fetch(new Request('http://validator-api/actuator/health')))
  }
}
