package dev.veltraph.client.navigation;
/** Pure sequence consistency. A constructed instance is never native ownership authority. */
public final class GroundSharedFlagsWriteSequence {
    public enum Result { NATIVE_SPRINT, NATIVE_OTHER, FOREIGN }
    private final Object data,item,accessor;private final byte planned;private final int flag;
    private final boolean changed,valid;private int seen;private boolean failed;
    public GroundSharedFlagsWriteSequence(Object data,Object item,Object accessor,byte before,byte planned,int flag,boolean enabled){
        this.data=data;this.item=item;this.accessor=accessor;this.planned=planned;this.flag=flag;changed=before!=planned;
        valid=data!=null&&item!=null&&accessor!=null&&flag>=0&&flag<=7
            &&planned==(byte)(enabled?(before|1<<flag):(before&~(1<<flag)));}
    public Result observe(Object actualData,Object actualItem,Object actualAccessor,Object value,int stage){
        if(failed||!valid||actualData!=data||actualItem!=item||actualAccessor!=accessor||!(value instanceof Byte b)
            ||b.byteValue()!=planned||stage!=seen+1||stage> (changed?3:2)){failed=true;return Result.FOREIGN;}
        seen=stage;return flag==3?Result.NATIVE_SPRINT:Result.NATIVE_OTHER;
    }
    public boolean complete(byte actual){return valid&&!failed&&seen==(changed?3:2)&&actual==planned;}
}
