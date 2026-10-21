package dev.veltraph.client.observation.nativeinput;

import java.util.ArrayList;
import java.util.List;

/** Package-private normal-return accounting. It cannot create a Root origin, seed or permission. */
final class NativeOpcodeReturnTrace {
    enum Opcode { KEY_SET, PITCH_SET, YAW_SET }
    private final List<Opcode> expected;
    private final ArrayList<Object> returned=new ArrayList<>();
    private Attempt pending;
    private boolean invalid,closed;
    private int next;
    NativeOpcodeReturnTrace(List<Opcode> expected){this.expected=List.copyOf(expected);if(expected.isEmpty()||expected.size()>9)invalid=true;}
    static final class Attempt {
        private final NativeOpcodeReturnTrace owner;
        private final Opcode opcode;
        private final Object receiver;
        private final long value;
        private boolean claimed,returned;
        private Attempt(NativeOpcodeReturnTrace owner,Opcode opcode,Object receiver,long value){this.owner=owner;this.opcode=opcode;this.receiver=receiver;this.value=value;}
    }
    Attempt begin(Opcode opcode,Object receiver,long value){
        if(invalid||closed||pending!=null||receiver==null||next>=expected.size()||expected.get(next)!=opcode){invalid=true;return null;}
        pending=new Attempt(this,opcode,receiver,value);return pending;
    }
    Object claim(Opcode opcode,Object receiver,long value){
        var actual=pending;
        if(invalid||closed||actual==null||actual.claimed||actual.opcode!=opcode||actual.receiver!=receiver||actual.value!=value){invalid=true;return null;}
        actual.claimed=true;return actual;
    }
    void normalReturn(Attempt exact,boolean normal){
        if(invalid||closed||exact==null||exact!=pending||exact.owner!=this||exact.returned||!normal||!exact.claimed){invalid=true;pending=null;return;}
        exact.returned=true;returned.add(exact);pending=null;next++;
    }
    void invalidate(){invalid=true;}
    boolean finish(boolean normal){
        if(closed){invalid=true;return false;}closed=true;
        if(!normal||pending!=null||next!=expected.size()||returned.size()!=expected.size())invalid=true;
        return !invalid;
    }
    List<Object> returned(Opcode opcode){
        if(!closed||invalid)return List.of();var out=new ArrayList<Object>();
        for(var object:returned){var attempt=(Attempt)object;if(attempt.opcode==opcode)out.add(attempt);}return List.copyOf(out);
    }
}
