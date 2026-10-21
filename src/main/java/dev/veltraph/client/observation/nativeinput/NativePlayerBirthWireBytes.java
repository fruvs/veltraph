package dev.veltraph.client.observation.nativeinput;

import java.util.Arrays;

/** Immutable byte origin data; transformations derive from this data rather than promoting current buffers. */
final class NativePlayerBirthWireBytes {
    private final byte[] bytes;
    final int originalIndex;

    NativePlayerBirthWireBytes(int originalIndex, byte[] actualPrimitiveOutput) {
        if (originalIndex < 0 || actualPrimitiveOutput == null) throw new IllegalArgumentException("native range");
        this.originalIndex = originalIndex;
        this.bytes = actualPrimitiveOutput.clone();
    }

    int size() { return bytes.length; }

    byte[] readableFrom(int originalReaderIndex) {
        int offset = originalReaderIndex - originalIndex;
        if (offset < 0 || offset > bytes.length) return null;
        return Arrays.copyOfRange(bytes, offset, bytes.length);
    }

    NativePlayerBirthWireBytes copied(int originalReaderIndex, int count, int resultReaderIndex) {
        int offset = originalReaderIndex - originalIndex;
        if (offset < 0 || count < 0 || offset > bytes.length - count) return null;
        return new NativePlayerBirthWireBytes(resultReaderIndex, Arrays.copyOfRange(bytes, offset, offset + count));
    }

    NativePlayerBirthWireBytes rebasedUnread(int originalReaderIndex, int resultReaderIndex) {
        byte[] unread = readableFrom(originalReaderIndex);
        return unread == null ? null : new NativePlayerBirthWireBytes(resultReaderIndex, unread);
    }

    boolean matches(int readerIndex, byte[] observedReadableBytes) {
        byte[] expected = readableFrom(readerIndex);
        return expected != null && observedReadableBytes != null && Arrays.equals(expected, observedReadableBytes);
    }

    boolean matchesSpan(int actualReaderIndex, int actualWriterIndex, byte[] observedOriginalRange) {
        if (originalIndex > Integer.MAX_VALUE - bytes.length || actualWriterIndex != originalIndex + bytes.length
                || actualReaderIndex < originalIndex || actualReaderIndex > actualWriterIndex) return false;
        return observedOriginalRange != null && Arrays.equals(bytes, observedOriginalRange);
    }

    static NativePlayerBirthWireBytes cumulated(NativePlayerBirthWireBytes old, int oldReaderIndex,
                                               boolean originalEmpty, NativePlayerBirthWireBytes incoming,
                                               int incomingReaderIndex, int resultReaderIndex) {
        if (incoming == null || !originalEmpty && old == null) return null;
        byte[] prefix = originalEmpty ? new byte[0] : old.readableFrom(oldReaderIndex);
        byte[] suffix = incoming.readableFrom(incomingReaderIndex);
        if (prefix == null || suffix == null || prefix.length > 8388608 - suffix.length) return null;
        byte[] combined = Arrays.copyOf(prefix, prefix.length + suffix.length);
        System.arraycopy(suffix, 0, combined, prefix.length, suffix.length);
        return new NativePlayerBirthWireBytes(resultReaderIndex, combined);
    }
}
