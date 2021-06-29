package fi.vm.sade.javautils.nio.cas;

import java.util.Date;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicLong;

public class CasTicketGrantingTicketFetchProcess {
    private static final AtomicLong counter = new AtomicLong(0);
    private final long id;
    private final CompletableFuture<CasTicketGrantingTicket> ticketGrantingTicketProcess;

    public CasTicketGrantingTicketFetchProcess(CompletableFuture<CasTicketGrantingTicket> ticketGrantingTicketProcess) {
        this.id = counter.getAndIncrement();
        this.ticketGrantingTicketProcess = ticketGrantingTicketProcess;
    }

    public CompletableFuture<CasTicketGrantingTicket> getTicketGrantingTicketProcess() {
        return ticketGrantingTicketProcess;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        CasTicketGrantingTicketFetchProcess that = (CasTicketGrantingTicketFetchProcess) o;
        return id == that.id;
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    public static CasTicketGrantingTicketFetchProcess emptyTicketGrantingTicketProcess() {
        return new CasTicketGrantingTicketFetchProcess(CompletableFuture.completedFuture(new CasTicketGrantingTicket("", new Date())));
    }
}