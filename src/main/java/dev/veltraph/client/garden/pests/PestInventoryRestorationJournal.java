package dev.veltraph.client.garden.pests;
import java.util.*;
/** Pure ordering/state evidence only; it cannot mint any native server or ownership receipt. */
public final class PestInventoryRestorationJournal<C> {
    /** Distinct boundary is pushed before fallible capture; null top never borrows an outer token. */
    public static final class InvocationBoundaries<T> {
        public static final class Boundary<T> {private T value;private Boundary(){}}
        private final java.util.ArrayList<Boundary<T>> stack=new java.util.ArrayList<>();
        public Boundary<T> enter(){var b=new Boundary<T>();stack.add(b);return b;}
        public void capture(Boundary<T> b,T value){if(!stack.isEmpty()&&stack.getLast()==b)b.value=value;}
        public T top(){return stack.isEmpty()?null:stack.getLast().value;}
        public void exit(Boundary<T> b){if(!stack.isEmpty()&&stack.getLast()==b)stack.removeLast();}
        public boolean empty(){return stack.isEmpty();}
    }
    /** Refusal gate only; native entry supplies actual client-thread and current LocalPlayer inventory identity. */
    public static boolean clientInventory(boolean clientThread,Object current,Object observed){return clientThread&&current!=null&&current==observed;}
    /** Native setter attempt/version bookkeeping only; no ownership or admission grant. */
    public static final class SlotVersions {
        private long version;private int inFlight;private boolean known=true;
        private void advance(){if(version==Long.MAX_VALUE)known=false;else version++;}
        public long version(){return version;}public int inFlight(){return inFlight;}public boolean known(){return known;}
        public void begin(){advance();inFlight++;}public void finish(){advance();inFlight--;if(inFlight<0)known=false;}
        public boolean matches(long expected){return known&&inFlight==0&&version==expected;}
    }
    /** Counts actual native effects in one issuing selection capsule; it grants no scope or receipt. */
    public static final class SelectionEffects {
        private int setters,completed,packets;private boolean failed;
        public void setter(){setters++;}public void completed(boolean normal){completed++;if(!normal)failed=true;}
        public void packet(boolean normal){packets++;if(!normal)failed=true;}public void refuse(){failed=true;}
        public boolean beforeSetter(){return !failed&&setters==0&&completed==0&&packets==0;}
        public boolean afterSetter(){return !failed&&setters==1&&completed==1;}
        public boolean exact(){return afterSetter()&&packets==1;}
    }
    public enum Status { ARMED,SUBMITTED,AUTHORITATIVE_AFTER_REQUEST,UNKNOWN,EXPIRED }
    public static final class Window<C> {
        private final long nonce,opened,deadline;private final int menu,revision,source,hotbar,temporary,restored;
        private final List<C> before,after;private final C cursor;private int selected,coverage,lastRevision;private boolean started;
        private Status status=Status.ARMED;
        private Window(long nonce,long opened,long deadline,int menu,int revision,int source,int hotbar,int temporary,int restored,List<C> before,List<C> after,C cursor){
            this.nonce=nonce;this.opened=opened;this.deadline=deadline;this.menu=menu;this.revision=revision;this.source=source;this.hotbar=hotbar;
            this.temporary=temporary;this.restored=restored;this.selected=temporary;this.before=List.copyOf(before);this.after=List.copyOf(after);this.cursor=cursor;lastRevision=revision;
        }
        public Status status(){return status;}public long nonce(){return nonce;}public int selected(){return selected;}
    }
    public static final class Send<C> {
        private final Window<C> window;private final boolean eligible;
        private Send(Window<C> window,boolean eligible){this.window=window;this.eligible=eligible;}
    }
    public static final class Receive<C> {
        private final Window<C> window;private final boolean eligible;
        private Receive(Window<C> window,boolean eligible){this.window=window;this.eligible=eligible;}
    }
    private Window<C> active;private long nextNonce;
    public Window<C> open(long now,long deadline,int menu,int revision,int source,int hotbar,int temporary,int restored,List<C> before,List<C> after,C cursor){
        if(now<0||deadline<=now||menu<0||revision<0||revision>32767||source<9||source>=36||hotbar<0||hotbar>8||temporary<0||temporary>8||restored<0||restored>8
                ||before==null||after==null||before.size()!=36||after.size()!=36||nextNonce==Long.MAX_VALUE)return null;
        for(int i=0;i<36;i++)if(!Objects.equals(after.get(i),before.get(i==source?hotbar:i==hotbar?source:i)))return null;
        if(active!=null)active.status=Status.UNKNOWN;
        active=new Window<>(++nextNonce,now,deadline,menu,revision,source,hotbar,temporary,restored,before,after,cursor);return active;
    }
    private boolean time(Window<C> w,long now){if(w==null||w!=active)return false;if(now<w.opened||now>=w.deadline){w.status=Status.EXPIRED;return false;}return true;}
    private boolean frame(Window<C> w,List<C> actual,C cursor,int selected){return actual!=null&&w.after.equals(actual)&&Objects.equals(w.cursor,cursor)&&w.selected==selected;}
    public Send<C> beginSend(Window<C> w,long now,int menu,int revision,int source,int button,boolean swap,List<C> actual,C cursor,int selected,boolean scope){
        boolean eligible=time(w,now)&&scope&&w.status==Status.ARMED&&!w.started&&menu==w.menu&&revision==w.revision&&source==w.source&&button==w.hotbar&&swap&&frame(w,actual,cursor,selected);
        if(w==active&&w!=null){if(!eligible)w.status=Status.UNKNOWN;else if(w.status==Status.ARMED)w.started=true;}
        return new Send<>(w,eligible);
    }
    public void finishSend(Send<C> call,long now,boolean normal,boolean scope,List<C> actual,C cursor,int selected){
        if(call==null||call.window!=active||!time(call.window,now))return;var w=call.window;
        if(!call.eligible||!normal||!scope||w.status!=Status.ARMED||!frame(w,actual,cursor,selected)){w.status=Status.UNKNOWN;return;}w.status=Status.SUBMITTED;
    }
    public Receive<C> beginReceive(Window<C> w,long now,boolean scope){return new Receive<>(w,time(w,now)&&scope&&(w.status==Status.SUBMITTED||w.status==Status.AUTHORITATIVE_AFTER_REQUEST));}
    /** A newer protocol revision is required. Wrap/rollback cannot be interpreted as an acknowledgment. */
    public boolean finishReceive(Receive<C> call,long now,boolean normal,boolean scope,int menu,int revision,Map<Integer,C> authoritative,List<C> actual,C cursor,int selected){
        if(call==null||call.window!=active||!time(call.window,now)||!call.eligible)return false;var w=call.window;
        if((w.status!=Status.SUBMITTED&&w.status!=Status.AUTHORITATIVE_AFTER_REQUEST)||!normal||!scope||!frame(w,actual,cursor,selected)){w.status=Status.UNKNOWN;return false;}
        if(menu!=w.menu||revision<=w.revision||revision<w.lastRevision||revision>32767||revision-w.revision>64||authoritative==null)return false;
        for(var e:authoritative.entrySet())if(e.getKey()<0||e.getKey()>=36||!Objects.equals(w.after.get(e.getKey()),e.getValue())){w.status=Status.UNKNOWN;return false;}
        w.lastRevision=revision;
        if(authoritative.containsKey(w.source))w.coverage|=1;if(authoritative.containsKey(w.hotbar))w.coverage|=2;
        if(w.coverage==3){w.status=Status.AUTHORITATIVE_AFTER_REQUEST;return true;}return false;
    }
    public boolean selected(Window<C> w,long now,int expected,int next,boolean nativeReceipt,boolean scope,List<C> actual,C cursor){
        if(!time(w,now)||(w.status!=Status.SUBMITTED&&w.status!=Status.AUTHORITATIVE_AFTER_REQUEST)||!nativeReceipt||!scope||expected!=w.temporary||next!=w.restored||w.selected!=expected||!w.after.equals(actual)||!Objects.equals(w.cursor,cursor)){if(w==active&&w!=null)w.status=Status.UNKNOWN;return false;}
        w.selected=next;return true;
    }
    public boolean current(Window<C> w,long now,boolean scope,List<C> actual,C cursor,int selected){return time(w,now)&&scope&&w.status==Status.AUTHORITATIVE_AFTER_REQUEST&&frame(w,actual,cursor,selected);}
    public void invalidate(Window<C> w){if(w!=null&&w==active)w.status=Status.UNKNOWN;}
}
