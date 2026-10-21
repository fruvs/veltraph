package dev.veltraph.client.navigation;
import java.lang.ref.WeakReference;
/** Refusal-only passive journal detail. Values and sources never create native ownership authority. */
public final class GroundNoGravityMutation {
    private GroundNoGravityMutation() {}
    public enum Source { ENTITY_METHOD, RAW_SET2, RAW_SET3, RAW_SET3_FORCED, RAW_ITEM, PACKET_ASSIGN }
    /** Weak identities keep the bounded static journal from retaining a departed native player through its data. */
    public static final class Attempt {
        private final Source source;private final WeakReference<Object> data,item,accessor,value;
        private Attempt(Source source,Object data,Object item,Object accessor,Object value){this.source=source;
            this.data=new WeakReference<>(data);this.item=new WeakReference<>(item);this.accessor=new WeakReference<>(accessor);this.value=new WeakReference<>(value);}
        public Source source(){return source;}public Object actualData(){return data.get();}public Object actualItem(){return item.get();}
        public Object attemptedAccessor(){return accessor.get();}public Object attemptedValue(){return value.get();}
    }
    public static Attempt record(Object actualPlayer,Source source,Object actualData,Object actualItem,Object accessor,Object value){
        var attempt=new Attempt(source,actualData,actualItem,accessor,value);
        GroundPhysicsNativeRevisions.mutation(actualPlayer,GroundPhysicsNativeRevisions.Kind.OTHER,attempt);return attempt;
    }
}
