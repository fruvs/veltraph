package dev.veltraph.client.builder;

import dev.veltraph.client.world.BlockPos;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SourceResourcePolicyTest {
    private SourceResourcePolicy policy(SourceResourcePolicy.Mode mode,SourceResourcePolicy.Order order){return new SourceResourcePolicy(mode,order,64,100,150,0,1,30000,new Random(7));}
    private SourceResourcePolicy.Facts facts(boolean gui,boolean tool,int stock,List<SourceResourcePolicy.Slot> in,List<SourceResourcePolicy.Slot> out,List<SourceResourcePolicy.Slot> packed,int others,long now){return new SourceResourcePolicy.Facts(true,gui,tool,false,true,false,false,false,stock,in,out,packed,others,List.of(),0,false,true,true,now);}
    @Test void startupHonorsOtherResourcesThenToolSelectionThenLookThenDissolve() {
        var p=policy(SourceResourcePolicy.Mode.BAZAAR,SourceResourcePolicy.Order.FIRST);
        assertEquals(SourceResourcePolicy.Action.SELL_OTHER,p.tick(facts(false,false,0,List.of(),List.of(),List.of(),6,0)).action());
        p=policy(SourceResourcePolicy.Mode.BAZAAR,SourceResourcePolicy.Order.FIRST);
        assertEquals(SourceResourcePolicy.Action.SELECT_TOOL,p.tick(new SourceResourcePolicy.Facts(true,false,false,false,false,true,false,false,0,List.of(),List.of(),List.of(),0,List.of(),0,false,true,true,0)).action());
        p=policy(SourceResourcePolicy.Mode.BAZAAR,SourceResourcePolicy.Order.FIRST);
        assertEquals(SourceResourcePolicy.Action.LOOK_AWAY,p.tick(new SourceResourcePolicy.Facts(true,false,false,false,true,true,false,false,0,List.of(),List.of(),List.of(),0,List.of(),0,false,true,true,0)).action());
        p=policy(SourceResourcePolicy.Mode.BAZAAR_AND_DISOLVE,SourceResourcePolicy.Order.FIRST);
        assertEquals(SourceResourcePolicy.Action.OPEN_INVENTORY,p.tick(facts(false,false,0,List.of(),List.of(),List.of(new SourceResourcePolicy.Slot(9,1)),0,0)).action());
    }
    @Test void toolForeignRemovalPrecedesInsertionAndBuyingUsesActualDeficit() {
        var p=policy(SourceResourcePolicy.Mode.BAZAAR,SourceResourcePolicy.Order.FIRST);var f=facts(true,true,7,List.of(new SourceResourcePolicy.Slot(40,64)),List.of(new SourceResourcePolicy.Slot(3,32)),List.of(),0,0);
        p.tick(f);p.tick(f);assertEquals(SourceResourcePolicy.Action.QUICK_MOVE_OUT,p.tick(f).action());
        p=policy(SourceResourcePolicy.Mode.BAZAAR,SourceResourcePolicy.Order.FIRST);f=facts(true,true,7,List.of(),List.of(),List.of(),0,0);p.tick(f);p.tick(f);var buy=p.tick(f);assertEquals(SourceResourcePolicy.Action.BUY,buy.action());assertEquals(57,buy.quantity());
    }
    @Test void sourceUnsupportedAcquisitionFlagsDoNotInventSuccessfulPurchases() {
        for(var mode:List.of(SourceResourcePolicy.Mode.INFINIDIRT,SourceResourcePolicy.Mode.MAD_REDSTONE_ENGINEER)){var p=policy(mode,SourceResourcePolicy.Order.FIRST);var f=facts(true,true,0,List.of(),List.of(),List.of(),0,0);p.tick(f);p.tick(f);assertEquals(SourceResourcePolicy.Action.STOP,p.tick(f).action());}
        for(var mode:List.of(SourceResourcePolicy.Mode.BAZAAR,SourceResourcePolicy.Mode.BUILDER,SourceResourcePolicy.Mode.WOOL_WEAVER,SourceResourcePolicy.Mode.BAZAAR_AND_DISOLVE)){var p=policy(mode,SourceResourcePolicy.Order.FIRST);var f=facts(true,true,0,List.of(),List.of(),List.of(),0,0);p.tick(f);p.tick(f);assertEquals(SourceResourcePolicy.Action.BUY,p.tick(f).action());}
    }
    @Test void firstLastRandomAndSeparatePerSlotCooldownFollowSourceChoice() {
        var slots=List.of(new SourceResourcePolicy.Slot(40,64),new SourceResourcePolicy.Slot(41,64));
        for(var order:SourceResourcePolicy.Order.values()){var p=policy(SourceResourcePolicy.Mode.BAZAAR,order);var f=facts(true,true,0,slots,List.of(),List.of(),0,0);p.tick(f);p.tick(f);var choice=p.tick(f);assertEquals(SourceResourcePolicy.Action.QUICK_MOVE_IN,choice.action());assertTrue(choice.slot()==40||choice.slot()==41);if(order==SourceResourcePolicy.Order.FIRST)assertEquals(40,choice.slot());if(order==SourceResourcePolicy.Order.LAST)assertEquals(41,choice.slot());}
        var p=policy(SourceResourcePolicy.Mode.BAZAAR,SourceResourcePolicy.Order.FIRST);var f=facts(true,true,0,slots,List.of(),List.of(),0,0);p.tick(f);p.tick(f);p.tick(f);assertEquals(SourceResourcePolicy.Action.NONE,p.tick(facts(true,true,0,slots,List.of(),List.of(),0,50)).action());assertEquals(SourceResourcePolicy.Action.QUICK_MOVE_IN,p.tick(facts(true,true,0,slots,List.of(),List.of(),0,100)).action());
    }
    @Test void randomizedOpenThresholdAndGenerationOwnedWiggleCallbacksResetOnlyTheirJob() {
        var p=policy(SourceResourcePolicy.Mode.BAZAAR,SourceResourcePolicy.Order.FIRST);SourceResourcePolicy.Decision recovery=null;int opens=0;
        for(int i=0;i<8;i++){var d=p.tick(facts(false,false,0,List.of(),List.of(),List.of(),0,i));if(d.action()==SourceResourcePolicy.Action.USE_TOOL)opens++;if(d.action()==SourceResourcePolicy.Action.RECOVER){recovery=d;break;}}
        assertNotNull(recovery);assertTrue(opens==3||opens==4);assertEquals(2000,recovery.quantity());long ticket=recovery.generation();
        assertFalse(p.recovered(ticket+1,true));assertEquals(SourceResourcePolicy.State.WIGGLE,p.state());assertTrue(p.recovered(ticket,true));assertFalse(p.recovered(ticket,true));assertEquals(SourceResourcePolicy.State.START,p.state());assertTrue(p.generation()>ticket);
        p.cancel();assertFalse(p.recovered(ticket,true));assertEquals(SourceResourcePolicy.State.BLOCKED,p.state());
    }
    @Test void wiggleScansOnlyActualSevenCubeStandingWitnessesAndChoosesNearestWithSourceTieOrder() {
        var origin=new BlockPos(0,70,0);BlockPos far=new BlockPos(3,70,0),near=new BlockPos(-2,70,0),tie=new BlockPos(2,70,0);
        var selected=SourceResourcePolicy.recoveryCandidates(origin,List.of(new SourceResourcePolicy.Candidate(far,3,true),new SourceResourcePolicy.Candidate(near,2,true),new SourceResourcePolicy.Candidate(tie,2,true),new SourceResourcePolicy.Candidate(new BlockPos(1,70,0),1,true),new SourceResourcePolicy.Candidate(new BlockPos(4,70,0),4,true),new SourceResourcePolicy.Candidate(new BlockPos(0,72,0),2,false)));
        assertEquals(List.of(near,tie,far),selected);
    }
    @Test void exactSourceCubeCenterAdmitsDiagonalAtOnePointFiveBeforeNominalTieSort() {
        var actor=new dev.veltraph.client.world.Vec3(.5,70,.5);var origin=new BlockPos(0,70,0);
        var facts=new ArrayList<SourceResourcePolicy.Candidate>();
        for(int x=-3;x<=3;x++)for(int y=-3;y<=3;y++)for(int z=-3;z<=3;z++) {
            var node=origin.add(x,y,z);
            facts.add(new SourceResourcePolicy.Candidate(node,SourceResourcePolicy.recoveryCenterDistance(node,actor),y==0));
        }
        var diagonal=new BlockPos(-1,70,-1);
        assertEquals(1.5,SourceResourcePolicy.recoveryCenterDistance(diagonal,actor));
        assertTrue(new dev.veltraph.client.world.Vec3(-.5,70,-.5).distanceTo(actor)<1.5);
        assertEquals(diagonal,SourceResourcePolicy.recoveryCandidates(origin,facts).getFirst());
        assertFalse(SourceResourcePolicy.recoveryCandidates(origin,facts).contains(new BlockPos(-1,70,0)));
    }
    @Test void randomCraftInputRequiresEmptyCellAndObservedCursorTransition() {
        var p=policy(SourceResourcePolicy.Mode.BAZAAR_AND_DISOLVE,SourceResourcePolicy.Order.FIRST);var packed=List.of(new SourceResourcePolicy.Slot(9,1));p.tick(facts(false,false,0,List.of(),List.of(),packed,0,0));
        var inv=new SourceResourcePolicy.Facts(true,true,false,true,true,false,false,false,-1,List.of(),List.of(),packed,0,List.of(1,2,3,4),0,false,true,true,1);p.tick(inv);assertEquals(SourceResourcePolicy.Action.PICK_PACKED,p.tick(inv).action());p.acknowledged(SourceResourcePolicy.Action.PICK_PACKED);
        var held=new SourceResourcePolicy.Facts(true,true,false,true,true,false,false,false,-1,List.of(),List.of(),List.of(),0,List.of(1,2,3,4),0,true,false,true,2);var put=p.tick(held);assertEquals(SourceResourcePolicy.Action.PUT_RANDOM_INPUT,put.action());assertTrue(put.slot()>=1&&put.slot()<=4);p.acknowledged(SourceResourcePolicy.Action.PUT_RANDOM_INPUT);
        var result=new SourceResourcePolicy.Facts(true,true,false,true,true,false,false,false,-1,List.of(),List.of(),List.of(),0,List.of(),9,false,true,true,3);assertEquals(SourceResourcePolicy.Action.QUICK_MOVE_RESULT,p.tick(result).action());p.acknowledged(SourceResourcePolicy.Action.QUICK_MOVE_RESULT);assertEquals(SourceResourcePolicy.State.CRAFT_ITEM,p.state());
    }
    @Test void contextLossForeignCraftGridAndDelayedCallbacksNeverAdvance() {
        var p=policy(SourceResourcePolicy.Mode.BAZAAR,SourceResourcePolicy.Order.FIRST);assertEquals(SourceResourcePolicy.Action.STOP,p.tick(new SourceResourcePolicy.Facts(false,false,false,false,true,false,false,false,64,List.of(),List.of(),List.of(),0,List.of(),0,false,true,true,0)).action());
        p=policy(SourceResourcePolicy.Mode.BAZAAR_AND_DISOLVE,SourceResourcePolicy.Order.FIRST);var packed=List.of(new SourceResourcePolicy.Slot(9,1));p.tick(facts(false,false,0,List.of(),List.of(),packed,0,0));assertEquals(SourceResourcePolicy.Action.STOP,p.tick(new SourceResourcePolicy.Facts(true,true,false,true,true,false,false,false,0,List.of(),List.of(),packed,0,List.of(1,2,3),0,false,true,false,1)).action());
    }
    @Test void actualEmptyResourceClearsForeignToolContentsThenClosesWithoutInventedStock(){var p=policy(SourceResourcePolicy.Mode.EMPTY,SourceResourcePolicy.Order.FIRST);var foreign=facts(true,true,0,List.of(),List.of(new SourceResourcePolicy.Slot(0,64)),List.of(),0,0);p.tick(foreign);p.tick(foreign);assertEquals(SourceResourcePolicy.Action.QUICK_MOVE_OUT,p.tick(foreign).action());var empty=facts(true,true,0,List.of(),List.of(),List.of(),0,200);p.tick(empty);p.tick(empty);assertEquals(SourceResourcePolicy.Action.CLOSE,p.tick(empty).action());assertEquals(SourceResourcePolicy.Action.COMPLETE,p.tick(facts(false,false,-1,List.of(),List.of(),List.of(),0,300)).action());}
    @Test void unknownNativeStorageCannotBeTreatedAsZeroAndTriggerPurchasing(){var p=policy(SourceResourcePolicy.Mode.BAZAAR,SourceResourcePolicy.Order.FIRST);var unknown=facts(true,true,-1,List.of(),List.of(),List.of(),0,0);p.tick(unknown);assertEquals(SourceResourcePolicy.Action.STOP,p.tick(unknown).action());assertTrue(p.status().contains("count unavailable"));}
}
