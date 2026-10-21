package dev.veltraph.client.observation.nativeinput;

import java.util.ArrayList;
import static dev.veltraph.client.observation.nativeinput.NativePlayerBirthWireOrder.Kind;
import static dev.veltraph.client.observation.nativeinput.NativePlayerBirthWireOrder.Node;

/** Original native bundle membership and whole handler closure. Current mutable Iterable contents cannot enroll a member. */
final class NativePlayerBirthBundleOrder {
    final Object owner, channel, originalList;
    final Node start;
    private final ArrayList<Object> members = new ArrayList<>();
    private final ArrayList<Node> memberSources = new ArrayList<>();
    private Object bundle;
    private Node bundleSource;
    private boolean refused;

    NativePlayerBirthBundleOrder(Object owner, Object channel, Object originalList, Node start) {
        this.owner = owner;
        this.channel = channel;
        this.originalList = originalList;
        this.start = start;
        if (owner == null || channel == null || originalList == null || start == null) refused = true;
    }

    synchronized boolean memberAdded(Object list, Object originalPacket, Node source, boolean returned) {
        if (refused || bundle != null || list != originalList || originalPacket == null || source == null
                || !source.viable() || !returned || members.size() >= 4096) {
            refuse();
            return false;
        }
        members.add(originalPacket);
        memberSources.add(source);
        return true;
    }

    synchronized Node constructed(Object list, Object actualBundle, Node constructor, Node originalCloseDelimiter) {
        if (refused || bundle != null || list != originalList || actualBundle == null || constructor == null
                || originalCloseDelimiter == null || !constructor.viable() || !originalCloseDelimiter.viable()) {
            refuse();
            return null;
        }
        Node[] parents = new Node[memberSources.size() + 3];
        parents[0] = start;
        parents[1] = constructor;
        parents[2] = originalCloseDelimiter;
        for (int i = 0; i < memberSources.size(); i++) parents[i + 3] = memberSources.get(i);
        bundle = actualBundle;
        bundleSource = new Node(Kind.BUNDLE, owner, channel, actualBundle, originalList, parents);
        bundleSource.returned(true);
        return bundleSource;
    }

    synchronized Handler handler(Object actualBundle) {
        if (refused || actualBundle != bundle || bundleSource == null || !bundleSource.viable()) return null;
        return new Handler(actualBundle, originalList, members.toArray(), bundleSource);
    }

    synchronized Node source(Object actualBundle) {
        return !refused && actualBundle == bundle ? bundleSource : null;
    }

    synchronized void refuse() {
        refused = true;
        start.refuse();
        if (bundleSource != null) bundleSource.refuse();
    }

    static final class Handler {
        final Object bundle, list;
        final Object[] members;
        final Node source;
        private Object iterator, next;
        private int memberIndex;
        private boolean refused, normal;

        private Handler(Object bundle, Object list, Object[] members, Node source) {
            this.bundle = bundle;
            this.list = list;
            this.members = members;
            this.source = source;
        }

        synchronized boolean iteratorReturned(Object originalList, Object actualIterator) {
            if (refused || originalList != list || actualIterator == null || iterator != null) return refuse();
            iterator = actualIterator;
            return true;
        }

        synchronized boolean nextReturned(Object originalIterator, Object actualMember) {
            if (refused || normal || iterator == null || originalIterator != iterator || next != null
                    || memberIndex >= members.length || members[memberIndex] != actualMember) return refuse();
            next = actualMember;
            return true;
        }

        synchronized boolean beginMember(Object actualMember) {
            if (refused || next == null || next != actualMember || members[memberIndex] != actualMember) return refuse();
            next = null;
            memberIndex++;
            return true;
        }

        synchronized void finish(boolean originalReturned) {
            if (!originalReturned || next != null || memberIndex != members.length) refuse();
            else normal = true;
        }

        synchronized boolean complete() { return !refused && normal && source.complete(); }
        synchronized boolean viable() { return !refused && source.viable(); }
        private boolean refuse() { refused = true; source.refuse(); return false; }
    }
}
