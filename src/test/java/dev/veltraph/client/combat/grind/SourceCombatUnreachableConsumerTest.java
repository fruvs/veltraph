package dev.veltraph.client.combat.grind;

import dev.veltraph.client.combat.ghost.GhostSourceOwnedParentArea;
import dev.veltraph.client.combat.ghost.GhostSourcePathJobTransaction;
import dev.veltraph.client.combat.ghost.GhostSourceUnreachableActorLedger;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SourceCombatUnreachableConsumerTest {
    private static void fail(GhostSourceUnreachableActorLedger ledger,Object body,long now) {
        var scope=new GhostSourcePathJobTransaction.Scope(new Object(),new Object(),
                new GhostSourceOwnedParentArea.Stamp(new Object(),1,2,new Object(),new Object(),new Object()));
        var token=ledger.issue(scope,List.of(body));
        assertEquals(1,ledger.recordReadyNoPath(token,scope,GhostSourceUnreachableActorLedger.NativeOutcome.READY_NO_PATH,
                List.of(new GhostSourceUnreachableActorLedger.Failure(body,GhostSourceUnreachableActorLedger.SourceFailurePredicate.WRITE_LEDGER)),now).written());
    }
    @Test void eachMappedFamilyReadsTheExistingWriterLedger() {
        var ledger=new GhostSourceUnreachableActorLedger();var failed=new Object();var other=new Object();
        fail(ledger,failed,100);
        for(var declaration:SourceCombatUnreachableConsumer.Declaration.values()) {
            assertFalse(SourceCombatUnreachableConsumer.accepts(declaration,failed,ledger));
            assertTrue(SourceCombatUnreachableConsumer.accepts(declaration,other,ledger));
        }
    }
    @Test void actorReplacementWithSameUuidAndValueNeverInheritsMembership() {
        record Body(UUID uuid,int id) {}
        var failed=new Body(UUID.randomUUID(),71);var replacement=new Body(failed.uuid(),failed.id());
        assertEquals(failed,replacement);var ledger=new GhostSourceUnreachableActorLedger();fail(ledger,failed,0);
        assertFalse(SourceCombatUnreachableConsumer.accepts(SourceCombatUnreachableConsumer.Declaration.GRAVEYARD_CHILD,failed,ledger));
        assertTrue(SourceCombatUnreachableConsumer.accepts(SourceCombatUnreachableConsumer.Declaration.GRAVEYARD_CHILD,replacement,ledger));
    }
    @Test void readerHasNoExpiryOrClearAndOnlyNonemptyPathWrapperCanReleaseIt() {
        var ledger=new GhostSourceUnreachableActorLedger();var failed=new Object();fail(ledger,failed,100);
        var declaration=SourceCombatUnreachableConsumer.Declaration.BESTIARY_GRAVEYARD_CHILD;
        for(int i=0;i<20;i++)assertFalse(SourceCombatUnreachableConsumer.accepts(declaration,failed,ledger));
        assertEquals(0,ledger.cleanupAtNonemptyWrapperInvocation(0,999999));
        assertFalse(SourceCombatUnreachableConsumer.accepts(declaration,failed,ledger));
        assertEquals(0,ledger.cleanupAtNonemptyWrapperInvocation(1,30100));
        assertFalse(SourceCombatUnreachableConsumer.accepts(declaration,failed,ledger));
        assertEquals(1,ledger.cleanupAtNonemptyWrapperInvocation(1,30101));
        assertTrue(SourceCombatUnreachableConsumer.accepts(declaration,failed,ledger));
    }
    @Test void unboundFamiliesDoNotReadOrCleanTheSharedLedger() {
        var ledger=new GhostSourceUnreachableActorLedger();var failed=new Object();fail(ledger,failed,0);
        assertTrue(SourceCombatUnreachableConsumer.accepts(null,failed,ledger));
        assertTrue(SourceCombatUnreachableConsumer.accepts(null,failed,null));
        assertTrue(ledger.containsIdentity(failed));assertFalse(SourceCombatUnreachableConsumer.accepts(null,null,ledger));
    }
    @Test void onlyExactMappedSlayerDeclarationsSelectTheReader() {
        assertEquals(SourceCombatUnreachableConsumer.Declaration.REVENANT_SLAYER_GRAVEYARD_CHILD,SourceCombatUnreachableConsumer.declaredSlayerChild("Revenant Horror"));
        assertEquals(SourceCombatUnreachableConsumer.Declaration.TARANTULA_SLAYER_GRAVEYARD_CHILD,SourceCombatUnreachableConsumer.declaredSlayerChild("Tarantula Broodfather"));
        for(String label:List.of("Sven Packmaster","Voidgloom Seraph","Inferno Demonlord","Riftstalker Bloodfiend","Revenant Horror II","Zombie",""))
            assertNull(SourceCombatUnreachableConsumer.declaredSlayerChild(label));
        assertNull(SourceCombatUnreachableConsumer.declaredSlayerChild(null));
    }
}
