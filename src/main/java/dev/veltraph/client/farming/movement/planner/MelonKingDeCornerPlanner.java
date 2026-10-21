package dev.veltraph.client.farming.movement.planner;

import dev.veltraph.client.farming.movement.Angles;
import dev.veltraph.client.farming.movement.GourdCropTarget;
import dev.veltraph.client.service.FarmServices;
import dev.veltraph.client.world.BlockPos;
import dev.veltraph.client.world.Direction;
import dev.veltraph.client.world.Vec3;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

/** Loaded end-bank completion before the original fixed-view row transfer. */
public final class MelonKingDeCornerPlanner {
    public enum State { IDLE, APPROACH, HARVEST, RESTORE, READY, BLOCKED }
    private record End(BlockPos floor, Direction inward) {}
    /** Original three-column terminal bank; a passive geometry result, never movement permission. */
    public record CourseStrip(BlockPos floor,Direction inward,Direction heading,boolean cleared) {}
    private CourseStrip observedStrip;
    public Optional<CourseStrip> courseStrip(){return Optional.ofNullable(observedStrip);}
    private final FarmServices services;
    private final Set<End> completed = new HashSet<>();
    private State state = State.IDLE;
    private End end;
    private Direction heading;
    private Vec3 goal;
    private BlockPos target;
    private Vec3 point;
    private Vec3 previous;
    private boolean corridorFocus;
    private boolean crossingWetCap;
    private int dryTicks;
    private int ticks;
    private String blocker = "idle";

    public MelonKingDeCornerPlanner(FarmServices services) { this.services=services; }
    public void reset() { observedStrip=null;completed.clear(); state=State.IDLE; end=null; target=null; point=null; previous=null; ticks=0; }
    public State state() { return state; }
    public String blocker() { return blocker; }
    public boolean active() { return state!=State.IDLE; }
    public boolean endReady() { return (state==State.IDLE||state==State.READY) && end!=null && completed.contains(end); }

    /** Row water is the actual lower water cell backed by SoulSand, never upper irrigation. */
    public boolean begin(BlockPos water,Direction heading) {
        if(active() || water==null)return active();
        var actor=services.player().position();
        for(var outward:new Direction[]{heading.rotateYClockwise(),heading.rotateYCounterclockwise()}) {
            var floor=new BlockPos(actor.toBlockPos().x(),water.y()-1,actor.toBlockPos().z());
            // Only the retained three-column outer strip belongs to this adaptation.
            // Taking control four cells early would suppress the last two ordinary
            // working-course columns on every row, regardless of repeated passes.
            for(int n=0;n<=2;n++) {
                var current=floor.offset(outward,n);var next=current.offset(outward);
                if(!services.world().isLoaded(current)||!services.world().isLoaded(next))break;
                if(!walkingFloor(current))break;
                if(walkingFloor(next))continue;
                var candidate=new End(current,outward.opposite());
                if(completed.contains(candidate))break;
                boolean capped=services.world().isBlock(current.up(),"minecraft:polished_andesite");
                boolean dry=dryBody(actor);
                // The transverse snow corridor has no lower soil bank to arrest forward
                // pressure. Hold both actual coordinates there while resolving its
                // exposed native crop windows, then restore the source course.
                double inset=capped&&!dry?1.1:.8;
                var proposed=new Vec3(current.x()+.5+candidate.inward.dx()*inset,water.y()-.125,
                        current.z()+.5+candidate.inward.dz()*inset);
                // Retain the actor's real crop-bank contact on the forward axis.
                proposed=heading.dx()==0?new Vec3(proposed.x(),proposed.y(),actor.z()):new Vec3(actor.x(),proposed.y(),proposed.z());
                boolean clear=services.world().bodyClear(proposed,.3,1.8),support=services.world().hasGroundSupport(proposed,.3);
                if(!clear||!support){blocker="end candidate rejected "+proposed+" clear="+clear+" support="+support;break;}
                this.end=candidate;this.heading=heading;this.goal=proposed;this.state=State.APPROACH;this.ticks=0;
                this.target=null;this.point=null;this.previous=actor;this.corridorFocus=true;
                this.crossingWetCap=capped&&!dry;this.dryTicks=dry?2:0;
                blocker="approaching loaded end bank "+current;return true;
            }
        }
        return false;
    }

