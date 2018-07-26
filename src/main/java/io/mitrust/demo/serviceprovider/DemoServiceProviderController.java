/**
 * Copyright © 2018 MiTrust (benoit@m-itrust.com)
 *
 * 						Unauthorized copying of this file, via any
 * 						medium is strictly prohibited Proprietary and confidential
 */
package io.mitrust.demo.serviceprovider;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.io.UnsupportedEncodingException;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.servlet.http.HttpServletRequest;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.env.Environment;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestOperations;
import org.springframework.web.util.UriTemplateHandler;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.Charsets;
import com.google.common.base.Strings;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.common.collect.ImmutableMap;
import com.google.common.io.CharStreams;

/**
 * Holds a minimal ServiceProvider, expecting MiTrust to provide data about users
 * 
 * @author Benoit Lacelle
 *
 */
@RequestMapping(value = { DemoServiceProviderController.SP_NAMESPACE })
@Controller
public class DemoServiceProviderController {
	private static final String DATA_SHARING_ENTRYPOINT = "data-sharing/v1/share-to-sp";

	protected static final Logger LOGGER = LoggerFactory.getLogger(DemoServiceProviderController.class);

	public static final String SP_NAMESPACE = "data/v1/";

	private final RestOperations restTemplate;
	private final UriTemplateHandler uriTemplateHandler;

	protected final Map<String, String> europCarUserIdToAccessToken = new ConcurrentHashMap<>();
	protected final Map<String, Map<?, ?>> europCarUserIdToDetails = new ConcurrentHashMap<>();

	// On OAuth2 callbacks, we need a way to retrieve EndUser identity given the state parameter
	protected final Cache<String, String> stateToEuropcarUserId =
			CacheBuilder.newBuilder().expireAfterWrite(1, TimeUnit.HOURS).build();

	// TODO: Give URL where is data can be find-back
	private final String MITRUST_CLIENT_ID;
	private final String MITRUST_SECRET_KEY;

	// In Dev mode, the gui is offered typically on localhost:4000 while backend is at localhost:8080
	private final String mitrustBackEnd;

	// Azure would hide https layer to the webapp
	private final boolean httpsIsHidden;

	public DemoServiceProviderController(Environment env,
			RestOperations restTemplate,
			UriTemplateHandler uriTemplateHandler) {
		this.restTemplate = restTemplate;
		this.uriTemplateHandler = uriTemplateHandler;

		this.mitrustBackEnd = env.getRequiredProperty("mitrust_url");
		LOGGER.info("mitrustBackEnd: {}", mitrustBackEnd);

		this.MITRUST_CLIENT_ID = env.getRequiredProperty("mitrust.oauth2.client_id");
		this.MITRUST_SECRET_KEY = env.getRequiredProperty("mitrust.oauth2.secret_key");
		LOGGER.info("MiTrust OAuth2 client_id is: {}", MITRUST_CLIENT_ID);

		// Useful in Azure where HTTPS in managed out of the application
		this.httpsIsHidden = env.getProperty("httpsIsHidden", Boolean.class, false);
	}

	@DeleteMapping("user")
	public ResponseEntity<?> deleteUser(HttpServletRequest httpServletRequest, String user_id) {
		LOGGER.info("Deleting data about user={}", user_id);

		europCarUserIdToDetails.remove(user_id);
		europCarUserIdToAccessToken.remove(user_id);

		return ResponseEntity.ok().build();
	}

	@PatchMapping("user")
	public ResponseEntity<?> refreshUser(HttpServletRequest httpServletRequest, String user_id) {
		LOGGER.info("Refreshing data about user={}", user_id);

		String access_token = europCarUserIdToAccessToken.get(user_id);

		if (Strings.isNullOrEmpty(access_token)) {
			// No access_token: redirect to authorize screen
			return MvcHelpers
					.redirectTo(uriTemplateHandler, SP_NAMESPACE + "/authorize?mode=popup&user_id={user_id}", user_id);
		}

		// We fetch the data synchronously
		fetchAndCacheUserInfo(user_id, access_token);

		// Redirect to the user-page
		return MvcHelpers.redirectTo(uriTemplateHandler, SP_NAMESPACE + "/user?user_id={user_id}", user_id);
	}

