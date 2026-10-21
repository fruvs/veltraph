package dev.veltraph.client.testutil;

import dev.veltraph.client.log.LogSink;

import java.util.ArrayList;
import java.util.List;

/** A {@link LogSink} that records lines in memory for assertions. Optionally throws to test failure handling. */
public final class RecordingLogSink implements LogSink {

    public final List<String> lines = new ArrayList<>();
    public boolean closed;
    public boolean throwOnWrite;

    @Override
    public void write(String line) {
        if (throwOnWrite) {
            throw new RuntimeException("boom");
        }
        lines.add(line);
    }

    @Override
    public void close() {
        closed = true;
    }

    @Override
    public String describe() {
        return "recording";
    }

    /** True if any recorded line contains all of the given substrings. */
    public boolean hasLineContaining(String... parts) {
        for (String line : lines) {
            boolean all = true;
            for (String p : parts) {
                if (!line.contains(p)) {
                    all = false;
                    break;
                }
            }
            if (all) {
                return true;
            }
        }
        return false;
    }
}
