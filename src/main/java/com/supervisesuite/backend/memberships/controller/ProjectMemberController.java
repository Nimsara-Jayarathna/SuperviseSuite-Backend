package com.supervisesuite.backend.memberships.controller;

import com.supervisesuite.backend.config.OpenApiConfig;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/memberships")
@Tag(name = "Memberships", description = "Project membership and assignment endpoints.")
@SecurityRequirement(name = OpenApiConfig.COOKIE_AUTH_SCHEME)
public class ProjectMemberController {
    // TODO: Add project membership endpoints.
}
