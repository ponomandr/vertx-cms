package server;

import io.vertx.core.http.HttpServerRequest;

public class MessageService {

    private final SignatureValidator signatureValidator = new SignatureValidator();

    public void processMessage(String sender, byte[] messageBody, String messageContentType, byte[] signatureBody, String signatureContentType, HttpServerRequest request) {
        System.out.println("Sender: " + sender);
        System.out.println("Message Content-Type: " + messageContentType);
        System.out.println("Signature Content-Type: " + signatureContentType);

        System.out.println(new String(messageBody));

        if (!signatureValidator.verifyDetachedSignature(messageBody, signatureBody)) {
            request.response().setStatusCode(400).end("Invalid signature");
            System.out.println("Invalid signature");
        }
    }
}
