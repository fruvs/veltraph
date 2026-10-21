package dev.veltraph.client.combat.slayer;

import dev.veltraph.client.world.Vec3;
import java.util.*;
import java.util.regex.Pattern;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.decoration.ArmorStand;
import static dev.veltraph.client.combat.slayer.SlayerSignals.*;

/** Separates phase plates, physical parts, spatial ownership and actual server-removal acknowledgment. */
public final class SlayerObjectives {
    public enum Kind { EGGS, PILLAR, ICHOR }
    public record Task(UUID identity,Kind kind,Entity target,int progress,boolean actionable,boolean present,
                       boolean removed,Vec3 destination,String detail,Set<UUID> bodies,List<ObjectiveOwnership.Boss> originRoster) {}
    private record Part(int id,UUID identity,Vec3 position) {}
    private static final Pattern PILLAR=Pattern.compile("(\\d{1,4}(?:[.,]\\d{1,3})?)s (\\d{1,3}) hits");
    private static final Pattern EGG=Pattern.compile("\\d+s \\d+/\\d+");
    private static final class Watch {
        final UUID identity,owner;final Kind kind;final Map<Integer,Part> parts=new HashMap<>();
        final List<ObjectiveOwnership.Boss> roster;
        int progress=-1;boolean plate;Vec3 anchor;
        Watch(UUID identity,UUID owner,Kind kind,Vec3 anchor,List<ObjectiveOwnership.Boss> roster) {this.identity=identity;this.owner=owner;this.kind=kind;this.anchor=anchor;this.roster=List.copyOf(roster);}
    }
    private final ObjectiveOwnership ownership=new ObjectiveOwnership();
    private final Map<UUID,Watch> watched=new HashMap<>();
    private final Set<UUID> completed=new HashSet<>();
    private UUID active,bossIdentity;
    private List<ObjectiveOwnership.Boss> currentBosses=List.of();
    public void reset() {ownership.reset();watched.clear();completed.clear();active=null;bossIdentity=null;currentBosses=List.of();}
    public Optional<UUID> phaseOwner(Entity phase,Family family,double radius) {
        return ownership.associate(phase.getUUID(),family,pos(phase),radius,currentBosses);
    }
    public Task observe(Minecraft client,LivingEntity boss,String label,long now) {
        if(!boss.getUUID().equals(bossIdentity)) {active=null;bossIdentity=boss.getUUID();}
        var entities=new ArrayList<Entity>();for(var e:client.level.entitiesForRendering())entities.add(e);
        var stands=entities.stream().filter(e->e instanceof ArmorStand).map(e->(ArmorStand)e).toList();
        var ichors=stands.stream().filter(s->SlayerSkulls.identify(s)==SlayerSkulls.Kind.BLOOD_ICHOR).toList();
        var bosses=new ArrayList<ObjectiveOwnership.Boss>();
        Family selected=family(label);
        for(var e:entities)if(e instanceof LivingEntity living&&!(e instanceof ArmorStand)&&e!=client.player&&e.isAlive()) {
            Family found=e==boss?selected:family(e.getName().getString());
            if(found==Family.OTHER&&clean(e.getName().getString()).equals("Bloodfiend")&&e instanceof net.minecraft.world.entity.player.Player
                    &&client.getConnection()!=null&&client.getConnection().getPlayerInfo(e.getUUID())==null)found=Family.VAMPIRE;
            if(found==Family.OTHER) {
                var plates=stands.stream().filter(s->s.hasCustomName()&&family(s.getName().getString())!=Family.OTHER
                        &&s.getBoundingBox().inflate(.6,2,.6).intersects(e.getBoundingBox())).toList();
                if(plates.size()==1)found=family(plates.getFirst().getName().getString());
            }
            if(found!=Family.OTHER)bosses.add(new ObjectiveOwnership.Boss(e.getUUID(),found,receivedPos(e)));
        }
        currentBosses=List.copyOf(bosses);
        for(var w:watched.values())w.plate=false;
        var receipts=SlayerParticleSignal.read(client.level,now);
        for(var stand:stands) {
            if(!stand.isAlive()||completed.contains(stand.getUUID()))continue;
            Kind kind=null;int progress=-1;double radius=22;Vec3 association=pos(stand);List<ArmorStand> parts=List.of();
            String text=clean(stand.getName().getString());var pillar=PILLAR.matcher(text);
            if(pillar.matches()) {
                kind=Kind.PILLAR;progress=Integer.parseInt(pillar.group(2));
                if(progress>128)continue;
                // No recovered live block/pet metadata is invented. Require a physically pickable part.
                parts=stands.stream().filter(s->s.isAlive()&&!s.isMarker()&&s.isPickable()&&!s.hasCustomName()
                        &&Math.abs(s.getX()-stand.getX())<.4&&Math.abs(s.getZ()-stand.getZ())<.4
                            &&s.getY()<=stand.getY()+1&&s.getY()>=stand.getY()-5).toList();
            } else if(EGG.matcher(text).matches()) {
                if(stands.stream().noneMatch(s->clean(s.getName().getString()).equalsIgnoreCase("SHOOT ME!")&&s.distanceToSqr(stand)<6.25))continue;
                kind=Kind.EGGS;radius=Double.POSITIVE_INFINITY; // Original box gate follows below.
                parts=stands.stream().filter(s->s.isAlive()&&!s.isMarker()&&s.isPickable()&&!s.hasCustomName()&&s.distanceToSqr(stand)<1.44).toList();
            } else if(ichors.contains(stand)) {
                kind=Kind.ICHOR;radius=3;parts=List.of(stand);
                if(ownership.owner(stand.getUUID()).isEmpty()) {
                    SlayerParticleSignal.Receipt match=null;
                    for(var receipt:receipts) {
                        if(receipt.position().distanceTo(pos(stand))>=3)continue;
                        long skulls=ichors.stream().filter(s->receipt.position().distanceTo(pos(s))<3).count();
                        if(skulls==1) {match=receipt;break;}
                    }
                    if(match==null)continue;association=match.position();
                }
            }
            if(kind==null||parts.isEmpty())continue;
            Family required=kind==Kind.PILLAR?Family.BLAZE:kind==Kind.EGGS?Family.SPIDER:Family.VAMPIRE;
            // Receive/render latency can put an approaching second boss just outside the strict source box.
            // The added envelope only rejects ambiguity; it cannot authorize an owner outside that box.
            var plausible=kind==Kind.EGGS?bosses.stream().filter(b->b.family()==Family.SPIDER&&Math.abs(b.position().x()-stand.getX())<=8
                    &&Math.abs(b.position().z()-stand.getZ())<=8&&Math.abs(stand.getY()-b.position().y())<=10).toList():bosses;
            if(kind==Kind.EGGS&&ownership.owner(stand.getUUID()).isEmpty()&&plausible.size()==1
                    &&(Math.abs(plausible.getFirst().position().x()-stand.getX())>5||Math.abs(plausible.getFirst().position().z()-stand.getZ())>5)) {
                ownership.reject(stand.getUUID());continue;
            }
            var owner=ownership.associateWithMargin(stand.getUUID(),required,association,radius,kind==Kind.ICHOR?1.5:kind==Kind.PILLAR?3:0,plausible);
            if(owner.isEmpty())continue;
            // Preserve an established physical UUID, but never adopt a new part that also fits another plate.
            var previousWatch=watched.get(stand.getUUID());var eligibleParts=new ArrayList<ArmorStand>();
            for(var part:parts) {
                boolean known=previousWatch!=null&&previousWatch.parts.containsKey(part.getId());
                boolean ambiguous=kind!=Kind.ICHOR&&!known&&stands.stream().anyMatch(s->s!=stand&&s.hasCustomName()
                        &&(EGG.matcher(clean(s.getName().getString())).matches()&&s.distanceToSqr(part)<1.44
                            ||PILLAR.matcher(clean(s.getName().getString())).matches()&&Math.abs(s.getX()-part.getX())<.4
                                &&Math.abs(s.getZ()-part.getZ())<.4&&s.getY()>=part.getY()-1&&s.getY()<=part.getY()+5));
                if(!ambiguous)eligibleParts.add(part);
            }
            if(eligibleParts.isEmpty())continue;
            Kind captured=kind;
            var watch=watched.computeIfAbsent(stand.getUUID(),id->new Watch(id,owner.get(),captured,pos(stand),bosses));
            watch.plate=true;watch.progress=progress;watch.anchor=pos(stand);
            // Keep earlier parts until removal is actually observed; a blinking marker cannot complete a task.
            for(var part:eligibleParts)watch.parts.put(part.getId(),new Part(part.getId(),part.getUUID(),pos(part)));
        }
        var visible=new HashSet<UUID>();for(var e:entities)visible.add(e.getUUID());
        completed.retainAll(visible);
        var tasks=new ArrayList<Task>();var finished=new ArrayList<UUID>();
        for(var w:watched.values()) {
            Entity nearest=null;boolean unknown=false;int remaining=0;var bodies=new HashSet<UUID>();
            for(var part:w.parts.values()) {
                var e=client.level.getEntity(part.id());
                if(e!=null&&e.getUUID().equals(part.identity())&&!e.isRemoved()) {
                    w.parts.put(part.id(),new Part(part.id(),part.identity(),pos(e)));
                    remaining++;if(e.isAlive()) {bodies.add(e.getUUID());if(nearest==null||client.player.distanceToSqr(e)<client.player.distanceToSqr(nearest))nearest=e;}
                } else if(e==null&&(!client.level.getChunkSource().hasChunk(part.position().toBlockPos().x()>>4,part.position().toBlockPos().z()>>4)
                        ||pos(client.player).distanceTo(part.position())>32))unknown=true;
            }
            if(remaining==0&&!unknown) {
                finished.add(w.identity);completed.add(w.identity);
                if(w.identity.equals(active)&&w.owner.equals(boss.getUUID()))tasks.add(new Task(w.identity,w.kind,null,w.progress,false,false,true,null,"Server removed objective body",Set.of(),w.roster));
                continue;
            }
            visible.add(w.identity);
            if(!w.owner.equals(boss.getUUID())||w.kind==Kind.PILLAR&&selected!=Family.BLAZE
                    ||w.kind==Kind.EGGS&&selected!=Family.SPIDER||w.kind==Kind.ICHOR&&selected!=Family.VAMPIRE)continue;
            Vec3 destination=nearest==null?w.anchor:pos(nearest);
            String detail=w.kind==Kind.EGGS?"Owned egg body: projectile request/removal pending":w.kind==Kind.PILLAR
                    ?"Owned Fire Pillar: server hit counter/removal pending":"Owned Blood Ichor: align Twinclaws; await server skull removal";
            if(w.kind==Kind.ICHOR&&nearest!=null) {
                double dx=nearest.getX()-boss.getX(),dz=nearest.getZ()-boss.getZ(),length=Math.max(.01,Math.hypot(dx,dz));
                destination=new Vec3(nearest.getX()+dx/length*2,client.player.getY(),nearest.getZ()+dz/length*2);
            }
            tasks.add(new Task(w.identity,w.kind,nearest,w.kind==Kind.EGGS?remaining:w.progress,w.plate&&nearest!=null&&w.kind!=Kind.ICHOR,nearest!=null,false,destination,detail,Set.copyOf(bodies),w.roster));
        }
        for(var id:finished)watched.remove(id);ownership.retain(visible);
        var removal=tasks.stream().filter(Task::removed).findFirst();if(removal.isPresent()) {active=null;return removal.get();}
        var previous=tasks.stream().filter(t->t.identity().equals(active)).findFirst();
        Task task=previous.orElseGet(()->tasks.stream().min(Comparator.comparingInt((Task t)->t.kind()==Kind.ICHOR?-100:t.kind()==Kind.PILLAR?-90:-85)
                .thenComparingDouble(t->pos(client.player).distanceTo(t.destination()))).orElse(null));
        active=task==null?null:task.identity();return task;
    }
    private static String clean(String s) {return s.replaceAll("§.","").strip();}
    private static Vec3 pos(Entity e) {return new Vec3(e.getX(),e.getY(),e.getZ());}
    /** Ownership uses the latest received position, while aim/movement keep their smooth rendered pose. */
    private static Vec3 receivedPos(Entity e) {
        var p=e.getClientPosition();return new Vec3(p.x,p.y,p.z);
    }
}
