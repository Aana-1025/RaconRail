package dev.reconrail.lifecycle;

import static org.junit.jupiter.api.Assertions.*;
import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.WriteListener;
import java.io.*;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class GatedServletOutputStreamTest {
    @Test void splitsOutputAndRechecksEachChunkOnWritingThread() throws Exception {
        var bytes = new ByteArrayOutputStream(); var delegate = delegate(bytes);
        var checks = new AtomicInteger(); var gate = new IoGate();
        var scope = AuthorityScope.account(UUID.randomUUID(), 1);
        var writer = new GatedServletOutputStream(delegate, gate, scope, ignored -> checks.incrementAndGet());
        byte[] body = new byte[GatedServletOutputStream.MAX_CHUNK_BYTES * 2 + 7];
        writer.write(body);
        assertArrayEquals(body, bytes.toByteArray());
        assertEquals(3, checks.get());
    }

    @Test void staleDurableGenerationWritesNoBytes() {
        var bytes = new ByteArrayOutputStream(); var scope = AuthorityScope.account(UUID.randomUUID(), 2);
        var writer = new GatedServletOutputStream(delegate(bytes), new IoGate(), scope,
                ignored -> { throw new IOException("ACCOUNT_AUTHORITY_STALE"); });
        IOException failure = assertThrows(IOException.class, () -> writer.write(new byte[]{1, 2, 3}));
        assertEquals("ACCOUNT_AUTHORITY_STALE", failure.getMessage());
        assertEquals(0, bytes.size());
    }

    @Test void explicitFlushIsAlsoAuthorizedBecauseItCanReachTheSocket() throws Exception {
        var flushes = new AtomicInteger();
        var delegate = new ServletOutputStream() {
            @Override public boolean isReady() { return true; }
            @Override public void setWriteListener(WriteListener listener) { }
            @Override public void write(int value) { }
            @Override public void flush() { flushes.incrementAndGet(); }
        };
        var gate = new IoGate(); var scope = AuthorityScope.account(UUID.randomUUID(), 1);
        var writer = new GatedServletOutputStream(delegate, gate, scope, ignored -> {});
        writer.flush(); assertEquals(1, flushes.get());
        gate.prepareDeny(scope);
        assertEquals("RESPONSE_AUTHORITY_DENIED", assertThrows(IOException.class, writer::flush).getMessage());
        assertEquals(1, flushes.get());
    }

    private static ServletOutputStream delegate(OutputStream output) {
        return new ServletOutputStream() {
            @Override public boolean isReady() { return true; }
            @Override public void setWriteListener(WriteListener listener) { }
            @Override public void write(int value) throws IOException { output.write(value); }
            @Override public void write(byte[] bytes, int offset, int length) throws IOException { output.write(bytes, offset, length); }
        };
    }
}
