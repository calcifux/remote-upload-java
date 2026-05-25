package com.github.calcifux.remoteupload;

/**
 * Base unchecked exception for failures raised by {@link UploadTarget}
 * implementations while streaming a body to a remote destination.
 *
 * <p>Prefer throwing one of the two subtypes so callers can act on the
 * distinction without parsing messages:
 * <ul>
 *     <li>{@link RetryableUploadException} — transient (network blip, 5xx,
 *         timeout): retrying later may succeed.</li>
 *     <li>{@link TerminalUploadException} — permanent (auth, 4xx, quota,
 *         validation): retrying the same request will fail again.</li>
 * </ul>
 *
 * <p>This retryable/terminal split is the deliberate improvement over
 * {@code remote-download}'s single exception: it lets an outbox / sync
 * coordinator decide between "keep retrying" and "mark failed, surface to user".
 *
 * @since 1.0.0
 */
public class RemoteUploadException extends RuntimeException {

    /**
     * @param message human-readable description of the failure
     */
    public RemoteUploadException(String message) {
        super(message);
    }

    /**
     * @param message human-readable description of the failure
     * @param cause   original throwable that triggered this exception
     */
    public RemoteUploadException(String message, Throwable cause) {
        super(message, cause);
    }

    /**
     * @param cause original throwable that triggered this exception
     */
    public RemoteUploadException(Throwable cause) {
        super(cause);
    }
}
