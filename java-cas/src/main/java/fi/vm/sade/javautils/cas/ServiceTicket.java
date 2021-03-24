package fi.vm.sade.javautils.cas;

import java.net.URI;

public class ServiceTicket {
    public final String service;
    public final String serviceTicket;

    public ServiceTicket(String service, String serviceTicket) {
        this.service = service;
        this.serviceTicket = serviceTicket;
    }

    public String getLoginUrl() {
        return this.service + "?ticket=" + this.serviceTicket;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        ServiceTicket that = (ServiceTicket) o;

        if (!service.equals(that.service)) return false;
        return serviceTicket.equals(that.serviceTicket);

    }

    @Override
    public int hashCode() {
        int result = service.hashCode();
        result = 31 * result + serviceTicket.hashCode();
        return result;
    }

    @Override
    public String toString() {
        return "ServiceTicket{" +
                "service='" + service + '\'' +
                ", serviceTicket='" + serviceTicket + '\'' +
                '}';
    }
}
