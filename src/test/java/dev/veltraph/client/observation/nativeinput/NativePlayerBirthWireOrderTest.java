package dev.veltraph.client.observation.nativeinput;

import org.junit.jupiter.api.Test;
import static dev.veltraph.client.observation.nativeinput.NativePlayerBirthWireOrder.Kind;
import static dev.veltraph.client.observation.nativeinput.NativePlayerBirthWireOrder.Node;
import static org.junit.jupiter.api.Assertions.*;

class NativePlayerBirthWireOrderTest {
    private final Object channel = new Object(), owner = new Object(), buffer = new Object();
    private Node transport() { return new Node(Kind.TRANSPORT, owner, channel, buffer, null); }
    private Node closedTransport() { var node = transport(); node.returned(true); return node; }

    @Test void AuthenticatedObjectCannotBeConsumedByCallbackSelectedDecoder() {
        var handler=new Object();var context=new Object();var node=closedTransport();
        var edge=new NativePlayerBirthWireOrder.HandlerEdge(handler,context,buffer,node);
        assertFalse(edge.claim(new Object(),context,buffer,node));
        assertFalse(node.complete());
    }

    @Test void OriginalHandlerEdgeRequiresExactRecipientContextAndOneClaim() {
        var handler=new Object();var context=new Object();var node=closedTransport();
        var edge=new NativePlayerBirthWireOrder.HandlerEdge(handler,context,buffer,node);
        assertTrue(edge.claim(handler,context,buffer,node));assertFalse(edge.claim(handler,context,buffer,node));
        var foreign=new NativePlayerBirthWireOrder.HandlerEdge(handler,context,buffer,closedTransport());
        assertFalse(foreign.claim(handler,new Object(),buffer,foreign.delivery));
    }

    @Test void decodedPacketCannotBecomeCurrentUntilAllActualOuterBodiesReturnNormally() {
        var raw = transport();
        var split = new Node(Kind.OUTPUT, owner, channel, new Object(), null, raw);
        var decode = new Node(Kind.DECODE, owner, channel, new Object(), new Object(), split);
        decode.returned(true);
        assertTrue(decode.viable());
        assertFalse(decode.complete());
        split.returned(true);
        assertFalse(decode.complete());
        raw.returned(true);
        assertTrue(decode.complete());
    }

    @Test void OriginalReadThrowRefusesEveryAlreadyDecodedDescendant() {
        var raw = transport();
        var decode = new Node(Kind.DECODE, owner, channel, new Object(), null, raw);
        decode.returned(true);
        raw.returned(false);
        raw.returned(true);
        assertFalse(raw.viable());
        assertFalse(decode.complete());
    }

    @Test void SyntheticObjectDuringGenuineReadCannotBorrowDelivery() {
        var delivery = new NativePlayerBirthWireOrder.Delivery(buffer, channel, owner, closedTransport());
        assertNull(delivery.claim(new Object(), channel));
        assertNull(delivery.claim(buffer, channel));
    }

    @Test void OriginalDeliveredObjectIsClaimedOnceBeforeCallback() {
        var node = closedTransport();
        var delivery = new NativePlayerBirthWireOrder.Delivery(buffer, channel, owner, node);
        assertSame(node, delivery.claim(buffer, channel));
        assertNull(delivery.claim(buffer, channel));
        assertFalse(node.complete());
    }

    @Test void ForeignChannelCannotBorrowAnExactBuffer() {
        var delivery = new NativePlayerBirthWireOrder.Delivery(buffer, channel, owner, closedTransport());
        assertNull(delivery.claim(buffer, new Object()));
    }

