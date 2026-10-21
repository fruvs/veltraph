package dev.veltraph.client.foraging.supply;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class ForagingSupplyReplenishmentTest {
    final ForagingSupplyRuntime.Scope scope=new ForagingSupplyRuntime.Scope(new Object(),new Object(),new Object());
    final ForagingSupplyBindings.Resource resource=new ForagingSupplyBindings.Resource("minecraft:dark_oak_sapling","Dark Oak Sapling","{}",64,"supply","Offer","Saplings","{}",10,"Purse:");
    final ForagingSupplyBindings bindings=new ForagingSupplyBindings(1,new dev.veltraph.client.world.BlockPos(0,0,0),new dev.veltraph.client.world.BlockPos(1,0,0),"Chest",Map.of(ForagingSupplyConfig.Type.SAPLING,resource,ForagingSupplyConfig.Type.BONEMEAL,resource),10000);
    final class Port implements ForagingSupplyRuntime.Port {
        List<ForagingSupplyRuntime.Stack> inventory=new ArrayList<>(Collections.nCopies(36,ForagingSupplyRuntime.Stack.EMPTY));
        List<ForagingSupplyRuntime.Stack> chest=new ArrayList<>(Collections.nCopies(27,ForagingSupplyRuntime.Stack.EMPTY));Object menu;boolean ack;long purse=100;int withdrawals,purchases,stores,releases;
        ForagingSupplyRuntime.Observation read(long now){return new ForagingSupplyRuntime.Observation(scope,now,inventory,true,true,purse,menu,menu==null?"":"Chest",chest,true,ack);}
        ForagingSupplyRuntime.Stack stack(int n){return new ForagingSupplyRuntime.Stack(resource.id(),resource.name(),resource.components(),n,false);}
        public boolean open(ForagingSupplyRuntime.Observation o){menu=new Object();return true;}
        public boolean withdraw(ForagingSupplyRuntime.Observation o,int slot){withdrawals++;inventory.set(9,chest.get(slot));chest.set(slot,ForagingSupplyRuntime.Stack.EMPTY);return true;}
        public boolean verifyStorage(ForagingSupplyRuntime.Observation o){menu=new Object();ack=true;return true;}
        public boolean store(ForagingSupplyRuntime.Observation o,int slot){stores++;return false;}
        public boolean acquire(ForagingSupplyRuntime.Observation o,ForagingSupplyBindings.Resource r){purchases++;inventory.set(9,stack(64));purse=90;ack=true;return true;}
        public void release(){releases++;menu=null;}
    }
    @Test void nativeAcknowledgedWithdrawalRetainsSupplyAndNeverStores(){var p=new Port();p.chest.set(2,p.stack(64));var r=new ForagingSupplyRuntime();assertTrue(r.startReplenishment(p.read(0),bindings,ForagingSupplyConfig.Type.SAPLING,4));for(int i=1;i<20&&!r.terminal();i++)r.tick(p.read(i),p);assertEquals(ForagingSupplyRuntime.Stage.COMPLETE,r.stage());assertEquals(64,r.acknowledgedInventory().get(9).count());assertEquals(1,p.withdrawals);assertEquals(0,p.purchases);assertEquals(0,p.stores);assertEquals(0,r.acquiredCost());}
    @Test void emptyChestUsesExistingPurchaseAcknowledgementAndRetainsResources(){var p=new Port();var r=new ForagingSupplyRuntime();assertTrue(r.startReplenishment(p.read(0),bindings,ForagingSupplyConfig.Type.SAPLING,4));for(int i=1;i<20&&!r.terminal();i++)r.tick(p.read(i),p);assertEquals(ForagingSupplyRuntime.Stage.COMPLETE,r.stage());assertEquals(64,r.acknowledgedInventory().get(9).count());assertEquals(1,p.purchases);assertEquals(0,p.stores);assertEquals(10,r.acquiredCost());}
    @Test void missingFreshWithdrawalReceiptCannotComplete(){var p=new Port();p.chest.set(2,p.stack(64));var r=new ForagingSupplyRuntime();r.startReplenishment(p.read(0),bindings,ForagingSupplyConfig.Type.SAPLING,4);for(int i=1;i<8;i++){r.tick(p.read(i),p);p.ack=false;}assertFalse(r.terminal());r.tick(p.read(10000),p);assertEquals(ForagingSupplyRuntime.Stage.UNKNOWN,r.stage());assertEquals(0,p.stores);}
    @Test void foreignInventoryDeltaStopsWithoutLaterPurchase(){var p=new Port();p.chest.set(2,p.stack(64));var r=new ForagingSupplyRuntime();r.startReplenishment(p.read(0),bindings,ForagingSupplyConfig.Type.SAPLING,4);for(int i=1;i<=3;i++)r.tick(p.read(i),p);p.inventory.set(20,new ForagingSupplyRuntime.Stack("minecraft:diamond","Diamond","{}",2,false));r.tick(p.read(4),p);assertEquals(ForagingSupplyRuntime.Stage.UNKNOWN,r.stage());assertEquals(0,p.purchases);assertEquals(0,p.stores);}
}
