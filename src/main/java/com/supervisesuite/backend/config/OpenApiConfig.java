package com.supervisesuite.backend.config;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeIn;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.security.SecurityScheme;

@OpenAPIDefinition(
    info = @Info(
        title = "SuperviseSuite API",
        version = "v1",
        description = """
            OpenAPI documentation for the SuperviseSuite backend.

            Authentication uses an httpOnly cookie named 'ss_access_token'. In Swagger UI,
            first call POST /api/auth/login (or /api/auth/refresh) so the browser stores the cookie,
            then call protected endpoints normally.

            Note: Swagger UI and /v3/api-docs are enabled only in the 'dev' profile by default.
            """
    )
)
@SecurityScheme(
    name = OpenApiConfig.COOKIE_AUTH_SCHEME,
    type = SecuritySchemeType.APIKEY,
    in = SecuritySchemeIn.COOKIE,
    paramName = "ss_access_token",
    description = "JWT access token carried via the 'ss_access_token' httpOnly cookie."
)
public final class OpenApiConfig {

    public static final String COOKIE_AUTH_SCHEME = "cookieAuth";

    private OpenApiConfig() {
    }
}
