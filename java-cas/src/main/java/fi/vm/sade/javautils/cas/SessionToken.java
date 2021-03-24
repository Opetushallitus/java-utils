package fi.vm.sade.javautils.cas;

import okhttp3.Cookie;

import java.net.HttpCookie;

public class SessionToken {
    public final ServiceTicket serviceTicket;
    public final Cookie cookie;

    public SessionToken(ServiceTicket serviceTicket, Cookie cookie) {
        this.serviceTicket = serviceTicket;
        this.cookie = cookie;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        SessionToken session = (SessionToken) o;

        if (!serviceTicket.equals(session.serviceTicket)) return false;
        return cookie.equals(session.cookie);

    }

    @Override
    public int hashCode() {
        int result = serviceTicket.hashCode();
        result = 31 * result + cookie.hashCode();
        return result;
    }

    @Override
    public String toString() {
        return "SessionToken{" +
                "serviceTicket=" + serviceTicket +
                ", cookie=" + cookie +
                '}';
    }
}
