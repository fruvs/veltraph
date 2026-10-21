package dev.veltraph.client.combat.ghost;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.phys.Vec3;

/** Current native body classification with exact source grace order and actual ClientLevel ticking-list membership. */
public final class McGhostSourceBodyEligibilitySnapshot {
    private final Minecraft client;private final LivingEntity target;private final Object player,world,connection;
    private final int tick;private final Vec3 position;private final boolean alive,ticking,dead,powered,graceReady,suppressed;
    private final float health;
    private final GhostSourceEntityGraceWindow.Stamp graceStamp;private final GhostSourcePathFailureSuppression.Stamp suppressionStamp;
    private McGhostSourceBodyEligibilitySnapshot(Minecraft client,LivingEntity target,GhostSourceEntityGraceWindow grace,
            GhostSourcePathFailureSuppression suppression,long observedNow){
        this.client=client;this.target=target;
        player=client.player;world=client.level;connection=client.getConnection();tick=client.player.tickCount;position=target.position();
        graceReady=grace.ready(target,observedNow);graceStamp=grace.capture(target);suppressionStamp=suppression.capture(target.getUUID());alive=target.isAlive();ticking=client.level.isTickingEntity(target);
        dead=target.isDeadOrDying();health=target.getHealth();powered=target instanceof Creeper c&&c.isPowered();
        suppressed=suppression.suppressed(target.getUUID(),observedNow);
    }
    public static McGhostSourceBodyEligibilitySnapshot observe(Minecraft client,LivingEntity target,GhostSourceEntityGraceWindow grace,
            GhostSourcePathFailureSuppression suppression){
        if(client==null||!client.isSameThread()||client.player==null||client.level==null||client.getConnection()==null||target==null
                ||grace==null||suppression==null||target.level()!=client.level||client.level.getEntity(target.getId())!=target)
            throw new IllegalStateException("Actual current native eligibility/PWC/ledgers required");
        return new McGhostSourceBodyEligibilitySnapshot(client,target,grace,suppression,System.currentTimeMillis());
    }
    public boolean current(){if(!client.isSameThread()||client.player!=player||client.level!=world||client.getConnection()!=connection)return false;
        long currentNow=System.currentTimeMillis();return graceStamp.current()&&suppressionStamp.current()
            &&graceStamp.readyAt(currentNow)==graceReady&&suppressionStamp.suppressedAt(currentNow)==suppressed
            &&client.player.tickCount==tick&&target.level()==client.level&&client.level.getEntity(target.getId())==target&&target.position().equals(position)
            &&target.isAlive()==alive&&client.level.isTickingEntity(target)==ticking&&target.isDeadOrDying()==dead
            &&Float.floatToIntBits(target.getHealth())==Float.floatToIntBits(health)
            &&(target instanceof Creeper c&&c.isPowered())==powered;}
    public boolean sourceEligible(){requireCurrent();boolean result=powered&&!suppressed&&GhostSourceBodyEligibility.eligible(true,graceReady,alive,ticking,dead);requireCurrent();return result;}
    public boolean defensiveBodySafe(){requireCurrent();boolean result=GhostSourceBodyEligibility.defensiveBodySafe(alive,ticking,dead);requireCurrent();return result;}
    private void requireCurrent(){if(!current())throw new IllegalStateException("Current native body/UUID suppression frame changed");}
}
