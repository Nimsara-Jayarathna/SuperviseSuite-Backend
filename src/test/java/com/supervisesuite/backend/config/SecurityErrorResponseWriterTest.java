package com.supervisesuite.backend.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.supervisesuite.backend.common.api.ApiResponseFactory;
import com.supervisesuite.backend.common.error.ErrorCode;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class SecurityErrorResponseWriterTest {

    private SecurityErrorResponseWriter writer;

    @BeforeEach
    void setUp() {
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.findAndRegisterModules();
        writer = new SecurityErrorResponseWriter(objectMapper, new ApiResponseFactory());
    }

    @Test
    void write_writesUnifiedErrorEnvelope() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/protected");
        MockHttpServletResponse response = new MockHttpServletResponse();

        writer.write(request, response, HttpStatus.UNAUTHORIZED, ErrorCode.UNAUTHORIZED, "Authentication required.");

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getContentType()).startsWith(MediaType.APPLICATION_JSON_VALUE);
        assertThat(response.getCharacterEncoding()).isEqualTo(StandardCharsets.UTF_8.name());
        assertThat(response.getContentAsString()).contains("\"code\":\"UNAUTHORIZED\"");
        assertThat(response.getContentAsString()).contains("\"message\":\"Authentication required.\"");
    }

    @Test
    void write_committedResponse_isNoOp() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/protected");
        MockHttpServletResponse response = new MockHttpServletResponse();
        response.getWriter().write("already-committed");
        response.flushBuffer();

        writer.write(request, response, HttpStatus.FORBIDDEN, ErrorCode.FORBIDDEN, "Access denied.");

        assertThat(response.getContentAsString()).contains("already-committed");
    }
}
