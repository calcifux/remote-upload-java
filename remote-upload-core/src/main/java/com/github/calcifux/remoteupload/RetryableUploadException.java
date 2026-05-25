package com.github.calcifux.remoteupload;

/**
 * Signals a <strong>transient</strong> upload failure — a network blip, a 5xx
 * response, a timeout, a connection reset. Retrying the same request later may
 * succeed.
 *
 * <p>Callers with a retry budget (an offline outbox, a sync coordinator) should
 * re-enqueue with backoff when they catch this.
 *
 * @since 1.0.0
 */
public class RetryableUploadException extends RemoteUploadException {

    /** @param message human-readable description of the failure */
    public RetryableUploadException(String message) {
        super(message);
    }

    /**
     * @param message human-readable description of the failure
     * @param cause   original throwable that triggered this exception
     */
    public RetryableUploadException(String message, Throwable cause) {
        super(message, cause);
    }
}
