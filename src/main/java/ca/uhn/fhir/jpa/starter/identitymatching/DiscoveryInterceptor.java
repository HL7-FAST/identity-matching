package ca.uhn.fhir.jpa.starter.identitymatching;
import ca.uhn.fhir.interceptor.api.Hook;
import ca.uhn.fhir.interceptor.api.Interceptor;
import ca.uhn.fhir.interceptor.api.Pointcut;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.PrintWriter;

@Interceptor
public class DiscoveryInterceptor
{
	private final Logger _logger = LoggerFactory.getLogger(DiscoveryInterceptor.class);

	@Hook(Pointcut.SERVER_INCOMING_REQUEST_PRE_PROCESSED)
	public boolean incomingRequestPreProcessed(HttpServletRequest theRequest, HttpServletResponse theResponse) throws IOException {
		// Check if the request is for /.well-known/udap
		if (theRequest.getRequestURI().equals("/fhir/.well-known/udap")) {

			_logger.info("Intercepted discovery request, sending security capability statement.");
			PrintWriter writer = theResponse.getWriter();
			writer.write("Does this work?\n");
			writer.close();

			return false;
		}

		// If the request is not for /fhir/.well-known/udap, continue processing the request
		return true;
	}

}