    /**
     * Passive completion evidence for the fixed-view course. Ordinary wall-driven movement
     * remains owned by the water planner; no per-fruit stop, angle, approach or attack is issued.
     * All original nine end cells must be loaded and actually cleared before END is ready.
     */
    public void observeCourse(BlockPos water,Direction heading){observeCourse(water,heading,()->true);}
    public void observeCourse(BlockPos water,Direction heading,java.util.function.BooleanSupplier originalCurrent) {
        if(originalCurrent==null)return;
        class Read {
            boolean lost;
            boolean current(){if(lost||!originalCurrent.getAsBoolean()){lost=true;return false;}return true;}
            boolean yes(java.util.function.BooleanSupplier provider){if(!current())return false;boolean value=provider.getAsBoolean();return current()&&value;}
            String id(BlockPos cell){if(!current())return null;String value=services.world().blockId(cell);return current()?value:null;}
            void clear(String reason){if(!current())return;observedStrip=null;end=null;state=State.IDLE;target=null;point=null;blocker=reason;}
        }
        var read=new Read();if(!read.current())return;
        if(water==null){read.clear("no current supported end bank");return;}
        Vec3 actor=services.player().position();if(!read.current())return;
        if(actor==null||!Double.isFinite(actor.x())||!Double.isFinite(actor.y())||!Double.isFinite(actor.z())){read.clear("invalid current walking-course position");return;}
        if(!read.yes(()->MelonKingDeWalkingLaneMarker.walkingWater(services.world(),water,water.y()))
                ||water.x()!=actor.toBlockPos().x()||water.z()!=actor.toBlockPos().z()
                ||Math.abs(actor.y()-(water.y()-.125))>=.01
                ||!read.yes(()->services.player().onGround())
                ||!read.yes(()->services.world().bodyClear(actor,.3,1.8))
                ||!read.yes(()->services.world().hasGroundSupport(actor,.3))){read.clear("no current supported end bank");return;}
        BlockPos floor=new BlockPos(actor.toBlockPos().x(),water.y()-1,actor.toBlockPos().z());
        for(Direction outward:new Direction[]{heading.rotateYClockwise(),heading.rotateYCounterclockwise()}) {
            for(int n=0;n<=2;n++) {
                BlockPos bank=floor.offset(outward,n),next=bank.offset(outward);
                if(!read.yes(()->services.world().isLoaded(bank))||!read.yes(()->services.world().isLoaded(next))
                        ||!read.yes(()->walkingFloor(bank)))break;
                if(read.yes(()->walkingFloor(next)))continue;
                if(!read.current())return;
                End candidate=new End(bank,outward.opposite());boolean remaining=false;
                for(int tier=0;tier<3;tier++)for(int along=0;along<3;along++) {
                    BlockPos crop=bank.up(2+tier*2).offset(candidate.inward,along).offset(heading,1+tier);
                    if(!read.yes(()->services.world().isLoaded(crop))||!read.yes(()->services.world().isLoaded(crop.down()))){read.clear("end crop coverage unknown");return;}
                    for(Direction neighbor:new Direction[]{Direction.NORTH,Direction.EAST,Direction.SOUTH,Direction.WEST}) {
                        BlockPos stem=crop.offset(neighbor);
                        if(!read.yes(()->services.world().isLoaded(stem))||!read.yes(()->services.world().isLoaded(stem.down()))){read.clear("end crop bed coverage unknown");return;}
                    }
                    String id=read.id(crop);if(!read.current())return;
                    boolean present=id.equals("minecraft:melon")||id.equals("minecraft:pumpkin")||id.equals("minecraft:carved_pumpkin");
                    boolean bed=present?read.yes(()->GourdCropTarget.cropBed(services.world(),crop,id.equals("minecraft:melon")?"melon":"pumpkin"))
                            :read.yes(()->GourdCropTarget.cropBed(services.world(),crop,"pumpkin"))||read.yes(()->GourdCropTarget.cropBed(services.world(),crop,"melon"));
                    if(!bed||(!present&&!id.equals("minecraft:air"))){read.clear("end crop planting geometry changed");return;}
                    remaining|=present;
                }
                boolean terminal=read.yes(()->services.world().isBlock(bank,"minecraft:soul_sand"))
                        &&read.yes(()->MelonKingDeWalkingLaneMarker.walkingWater(services.world(),bank.up(),water.y()))
                        &&read.yes(()->services.world().isLoaded(next.up()))
                        &&!read.yes(()->services.world().isBlock(next.up(),"minecraft:polished_andesite"));
                for(int distance=1;terminal&&distance<=4;distance++) {
                    BlockPos following=bank.up().offset(heading,distance).offset(candidate.inward);
                    if(!read.yes(()->services.world().isLoaded(following))||!read.yes(()->services.world().isLoaded(following.down()))
                            ||read.yes(()->MelonKingDeWalkingLaneMarker.walkingWater(services.world(),following,water.y())))terminal=false;
                }
                if(!read.current())return;
                end=candidate;this.heading=heading;state=State.IDLE;target=null;point=null;
                if(remaining){completed.remove(candidate);blocker="end crops remain on fixed-view course";}
                else{completed.add(candidate);blocker="loaded end bank observed cleared";}
                observedStrip=terminal?new CourseStrip(bank,candidate.inward,heading,!remaining):null;
                return;
            }
        }
        read.clear("ordinary fixed-view working columns");
    }

