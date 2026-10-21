package dev.veltraph.client.garden.visitor.history;

/** Ordering/identity state for a private target-held native invocation. Constructing one is not native authority. */
public final class VisitorHistoryNativeCallFrame {
    private final Object packet,argument1,argument2,token=new Object();private final int kind;
    private final Thread thread=Thread.currentThread();private boolean claimed,finished,returned;
    public VisitorHistoryNativeCallFrame(Object packet,Object argument1,Object argument2,int kind){this.packet=packet;this.argument1=argument1;this.argument2=argument2;this.kind=kind;}
    public Object claim(Object packet,Object a1,Object a2,int kind){
        if(claimed||finished||!same(packet,a1,a2,kind))return null;claimed=true;return token;
    }
    /** Only an actual wrapper retaining this private target-held frame calls this after original.call. */
    public void originalReturned(boolean normal){if(Thread.currentThread()!=thread||finished)return;finished=true;returned=normal;}
    public boolean current(Object actualToken,Object packet,Object a1,Object a2,int kind,boolean requireNormalReturn){
        return claimed&&actualToken==token&&same(packet,a1,a2,kind)&&(requireNormalReturn?finished&&returned:!finished);
    }
    private boolean same(Object packet,Object a1,Object a2,int kind){return Thread.currentThread()==thread&&this.packet==packet&&argument1==a1&&argument2==a2&&this.kind==kind;}
}
