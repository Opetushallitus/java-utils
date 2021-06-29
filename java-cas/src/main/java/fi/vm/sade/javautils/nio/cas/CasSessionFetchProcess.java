package fi.vm.sade.javautils.nio.cas;

import java.util.Date;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicLong;

public class CasSessionFetchProcess {
  private static final AtomicLong counter = new AtomicLong(0);
  private final long id;
  private final CompletableFuture<CasSession> sessionProcess;

  public CasSessionFetchProcess(CompletableFuture<CasSession> sessionProcess) {
    this.id = counter.getAndIncrement();
    this.sessionProcess = sessionProcess;
  }

  public CompletableFuture<CasSession> getSessionProcess() {
    return sessionProcess;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    CasSessionFetchProcess that = (CasSessionFetchProcess) o;
    return id == that.id;
  }

  @Override
  public int hashCode() {
    return Objects.hash(id);
  }

  public static CasSessionFetchProcess emptySessionProcess() {
    return new CasSessionFetchProcess(CompletableFuture.completedFuture(new CasSession("", new Date())));
  }
}
