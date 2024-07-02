package fi.vm.sade.javautils.nio.cas.impl;

import org.junit.Assert;
import org.junit.Test;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

public class CachedSupplierTest {

  @Test
  public void testGet() {
    Supplier<String> s = new CachedSupplier<>(10000, () -> "test");
    Assert.assertEquals("test", s.get());
  }

  @Test
  public void testCachedWithinTTL() {
    Supplier<String> s = new CachedSupplier<>(10000, new Supplier<>() {
      boolean called = false;

      @Override
      public String get() {
        if(called) throw new RuntimeException();
        called = true;
        return "test";
      }
    });
    Assert.assertEquals("test", s.get());
  }

  @Test
  public void testRefreshWhenExpired() throws Exception {
    Supplier<String> s = new CachedSupplier<>(100, new Supplier<>() {
      Queue<String> queue = new LinkedList<>();
      {
        queue.add("test");
        queue.add("test2");
      }

      @Override
      public String get() {
        return queue.remove();
      }
    });
    Assert.assertEquals("test", s.get());
    Thread.sleep(150);
    Assert.assertEquals("test2", s.get());
  }

  @Test
  public void testExceptionPropagated() throws Exception {
    Supplier<String> s = new CachedSupplier<>(100, () -> {
      throw new RuntimeException("test");
    });

    try {
      s.get();
      Assert.fail();
    } catch (RuntimeException e) {
      Assert.assertEquals("test", e.getMessage());
    }
  }

  @Test
  public void testExceptionNotCached() {
    Supplier<String> s = new CachedSupplier<>(100000, new Supplier<>() {
      Queue<String> queue = new LinkedList<>();
      {
        queue.add("test");
        queue.add("test2");
      }

      @Override
      public String get() {
        throw new RuntimeException(queue.remove());
      }
    });

    try {
      s.get();
      Assert.fail();
    } catch (RuntimeException e) {
      Assert.assertEquals("test", e.getMessage());
    }
    try {
      s.get();
      Assert.fail();
    } catch (RuntimeException e) {
      Assert.assertEquals("test2", e.getMessage());
    }
  }

  @Test
  public void testSynchronized() {
    final int NUMBER_OF_THREADS = 50;
    final int INVOCATIONS_PER_THREAD = 100;

    // supplier contains a non atomic counter
    Supplier<String> s = new CachedSupplier<>(100000, new Supplier<>() {
      int counter = 0;

      @Override
      public String get() {
        throw new RuntimeException(counter++ + "");
      }
    });

    // have multiple threads increment the counter
    Collection<CompletableFuture<Void>> futures = new ArrayList<>();
    for(int i = 0; i < NUMBER_OF_THREADS; i++) {
      CompletableFuture<Void> f = new CompletableFuture<>();
      futures.add(f);
      new Thread(() -> {
        for(int j = 0; j < INVOCATIONS_PER_THREAD; j++) {
          try {
            s.get();
          } catch (Exception e) {}
        }
        f.complete(null);
      }).start();
    }

    // increments match total number of invocations
    CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
    try {
      s.get();
      Assert.fail();
    } catch (RuntimeException e) {
      Assert.assertEquals(NUMBER_OF_THREADS*INVOCATIONS_PER_THREAD + "", e.getMessage());
    }
  }
}
