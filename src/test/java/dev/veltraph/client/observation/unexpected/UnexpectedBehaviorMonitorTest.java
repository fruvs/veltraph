package dev.veltraph.client.observation.unexpected;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static dev.veltraph.client.observation.unexpected.UnexpectedBehaviorMonitor.*;

final class UnexpectedBehaviorMonitorTest {
    private final Context context=new Context(new Activation("farm",1,true),1,1,1);
    private final UnexpectedBehaviorMonitor monitor=new UnexpectedBehaviorMonitor(f->{});
    private Finding event(Kind kind,Value before,Value after,long now) {return monitor.observe(new Observation(context,kind,before,after,now));}
    private Finding rotate(double yaw,long now) {return event(Kind.ROTATION,Value.rotation(0,0),Value.rotation(yaw,0),now);}
    private Finding move(double x,long now) {return event(Kind.POSITION,Value.position(0,0,0),Value.position(x,0,0),now);}
    @Test void exactRotationAcknowledgesOnlyOneIntent() {
        monitor.intent(context,Kind.ROTATION,Value.rotation(90,0),.01,100,100);
        assertEquals(Classification.EXPLAINED,rotate(90,101).classification());
        assertEquals(0,monitor.pendingIntents());
        assertEquals(Classification.UNEXPECTED,rotate(90,102).classification());
    }
    @Test void wrongLandingCannotConsumeTeleportTicket() {
        monitor.intent(context,Kind.POSITION,Value.position(10,0,0),1,100,1000);
        assertEquals(Classification.UNEXPECTED,move(30,101).classification());assertEquals(1,monitor.pendingIntents());
        assertEquals(Classification.EXPLAINED,move(10.5,102).classification());
    }
    @Test void supersededIntermediateDispatchCannotExplainLaterChange() {monitor.intent(context,Kind.ROTATION,Value.rotation(30,0),0,100,100);monitor.intent(context,Kind.ROTATION,Value.rotation(60,0),0,101,100);assertEquals(Classification.UNEXPECTED,rotate(30,102).classification());assertEquals(Classification.EXPLAINED,rotate(60,103).classification());}
    @Test void expiredIntentDoesNotExplainLaterEvent() {monitor.intent(context,Kind.ROTATION,Value.rotation(90,0),0,100,10);assertEquals(Classification.UNEXPECTED,rotate(90,111).classification());}
    @Test void futureEventDoesNotApplyBackwards() {monitor.intent(context,Kind.ROTATION,Value.rotation(90,0),0,100,100);assertEquals(Classification.UNEXPECTED,rotate(90,99).classification());}
    @Test void activationChangeClearsIntents() {monitor.intent(context,Kind.ROTATION,Value.rotation(90,0),0,100,100);monitor.context(new Context(new Activation("farm",2,true),1,1,1),101);assertEquals(0,monitor.pendingIntents());assertEquals(Classification.UNCERTAIN,rotate(90,102).classification());}
    @Test void eachNativeIdentityChangeClears() {
        for(var different:new Context[]{new Context(context.activation(),2,1,1),new Context(context.activation(),1,2,1),new Context(context.activation(),1,1,2)}) {
            monitor.intent(context,Kind.ROTATION,Value.rotation(90,0),0,100,100);monitor.context(different,101);assertEquals(0,monitor.pendingIntents());
        }
    }
    @Test void physicalKeyboardDoesNotExplainRotationOrTeleport() {
        monitor.signal(context,Kind.POSITION,Cause.NATIVE_KEY_EVENT,100,200);
        monitor.signal(context,Kind.INPUT_STATE,Cause.NATIVE_KEY_EVENT,100,200);
        assertEquals(Classification.UNEXPECTED,rotate(90,101).classification());assertEquals(Classification.UNEXPECTED,move(20,102).classification());
    }
    @Test void boundedManualMovementHasEvidenceButNoHumanIdentityClaim() {monitor.signal(context,Kind.POSITION,Cause.NATIVE_KEY_EVENT,100,200);var f=move(.2,101);assertEquals(Classification.EXPLAINED,f.classification());assertTrue(f.evidence().contains(Cause.NATIVE_KEY_EVENT));}
    @Test void physicalMouseExplainsLocalRotation() {monitor.nativeReceipt(context,Kind.ROTATION,Cause.NATIVE_MOUSE_EVENT,Value.rotation(90,0),.01,100,150);var f=rotate(90,101);assertEquals(Classification.EXPLAINED,f.classification());assertTrue(f.detail().contains("identity is unknown"));}
    @Test void nativeReceiptAcknowledgesOnlyOneChange() {monitor.nativeReceipt(context,Kind.ROTATION,Cause.NATIVE_MOUSE_EVENT,Value.rotation(90,0),.01,100,150);assertEquals(Classification.EXPLAINED,rotate(90,101).classification());assertEquals(Classification.UNEXPECTED,rotate(90,102).classification());}
    @Test void wrongRotationAfterNativeMouseRemainsUnexpected() {monitor.nativeReceipt(context,Kind.ROTATION,Cause.NATIVE_MOUSE_EVENT,Value.rotation(20,0),.01,100,150);assertEquals(Classification.UNEXPECTED,rotate(90,101).classification());}
    @Test void cursorEventAloneCannotExplainRotation() {monitor.signal(context,Kind.ROTATION,Cause.NATIVE_MOUSE_EVENT,100,150);assertEquals(Classification.UNEXPECTED,rotate(90,101).classification());}
    @Test void mouseAndServerCorrectionDoNotCreateBlanketImmunity() {monitor.nativeReceipt(context,Kind.ROTATION,Cause.NATIVE_MOUSE_EVENT,Value.rotation(90,0),.01,100,150);monitor.signal(context,Kind.ROTATION,Cause.SERVER_POSITION_PACKET,100,150);assertEquals(Classification.UNEXPECTED,rotate(90,101).classification());}
    @Test void rawKeyCallbackCannotExplainArbitraryKeyMappingWrite() {monitor.signal(context,Kind.INPUT_STATE,Cause.NATIVE_KEY_EVENT,100,200);assertEquals(Classification.UNCERTAIN,event(Kind.INPUT_STATE,Value.number(0),Value.number(7),101).classification());}
    @Test void manualNavigationWithoutDestinationLeavesOneRelocationUncertain() {
        monitor.signal(context,Kind.POSITION,Cause.NATIVE_NAVIGATION_COMMAND,100,5000);
        monitor.signal(context,Kind.POSITION,Cause.SERVER_POSITION_PACKET,101,150);
        assertEquals(Classification.UNCERTAIN,move(100,102).classification());
        assertEquals(Classification.UNEXPECTED,move(200,103).classification());
    }
    @Test void unknownManualNavigationDestinationWithoutPacketIsStillUncertain() {monitor.signal(context,Kind.POSITION,Cause.NATIVE_NAVIGATION_COMMAND,100,5000);assertEquals(Classification.UNCERTAIN,move(100,101).classification());assertEquals(Classification.UNEXPECTED,move(100,102).classification());}
    @Test void manualCommandDoesNotImmunizeRotationOrGameMode() {
        monitor.signal(context,Kind.POSITION,Cause.NATIVE_NAVIGATION_COMMAND,100,5000);
        assertEquals(Classification.UNEXPECTED,rotate(90,101).classification());
        assertEquals(Classification.UNEXPECTED,event(Kind.GAMEMODE,Value.number(0),Value.number(1),102).classification());
    }
    @Test void exactNativeCommandRequiresMatchingServerReceiptAndTarget() {
        monitor.nativeReceipt(context,Kind.POSITION,Cause.NATIVE_NAVIGATION_COMMAND,Value.position(40,0,0),.5,100,5000);
        monitor.signal(context,Kind.POSITION,Cause.SERVER_POSITION_PACKET,101,150);
        assertEquals(Classification.UNEXPECTED,move(50,102).classification());
        assertEquals(Classification.EXPLAINED,move(40,103).classification());
        assertEquals(Classification.UNEXPECTED,move(40,104).classification());
    }
    @Test void matchingNativeCommandTargetWithoutServerAckRemainsUncertain() {monitor.nativeReceipt(context,Kind.POSITION,Cause.NATIVE_NAVIGATION_COMMAND,Value.position(40,0,0),.5,100,5000);assertEquals(Classification.UNCERTAIN,move(40,101).classification());}
    @Test void navigationCorrelationExpiresAndCannotCrossNativeContext() {
        monitor.signal(context,Kind.POSITION,Cause.NATIVE_NAVIGATION_COMMAND,100,5000);
        monitor.signal(context,Kind.POSITION,Cause.SERVER_POSITION_PACKET,5101,150);
        assertEquals(Classification.UNEXPECTED,move(30,5102).classification());
        monitor.signal(context,Kind.POSITION,Cause.NATIVE_NAVIGATION_COMMAND,5200,5000);
        var next=new Context(context.activation(),1,1,2);monitor.context(next,5201);
        monitor.signal(next,Kind.POSITION,Cause.SERVER_POSITION_PACKET,5201,150);
        assertEquals(Classification.UNEXPECTED,monitor.observe(new Observation(next,Kind.POSITION,Value.position(0,0,0),Value.position(30,0,0),5202)).classification());
    }
    @Test void nativeCommandCanPrecedeActivationWithoutCarryingModIntent() {
        var idle=new Context(Activation.inactive(),1,1,1);monitor.signal(idle,Kind.POSITION,Cause.NATIVE_NAVIGATION_COMMAND,100,5000);
        monitor.context(context,101);monitor.signal(context,Kind.POSITION,Cause.SERVER_POSITION_PACKET,101,150);
        assertEquals(Classification.UNCERTAIN,move(40,102).classification());
        monitor.intent(context,Kind.POSITION,Value.position(40,0,0),.5,103,1000);
        var next=new Context(new Activation("another_owner",2,true),1,1,1);monitor.context(next,104);
        assertEquals(0,monitor.pendingIntents());
        monitor.signal(next,Kind.POSITION,Cause.SERVER_POSITION_PACKET,104,150);
        assertEquals(Classification.UNEXPECTED,monitor.observe(new Observation(next,Kind.POSITION,Value.position(0,0,0),Value.position(40,0,0),105)).classification());
    }
    @Test void navigationCannotGrantGenericKindWildcard() {
        assertThrows(IllegalArgumentException.class,()->monitor.signal(context,Kind.ROTATION,Cause.NATIVE_NAVIGATION_COMMAND,100,100));
        assertThrows(IllegalArgumentException.class,()->monitor.nativeReceipt(context,Kind.GAMEMODE,Cause.NATIVE_NAVIGATION_COMMAND,Value.number(1),0,100,100));
    }
    @Test void fastFallFlightVehicleAndKnockbackRemainUncertain() {
        for(var cause:new Cause[]{Cause.FALLING_STATE,Cause.FLIGHT,Cause.VEHICLE,Cause.VELOCITY_PACKET,Cause.DAMAGE_PACKET,Cause.EXPLOSION_PACKET}) {
            monitor.clear();monitor.signal(context,Kind.POSITION,cause,100,1000);assertEquals(Classification.UNCERTAIN,move(10,101).classification());
        }
    }
    @Test void serverCorrectionDuringHeldForwardRemainsUnexplained() {
        monitor.signal(context,Kind.POSITION,Cause.NATIVE_KEY_EVENT,100,150);
        monitor.signal(context,Kind.POSITION,Cause.MOD_MOVEMENT_INPUT,100,150);
        monitor.signal(context,Kind.POSITION,Cause.SERVER_POSITION_PACKET,100,150);
        assertEquals(Classification.UNEXPECTED,move(20,101).classification());
    }
    @Test void serverCorrectionIsNotExplainedByUnrelatedPhysicsState() {
        monitor.signal(context,Kind.POSITION,Cause.FLIGHT,100,150);
        monitor.signal(context,Kind.POSITION,Cause.SERVER_POSITION_PACKET,100,150);
        assertEquals(Classification.UNEXPECTED,move(20,101).classification());
    }
    @Test void collisionAloneDoesNotExplainDisplacement() {monitor.signal(context,Kind.POSITION,Cause.COLLISION,100,150);assertEquals(Classification.UNCERTAIN,move(.5,101).classification());}
    @Test void velocityDamageAndFluidsExplainOnlyBoundedMotion() {
        for(var cause:new Cause[]{Cause.VELOCITY_PACKET,Cause.DAMAGE_PACKET,Cause.FLUID,Cause.GRAVITY,Cause.VEHICLE,Cause.MOD_MOVEMENT_INPUT}) {
            monitor.clear();monitor.signal(context,Kind.POSITION,cause,100,500);assertEquals(Classification.EXPLAINED,move(.5,101).classification());assertEquals(cause==Cause.MOD_MOVEMENT_INPUT?Classification.UNEXPECTED:Classification.UNCERTAIN,move(20,102).classification());
        }
    }
    @Test void serverPositionReceiptProvesMechanismButNotExpectedIntent() {monitor.signal(context,Kind.POSITION,Cause.SERVER_POSITION_PACKET,100,150);var f=move(40,101);assertEquals(Classification.UNEXPECTED,f.classification());assertTrue(f.evidence().contains(Cause.SERVER_POSITION_PACKET));assertTrue(f.detail().contains("source identity"));}
    @Test void scopedLandingCanAcknowledgeServerPacket() {monitor.intent(context,Kind.POSITION,Value.position(40,0,0),.5,100,1000);monitor.signal(context,Kind.POSITION,Cause.SERVER_POSITION_PACKET,101,150);assertEquals(Classification.EXPLAINED,move(40,102).classification());}
    @Test void inventoryAndBlockServerChangesRemainUncertain() {
        for(var kind:new Kind[]{Kind.INVENTORY,Kind.BLOCK}) {monitor.signal(context,kind,kind==Kind.BLOCK?Cause.SERVER_BLOCK_PACKET:Cause.SERVER_INVENTORY_PACKET,100,150);assertEquals(Classification.UNCERTAIN,event(kind,Value.number(1),Value.number(2),101).classification());}
    }
    @Test void localInventoryClickDoesNotProveServerContentExpected() {monitor.signal(context,Kind.INVENTORY,Cause.NATIVE_CONTAINER_INPUT,100,150);assertEquals(Classification.UNCERTAIN,event(Kind.INVENTORY,Value.number(1),Value.number(2),101).classification());}
    @Test void joinRespawnLifecycleExplainsContextOnly() {monitor.signal(context,Kind.CONTEXT,Cause.JOIN_OR_RESPAWN,100,2000);assertEquals(Classification.UNCERTAIN,event(Kind.CONTEXT,Value.number(1),Value.number(2),101).classification());assertEquals(Classification.UNEXPECTED,move(50,102).classification());}
    @Test void serverForcedSelectedSlotIsNotExplainedByCoincidentKey() {monitor.nativeReceipt(context,Kind.SELECTED_SLOT,Cause.NATIVE_KEY_EVENT,Value.number(2),0,100,150);monitor.signal(context,Kind.SELECTED_SLOT,Cause.SERVER_INVENTORY_PACKET,100,150);assertEquals(Classification.UNEXPECTED,event(Kind.SELECTED_SLOT,Value.number(0),Value.number(2),101).classification());}
    @Test void exactModSlotIntentIsSupersededByServerPacket() {
        monitor.intent(context,Kind.SELECTED_SLOT,Value.number(2),0,100,500);
        monitor.signal(context,Kind.SELECTED_SLOT,Cause.SERVER_INVENTORY_PACKET,101,150);
        assertEquals(0,monitor.pendingIntents());
        assertEquals(Classification.UNEXPECTED,event(Kind.SELECTED_SLOT,Value.number(0),Value.number(2),102).classification());
    }
    @Test void matchedNativeHotbarReceiptIsExplainedOnlyOnce() {
        monitor.nativeReceipt(context,Kind.SELECTED_SLOT,Cause.NATIVE_KEY_EVENT,Value.number(2),0,100,150);
        assertEquals(Classification.EXPLAINED,event(Kind.SELECTED_SLOT,Value.number(0),Value.number(2),101).classification());
        assertEquals(Classification.UNEXPECTED,event(Kind.SELECTED_SLOT,Value.number(0),Value.number(2),102).classification());
    }
    @Test void wrongSlotCannotConsumeModIntent() {
        monitor.intent(context,Kind.SELECTED_SLOT,Value.number(2),0,100,500);
        assertEquals(Classification.UNEXPECTED,event(Kind.SELECTED_SLOT,Value.number(0),Value.number(3),101).classification());
        assertEquals(1,monitor.pendingIntents());
        assertEquals(Classification.EXPLAINED,event(Kind.SELECTED_SLOT,Value.number(3),Value.number(2),102).classification());
    }
    @Test void idleSlotDriftRemainsUncertain() {
        var idle=new Context(Activation.inactive(),1,1,1);monitor.context(idle,100);
        assertEquals(Classification.UNCERTAIN,monitor.observe(new Observation(idle,Kind.SELECTED_SLOT,Value.number(0),Value.number(2),101)).classification());
    }
    @Test void serverGameModeChangeWhileActiveIsUnexpected() {monitor.signal(context,Kind.GAMEMODE,Cause.SERVER_GAME_MODE_PACKET,100,150);assertEquals(Classification.UNEXPECTED,event(Kind.GAMEMODE,Value.number(0),Value.number(1),101).classification());}
    @Test void idleDiscontinuityIsUncertain() {var idle=new Context(Activation.inactive(),1,1,1);monitor.context(idle,100);assertEquals(Classification.UNCERTAIN,monitor.observe(new Observation(idle,Kind.POSITION,Value.position(0,0,0),Value.position(50,0,0),101)).classification());}
    @Test void ledgerAndHistoryAreBounded() {
        for(int n=0;n<1000;n++)monitor.intent(context,Kind.ROTATION,Value.rotation(n,0),0,100,1000);
        assertEquals(1,monitor.pendingIntents());for(int n=0;n<1000;n++)rotate(90,101);assertEquals(128,monitor.history().size());
    }
    @Test void noUnlimitedOrBroadExpectations() {
        assertThrows(IllegalArgumentException.class,()->monitor.intent(context,Kind.POSITION,Value.position(0,0,0),4,100,100));
        assertThrows(IllegalArgumentException.class,()->monitor.intent(context,Kind.ROTATION,Value.rotation(0,0),3,100,100));
        assertThrows(IllegalArgumentException.class,()->monitor.intent(context,Kind.INVENTORY,Value.number(0),1,100,100));
        assertThrows(IllegalArgumentException.class,()->monitor.intent(context,Kind.POSITION,Value.position(0,0,0),0,100,Long.MAX_VALUE));
        assertThrows(IllegalArgumentException.class,()->monitor.signal(context,Kind.POSITION,Cause.MOD_INTENT,100,100));
    }
    @Test void rotationWrapMatches() {monitor.intent(context,Kind.ROTATION,Value.rotation(359,0),.01,100,100);assertEquals(Classification.EXPLAINED,rotate(-1,101).classification());}
    @Test void observationsAndOwnerRejectUnboundedPrivateContent() {assertThrows(IllegalArgumentException.class,()->new Activation("player message here",1,true));assertThrows(IllegalArgumentException.class,()->Value.position(Double.NaN,0,0));}
    @Test void noInputDoesNotMeanStaffOrHuman() {var f=move(.4,100);assertEquals(Classification.UNCERTAIN,f.classification());assertFalse(f.detail().contains("staff"));}
}
