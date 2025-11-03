package server;

import javax.naming.InvalidNameException;
import javax.naming.ldap.LdapName;
import javax.security.auth.x500.X500Principal;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static java.util.Collections.emptyList;

class SenderValidator {

    private static final Map<String, List<LdapName>> SENDER_DN;

    static {
        try {
            SENDER_DN = Map.of("DEMOCLIENT", List.of(new LdapName("CN=Demo Client")));
        } catch (InvalidNameException e) {
            throw new IllegalStateException(e);
        }
    }

    boolean isKnownSender(String dn) {
        return SENDER_DN.containsKey(dn);
    }

    boolean isValidSenderCert(String sender, List<Certificate> certificates) {
        var allowedDnList = SENDER_DN.getOrDefault(sender, emptyList());
        var certDnList = certificates.stream()
                .filter(X509Certificate.class::isInstance)
                .map(X509Certificate.class::cast)
                .map(X509Certificate::getSubjectX500Principal)
                .map(X500Principal::toString)
                .map(this::toLdapNameOrNull)
                .filter(Objects::nonNull)
                .toList();
        return certDnList.stream()
                .anyMatch(dn -> allowedDnList.stream().anyMatch(allowedDn -> allowedDn.endsWith(dn)));
    }

    private LdapName toLdapNameOrNull(String name) {
        try {
            return new LdapName(name);
        } catch (InvalidNameException e) {
            return null;
        }
    }
}
