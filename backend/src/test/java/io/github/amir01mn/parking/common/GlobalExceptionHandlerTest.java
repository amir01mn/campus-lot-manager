package io.github.amir01mn.parking.common;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import java.sql.SQLException;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

class GlobalExceptionHandlerTest {

    private final MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new ThrowingController())
            .setControllerAdvice(new GlobalExceptionHandler())
            .build();

    @Test
    void bodyValidationErrorsAre400WithFieldList() throws Exception {
        mockMvc.perform(post("/test/body").contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.type").value("urn:campus-parking:problem:validation"))
                .andExpect(jsonPath("$.errors[0].field").value("name"));
    }

    @Test
    void parameterValidationErrorsAre400() throws Exception {
        mockMvc.perform(get("/test/param").param("size", "0"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("size"));
    }

    @Test
    void malformedJsonIs400() throws Exception {
        mockMvc.perform(post("/test/body").contentType(MediaType.APPLICATION_JSON).content("{"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void notFoundIs404() throws Exception {
        mockMvc.perform(get("/test/not-found"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("Resource not found"))
                .andExpect(jsonPath("$.detail").value("Booking 42 was not found"));
    }

    @Test
    void conflictIs409() throws Exception {
        mockMvc.perform(get("/test/conflict"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("Email already registered"));
    }

    @Test
    void exclusionConstraintViolationIs409BookingOverlap() throws Exception {
        mockMvc.perform(get("/test/overlap"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.type").value("urn:campus-parking:problem:booking-overlap"));
    }

    @Test
    void otherIntegrityViolationsAre409WithoutLeakingSql() throws Exception {
        mockMvc.perform(get("/test/integrity"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.type").value("urn:campus-parking:problem:data-integrity"))
                .andExpect(jsonPath("$.detail").value("The request conflicts with existing data."));
    }

    @Test
    void forbiddenIs403() throws Exception {
        mockMvc.perform(get("/test/forbidden")).andExpect(status().isForbidden());
        mockMvc.perform(get("/test/access-denied"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.detail").value("You are not allowed to do this."));
    }

    @Test
    void businessRuleIs422WithCode() throws Exception {
        mockMvc.perform(get("/test/rule"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("BOOKING_TOO_SHORT"))
                .andExpect(jsonPath("$.detail").value("Bookings must be at least 30 minutes"));
    }

    @Test
    void unexpectedErrorsAre500WithoutDetails() throws Exception {
        mockMvc.perform(get("/test/boom"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.detail").value("An unexpected error occurred."));
    }

    record Body(@NotBlank String name) {
    }

    @RestController
    static class ThrowingController {

        @PostMapping("/test/body")
        String body(@Valid @RequestBody Body body) {
            return body.name();
        }

        @GetMapping("/test/param")
        String param(@RequestParam @Min(1) int size) {
            return Integer.toString(size);
        }

        @GetMapping("/test/not-found")
        String notFound() {
            throw NotFoundException.of("Booking", 42);
        }

        @GetMapping("/test/conflict")
        String conflict() {
            throw new ConflictException("Email already registered");
        }

        @GetMapping("/test/overlap")
        String overlap() {
            throw new DataIntegrityViolationException("could not execute statement",
                    new SQLException("conflicting key value violates exclusion constraint", "23P01"));
        }

        @GetMapping("/test/integrity")
        String integrity() {
            throw new DataIntegrityViolationException("could not execute statement",
                    new SQLException("violates check constraint \"ck_secret\"", "23514"));
        }

        @GetMapping("/test/forbidden")
        String forbidden() {
            throw new ForbiddenException("Not your booking");
        }

        @GetMapping("/test/access-denied")
        String accessDenied() {
            throw new AccessDeniedException("Access is denied");
        }

        @GetMapping("/test/rule")
        String rule() {
            throw new BusinessRuleException("BOOKING_TOO_SHORT", "Bookings must be at least 30 minutes");
        }

        @GetMapping("/test/boom")
        String boom() {
            throw new IllegalStateException("secret internals");
        }
    }
}
