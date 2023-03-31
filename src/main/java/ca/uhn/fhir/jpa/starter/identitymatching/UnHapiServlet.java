package ca.uhn.fhir.jpa.starter.identitymatching;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import java.io.IOException;

@RestController
@RequestMapping("sec")
public class UnHapiServlet extends HttpServlet  {

	private static final Logger _Logger = LoggerFactory.getLogger(UnHapiServlet.class);

	@Override
	public final void init() throws ServletException {
		_Logger.info("Initializing UnHapi resful server.");
	}

	@RequestMapping(value = "/.well-known/udap", produces = "application/json", method = RequestMethod.GET)
	public String getDiscover() throws IOException {
		try {
			String test = "Did this work?";
			return test;
		}
		catch(Exception ex) {
			_Logger.error(ex.getMessage());
			throw new IOException(ex.getMessage());
		}
	}

}