package com.github.calcifux.remoteupload.spring.support;

import com.github.calcifux.remoteupload.UploadContent;
import com.github.calcifux.remoteupload.UploadResult;
import com.github.calcifux.remoteupload.UploadTarget;

import java.io.IOException;

/**
 * Test {@link UploadTarget} that drains the body into memory and records what it
 * received, so assertions can inspect bytes and metadata.
 */
public class InMemoryTarget implements UploadTarget {

    public byte[] received;
    public UploadContent seen;

    @Override
    public UploadResult upload(UploadContent content) throws IOException {
        this.seen = content;
        this.received = content.getBody().readAllBytes();
        return UploadResult.builder().key("mem://key").etag("mem-etag").build();
    }
}
