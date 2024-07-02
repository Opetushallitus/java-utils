package fi.vm.sade.javautils.nio.cas.impl;

import java.time.Instant;
import java.util.function.Supplier;

public class CachedSupplier<T> implements Supplier<T> {

  private T value;
  private Instant validUntil;
  private final long ttlMs;

  private final Supplier<T> supplier;

  public CachedSupplier(long ttlMs, Supplier<T> supplier) {
    this.ttlMs = ttlMs;
    this.supplier = supplier;
  }

  public void clear() {
    synchronized (this) {
      this.value = null;
      this.validUntil = null;
    }
  }

  @Override
  public T get() {
    synchronized (this) {
      Instant now = Instant.now();
      try {
        if(value == null || validUntil.isBefore(now)) {
          value = supplier.get();
          validUntil = Instant.now().plusMillis(this.ttlMs);
        }
      } catch (Throwable t) {
        this.clear();
        throw t;
      }
      return value;
    }
  }
}
