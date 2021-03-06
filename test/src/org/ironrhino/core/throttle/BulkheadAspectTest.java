package org.ironrhino.core.throttle;

import static org.junit.Assert.assertEquals;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.ironrhino.core.throttle.BulkheadAspectTest.BulkheadConfiguration;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringRunner;

import io.github.resilience4j.bulkhead.BulkheadFullException;

@RunWith(SpringRunner.class)
@ContextConfiguration(classes = BulkheadConfiguration.class)
public class BulkheadAspectTest {

	@Autowired
	private TestService testService;

	@Test
	public void testPermit() throws Exception {
		test(0);
	}

	public void testNotPermit() throws Exception {
		test(5);
	}

	private void test(int overflow) throws Exception {
		ExecutorService es = Executors.newFixedThreadPool(TestService.MAX_CONCURRENT_CALLS + overflow);
		AtomicInteger success = new AtomicInteger();
		AtomicInteger error = new AtomicInteger();
		for (int i = 0; i < TestService.MAX_CONCURRENT_CALLS + overflow; i++) {
			es.execute(() -> {
				try {
					testService.test();
					success.getAndIncrement();
				} catch (BulkheadFullException e) {
					error.getAndIncrement();
				}
			});
		}
		es.shutdown();
		es.awaitTermination(100, TimeUnit.MILLISECONDS);
		assertEquals(TestService.MAX_CONCURRENT_CALLS, success.get());
		assertEquals(overflow, error.get());
	}

	public static class TestService {
		public static final int MAX_CONCURRENT_CALLS = 10;

		@Bulkhead(maxConcurrentCalls = MAX_CONCURRENT_CALLS)
		public void test() {
			try {
				Thread.sleep(10);
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
		}

	}

	@Configuration
	@EnableAspectJAutoProxy(proxyTargetClass = true)
	static class BulkheadConfiguration {

		@Bean
		public TestService testService() {
			return new TestService();
		}

		@Bean
		public BulkheadAspect bulkheadAspect() {
			return new BulkheadAspect();
		}

	}
}
