package fi.vm.sade.javautils.nio.cas.impl;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;

import java.util.function.Supplier;

public class CircuitBreakerSupplier<T> implements Supplier<T> {

  Supplier<T> supplier;

  public CircuitBreakerSupplier(String name, CircuitBreakerConfig circuitBreakerConfig, Supplier<T> supplier) {
    this.supplier = CircuitBreaker.decorateSupplier(CircuitBreaker.of(name, circuitBreakerConfig), supplier);
  }

  @Override
  public T get() {
    return this.supplier.get();
  }
}
