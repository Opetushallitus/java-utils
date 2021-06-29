package fi.vm.sade.javautils.nio.cas;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Date;

public class CasTicketGrantingTicket {
    private static final Logger logger = LoggerFactory.getLogger(CasTicketGrantingTicket.class);

    private final String ticketGrantingTicket;
    private final Date validUntil;

    public CasTicketGrantingTicket(String ticketGrantingTicket, Date validUntil) {
        this.ticketGrantingTicket = ticketGrantingTicket;
        this.validUntil = validUntil;
    }

    public boolean isValid() {
        final boolean valid = new Date().before(validUntil);
        logger.info("Checking if TGT " + ticketGrantingTicket + " is valid? Valid = " + valid);
        return valid;
    }

    public String getTicketGrantingTicket() { return ticketGrantingTicket; }
}
