package com.vendorfraud.security;

import com.vendorfraud.config.JwtProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import javax.crypto.SecretKey;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Issues and verifies HS256 access tokens. The signing key comes from
 * configuration - there is no literal secret anywhere in the source.
 */
@Service
public class JwtService {

    private static final Logger log = LoggerFactory.getLogger(JwtService.class);
    private static final String CLAIM_USER_ID = "uid";
    private static final String CLAIM_ROLES = "roles";
    private static final String CLAIM_FULL_NAME = "name";

    private final JwtProperties properties;
    private final SecretKey signingKey;

    public JwtService(JwtProperties properties) {
        this.properties = properties;
        this.signingKey = Keys.hmacShaKeyFor(properties.secret().getBytes(StandardCharsets.UTF_8));
    }

    public String issueToken(AppUserPrincipal principal) {
        Instant now = Instant.now();
        Instant expiry = now.plus(properties.accessTokenTtl());
        List<String> roles = principal.getAuthorities().stream()
                .map(a -> a.getAuthority())
                .toList();
        return Jwts.builder()
                .issuer(properties.issuer())
                .subject(principal.getUsername())
                .claim(CLAIM_USER_ID, principal.getId())
                .claim(CLAIM_FULL_NAME, principal.getFullName())
                .claim(CLAIM_ROLES, roles)
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiry))
                .signWith(signingKey)
                .compact();
    }

    public Instant expiryOf(String token) {
        return parse(token).getExpiration().toInstant();
    }

    /**
     * Verifies signature, issuer and expiry, then rebuilds the principal
     * from the claim set.
     *
     * @return the principal, or {@code null} when the token is unusable
     */
    @SuppressWarnings("unchecked")
    public AppUserPrincipal toPrincipal(String token) {
        try {
            Claims claims = parse(token);
            Long userId = claims.get(CLAIM_USER_ID, Number.class).longValue();
            List<String> roles = (List<String>) claims.get(CLAIM_ROLES, List.class);
            return new AppUserPrincipal(userId, claims.getSubject(),
                    claims.get(CLAIM_FULL_NAME, String.class),
                    roles == null ? List.of() : roles);
        } catch (JwtException | IllegalArgumentException | NullPointerException ex) {
            log.debug("Rejected access token: {}", ex.getClass().getSimpleName());
            return null;
        }
    }

    private Claims parse(String token) {
        return Jwts.parser()
                .verifyWith(signingKey)
                .requireIssuer(properties.issuer())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}
