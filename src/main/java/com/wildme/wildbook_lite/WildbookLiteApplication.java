package com.wildme.wildbook_lite;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.retry.annotation.EnableRetry;

/**
 * @EnableRetry activates the Spring Retry AOP proxy so @Retryable
 * annotations (e.g., on EmailSender.send) actually fire. Without it
 * the annotation is a silent no-op.
 */
@SpringBootApplication
@EnableRetry
public class WildbookLiteApplication {

	public static void main(String[] args) {
		SpringApplication.run(WildbookLiteApplication.class, args);
	}

}
