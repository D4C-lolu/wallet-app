package com.interswitch.walletapp.services;

import com.interswitch.walletapp.configuration.JwtProperties;
import com.interswitch.walletapp.security.UserPrincipal;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.util.Date;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class JwtService {

    private final JwtProperties jwtProperties;

    private SecretKey getSigningKey() {
        byte[] keyBytes = Decoders.BASE64.decode(jwtProperties.getSecret());
        return Keys.hmacShaKeyFor(keyBytes);
    }

    public String generateAccessToken(UserPrincipal principal) {
        return generateToken(principal, jwtProperties.getAccessTokenExpiry());
    }

    public String generateRefreshToken(UserPrincipal principal) {
        return generateToken(principal, jwtProperties.getRefreshTokenExpiry());
    }

    private String generateToken(UserPrincipal principal, long expiry) {
        return Jwts.builder()
                .id(UUID.randomUUID().toString())
                .subject(String.valueOf(principal.user().getId()))
                .claim("email", principal.user().getEmail())
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + (expiry * 1000)))
                .signWith(getSigningKey())
                .compact();
    }

    public Claims extractAllClaims(String token) {
        return Jwts.parser()
                .verifyWith(getSigningKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public Long extractUserId(String token) {
        return  Long.parseLong(extractAllClaims(token).getSubject());
    }

    public String extractJti(String token) {
        return extractAllClaims(token).getId();
    }

    public Date extractIssuedAt(String token) {
        return extractAllClaims(token).getIssuedAt();
    }

    public boolean isTokenExpired(String token) {
        return extractAllClaims(token).getExpiration().before(new Date());
    }

    public boolean isTokenInvalid(String token) {
        try {
            return isTokenExpired(token);
        } catch (JwtException | IllegalArgumentException e) {
            return true;
        }
    }
}
