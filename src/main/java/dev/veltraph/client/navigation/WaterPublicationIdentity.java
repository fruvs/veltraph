package dev.veltraph.client.navigation;
import java.util.Objects;
/** Pure issued WATER plan/hold identity. Data grants no gameplay authority. */
public final class WaterPublicationIdentity {
 private WaterPublicationIdentity(){}
 public record Scope(Object actor,Object leg,Object parent,Object player,Object world,Object connection,Object config,long revision,long journey,long generation,long epoch,long serial,long ticket){
  public Scope{Objects.requireNonNull(actor);Objects.requireNonNull(leg);Objects.requireNonNull(parent);Objects.requireNonNull(player);Objects.requireNonNull(world);Objects.requireNonNull(connection);Objects.requireNonNull(config);if(revision<0||journey<1||generation<0||epoch<0||serial<0||ticket<0)throw new IllegalArgumentException("Issued native WATER scope required");}
  public boolean lineage(Scope o){return o!=null&&actor==o.actor&&leg==o.leg&&parent==o.parent&&player==o.player&&world==o.world&&connection==o.connection&&config==o.config&&revision==o.revision&&journey==o.journey&&generation==o.generation;}
  public boolean exact(Scope o){return lineage(o)&&epoch==o.epoch&&serial==o.serial&&ticket==o.ticket;}
 }
 public static final class Cursor {
  private final Object plan;private Scope expected;private boolean revoked;
  public Cursor(Object plan,Scope issued){this.plan=Objects.requireNonNull(plan);expected=Objects.requireNonNull(issued);}
  public boolean current(Object plan,Scope actual){return !revoked&&this.plan==plan&&expected.exact(actual);}
  /** Only actual own publication may move a retained plan/hold to another native camera serial. */
  public boolean published(Object plan,Scope before,Scope after,long actualTicket){
   if(!current(plan,before)||after==null||!before.lineage(after)||actualTicket<=0)return false;
   boolean reused=before.exact(after)&&before.ticket()==actualTicket;
   boolean newOwn=after.serial()==before.serial()+1&&after.ticket()==actualTicket&&actualTicket==after.serial()
       &&after.epoch()>=before.epoch()&&after.epoch()-before.epoch()<=1;
   if(!reused&&!newOwn)return false;expected=after;return true;
  }
  public void revoke(){revoked=true;}public Scope issuedScope(){return expected;}
 }
}
