package com.supervisesuite.backend.users.controller;

import com.supervisesuite.backend.config.OpenApiConfig;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/users")
@Tag(name = "Users", description = "User management endpoints.")
@SecurityRequirement(name = OpenApiConfig.COOKIE_AUTH_SCHEME)
public class UserController {
    // TODO: Add user management endpoints.
}
