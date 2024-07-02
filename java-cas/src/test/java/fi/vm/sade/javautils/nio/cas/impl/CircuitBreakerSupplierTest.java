package fi.vm.sade.javautils.nio.cas.impl;

import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import org.junit.Assert;
import org.junit.Test;

import java.time.Duration;
import java.util.LinkedList;
import java.util.Queue;
import java.util.function.Supplier;

public class CircuitBreakerSupplierTest {

  private static final int MINIMUM_NUMBER_OF_CALLS = 6;
  private static final int PERMITTED_IN_HALF_OPEN = 4;
  private static final int WAIT_IN_OPEN_STATE = 100;

  CircuitBreakerConfig circuitBreakerConfig = CircuitBreakerConfig.custom()
      .failureRateThreshold(50)
      .slowCallRateThreshold(50)
      .waitDurationInOpenState(Duration.ofMillis(WAIT_IN_OPEN_STATE))
      .slowCallDurationThreshold(Duration.ofSeconds(10))
      .permittedNumberOfCallsInHalfOpenState(4)
      .minimumNumberOfCalls(6)
      .slidingWindowType(CircuitBreakerConfig.SlidingWindowType.TIME_BASED)
      .slidingWindowSize(10)
      .build();

  @Test
  public void testGet() {
    Supplier<String> s = new CircuitBreakerSupplier<>("abc", circuitBreakerConfig, () -> "test");
    Assert.assertEquals("test", s.get());
  }

  @Test
  public void testOpens() {
    Supplier<String> s = new CircuitBreakerSupplier<>("abc", circuitBreakerConfig, () -> {
      throw new RuntimeException();
    });

    // after a configured number of failed calls, circuitbreaker will start throwing CallNotPermittedExceptions
    for(int i=0;i<MINIMUM_NUMBER_OF_CALLS;i++) {
      try {
        s.get();
      } catch (RuntimeException e) {}
    }

    try {
      s.get();
      Assert.fail();
    } catch (CallNotPermittedException e) {
      return;
    }
    Assert.fail();
  }

  @Test
  public void testRecovery() throws Exception {
    Supplier<String> s = new CircuitBreakerSupplier<>("abc", circuitBreakerConfig, new Supplier<String>() {
      Queue<Boolean> queue = new LinkedList<>();
      {
        // calls in open state
        queue.add(false);
        queue.add(false);
        queue.add(false);
        queue.add(false);
        queue.add(false);
        queue.add(false);

        // calls in half-open state
        queue.add(true);
        queue.add(true);
        queue.add(true);
        queue.add(true);

        // calls in open state
        queue.add(false);
      }

      @Override
      public String get() {
        if(queue.remove()) {
          return "success";
        }
        throw new RuntimeException("failure");
      }
    });

    // after a configured number of failed calls, circuitbreaker will open
    for(int i=0;i<MINIMUM_NUMBER_OF_CALLS;i++) {
      try {
        s.get();
      } catch (RuntimeException e) {}
    }

    // ...  and start throwing CallNotPermittedExceptions
    try {
      s.get();
      Assert.fail();
    } catch (CallNotPermittedException e) {}

    // after we wait for circuit breaker to turn into half open state
    Thread.sleep(WAIT_IN_OPEN_STATE);

    // ... limited number of calls go through to original supplier
    for(int i=0;i<PERMITTED_IN_HALF_OPEN;i++) {
      s.get();
    }

    // ... and as more than half succeed, the circuitbreaker opens again
    try {
      s.get();
      Assert.fail();
    } catch (RuntimeException e) {
      Assert.assertEquals("failure", e.getMessage());
      return;
    }
    Assert.fail();
  }

}
