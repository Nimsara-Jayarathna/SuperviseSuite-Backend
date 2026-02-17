package com.supervisesuite.backend.auth.security;

import java.util.Optional;
import org.springframework.stereotype.Service;

@Service
public class JwtService {

    public Optional<String> extractSubject(String token) {
        // TODO: Implement JWT parsing and validation.
        return Optional.empty();
    }
}
