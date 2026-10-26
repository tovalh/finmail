package com.tovalh.finmail.controller;

import com.tovalh.finmail.service.JwtService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/auth")
@Tag(name = "Auth", description = "Autenticación con JWT")
public class AuthController {

    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;

    public AuthController(AuthenticationManager authenticationManager, JwtService jwtService) {
        this.authenticationManager = authenticationManager;
        this.jwtService = jwtService;
    }

    record LoginRequest(String username, String password) {}
    record TokenResponse(String accessToken, String refreshToken) {}

    @Operation(summary = "Login", description = "Retorna access token (1h) y refresh token (30 días)")
    @PostMapping("/login")
    public TokenResponse login(@RequestBody LoginRequest request) {
        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.username(), request.password())
        );
        return new TokenResponse(
                jwtService.generarAccessToken(request.username()),
                jwtService.generarRefreshToken(request.username())
        );
    }

    @Operation(summary = "Refresh token", description = "Genera un nuevo access token usando el refresh token")
    @PostMapping("/refresh")
    public TokenResponse refresh(@RequestBody String refreshToken) {
        if (jwtService.validarToken(refreshToken) != JwtService.TokenStatus.VALID) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Refresh token inválido o expirado");
        }
        String username = jwtService.extraerUsername(refreshToken);
        return new TokenResponse(
                jwtService.generarAccessToken(username),
                refreshToken
        );
    }
}
