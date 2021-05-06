package fi.vm.sade.javautils.nio.cas;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Date;

public class CasSession {
  private static final Logger logger = LoggerFactory.getLogger(CasSession.class);

  private final String sessionCookieName;
  private final String sessionCookie;
  private final Date validUntil;

  public CasSession(String sessionCookieName, String sessionCookie, Date validUntil) {
    this.sessionCookieName = sessionCookieName;
    this.sessionCookie = sessionCookie;
    this.validUntil = validUntil;
  }

  public boolean isValid() {
    final boolean valid = new Date().before(validUntil);
    logger.info("Checking if cookie " + sessionCookieName + ": " + sessionCookie + " is valid? Valid = " + valid);
    return valid;
  }

  public String getSessionCookieName() {
    return sessionCookieName;
  }
  public String getSessionCookie() { return sessionCookie; }
}
