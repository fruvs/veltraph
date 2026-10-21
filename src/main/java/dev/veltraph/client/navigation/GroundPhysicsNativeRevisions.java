package dev.veltraph.client.navigation;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;

/** Every attempted mutation advances revision. Exact event comparison never itself grants custody. */
public final class GroundPhysicsNativeRevisions {
    private GroundPhysicsNativeRevisions() {}
    private static final int LIMIT=128, HISTORY=32;
    private static final ArrayList<Entry> entries=new ArrayList<>();
    public enum Kind { OTHER, SPRINT_METHOD, SPRINT_FLAG, SPRINT_SHARED, SPRINT_DATA_SET2, SPRINT_DATA_SET3, SPRINT_DATA_ITEM, FOREIGN_FLAGS_PACKET, SPEED_FIELD, REMOVE_ID, ADD_TRANSIENT, ADD_MODIFIER }
    public record Change(Kind kind,Object detail) {}
    private static final class Entry {
        final WeakReference<Object> object;long revision;final ArrayList<Change> changes=new ArrayList<>();
        Entry(Object object){this.object=new WeakReference<>(object);}
    }
    public static final class Token {
        private final Entry entry;private final long revision;
        private Token(Entry entry){this.entry=entry;revision=entry.revision;}
        public boolean current(){synchronized(entries){return entry.object.get()!=null&&revision>=0&&entry.revision==revision;}}
        public boolean sameRevision(Token other){synchronized(entries){return other!=null&&entry==other.entry&&revision>=0
                &&revision==other.revision&&entry.object.get()!=null;}}
        /** Pure lineage equality; native admission also needs actual privately stored carrier identity. */
        public boolean followedExactlyBy(Token next,List<Change> expected){synchronized(entries){
            if(next==null||expected==null||entry!=next.entry||entry.object.get()==null||revision<0
                    ||!next.current()||next.revision-revision!=expected.size())return false;
            long first=entry.revision-entry.changes.size();
            if(revision<first)return false;
            for(int i=0;i<expected.size();i++){
                Change actual=entry.changes.get((int)(revision-first)+i),want=expected.get(i);
                // Modifier and identifier objects are native identity, not caller-equal substitutes.
                if(actual.kind()!=want.kind()|| (actual.kind()==Kind.SPRINT_METHOD||actual.kind()==Kind.SPRINT_FLAG||actual.kind()==Kind.SPRINT_SHARED||actual.kind()==Kind.SPRINT_DATA_SET2||actual.kind()==Kind.SPRINT_DATA_SET3||actual.kind()==Kind.SPRINT_DATA_ITEM||actual.kind()==Kind.SPEED_FIELD
                        ? !java.util.Objects.equals(actual.detail(),want.detail()) : actual.detail()!=want.detail()))return false;
            }
            return true;
        }}
    }
    /** Ordered read-only comparison of privately captured token lists. Each visited token still
     * takes its original current() journal lock; no aggregate snapshot, refresh or custody is issued.
     * A null list retains the original stream receiver failure, and null tokens short-circuit false.
     */
    public static boolean capturedTokensCurrent(List<Token> captured) {
        int count=captured.size();
        for(int i=0;i<count;i++) {
            var token=captured.get(i);
            if(token==null||!token.current())return false;
        }
        return true;
    }
    public static Token watch(Object object){if(object==null)return null;synchronized(entries){
        entries.removeIf(e->e.object.get()==null);for(var e:entries)if(e.object.get()==object)return e.revision<0?null:new Token(e);
        if(entries.size()>=LIMIT)return null;var e=new Entry(object);entries.add(e);return new Token(e);
    }}
    public static void mutation(Object object){mutation(object,Kind.OTHER,null);}
    public static void mutation(Object object,Kind kind,Object detail){if(object==null)return;synchronized(entries){
        for(var e:entries)if(e.object.get()==object){
            if(e.revision<0||e.revision==Long.MAX_VALUE){e.revision=-1;e.changes.clear();return;}
            e.revision++;e.changes.add(new Change(kind==null?Kind.OTHER:kind,detail));
            if(e.changes.size()>HISTORY)e.changes.removeFirst();return;
        }
    }}
}
