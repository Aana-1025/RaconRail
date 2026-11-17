package dev.reconrail.lifecycle;

import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.WriteListener;
import java.io.IOException;
import java.time.Duration;
import java.util.Objects;
import java.util.function.LongSupplier;

/**
 * Rechecks authority for every bounded chunk on the actual writing thread.
 * Bytes accepted by the container before a later denial cannot be recalled.
 */
public final class GatedServletOutputStream extends ServletOutputStream {
    public static final int MAX_CHUNK_BYTES = 64 * 1024;
    public static final Duration IO_BUDGET = Duration.ofSeconds(1);

    private final ServletOutputStream delegate;
    private final IoGate gate;
    private final AuthorityScope scope;
    private final AuthorityProbe probe;
    private final LongSupplier nanoTime;

    public GatedServletOutputStream(
            ServletOutputStream delegate, IoGate gate, AuthorityScope scope, AuthorityProbe probe) {
        this(delegate, gate, scope, probe, System::nanoTime);
    }

    GatedServletOutputStream(ServletOutputStream delegate, IoGate gate, AuthorityScope scope,
            AuthorityProbe probe, LongSupplier nanoTime) {
        this.delegate = Objects.requireNonNull(delegate, "delegate");
        this.gate = Objects.requireNonNull(gate, "gate");
        this.scope = Objects.requireNonNull(scope, "scope");
        this.probe = Objects.requireNonNull(probe, "probe");
        this.nanoTime = Objects.requireNonNull(nanoTime, "nanoTime");
    }

    @Override public void write(int value) throws IOException {
        byte[] one = {(byte) value};
        write(one, 0, 1);
    }

    @Override public void write(byte[] bytes, int offset, int length) throws IOException {
        Objects.checkFromIndexSize(offset, length, bytes.length);
        for (int position = offset, remaining = length; remaining > 0;) {
            int chunk = Math.min(remaining, MAX_CHUNK_BYTES);
            writeChunk(bytes, position, chunk);
            position += chunk;
            remaining -= chunk;
        }
    }

    private void writeChunk(byte[] bytes, int offset, int length) throws IOException {
        authorize(() -> delegate.write(bytes, offset, length));
    }

    @FunctionalInterface private interface IoAction { void run() throws IOException; }

    private void authorize(IoAction output) throws IOException {
        long started = nanoTime.getAsLong();
        try {
            gate.withShared(scope, () -> {
                probe.requireCurrent(scope); // JDBC work returns before the socket write.
                output.run();
            });
        } catch (IoGate.AuthorityDeniedException denied) {
            throw new IOException("RESPONSE_AUTHORITY_DENIED", denied);
        } catch (IoGate.AdmissionTimeoutException timeout) {
            throw new IOException("RESPONSE_ADMISSION_TIMEOUT", timeout);
        } catch (IOException failure) {
            throw failure;
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IOException("RESPONSE_WRITE_INTERRUPTED", interrupted);
        } catch (Exception failure) {
            throw new IOException("RESPONSE_WRITE_FAILED", failure);
        }
        if (nanoTime.getAsLong() - started > IO_BUDGET.toNanos()) {
            // Detection is fail-closed for later chunks; blocking Servlet I/O cannot recall this chunk.
            throw new IOException("RESPONSE_IO_BUDGET_EXCEEDED");
        }
    }

    @Override public boolean isReady() { return delegate.isReady(); }
    @Override public void setWriteListener(WriteListener listener) { delegate.setWriteListener(listener); }
    @Override public void flush() throws IOException { authorize(delegate::flush); }
    @Override public void close() throws IOException { authorize(delegate::close); }
}
