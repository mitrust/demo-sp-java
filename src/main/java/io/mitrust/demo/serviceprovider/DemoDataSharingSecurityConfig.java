/**
 * Copyright © 2018 MiTrust (benoit@m-itrust.com)
 *
 * 						Unauthorized copying of this file, via any
 * 						medium is strictly prohibited Proprietary and confidential
 */
package io.mitrust.demo.serviceprovider;

import java.util.Arrays;

import org.springframework.context.annotation.Bean;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityConfigurerAdapter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

/**
 * The minimal Spring Security configuration to wire MiTrust data-sharing
 * 
 * @author Benoit Lacelle
 *
 */
@EnableWebSecurity
public class DemoDataSharingSecurityConfig extends WebSecurityConfigurerAdapter {

	@Override
	protected void configure(HttpSecurity http) throws Exception {
		// MiTrust will make CORS requests to the SP backend
		http.cors();
	}

	@Bean
	CorsConfigurationSource corsConfigurationSource() {
		CorsConfiguration configuration = new CorsConfiguration();

		// Depending on your needs, you may allow various MiTrust environments
		configuration.setAllowedOrigins(Arrays.asList("https://api.m-itrust.com",
				"https://dev-api.m-itrust.com",
				"https://uat-api.m-itrust.com",
				"http://localhost:8080",
				"http://localhost:4000",
				"http://localhost:4001"));
		configuration.setAllowedMethods(Arrays.asList(HttpMethod.OPTIONS.name(), HttpMethod.GET.name()));
		configuration.setAllowedHeaders(Arrays.asList(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD));
		UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();

		// MiTrust GUI will push the authorization code and state through a GET query
		// It differs from standard OAuth2 as MiTrust needs to propose additional options to the EndUser after the
		// sharing (e.g. enables offline sharing for N months)
		source.registerCorsConfiguration(
				"/" + DemoServiceProviderController.SP_NAMESPACE
						+ DemoServiceProviderController.API_WEBHOOK.substring(1),
				configuration);
		return source;
	}
}