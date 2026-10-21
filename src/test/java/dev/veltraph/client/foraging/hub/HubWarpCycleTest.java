package dev.veltraph.client.foraging.hub;
import dev.veltraph.client.world.Vec3;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class HubWarpCycleTest {
    static final Object PLAYER=new Object(),CONNECTION=new Object(),WORLD=new Object();
    static HubWarpCycle.Contract contract(){return new HubWarpCycle.Contract("warp hub","Warp accepted",Set.of("Warp denied"),"minecraft:overworld",new Vec3(-20,71,-27),.75,15000);}
    static HubWarpCycle.Observation observed(Vec3 p,boolean hub){return new HubWarpCycle.Observation(PLAYER,CONNECTION,WORLD,"minecraft:overworld",p,hub,true);}
    static HubWarpCycle.Observation origin(){return observed(new Vec3(-100,73,-25),true);}
    @Test void blankContractDoesNotDefaultToGarden(){var c=new HubWarpCycle(()->1750);assertFalse(c.start(new HubWarpCycle.Contract("","",Set.of(),"",null,.75,15000),origin(),0));assertEquals(HubWarpCycle.State.IDLE,c.state());}
    @Test void sendAndAckNeverReplaceActualTeleportAndPose(){var c=new HubWarpCycle(()->1750);assertTrue(c.start(contract(),origin(),0));c.sent(true);c.feedback(origin(),"Warp accepted",1);c.tick(origin(),2);assertEquals(HubWarpCycle.State.WAITING,c.state());assertTrue(c.consumeExpectedTeleport(origin(),contract().destination(),new HubWarpCycle.TeleportReceipt(1,contract().destination()),3));c.tick(origin(),4);assertEquals(HubWarpCycle.State.WAITING,c.state());for(int tick=0;tick<6;tick++)c.tick(observed(contract().destination(),true),5+tick);assertEquals(HubWarpCycle.State.ARRIVED,c.state());}
    @Test void poseAndTeleportWithoutAckRemainPending(){var c=new HubWarpCycle(()->1750);c.start(contract(),origin(),0);c.sent(true);assertTrue(c.consumeExpectedTeleport(origin(),contract().destination(),new HubWarpCycle.TeleportReceipt(1,contract().destination()),1));c.tick(observed(contract().destination(),true),2);assertEquals(HubWarpCycle.State.WAITING,c.state());}
    @Test void wrongAndDuplicateTeleportsAreNotPermitted(){var c=new HubWarpCycle(()->1750);c.start(contract(),origin(),0);c.sent(true);assertFalse(c.consumeExpectedTeleport(origin(),new Vec3(0,71,0),new HubWarpCycle.TeleportReceipt(1,contract().destination()),1));assertTrue(c.consumeExpectedTeleport(origin(),contract().destination(),new HubWarpCycle.TeleportReceipt(1,contract().destination()),2));assertFalse(c.consumeExpectedTeleport(origin(),contract().destination(),new HubWarpCycle.TeleportReceipt(1,contract().destination()),3));}
    @Test void wrongAreaAndDimensionCannotComplete(){var c=new HubWarpCycle(()->1750);c.start(contract(),origin(),0);c.sent(true);c.feedback(origin(),"Warp accepted",1);c.consumeExpectedTeleport(origin(),contract().destination(),new HubWarpCycle.TeleportReceipt(1,contract().destination()),2);c.tick(observed(contract().destination(),false),3);assertEquals(HubWarpCycle.State.WAITING,c.state());c.tick(new HubWarpCycle.Observation(PLAYER,CONNECTION,WORLD,"minecraft:the_nether",contract().destination(),true,true),4);assertEquals(HubWarpCycle.State.WAITING,c.state());}
    @Test void exactRejectionTerminatesAndSuppressesPermit(){var c=new HubWarpCycle(()->1750);c.start(contract(),origin(),0);c.sent(true);c.feedback(origin(),"Warp denied",1);assertEquals(HubWarpCycle.State.FAILED,c.state());assertFalse(c.consumeExpectedTeleport(origin(),contract().destination(),new HubWarpCycle.TeleportReceipt(1,contract().destination()),2));}
    @Test void contextAndMenuChangeCancelBeforeReceipt(){for(int i=0;i<4;i++){var c=new HubWarpCycle(()->1750);c.start(contract(),origin(),0);c.sent(true);var bad=new HubWarpCycle.Observation(i==0?new Object():PLAYER,i==1?new Object():CONNECTION,i==2?new Object():WORLD,"minecraft:overworld",contract().destination(),true,i!=3);assertFalse(c.consumeExpectedTeleport(bad,contract().destination(),new HubWarpCycle.TeleportReceipt(1,contract().destination()),1));assertEquals(HubWarpCycle.State.FAILED,c.state());}}
    @Test void deadlineCancelAndFailedSendReleaseAuthority(){var c=new HubWarpCycle(()->1750);c.start(contract(),origin(),0);c.sent(true);c.tick(origin(),15001);assertEquals(HubWarpCycle.State.FAILED,c.state());c.reset();c.start(contract(),origin(),0);c.sent(true);c.sent(false);assertEquals(HubWarpCycle.State.FAILED,c.state());c.reset();c.start(contract(),origin(),0);c.sent(true);c.cancel();assertFalse(c.consumeExpectedTeleport(origin(),contract().destination(),new HubWarpCycle.TeleportReceipt(1,contract().destination()),1));assertEquals(HubWarpCycle.State.CANCELLED,c.state());}
    @Test void staleAckDoesNotCountForNextTransaction(){var c=new HubWarpCycle(()->1750);c.start(contract(),origin(),0);c.sent(true);c.feedback(origin(),"Warp accepted",1);c.reset();c.start(contract(),origin(),2);c.sent(true);c.consumeExpectedTeleport(origin(),contract().destination(),new HubWarpCycle.TeleportReceipt(1,contract().destination()),3);c.tick(observed(contract().destination(),true),4);assertEquals(HubWarpCycle.State.WAITING,c.state());}
    @Test void sameUuidIssuedReplacementWaitsUntilStablePhysicalReceipt(){
        var uuid=UUID.randomUUID();var origin=new HubWarpCycle.Observation(PLAYER,CONNECTION,WORLD,"minecraft:overworld",new Vec3(-100,73,-25),true,true,uuid);
        var replacement=new HubWarpCycle.Observation(new Object(),CONNECTION,new Object(),"minecraft:overworld",contract().destination(),true,true,uuid);
        var c=new HubWarpCycle(()->1750);c.start(contract(),origin,0);assertFalse(c.permitsPendingReplacement(c.issuedTicket(),replacement,1));c.sent(true);
        var ticket=c.issuedTicket();assertTrue(c.permitsPendingReplacement(ticket,replacement,1));c.feedback(replacement,"Warp accepted",2);
        assertTrue(c.consumeExpectedTeleport(replacement,contract().destination(),new HubWarpCycle.TeleportReceipt(1,contract().destination()),3));
        for(int i=0;i<5;i++){c.tick(replacement,4+i);assertFalse(c.bindsArrivedReplacement(replacement));}
        c.tick(replacement,9);assertTrue(c.bindsArrivedReplacement(replacement));assertFalse(c.permitsPendingReplacement(ticket,replacement,10));
    }
    @Test void missingWorldOnlyWaitsOnIssuedSameConnectionUuidTicket(){
        var uuid=UUID.randomUUID();var origin=new HubWarpCycle.Observation(PLAYER,CONNECTION,WORLD,"minecraft:overworld",new Vec3(-100,73,-25),true,true,uuid);
        var empty=new HubWarpCycle.Observation(null,CONNECTION,null,"",null,false,true,null);
        var c=new HubWarpCycle(()->1750);c.start(contract(),origin,0);c.sent(true);assertTrue(c.permitsPendingReplacement(c.issuedTicket(),empty,1));assertFalse(c.consumeExpectedTeleport(empty,contract().destination(),new HubWarpCycle.TeleportReceipt(1,contract().destination()),2));
        assertFalse(c.permitsPendingReplacement(c.issuedTicket(),empty,15001));
    }
    @Test void foreignUuidAndForeignConnectionDoNotAcquireIssuedTicket(){
        for(int i=0;i<2;i++){var c=new HubWarpCycle(()->1750);var uuid=UUID.randomUUID();var origin=new HubWarpCycle.Observation(PLAYER,CONNECTION,WORLD,"minecraft:overworld",new Vec3(0,70,0),true,true,uuid);c.start(contract(),origin,0);c.sent(true);
            var other=new HubWarpCycle.Observation(new Object(),i==0?new Object():CONNECTION,new Object(),"minecraft:overworld",contract().destination(),true,true,i==1?UUID.randomUUID():uuid);
            assertFalse(c.permitsPendingReplacement(c.issuedTicket(),other,1));assertEquals(HubWarpCycle.State.FAILED,c.state());}
    }
    @Test void sourceCadenceRetriesAtMostSixCommandsAndNoUnissuedRetry(){
        var c=new HubWarpCycle(()->1750);c.start(contract(),origin(),0);assertTrue(c.nextCommand(origin(),2000).isEmpty());c.sent(true);
        for(int i=1;i<=5;i++){assertTrue(c.nextCommand(origin(),i*1750-1).isEmpty());assertEquals("warp hub",c.nextCommand(origin(),i*1750).orElseThrow());c.sent(true);}
        assertEquals(6,c.commandSends());assertTrue(c.nextCommand(origin(),10500).isEmpty());assertEquals(HubWarpCycle.State.FAILED,c.state());
    }
    @Test void sourceOptionalAckUsesIssuedTeleportAndSixStableBodyReceipts(){
        var configured=new HubWarpCycle.Contract("warp hub","",Set.of("Warp denied"),"minecraft:overworld",contract().destination(),.75,15000);
        var c=new HubWarpCycle(()->1750);assertTrue(c.start(configured,origin(),0));c.sent(true);c.consumeExpectedTeleport(origin(),contract().destination(),new HubWarpCycle.TeleportReceipt(1,contract().destination()),1);
        var notReady=new HubWarpCycle.Observation(PLAYER,CONNECTION,WORLD,"minecraft:overworld",contract().destination(),true,true,null,false);
        for(int i=0;i<8;i++)c.tick(notReady,i+2);assertEquals(HubWarpCycle.State.WAITING,c.state());
        for(int i=0;i<6;i++)c.tick(observed(contract().destination(),true),i+10);assertEquals(HubWarpCycle.State.ARRIVED,c.state());
    }
    @Test void lifecycleProbeIsReadOnlyAndDoesNotAdvanceStableTicksOrSends(){
        var c=new HubWarpCycle(()->1750);c.start(contract(),origin(),0);c.sent(true);c.feedback(origin(),"Warp accepted",1);c.consumeExpectedTeleport(origin(),contract().destination(),new HubWarpCycle.TeleportReceipt(1,contract().destination()),2);
        for(int i=0;i<100;i++)assertTrue(c.pendingContextMatches(observed(contract().destination(),true),10000));
        assertEquals(HubWarpCycle.State.WAITING,c.state());assertEquals(1,c.commandSends());assertTrue(c.hasIssuedWarp());
        for(int i=0;i<5;i++)c.tick(observed(contract().destination(),true),i+3);assertEquals(HubWarpCycle.State.WAITING,c.state());
        c.tick(observed(contract().destination(),true),8);assertTrue(c.bindsArrivedReplacement(observed(contract().destination(),true)));assertTrue(c.hasIssuedWarp());
        c.reset();assertFalse(c.hasIssuedWarp());
    }
    @Test void repeatedObserversCannotAdvanceSameNativeTickReadiness(){
        var configured=new HubWarpCycle.Contract("warp hub","",Set.of(),"minecraft:overworld",contract().destination(),.75,15000);
        var c=new HubWarpCycle(()->1750);c.start(configured,origin(),0);c.sent(true);c.consumeExpectedTeleport(origin(),contract().destination(),new HubWarpCycle.TeleportReceipt(1,contract().destination()),1);
        for(int tick=0;tick<5;tick++)for(int observer=0;observer<10;observer++)c.tick(new HubWarpCycle.Observation(PLAYER,CONNECTION,WORLD,"minecraft:overworld",contract().destination(),true,true,null,true,tick),2+tick*10+observer);
        assertEquals(HubWarpCycle.State.WAITING,c.state());
        c.tick(new HubWarpCycle.Observation(PLAYER,CONNECTION,WORLD,"minecraft:overworld",contract().destination(),true,true,null,true,5),60);assertEquals(HubWarpCycle.State.ARRIVED,c.state());
    }
    @Test void loadingScreenNeedsExactIssuedConnectionAndIdenticalTracker(){
        var c=new HubWarpCycle(()->1750);c.start(contract(),origin(),0);var tracker=new Object();
        assertFalse(c.permitsLoadingScreen(CONNECTION,true,tracker,tracker,1));c.sent(true);
        assertTrue(c.permitsLoadingScreen(CONNECTION,true,tracker,tracker,1));
        assertFalse(c.permitsLoadingScreen(new Object(),true,tracker,tracker,1));
        assertFalse(c.permitsLoadingScreen(CONNECTION,false,tracker,tracker,1));
        assertFalse(c.permitsLoadingScreen(CONNECTION,true,tracker,new Object(),1));
        assertFalse(c.permitsLoadingScreen(CONNECTION,true,null,null,1));
        assertFalse(c.permitsLoadingScreen(CONNECTION,true,tracker,tracker,15001));c.cancel();assertFalse(c.permitsLoadingScreen(CONNECTION,true,tracker,tracker,2));
    }
    @Test void foreignScreenDuringMissingWorldDoesNotAcquireLoadingAllowance(){
        var uuid=UUID.randomUUID();var c=new HubWarpCycle(()->1750);c.start(contract(),new HubWarpCycle.Observation(PLAYER,CONNECTION,WORLD,"minecraft:overworld",new Vec3(0,70,0),true,true,uuid),0);c.sent(true);
        var blocked=new HubWarpCycle.Observation(null,CONNECTION,null,"",null,false,false,null);assertFalse(c.permitsPendingReplacement(c.issuedTicket(),blocked,1));assertEquals(HubWarpCycle.State.FAILED,c.state());
    }
    @Test void permittedLoadingNeverBecomesGameplayReadiness(){
        var c=new HubWarpCycle(()->1750);c.start(contract(),origin(),0);c.sent(true);c.feedback(origin(),"Warp accepted",1);c.consumeExpectedTeleport(origin(),contract().destination(),new HubWarpCycle.TeleportReceipt(1,contract().destination()),2);
        var loading=new HubWarpCycle.Observation(PLAYER,CONNECTION,WORLD,"minecraft:overworld",contract().destination(),true,true,null,false,0);
        for(int i=0;i<20;i++)c.tick(loading,i+3);assertEquals(HubWarpCycle.State.WAITING,c.state());
    }
    @Test void actualReceiptCompletesWithoutHydraExemptionAndExemptionConsumesSameReceiptOnce(){
        var configured=new HubWarpCycle.Contract("warp hub","",Set.of(),"minecraft:overworld",contract().destination(),.75,15000);
        var c=new HubWarpCycle(()->1750);c.start(configured,origin(),0,40);c.sent(true);
        var receipt=new HubWarpCycle.TeleportReceipt(41,contract().destination());assertTrue(c.observeTeleport(origin(),receipt,1));
        assertTrue(c.consumeExpectedTeleport(origin(),contract().destination(),receipt,2));assertFalse(c.consumeExpectedTeleport(origin(),contract().destination(),receipt,3));
        for(int i=0;i<6;i++)c.tick(observed(contract().destination(),true),4+i);assertEquals(HubWarpCycle.State.ARRIVED,c.state());
    }
    @Test void preSendAndStaleSerialOrForeignContextCannotManufactureReceipt(){
        var c=new HubWarpCycle(()->1750);c.start(contract(),origin(),0,20);
        assertFalse(c.observeTeleport(origin(),new HubWarpCycle.TeleportReceipt(21,contract().destination()),1));c.sent(true);
        for(int serial=18;serial<=20;serial++){var receipt=new HubWarpCycle.TeleportReceipt(serial,contract().destination());assertFalse(c.observeTeleport(origin(),receipt,2));assertFalse(c.consumeExpectedTeleport(origin(),contract().destination(),receipt,2));}
        assertFalse(c.observeTeleport(origin(),new HubWarpCycle.TeleportReceipt(21,null),3));
        var foreign=new HubWarpCycle.Observation(PLAYER,new Object(),WORLD,"minecraft:overworld",contract().destination(),true,true);
        assertFalse(c.observeTeleport(foreign,new HubWarpCycle.TeleportReceipt(21,contract().destination()),4));assertEquals(HubWarpCycle.State.FAILED,c.state());
    }
    @Test void freshReceiptAtWrongDestinationFailsWithHydraDisabled(){var c=new HubWarpCycle(()->1750);c.start(contract(),origin(),0,10);c.sent(true);assertFalse(c.observeTeleport(origin(),new HubWarpCycle.TeleportReceipt(11,new Vec3(0,70,0)),1));assertEquals(HubWarpCycle.State.FAILED,c.state());}
    @Test void newerPacketCannotReforgeConsumedExemption(){var c=new HubWarpCycle(()->1750);c.start(contract(),origin(),0,10);c.sent(true);assertTrue(c.consumeExpectedTeleport(origin(),contract().destination(),new HubWarpCycle.TeleportReceipt(11,contract().destination()),1));assertTrue(c.observeTeleport(origin(),new HubWarpCycle.TeleportReceipt(12,contract().destination()),2));assertFalse(c.consumeExpectedTeleport(origin(),contract().destination(),new HubWarpCycle.TeleportReceipt(12,contract().destination()),3));}
    @Test void crossDimensionReplacementCompletesFromFreshPacketWithoutHydraOrAck(){
        var uuid=UUID.randomUUID();var start=new HubWarpCycle.Observation(PLAYER,CONNECTION,WORLD,"minecraft:the_nether",new Vec3(10,70,10),false,true,uuid);
        var arrived=new HubWarpCycle.Observation(new Object(),CONNECTION,new Object(),"minecraft:overworld",contract().destination(),true,true,uuid);
        var configured=new HubWarpCycle.Contract("warp hub","",Set.of(),"minecraft:overworld",contract().destination(),.75,15000);
        var c=new HubWarpCycle(()->1750);c.start(configured,start,0,30);c.sent(true);assertTrue(c.observeTeleport(arrived,new HubWarpCycle.TeleportReceipt(31,contract().destination()),1));
        for(int i=0;i<6;i++)c.tick(arrived,2+i);assertEquals(HubWarpCycle.State.ARRIVED,c.state());assertTrue(c.bindsArrivedReplacement(arrived));
    }
    @Test void foreignUuidCannotReuseOriginalEntityIdentity(){var uuid=UUID.randomUUID();var c=new HubWarpCycle(()->1750);c.start(contract(),new HubWarpCycle.Observation(PLAYER,CONNECTION,WORLD,"minecraft:overworld",new Vec3(0,70,0),true,true,uuid),0,10);c.sent(true);var foreign=new HubWarpCycle.Observation(PLAYER,CONNECTION,WORLD,"minecraft:overworld",contract().destination(),true,true,UUID.randomUUID());assertFalse(c.pendingContextMatches(foreign,1));assertFalse(c.observeTeleport(foreign,new HubWarpCycle.TeleportReceipt(11,contract().destination()),1));}
    @Test void arrivedReadOnlyBindingRequiresCurrentExactNativeDestinationAndReadiness(){
        var configured=new HubWarpCycle.Contract("warp hub","",Set.of(),"minecraft:overworld",contract().destination(),.75,15000);
        var c=new HubWarpCycle(()->1750);c.start(configured,origin(),0,10);c.sent(true);c.observeTeleport(origin(),new HubWarpCycle.TeleportReceipt(11,contract().destination()),1);
        for(int i=0;i<6;i++)c.tick(observed(contract().destination(),true),2+i);
        assertTrue(c.bindsArrivedReplacement(observed(contract().destination(),true)));
        assertFalse(c.bindsArrivedReplacement(observed(new Vec3(0,70,0),true)));assertFalse(c.bindsArrivedReplacement(observed(contract().destination(),false)));
        assertFalse(c.bindsArrivedReplacement(new HubWarpCycle.Observation(PLAYER,CONNECTION,WORLD,"minecraft:the_nether",contract().destination(),true,true)));
        assertFalse(c.bindsArrivedReplacement(new HubWarpCycle.Observation(PLAYER,CONNECTION,WORLD,"minecraft:overworld",contract().destination(),true,true,null,false)));
    }
    @Test void matchingLabelMustBeExact(){var c=new HubWarpCycle(()->1750);c.start(contract(),origin(),0);c.sent(true);c.feedback(origin(),"Prefix Warp accepted",1);c.consumeExpectedTeleport(origin(),contract().destination(),new HubWarpCycle.TeleportReceipt(1,contract().destination()),2);c.tick(observed(contract().destination(),true),3);assertEquals(HubWarpCycle.State.WAITING,c.state());}
}
