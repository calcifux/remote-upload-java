package com.github.calcifux.remoteupload;

/**
 * Signals a <strong>permanent</strong> upload failure — invalid credentials, a
 * 4xx response, quota exceeded, a validation error. Retrying the same request
 * will fail again; the caller must change something (re-auth, fix the payload,
 * escalate) instead of blindly retrying.
 *
 * @since 1.0.0
 */
public class TerminalUploadException extends RemoteUploadException {

    /** @param message human-readable description of the failure */
    public TerminalUploadException(String message) {
        super(message);
    }

    /**
     * @param message human-readable description of the failure
     * @param cause   original throwable that triggered this exception
     */
    public TerminalUploadException(String message, Throwable cause) {
        super(message, cause);
    }
}
