package dev.veltraph.client.observation.nativeinput;

import org.junit.jupiter.api.Test;
import static dev.veltraph.client.observation.nativeinput.NativePlayerBirthWireOrder.Kind;
import static dev.veltraph.client.observation.nativeinput.NativePlayerBirthWireOrder.Node;
import static org.junit.jupiter.api.Assertions.*;

class NativePlayerBirthBundleOrderTest {
    private final Object owner = new Object(), channel = new Object(), list = new Object(), bundle = new Object();
    private Node source(Object input) {
        var result = new Node(Kind.TRANSPORT, owner, channel, input, null);
        result.returned(true);
        return result;
    }

    @Test void GenuineLoginAndPositionMembersKeepOriginalIdentitiesThroughWholeBundleHandler() {
        var login = new Object(); var position = new Object();
        var order = new NativePlayerBirthBundleOrder(owner, channel, list, source(new Object()));
        assertTrue(order.memberAdded(list, login, source(login), true));
        assertTrue(order.memberAdded(list, position, source(position), true));
        order.constructed(list, bundle, source(bundle), source(new Object()));
        var handler = order.handler(bundle); var iterator = new Object();
        assertTrue(handler.iteratorReturned(list, iterator));
        assertTrue(handler.nextReturned(iterator, login)); assertTrue(handler.beginMember(login));
        assertTrue(handler.nextReturned(iterator, position)); assertTrue(handler.beginMember(position));
        assertFalse(handler.complete());
        handler.finish(true);
        assertTrue(handler.complete());
    }

    @Test void ForeignPacketInsertedIntoMutableNativeListCannotBecomeAnOriginalMember() {
        var original = new Object();
        var order = new NativePlayerBirthBundleOrder(owner, channel, list, source(original));
        order.memberAdded(list, original, source(original), true);
        order.constructed(list, bundle, source(bundle), source(new Object()));
        var handler = order.handler(bundle); var iterator = new Object();
        handler.iteratorReturned(list, iterator);
        assertFalse(handler.nextReturned(iterator, new Object()));
        handler.finish(true);
        assertFalse(handler.complete());
    }

    @Test void ReorderedNativeMembersRefuseWholeBundle() {
        var first = new Object(); var second = new Object();
        var order = new NativePlayerBirthBundleOrder(owner, channel, list, source(first));
        order.memberAdded(list, first, source(first), true); order.memberAdded(list, second, source(second), true);
        order.constructed(list, bundle, source(bundle), source(new Object()));
        var handler = order.handler(bundle); var iterator = new Object();
        handler.iteratorReturned(list, iterator);
        assertFalse(handler.nextReturned(iterator, second));
    }

    @Test void MissingMemberOrOriginalHandlerThrowCannotAuthorizeEarlierHandledLogin() {
        var first = new Object(); var second = new Object();
        var order = new NativePlayerBirthBundleOrder(owner, channel, list, source(first));
        order.memberAdded(list, first, source(first), true); order.memberAdded(list, second, source(second), true);
        order.constructed(list, bundle, source(bundle), source(new Object()));
        var handler = order.handler(bundle); var iterator = new Object();
        handler.iteratorReturned(list, iterator); handler.nextReturned(iterator, first); handler.beginMember(first);
        handler.finish(true);
        assertFalse(handler.complete());
    }

    @Test void ConstructorNeedsOriginalListAndEveryMemberSourceNormalReturn() {
        var pending = new Node(Kind.DECODE, owner, channel, new Object(), list);
        var order = new NativePlayerBirthBundleOrder(owner, channel, list, source(new Object()));
        order.memberAdded(list, pending.input, pending, true);
        var whole = order.constructed(list, bundle, source(bundle), source(new Object()));
        assertFalse(whole.complete());
        pending.returned(true);
        assertTrue(whole.complete());
        var wrong = new NativePlayerBirthBundleOrder(owner, channel, list, source(new Object()));
        assertNull(wrong.constructed(new Object(), bundle, source(bundle), source(new Object())));
    }

    @Test void NativeNextReturnDoesNotPermitSameMemberToRunTwice() {
        var packet = new Object();
        var order = new NativePlayerBirthBundleOrder(owner, channel, list, source(packet));
        order.memberAdded(list, packet, source(packet), true);
        order.constructed(list, bundle, source(bundle), source(new Object()));
        var handler = order.handler(bundle); var iterator = new Object();
        handler.iteratorReturned(list, iterator); handler.nextReturned(iterator, packet);
        assertTrue(handler.beginMember(packet));
        assertFalse(handler.beginMember(packet));
    }
}
