package org.hl7.fast.security;

import com.auth0.jwt.exceptions.AlgorithmMismatchException;
import com.auth0.jwt.exceptions.JWTDecodeException;
import com.auth0.jwt.exceptions.JWTVerificationException;
import com.auth0.jwt.exceptions.SignatureVerificationException;
import com.auth0.jwt.exceptions.TokenExpiredException;

/**
 * Every way an access check can end. The three values are the bounded tag set of the
 * security.access.decisions counter and the fields of the access log line.
 * Rejections and errors are kept apart so a dead identity provider never reads as bad credentials.
 */
public enum AccessOutcome {
	AUTH_DISABLED("auth_disabled", "none", "none"),
	BYPASS_HEADER("bypass_header", "none", "none"),
	PUBLIC_ENDPOINT("public_endpoint", "none", "none"),
	UNPROTECTED_ENDPOINT("unprotected_endpoint", "none", "none"),
	MISSING_TOKEN("rejected", "none", "missing_token"),
	NOT_BEARER("rejected", "none", "not_bearer"),
	INTROSPECTION_OK("authenticated", "introspection", "none"),
	INTROSPECTION_INACTIVE("rejected", "introspection", "inactive_token"),
	INTROSPECTION_FAILURE("error", "introspection", "dependency_failure"),
	INTROSPECTION_OTHER("error", "introspection", "other"),
	JWT_OK("authenticated", "jwt", "none"),
	JWT_ISSUER_MISMATCH("rejected", "jwt", "issuer_mismatch"),
	JWT_UNKNOWN_KEY("rejected", "jwt", "unknown_key"),
	JWT_EXPIRED("rejected", "jwt", "expired"),
	JWT_INVALID_SIGNATURE("rejected", "jwt", "invalid_signature"),
	JWT_INVALID_CLAIM("rejected", "jwt", "invalid_claim"),
	JWT_MALFORMED("rejected", "jwt", "malformed"),
	JWT_DEPENDENCY_FAILURE("error", "jwt", "dependency_failure"),
	JWT_OTHER("error", "jwt", "other");

	public final String decision;
	public final String method;
	public final String reason;

	AccessOutcome(String decision, String method, String reason) {
		this.decision = decision;
		this.method = method;
		this.reason = reason;
	}

	public boolean isError() {
		return "error".equals(decision);
	}

	static AccessOutcome fromJwtException(JWTVerificationException ex) {
		if (ex instanceof TokenExpiredException) {
			return JWT_EXPIRED;
		}
		if (ex instanceof SignatureVerificationException || ex instanceof AlgorithmMismatchException) {
			return JWT_INVALID_SIGNATURE;
		}
		if (ex instanceof JWTDecodeException) {
			return JWT_MALFORMED;
		}
		return JWT_INVALID_CLAIM;
	}
}
