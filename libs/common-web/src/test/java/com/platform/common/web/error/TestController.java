package com.platform.common.web.error;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/test")
class TestController {

    record Payload(@NotBlank String name) {
    }

    @PostMapping("/validate")
    Payload validate(@Valid @RequestBody Payload payload) {
        return payload;
    }

    @GetMapping("/not-found")
    void notFound() {
        throw new ResourceNotFoundException("Widget", "42");
    }

    @GetMapping("/conflict")
    void conflict() {
        throw new ConflictException("WIDGET_ALREADY_EXISTS", "Widget already exists");
    }

    @GetMapping("/boom")
    void boom() {
        throw new IllegalStateException("database password is hunter2");
    }

    @GetMapping("/header")
    String header(@RequestHeader("Idempotency-Key") String key) {
        return key;
    }

    @GetMapping("/param")
    int param(@RequestParam @Min(1) int size) {
        return size;
    }
}
