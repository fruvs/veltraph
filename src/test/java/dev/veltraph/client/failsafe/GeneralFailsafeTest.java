package dev.veltraph.client.failsafe;
import dev.veltraph.client.config.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
class GeneralFailsafeTest {
    @Test void filtersAndLatchesNearbyPlayersAndHonorsStopReturnPolicy() {
        var config=new MapConfigService();var calls=new ArrayList<String>();
        var runtime=new GeneralFailsafe(config,new GeneralFailsafe.Actions() {
            public void alert(String reason) { calls.add("alert"); }public void stop() { calls.add("stop"); }public void returnToLocation() { calls.add("return"); }
        });
        config.set(FailsafeConfig.FAILSAFE_RAYTRACED_ONLY,true);
        config.set(FailsafeConfig.FAILSAFE_REACTION_POLICY,FailsafeReactionPolicy.STOP_AND_RETURN_TO_LOCATION);
        runtime.tick(true,false,List.of(new GeneralFailsafe.Player(2,false,true,true,false)));
        assertTrue(calls.isEmpty());
        var nearby=List.of(new GeneralFailsafe.Player(2,false,true,true,true));
        runtime.tick(true,false,nearby);runtime.tick(true,false,nearby);
        assertEquals(List.of("alert","stop","return"),calls);
        runtime.tick(false,false,nearby);assertEquals(3,calls.size());
    }
    @Test void alertOnlyAndMentionNameBoundaries() {
        var config=new MapConfigService();var calls=new ArrayList<String>();
        config.set(FailsafeConfig.FAILSAFE_REACTION_POLICY,FailsafeReactionPolicy.DO_NOTHING);
        var runtime=new GeneralFailsafe(config,new GeneralFailsafe.Actions() {
            public void alert(String reason) { calls.add("alert"); }public void stop() { fail("must not stop"); }public void returnToLocation() { fail("must not return"); }
        });
        runtime.mention(false,"Max","@Max");runtime.mention(true,"Max","@Maximum");assertTrue(calls.isEmpty());
        runtime.mention(true,"Max","hello @Max!");assertEquals(List.of("alert"),calls);
    }
}