	/**
	 * Return an HTML printing the details about given user
	 */
	@GetMapping("user")
	public ResponseEntity<?> getDataAsHtml(HttpServletRequest httpServletRequest, String user_id) {
		Map<?, ?> data = europCarUserIdToDetails.get(user_id);

		if (data == null) {
			// No cached-data, try to fetch it again
			String access_token = europCarUserIdToAccessToken.get(user_id);

			if (Strings.isNullOrEmpty(access_token)) {
				// No access_token: redirect to authorize screen
				return MvcHelpers.redirectTo(uriTemplateHandler,
						SP_NAMESPACE + "/authorize?mode=popup&user_id={user_id}",
						user_id);
			}

			fetchAndCacheUserInfo(user_id, access_token);

			data = europCarUserIdToDetails.get(user_id);

			if (data == null) {
				return ResponseEntity.badRequest().body("Failure finding data");
			}
		}

		ObjectMapper objectMapper = new ObjectMapper();

		Map<?, ?> dataInData = (Map<?, ?>) data.get("data");

		String result = loadToString("/templates/userinfo.html");
		{
			List<Map<?, ?>> userInfo = (List<Map<?, ?>>) dataInData.get("user_info");

			String userContentAsHTML = prettyPrint(objectMapper, userInfo);

			result = result.replaceFirst(Pattern.quote("<!-- USERINFO -->"),
					Matcher.quoteReplacement(userContentAsHTML));
		}
		{
			Map<?, ?> userConsent = (Map<?, ?>) dataInData.get("user_consent");
			if (userConsent != null) {
				// The user_consent may be null if no data have been retrieved
				result = result.replaceFirst(Pattern.quote("<!-- USERCONSENT -->"),
						Matcher.quoteReplacement(userConsent.toString()));
			}
		}

		// return ok(data, "Data for " + user_id);
		return ResponseEntity.ok(result);
	}

	private String prettyPrint(ObjectMapper objectMapper, List<Map<?, ?>> userInfo) {
		try {
			return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(userInfo);
		} catch (JsonProcessingException e) {
			throw new UncheckedIOException(e);
		}
	}

	@GetMapping("raw_user")
	public ResponseEntity<?> getDataAsJson(HttpServletRequest httpServletRequest, String user_id) {
		Map<?, ?> data = europCarUserIdToDetails.get(user_id);

		if (data == null) {
			// No cached-data, try to fetch it again
			String access_token = europCarUserIdToAccessToken.get(user_id);

			if (Strings.isNullOrEmpty(access_token)) {
				// No access_token: redirect to authorize screen
				return MvcHelpers.redirectTo(uriTemplateHandler,
						SP_NAMESPACE + "/authorize?mode=popup&user_id={user_id}",
						user_id);
			}

			fetchAndCacheUserInfo(user_id, access_token);

			data = europCarUserIdToDetails.get(user_id);

			if (data == null) {
				return ResponseEntity.badRequest().body("Failure finding data");
			}
		}

		return ResponseEntity.ok(data);
	}

	/**
	 * As a ServiceProvider, we manage redirection to MiTrust with this method. it will redirect the user to MiTrust
	 * Consent screen, to ask the EndUser to accept sharing given scopes
	 * 
	 * @throws MalformedURLException
	 * @throws UnsupportedEncodingException
	 */
	@GetMapping("authorize")
	public ResponseEntity<?> toMiTrustAutorizeScreen(HttpServletRequest request,
			@RequestParam(defaultValue = "popup") String mode,
			@RequestParam(defaultValue = "address") String scope)
			throws UnsupportedEncodingException, MalformedURLException {
		// We may have another way to identify an EndUser
		String europcarUserId = UUID.randomUUID().toString();

		if (!Arrays.asList("popup", "iframe").contains(mode)) {
			throw new IllegalStateException("The mode for authorization should be either 'popup' or 'iframe'");
		}

		// We generate a new random state
		String state = generateUniqueState();

		// Register to which user this state is associated. It will be used in the callback once MiTrust returns with
		// the data
		stateToEuropcarUserId.put(state, europcarUserId);
		LOGGER.info("We match state={} to user_id={}", state, europcarUserId);

		String linkTemplate = makeLinkToMiTrust();

		Object[] uriVariables = new Object[] { MITRUST_CLIENT_ID, scope, redirectUri(request), state, "code" };

		if ("popup".equals(mode)) {
			// This link could be used in a "<a target='_blank' href='authorize'>" link
			return MvcHelpers.redirectTo(uriTemplateHandler, linkTemplate, uriVariables);
		} else {
			URI fullLink = uriTemplateHandler.expand(linkTemplate, uriVariables);

			// This will open a page inside the service-provider domain, but embedding MiTrust
			// TODO MiTrust should decide if this is a legal usage or not (as iframe are inherently not safe)
			// model.addAttribute("iframe.source", fullLink.toURL().toExternalForm());
			String result = loadToString("/templates/iframe.html");

			// Customize the iframe with MiTrust URL
			result = result.replace("{iframe.source}", fullLink.toURL().toExternalForm());

			return ResponseEntity.ok(result);
		}
	}