    private boolean walkingFloor(BlockPos floor) {
        return services.world().isBlock(floor,"minecraft:soul_sand")
                ||(services.world().isBlock(floor,"minecraft:snow")
                &&services.world().blockProperty(floor,"layers").filter("8"::equals).isPresent());
    }

    private boolean dryBody(Vec3 actor) {
        for(int x=(int)Math.floor(actor.x()-.3001);x<=Math.floor(actor.x()+.3001);x++)
            for(int z=(int)Math.floor(actor.z()-.3001);z<=Math.floor(actor.z()+.3001);z++)
                for(int y=(int)Math.floor(actor.y());y<=Math.floor(actor.y()+1.8);y++) {
                    var p=new BlockPos(x,y,z);
                    if(!services.world().isLoaded(p)||services.world().isBlock(p,"minecraft:water")
                            ||services.world().isBlock(p,"minecraft:bubble_column")||services.world().isBlock(p,"minecraft:lava")
                            ||services.world().blockProperty(p,"waterlogged").filter("true"::equals).isPresent())return false;
                }
        return true;
    }

    /** Creator rows hold their startup view, including loaded end-bank completion. */
    public Optional<float[]> focusAngles() { return Optional.empty(); }

    private boolean endCrop(BlockPos crop) {
        if(crop==null)return false;
        for(int tier=0;tier<3;tier++)for(int along=0;along<3;along++)
            if(crop.equals(end.floor.up(2+tier*2).offset(end.inward,along).offset(heading,1+tier)))return true;
        return false;
    }

