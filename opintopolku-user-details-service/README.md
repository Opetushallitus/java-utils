# UserDetailsService

Spring Securityn UserDetailsService-rajapinnan toteutus, joka hakee käyttäjän tiedot käyttöoikeuspalvelusta.

## Konfigurointi

XML

    <beans:bean id="authenticationUserDetailsService" class="fi.vm.sade.javautils.kayttooikeusclient.OphUserDetailsServiceImpl" />

    <beans:bean id="casAuthenticationProvider" class="org.springframework.security.cas.authentication.CasAuthenticationProvider">
        <beans:property name="authenticationUserDetailsService" ref="authenticationUserDetailsService"/>
        ...
    </beans:bean>

Java

    @Bean
    public CasAuthenticationProvider casAuthenticationProvider() {
        CasAuthenticationProvider casAuthenticationProvider = new CasAuthenticationProvider();
        casAuthenticationProvider.setAuthenticationUserDetailsService(new OphUserDetailsServiceImpl());
        ...
        return casAuthenticationProvider;
    }
