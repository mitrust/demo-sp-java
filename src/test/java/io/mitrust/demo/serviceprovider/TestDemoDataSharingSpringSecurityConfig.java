/**
 * Copyright © 2018 MiTrust (benoit@m-itrust.com)
 *
 * 						Unauthorized copying of this file, via any
 * 						medium is strictly prohibited Proprietary and confidential
 */
package io.mitrust.demo.serviceprovider;

import java.util.Map;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockServletContext;
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.result.MockMvcResultHandlers;
import org.springframework.test.web.servlet.result.MockMvcResultMatchers;
import org.springframework.test.web.servlet.setup.DefaultMockMvcBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.client.RestOperations;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.util.UriTemplateHandler;

import com.google.common.collect.ImmutableMap;

// https://stackoverflow.com/questions/30526247/how-to-mock-rest-template-using-mockito
@RunWith(SpringRunner.class)
@WebAppConfiguration
@SpringBootTest(classes = { MockServletContext.class,
		DemoDataSharingSecurityConfig.class,
		DemoServiceProviderController.class,
		TestDemoDataSharingSpringSecurityConfig.ComplementForTestDemoDataSharingSpringSecurityConfig.class })
public class TestDemoDataSharingSpringSecurityConfig {

	@SpringBootApplication
	@ComponentScan("none")
	public static class ComplementForTestDemoDataSharingSpringSecurityConfig {
		@Bean
		public RestOperations restOperations() {
			RestOperations restOperations = Mockito.mock(RestOperations.class);

			Mockito.when(restOperations.exchange(Mockito
					.anyString(), Mockito.any(), Mockito.any(), Mockito.eq(Map.class), Mockito.any(Object[].class)))
					.thenReturn(ResponseEntity.ok(ImmutableMap.of("k", "v")));

			return restOperations;
		}

		@Bean
		public UriTemplateHandler uriTemplateHandler() {
			return Mockito.mock(UriTemplateHandler.class);
		}
	}

	@Autowired
	private WebApplicationContext wac;

	public MockMvc mockMvc;

	@Before
	public void setup() {
		DefaultMockMvcBuilder builder = MockMvcBuilders.webAppContextSetup(this.wac)
				.apply(SecurityMockMvcConfigurers.springSecurity())
				.dispatchOptions(true);
		this.mockMvc = builder.build();
	}

	final String apiQuery = "/data/v1/webhook?code=someCode&state=someState";

	@Test
	public void testCors_MiTrustOrigin_GET() throws Exception {
		this.mockMvc
				.perform(MockMvcRequestBuilders.get(apiQuery).header(HttpHeaders.ORIGIN, "https://api.m-itrust.com"))
				.andDo(MockMvcResultHandlers.print())
				.andExpect(MockMvcResultMatchers.status().isOk())
				.andExpect(MockMvcResultMatchers.content().contentType(MediaType.APPLICATION_JSON_UTF8));
	}

	@Test
	public void testCors_MiTrustOrigin_OPTIONS() throws Exception {
		this.mockMvc
				.perform(MockMvcRequestBuilders.options(apiQuery)
						.header(HttpHeaders.ORIGIN, "https://api.m-itrust.com")
						.header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD)
						.header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, HttpMethod.GET.name()))
				.andDo(MockMvcResultHandlers.print())
				.andExpect(MockMvcResultMatchers.status().isOk())
				.andExpect(
						MockMvcResultMatchers.header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_METHODS, "OPTIONS,GET"))
				.andExpect(MockMvcResultMatchers.content().string(""));
	}

	@Test
	public void testCors_MiTrustOrigin_WrongScheme() throws Exception {
		this.mockMvc.perform(MockMvcRequestBuilders.get(apiQuery).header(HttpHeaders.ORIGIN, "http://api.m-itrust.com"))
				.andDo(MockMvcResultHandlers.print())
				.andExpect(MockMvcResultMatchers.status().isForbidden());
	}

	@Test
	public void testCors_MissingOriginHeader() throws Exception {
		this.mockMvc.perform(MockMvcRequestBuilders.get(apiQuery))
				.andDo(MockMvcResultHandlers.print())
				.andExpect(MockMvcResultMatchers.status().isOk())
				.andExpect(MockMvcResultMatchers.content().contentType(MediaType.APPLICATION_JSON_UTF8));
	}

	@Test
	public void testCors_ExternalOrigin() throws Exception {
		this.mockMvc
				.perform(
						MockMvcRequestBuilders.get(apiQuery).header(HttpHeaders.ORIGIN, "http://some.other-domain.com"))
				.andDo(MockMvcResultHandlers.print())
				.andExpect(MockMvcResultMatchers.status().isForbidden());
	}
}
