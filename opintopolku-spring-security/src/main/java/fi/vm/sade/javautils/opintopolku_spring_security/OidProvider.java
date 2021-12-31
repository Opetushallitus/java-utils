package fi.vm.sade.javautils.opintopolku_spring_security;

import java.util.List;

public interface OidProvider {
    List<String> getSelfAndParentOids(String organisaatioOid);
}
