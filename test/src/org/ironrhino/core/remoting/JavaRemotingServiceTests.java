package org.ironrhino.core.remoting;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.Collections;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import javax.validation.ConstraintViolationException;

import org.ironrhino.core.metadata.Scope;
import org.ironrhino.core.remoting.JavaRemotingServiceTests.RemotingConfiguration;
import org.ironrhino.core.remoting.client.HttpInvokerClient;
import org.ironrhino.sample.remoting.BarService;
import org.ironrhino.sample.remoting.FooService;
import org.ironrhino.sample.remoting.PersonRepository;
import org.ironrhino.sample.remoting.TestService;
import org.ironrhino.sample.remoting.TestService.FutureType;
import org.ironrhino.security.domain.User;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.util.concurrent.ListenableFuture;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

@RunWith(SpringRunner.class)
@ContextConfiguration(classes = RemotingConfiguration.class)
@TestPropertySource(properties = "httpInvoker.serializationType=JAVA")
public class JavaRemotingServiceTests {

	public static final int THREADS = 100;

	public static final int LOOP = 100;

	private static ExecutorService executorService;

	@Autowired
	protected TestService testService;

	@Autowired
	protected FooService fooService;

	@Autowired
	protected BarService barService;

	@Autowired
	protected PersonRepository personRepository;

	@BeforeClass
	public static void setup() {
		executorService = Executors.newFixedThreadPool(THREADS);
	}

	@AfterClass
	public static void destroy() {
		executorService.shutdown();
	}

	@Test
	public void testJdbcRepository() {
		assertNotNull(personRepository.findAll());
	}

	@Test
	public void testServiceImplementedByFactoryBean() {
		assertEquals("test", fooService.test("test"));
	}

	@Test
	public void testServiceRegistriedInConfigurationClass() {
		assertEquals("test", barService.test("test"));
	}

	@Test
	public void testEcho() {
		testService.ping();
		assertEquals("test", testService.defaultEcho("test"));
		assertEquals("", testService.echo());
		assertNull(testService.echo((String) null));
		assertEquals("test", testService.echo("test"));
		assertEquals(Collections.singletonList("list"), testService.echoList(Collections.singletonList("list")));
		assertTrue(Arrays.equals(new String[] { "echoWithArrayList" },
				testService.echoArray(new String[] { "echoWithArrayList" })));
		assertEquals(3, testService.countAndAdd(Collections.singletonList("test"), 2));
		TestService.Immutable value = new TestService.Immutable(12, "test");
		assertEquals(value, testService.echoImmutable(value));
	}

	@Test
	public void testEchoListWithArray() {
		assertEquals("test",
				testService.echoListWithArray(Collections.singletonList(new String[] { "test" })).get(0)[0]);
	}

	@Test
	public void testConcreteType() {
		assertNull(testService.loadUserByUsername(null));
		assertEquals("username", testService.loadUserByUsername("username").getUsername());
		assertNull(testService.searchUser(null));
		assertEquals(Collections.EMPTY_LIST, testService.searchUser(""));
		assertEquals("username", testService.searchUser("username").get(0).getUsername());
	}

	@Test
	public void testNonConcreteType() {
		User user = new User();
		user.setUsername("test");
		assertEquals(user.getUsername(), testService.echoUserDetails(user).getUsername());
		assertNull(testService.loadUserDetailsByUsername(null));
		assertEquals("username", testService.loadUserDetailsByUsername("username").getUsername());
		assertNull(testService.searchUserDetails(null));
		assertEquals(Collections.EMPTY_LIST, testService.searchUserDetails(""));
		assertEquals("username", testService.searchUserDetails("username").get(0).getUsername());
	}

	@Test(expected = ConstraintViolationException.class)
	public void testBeanValidation() throws Exception {
		assertEquals(Scope.LOCAL, testService.echoScope(Scope.LOCAL));
		testService.echoScope((Scope) null);
	}

	@Test(expected = ConstraintViolationException.class)
	public void testBeanValidationWithValid() throws Exception {
		User user = new User();
		user.setEmail("test@test.com");
		assertEquals(user, testService.echoUser(user));
		user.setEmail("iamnotemail");
		testService.echoUser(user);
	}

	@Test(expected = IllegalArgumentException.class)
	public void testOnException() throws Exception {
		testService.throwException("this is a message");
	}

	@Test
	public void testConcreteOptional() {
		assertFalse(testService.loadOptionalUserByUsername("").isPresent());
		assertEquals("username", testService.loadOptionalUserByUsername("username").get().getUsername());
	}

	@Test
	public void testNonConcreteOptional() {
		assertFalse(testService.loadOptionalUserDetailsByUsername("").isPresent());
		assertEquals("username", testService.loadOptionalUserDetailsByUsername("username").get().getUsername());
	}

	@Test(expected = IllegalArgumentException.class)
	public void testConcreteOptionalWithException() {
		testService.loadOptionalUserByUsername(null);
	}

	@Test(expected = IllegalArgumentException.class)
	public void testNonConcreteOptionalWithException() {
		testService.loadOptionalUserDetailsByUsername(null);
	}

