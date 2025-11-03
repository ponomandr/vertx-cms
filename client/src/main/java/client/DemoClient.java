package client;

import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.client5.http.entity.mime.MultipartEntityBuilder;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManagerBuilder;
import org.apache.hc.client5.http.ssl.ClientTlsStrategyBuilder;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.apache.hc.core5.ssl.SSLContexts;
import org.bouncycastle.cert.jcajce.JcaCertStore;
import org.bouncycastle.cms.CMSProcessableByteArray;
import org.bouncycastle.cms.CMSSignedDataGenerator;
import org.bouncycastle.cms.jcajce.JcaSignerInfoGeneratorBuilder;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.bouncycastle.operator.jcajce.JcaDigestCalculatorProviderBuilder;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.cert.X509Certificate;
import java.util.List;

public class DemoClient {

    public static void main(String[] args) throws Exception {

        var message = "<hello/>";

        var trustStorePath = Path.of("secrets/client-truststore.p12");
        var trustStorePassword = "password".toCharArray();

        var tlsKeyStorePath = Path.of("secrets/client-tls-keystore.p12");
        var tlsKeyStorePassword = "password".toCharArray();
        var tlsKeyPassword = "password".toCharArray();

        var cmsKeyStorePath = Path.of("secrets/client-cms-keystore.p12");
        var cmsKeyStorePassword = "password".toCharArray();
        var cmsKeyPassword = "password".toCharArray();
        var cmsKeyAlias = "client-cms";

        var trustStore = KeyStore.getInstance("PKCS12");
        try (var inputStream = Files.newInputStream(trustStorePath)) {
            trustStore.load(inputStream, trustStorePassword);
        }
        var tlsKeyStore = KeyStore.getInstance("PKCS12");
        try (var inputStream = Files.newInputStream(tlsKeyStorePath)) {
            tlsKeyStore.load(inputStream, tlsKeyStorePassword);
        }
        var cmsKeyStore = KeyStore.getInstance("PKCS12");
        try (var inputStream = Files.newInputStream(cmsKeyStorePath)) {
            cmsKeyStore.load(inputStream, cmsKeyStorePassword);
        }

        var cmsCertificate = (X509Certificate) cmsKeyStore.getCertificate(cmsKeyAlias);
        var cmsPrivateKey = (PrivateKey) cmsKeyStore.getKey(cmsKeyAlias, cmsKeyPassword);

        var contentSigner = new JcaContentSignerBuilder("SHA256withRSA").build(cmsPrivateKey);
        var digestCalculatorProvider = new JcaDigestCalculatorProviderBuilder().build();
        var signerInfoGenerator = new JcaSignerInfoGeneratorBuilder(digestCalculatorProvider).build(contentSigner, cmsCertificate);
        var signedDataGenerator = new CMSSignedDataGenerator();
        signedDataGenerator.addSignerInfoGenerator(signerInfoGenerator);
        signedDataGenerator.addCertificates(new JcaCertStore(List.of(cmsCertificate)));

        var signedData = signedDataGenerator.generate(new CMSProcessableByteArray(message.getBytes()), false);
        var signature = signedData.getEncoded();

        var sslContext = SSLContexts.custom()
                .setKeyStoreType("PKCS12")
                .loadKeyMaterial(tlsKeyStore, tlsKeyPassword)
                .loadTrustMaterial(trustStore, null)
                .build();
        var tlsSocketStrategy = ClientTlsStrategyBuilder.create()
                .setSslContext(sslContext)
                .buildClassic();
        var connectionManager = PoolingHttpClientConnectionManagerBuilder.create()
                .setTlsSocketStrategy(tlsSocketStrategy)
                .build();

        try (var httpClient = HttpClients.custom().setConnectionManager(connectionManager).build()) {
            var multipartEntityBuilder = MultipartEntityBuilder.create()
//                    .setContentType(ContentType.parse("multipart/signed; protocol=application/pkcs7-signature; micalg=sha-256"))
                    .setContentType(ContentType.parse("multipart/form-data"))
                    .addBinaryBody("message", message.getBytes(), ContentType.parse("application/vnd.realnet-central.inst.v1+xml"), "message.xml")
                    .addBinaryBody("signature", signature, ContentType.parse("application/pkcs7-signature"), "smime.p7s");

            var httpPost = new HttpPost("https://localhost:8443/api/DEMOCLIENT");
            var multipartEntity = multipartEntityBuilder.build();
            httpPost.setEntity(multipartEntity);

            httpClient.execute(httpPost, response -> {
                System.out.println("Status Code: " + response.getCode()); // should be 202 Accepted
                System.out.println("Entity: " + EntityUtils.toString(response.getEntity()));
                return null;
            });
        }
    }
}
