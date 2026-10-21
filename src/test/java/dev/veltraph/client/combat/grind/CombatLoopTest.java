package dev.veltraph.client.combat.grind;
import dev.veltraph.client.world.Vec3;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
class CombatLoopTest {
    @org.junit.jupiter.api.Test void delayedMechanicRetainsTheSelectedBossWithoutCountingAnAttackOrKill() {
        var loop=new CombatLoop();var p=new dev.veltraph.client.world.Vec3(0,70,0);
        var first=new CombatTarget(1,"Inferno Demonlord",new dev.veltraph.client.world.Vec3(4,70,0),true,false);
        var other=new CombatTarget(2,"Inferno Demonlord",new dev.veltraph.client.world.Vec3(2,70,0),true,false);
        loop.tick(java.util.List.of(first),p,"Inferno Demonlord",3,16,false,false,0,1000);
        for(int tick=1;tick<=12;tick++) {
            loop.preserveTarget(1,tick*900L);
            var decision=loop.tick(java.util.List.of(first,other),p,"Inferno Demonlord",3,16,false,false,tick*900L,1000);
            org.junit.jupiter.api.Assertions.assertEquals(1,decision.target().id());
            org.junit.jupiter.api.Assertions.assertNotEquals(CombatLoop.Action.ATTACK,decision.action());
            org.junit.jupiter.api.Assertions.assertEquals(0,loop.kills());
        }
        loop.preserveTarget(2,20000); // a stale/unrelated objective cannot renew this lease
        var ordinary=loop.tick(java.util.List.of(first,other),p,"Inferno Demonlord",3,16,false,false,20000,1000);
        org.junit.jupiter.api.Assertions.assertEquals(2,ordinary.target().id());
    }
    final Vec3 player=new Vec3(0,1,0);
    CombatTarget target(int id,String name,double distance,boolean alive,boolean protectedTarget) {
        return new CombatTarget(id,name,new Vec3(distance,1,0),alive,protectedTarget);
    }
    @Test void rejectsProtectedNonmatchingAndOutOfRangeMobs() {
        var loop=new CombatLoop();
        var out=loop.tick(List.of(target(1,"Zealot",1,true,true),target(2,"Cow",1,true,false),target(3,"Zealot",30,true,false)),player,"Zealot",3,16,true,true,1000,5000);
        assertNull(out.target());assertEquals(CombatLoop.Action.WAIT,out.action());
    }
    @Test void aimIsRequiredForNewTargetAndAttacksAreThrottled() {
        var loop=new CombatLoop(); var targets=List.of(target(1,"[Lv55] Zealot",2,true,false));
        assertEquals(CombatLoop.Action.AIM,loop.tick(targets,player,"Zealot",3,16,true,true,1000,5000).action());
        assertEquals(CombatLoop.Action.ATTACK,loop.tick(targets,player,"Zealot",3,16,true,true,1100,5000).action());
        assertEquals(CombatLoop.Action.AIM,loop.tick(targets,player,"Zealot",3,16,true,true,1150,5000).action());
        assertEquals(CombatLoop.Action.AIM,loop.tick(targets,player,"Zealot",3,16,true,false,1300,5000).action());
    }
    @Test void timesOutAndBlacklistsWithoutCountingUnloadAsKill() {
        var loop=new CombatLoop();var targets=List.of(target(1,"Ghost",8,true,false));
        assertEquals(CombatLoop.Action.WALK,loop.tick(targets,player,"Ghost",3,16,false,false,0,1000).action());
        assertNull(loop.tick(targets,player,"Ghost",3,16,false,false,1001,1000).target());
        assertEquals(0,loop.kills());
        assertNotNull(loop.tick(targets,player,"Ghost",3,16,false,false,12000,1000).target());
        loop.tick(List.of(),player,"Ghost",3,16,false,false,12001,1000); assertEquals(0,loop.kills());
    }
    @Test void countsOnlyObservedDeathsAndDoesNotMatchSubstrings() {
        var loop=new CombatLoop();loop.tick(List.of(target(1,"Ghost",1,true,false)),player,"Ghost",3,16,false,false,1000,5000);
        loop.tick(List.of(target(1,"Ghost",1,false,false)),player,"Ghost",3,16,false,false,1100,5000);
        assertEquals(1,loop.kills());
        assertFalse(CombatLoop.matches("Ghostly Knight","Ghost"));
        assertTrue(CombatLoop.matches("§c[Lv250] Ghost 1M❤","Ghost"));
        assertFalse(CombatLoop.matches("Anything",", ,"));
    }
    @Test void ownedRebindClearsOldNativeTargetsButPreservesConfirmedProgression(){
        var loop=new CombatLoop();loop.tick(List.of(target(1,"Ghost",1,true,false)),player,"Ghost",3,16,false,false,1000,5000);
        loop.tick(List.of(target(1,"Ghost",1,false,false)),player,"Ghost",3,16,false,false,1100,5000);
        assertEquals(1,loop.kills());loop.rebind(50000);assertEquals(1,loop.kills());assertEquals(-1,loop.targetId());
        assertEquals(CombatLoop.Action.AIM,loop.tick(List.of(target(1,"Ghost",1,true,false)),player,"Ghost",3,16,true,true,50100,5000).action());
        assertEquals(1,loop.kills());
    }

}
