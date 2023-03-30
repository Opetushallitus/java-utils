package fi.vm.sade.javautils.nio.cas;

import java.util.Optional;
import java.util.concurrent.TimeUnit;

public class CasConfig {
  private String username;
  private String password;
  private String casUrl;
  private String serviceUrl;
  private String csrf;
  private String callerId;
  private String jSessionName;
  private String serviceUrlSuffix;
  private String sessionUrl;
  private String serviceTicketHeaderName;
  private long sessionTicketValidMs;
  private long ticketGrantingTicketValidMs;
  private int numberOfRetries;
  private boolean refreshTicketsOnLoginRedirect;

  private CasConfig() {
  }

  public static class CasConfigBuilder {
    private final String username;
    private final String password;
    private final String casUrl;
    private final String serviceUrl;
    private final String csrf;
    private final String callerId;
    private String jSessionName;
    private final String serviceUrlSuffix;
    private String sessionUrl;
    private String serviceTicketHeaderName;
    private Optional<Long> sessionTicketValidMs = Optional.empty();
    private Optional<Long> ticketGrantingTicketValidMs = Optional.empty();
    private Optional<Integer> numberOfRetries = Optional.empty();
    private boolean refreshTicketsOnLoginRedirect;

    public CasConfigBuilder(String username, String password, String casUrl, String serviceUrl, String csrf, String callerId, String serviceUrlSuffix) {
      this.username = username;
      this.password = password;
      this.casUrl = casUrl;
      this.serviceUrl = serviceUrl;
      this.csrf = csrf;
      this.callerId = callerId;
      this.serviceUrlSuffix = serviceUrlSuffix;
    }

    public CasConfigBuilder setSessionUrl(String sessionUrl) {
      this.sessionUrl = sessionUrl;
      return this;
    }
    public CasConfigBuilder setNumberOfRetries(int numberOfRetries) {
      this.numberOfRetries = Optional.of(numberOfRetries);
      return this;
    }
    public CasConfigBuilder setServiceTicketHeaderName(String serviceTicketHeaderName) {
      this.serviceTicketHeaderName = serviceTicketHeaderName;
      return this;
    }
    public CasConfigBuilder sessionTicketValid(TimeUnit unit, long duration) {
      this.sessionTicketValidMs = Optional.of(unit.toMillis(duration));
      return this;
    }
    public CasConfigBuilder ticketGrantingTicketValidMs(TimeUnit unit, long duration) {
      this.ticketGrantingTicketValidMs = Optional.of(unit.toMillis(duration));
      return this;
    }
    public CasConfigBuilder setJsessionName(String jSessionName) {
      this.jSessionName = jSessionName;
      return this;
    }
    public CasConfigBuilder setRefreshTicketsOnLoginRedirect(boolean refreshTicketsOnLoginRedirect) {
      this.refreshTicketsOnLoginRedirect = refreshTicketsOnLoginRedirect;
    }

    public CasConfig build()
    {
      CasConfig casConfig = new CasConfig();
      casConfig.username = this.username;
      casConfig.password = this.password;
      casConfig.casUrl = this.casUrl;
      casConfig.serviceUrl = this.serviceUrl;
      casConfig.csrf = this.csrf;
      casConfig.callerId = this.callerId;
      casConfig.jSessionName = this.jSessionName;
      casConfig.serviceUrlSuffix = this.serviceUrlSuffix;
      casConfig.sessionUrl = this.sessionUrl;
      casConfig.serviceTicketHeaderName = this.serviceTicketHeaderName;
      casConfig.ticketGrantingTicketValidMs = this.ticketGrantingTicketValidMs.orElseGet(() -> TimeUnit.HOURS.toMillis(7));
      casConfig.sessionTicketValidMs = this.sessionTicketValidMs.orElseGet(() -> TimeUnit.MINUTES.toMillis(15));
      casConfig.numberOfRetries = this.numberOfRetries.orElse(1);
      casConfig.refreshTicketsOnLoginRedirect = this.refreshTicketsOnLoginRedirect;
      return casConfig;
    }
  }

  public static CasConfig RingSessionCasConfig(String username, String password, String casUrl, String serviceUrl, String csrf, String callerId, boolean refreshTicketsOnLoginRedirect) {
    return new CasConfigBuilder(username, password, casUrl, serviceUrl, csrf, callerId, "/auth/cas")
            .setJsessionName("ring-session")
            .setRefreshTicketsOnLoginRedirect(refreshTicketsOnLoginRedirect)
            .build();
  }

  public static CasConfig SpringSessionCasConfig(String username, String password, String casUrl, String serviceUrl, String csrf, String callerId, boolean refreshTicketsOnLoginRedirect) {
    return new CasConfigBuilder(username, password, casUrl, serviceUrl, csrf, callerId, "/j_spring_cas_security_check")
            .setJsessionName("JSESSIONID")
            .setRefreshTicketsOnLoginRedirect(refreshTicketsOnLoginRedirect)
            .build();
  }

  public String getjSessionName() {
    return jSessionName;
  }

  public String getPassword() {
    return password;
  }

  public String getServiceUrlSuffix() {
    return serviceUrlSuffix;
  }

  public String getUsername() {
    return username;
  }

  public String getCasUrl() {
    return casUrl;
  }

  public String getServiceUrl() { return serviceUrl; }

  public String getCallerId() {
    return callerId;
  }

  public String getCsrf() {
    return csrf;
  }

  public String getSessionUrl() {
    return sessionUrl == null ? serviceUrl + getServiceUrlSuffix() : sessionUrl;
  }

  public String getServiceTicketHeaderName() {
    return serviceTicketHeaderName == null ? null : serviceTicketHeaderName;
  }

  public long getSessionTicketValidMs() {
    return sessionTicketValidMs;
  }

  public long getTicketGrantingTicketValidMs() {
    return ticketGrantingTicketValidMs;
  }

  public int getNumberOfRetries() {
    return numberOfRetries;
  }

  public boolean getRefreshTicketsOnLoginRedirect() { return refreshTicketsOnLoginRedirect; }
}
