package dev.veltraph.client.farming.movement;

import dev.veltraph.client.world.Direction;
import dev.veltraph.client.world.Vec3;
import dev.veltraph.client.farming.module.FarmMaintenanceState;
import java.util.Objects;

/** Key-driven, fixed-camera supplemental pass. Plans are geometry data, never native authority. */
public final class SupplementalWaterCoverage {
 public enum State { IDLE,APPROACH,JUMP_CAP,ENTER_CHANNEL,SWEEP_OUT,SWEEP_BACK,JUMP_EXIT,RETURN_PRIMARY,COMPLETE,BLOCKED }
 public record Plan(Vec3 primaryEnd,Vec3 approachFeet,Vec3 capFeet,Vec3 channelNear,Vec3 channelFar,
                    Direction inward,Direction harvestHeading) {
  public Plan {
   if(!valid(primaryEnd,approachFeet,capFeet,channelNear,channelFar,inward,harvestHeading))
    throw new IllegalArgumentException("Loaded orthogonal dry-cap/water-column plan required");
  }
  private static boolean finite(Vec3 p){return p!=null&&Double.isFinite(p.x())&&Double.isFinite(p.y())&&Double.isFinite(p.z());}
  private static double dot(Vec3 from,Vec3 to,Direction d){return (to.x()-from.x())*d.dx()+(to.z()-from.z())*d.dz();}
  private static boolean sameAxis(Vec3 a,Vec3 b,Direction d){return Math.abs((b.x()-a.x())*d.dz()-(b.z()-a.z())*d.dx())<1e-7;}
  private static boolean valid(Vec3 p,Vec3 a,Vec3 cap,Vec3 near,Vec3 far,Direction in,Direction heading){
   if(!finite(p)||!finite(a)||!finite(cap)||!finite(near)||!finite(far)||in==null||heading==null
      ||!in.isHorizontal()||!heading.isHorizontal()||in.dx()*heading.dx()+in.dz()*heading.dz()!=0)return false;
   double advance=dot(p,a,heading),length=dot(near,far,in);
   return sameAxis(p,a,heading)&&advance>=.5&&advance<=8.01
     &&sameAxis(a,cap,in)&&Math.abs(dot(a,cap,in)-1)<1e-7
     &&sameAxis(cap,near,in)&&Math.abs(dot(cap,near,in)-1)<1e-7
     &&sameAxis(near,far,in)&&length>=1&&length<=127&&Math.abs(length-Math.rint(length))<1e-7
     &&Math.abs(p.y()-a.y())<1e-7&&Math.abs(cap.y()-p.y()-1)<1e-7
     &&Math.abs(near.y()-p.y())<1e-7&&Math.abs(far.y()-p.y())<1e-7;
  }
 }
 /** Actual loaded geometry and native actor observations. This port grants no input permission. */
 public interface World {
  Vec3 position();boolean onGround();boolean inWater();
  boolean bodyClear(Vec3 feet,boolean allowWater);
  boolean current(Plan plan);
 }
 /** Parent engine steers in the ACTUAL fixed row-yaw basis and uses its ordinary guarded breaker.
  * toward must explicitly clear sneak/sprint and publish the requested jump state. release must
  * release all movement (including jump) and attack. No method may change camera or native pose. */
 public interface MovementPort {void toward(Vec3 target,boolean jump,boolean attack);void release();}
 private final World world;private final MovementPort movement;
 private Plan plan;private State state=State.IDLE;private int ticks,phaseTicks;private boolean jumpIssued,sawAirborne,returnPortal;
 private long generation;private boolean generationExhausted;private Frame tickingFrame;
 private String reason="idle";
 private record Frame(long generation,Plan plan,State state){}
 private Frame frame(){return new Frame(generation,plan,state);}
 private boolean current(Frame f){return !generationExhausted&&f.generation()==generation&&f.plan()==plan&&f.state()==state;}
 private boolean advance(){if(generationExhausted||generation==Long.MAX_VALUE){generationExhausted=true;return false;}generation++;return true;}
 public SupplementalWaterCoverage(World world,MovementPort movement){this.world=Objects.requireNonNull(world);this.movement=Objects.requireNonNull(movement);}
 public long generation(){return generation;}public State state(){return state;}public Plan plan(){return plan;}public String reason(){return reason;}
 public boolean active(){return state!=State.IDLE&&state!=State.COMPLETE&&state!=State.BLOCKED;}
 public boolean start(Plan candidate){
  if(active()||candidate==null||generationExhausted)return false;
  Frame before=frame();
  Vec3 feet=world.position();if(!current(before))return false;
  boolean valid=world.current(candidate);if(!current(before))return false;
  boolean ground=world.onGround();if(!current(before))return false;
  boolean wet=world.inWater();if(!current(before))return false;
  boolean clear=finite(feet)&&world.bodyClear(feet,false);if(!current(before))return false;
  if(!current(before)||!valid||!ground||wet||!at(feet,candidate.primaryEnd(),.35,.01)||!clear||!advance())return false;
  plan=candidate;tickingFrame=null;ticks=phaseTicks=0;jumpIssued=sawAirborne=returnPortal=false;state=State.APPROACH;reason="approaching loaded cap";
  Frame started=frame();movement.release();return current(started);
 }
 public void pause(){movement.release();} // No route, pose, phase, tick or jump evidence is adopted.
 /** Metadata-only retirement. The original module remains the sole native cleanup owner. */
 public void quiesce(){advance();state=State.IDLE;plan=null;tickingFrame=null;jumpIssued=sawAirborne=returnPortal=false;reason="quiesced";}
 public void stop(){advance();state=State.IDLE;plan=null;tickingFrame=null;jumpIssued=sawAirborne=returnPortal=false;reason="stopped";movement.release();}
 private State block(Frame f,String why){if(!current(f))return state;state=State.BLOCKED;reason=why;movement.release();return state;}
 private void phase(Frame f,State next,String why){if(!current(f))return;state=next;phaseTicks=0;jumpIssued=sawAirborne=false;reason=why;movement.release();}
 private static boolean finite(Vec3 p){return p!=null&&Double.isFinite(p.x())&&Double.isFinite(p.y())&&Double.isFinite(p.z());}
 private static boolean at(Vec3 feet,Vec3 target,double horizontal,double vertical){return finite(feet)&&feet.horizontalDistanceTo(target)<=horizontal&&Math.abs(feet.y()-target.y())<=vertical;}
 /** A dry, genuinely grounded landing may center on the same cap without accepting arrival.
  * Native grounding permits partial foot overhang. The feet center must remain over the
  * same original cap block; column/current full plan/body-clear gates have already passed. */
 private static boolean supportedCapCentering(Plan p,Vec3 feet,boolean ground,boolean wet){return ground&&!wet
   &&Math.abs(feet.y()-p.capFeet().y())<=.03
   &&Math.floor(feet.x())==Math.floor(p.capFeet().x())
   &&Math.floor(feet.z())==Math.floor(p.capFeet().z());}
 /** RETURN_PRIMARY comes only after the original requested jump landed on the cap.
  * Its grounded dry portal belongs to the existing supported corridor, not the water envelope.
  * Center here without crediting the unchanged .1 approach arrival or changing any other phase. */
 private static boolean supportedReturnPortalCentering(Plan p,Vec3 feet,boolean ground,boolean wet){return ground&&!wet
   &&Math.abs(feet.y()-p.approachFeet().y())<=.01&&column(p,feet)&&dryCorridor(p,feet)
   &&Math.floor(feet.x())==Math.floor(p.approachFeet().x())&&Math.floor(feet.z())==Math.floor(p.approachFeet().z());}
 private static boolean column(Plan p,Vec3 feet){var in=p.inward();return Math.abs((feet.x()-p.approachFeet().x())*in.dz()-(feet.z()-p.approachFeet().z())*in.dx())<=.18;}
 private static double along(Plan p,Vec3 feet){var in=p.inward();return (feet.x()-p.approachFeet().x())*in.dx()+(feet.z()-p.approachFeet().z())*in.dz();}
 private static boolean dryCorridor(Plan p,Vec3 feet){var h=p.harvestHeading();double d=(feet.x()-p.primaryEnd().x())*h.dx()+(feet.z()-p.primaryEnd().z())*h.dz();double end=p.primaryEnd().horizontalDistanceTo(p.approachFeet());return Math.abs((feet.x()-p.primaryEnd().x())*h.dz()-(feet.z()-p.primaryEnd().z())*h.dx())<=.205&&d>=-.35&&d<=end+.18&&Math.abs(feet.y()-p.primaryEnd().y())<=.05;}
 private void toward(Frame f,Vec3 target,boolean jump,boolean attack){if(current(f))movement.toward(target,jump,attack);}
 public State tick(){
  if(!active())return state;
  if(tickingFrame!=null){advance();return state;} // Recursive public pumping retires the old frame without an input effect.
  Frame f=frame();tickingFrame=f;
  try{return tickOriginal(f);}finally{if(tickingFrame==f)tickingFrame=null;}
 }
 private State tickOriginal(Frame f){
  Plan p=f.plan();if(!current(f))return state;
  if(++ticks>2400||++phaseTicks>phaseLimit(f.state()))return block(f,"bounded supplemental coverage deadline");
  Vec3 feet=world.position();if(!current(f))return state;
  boolean ground=world.onGround();if(!current(f))return state;
  boolean wet=world.inWater();if(!current(f))return state;
  boolean valid=world.current(p);if(!current(f))return state;
  boolean clear=finite(feet)&&world.bodyClear(feet,true);if(!current(f))return state;
  // Each callback is followed by a raw frame check before another read, metadata or effect.
  if(!finite(feet)||!valid||!clear)return block(f,"original loaded cap/channel geometry unavailable");
  double base=p.primaryEnd().y(),depth=along(p,feet),length=along(p,p.channelFar());
  boolean portalCentering=f.state()==State.RETURN_PRIMARY&&!returnPortal&&supportedReturnPortalCentering(p,feet,ground,wet);
  if(f.state()==State.APPROACH||f.state()==State.RETURN_PRIMARY&&returnPortal){
   if(!dryCorridor(p,feet)||wet||!ground)return block(f,"supplemental dry corridor lost original support");
  }else if(!portalCentering&&(!column(p,feet)||depth<-.18||depth>length+.18||feet.y()<base-.02||feet.y()>base+2.3))
   return block(f,"supplemental body left the nonfarmland column envelope");
  switch(f.state()){
   case APPROACH->{if(at(feet,p.approachFeet(),.1,.01)&&ground)phase(f,State.JUMP_CAP,"native dry cap jump");else toward(f,p.approachFeet(),false,true);}
   case JUMP_CAP->{
    if(at(feet,p.capFeet(),.18,.03)&&ground){if(!jumpIssued||!sawAirborne)return block(f,"cap landing lacks actual keyed airborne observation");phase(f,State.ENTER_CHANNEL,"entering loaded shallow water");}
    else{if(!ground)sawAirborne=true;
     if(ground&&sawAirborne){
      if(!jumpIssued||!supportedCapCentering(p,feet,ground,wet))return block(f,"jump landed outside the original dirt cap");
      reason="centering on the supported original dirt cap";toward(f,p.capFeet(),false,false);
     }else{boolean jump=!jumpIssued&&ground&&!wet;jumpIssued|=jump;toward(f,p.capFeet(),jump,false);}}
   }
   case ENTER_CHANNEL->{if(at(feet,p.channelNear(),.18,.06)&&wet&&ground)phase(f,State.SWEEP_OUT,"fixed-view outward channel sweep");else toward(f,p.channelNear(),false,true);}
   case SWEEP_OUT,SWEEP_BACK->{
    if((!ground&&!wet)||feet.y()>base+.9||depth<1.7)return block(f,"water pass lacks actual floor/water support");
    Vec3 target=f.state()==State.SWEEP_OUT?p.channelFar():p.channelNear();
    if(at(feet,target,.12,.12))phase(f,f.state()==State.SWEEP_OUT?State.SWEEP_BACK:State.JUMP_EXIT,f.state()==State.SWEEP_OUT?"fixed-view return sweep":"native water cap climb");
    else toward(f,target,false,true);
   }
   case JUMP_EXIT->{if(at(feet,p.capFeet(),.18,.03)&&ground){if(!jumpIssued)return block(f,"cap exit lacks actual requested water jump");phase(f,State.RETURN_PRIMARY,"returning to original primary end");}else{boolean jump=wet;jumpIssued|=jump;toward(f,p.capFeet(),jump,false);}}
   case RETURN_PRIMARY->{
    if(!returnPortal){if(!portalCentering&&(depth<-.18||depth>1.18||feet.y()>base+1.05))return block(f,"cap exit left original dry portal");if(at(feet,p.approachFeet(),.1,.01)&&ground&&!wet){returnPortal=true;movement.release();}else toward(f,p.approachFeet(),false,false);}
    else if(at(feet,p.primaryEnd(),.18,.01)&&ground&&!wet){state=State.COMPLETE;reason="same original primary end physically restored";movement.release();}
    else toward(f,p.primaryEnd(),false,false);
   }
   default->throw new AssertionError("Unsupported active phase "+f.state());
  }
  return state;
 }
 private static int phaseLimit(State s){return switch(s){case APPROACH,RETURN_PRIMARY->160;case JUMP_CAP,JUMP_EXIT->160;case ENTER_CHANNEL->120;case SWEEP_OUT,SWEEP_BACK->1100;default->1;};}
 public FarmMaintenanceState maintenanceState(){return FarmMaintenanceState.of(this,"world",world,"movement",movement,"plan",plan,"state",state,"ticks",ticks,"phaseTicks",phaseTicks,"jumpIssued",jumpIssued,"sawAirborne",sawAirborne,"returnPortal",returnPortal,"generation",generation,"generationExhausted",generationExhausted,"tickingFrame",tickingFrame,"reason",reason);}
}
