/**
 * Copyright © 2018 MiTrust (benoit@m-itrust.com)
 *
 * 						Unauthorized copying of this file, via any
 * 						medium is strictly prohibited Proprietary and confidential
 */
package io.mitrust.demo.serviceprovider;

import java.util.stream.Stream;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;
import org.springframework.test.context.junit4.SpringRunner;

@RunWith(SpringRunner.class)
public class TestDemoSpringConfigAutonomy {

	protected static final Logger LOGGER = LoggerFactory.getLogger(TestDemoSpringConfigAutonomy.class);

	@Configuration
	@PropertySource({ "/test.properties" })
	public static class RunMiTrustSpringConfigComplement {

	}

	@Test
	public void testAutonomy() {
		System.setProperty("spring.profiles.active", "default");

		Class<?>[] classes = Stream.concat(RunDemoDataSharing.getSpringConfigClasses().stream(),
				Stream.of(RunMiTrustSpringConfigComplement.class)).toArray(Class[]::new);
		try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext(classes)) {
			LOGGER.debug("Started on {}", context.getStartupDate());
		} finally {
			System.setProperty("spring.profiles.active", "");
		}
	}
}