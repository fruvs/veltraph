package dev.veltraph.client.movement.device;

import dev.veltraph.client.world.Vec3;
import java.util.Objects;

/** One admitted issue and one applied receipt. This core never sends an action or predicts server success. */
public final class DeviceUseReceipt {
    public record Context(Object parent,Object player,Object world,Object connection,Object policy,long revision,long journey,long cameraEpoch) {
        public Context {Objects.requireNonNull(parent);Objects.requireNonNull(player);Objects.requireNonNull(world);Objects.requireNonNull(connection);Objects.requireNonNull(policy);if(revision<0||journey<1||cameraEpoch<0)throw new IllegalArgumentException("Invalid custody generation");}
        public boolean same(Context o){return o!=null&&parent==o.parent&&player==o.player&&world==o.world&&connection==o.connection&&policy==o.policy&&revision==o.revision&&journey==o.journey&&cameraEpoch==o.cameraEpoch;}
    }
    public record Issue(Context context,Object domain,Object nonce,long baseline,long issuedAt,long deadline,Vec3 goal,double tolerance) {
        public Issue {Objects.requireNonNull(context);Objects.requireNonNull(domain);Objects.requireNonNull(nonce);if(baseline<0||issuedAt<0||deadline<=issuedAt||deadline-issuedAt>30000||!finite(goal)||!Double.isFinite(tolerance)||tolerance<=0||tolerance>1)throw new IllegalArgumentException("Invalid issue");}
    }
    public record Applied(Context context,Object domain,Object nonce,long serial,long receivedAt,Vec3 position) {}
    public enum State { IDLE, WAITING, APPLIED, REVOKED, TIMED_OUT }
    private Issue issue;private State state=State.IDLE;private long lastNow=-1;
    public boolean arm(Issue next){if(state!=State.IDLE||next==null)return false;issue=next;lastNow=next.issuedAt();state=State.WAITING;return true;}
    public boolean observe(Context current,Applied r,long now){
        if(!validate(current,now)||r==null||!issue.context().same(r.context())||r.domain()!=issue.domain()||r.nonce()!=issue.nonce()
                ||r.serial()<=issue.baseline()||r.receivedAt()<issue.issuedAt()||r.receivedAt()>now||!finite(r.position())
                ||r.position().distanceTo(issue.goal())>issue.tolerance())return false;
        state=State.APPLIED;return true;
    }
    public boolean validate(Context current,long now){if(state!=State.WAITING)return false;
        if(now<lastNow||!issue.context().same(current)){state=State.REVOKED;return false;}lastNow=now;
        if(now>=issue.deadline()){state=State.TIMED_OUT;return false;}return true;}
    public void revoke(){if(state==State.WAITING)state=State.REVOKED;}
    public State state(){return state;}public Issue issue(){return issue;}
    private static boolean finite(Vec3 p){return p!=null&&Double.isFinite(p.x())&&Double.isFinite(p.y())&&Double.isFinite(p.z());}
}
