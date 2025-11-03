package server;

import org.bouncycastle.cert.X509CertificateHolder;
import org.bouncycastle.cms.CMSException;
import org.bouncycastle.cms.CMSProcessableByteArray;
import org.bouncycastle.cms.CMSSignedData;
import org.bouncycastle.cms.SignerInformation;
import org.bouncycastle.cms.jcajce.JcaSimpleSignerInfoVerifierBuilder;
import org.bouncycastle.operator.OperatorCreationException;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.Collection;

public class SignatureValidator {

    public boolean verifyDetachedSignature(byte[] data, byte[] signature) {
        try {
            CMSSignedData signedData = new CMSSignedData(new CMSProcessableByteArray(data), signature);
            CertificateFactory certificateFactory = getCertificateFactory();
            var certiciateStore = signedData.getCertificates();
            var signers = signedData.getSignerInfos().getSigners();
            for (SignerInformation signer : signers) {
                Collection<X509CertificateHolder> certificateHolders = certiciateStore.getMatches(signer.getSID());
                for (X509CertificateHolder certHolder : certificateHolders) {
                    try {
                        var certificate = certificateFactory.generateCertificate(new ByteArrayInputStream(certHolder.getEncoded()));
                        if (certificate instanceof X509Certificate x509) {
                            var verifier = new JcaSimpleSignerInfoVerifierBuilder().build(x509);
                            if (signer.verify(verifier)) {
                                return true; // Signature is valid
                            }
                        }
                    } catch (CertificateException | OperatorCreationException | CMSException ignored) {
                        // Bad certificate, continue to the next cert
                    }
                }
            }
        } catch (CMSException | IOException e) {
            return false; // Not readable signature
        }
        return false; // No valid signature found
    }

    private static CertificateFactory getCertificateFactory() {
        CertificateFactory certificateFactory;
        try {
            certificateFactory = CertificateFactory.getInstance("X.509");
        } catch (CertificateException e) {
            throw new IllegalStateException(e);
        }
        return certificateFactory;
    }
}
