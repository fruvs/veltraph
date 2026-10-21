package dev.veltraph.client.combat.slayer;

import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.item.PrimedTnt;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import dev.veltraph.client.world.Vec3;
import static dev.veltraph.client.combat.slayer.SlayerSignals.*;

/** Scopes phase observations to the selected boss; never treats ordinary player chat as a trigger. */
public final class CombatAwareness {
    public record Observation(Response response,Vec3 objective,Entity entity,List<Vec3> danger,String detail,boolean mania,Vec3 safeFloor,Entity spring,TwinclawsTiming.Hazard twinclaws,SlayerObjectives.Task task,boolean suspendAttacks) {}
    private final TwinclawsTiming claws=new TwinclawsTiming();
    private final GlyphTracker glyphTracker=new GlyphTracker();
    private final BedrockEscape bedrockEscape=new BedrockEscape();
    private final SlayerObjectives objectives=new SlayerObjectives();
    private final SlayerAdds adds=new SlayerAdds();
    private final EnvironmentalHazardEscape environmentalEscape=new EnvironmentalHazardEscape();
    private Object worldOwner;
    private int bossId=-1,retreatSource=-1;private long retreatUntil;private String retreatReason="";private Vec3 retreatPoint;
    private static int priority(Response r) { return switch(r) {
        case ESCAPE -> 110;case SAFE_FLOOR,ICHOR -> 100;case GLYPH -> 95;case SPRING,PILLAR -> 90;case EGGS -> 85;
        case LOOK -> 80;case RETREAT -> 75;case DODGE -> 70;case ADDS -> 40;case FINISH -> 30;case ATTUNEMENT,HITSHIELD -> 25;case IMMUNE -> 20;case NONE -> 0;
    }; }
    private void resetBoss() {environmentalEscape.reset();bedrockEscape.reset();glyphTracker.reset();claws.reset();bossId=-1;retreatSource=-1;retreatUntil=0;retreatReason="";retreatPoint=null;}
    public void reset() {objectives.reset();adds.reset();worldOwner=null;resetBoss();}
    private static Vec3 pos(Entity e) { return new Vec3(e.getX(),e.getY(),e.getZ()); }
    public Observation observe(Minecraft client,LivingEntity boss,String label,long now) {
        return observe(client,boss,label,now,p->true);
    }
    public Observation observe(Minecraft client,LivingEntity boss,String label,long now,java.util.function.Predicate<Vec3> reachable) {
        if(worldOwner!=client.level) {reset();worldOwner=client.level;}
        if(bossId!=boss.getId()) {resetBoss();bossId=boss.getId();}
        Family family=family(label);Response response=Response.NONE;Vec3 objective=null;Entity entity=null;
        Entity spring=null;Vec3 safeFloor=null;boolean mania=false,uncertainPhase=false;List<Vec3> danger=new ArrayList<>();String detail="Melee";
        var task=objectives.observe(client,boss,label,now);
        var add=adds.observe(client,boss,label,SlayerEntityOwnership.visible(client));
        if(add.target()!=null){response=Response.ADDS;objective=pos(add.target());entity=add.target();detail="Independently owned add: "+add.name();}
        if(add.withheld())uncertainPhase=true;
        if(task!=null&&!task.removed()) {
            response=task.kind()==SlayerObjectives.Kind.EGGS?Response.EGGS:task.kind()==SlayerObjectives.Kind.PILLAR?Response.PILLAR:Response.ICHOR;
            objective=task.destination();entity=task.target();detail=task.detail();
        }
        if(VampireFinisher.ready(label)) {response=Response.FINISH;detail="Steak Stake ready";}
        if(client.player.getHealth()<client.player.getMaxHealth()*.3) { retreatUntil=now+1000;retreatSource=boss.getId();retreatPoint=pos(boss);retreatReason="Critical health: disengage"; }
        if(family==Family.SPIDER) {
            float bearing=(float)Math.toDegrees(Math.atan2(client.player.getX()-boss.getX(),boss.getZ()-client.player.getZ()));
            if(Math.abs(dev.veltraph.client.navigation.WalkController.wrap(bearing-client.player.getYRot()))>80) {
                if(response==Response.NONE) {response=Response.DODGE;detail="Spider backstab: turn and reposition";}
            }
        }
        if(family==Family.ENDERMAN && boss.getVehicle()!=null) {
            retreatUntil=now+1500;retreatSource=boss.getId();retreatPoint=pos(boss);retreatReason="Voidgloom laser phase";
        }
        List<TwinclawsTiming.Signal> clawSignals=new ArrayList<>();
        Map<Integer,Vec3> flyingGlyphs=new HashMap<>();
        for(Entity e:client.level.entitiesForRendering()) {
            if(e==boss||e.distanceToSqr(boss)>22*22&&e.distanceToSqr(client.player)>16*16)continue;
            if(e instanceof ArmorStand stand) {
                if(family==Family.ENDERMAN && (e.distanceToSqr(boss)<=16||glyphTracker.tracking(e.getId())) && stand.getItemBySlot(EquipmentSlot.HEAD).is(Items.BEACON)) flyingGlyphs.put(e.getId(),pos(e));
                var skull=SlayerSkulls.identify(stand);
                if(family==Family.ENDERMAN&&skull==SlayerSkulls.Kind.NUKEKUBI){
                    boolean selected=objectives.phaseOwner(e,Family.ENDERMAN,22).filter(boss.getUUID()::equals).isPresent();
                    if(!selected)uncertainPhase=true;
                    else if(priority(response)<priority(Response.LOOK)){response=Response.LOOK;entity=e;objective=pos(e);detail="Nukekubi: hold gaze";}
                }
                if(family==Family.VAMPIRE&&skull==SlayerSkulls.Kind.KILLER_SPRING){
                    boolean selected=objectives.phaseOwner(e,Family.VAMPIRE,22).filter(boss.getUUID()::equals).isPresent();
                    if(!selected)uncertainPhase=true;
                    else{
                        if(spring==null||client.player.distanceToSqr(e)<client.player.distanceToSqr(spring))spring=e;
                        if(priority(response)<priority(Response.SPRING)){response=Response.SPRING;entity=e;objective=pos(e);detail="Killer spring: strike";}
                    }
                }
            }
            if(e instanceof PrimedTnt || e instanceof LightningBolt) danger.add(pos(e));
            if(!(e instanceof ArmorStand)||!e.hasCustomName())continue;
            Response signal=response(family,e.getName().getString());
            if(signal==Response.EGGS) {
                boolean timer=false;
                for(Entity partner:client.level.entitiesForRendering()) if(partner instanceof ArmorStand && partner.distanceToSqr(e)<6.25
                        && partner.getName().getString().replaceAll("§.","").matches("\\d+s \\d+/\\d+")) { timer=true;break; }
                if(!timer)continue;
            }
            boolean attached=e.distanceToSqr(boss)<4*4;
            boolean ordinary=signal==Response.ATTUNEMENT||signal==Response.HITSHIELD||signal==Response.IMMUNE;
            var owner=ordinary?objectives.phaseOwner(e,family,4):Optional.<UUID>empty();
            boolean ownsSelected=ordinary&&owner.filter(boss.getUUID()::equals).isPresent();
            if(ordinary&&attached&&owner.isEmpty())uncertainPhase=true;
            if(family==Family.VAMPIRE && e.distanceToSqr(boss)<2.5*2.5
                    &&Double.isFinite(TwinclawsTiming.seconds(e.getName().getString()))) {
                if(objectives.phaseOwner(e,Family.VAMPIRE,2.5).filter(boss.getUUID()::equals).isPresent())clawSignals.add(new TwinclawsTiming.Signal(e.getId(),e.getName().getString()));
            }
            boolean selectedMania=signal==Response.SAFE_FLOOR&&attached
                    &&objectives.phaseOwner(e,Family.VAMPIRE,4).filter(boss.getUUID()::equals).isPresent();
            if(signal==Response.SAFE_FLOOR&&attached&&!selectedMania)uncertainPhase=true;
            if(selectedMania)mania=true;
            if(response(Family.REVENANT,e.getName().getString())==Response.RETREAT) {
                var warningOwner=objectives.phaseOwner(e,Family.REVENANT,4);
                Entity source=null;
                if(warningOwner.isPresent())for(Entity candidate:client.level.entitiesForRendering())
                    if(candidate.getUUID().equals(warningOwner.get())){source=candidate;break;}
                if(source!=null&&(source==boss||source.distanceToSqr(client.player)<12*12)){
                    retreatUntil=now+3000;retreatSource=source.getId();retreatPoint=pos(source);
                    retreatReason=source==boss?"Revenant explosion/enrage":"Nearby Revenant explosion: pause objective";
                    danger.add(retreatPoint);
                } else if(e.distanceToSqr(client.player)<12*12){
                    // An unowned visible warning can still harm the player; do not assign it to the selected boss.
                    retreatUntil=now+3000;retreatSource=e.getId();retreatPoint=pos(e);
                    retreatReason="Nearby unattributed Revenant warning: disengage";danger.add(retreatPoint);
                }
            }
            if(selectedMania && priority(response)<priority(signal)) { response=signal;detail="Mania: find green ground"; }
            if((signal==Response.ATTUNEMENT||signal==Response.HITSHIELD) && ownsSelected && response==Response.NONE) { response=signal;detail=e.getName().getString().replaceAll("§.",""); }
            if((signal==Response.IMMUNE && ownsSelected) && response==Response.NONE) { response=signal;detail="Boss immune: hold attacks"; }
            if((signal==Response.PILLAR||signal==Response.EGGS)&&attached&&response==Response.NONE
                    &&objectives.phaseOwner(e,signal==Response.PILLAR?Family.BLAZE:Family.SPIDER,signal==Response.PILLAR?22:7.2).filter(boss.getUUID()::equals).isPresent()) {
                response=signal;entity=null;objective=null;detail="Phase observed; owned hittable objective body unavailable";
            }
            if(signal==Response.GLYPH || signal==Response.LOOK) {
                if(objectives.phaseOwner(e,Family.ENDERMAN,22).filter(boss.getUUID()::equals).isEmpty()){uncertainPhase=true;continue;}
                if(priority(signal)>priority(response)||priority(signal)==priority(response)&&(objective==null||client.player.distanceToSqr(e)<client.player.position().distanceToSqr(objective.x(),objective.y(),objective.z()))) {
                    response=signal;objective=pos(e);entity=e;detail="Priority: "+e.getName().getString();
                }
            }
        }
        Set<dev.veltraph.client.world.BlockPos> glyphs=Set.of();
        if(family==Family.ENDERMAN) {
            Set<dev.veltraph.client.world.BlockPos> visibleBeacons=new HashSet<>();var origin=client.player.blockPosition();
            for(int x=-10;x<=10;x++)for(int z=-10;z<=10;z++)for(int y=-1;y<=1;y++) {
                var p=origin.offset(x,y,z);
                if(client.level.getChunkSource().hasChunk(p.getX()>>4,p.getZ()>>4)&&client.level.getBlockState(p).is(Blocks.BEACON))
                    visibleBeacons.add(new dev.veltraph.client.world.BlockPos(p.getX(),p.getY(),p.getZ()));
            }
            glyphs=glyphTracker.observe(flyingGlyphs,visibleBeacons,now);
        }
        // Beacon helmet and landed block are distinct stages; don't run under a still-flying glyph.
        if(family==Family.ENDERMAN || mania || response==Response.SAFE_FLOOR || family==Family.REVENANT || family==Family.BLAZE) {
            var origin=client.player.blockPosition();double nearest=Double.POSITIVE_INFINITY;
            for(int x=-10;x<=10;x++)for(int z=-10;z<=10;z++)for(int y=-1;y<=1;y++) {
                var p=origin.offset(x,y,z);if(!client.level.getChunkSource().hasChunk(p.getX()>>4,p.getZ()>>4))continue;
                var block=client.level.getBlockState(p);Vec3 location=new Vec3(p.getX()+.5,p.getY()+1,p.getZ()+.5);
                if(family==Family.REVENANT && (block.is(Blocks.DYED_TERRACOTTA.red())||block.is(Blocks.TNT))
                        || family==Family.BLAZE && (block.is(Blocks.FIRE)||block.is(Blocks.LAVA))) danger.add(location);
                boolean glyph=glyphs.contains(new dev.veltraph.client.world.BlockPos(p.getX(),p.getY(),p.getZ()));
                boolean green=mania && block.is(Blocks.DYED_TERRACOTTA.green());
                if(glyph) {
                    Vec3 adjacent=null;double nearestSide=Double.POSITIVE_INFINITY;
                    for(int[] direction:new int[][]{{1,0},{-1,0},{0,1},{0,-1}}) {
                        var side=p.offset(direction[0],0,direction[1]);
                        if(!client.level.getBlockState(side).getCollisionShape(client.level,side).isEmpty()
                            ||!client.level.getBlockState(side.above()).getCollisionShape(client.level,side.above()).isEmpty()
                            ||!client.level.getBlockState(side.below()).isFaceSturdy(client.level,side.below(),net.minecraft.core.Direction.UP))continue;
                        double d=client.player.position().distanceToSqr(side.getX()+.5,side.getY(),side.getZ()+.5);
                        if(d<nearestSide) { nearestSide=d;adjacent=new Vec3(side.getX()+.5,side.getY(),side.getZ()+.5); }
                    }
                    if(adjacent==null)continue;location=adjacent;
                }
                if(glyph||green) {
                    double d=client.player.position().distanceToSqr(location.x(),location.y(),location.z());
                    if(green) {
                        if(!EnvironmentalHazardEscape.clearOfHazards(location,danger)||!reachable.test(location))continue;
                        if(task!=null&&task.kind()==SlayerObjectives.Kind.ICHOR&&task.destination()!=null)
                            d=location.horizontalDistanceTo(task.destination())*2+Math.sqrt(d)*.15;
                    }
                    if(d<nearest) {
                        nearest=d;
                        if(green)safeFloor=location;
                        if(glyph||response==Response.SAFE_FLOOR) {objective=location;entity=null;}
                        if(glyph) {response=Response.GLYPH;detail="Yang glyph: move adjacent";}
                    }
                }
            }
        }
        var physicalEscape=environmentalEscape.observe(family!=Family.OTHER,pos(client.player),danger,p->{
            var cell=new net.minecraft.core.BlockPos(p.toBlockPos().x(),p.toBlockPos().y(),p.toBlockPos().z());
            var scan=client.player.blockPosition();
            return client.level.isLoaded(cell)&&Math.abs(cell.getX()-scan.getX())<=10
                    &&Math.abs(cell.getZ()-scan.getZ())<=10&&Math.abs(cell.getY()-scan.getY())<=3;
        },now,family==Family.REVENANT?"Revenant":"Slayer environmental");
        if(physicalEscape.active()&&now>=retreatUntil&&!mania){
            response=Response.RETREAT;objective=physicalEscape.source();entity=null;detail=physicalEscape.detail();
        }
        if(physicalEscape.active()&&mania)detail+=" | "+physicalEscape.detail();
        boolean emergency=now<retreatUntil&&(retreatReason.startsWith("Critical")||retreatReason.startsWith("Nearby"));
        if(now<retreatUntil && !mania && (emergency||priority(response)<75 || family==Family.REVENANT || family==Family.ENDERMAN&&response!=Response.GLYPH)) {
            response=Response.RETREAT;var source=client.level.getEntity(retreatSource);if(source!=null)retreatPoint=pos(source);objective=retreatPoint==null?pos(boss):retreatPoint;entity=null;detail=retreatReason;
        }
        var twinclaws=claws.observe(boss.getId(),clawSignals,now);
        if(twinclaws.active()) {
            // Preserve Mania safe-ground and Ichor alignment; ordinary melee instead
            // disengages for a conservative radius, without inventing beam directions.
            if(!mania && response!=Response.ICHOR) {response=Response.RETREAT;objective=pos(boss);entity=null;detail=twinclaws.detail();}
            else detail+=" | "+twinclaws.detail();
        }
        if(cryptBoss(label)) {
            var bedrock=new HashSet<dev.veltraph.client.world.BlockPos>();var escapeCandidates=new ArrayList<Vec3>();
            var origin=bedrockEscape.origin()==null?client.player.blockPosition():new net.minecraft.core.BlockPos(
                    bedrockEscape.origin().toBlockPos().x(),bedrockEscape.origin().toBlockPos().y(),bedrockEscape.origin().toBlockPos().z());
            // Scan the activation area, not the moving boss or the retreating player.
            for(int x=-12;x<=12;x++)for(int z=-12;z<=12;z++) {
                var feet=origin.offset(x,0,z);var floor=feet.below();
                if(!client.level.getChunkSource().hasChunk(feet.getX()>>4,feet.getZ()>>4))continue;
                var state=client.level.getBlockState(floor);
                if(state.is(Blocks.BEDROCK))bedrock.add(new dev.veltraph.client.world.BlockPos(floor.getX(),floor.getY(),floor.getZ()));
                else if(state.isFaceSturdy(client.level,floor,net.minecraft.core.Direction.UP)
                        &&client.level.getBlockState(feet).getCollisionShape(client.level,feet).isEmpty()
                        &&client.level.getBlockState(feet.above()).getCollisionShape(client.level,feet.above()).isEmpty())
                    escapeCandidates.add(new Vec3(feet.getX()+.5,feet.getY(),feet.getZ()+.5));
            }
            // An unloaded chunk does not acknowledge server removal of a tracked hazard.
            for(var tracked:bedrockEscape.footprint())if(!client.level.getChunkSource().hasChunk(tracked.x()>>4,tracked.z()>>4))bedrock.add(tracked);
            var escape=bedrockEscape.observe(true,pos(client.player),pos(boss),bedrock,escapeCandidates,reachable,now);
            if(escape.active()) {response=Response.ESCAPE;objective=escape.objective();entity=null;detail=escape.detail();}
        } else bedrockEscape.reset();
        if(uncertainPhase&&response==Response.NONE){response=Response.IMMUNE;detail="Unattributed boss phase: hold attacks and preserve gear";}
        return new Observation(response,objective,entity,List.copyOf(danger),detail,mania,safeFloor,spring,twinclaws,task,emergency||uncertainPhase||physicalEscape.active());
    }
}
