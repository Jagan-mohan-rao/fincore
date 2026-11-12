package com.fincore.auth;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

@RestController @RequestMapping("/api/auth")
public class AuthController {
    private final AuthService service;
    public AuthController(AuthService service) { this.service=service; }
    record RegisterRequest(@NotBlank @Size(max=120) String name, @NotBlank @Email @Size(max=190) String email, @NotBlank @Size(min=8,max=72) String password) {}
    record LoginRequest(@NotBlank @Email String email, @NotBlank String password) {}
    @PostMapping("/register") Map<String,Object> register(@Valid @RequestBody RegisterRequest r) { return service.register(r.name(),r.email(),r.password()); }
    @PostMapping("/login") Map<String,Object> login(@Valid @RequestBody LoginRequest r) { return service.login(r.email(),r.password()); }
}
