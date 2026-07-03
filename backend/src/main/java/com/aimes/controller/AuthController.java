package com.aimes.controller;

import com.aimes.common.Result;
import com.aimes.dto.Requests.LoginRequest;
import com.aimes.service.AuthService;
import jakarta.validation.Valid;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@Tag(name = "认证")
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/login")
    public Result<Map<String, Object>> login(@Valid @RequestBody LoginRequest request) {
        return Result.ok(authService.login(request));
    }

    @PostMapping("/logout")
    public Result<Void> logout() {
        authService.logout();
        return Result.ok("退出成功", null);
    }

    @GetMapping("/info")
    public Result<Map<String, Object>> info() {
        return Result.ok(authService.info());
    }

    @GetMapping("/captcha")
    public Result<Map<String, Object>> captcha() {
        return Result.ok(authService.captcha());
    }

    @GetMapping("/captcha/required")
    public Result<Map<String, Object>> captchaRequired() {
        return Result.ok(authService.captchaRequired());
    }

    @org.springframework.web.bind.annotation.PutMapping("/profile")
    public Result<Map<String, Object>> updateProfile(@jakarta.validation.Valid @RequestBody com.aimes.dto.Requests.ProfileUpdateRequest request) {
        return Result.ok(authService.updateProfile(request));
    }

    @org.springframework.web.bind.annotation.PutMapping("/password")
    public Result<String> changePassword(@jakarta.validation.Valid @RequestBody com.aimes.dto.Requests.PasswordChangeRequest request) {
        authService.changePassword(request);
        return Result.ok("密码修改成功");
    }
}