	@Test
	public void testConcreteFuture() throws Exception {
		for (FutureType futureType : FutureType.values()) {
			assertEquals("username", testService.loadFutureUserByUsername("username", futureType).get().getUsername());
		}
	}

	@Test
	public void testNonConcreteFuture() throws Exception {
		for (FutureType futureType : FutureType.values()) {
			assertEquals("username",
					testService.loadFutureUserDetailsByUsername("username", futureType).get().getUsername());
		}
	}

	@Test
	public void testFutureWithNullUsername() throws Exception {
		for (FutureType futureType : FutureType.values()) {
			boolean error = false;
			try {
				testService.loadFutureUserByUsername(null, futureType).get();
			} catch (ExecutionException e) {
				assertTrue(e.getCause() instanceof IllegalArgumentException);
				error = true;
			}
			assertTrue(error);
		}
	}

	@Test
	public void testFutureWithBlankUsername() throws Exception {
		for (FutureType futureType : FutureType.values()) {
			boolean error = false;
			try {
				testService.loadFutureUserByUsername("", futureType).get();
			} catch (ExecutionException e) {
				assertTrue(e.getCause() instanceof IllegalArgumentException);
				error = true;
			}
			assertTrue(error);
		}
	}

	@Test
	public void testConcreteListenableFuture() throws Exception {
		ListenableFuture<User> future = testService.loadListenableFutureUserByUsername("username");
		AtomicBoolean b1 = new AtomicBoolean();
		AtomicBoolean b2 = new AtomicBoolean();
		future.addCallback(u -> {
			b1.set("username".equals(u.getUsername()));
		}, e -> {
			b2.set(true);
		});
		Thread.sleep(1000);
		assertTrue(b1.get());
		assertFalse(b2.get());
		assertEquals("username", future.get().getUsername());
	}

	@Test
	public void testNonConcreteListenableFuture() throws Exception {
		ListenableFuture<? extends UserDetails> future = testService
				.loadListenableFutureUserDetailsByUsername("username");
		AtomicBoolean b1 = new AtomicBoolean();
		AtomicBoolean b2 = new AtomicBoolean();
		future.addCallback(u -> {
			b1.set("username".equals(u.getUsername()));
		}, e -> {
			b2.set(true);
		});
		Thread.sleep(1000);
		assertTrue(b1.get());
		assertFalse(b2.get());
		assertEquals("username", future.get().getUsername());
	}

	@Test
	public void testConcreteCallable() throws Exception {
		assertEquals("username", testService.loadCallableUserByUsername("username").call().getUsername());
	}

	@Test
	public void testNonConcreteCallable() throws Exception {
		assertEquals("username", testService.loadCallableUserDetailsByUsername("username").call().getUsername());
	}

	@Test(expected = IllegalArgumentException.class)
	public void testCallableWithNullUsername() throws Exception {
		testService.loadCallableUserByUsername(null).call();
	}

	@Test(expected = IllegalArgumentException.class)
	public void testCallableWithBlankUsername() throws Exception {
		testService.loadCallableUserByUsername("").call();
	}

	@Test
	public void testConcurreny() throws InterruptedException {
		final CountDownLatch cdl = new CountDownLatch(THREADS);
		final AtomicInteger count = new AtomicInteger();
		long time = System.currentTimeMillis();
		for (int i = 0; i < THREADS; i++) {

			executorService.execute(() -> {
				for (int j = 0; j < LOOP; j++) {
					assertEquals("test" + j, testService.echo("test" + j));
					count.incrementAndGet();
				}
				cdl.countDown();
			});
		}
		cdl.await();
		time = System.currentTimeMillis() - time;
		System.out.println(getClass().getSimpleName() + " completed " + count.get() + " requests with concurrency("
				+ THREADS + ") in " + time + "ms (tps = " + (count.get() * 1000 / time) + ")");
		assertEquals(count.get(), THREADS * LOOP);
	}

	@Configuration
	static class RemotingConfiguration {

		@Bean
		public HttpInvokerClient testService() {
			HttpInvokerClient hic = new HttpInvokerClient();
			hic.setServiceInterface(TestService.class);
			hic.setHost("localhost");
			return hic;
		}

		@Bean
		public HttpInvokerClient fooService() {
			HttpInvokerClient hic = new HttpInvokerClient();
			hic.setServiceInterface(FooService.class);
			hic.setHost("localhost");
			return hic;
		}

		@Bean
		public HttpInvokerClient barService() {
			HttpInvokerClient hic = new HttpInvokerClient();
			hic.setServiceInterface(BarService.class);
			hic.setHost("localhost");
			return hic;
		}

		@Bean
		public HttpInvokerClient personRepository() {
			HttpInvokerClient hic = new HttpInvokerClient();
			hic.setServiceInterface(PersonRepository.class);
			hic.setHost("localhost");
			return hic;
		}

		@Bean
		public LocalValidatorFactoryBean validatorFactory() {
			return new LocalValidatorFactoryBean();
		}

	}

}