    /** Aim is stepped once by the module; this method owns only ordinary held keys and attack. */
    public void tick(float rowYaw,float rowPitch) {
        services.input().releaseAllMovement();services.breaking().setBreaking(false);
        if(!active())return;
        if(state!=State.READY&&++ticks>600){state=State.BLOCKED;blocker="end crop completion timed out";return;}
        if(state==State.BLOCKED)return;
        var actor=services.player().position();
        double progress=previous==null?0:(actor.x()-previous.x())*end.inward.dx()+(actor.z()-previous.z())*end.inward.dz();
        double forwardProgress=previous==null?0:(actor.x()-previous.x())*heading.dx()+(actor.z()-previous.z())*heading.dz();
        previous=actor;
        if(!services.world().bodyClear(actor,.3,1.8)||!services.world().hasGroundSupport(actor,.3)
                ||!services.player().onGround()){blocker="waiting for actual supported end pose";return;}
        if(state==State.READY) {
            holdContact(actor,progress,forwardProgress);blocker="final crops complete; holding supported END";return;
        }
        if(state==State.APPROACH) {
            if(Math.abs(Angles.signedDelta(services.rotation().yaw(),rowYaw))>.2||Math.abs(services.rotation().pitch()-rowPitch)>.2) {
                blocker="restoring row view before end approach";return;
            }
            if(crossingWetCap) {
                dryTicks=dryBody(actor)?dryTicks+1:0;
                if(dryTicks<2) {
                    // A wet horizontal collision can invoke vanilla's automatic liquid
                    // wall-exit lift without any jump key. Cross the actual dry body
                    // boundary first, using ordinary momentum/coasting, then touch the cap.
                    holdContact(actor,progress,forwardProgress);blocker="coasting across observed dry end boundary";return;
                }
                goal=heading.dx()==0?new Vec3(end.floor.x()+.5+end.inward.dx()*.8,goal.y(),goal.z())
                        :new Vec3(goal.x(),goal.y(),end.floor.z()+.5+end.inward.dz()*.8);
                crossingWetCap=false;
            }
            double distance=(goal.x()-actor.x())*end.inward.dx()+(goal.z()-actor.z())*end.inward.dz();
            if(corridorFocus) {
                if(capContact())holdWorldDirection(end.inward.opposite(),false);
                else holdContact(actor,progress,forwardProgress);
                if(capContact()&&Math.abs(distance)<.15&&Math.abs(progress)<.025&&Math.abs(forwardProgress)<.025) {
                    // The source-to-dry transition may leave a small real forward drift.
                    // Preserve its actual supported stopping coordinate, rather than
                    // inventing a sub-block placement with native400 key pulses.
                    goal=heading.dx()==0?new Vec3(goal.x(),goal.y(),actor.z()):new Vec3(actor.x(),goal.y(),goal.z());
                }
                double forwardDistance=(goal.x()-actor.x())*heading.dx()+(goal.z()-actor.z())*heading.dz();
                if(Math.abs(distance)>.15||Math.abs(forwardDistance)>.1||Math.abs(progress)>.025||Math.abs(forwardProgress)>.025) {
                    blocker="settling actual supported end coordinates";return;
                }
            }
            else if(Math.abs(distance)>.35){
                double predicted=distance-progress*1.5;
                var direction=predicted>0?end.inward:end.inward.opposite();
                var step=new Vec3(actor.x()+direction.dx()*.15,actor.y(),actor.z()+direction.dz()*.15);
                if(!services.world().bodyClear(step,.3,1.8)||!services.world().hasGroundSupport(step,.3)) {
                    blocker="end approach constrained by actual support/collision";return;
                }
                if(corridorFocus)holdContact(actor,progress,forwardProgress);
                else holdWorldDirection(direction,true);
                blocker="walking to loaded end contact";return;
            }
            state=State.HARVEST;blocker="finding exposed end crop";
        }
        if(state==State.HARVEST) {
            if(capContact())services.input().releaseAllMovement();
            else holdContact(actor,progress,forwardProgress);
            // The held row view decides which original end fruit can be hit. Prefer the
            // actual ray, never rotate to a geometry candidate chosen from another tier.
            var nativeTarget=services.breaking().targetBlockPos().filter(this::endCrop)
                    .filter(crop->GourdCropTarget.harvestable(services.world(),crop));
            if(nativeTarget.isPresent()){target=nativeTarget.get();point=null;}
            if(target!=null&&!GourdCropTarget.harvestable(services.world(),target)){target=null;point=null;}
            if(target!=null) {
                var currentPoint=services.world().interactionPoint(target,services.player().eyePosition(),services.player().blockInteractionRange());
                if(currentPoint.isPresent())point=currentPoint.get();
                else {target=null;point=null;}
            }
            if(target==null) {
                boolean remaining=false;
                // All three source tiers and the full retained three-cell outer bank, including
                // original outermost crop cells. Crop validation is independent of this grouping.
                for(int tier=0;tier<3&&target==null;tier++)for(int along=0;along<3&&target==null;along++) {
                    var crop=end.floor.up(2+tier*2).offset(end.inward,along).offset(heading,1+tier);
                    if(!GourdCropTarget.harvestable(services.world(),crop))continue;
                    remaining=true;
                    var candidate=services.world().interactionPoint(crop,services.player().eyePosition(),services.player().blockInteractionRange());
                    if(candidate.isPresent()){target=crop;point=candidate.get();}
                }
                if(target==null) {
                    if(remaining){
                        {
                            // The uncapped final stream retains all three soil banks.
                            // Move under a remaining fruit's own loaded column before
                            // declaring a source-control coverage limit from the cap pose.
                            for(int tier=0;tier<3;tier++)for(int along=0;along<3;along++) {
                                var crop=end.floor.up(2+tier*2).offset(end.inward,along).offset(heading,1+tier);
                                if(!GourdCropTarget.harvestable(services.world(),crop))continue;
                                var closer=heading.dx()==0?new Vec3(crop.x()+.5,goal.y(),goal.z()):new Vec3(goal.x(),goal.y(),crop.z()+.5);
                                if(actor.horizontalDistanceTo(closer)<.2||!services.world().bodyClear(closer,.3,1.8)
                                        ||!services.world().hasGroundSupport(closer,.3))continue;
                                goal=closer;state=State.APPROACH;blocker="checking remaining final fruit from its supported column";return;
                            }
                        }
                        if(!corridorFocus) {
                            var closer=new Vec3(end.floor.x()+.5+end.inward.dx()*.95,goal.y(),end.floor.z()+.5+end.inward.dz()*.95);
                            closer=heading.dx()==0?new Vec3(closer.x(),closer.y(),goal.z()):new Vec3(goal.x(),closer.y(),closer.z());
                            if(services.world().bodyClear(closer,.3,1.8)&&services.world().hasGroundSupport(closer,.3)) {
                                goal=closer;corridorFocus=true;state=State.APPROACH;blocker="checking closer supported corridor sightline";return;
                            }
                        }
                        blocker="remaining end fruit occluded or out of native reach";return;
                    }
                    if(!capped()) {
                        double distance=(actor.x()-end.floor.x()-.5)*end.inward.dx()+(actor.z()-end.floor.z()-.5)*end.inward.dz();
                        if(distance<.4) {
                            goal=heading.dx()==0?new Vec3(end.floor.x()+.5+end.inward.dx()*.8,goal.y(),goal.z())
                                    :new Vec3(goal.x(),goal.y(),end.floor.z()+.5+end.inward.dz()*.8);
                            state=State.APPROACH;blocker="returning to supported final course contact";return;
                        }
                    }
                    completed.add(end);state=State.RESTORE;point=null;blocker="restoring creator row view";return;
                }
            }
            // The fresh native crosshair remains authoritative; geometry candidates never install
            // a synthetic hit result or permit attacking an occluding stem/support.
            boolean exact=services.breaking().targetBlockPos().filter(target::equals).isPresent()
                    &&GourdCropTarget.harvestable(services.world(),target);
            services.breaking().setBreaking(exact);blocker="held-view end crop "+target;
            // The final uncapped source end may have ordinary horizontal flow. Stay supported
            // with native input feedback while retaining the measured focus, without changing vY.
            return;
        }
        if(state==State.RESTORE) {
            if(!capContact())holdContact(actor,progress,forwardProgress);
            if(Math.abs(Angles.signedDelta(services.rotation().yaw(),rowYaw))<.2
                &&Math.abs(services.rotation().pitch()-rowPitch)<.2){state=capped()?State.IDLE:State.READY;blocker="end bank complete";}
        }
    }

