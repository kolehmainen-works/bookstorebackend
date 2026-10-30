package com.viimeiset.bookstore.service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

@Service
public class JwtService {

	// the claim WebSecurityConfig reads the user's roles from
	public static final String ROLES_CLAIM = "roles";

	private final JwtEncoder jwtEncoder;
	private final long expirationMinutes;

	public JwtService(JwtEncoder jwtEncoder, @Value("${app.jwt.expiration-minutes:60}") long expirationMinutes) {
		this.jwtEncoder = jwtEncoder;
		this.expirationMinutes = expirationMinutes;
	}

	public IssuedToken createToken(Authentication authentication) {
		Instant now = Instant.now();
		Instant expiresAt = now.plus(expirationMinutes, ChronoUnit.MINUTES);
		List<String> roles = authentication.getAuthorities().stream().map(GrantedAuthority::getAuthority).toList();

		JwtClaimsSet claims = JwtClaimsSet.builder()
				.issuer("bookstorebackend")
				.issuedAt(now)
				.expiresAt(expiresAt)
				.subject(authentication.getName())
				.claim(ROLES_CLAIM, roles)
				.build();
		JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();

		String token = jwtEncoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
		return new IssuedToken(token, expiresAt, roles);
	}

	public record IssuedToken(String token, Instant expiresAt, List<String> roles) {
	}
}