	private String generateUniqueState() {
		return UUID.randomUUID().toString();
	}

	private String loadToString(String path) {
		String result;
		try {
			result = CharStreams
					.toString(new InputStreamReader(new ClassPathResource(path).getInputStream(), Charsets.UTF_8));
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
		return result;
	}

	public String makeLinkToMiTrust() {
		String linkTemplate = mitrustBackEnd;

		// io.mitrust.config.IMiTrustServiceProviderApiDefinition.API_AUTHORIZE_SHARING
		linkTemplate += "/data-sharing/v1/start-sharing-flow"
				+ "?client_id={client_id}&scope={scope}&webhook_uri={webhook_uri}&state={state}&response_type={response_type}";
		return linkTemplate;
	}

	public String redirectUri(HttpServletRequest request) {
		// TODO In some contexts (like Azure), the user would see https in his browser, but the application receives
		// only HTTP as https is managed by Azure
		// This is the URL registered as redirect_uri in MiTrust OAuth2-ServiceProvider panel
		String redirectUri = MvcHelpers.getLocalRoot(request) + API_WEBHOOK;

		if (httpsIsHidden) {
			// As we build a redirection URI for an OAuth2 server, we need to restore the proper URI scheme
			if (redirectUri.startsWith("http://")) {
				redirectUri = "https://" + redirectUri.substring("http://".length());
			}
		}

		return redirectUri;
	}

	public static final String API_WEBHOOK = "/webhook";

	/**
	 * Once the EndUser has given consent to share data with SP, the SP receive a notification of the provided
	 * webhook_uri.
	 * 
	 * The webhook should return the URL to which the user may be redirected Redirect-to by MiTrust once the EndUser
	 * gave its consent. The EndUser is redirected here by MiTrust: it is the EndUser browser which is doing this GET
	 * query
	 */
	@GetMapping(API_WEBHOOK)
	public ResponseEntity<?> redirectedFromMiTrust(HttpServletRequest request,
			String code,
			String state,
			String error,
			String error_description) throws RestClientException, URISyntaxException {
		if (Strings.isNullOrEmpty(code)) {
			LOGGER.warn("We received an errored callback: state={} error={} error_description={}",
					state,
					error,
					error_description);
			throw new RuntimeException(
					"We did not received a code: error=" + error + " errorDescription=" + error_description);
		} else if (Strings.isNullOrEmpty(state)) {
			LOGGER.warn("We miss the state parameter", state, error, error_description);
			throw new RuntimeException(
					"We did not received the state: error=" + error + " errorDescription=" + error_description);
		}

		String redirectUrl = redirectUri(request);

		// We prepare a GET query to turn the authorization_code into an access_token
		LinkedMultiValueMap<String, Object> parameters = new LinkedMultiValueMap<>();
		parameters.add("client_id", MITRUST_CLIENT_ID);
		parameters.add("redirect_uri", redirectUrl);
		parameters.add("client_secret", MITRUST_SECRET_KEY);
		parameters.add("code", code);
		parameters.add("grant_type", "authorization_code");

		LOGGER.info("MiTrust called-back with code={} for state={}", code, state);

		return onAccessToken(parameters, access_token -> {
			// The call succeeded: we have an access_token
			LOGGER.info("MiTrust turned code={} to access_token={} for state={}", code, access_token, state);

			String europcarUserId = stateToEuropcarUserId.getIfPresent(state);

			if (Strings.isNullOrEmpty(europcarUserId)) {
				LOGGER.error("Unknown userId. For development purposes, we initialize it lazily");
				europcarUserId = generateUniqueState();
				stateToEuropcarUserId.put(state, europcarUserId);
				// return ResponseEntity.badRequest().body("SP does not known this userId");
			}

			fetchAndCacheUserInfo(europcarUserId, access_token);

			// We have an access_token. We can redirect the EndUser somewhere we use his data
			String redirection = MvcHelpers.getLocalRoot(request) + "/user?user_id=" + europcarUserId;

			return ResponseEntity.ok(ImmutableMap.of("redirect_uri", redirection));
		}, responseEntity -> {
			// The call fail a way or another: we have no access_token
			return ResponseEntity.badRequest().body("Something failed");
		});
	}

	public void fetchAndCacheUserInfo(String state, String access_token) {
		ResponseEntity<Map> userInfo;
		{
			HttpHeaders headers = new HttpHeaders();
			headers.set(HttpHeaders.AUTHORIZATION, "Bearer" + " " + access_token);
			HttpEntity<Map<?, ?>> requestEntity = new HttpEntity<>(headers);

			userInfo = restTemplate
					.exchange(mitrustBackEnd + "/user_data/v1/userinfo", HttpMethod.GET, requestEntity, Map.class);

			// Use the refresh token to get a new access token and new refresh token. You should request a new
			// token before the current access token expires, or catch the AuthenticationTokenExpired error code
			// (109) and then request a refresh token.
		}

		// TODO: Maintain files in-memory and make them available for download: we demonstrate the data is in
		// Europcar. Nop, it is ok the fetch the PDF only when the user request the link as we have an access_token

		// We wrapped the userId in the state
		// String europcarCarUserId = UUID.fromString(state);

		LOGGER.info("MiTrust provided user_info={} for access_token={} and state={}",
				userInfo.getBody(),
				access_token,
				state);
		europCarUserIdToDetails.put(state, userInfo.getBody());
	}

	@GetMapping("errorRetrievingToken")
	public ResponseEntity<?> errorRetrievingToken() {
		return ResponseEntity.badRequest().body(ImmutableMap.of("errorRetrievingToken", "ARG"));
	}

	private <T> ResponseEntity<?> onAccessToken(MultiValueMap<String, ?> parameters,
			Function<String, ? extends ResponseEntity<? extends T>> accessTokenHandler,
			Function<ResponseEntity<?>, ? extends ResponseEntity<? extends T>> errorResponseHandler) {
		// see DefaultOAuth2RequestAuthenticator
		MultiValueMap<String, String> headers = new HttpHeaders();
		String credentials = parameters.getFirst("client_id") + ":" + parameters.getFirst("client_secret");

		// Retrieve an access_token by authenticating the application through BASIC
		headers.add("Authorization",
				"Basic " + Base64.getEncoder().encodeToString(credentials.getBytes(StandardCharsets.UTF_8)));

		HttpEntity<Map<String, ?>> requestEntity = new HttpEntity<>(parameters, headers);
		// We have been called-back by MiTrust with a code allowing us to fetch an access_token
		// Typically error: {"status":"fail","message":"invalid_grant"}
		ResponseEntity<Map> tokenResponseEntity;
		try {
			tokenResponseEntity =
					restTemplate.exchange(mitrustBackEnd + "/oauth/token", HttpMethod.POST, requestEntity, Map.class);
		} catch (RuntimeException e) {
			// TODO if client_secret if false, we would get a 401 here

			// We failed transforming an authorization_code into a token: leave current URL as the provided code is not
			// valid anymore and we do not want to retry converting this code to access-token
			LOGGER.error("Arg", e);

			// return redirectTo("errorRetrievingToken");
			throw e;
		}

		if (tokenResponseEntity.getStatusCodeValue() == 200) {
			Map<?, ?> tokenBody = tokenResponseEntity.getBody();

			// Now we have an access_token, we can ask for userInfo
			String accessToken = (String) tokenBody.get("access_token");

			// TODO: Security checks to ensure the token is legit?

			return accessTokenHandler.apply(accessToken);
		} else {
			return errorResponseHandler.apply(tokenResponseEntity);
		}
	}
}