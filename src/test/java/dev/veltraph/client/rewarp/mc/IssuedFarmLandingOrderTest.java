package dev.veltraph.client.rewarp.mc;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class IssuedFarmLandingOrderTest {
 final Object packet=new Object(),listener=new Object(),player=new Object(),world=new Object(),command=new Object();
 IssuedFarmLandingOrder fresh(){return new IssuedFarmLandingOrder(packet,listener,player,world,command);}
 @Test void actualApplyAckOuterReturnRequiredAndConsumedOnce(){var o=fresh();assertTrue(o.enter(packet,listener,player,world,command));assertFalse(o.complete());assertTrue(o.applied(packet,true,true));assertTrue(o.acknowledged(packet,true,true));assertTrue(o.returned(packet,true));assertTrue(o.complete());assertTrue(o.consume(command));assertFalse(o.consume(command));}
 @Test void directUnissuedAndForeignPacketContextCannotEnter(){for(Object foreign:new Object[]{new Object()}){var o=fresh();assertFalse(o.enter(foreign,listener,player,world,command));var l=fresh();assertFalse(l.enter(packet,foreign,player,world,command));var p=fresh();assertFalse(p.enter(packet,listener,foreign,world,command));var w=fresh();assertFalse(w.enter(packet,listener,player,foreign,command));var c=fresh();assertFalse(c.enter(packet,listener,player,world,foreign));}}
 @Test void cancelledApplicationAndMissingAckCannotReturn(){var o=fresh();o.enter(packet,listener,player,world,command);assertFalse(o.applied(packet,false,true));assertFalse(o.returned(packet,true));var a=fresh();a.enter(packet,listener,player,world,command);a.applied(packet,true,true);assertFalse(a.returned(packet,true));}
 @Test void forgedWrongAckAndRecursiveApplyRefuse(){var o=fresh();o.enter(packet,listener,player,world,command);o.applied(packet,true,true);assertFalse(o.acknowledged(packet,true,false));var a=fresh();a.enter(packet,listener,player,world,command);assertTrue(a.applied(packet,true,true));assertFalse(a.applied(packet,true,true));}
 @Test void throwingOuterHandlerCannotMintLanding(){var o=fresh();o.enter(packet,listener,player,world,command);o.applied(packet,true,true);o.acknowledged(packet,true,true);assertFalse(o.returned(packet,false));assertFalse(o.complete());}
 @Test void staleCommandOrReturnReplayCannotAdoptLanding(){var o=fresh();o.enter(packet,listener,player,world,command);o.applied(packet,true,true);o.acknowledged(packet,true,true);o.returned(packet,true);assertFalse(o.consume(new Object()));var r=fresh();r.enter(packet,listener,player,world,command);r.applied(packet,true,true);r.acknowledged(packet,true,true);r.returned(packet,true);assertFalse(r.returned(packet,true));}
}
