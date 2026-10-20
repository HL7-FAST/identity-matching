package org.hl7.fast.security;

import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.exceptions.AlgorithmMismatchException;
import com.auth0.jwt.exceptions.JWTDecodeException;
import com.auth0.jwt.exceptions.JWTVerificationException;
import com.auth0.jwt.exceptions.MissingClaimException;
import com.auth0.jwt.exceptions.SignatureVerificationException;
import com.auth0.jwt.exceptions.TokenExpiredException;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AccessOutcomeTest {

	@Test
	void expiredTokenMapsToJwtExpired() {
		var ex = new TokenExpiredException("expired", Instant.now());
		assertEquals(AccessOutcome.JWT_EXPIRED, AccessOutcome.fromJwtException(ex));
	}

	@Test
	void signatureAndAlgorithmFailuresMapToInvalidSignature() {
		assertEquals(AccessOutcome.JWT_INVALID_SIGNATURE,
				AccessOutcome.fromJwtException(new SignatureVerificationException(Algorithm.none())));
		assertEquals(AccessOutcome.JWT_INVALID_SIGNATURE,
				AccessOutcome.fromJwtException(new AlgorithmMismatchException("alg")));
	}

	@Test
	void decodeFailureMapsToMalformed() {
		assertEquals(AccessOutcome.JWT_MALFORMED, AccessOutcome.fromJwtException(new JWTDecodeException("bad")));
	}

	@Test
	void otherVerificationFailuresMapToInvalidClaim() {
		assertEquals(AccessOutcome.JWT_INVALID_CLAIM, AccessOutcome.fromJwtException(new JWTVerificationException("x")));
		assertEquals(AccessOutcome.JWT_INVALID_CLAIM, AccessOutcome.fromJwtException(new MissingClaimException("iss")));
	}

	@Test
	void tagValuesAreLowerSnakeCase() {
		for (AccessOutcome outcome : AccessOutcome.values()) {
			for (String tag : List.of(outcome.decision, outcome.method, outcome.reason)) {
				assertTrue(tag != null && tag.matches("[a-z_]+"), outcome + " has bad tag: " + tag);
			}
		}
	}

	@Test
	void tagTriplesAreUnique() {
		var triples = Arrays.stream(AccessOutcome.values())
				.map(o -> List.of(o.decision, o.method, o.reason))
				.collect(Collectors.toSet());
		assertEquals(AccessOutcome.values().length, triples.size());
	}

	@Test
	void isErrorOnlyForErrorDecision() {
		for (AccessOutcome outcome : AccessOutcome.values()) {
			assertEquals("error".equals(outcome.decision), outcome.isError(), outcome.toString());
		}
	}
}
