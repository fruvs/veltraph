package dev.veltraph.client.observation.nativeinput;

import java.util.ArrayDeque;
import java.util.IdentityHashMap;

/** Data custody for actual native operations. Nodes cannot certify themselves or substitute input identities. */
final class NativePlayerBirthWireOrder {
    enum Kind { TRANSPORT, DELIVERY, READ, CUMULATION, DECODE, OUTPUT, QUEUE, LOCAL, BUNDLE }

    static final class Node {
        final Kind kind;
        final Object owner, channel, input, list;
        private final Node[] parents;
        private volatile boolean normalReturned, refused;

        Node(Kind kind, Object owner, Object channel, Object input, Object list, Node... parents) {
            this.kind = kind;
            this.owner = owner;
            this.channel = channel;
            this.input = input;
            this.list = list;
            this.parents = parents.clone();
            if (owner == null || channel == null || input == null) refused = true;
            for (Node parent : this.parents) if (parent == null) refused = true;
        }

        void returned(boolean normal) {
            if (!normal) refused = true;
            else normalReturned = true;
        }

        void refuse() { refused = true; }

        boolean viable() {
            return inspect(false);
        }

        boolean complete() {
            return inspect(true);
        }

        private boolean inspect(boolean requireNormal) {
            var pending = new ArrayDeque<Node>();
            var visited = new IdentityHashMap<Node, Boolean>();
            pending.add(this);
            while (!pending.isEmpty()) {
                Node next = pending.removeLast();
                if (visited.put(next, Boolean.TRUE) != null) continue;
                if (visited.size() > 16384 || next.refused || requireNormal && !next.normalReturned) return false;
                for (Node parent : next.parents) pending.add(parent);
            }
            return true;
        }
    }

    /** A real opcode must consume the exact delivered object once, before any downstream callback. */
    static final class Delivery {
        final Object object, channel, producer;
        final Node node;
        private boolean claimed;

        Delivery(Object object, Object channel, Object producer, Node node) {
            this.object = object;
            this.channel = channel;
            this.producer = producer;
            this.node = node;
        }

        synchronized Node claim(Object expectedObject, Object expectedChannel) {
            if (claimed || object != expectedObject || channel != expectedChannel || node == null || !node.viable()) {
                if (node != null) node.refuse();
                return null;
            }
            claimed = true;
            return node;
        }
    }

    /** Bound only by the actual upstream handler opcode, before its original callback. */
    static final class HandlerEdge {
        final Object handler, context, message;
        final Node delivery;
        private boolean claimed;

        HandlerEdge(Object handler, Object context, Object message, Node delivery) {
            this.handler=handler;this.context=context;this.message=message;this.delivery=delivery;
        }

        synchronized boolean claim(Object originalHandler,Object originalContext,Object originalMessage,Node originalDelivery) {
            if(claimed||handler!=originalHandler||context!=originalContext||message!=originalMessage
                    ||delivery!=originalDelivery||delivery==null||!delivery.viable()) {
                if(delivery!=null)delivery.refuse();return false;
            }
            claimed=true;return true;
        }
    }

    /** Qualified unread bytes persist across reads; an unknown append poisons that exact cumulation. */
    static Node cumulated(Object owner, Object channel, Object result, Node read,
                          Node oldUnread, boolean originalEmpty, Node newInput) {
        if (read == null || newInput == null || !read.viable() || !newInput.viable()
                || !originalEmpty && (oldUnread == null || !oldUnread.viable())) {
            if (read != null) read.refuse();
            if (oldUnread != null) oldUnread.refuse();
            return null;
        }
        Node combined = originalEmpty
                ? new Node(Kind.CUMULATION, owner, channel, result, null, read, newInput)
                : new Node(Kind.CUMULATION, owner, channel, result, null, read, oldUnread, newInput);
        combined.returned(true);
        return combined;
    }

    /** Queue custody is retained per exact object and per original queue, across asynchronous drains. */
    static final class Queued {
        final Object owner, queue, object, channel;
        final Node source;
        private boolean polled;

        Queued(Object owner, Object queue, Object object, Object channel, Node source) {
            this.owner = owner;
            this.queue = queue;
            this.object = object;
            this.channel = channel;
            this.source = source;
        }

        synchronized Node poll(Object originalOwner, Object originalQueue, Object actualResult, Object originalChannel) {
            if (polled || owner != originalOwner || queue != originalQueue || object != actualResult
                    || channel != originalChannel || source == null || !source.viable()) {
                if (source != null) source.refuse();
                return null;
            }
            polled = true;
            return source;
        }
    }
}
