package dev.veltraph.client.rotation;
/** Exact request identity, including a just-completed request whose pending flag was cleared before callback. */
public final class RotationReceiptIdentity {
    private RotationReceiptIdentity(){}
    public record Stamp(Object service,long generation,Object authority,Object player,Object world,Object connection,long epoch){}
    public static boolean current(Stamp expected,Stamp actual,Object nativePlayer,Object nativeWorld,Object nativeConnection,Object nativeAuthority,boolean allowAbsentAuthority){
        return expected!=null&&actual!=null&&expected.service==actual.service&&expected.generation==actual.generation
                &&expected.authority!=null&&expected.authority==actual.authority&&expected.player==actual.player
                &&expected.world==actual.world&&expected.connection==actual.connection&&expected.epoch==actual.epoch
                &&expected.player==nativePlayer&&expected.world==nativeWorld&&expected.connection==nativeConnection
                &&(nativeAuthority==expected.authority||nativeAuthority==null&&allowAbsentAuthority);
    }
}
