package dev.veltraph.client.navigation;
import dev.veltraph.client.world.Vec3;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
/** SOURCE ONLY, UNRUN. Actual native producers still must supply the scoped stamps/tickets. */
final class WaterPublicationIdentityTest {
 final Object actor=new Object(),leg=new Object(),parent=new Object(),player=new Object(),world=new Object(),connection=new Object(),config=new Object(),plan=new Object();
 WaterPublicationIdentity.Scope stamp(long generation,long epoch,long serial,long ticket){return new WaterPublicationIdentity.Scope(actor,leg,parent,player,world,connection,config,3,8,generation,epoch,serial,ticket);}
 @Test void actualOwnTicketAdvancesPlan(){var a=stamp(4,2,9,0);var b=stamp(4,3,10,10);var c=new WaterPublicationIdentity.Cursor(plan,a);assertTrue(c.published(plan,a,b,10));assertTrue(c.current(plan,b));assertFalse(c.current(plan,a));}
 @Test void rawSameParentSerialCannotBecomeCurrent(){var a=stamp(4,2,9,9);var c=new WaterPublicationIdentity.Cursor(plan,a);assertFalse(c.current(plan,stamp(4,2,10,10)));}
 @Test void sameEpochNewServiceGenerationDenied(){var a=stamp(4,2,9,9);assertFalse(new WaterPublicationIdentity.Cursor(plan,a).current(plan,stamp(5,2,9,9)));}
 @Test void sameEpochDifferentIssuedLegDenied(){var a=stamp(4,2,9,9);var b=new WaterPublicationIdentity.Scope(actor,new Object(),parent,player,world,connection,config,3,8,4,2,9,9);assertFalse(new WaterPublicationIdentity.Cursor(plan,a).current(plan,b));}
 @Test void sameLegDifferentActorDenied(){var a=stamp(4,2,9,9);var b=new WaterPublicationIdentity.Scope(new Object(),leg,parent,player,world,connection,config,3,8,4,2,9,9);assertFalse(new WaterPublicationIdentity.Cursor(plan,a).current(plan,b));}
 @Test void foreignPlanCursorDenied(){var a=stamp(4,2,9,9);assertFalse(new WaterPublicationIdentity.Cursor(plan,a).current(new Object(),a));}
 @Test void wrongActualTicketCannotRefresh(){var a=stamp(4,2,9,9);assertFalse(new WaterPublicationIdentity.Cursor(plan,a).published(plan,a,stamp(4,2,10,10),11));}
 @Test void skippedSerialCannotRefresh(){var a=stamp(4,2,9,9);assertFalse(new WaterPublicationIdentity.Cursor(plan,a).published(plan,a,stamp(4,2,11,11),11));}
 @Test void epochSkipCannotRefresh(){var a=stamp(4,2,9,9);assertFalse(new WaterPublicationIdentity.Cursor(plan,a).published(plan,a,stamp(4,4,10,10),10));}
 @Test void newRequestGenerationCannotRefresh(){var a=stamp(4,2,9,9);assertFalse(new WaterPublicationIdentity.Cursor(plan,a).published(plan,a,stamp(5,2,10,10),10));}
 @Test void exactSameTickOwnTicketMayBeReused(){var a=stamp(4,2,9,9);assertTrue(new WaterPublicationIdentity.Cursor(plan,a).published(plan,a,a,9));}
 @Test void unownedGlobalSerialCannotBeReused(){var a=stamp(4,2,9,0);assertFalse(new WaterPublicationIdentity.Cursor(plan,a).published(plan,a,a,9));}
 @Test void configRevisionABARevokes(){var a=stamp(4,2,9,9);var b=new WaterPublicationIdentity.Scope(actor,leg,parent,player,world,connection,config,5,8,4,2,9,9);assertFalse(new WaterPublicationIdentity.Cursor(plan,a).current(plan,b));}
 @Test void foreignPwcRevokes(){var a=stamp(4,2,9,9);var b=new WaterPublicationIdentity.Scope(actor,leg,parent,new Object(),new Object(),new Object(),config,3,8,4,2,9,9);assertFalse(new WaterPublicationIdentity.Cursor(plan,a).current(plan,b));}
 @Test void retirementNeverResurrects(){var a=stamp(4,2,9,9);var c=new WaterPublicationIdentity.Cursor(plan,a);c.revoke();assertFalse(c.current(plan,a));assertFalse(c.published(plan,a,stamp(4,2,10,10),10));}
 @Test void strictHoldCannotExtendOriginalDeadline(){var goal=new Vec3(1.5,67,.5);var hold=new WaterTerminalLease(plan,parent,player,world,connection,goal,.18,1000,5000);assertFalse(hold.expiresAt(5001));assertTrue(hold.valid(plan,parent,player,world,connection,goal,.18,true,4000));assertFalse(hold.valid(plan,parent,player,world,connection,goal,.18,true,5001));}
 @Test void strictHoldRadiusCannotWiden(){var goal=new Vec3(1.5,67,.5);var hold=new WaterTerminalLease(plan,parent,player,world,connection,goal,.18,1000,5000);assertFalse(hold.valid(plan,parent,player,world,connection,goal,.3,true,1001));}
 @Test void holdWrongCursorAndForeignOwnerDenied(){var goal=new Vec3(1.5,67,.5);var hold=new WaterTerminalLease(plan,parent,player,world,connection,goal,.18,1000,5000);assertFalse(hold.valid(new Object(),new Object(),player,world,connection,goal,.18,true,1001));}
}
