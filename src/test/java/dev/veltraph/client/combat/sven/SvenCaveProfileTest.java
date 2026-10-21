package dev.veltraph.client.combat.sven;

import dev.veltraph.client.world.BlockPos;
import dev.veltraph.client.world.Vec3;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SvenCaveProfileTest {
    @Test void profilesRetainAllSourcePositions(){
        var p=SvenCaveProfile.DARK_OAK;assertEquals(new BlockPos(-390,97,-63),p.pathTarget());assertEquals(new BlockPos(-392,98,-63),p.gate());assertEquals(new BlockPos(-386,85,-83),p.storedPosition());
        p=SvenCaveProfile.ACACIA;assertEquals(new BlockPos(-436,107,46),p.pathTarget());assertEquals(new BlockPos(-436,108,48),p.gate());assertEquals(new BlockPos(-426,98,47),p.storedPosition());
        p=SvenCaveProfile.JUNGLE;assertEquals(new BlockPos(-461,119,-81),p.pathTarget());assertEquals(new BlockPos(-464,120,-81),p.gate());assertEquals(new BlockPos(-452,108,-91),p.storedPosition());
        assertEquals(new Vec3(-266,79,-18),SvenCaveProfile.PARK_SPAWN);
    }
    @Test void combatBoundsNormalizeActualReversedEndpoints(){
        assertBounds(SvenCaveProfile.DARK_OAK,new BlockPos(-410,90,-35),new BlockPos(-360,72,-92));
        assertBounds(SvenCaveProfile.ACACIA,new BlockPos(-400,105,-10),new BlockPos(-455,85,60));
        assertBounds(SvenCaveProfile.JUNGLE,new BlockPos(-435,112,-105),new BlockPos(-487,90,-30));
        for(var profile:SvenCaveProfile.values())for(var waypoint:profile.combatWaypoints())assertTrue(profile.contains(new Vec3(waypoint.x(),waypoint.y(),waypoint.z())));
        assertFalse(SvenCaveProfile.DARK_OAK.contains(new Vec3(-410.01,81,-72)));
    }
    static void assertBounds(SvenCaveProfile p,BlockPos first,BlockPos second){assertEquals(first,p.firstBound());assertEquals(second,p.secondBound());assertTrue(p.contains(new Vec3(first.x(),first.y(),first.z())));assertTrue(p.contains(new Vec3(second.x(),second.y(),second.z())));}
    @Test void waypointsAreActualProfileListsAndImmutable(){
        assertEquals(List.of(new BlockPos(-385,81,-72),new BlockPos(-396,82,-51),new BlockPos(-373,76,-78),new BlockPos(-372,84,-60)),SvenCaveProfile.DARK_OAK.combatWaypoints());
        assertEquals(List.of(new BlockPos(-426,98,46),new BlockPos(-410,97,14),new BlockPos(-442,94,16),new BlockPos(-409,92,39)),SvenCaveProfile.ACACIA.combatWaypoints());
        assertEquals(List.of(new BlockPos(-451,108,-90),new BlockPos(-474,104,-42),new BlockPos(-460,97,-81)),SvenCaveProfile.JUNGLE.combatWaypoints());
        assertThrows(UnsupportedOperationException.class,()->SvenCaveProfile.DARK_OAK.combatWaypoints().clear());
    }
    @Test void allUnrecoveredIdentityDefaultsAreBlank(){
        assertTrue(SvenConfig.SVEN_SOURCE_BINDING_PROFILE.defaultValue().isBlank());assertTrue(SvenConfig.SVEN_RESOURCE_NAME.defaultValue().isBlank());assertTrue(SvenConfig.SVEN_RESOURCE_ALTERNATIVE_NAME.defaultValue().isBlank());
        assertTrue(SvenConfig.SVEN_PURCHASE_RECEIPT_PREFIX.defaultValue().isBlank());assertTrue(SvenConfig.SVEN_PURCHASE_RECEIPT_ITEM.defaultValue().isBlank());
        assertFalse(SvenConfig.SVEN_AUTO_SLAYER.defaultValue());assertEquals(17,SvenIngressController.State.values().length);
    }
}
