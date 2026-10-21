package dev.veltraph.client.observation.nativeinput;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class NativePlayerBirthWireBytesTest {
    @Test void PrimitiveReceiptCopiesBytesBeforeAnOuterCallbackCanRewriteOriginalArray() {
        byte[] original = {1, 2, 3};
        var receipt = new NativePlayerBirthWireBytes(4, original);
        original[0] = 9;
        assertTrue(receipt.matches(4, new byte[]{1, 2, 3}));
        assertFalse(receipt.matches(4, original));
    }

    @Test void SameBufferIdentityDoesNotAuthenticateDifferentContents() {
        var receipt = new NativePlayerBirthWireBytes(0, new byte[]{10, 11, 12});
        assertFalse(receipt.matches(0, new byte[]{10, 99, 12}));
        assertFalse(receipt.matches(0, new byte[]{10, 11}));
        assertFalse(receipt.matches(0, new byte[]{10, 11, 12, 13}));
    }

    @Test void FrameExtractionDerivesExactRangeFromOriginalBytes() {
        var original = new NativePlayerBirthWireBytes(0, new byte[]{3, 21, 22, 23, 8});
        var frame = original.copied(1, 3, 0);
        assertTrue(frame.matches(0, new byte[]{21, 22, 23}));
        assertFalse(frame.matches(0, new byte[]{21, 22, 99}));
    }

    @Test void ConsumedPrefixDoesNotReenterNextFragment() {
        var first = new NativePlayerBirthWireBytes(4, new byte[]{90, 91, 1, 2});
        var next = new NativePlayerBirthWireBytes(0, new byte[]{3, 4});
        var combined = NativePlayerBirthWireBytes.cumulated(first, 6, false, next, 0, 0);
        assertTrue(combined.matches(0, new byte[]{1, 2, 3, 4}));
        assertFalse(combined.matches(0, new byte[]{90, 91, 1, 2, 3, 4}));
    }

    @Test void KnownNewBytesCannotCleanseUnknownCumulation() {
        var incoming = new NativePlayerBirthWireBytes(0, new byte[]{1, 2});
        assertNull(NativePlayerBirthWireBytes.cumulated(null, 0, false, incoming, 0, 0));
        assertTrue(NativePlayerBirthWireBytes.cumulated(null, 0, true, incoming, 0, 7)
                .matches(7, new byte[]{1, 2}));
    }

    @Test void OriginalCursorBoundsRefuseForgedRanges() {
        var receipt = new NativePlayerBirthWireBytes(4, new byte[]{1, 2, 3});
        assertNull(receipt.copied(3, 1, 0));
        assertNull(receipt.copied(4, 4, 0));
        assertNull(receipt.copied(4, -1, 0));
        assertNull(receipt.readableFrom(8));
        assertFalse(receipt.matches(3, new byte[]{1, 2, 3}));
    }

    @Test void CompactionRebasesOnlyPreviouslyQualifiedUnreadBytes() {
        var receipt = new NativePlayerBirthWireBytes(4, new byte[]{1, 2, 3, 4});
        var compacted = receipt.rebasedUnread(6, 0);
        assertTrue(compacted.matches(0, new byte[]{3, 4}));
        assertFalse(compacted.matches(0, new byte[]{1, 2, 3, 4}));
    }

    @Test void ReturnedSnapshotCannotMutatePrivateExpectedBytes() {
        var receipt = new NativePlayerBirthWireBytes(0, new byte[]{1, 2, 3});
        byte[] firstCopy = receipt.readableFrom(0);
        firstCopy[1] = 9;
        assertTrue(receipt.matches(0, new byte[]{1, 2, 3}));
    }

    @Test void ConsumedAuthenticatedPrefixCannotAuthorizeAnAppendedFakePacket() {
        var receipt = new NativePlayerBirthWireBytes(0, new byte[]{1, 2, 3});
        assertTrue(receipt.matchesSpan(3, 3, new byte[]{1, 2, 3}));
        assertFalse(receipt.matchesSpan(3, 6, new byte[]{1, 2, 3}));
        var empty = new NativePlayerBirthWireBytes(3, new byte[0]);
        assertTrue(empty.matchesSpan(3, 3, new byte[0]));
        assertFalse(empty.matchesSpan(3, 6, new byte[0]));
    }

    @Test void NativeSpanCannotMoveReaderBeforeOriginOrWriterBeyondOrigin() {
        var receipt = new NativePlayerBirthWireBytes(4, new byte[]{1, 2, 3});
        assertTrue(receipt.matchesSpan(4, 7, new byte[]{1, 2, 3}));
        assertTrue(receipt.matchesSpan(7, 7, new byte[]{1, 2, 3}));
        assertFalse(receipt.matchesSpan(3, 7, new byte[]{1, 2, 3}));
        assertFalse(receipt.matchesSpan(8, 7, new byte[]{1, 2, 3}));
        assertFalse(receipt.matchesSpan(4, 6, new byte[]{1, 2, 3}));
    }
}