    private boolean capped() { return services.world().isBlock(end.floor.up(),"minecraft:polished_andesite"); }
    private boolean capContact() {
        double distance=(goal.x()-end.floor.x()-.5)*end.inward.dx()+(goal.z()-end.floor.z()-.5)*end.inward.dz();
        return capped()&&Math.abs(distance-.8)<.01;
    }

    private void holdWorldDirection(Direction direction,boolean pressureBank) {
        double yaw=Math.toRadians(services.rotation().yaw());
        double forward=-Math.sin(yaw)*direction.dx()+Math.cos(yaw)*direction.dz();
        double right=-Math.cos(yaw)*direction.dx()-Math.sin(yaw)*direction.dz();
        services.input().setForwardHeld(forward>.5 || pressureBank);
        services.input().setBackHeld(forward<-.5);
        services.input().setRightHeld(right>.5);services.input().setLeftHeld(right<-.5);
    }

    private void holdContact(Vec3 actor,double progress,double forwardProgress) {
        // A single native400 key pulse already carries momentum. Let that observed
        // motion coast before correcting again; immediate opposite pulses otherwise
        // create a permanent two-tick oscillation at the uncapped final stream.
        if(corridorFocus&&(Math.abs(progress)>.05||Math.abs(forwardProgress)>.05))return;
        double yaw=Math.toRadians(services.rotation().yaw());
        double fx=-Math.sin(yaw),fz=Math.cos(yaw),rx=-Math.cos(yaw),rz=-Math.sin(yaw);
        double error=(goal.x()-actor.x())*end.inward.dx()+(goal.z()-actor.z())*end.inward.dz();
        double lateral=Math.max(-2,Math.min(2,error*4-progress*2));
        double forwardError=(goal.x()-actor.x())*heading.dx()+(goal.z()-actor.z())*heading.dz();
        double pressure=corridorFocus?Math.max(-2,Math.min(2,forwardError*4-forwardProgress*2)):1;
        double dx=heading.dx()*pressure+end.inward.dx()*lateral,dz=heading.dz()*pressure+end.inward.dz()*lateral;
        int bestForward=0,bestRight=0;double score=corridorFocus?0:Double.NEGATIVE_INFINITY;
        for(int forward=-1;forward<=1;forward++)for(int right=-1;right<=1;right++) {
            if(forward==0&&right==0)continue;
            double scale=Math.hypot(forward,right),x=(fx*forward+rx*right)/scale,z=(fz*forward+rz*right)/scale;
            if(!corridorFocus&&x*heading.dx()+z*heading.dz()<.1)continue;
            double candidate=x*dx+z*dz-(corridorFocus?.30:0);if(candidate>score){score=candidate;bestForward=forward;bestRight=right;}
        }
        services.input().setForwardHeld(bestForward>0);services.input().setBackHeld(bestForward<0);
        services.input().setRightHeld(bestRight>0);services.input().setLeftHeld(bestRight<0);
    }

    /** Passive exact maintenance checkpoint. No services, geometry, clocks or planner callbacks run. */
    public dev.veltraph.client.farming.module.FarmMaintenanceState maintenanceState() {
        return dev.veltraph.client.farming.module.FarmMaintenanceState.of(this,
                "services", services,
                "completed", completed,
                "observedStrip", observedStrip,
                "state", state,
                "end", end,
                "heading", heading,
                "goal", goal,
                "target", target,
                "point", point,
                "previous", previous,
                "corridorFocus", corridorFocus,
                "crossingWetCap", crossingWetCap,
                "dryTicks", dryTicks,
                "ticks", ticks,
                "blocker", blocker);
    }
}
