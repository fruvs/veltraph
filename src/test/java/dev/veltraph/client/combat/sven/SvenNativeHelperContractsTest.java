package dev.veltraph.client.combat.sven;

import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SvenNativeHelperContractsTest {
    @Test void missingAmbiguousNegativeAndMalformedQuotesCannotAuthorizePurchase(){
        assertTrue(SvenPurchaseQuote.price(List.of("Price: 999 coins"),"Verified price:").isEmpty());
        assertTrue(SvenPurchaseQuote.price(List.of("Price: 5 coins","Price: 10 coins"),"Price:").isEmpty());
        assertTrue(SvenPurchaseQuote.price(List.of("Price: -5 coins"),"Price:").isEmpty());
        assertTrue(SvenPurchaseQuote.price(List.of("Price: 9999999999999999999999999999 coins"),"Price:").isEmpty());
        assertEquals(1001,SvenPurchaseQuote.price(List.of("§aPrice: 1,000.01 coins"),"Price:").orElseThrow());
    }
    SvenBindingProfile.Buff buff(){return new SvenBindingProfile.Buff("verified:menu","Verified item","Verified SkyBlock","Verified Cookie","Verified cookie menu","Verified potion button","Verified potion menu",List.of("Verified Jump Boost"),"Verified disabled","Verified next","", "Effect status:.*", "Effect status:", ";", "inactive", "Verified effect", "Details", "Inactive:");}
    @Test void sourceBuffFooterAndOrderedNeighborGrammarAreObservedRatherThanConfiguredReadiness(){
        var predicate=new SvenBuffSignalPredicate();
        assertFalse(predicate.observe("Effect status: inactive;",List.of("Verified effect Details","Inactive: missing"),buff()));
        assertTrue(predicate.observe("Effect status: active;",List.of(),buff()));
        assertTrue(predicate.observe("unrelated footer",List.of(),buff())); // literal cached flag behavior
        predicate.reset();assertFalse(predicate.observe("",List.of("Verified effect Details","Inactive: no potion"),buff()));
        assertTrue(predicate.observe("",List.of("Verified effect Details","Verified active row"),buff()));
    }
    @Test void malformedMatchingBuffFooterCannotClaimActive(){
        var predicate=new SvenBuffSignalPredicate();assertFalse(predicate.observe("Effect status: active without end",List.of(),buff()));
    }
    List<SvenInventoryPlan.Slot> full(){return java.util.stream.IntStream.range(36,44).mapToObj(i->new SvenInventoryPlan.Slot(i,1,true,true,"diamond_sword")).toList();}
    @Test void sourceProtectsActualTagOrRegistryPathAndSelectsOnlyUnprotectedSlots(){
        var slots=new ArrayList<>(full());slots.set(4,new SvenInventoryPlan.Slot(40,12,true,false,"paper"));
        var decision=SvenInventoryPlan.choose(slots,"sword",new Random(1));assertEquals(SvenInventoryPlan.Kind.QUICK_MOVE,decision.kind());assertEquals(40,decision.slot());
        assertEquals(SvenInventoryPlan.Kind.FAILED,SvenInventoryPlan.choose(full(),"sword",new Random()).kind());
    }
    @Test void indexFortyFourIsExcludedAndAnEmptySlotWithinLiteralSourceRangeCompletes(){
        var slots=new ArrayList<>(full());slots.add(new SvenInventoryPlan.Slot(44,0,true,false,"air"));
        assertEquals(SvenInventoryPlan.Kind.FAILED,SvenInventoryPlan.choose(slots,"sword",new Random()).kind());
        slots.set(0,new SvenInventoryPlan.Slot(36,0,true,false,"air"));assertEquals(SvenInventoryPlan.Kind.READY,SvenInventoryPlan.choose(slots,"sword",new Random()).kind());
    }
    @Test void foreignSlotTopologyCannotAuthorizeInventoryMutation(){
        var slots=new ArrayList<>(full());slots.set(0,new SvenInventoryPlan.Slot(36,12,false,false,"paper"));
        assertEquals(SvenInventoryPlan.Kind.FAILED,SvenInventoryPlan.choose(slots,"sword",new Random()).kind());
    }
}
