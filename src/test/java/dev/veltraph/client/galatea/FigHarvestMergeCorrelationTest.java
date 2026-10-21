package dev.veltraph.client.galatea;
import dev.veltraph.client.world.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static dev.veltraph.client.galatea.FigHarvestPickupLedger.*;
import static dev.veltraph.client.galatea.FigHarvestMergeCorrelation.*;

/** STAGED uncompiled/unrun. Synthetic records test the pure contract, not native ancestry. */
class FigHarvestMergeCorrelationTest {
    private final Scope scope=new Scope(new Object(),new Object(),new Object(),new Object(),1,new Object(),2);
    private final UUID destination=UUID.randomUUID(),donorA=UUID.randomUUID(),donorB=UUID.randomUUID();
    private Carrier carrier(UUID id,int number,int count,Object unit,long ack,boolean tainted,boolean spent){return new Carrier(scope,id,number,
            new Break(number,new BlockPos(number,70,0),10,ack,20,"original native state"),new Vec3(number+.3,70.2,.4),30+number,30,40+number,40,unit,count,tainted,spent);}
    private Carrier destination(){return carrier(destination,1,1,"full unit",21,false,false);}
    private Metadata meta(int amount,Object unit,long at){return new Metadata(destination,100,at,unit,1,amount,64);}
    private Donor donor(UUID id,int number,int count,long removalAt,boolean take){var c=carrier(id,number,count,"full unit",20+number,false,false);return new Donor(c,new Removal(id,number,80+number,removalAt,true,take));}
    private Optional<Proof> proof(Metadata m,Donor... donors){return correlate(destination(),m,List.of(donors),scope,1000);}
    @Test void uniqueOwnedWholeDonorAndMetadataIncreaseProduceTypedProof(){var donor=donor(donorA,2,1,60,false);var p=proof(meta(2,"full unit",70),donor).orElseThrow();assertEquals(List.of(donor),p.donors());assertEquals(2,p.metadata().afterCount());}
    @Test void removalBeforeOrAfterMetadataDoesNotInventTransportOrder(){assertTrue(proof(meta(2,"full unit",70),donor(donorA,2,1,60,false)).isPresent());assertTrue(proof(meta(2,"full unit",50),donor(donorA,2,1,60,false)).isPresent());}
    @Test void twoMatchingDonorsForOneUnitAreAmbiguous(){assertTrue(proof(meta(2,"full unit",70),donor(donorA,2,1,60,false),donor(donorB,3,1,61,false)).isEmpty());}
    @Test void exactTwoDonorMassCanCloseOneLargerMetadataIncrease(){assertEquals(2,proof(meta(3,"full unit",70),donor(donorA,2,1,60,false),donor(donorB,3,1,61,false)).orElseThrow().donors().size());}
    @Test void noAuthoritativeBreakAcknowledgmentDeniesDonor(){var c=carrier(donorA,2,1,"full unit",0,false,false);assertTrue(proof(meta(2,"full unit",70),new Donor(c,new Removal(donorA,2,80,60,true,false))).isEmpty());}
    @Test void foreignOrLocalTakeBeforeRemovalDeniesDonation(){assertTrue(proof(meta(2,"full unit",70),donor(donorA,2,1,60,true)).isEmpty());}
    @Test void reusedOrTaintedQuantityCannotDonateAgain(){for(boolean tainted:List.of(false,true)){var c=carrier(donorA,2,1,"full unit",22,tainted,!tainted);assertTrue(proof(meta(2,"full unit",70),new Donor(c,new Removal(donorA,2,80,60,true,false))).isEmpty());}}
    @Test void changedCanonicalComponentIdentityCannotExplainAnIncrease(){assertTrue(proof(meta(2,"changed components",70),donor(donorA,2,1,60,false)).isEmpty());}
    @Test void CountDecreaseOrEqualReplayNeverBecomesMergeProof(){assertTrue(proof(meta(1,"full unit",70),donor(donorA,2,1,60,false)).isEmpty());assertTrue(proof(meta(0,"full unit",70),donor(donorA,2,1,60,false)).isEmpty());}
    @Test void mismatchedMassAndPartialDonorConsumptionDeny(){assertTrue(proof(meta(3,"full unit",70),donor(donorA,2,1,60,false)).isEmpty());assertTrue(proof(meta(2,"full unit",70),donor(donorA,2,2,60,false)).isEmpty());}
    @Test void MissingActualRemovalCannotBeInferredFromDisappearance(){var c=carrier(donorA,2,1,"full unit",22,false,false);assertTrue(proof(meta(2,"full unit",70),new Donor(c,null)).isEmpty());assertTrue(proof(meta(2,"full unit",70),new Donor(c,new Removal(donorA,2,80,60,false,false))).isEmpty());}
    @Test void originalDeadlineAndScopeCannotBeRebound(){assertTrue(proof(meta(2,"full unit",1001),donor(donorA,2,1,60,false)).isEmpty());var other=new Scope(scope.owner(),scope.player(),scope.world(),scope.connection(),2,scope.treeIdentity(),scope.treeGeneration());assertTrue(correlate(destination(),meta(2,"full unit",70),List.of(donor(donorA,2,1,60,false)),other,1000).isEmpty());}
    @Test void nativeRecoveredMergeCapCannotBeBumped(){assertTrue(proof(meta(65,"full unit",70),donor(donorA,2,64,60,false)).isEmpty());}
    @Test void proofCommitRequiresOriginalCustodyAndCannotReplay(){var p=proof(meta(2,"full unit",70),donor(donorA,2,1,60,false)).orElseThrow();assertFalse(p.commit(scope,()->false));assertTrue(p.commit(scope,()->true));assertFalse(p.commit(scope,()->true));}
    @Test void donorEvidenceRemainsQueryableAndImmutable(){var p=proof(meta(2,"full unit",70),donor(donorA,2,1,60,false)).orElseThrow();assertTrue(p.donors().getFirst().removal().actuallyAbsent());assertThrows(UnsupportedOperationException.class,()->p.donors().clear());}
}
