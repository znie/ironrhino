package org.ironrhino.sample.api;

import org.ironrhino.rest.ApiConfigBase;
import org.ironrhino.sample.api.interceptor.LoggingInteceptor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;

@Configuration
@ComponentScan
public class ApiConfig extends ApiConfigBase {

	@Bean
	public LoggingInteceptor loggingInteceptor() {
		return new LoggingInteceptor();
	}

	@Override
	protected void addInterceptors(InterceptorRegistry registry) {
		registry.addInterceptor(loggingInteceptor());
	}
}