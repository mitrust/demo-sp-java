package io.mitrust.demo.serviceprovider;

import java.net.URI;
import java.util.Arrays;

import javax.servlet.http.HttpServletRequest;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.util.UriTemplateHandler;

public class MvcHelpers {
	protected static final Logger LOGGER = LoggerFactory.getLogger(MvcHelpers.class);

	public static String getLocalRoot(HttpServletRequest request) {
		return removeLastPathes(request.getRequestURL().toString(), 1);
	}

	public static String removeLastPathes(String requestURL, int nbToRwemove) {
		if (nbToRwemove == 0) {
			throw new IllegalArgumentException("Not implemented");
		}

		int leftIndex = requestURL.length() - 1;

		// Remove trailing '/'
		while (requestURL.charAt(leftIndex) == '/') {
			leftIndex--;
		}

		// Remove last part
		for (int i = 0; i < nbToRwemove && leftIndex >= 0; i++) {
			leftIndex = requestURL.lastIndexOf('/', leftIndex);
		}

		if (leftIndex < 0) {
			return "";
		} else {
			return requestURL.substring(0, leftIndex);
		}
	}

	public static ResponseEntity<?> redirectTo(UriTemplateHandler uriTemplateHandler,
			String redirection,
			Object... params) {
		URI expandedUrl = uriTemplateHandler.expand(redirection, params);

		LOGGER.debug("Expanded {} and {} into {}", redirection, Arrays.toString(params), expandedUrl.toASCIIString());

		HttpHeaders headers = new HttpHeaders();
		headers.add(HttpHeaders.LOCATION, expandedUrl.toASCIIString());

		return new ResponseEntity<>(headers, HttpStatus.FOUND);
	}

}
