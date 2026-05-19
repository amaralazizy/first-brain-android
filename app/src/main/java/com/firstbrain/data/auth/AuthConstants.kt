package com.firstbrain.data.auth

object AuthConstants {
    const val NEON_AUTH_URL =
        "https://ep-proud-resonance-amlj3hjf.neonauth.c-5.us-east-1.aws.neon.tech/neondb/auth/"
    const val NEON_DATA_URL =
        "https://ep-proud-resonance-amlj3hjf.apirest.c-5.us-east-1.aws.neon.tech/neondb/rest/v1/"
    const val RECOMMENDATION_URL =
        "https://ml-api-production-394a.up.railway.app/"

    /** Origin header value; must match a trusted origin on the Neon Auth server. */
    const val ORIGIN = "http://localhost"

    /** Cookie name used by Better Auth on the Neon side. */
    const val SESSION_COOKIE = "__Secure-neon-auth.session_token"

    /** Refresh the JWT this many seconds before its actual expiry. */
    const val JWT_REFRESH_LEEWAY_SECONDS = 60L
}
