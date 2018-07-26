/**
 * Copyright © 2018 MiTrust (benoit@m-itrust.com)
 *
 * 						Unauthorized copying of this file, via any
 * 						medium is strictly prohibited Proprietary and confidential
 */
package io.mitrust.demo.serviceprovider;

import org.junit.Assert;
import org.junit.Test;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.client.RestTemplate;

public class TestDemoServiceProviderController {
	final RestTemplate restTemplate = new RestTemplate();

	@Test
	public void testLinkToAuthorize() {
		MockEnvironment env = defaultEnv();

		DemoServiceProviderController controller =
				new DemoServiceProviderController(env, restTemplate, restTemplate.getUriTemplateHandler());

		Assert.assertEquals("http://mitrustHost/someContext" + "/data-sharing/v1/start-sharing-flow?"
				+ "client_id={client_id}&scope={scope}&webhook_uri={webhook_uri}&state={state}&response_type={response_type}",
				controller.makeLinkToMiTrust());
	}

	public MockEnvironment defaultEnv() {
		MockEnvironment env = new MockEnvironment();

		env.setProperty("mitrust_url", "http://mitrustHost/someContext");

		env.setProperty("mitrust.oauth2.client_id", "client_id");
		env.setProperty("mitrust.oauth2.secret_key", "client_secret");
		env.setProperty("mitrust.oauth2.scope", "scope1,scope2");
		return env;
	}

	@Test
	public void testLinkToAuthorize_httpIsHidden() {
		MockEnvironment env = defaultEnv();

		env.setProperty("httpsIsHidden", "true");

		DemoServiceProviderController controller =
				new DemoServiceProviderController(env, restTemplate, restTemplate.getUriTemplateHandler());

		String redirect_uri = controller.redirectUri(new MockHttpServletRequest("GET", "/spHost/someContext"));

		Assert.assertEquals("https://localhost/spHost/webhook", redirect_uri);
	}

	@Test
	public void testLinkToAuthorize_httpIsNOTHidden() {
		MockEnvironment env = defaultEnv();

		// env.setProperty("httpsIsHidden", "true");

		DemoServiceProviderController controller =
				new DemoServiceProviderController(env, restTemplate, restTemplate.getUriTemplateHandler());

		String redirect_uri = controller.redirectUri(new MockHttpServletRequest("GET", "/spHost/someContext"));

		Assert.assertEquals("http://localhost/spHost/webhook", redirect_uri);
	}
}
