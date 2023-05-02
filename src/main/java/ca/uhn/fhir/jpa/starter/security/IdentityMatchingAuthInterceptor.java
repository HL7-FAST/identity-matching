package ca.uhn.fhir.jpa.starter.security;
import ca.uhn.fhir.interceptor.api.Hook;
import ca.uhn.fhir.interceptor.api.Interceptor;
import ca.uhn.fhir.interceptor.api.Pointcut;
import ca.uhn.fhir.rest.api.Constants;
import ca.uhn.fhir.rest.api.server.RequestDetails;
import ca.uhn.fhir.rest.server.interceptor.auth.RuleBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import javax.naming.AuthenticationException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

@Interceptor
public class IdentityMatchingAuthInterceptor {

	private final Logger _logger = LoggerFactory.getLogger(IdentityMatchingAuthInterceptor.class);

	@Hook(Pointcut.SERVER_INCOMING_REQUEST_POST_PROCESSED)
	public boolean incomingRequestPostProcessed(RequestDetails details, HttpServletRequest request, HttpServletResponse response) throws AuthenticationException, IOException {
		boolean authenticated = false;
		//check if request path is an endpoint that needs validation
		if(request.getRequestURI().equals("/fhir/Patient/$match")) {
			try {
				String token = details.getHeader(Constants.HEADER_AUTHORIZATION);
				if (token == null) {
					throw new AuthenticationException("Not authorized (no authorization header found in request)");
				}
				if (!token.startsWith(Constants.HEADER_AUTHORIZATION_VALPREFIX_BEARER)) {
					throw new AuthenticationException("Not authorized (authorization header does not contain a bearer token)");
				}

				boolean tokenValidated = false; //call introspection here
				//if token validates
				if(tokenValidated) {
					authenticated = true;
				}
				else {
					authenticated = false;
				}

			} catch (AuthenticationException ex) {
				_logger.error(ex.getMessage(), ex);
				_logger.info("Failed to authenticate request");
			}
		} //otherwise, allow all
		else {
			authenticated = true;
		}

		if(!authenticated) {
			response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
			response.getWriter().println("You are unauthorized to perform this request.");
		}
		return authenticated;
	}
}
