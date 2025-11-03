package server;

import io.vertx.core.Handler;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpServerFileUpload;
import io.vertx.core.http.HttpServerRequest;

import java.util.concurrent.atomic.AtomicReference;

class UploadHandler implements Handler<HttpServerFileUpload> {
    private static final int MAX_BODY_SIZE = 1_000_000;

    private final HttpServerRequest request;
    private final AtomicReference<Buffer> messageBuffer = new AtomicReference<>();
    private final AtomicReference<String> messageContentType = new AtomicReference<>();
    private final AtomicReference<Buffer> signatureBuffer = new AtomicReference<>();
    private final AtomicReference<String> signatureContentType = new AtomicReference<>();

    public UploadHandler(HttpServerRequest request) {
        this.request = request;
    }

    @Override
    public void handle(HttpServerFileUpload upload) {
        var bodyBuffer = Buffer.buffer();
        upload.handler(buffer -> {
            if (bodyBuffer.length() + buffer.length() > MAX_BODY_SIZE) {
                request.response().setStatusCode(413).end("Request body too large");
            } else {
                bodyBuffer.appendBuffer(buffer);
            }
        });
        upload.endHandler(v -> {
            switch (upload.name()) {
                case "message" -> {
                    messageBuffer.set(bodyBuffer);
                    messageContentType.set(upload.contentType());
                }
                case "signature" -> {
                    signatureBuffer.set(bodyBuffer);
                    signatureContentType.set(upload.contentType());
                }
                case null, default -> {
                    // ignore unknown field (should not actually happen)
                }
            }
        });
    }

    public byte[] getMessage() {
        var buffer = messageBuffer.get();
        return buffer == null ? null : buffer.getBytes();
    }

    public byte[] getSignature() {
        var buffer = signatureBuffer.get();
        return buffer == null ? null : buffer.getBytes();
    }

    public String getMessageContentType() {
        return messageContentType.get();
    }

    public String getSignatureContentType() {
        return signatureContentType.get();
    }
}
