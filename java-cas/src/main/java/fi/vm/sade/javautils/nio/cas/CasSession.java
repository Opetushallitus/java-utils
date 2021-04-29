package fi.vm.sade.javautils.nio.cas;

import java.util.Date;

public class CasSession {
  private final String sessionCookie;
  private final Date validUntil;

  public CasSession(String sessionCookie, Date validUntil) {
    this.sessionCookie = sessionCookie;
    this.validUntil = validUntil;
  }

  public boolean isValid() {
    final boolean valid = new Date().before(validUntil);
    // System.out.println("Checking if '" + sessionCookie + "' is valid? Valid = " + valid);
    return valid;
  }

  public String getSessionCookie() {
    return sessionCookie;
  }
}
