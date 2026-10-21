package dev.veltraph.client.navigation.mc;
import dev.veltraph.client.navigation.*;
import java.util.Objects;
/** Actual concrete Root leg WATER publisher. Own native tickets certify plan/hold continuation. */
public final class LeaseWaterAdapter implements WaterPublicationActor,WaterPublicationInputs {
 private final LegacyTravelPublicationLease.Leg leg;private Object plan;private WaterPublicationIdentity.Cursor cursor;private boolean retired;
 public LeaseWaterAdapter(LegacyTravelPublicationLease.Leg leg){this.leg=Objects.requireNonNull(leg);}
 public WaterPublicationActor actor(){return this;}
 public WaterPublicationIdentity.Scope scope(){var c=leg.commandContext();return c==null?null:new WaterPublicationIdentity.Scope(this,leg,c.parent(),c.player(),c.world(),c.connection(),c.config(),c.revision(),c.journey(),c.requestGeneration(),c.epoch(),c.publicationSerial(),leg.actualTicket());}
 public boolean current(){return !retired&&leg.current()&&(cursor==null||cursor.current(plan,scope()));}
 public boolean bindPlan(Object value){if(value==null||plan!=null||!current())return false;var stamp=scope();if(stamp==null||!current())return false;plan=value;cursor=new WaterPublicationIdentity.Cursor(value,stamp);return current();}
 public boolean planCurrent(Object value){return value!=null&&value==plan&&current();}
 public void retirePlan(Object value){if(value!=null&&value==plan){if(cursor!=null)cursor.revoke();plan=null;cursor=null;retired=true;}}
 private long publish(float yaw,Float pitch){if(!current()||cursor==null)return -1;var before=scope();long ticket=pitch==null?leg.publishYaw(yaw):leg.publishTracked(yaw,pitch);var after=scope();return cursor.published(plan,before,after,ticket)&&leg.current()?ticket:-1;}
 public long publishYaw(float yaw){return publish(yaw,null);}public long publishTrackedPose(float yaw,float pitch){return publish(yaw,pitch);}
 public void cleanupOwned(){leg.retireOwned();}
 private static void admitted(boolean ok){if(!ok)throw new WaterPublicationRevokedException();}
 public void setForwardHeld(boolean held){admitted(current()&&leg.setForwardHeld(held));}public void setBackHeld(boolean held){admitted(current()&&leg.setBackHeld(held));}
 public void setLeftHeld(boolean held){admitted(current()&&leg.setLeftHeld(held));}public void setRightHeld(boolean held){admitted(current()&&leg.setRightHeld(held));}
 public void setJumpHeld(boolean held){admitted(current()&&leg.setJumpHeld(held));}public void setSneakHeld(boolean held){admitted(current()&&leg.setSneakHeld(held));}
 public void setSprintHeld(boolean held){admitted(current()&&leg.setSprintHeld(held));}
 /** Active neutral input is a guarded publication; cleanup itself uses exact own nine-key receipts. */
 public void releaseAllMovement(){setForwardHeld(false);setBackHeld(false);setLeftHeld(false);setRightHeld(false);setJumpHeld(false);setSneakHeld(false);setSprintHeld(false);}
}
