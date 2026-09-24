package com.platform.user.api;

import com.platform.common.web.paging.PageResponse;
import com.platform.user.api.dto.ChangeUserStatusRequest;
import com.platform.user.api.dto.CreateUserRequest;
import com.platform.user.api.dto.UpdateUserRequest;
import com.platform.user.api.dto.UserResponse;
import com.platform.user.api.dto.UserValidationResponse;
import com.platform.user.domain.User;
import com.platform.user.domain.UserStatus;
import com.platform.user.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.UUID;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

@RestController
@RequestMapping("/api/v1/users")
@Tag(name = "Users")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @PostMapping
    @Operation(summary = "Register a user")
    public ResponseEntity<UserResponse> create(@Valid @RequestBody CreateUserRequest request) {
        User user = userService.create(request);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest().path("/{id}").buildAndExpand(user.getId()).toUri();
        return ResponseEntity.created(location).body(UserResponse.from(user));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get a user by ID")
    public UserResponse get(@PathVariable UUID id) {
        return UserResponse.from(userService.get(id));
    }

    @GetMapping
    @Operation(summary = "List users", description = "Sortable by: createdAt, email, lastName")
    public PageResponse<UserResponse> list(
            @RequestParam(required = false) UserStatus status,
            @RequestParam(required = false) String email,
            @ParameterObject @PageableDefault(sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return PageResponse.from(userService.search(status, email, pageable), UserResponse::from);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Replace a user's profile")
    public UserResponse update(@PathVariable UUID id, @Valid @RequestBody UpdateUserRequest request) {
        return UserResponse.from(userService.update(id, request));
    }

    @PatchMapping("/{id}/status")
    @Operation(summary = "Activate or suspend a user")
    public UserResponse changeStatus(@PathVariable UUID id, @Valid @RequestBody ChangeUserStatusRequest request) {
        return UserResponse.from(userService.changeStatus(id, request.status()));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Delete a user")
    public void delete(@PathVariable UUID id) {
        userService.delete(id);
    }

    @GetMapping("/{id}/validation")
    @Operation(summary = "Check whether a user may place orders", description = "Used by the master-service during order orchestration")
    public UserValidationResponse validate(@PathVariable UUID id) {
        return userService.validateForOrdering(id);
    }
}
