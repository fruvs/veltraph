package dev.veltraph.client.rotation;

import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

/** Production history primitive only. Model tokens below supply no native source, key receipt, or input permission. */
class RollbackNineKeyNegativePrefixHistoryTest {
    @Test void everyFailedCutKeepsUnissuedKeysOriginalAndAllowsOnlyItsExactReturnedPrefix() {
        for (int cut=0;cut<9;cut++) {
            var keys=keys();var original=snapshots(keys);var retained=original.clone();
            for (int i=0;i<=cut;i++) {
                Object realModelReturn=new Object();keys[i].begin(realModelReturn,true);keys[i].end(false);
                retained[i]=keys[i].snapshot();assertTrue(keys[i].advancedOnly(original[i],retained[i],List.of(realModelReturn)));
            }
            for (int i=0;i<9;i++) assertTrue(keys[i].current(retained[i]),"cut="+cut+" key="+i);
            for (int i=cut+1;i<9;i++) assertSame(original[i],retained[i]);
            for (int i=0;i<=cut;i++) {
                var old=retained[i];Object negativeReturn=new Object();keys[i].begin(negativeReturn,true);keys[i].end(false);
                var released=keys[i].snapshot();assertTrue(keys[i].advancedOnly(old,released,List.of(negativeReturn)));
                assertFalse(keys[i].advancedOnly(old,released,List.of(new Object())));
                retained[i]=released;
                for (int j=0;j<9;j++) assertTrue(keys[j].current(retained[j]),"OTHER8 cut="+cut+" key="+j);
            }
        }
    }
    @Test void unscopedChangeAndRevertAtEveryKeyCannotBeEnrolledIntoAnOldPrefix() {
        for (int selected=0;selected<9;selected++) {
            var keys=keys();var original=snapshots(keys);
            keys[selected].begin(null,true);keys[selected].end(false);
            keys[selected].begin(null,true);keys[selected].end(false);
            Object returned=new Object();keys[selected].begin(returned,true);keys[selected].end(false);
            assertFalse(keys[selected].current(original[selected]));
            assertFalse(keys[selected].advancedOnly(original[selected],keys[selected].snapshot(),List.of(returned)));
            for(int i=0;i<9;i++)if(i!=selected)assertTrue(keys[i].current(original[i]));
        }
    }
    @Test void foreignOtherKeyInvalidatesEachCutWithoutChangingTheSelectedOldProof() {
        for (int cut=0;cut<9;cut++) {
            var keys=keys();var old=snapshots(keys);int foreign=(cut+1)%9;
            keys[foreign].begin(null,true);keys[foreign].end(false);
            assertFalse(keys[foreign].current(old[foreign]));assertTrue(keys[cut].current(old[cut]));
            Object selected=new Object();keys[cut].begin(selected,true);
            assertTrue(keys[cut].inFlightOnly(old[cut],List.of(),selected,1));
            assertFalse(keys[foreign].current(old[foreign]));keys[cut].end(false);
        }
    }
    @Test void exactToggleSuperclassReturnIsOneEventWhileAdditionalNestedCallbackIsRefusedAtEveryKey() {
        for (int i=0;i<9;i++) {
            var version=new NormalIdleInputVersion();var old=version.snapshot();Object selected=new Object();
            version.begin(selected,true);version.begin(null,true);
            assertTrue(version.inFlightOnly(old,List.of(),selected,2));
            version.end(true);version.end(true);
            assertTrue(version.advancedOnly(old,version.snapshot(),List.of(selected)));
            var bad=new NormalIdleInputVersion();var before=bad.snapshot();
            bad.begin(selected,true);bad.begin(null,true);bad.begin(null,true);
            assertFalse(bad.inFlightOnly(before,List.of(),selected,2));
        }
    }
    private static NormalIdleInputVersion[] keys(){var values=new NormalIdleInputVersion[9];for(int i=0;i<9;i++)values[i]=new NormalIdleInputVersion();return values;}
    private static NormalIdleInputVersion.Snapshot[] snapshots(NormalIdleInputVersion[] keys){var values=new NormalIdleInputVersion.Snapshot[9];for(int i=0;i<9;i++)values[i]=keys[i].snapshot();return values;}
}
