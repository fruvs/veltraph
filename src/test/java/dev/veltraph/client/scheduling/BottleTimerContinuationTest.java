package dev.veltraph.client.scheduling;

import dev.veltraph.client.inventory.bottle.BottleAcquisitionRuntime;
import dev.veltraph.client.game.GameArea;
import dev.veltraph.client.world.Vec3;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

final class BottleTimerContinuationTest {
    private final Object owner=new Object(),pin=new Object(),player=new Object(),world=new Object(),connection=new Object();private final UUID uuid=UUID.randomUUID();
    private BottleAcquisitionRuntime.Travel travel(){return new BottleAcquisitionRuntime.Travel(1,player,world,connection,uuid,new BottleAcquisitionRuntime.Landing("nativehub",GameArea.HUB,"minecraft:overworld",new Vec3(.5,70,.5)),3,100,500,List.of());}
    @Test void oneGrantAdvancesOriginalTimerOnceWithoutNewArmOrPin(){var t=travel();var c=new BottleTimerContinuation(owner,pin,t);assertTrue(c.grant(owner,pin,t,connection,uuid,true,150));assertTrue(c.consume(owner,pin,t,connection,uuid,true,150));assertFalse(c.consume(owner,pin,t,connection,uuid,true,150));}
    @Test void repeatedWaitUsesSameOriginalPinAndUnchangedExpiry(){var t=travel();var c=new BottleTimerContinuation(owner,pin,t);for(long now:new long[]{150,200,500}){assertTrue(c.grant(owner,pin,t,connection,uuid,true,now));assertTrue(c.consume(owner,pin,t,connection,uuid,true,now));}assertFalse(c.grant(owner,pin,t,connection,uuid,true,501));assertFalse(c.grant(owner,pin,t,connection,uuid,true,200),"Expired ticket cannot revive");}
    @Test void foreignOwnerPinPermitConnectionOrUuidRevokesPermanently(){var t=travel();for(int which=0;which<5;which++){var c=new BottleTimerContinuation(owner,pin,t);assertFalse(c.grant(which==0?new Object():owner,which==1?new Object():pin,which==2?travel():t,which==3?new Object():connection,which==4?UUID.randomUUID():uuid,true,150));assertFalse(c.grant(owner,pin,t,connection,uuid,true,150));}}
    @Test void loadingHasCanonicalUuidInPermitAndSameConnectionEvenWithoutCurrentPlayer(){var t=travel();var c=new BottleTimerContinuation(owner,pin,t);assertTrue(c.grant(owner,pin,t,connection,null,true,150));assertTrue(c.consume(owner,pin,t,connection,null,true,150));assertFalse(c.grant(owner,pin,t,new Object(),null,true,200));}
    @Test void disabledMetadataClockRollbackAndExplicitStopCannotRetainTicket(){var t=travel();var c=new BottleTimerContinuation(owner,pin,t);assertFalse(c.grant(owner,pin,t,connection,uuid,false,150));var rollback=new BottleTimerContinuation(owner,pin,t);assertFalse(rollback.grant(owner,pin,t,connection,uuid,true,99));var stop=new BottleTimerContinuation(owner,pin,t);stop.revoke();assertFalse(stop.grant(owner,pin,t,connection,uuid,true,150));}
    @Test void clockRollbackAfterAValidWaitPermanentlyRevokes(){var t=travel();var c=new BottleTimerContinuation(owner,pin,t);assertTrue(c.grant(owner,pin,t,connection,uuid,true,200));assertTrue(c.consume(owner,pin,t,connection,uuid,true,200));assertFalse(c.grant(owner,pin,t,connection,uuid,true,199));assertFalse(c.grant(owner,pin,t,connection,uuid,true,201));}
}
