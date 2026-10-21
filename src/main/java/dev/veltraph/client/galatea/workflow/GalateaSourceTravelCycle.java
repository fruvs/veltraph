package dev.veltraph.client.galatea.workflow;

import dev.veltraph.client.world.Vec3;
import java.util.Objects;

/** Source Marsh <20 / Murkwater <5 gates. Commands and all acknowledgment bounds are local profiles. */
public final class GalateaSourceTravelCycle {
    public static final Vec3 MARSH=new Vec3(-548,109,-23), MURKWATER=new Vec3(-645,90,2);
    public enum Kind { RESET,MURKWATER }
    public enum State { IDLE,WAITING,COMPLETE,FAILED,CANCELLED }
    public record Profile(Kind kind,boolean permission,String command,String receipt,String rejection,
                          String dimension,Vec3 destination,long timeout) {}
    public record Observation(boolean healthy,Object player,Object world,Object connection,String dimension,
                              Vec3 position,boolean supported,long nativeTick,long serial,Vec3 teleport,
                              boolean receipt,boolean rejected,long now) {}
    public interface Actions {boolean send(String command);void release();}
    private Profile profile;private Observation origin;private State state=State.IDLE;private long last,seenTick;
    private int grounded;private boolean freshTeleport,expectedConsumed;private String reason="Idle";
    public static boolean finite(Vec3 p){return p!=null&&Double.isFinite(p.x())&&Double.isFinite(p.y())&&Double.isFinite(p.z());}
    public static boolean commandSafe(String command){return command!=null&&command.length()>1&&command.length()<=256&&command.startsWith("/")&&!command.startsWith("//")&&command.chars().noneMatch(Character::isISOControl)&&command.equals(command.strip());}
    public static boolean valid(Profile p){return p!=null&&p.kind()!=null&&p.permission()&&commandSafe(p.command())&&p.receipt()!=null&&!p.receipt().isBlank()&&p.rejection()!=null&&!p.rejection().isBlank()&&!p.receipt().equals(p.rejection())&&p.dimension()!=null&&p.dimension().matches("[a-z0-9_.-]+:[a-z0-9_/.-]+")&&finite(p.destination())&&p.timeout()>=1000&&p.timeout()<=60000&&(p.kind()!=Kind.MURKWATER||p.destination().equals(MURKWATER));}
    private static boolean healthy(Observation o){return o!=null&&o.healthy()&&o.player()!=null&&o.world()!=null&&o.connection()!=null&&finite(o.position());}
    public boolean start(Profile p,Observation o,Actions a){
        state=State.IDLE;freshTeleport=expectedConsumed=false;grounded=0;
        if(!valid(p)||!healthy(o)||!o.supported()||!p.dimension().equals(o.dimension())||p.kind()==Kind.MURKWATER&&o.position().distanceTo(MARSH)>=20){state=State.FAILED;reason="Explicit profile/source admission missing";return false;}
        profile=p;origin=o;last=o.now();seenTick=o.nativeTick();state=State.WAITING;
        a.release();try{if(!a.send(p.command())){fail("Command dispatch refused",a);return false;}}catch(RuntimeException error){fail("Command dispatch uncertain; no retry",a);return false;}
        reason="Waiting for fresh server command and native arrival receipts";return true;
    }
    private boolean owner(Observation o){return healthy(o)&&o.player()==origin.player()&&o.world()==origin.world()&&o.connection()==origin.connection()&&Objects.equals(o.dimension(),profile.dimension());}
    private boolean arrived(Vec3 p){return finite(p)&&p.distanceTo(profile.destination())<(profile.kind()==Kind.MURKWATER?5:1);}
    public State tick(Profile p,Observation o,Actions a){
        if(state!=State.WAITING)return state;
        if(!Objects.equals(profile,p)||!owner(o)){cancel(a);reason="Profile/native owner changed";return state;}
        if(o.now()<last||o.now()-origin.now()>=profile.timeout()){fail("Command outcome timed out; no retry",a);return state;}last=o.now();
        if(o.rejected()){fail("Exact server rejection",a);return state;}
        if(o.serial()>origin.serial()){if(!arrived(o.teleport())){fail("Fresh teleport outside exact destination",a);return state;}freshTeleport=true;}
        if(o.nativeTick()!=seenTick){seenTick=o.nativeTick();grounded=freshTeleport&&o.receipt()&&o.supported()&&arrived(o.position())?grounded+1:0;}
        if(grounded>=3){state=State.COMPLETE;reason="Fresh server receipt and three supported native arrival ticks";a.release();}return state;
    }
    public boolean consumeExpectedTeleport(Observation o,Vec3 position,long now){
        if((state!=State.WAITING&&state!=State.COMPLETE)||expectedConsumed||!owner(o)||now<origin.now()||now-origin.now()>=profile.timeout()||o.serial()<=origin.serial()||!arrived(o.teleport())||!arrived(position))return false;
        expectedConsumed=true;return true;
    }
    private void fail(String why,Actions a){state=State.FAILED;reason=why;a.release();}
    public void cancel(Actions a){if(state!=State.COMPLETE&&state!=State.FAILED)state=State.CANCELLED;a.release();}
    public State state(){return state;}public String reason(){return reason;}
}
