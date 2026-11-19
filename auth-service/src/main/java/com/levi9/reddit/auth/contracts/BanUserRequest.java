package com.levi9.reddit.auth.contracts;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record BanUserRequest(@NotBlank @Size(max = 500) String reason) {}
