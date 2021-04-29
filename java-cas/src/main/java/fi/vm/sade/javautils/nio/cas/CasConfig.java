package fi.vm.sade.javautils.nio.cas;

public class CasConfig {
  private final String username;
  private final String password;
  private final String casUrl;
  private final String serviceUrl;
  private final String csrf;
  private final String callerId;
  private final String jSessionName;
  private final String serviceUrlSuffix;

  public CasConfig(String username, String password, String casUrl, String serviceUrl, String csrf, String callerId, String jSessionName, String serviceUrlSuffix) {
    this.username = username;
    this.password = password;
    this.casUrl = casUrl;
    this.serviceUrl = serviceUrl;
    this.csrf = csrf;
    this.callerId = callerId;
    this.jSessionName = jSessionName;
    this.serviceUrlSuffix = serviceUrlSuffix;
  }

  public static CasConfig RingSessionCasConfig(String username, String password, String casUrl, String serviceUrl, String csrf, String callerId) {
    final String jSessionName = "ring-session";
    final String serviceUrlSuffix = "/auth/cas";
    return new CasConfig(username, password, casUrl, serviceUrl, csrf, callerId, jSessionName, serviceUrlSuffix);
  }

  public static CasConfig SpringSessionCasConfig(String username, String password, String casUrl, String serviceUrl, String csrf, String callerId) {
    final String jSessionName = "JSESSIONID";
    final String serviceUrlSuffix = "/j_spring_cas_security_check";
    return new CasConfig(username, password, casUrl, serviceUrl, csrf, callerId, jSessionName, serviceUrlSuffix);
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

  public String getServiceUrl() {
    return serviceUrl;
  }

  public String getCallerId() {
    return callerId;
  }

  public String getCsrf() {
    return csrf;
  }
}