    @Test void FragmentedFrameRetainsBothActualReadsAcrossChangedCumulationIdentity() {
        var first = closedTransport();
        var readOne = new Node(Kind.READ, owner, channel, buffer, null, first);
        readOne.returned(true);
        var firstCumulation = NativePlayerBirthWireOrder.cumulated(owner, channel, new Object(), readOne,
                null, true, first);
        assertTrue(firstCumulation.complete());
        var second = closedTransport();
        var readTwo = new Node(Kind.READ, owner, channel, new Object(), null, second);
        readTwo.returned(true);
        var combined = NativePlayerBirthWireOrder.cumulated(owner, channel, new Object(), readTwo,
                firstCumulation, false, second);
        assertTrue(combined.complete());
        first.refuse();
        assertFalse(combined.complete());
    }

    @Test void NewGenuineReadCannotCleanseUnknownUnreadBytes() {
        var input = closedTransport();
        var read = new Node(Kind.READ, owner, channel, buffer, null, input);
        read.returned(true);
        assertNull(NativePlayerBirthWireOrder.cumulated(owner, channel, new Object(), read,
                null, false, input));
        assertFalse(read.complete());
    }

    @Test void UnknownAppendPoisonsQualifiedUnreadPrefix() {
        var prefix = closedTransport();
        var read = new Node(Kind.READ, owner, channel, buffer, null, closedTransport());
        assertNull(NativePlayerBirthWireOrder.cumulated(owner, channel, buffer, read, prefix, false, null));
        assertFalse(prefix.complete());
    }

    @Test void FlowQueueCustodySurvivesTransportReturnAndASeparateDrain() {
        var source = transport();
        var queue = new Object();
        var queued = new NativePlayerBirthWireOrder.Queued(owner, queue, buffer, channel, source);
        source.returned(true);
        assertSame(source, queued.poll(owner, queue, buffer, channel));
        assertTrue(source.complete());
    }

    @Test void SameObjectInForeignQueueCannotBorrowCustody() {
        var source = closedTransport();
        var queued = new NativePlayerBirthWireOrder.Queued(owner, new Object(), buffer, channel, source);
        assertNull(queued.poll(owner, new Object(), buffer, channel));
        assertFalse(source.complete());
    }

    @Test void UnknownDecoderReentryQueuedObjectCannotBorrowOriginalInput() {
        var source = closedTransport();
        var queue = new Object();
        var queued = new NativePlayerBirthWireOrder.Queued(owner, queue, buffer, channel, source);
        assertNull(queued.poll(owner, queue, new Object(), channel));
    }

    @Test void QueueDrainIsExactlyOnceAndRequiresOriginalOwner() {
        var source = closedTransport();
        var queue = new Object();
        var queued = new NativePlayerBirthWireOrder.Queued(owner, queue, buffer, channel, source);
        assertSame(source, queued.poll(owner, queue, buffer, channel));
        assertNull(queued.poll(owner, queue, buffer, channel));
        var wrong = new NativePlayerBirthWireOrder.Queued(owner, queue, buffer, channel, closedTransport());
        assertNull(wrong.poll(new Object(), queue, buffer, channel));
    }

    @Test void DecompressionAndCipherCannotReplaceFailedSourceWithNormalResults() {
        var raw = closedTransport();
        var cipher = new Node(Kind.OUTPUT, owner, channel, new Object(), null, raw);
        cipher.returned(true);
        var decompressed = new Node(Kind.OUTPUT, owner, channel, new Object(), null, cipher);
        decompressed.returned(true);
        assertTrue(decompressed.complete());
        cipher.refuse();
        assertFalse(decompressed.complete());
    }

    @Test void LocalUnpackStillRequiresOriginalHiddenObjectDeliveryAndWholeRead() {
        var raw = transport();
        var hiddenRead = new Node(Kind.READ, owner, channel, new Object(), null, raw);
        var unpacked = new Node(Kind.LOCAL, owner, channel, buffer, null, hiddenRead);
        unpacked.returned(true);
        assertFalse(unpacked.complete());
        raw.returned(true);
        hiddenRead.returned(true);
        assertTrue(unpacked.complete());
    }
}
