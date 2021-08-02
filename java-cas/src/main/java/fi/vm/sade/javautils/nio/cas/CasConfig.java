package fi.vm.sade.javautils.nio.cas;

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

  private CasConfig() {
  }

  static class CasConfigBuilder {
    private final String username;
    private final String password;
    private final String casUrl;
    private final String serviceUrl;
    private final String csrf;
    private final String callerId;
    private final String jSessionName;
    private final String serviceUrlSuffix;
    private String sessionUrl;
    private String serviceTicketHeaderName;

    public CasConfigBuilder(String username, String password, String casUrl, String serviceUrl, String csrf, String callerId, String jSessionName, String serviceUrlSuffix) {
      this.username = username;
      this.password = password;
      this.casUrl = casUrl;
      this.serviceUrl = serviceUrl;
      this.csrf = csrf;
      this.callerId = callerId;
      this.jSessionName = jSessionName;
      this.serviceUrlSuffix = serviceUrlSuffix;
    }

    public CasConfigBuilder setSessionUrl(String sessionUrl) {
      this.sessionUrl = sessionUrl;
      return this;
    }

    public CasConfigBuilder setServiceTicketHeaderName(String serviceTicketHeaderName) {
      this.serviceTicketHeaderName = serviceTicketHeaderName;
      return this;
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
      return casConfig;
    }
  }

  public static CasConfig RingSessionCasConfig(String username, String password, String casUrl, String serviceUrl, String csrf, String callerId) {
    return new CasConfigBuilder(username, password, casUrl, serviceUrl, csrf, callerId, "ring-session", "/auth/cas").build();
  }

  public static CasConfig SpringSessionCasConfig(String username, String password, String casUrl, String serviceUrl, String csrf, String callerId) {
    return new CasConfigBuilder(username, password, casUrl, serviceUrl, csrf, callerId, "JSESSIONID", "/j_spring_cas_security_check").build();
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
}
