package server;

import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpServerRequest;

class RequestHandler implements Handler<HttpServerRequest> {

    private static final String API_PATH = "/api/";

    private final SenderValidator senderValidator = new SenderValidator();
    private final Vertx vertx;
    private final MessageService messageService;

    RequestHandler(Vertx vertx, MessageService messageService) {
        this.vertx = vertx;
        this.messageService = messageService;
    }

    @Override
    public void handle(HttpServerRequest request) {
        try {
            if (!request.path().startsWith(API_PATH)) {
                request.response().setStatusCode(404).end("Invalid request path");
                return;
            }

            var contentType = request.getHeader("Content-Type");
            if (!contentType.startsWith("multipart/form-data")) {
                request.response().setStatusCode(415).end("Not multipart/form-data");
                return;
            }

            var sender = request.path().substring(API_PATH.length());
            if (!senderValidator.isKnownSender(sender)) {
                request.response().setStatusCode(404).end("Unknown sender");
                return;
            }

            // Check if the connection has a certificate with a registered DN for this sender
            var certificates = request.connection().peerCertificates();
            if (certificates == null) {
                throw new IllegalStateException("No peer certificates");
            }
            if (!senderValidator.isValidSenderCert(sender, certificates)) {
                request.response().setStatusCode(401).end("No valid DN in certificate");
                return;
            }

            var uploadHandler = new UploadHandler(request);
            request.setExpectMultipart(true);
            request.uploadHandler(uploadHandler);

            request.endHandler(v -> {
                if (uploadHandler.getMessage() == null) {
                    request.response().setStatusCode(400).end("Missing 'message'");
                }
                // TODO: check if 'return' required
                if (uploadHandler.getSignature() == null) {
                    request.response().setStatusCode(400).end("Missing 'signature'");
                }
                if (uploadHandler.getMessageContentType() == null) {
                    request.response().setStatusCode(400).end("Missing Content-Type of 'message'");
                }
                if (uploadHandler.getSignatureContentType() == null) {
                    request.response().setStatusCode(400).end("Missing Content-Type of 'signature'");
                }
                vertx.executeBlocking(() -> {
                            messageService.processMessage(sender, uploadHandler.getMessage(), uploadHandler.getMessageContentType(), uploadHandler.getSignature(), uploadHandler.getSignatureContentType(), request);
                            return null;
                        })
                        .onSuccess(event -> request.response().setStatusCode(202).end())
                        .onFailure(throwable -> {
                            request.response().setStatusCode(500).end();
                            throwable.printStackTrace();
                        });
            });
        } catch (Exception e) {
            request.response().setStatusCode(500).end(); // TODO: check if vert.x would return 500 if the exception is not caught
            e.printStackTrace();
        }
    }
}
