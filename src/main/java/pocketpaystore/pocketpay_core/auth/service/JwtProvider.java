package pocketpaystore.pocketpay_core.auth.service;

import java.util.Date;
import java.util.UUID;

import javax.crypto.SecretKey;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import pocketpaystore.pocketpay_core.member.domain.MemberRole;

@Component
public class JwtProvider {

	private static final String CLAIM_ROLE = "role";

	private final SecretKey secretKey;
	private final long expiryMillis;

	public JwtProvider(
			@Value("${jwt.secret}") String secret,
			@Value("${jwt.expiry-hours}") long expiryHours
	) {
		this.secretKey = Keys.hmacShaKeyFor(Decoders.BASE64.decode(secret));
		this.expiryMillis = expiryHours * 3600 * 1000;
	}

	public String generate(Long memberId, MemberRole role) {
		return Jwts.builder()
				.id(UUID.randomUUID().toString())
				.subject(String.valueOf(memberId))
				.claim(CLAIM_ROLE, role.name())
				.issuedAt(new Date())
				.expiration(new Date(System.currentTimeMillis() + expiryMillis))
				.signWith(secretKey)
				.compact();
	}

	public Claims parseToken(String token) {
		return Jwts.parser()
				.verifyWith(secretKey)
				.build()
				.parseSignedClaims(token)
				.getPayload();
	}

	public boolean validateToken(String token) {
		try {
			parseToken(token);
			return true;
		} catch (Exception e) {
			return false;
		}
	}
}
