package com.github.calcifux.remoteupload.quarkus.support;

import com.github.calcifux.remoteupload.UploadContent;
import com.github.calcifux.remoteupload.UploadResult;
import com.github.calcifux.remoteupload.UploadTarget;

import java.io.IOException;

/**
 * In-memory {@link UploadTarget} for the Quarkus module test suite: drains the
 * body and records what it received, without a CDI container.
 */
public final class InMemoryTarget implements UploadTarget {

    public byte[] received;
    public UploadContent seen;

    @Override
    public UploadResult upload(UploadContent content) throws IOException {
        this.seen = content;
        this.received = content.getBody().readAllBytes();
        return UploadResult.builder().key("mem://key").etag("mem-etag").build();
    }

    /** A target whose {@link #upload(UploadContent)} always fails — for error paths. */
    public static UploadTarget failing(String message) {
        return content -> {
            throw new IOException(message);
        };
    }
}
