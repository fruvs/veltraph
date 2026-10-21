package dev.veltraph.client.fishing.offense;

import java.util.Objects;
import java.util.UUID;

/** Exact native hook and player/world/connection receipt, never a timer or predicted hook removal. */
public final class FishingOwnedHook {
    public record Scope(Object player,Object world,Object connection){public Scope{Objects.requireNonNull(player);Objects.requireNonNull(world);Objects.requireNonNull(connection);}boolean same(Scope s){return s!=null&&player==s.player&&world==s.world&&connection==s.connection;}}
    public record Hook(Object entity,Object owner,Object world,int nativeId,UUID uuid){}
    private Scope scope;
    private Hook expected;
    private boolean active,sent,removed;
    private boolean owned(Hook h){return h!=null&&h.entity()!=null&&h.owner()==scope.player()&&h.world()==scope.world()&&h.nativeId()>=0&&h.uuid()!=null;}
    private boolean same(Hook h){return expected!=null&&h.entity()==expected.entity()&&h.owner()==expected.owner()&&h.world()==expected.world()&&h.nativeId()==expected.nativeId()&&h.uuid().equals(expected.uuid());}
    public boolean begin(Scope next,Hook initial){scope=Objects.requireNonNull(next);expected=null;active=true;sent=removed=false;if(initial!=null&&!owned(initial)){cancel();return false;}expected=initial;return true;}
    public boolean observe(Scope current,Hook hook){
        if(!active||!scope.same(current))return false;
        if(hook==null){if(sent)removed=true;return true;}
        return owned(hook)&&!(sent&&removed)&&(expected==null||same(hook));
    }
    /** A late but current player-owned hook may be claimed once, before tool selection. */
    public boolean issueReel(Scope current,Hook hook){if(sent||hook==null||!observe(current,hook))return false;expected=hook;sent=true;removed=false;return true;}
    public boolean maySelectTool(Scope current,Hook hook){return observe(current,hook)&&hook==null&&(!sent||removed);}
    public boolean reelIssued(){return sent;}
    public boolean removalObserved(){return removed;}
    public void cancel(){active=false;}
    public void clear(){active=false;scope=null;expected=null;sent=removed=false;}
}
