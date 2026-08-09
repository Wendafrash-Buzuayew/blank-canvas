package com.qrserve.auth.dto;

import com.qrserve.shared.security.UserRole;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserInfoResponse {
    private UUID id;
    private String name;
    private String email;
    private UserRole role;
    private UUID merchantId;
    private boolean enabled;
}