/**
 * Copyright © 2018 MiTrust (benoit@m-itrust.com)
 *
 * 						Unauthorized copying of this file, via any
 * 						medium is strictly prohibited Proprietary and confidential
 */
package io.mitrust.demo.serviceprovider;

import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.bridge.SLF4JBridgeHandler;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.DefaultUriBuilderFactory;

// https://stackoverflow.com/questions/40228036/how-to-turn-off-spring-security-in-spring-boot-applicationot
@SpringBootApplication(
		exclude = org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration.class,
		scanBasePackages = "noscan")
public class RunDemoDataSharing {
	protected static final Logger LOGGER = LoggerFactory.getLogger(RunDemoDataSharing.class);

	protected RunDemoDataSharing() {
		// hidden
	}

	public static void main(String[] args) {
		start(args);
	}

	public static ConfigurableApplicationContext start(String... args) {
		SLF4JBridgeHandler.removeHandlersForRootLogger();
		SLF4JBridgeHandler.install();

		List<Class<?>> sources = getSpringConfigClasses();

		SpringApplication app = new SpringApplication(sources.toArray(new Class[0]));

		return app.run(args);
	}

	public static List<Class<?>> getSpringConfigClasses() {
		List<Class<?>> sources = new ArrayList<>();

		// Main entry point
		sources.add(RunDemoDataSharing.class);
		sources.add(RestTemplate.class);
		sources.add(DefaultUriBuilderFactory.class);

		// Security configuration: enables CORS configuration to receive oauth2 authorization code
		sources.add(DemoDataSharingSecurityConfig.class);

		// API
		sources.add(DemoServiceProviderController.class);
		return sources;
	}

}
