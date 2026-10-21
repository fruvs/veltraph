package dev.veltraph.client.combat.ghost;

/** Exact source helper predicate, after a live native/context observation. Does not open a menu or resume a parent. */
public final class GhostSourceJumpBoostGate {
    public enum Observed { YES, NO, UNKNOWN }
    public enum Result { MODIFY_REQUIRED, NOT_REQUIRED, UNKNOWN }
    public static final long SOURCE_COOLDOWN_MS=300000;
    public static final boolean SOURCE_MODIFY_DEFAULT=false;
    private GhostSourceJumpBoostGate() {}
    public static Result evaluate(Observed nativeJumpBoost,Observed cookieAccess,boolean modifyJumpBoost,long now,long lastMenuReset){
        if(nativeJumpBoost==null||nativeJumpBoost==Observed.UNKNOWN)return Result.UNKNOWN;
        if(nativeJumpBoost==Observed.NO)return Result.NOT_REQUIRED;
        if(cookieAccess==null||cookieAccess==Observed.UNKNOWN)return Result.UNKNOWN;
        if(cookieAccess==Observed.NO||!modifyJumpBoost)return Result.NOT_REQUIRED;
        return now-lastMenuReset>SOURCE_COOLDOWN_MS?Result.MODIFY_REQUIRED:Result.NOT_REQUIRED;
    }
}
