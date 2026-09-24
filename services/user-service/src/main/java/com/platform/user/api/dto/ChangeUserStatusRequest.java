package com.platform.user.api.dto;

import com.platform.user.domain.UserStatus;
import jakarta.validation.constraints.NotNull;

public record ChangeUserStatusRequest(@NotNull UserStatus status) {
}
