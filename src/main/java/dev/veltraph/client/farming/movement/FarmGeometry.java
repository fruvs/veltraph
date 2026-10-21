package dev.veltraph.client.farming.movement;

import dev.veltraph.client.config.ConfigService;
import dev.veltraph.client.config.FarmingConfig;
import dev.veltraph.client.service.PlayerService;
import dev.veltraph.client.service.WorldService;
import dev.veltraph.client.world.BlockPos;
import dev.veltraph.client.world.Direction;
import dev.veltraph.client.world.Vec3;

/**
 * Crop/farm-aware geometry for the S-shape row engine, translated from the recovered
 * {@code MovementDirection} predicates ({@code reports/_spec_parts/FarmingForwardExecutor.md} /
 * {@code MovementDirectionDecoded.md}): <i>near-middle</i> (fractional block radius {@code 0.21}, or
 * {@code 0.3} when the higher-corner branch applies), <i>block-in-front</i>, next-step validity, and
 * <i>right-then-left</i> movable-direction probing.
 *
 * <p>Runtime geometry accounts for:
 * <ul>
 *   <li><b>Uses the macro-owned heading</b> (set by the engine), never the live {@code player.facing()},
 *       so the geometry stays consistent while the camera pans (goal Target 3 / root cause #4).</li>
 *   <li><b>Recovered row Y</b> ({@code floor(Y + 0.15)}) places the body above farmland and soul
 *       sand, whose collision tops sit below the next integer Y. A solid-floor check alone did
 *       not fix the support block being mistaken for the body.</li>
 *   <li><b>Farm-aware support</b> ({@link WorldService#isSolidFloor}) and loaded body/head/floor
 *       probes preserve conservative movement clearance.</li>
 *   <li><b>Ground-crop sightline</b> enters the nearest visible wheat/carrot/potato/wart hitbox.
 *       See {@code docs/FARMING_HARVEST_SOURCE_AUDIT.md} for actual removal/yield evidence.</li>
 * </ul>
 *
 * <p>Pure (service interfaces + value types only), so it is unit-testable with fakes.
 */
public final class FarmGeometry implements SShapeGeometry {

    private final ConfigService config;
    private final PlayerService player;
    private final WorldService world;

    /** Macro-owned heading: the cardinal the player faces toward the crop wall. Set by the engine. */
    private Direction heading = Direction.NORTH;
    private String selectedCrop;
    private FarmCollisionProbe collision;
    private String normalProfileRefusal="normal profile not observed";

    public FarmGeometry(ConfigService config, PlayerService player, WorldService world) {
        this.config = config;
        this.player = player;
        this.world = world;
    }

    @Override
    public void setHeading(Direction heading) {
        this.heading = heading;
    }

    @Override
    public Direction heading() {
        return heading;
    }

    /** Set by the crop owner on enable; unbound geometry tests may inspect any ground crop. */
    public void setSelectedCrop(String blockId) { selectedCrop = blockId; }
    public void setCollisionProbe(FarmCollisionProbe collision) { this.collision=collision; }
    @Override public float eyeHeight(){return collision==null?1.62f:collision.eyeHeight();}

    /** Loaded supplement only for a wheat column beyond the actual primary-lane native reach. */
    @Override public SupplementalWaterCoverage.Plan supplementalCoveragePlan(TurnDirection sweep) {
        if((sweep!=TurnDirection.LEFT&&sweep!=TurnDirection.RIGHT)||collision==null||!"minecraft:wheat".equals(selectedCrop)||!player.onGround())return null;
        double reach=player.blockInteractionRange();
        if(!Double.isFinite(reach)||reach<=0||reach>64)return null;
        Vec3 primary=rowEndCenter();BlockPos base=primary.toBlockPos();Direction inward=lateral(sweep.opposite());
        Vec3 nextPrimary=rowTransitionTarget(sweep);
        if(nextPrimary==null)return null;
        double nextAdvance=(nextPrimary.x()-primary.x())*heading.dx()+(nextPrimary.z()-primary.z())*heading.dz();
        if(!Double.isFinite(nextAdvance)||nextAdvance<1||nextAdvance>16)return null;
        // Never borrow a channel belonging to a later ordinary lane; that lane has its own reach.
        int lastDistance=Math.min(8,(int)Math.ceil(nextAdvance)-1);
        for(int distance=1;distance<=lastDistance;distance++) {
            BlockPos portal=base.offset(heading,distance),cap=portal.offset(inward),near=cap.offset(inward);
            if(!world.isLoaded(portal)||!world.isLoaded(cap)||!world.isLoaded(near))return null;
            if(!supplementDryPoint(new Vec3(portal.x()+.5,primary.y(),portal.z()+.5)))return null;
            if(!world.isBlock(cap,"minecraft:dirt")||!world.isWater(near))continue;
            // The nearest forward crop face remains out of reach even at the safe .2 path edge.
            if(distance+.5-.2<=reach)continue;
            int length=0;boolean matureRemaining=false;
            for(;length<128;length++) {
                BlockPos cell=near.offset(inward,length);
                if(!world.isLoaded(cell))return null;
                if(!world.isBlock(cell,"minecraft:water"))break;
                if(!supplementWaterCell(cell,primary.y()))return null;
                BlockPos crop=cell.offset(heading).up();
                matureRemaining|=world.isBlock(crop,"minecraft:wheat")&&collision.matureCrop(crop);
            }
            if(length<2||length==128||!matureRemaining)return null;
            BlockPos after=near.offset(inward,length);
            if(!world.isLoaded(after)||!(world.isBlock(after,"minecraft:dirt")||world.isBlock(after,"minecraft:grass_block")))return null;
            Vec3 approach=new Vec3(portal.x()+.5,primary.y(),portal.z()+.5);
            Vec3 capFeet=new Vec3(cap.x()+.5,primary.y()+1,cap.z()+.5);
            Vec3 nearFeet=new Vec3(near.x()+.5,primary.y(),near.z()+.5);
            BlockPos last=near.offset(inward,length-1);Vec3 far=new Vec3(last.x()+.5,primary.y(),last.z()+.5);
            var plan=new SupplementalWaterCoverage.Plan(primary,approach,capFeet,nearFeet,far,inward,heading);
            return supplementalCoverageCurrent(plan)?plan:null;
        }
        return null;
    }
    @Override public boolean supplementalCoverageBodyClear(Vec3 feet,boolean allowWater) {
        return collision!=null&&collision.loaded(feet)&&collision.clear(feet,allowWater);
    }
    private boolean supplementDryPoint(Vec3 feet) {
        BlockPos body=feet.toBlockPos();
        if(!world.isLoaded(body.down())||!world.isBlock(body.down(),"minecraft:dirt")
                ||!supplementalCoverageBodyClear(feet,false)||!supplementStandingClear(feet,false))return false;
        var support=collision.supportY(feet.x(),feet.z(),feet.y()+.02,1);
        return support.isPresent()&&Math.abs(support.getAsDouble()-feet.y())<.01;
    }
    private boolean supplementWaterCell(BlockPos cell,double footY) {
        if(!world.isLoaded(cell.down())||!world.isBlock(cell,"minecraft:water")
                ||!world.isBlock(cell.down(),"minecraft:dirt")
                ||!world.isLoaded(cell.up())||!world.isLoaded(cell.up(2))
                ||!isAir(cell.up()))return false;
        // Y+2 remains loaded; actual native body/collision checks below decide whether its shape
        // intrudes into the standing envelope. A ceiling above that envelope need not be AIR.
        BlockPos front=cell.offset(heading),back=cell.offset(heading.opposite());
        if(!world.isLoaded(front)||!world.isLoaded(back)||!world.isLoaded(front.up())
                ||!world.isBlock(front,"minecraft:farmland")||!world.isBlock(back,"minecraft:farmland"))return false;
        String crop=world.blockId(front.up());
        if(!crop.equals("minecraft:wheat")&&!isAir(front.up()))return false;
        Vec3 feet=new Vec3(cell.x()+.5,footY,cell.z()+.5);
        if(!supplementalCoverageBodyClear(feet,true)||!supplementStandingClear(feet,true))return false;
        var support=collision.supportY(feet.x(),feet.z(),footY+.02,1);
        return support.isPresent()&&Math.abs(support.getAsDouble()-footY)<.01;
    }
    /** Reproves the entire exact original column and cap; unknown fluid/support never inherits dry ground. */
    @Override public boolean supplementalCoverageCurrent(SupplementalWaterCoverage.Plan plan) {
        if(plan==null||collision==null||!"minecraft:wheat".equals(selectedCrop)||heading!=plan.harvestHeading())return false;
        double y=plan.primaryEnd().y();BlockPos cap=plan.capFeet().toBlockPos().down();
        if(!world.isLoaded(cap)||!world.isBlock(cap,"minecraft:dirt")
                ||!supplementalCoverageBodyClear(plan.capFeet(),false)||!supplementStandingClear(plan.capFeet(),false))return false;
        var top=collision.supportY(plan.capFeet().x(),plan.capFeet().z(),plan.capFeet().y()+.02,1);
        if(top.isEmpty()||Math.abs(top.getAsDouble()-plan.capFeet().y())>.01)return false;
        int dry=Math.max(1,(int)Math.ceil(plan.primaryEnd().horizontalDistanceTo(plan.approachFeet())*10));
        for(int i=0;i<=dry;i++) {
            double t=(double)i/dry;
            if(!supplementDryPoint(new Vec3(plan.primaryEnd().x()+(plan.approachFeet().x()-plan.primaryEnd().x())*t,y,
                    plan.primaryEnd().z()+(plan.approachFeet().z()-plan.primaryEnd().z())*t)))return false;
        }
        int water=(int)Math.round(plan.channelNear().horizontalDistanceTo(plan.channelFar()));
        if(water<1||water>127)return false;
        BlockPos near=plan.channelNear().toBlockPos();
        for(int i=0;i<=water;i++)if(!supplementWaterCell(near.offset(plan.inward(),i),y))return false;
        // All body/head space above the only nonfarmland crossing is loaded and clear.
        // This is geometry for a genuine native jump/swim; it is never a position or physics write.
        for(int i=0;i<=20;i++) {
            double t=i/20.0;
            double x=plan.approachFeet().x()+(plan.channelNear().x()-plan.approachFeet().x())*t;
            double z=plan.approachFeet().z()+(plan.channelNear().z()-plan.approachFeet().z())*t;
            for(double rise:new double[]{1.01,1.3,2.3})
                if(!supplementalCoverageBodyClear(new Vec3(x,y+rise,z),false)||!supplementStandingClear(new Vec3(x,y+rise,z),false))return false;
        }
        return true;
    }

    /** The captured normal standing .6 x 1.8 body must fit independently of a crouched live pose. */
    private boolean supplementStandingClear(Vec3 feet,boolean allowWater) {
        for(int x=(int)Math.floor(feet.x()-.3);x<=(int)Math.floor(feet.x()+.3-1e-7);x++)
            for(int y=(int)Math.floor(feet.y()+.001);y<=(int)Math.floor(feet.y()+1.8-1e-7);y++)
                for(int z=(int)Math.floor(feet.z()-.3);z<=(int)Math.floor(feet.z()+.3-1e-7);z++) {
                    BlockPos cell=new BlockPos(x,y,z);
                    if(!world.isLoaded(cell)||(!isAir(cell)&&!(allowWater&&world.isBlock(cell,"minecraft:water"))))return false;
                }
        return true;
    }

    @Override public Vec3 safeDropTarget(TurnDirection side) {
        if(collision==null)return null;
        Vec3 p=player.position();Direction direction=lateral(side);
        double x=p.x()+direction.dx()*.9,z=p.z()+direction.dz()*.9;
        Vec3 direct=validatedDropTarget(p,x,z);
        if(direct!=null)return direct;
        // Original thin ladder/guide apertures need a centered perpendicular body footprint.
        // This is a validated navigator destination; normal movement still proves its arrival.
        return validatedDropTarget(p,direction.dx()==0?Math.floor(p.x())+.5:x,
                direction.dz()==0?Math.floor(p.z())+.5:z);
    }
    private Vec3 validatedDropTarget(Vec3 p,double x,double z){
        var floor=collision.supportY(x,z,p.y()-.1,3);
        if(floor.isEmpty()||p.y()-floor.getAsDouble()<=.6||p.y()-floor.getAsDouble()>3.001)return null;
        Vec3 landing=new Vec3(x,floor.getAsDouble(),z);
        Vec3 edge=new Vec3(x,p.y(),z);
        if(!collision.loaded(edge)||!collision.clear(edge,true)||!dropLandingValid(landing))return null;
        BlockPos body=new Vec3(x,landing.y()+.15,z).toBlockPos();
        return cropBedAhead(body)?landing:null;
    }
    @Override public boolean dropLandingValid(Vec3 target) {
        if(collision==null||!collision.loaded(target)||!collision.clear(target,true)
                ||world.isWater(new Vec3(target.x(),target.y()+1.62,target.z()).toBlockPos()))return false;
        var floor=collision.supportY(target.x(),target.z(),target.y()+.02,1);
        return floor.isPresent()&&Math.abs(floor.getAsDouble()-target.y())<.03;
    }

    public Direction lateral(TurnDirection side) {
        return side == TurnDirection.RIGHT ? heading.rotateYClockwise() : heading.rotateYCounterclockwise();
    }

    /** Player is near the centre of its block (within the near-middle radius on both X and Z). */
    public boolean nearMiddle() {
        double radius = config.get(FarmingConfig.ALLOW_IRREGULAR_CORNERS)
                ? config.get(FarmingConfig.HIGHER_CORNER_BLOCK_RADIUS)
                : config.get(FarmingConfig.NEAR_MIDDLE_RADIUS);
        Vec3 p = player.position();
        double fx = frac(p.x());
        double fz = frac(p.z());
        return Math.abs(fx - 0.5) <= radius && Math.abs(fz - 0.5) <= radius;
    }

    /** Is there a (non-air) block directly in front along the heading — a crop/wall to break? */
    public boolean blockInFront() {
        BlockPos front = rowBodyPos().offset(heading);
        return world.isLoaded(front) && !isAir(front);
    }

    /** Can strafe one block to {@code side} (lateral neighbour enterable + has a floor). */
    public boolean canStrafe(TurnDirection side) {
        return laneOpen(lateral(side));
    }

    /** Can step forward one block along the heading into the next row. */
    public boolean canStepForward() {
        return laneOpen(heading);
    }

    /** Probe RIGHT then LEFT (recovered order); {@link TurnDirection#FORWARD} if neither is movable. */
    public TurnDirection movableLateral() {
        if (canStrafe(TurnDirection.RIGHT)) {
            return TurnDirection.RIGHT;
        }
        if (canStrafe(TurnDirection.LEFT)) {
            return TurnDirection.LEFT;
        }
        return TurnDirection.FORWARD;
    }

    /**
     * Aim inside the nearest loaded ground crop, rather than above its entire selection shape.
     * The source executor begins with the adjacent crop. Looking farther is useful only once that
     * crop is gone; keep the scan bounded and stop at unloaded space or a physical obstruction.
     * Generic/non-ground-crop layouts retain their configured sightline.
     */
    public Vec3 targetVec() {
        Vec3 crop = groundCropTarget();
        if (crop != null) return crop;
        return CropSightline.target(rowBodyPos(), heading,
                config.get(FarmingConfig.CROP_SIGHTLINE_DISTANCE),
                config.get(FarmingConfig.CROP_AIM_HEIGHT_OFFSET));
    }

    @Override public boolean supplementalWaterAt(Vec3 feet) {
        return collision!=null&&collision.loaded(feet)&&collision.playerInWater();
    }

    @Override public boolean requiresNormalFixedRowView(){
        return collision!=null&&("minecraft:wheat".equals(selectedCrop)||"minecraft:carrots".equals(selectedCrop)
            ||"minecraft:potatoes".equals(selectedCrop)||"minecraft:nether_wart".equals(selectedCrop));
    }
    @Override public String normalFixedRowViewRefusal(){return normalProfileRefusal;}
    @Override public FixedRowView initialFixedRowView() {
        normalProfileRefusal="normal profile unavailable for selected crop="+selectedCrop;
        if (!requiresNormalFixedRowView() || collision == null) return null;
        Vec3 feet=player.position();
        normalProfileRefusal="crop="+selectedCrop+" observedFeet="+feet+" startup requires current dry grounded support";
        if(feet==null||!Double.isFinite(feet.x())||!Double.isFinite(feet.y())||!Double.isFinite(feet.z())
                ||!player.onGround()||!collision.loaded(feet)||!collision.clear(feet,false))return null;
        var support=collision.supportY(feet.x(),feet.z(),feet.y()+.05,1);
        if(support.isEmpty()||!Double.isFinite(support.getAsDouble())
                ||Math.abs(support.getAsDouble()-feet.y())>.03)return null;
        BlockPos base=new Vec3(feet.x(),feet.y()+.15,feet.z()).toBlockPos();double eye=feet.y()+eyeHeight();
        normalProfileRefusal="crop="+selectedCrop+" eyeY="+eye+" no loaded mature outline on selected soil";
        int nearest=0,furthest=0;Integer cropY=null;double outlineHeight=Double.POSITIVE_INFINITY;
        for(int distance=1;distance<=4;distance++) {
            BlockPos front=base.offset(heading,distance);
            if(!world.isLoaded(front))break;
            for(int rise=0;rise<=2;rise++) {
                BlockPos crop=front.up(rise);
                if(!world.isLoaded(crop)||!world.isLoaded(crop.down()))continue;
                if(!world.isBlock(crop,selectedCrop)||!collision.matureCrop(crop)
                        ||!isRunSupport(crop.down()))continue;
                var height=collision.matureOutlineHeight(crop);
                if(height.isEmpty()||!Double.isFinite(height.getAsDouble())
                        ||height.getAsDouble()<=0||height.getAsDouble()>1){
                    normalProfileRefusal="crop="+selectedCrop+" eyeY="+eye+" cell="+crop+" native mature outline unavailable";return null;
                }
                if(cropY!=null&&cropY!=crop.y()){
                    normalProfileRefusal="crop="+selectedCrop+" eyeY="+eye+" mixed observed crop elevations";return null;
                }
                cropY=crop.y();if(nearest==0)nearest=distance;furthest=distance;outlineHeight=Math.min(outlineHeight,height.getAsDouble());
            }
            if(!world.isPassable(front)&&!isCropSupport(front))break;
        }
        if(cropY==null||furthest<1)return null;
        double forwardOffset=(feet.x()-(base.x()+.5))*heading.dx()+(feet.z()-(base.z()+.5))*heading.dz();
        var view=FixedRowView.forNormalCrop((float)heading.toYaw(),eye,cropY,nearest,furthest,outlineHeight,.2,forwardOffset);
        normalProfileRefusal=view==null?"crop="+selectedCrop+" eyeY="+eye+" cropY="+cropY
            +" nearest="+nearest+" furthest="+furthest+" outlineHeight="+outlineHeight
            +" forwardOffset="+forwardOffset+" referencePitch=3.0 margin=.2 does not intersect observed outline":"";
        return view;
    }

    @Override public boolean harvestTargetAvailable() { return groundCropTarget() != null; }
    @Override public boolean fixedViewHarvestTargetAvailable(){
        return collision!=null&&requiresNormalFixedRowView()&&collision.matureHarvestHit(selectedCrop);
    }

    private Vec3 groundCropTarget() {
        BlockPos base=rowBodyPos();double eyeY=player.position().y()+eyeHeight();
        for(int distance=1;distance<=4;distance++) {
            BlockPos front=base.offset(heading,distance);
            if(!world.isLoaded(front))break;
            for(int rise=0;rise<=2;rise++) {
                BlockPos crop=front.up(rise),support=crop.down();
                if(!world.isLoaded(crop)||!world.isLoaded(support))continue;
                String id=world.blockId(crop);double height=groundCropAimHeight(id);
                if(height<=0)continue;
                if(collision!=null&&!collision.matureCrop(crop))continue;
                if(rise>0&&(!isCropSupport(support)||eyeY<cropSupportTop(support)+.02))continue;
                if(selectedCrop!=null&&!selectedCrop.equals(id))return null;
                return new Vec3(crop.x()+.5,crop.y()+height,crop.z()+.5);
            }
            boolean through=world.isPassable(front);
            for(int rise=0;rise<=1&&!through;rise++) {
                BlockPos soil=front.up(rise);
                through=world.isLoaded(soil.up())&&isCropSupport(soil)
                        &&cropSupportTop(soil)<eyeY&&world.isPassable(soil.up());
            }
            if(!through)break;
        }
        return null;
    }
    private double cropSupportTop(BlockPos support) {
        return support.y()+(world.isBlock(support,"minecraft:soul_sand")?.875:.9375);
    }

    @Override public Vec3 rowEndCenter() {
        BlockPos body = rowBodyPos();
        return new Vec3(body.x() + 0.5, player.position().y(), body.z() + 0.5);
    }

    @Override public boolean requiresObservedRowDestination() { return true; }

    /**
     * Infer a lane from the actual end corridor, not a preset width or a timer. Every intermediate
     * cell must be loaded, dry, clear and supported. A destination must open into the return sweep
     * and face crop support. The latter survives harvesting, unlike counting remaining crops.
     * Sixteen cells bounds the local solve; the decoded geometry also has longer bounded scans.
     */
    @Override public Vec3 rowTransitionTarget(TurnDirection sweep) {
        BlockPos base = rowBodyPos();
        Direction returning = lateral(sweep.opposite());
        for (int distance = 1; distance <= 16; distance++) {
            BlockPos candidate = base.offset(heading, distance);
            if (!dryLaneOpen(candidate)) return null;
            BlockPos returnCell = candidate.offset(returning);
            if (dryLaneOpen(returnCell) && cropBedAhead(returnCell))
                return new Vec3(candidate.x() + 0.5, player.position().y(), candidate.z() + 0.5);
        }
        return null;
    }

    @Override public boolean initialCoverageEndProved(TurnDirection side){
        return initialCoverageEndProved(side,true);
    }
    @Override public boolean initialCoverageEndProved(TurnDirection side,boolean requiresForwardExit){
        BlockPos base=rowBodyPos(),edge=base.offset(lateral(side));
        for(int y=-1;y<=2;y++)if(!world.isLoaded(base.up(y))||!world.isLoaded(edge.up(y)))return false;
        if(collision==null||!collision.loaded(player.position())||!collision.clear(player.position(),false)
                ||collision.supportY(player.position().x(),player.position().z(),player.position().y()+.02,1).isEmpty()
                ||canStrafe(side)||!canStrafe(side.opposite()))return false;
        if(requiresForwardExit)return rowTransitionTarget(side)!=null;
        // A same-lane far reversal needs a known crop-bed boundary, not a nonexistent next-lane connector.
        // Crop support survives harvest; an arbitrary obstacle midway in the crop row must not count.
        boolean observedBed=false;
        for(int distance=1;distance<=4;distance++){
            BlockPos own=base.offset(heading,distance),outside=edge.offset(heading,distance);
            for(int y=-1;y<=1;y++)if(!world.isLoaded(own.up(y))||!world.isLoaded(outside.up(y)))return false;
            boolean cropSupport=isRunSupport(own.down())||isRunSupport(own)||isRunSupport(own.up());
            boolean continues=isRunSupport(outside.down())||isRunSupport(outside)||isRunSupport(outside.up());
            if(cropSupport){
                observedBed=true;
                if(continues)return false;
            }
        }
        return observedBed;
    }

    private boolean cropBedAhead(BlockPos base) {
        for (int distance = 1; distance <= 3; distance++) {
            BlockPos front = base.offset(heading, distance);
            if (!world.isLoaded(front) || !world.isLoaded(front.down())) return false;
            if (isRunSupport(front) || isRunSupport(front.down())
                    || world.isLoaded(front.up()) && isRunSupport(front.up())) return true;
            if (!world.isPassable(front)) return false;
        }
        return false;
    }

    private boolean isCropSupport(BlockPos pos) {
        return world.isBlock(pos, "minecraft:farmland") || world.isBlock(pos, "minecraft:soul_sand");
    }
    private boolean isRunSupport(BlockPos pos){
        return selectedCrop==null?isCropSupport(pos):world.isBlock(pos,
                selectedCrop.equals("minecraft:nether_wart")?"minecraft:soul_sand":"minecraft:farmland");
    }

    private boolean dryLaneOpen(BlockPos body) {
        return !world.isWater(body) && computeLaneOpen(body, body.up(), body.down());
    }

    private static double groundCropAimHeight(String id) {
        return switch (id) {
            case "minecraft:wheat", "minecraft:nether_wart" -> 0.45;
            case "minecraft:carrots", "minecraft:potatoes" -> 0.35;
            default -> -1;
        };
    }

    private double cropAimHeight(String id) {
        return selectedCrop == null || selectedCrop.equals(id) ? groundCropAimHeight(id) : -1;
    }

    /** Decoded GeometryEngine.getBlockPos7 :261–267 rounds Y - 0.2 + 0.35. */
    public BlockPos rowBodyPos() {
        Vec3 position = player.position();
        return new Vec3(position.x(), position.y() + 0.15, position.z()).toBlockPos();
    }

    /** A player could step one block in {@code dir}: head clear, body enterable, and a floor below. */
    private boolean laneOpen(Direction dir) {
        BlockPos base = rowBodyPos();
        BlockPos body = base.offset(dir);
        if(collision!=null){
            double x=body.x()+.5,z=body.z()+.5,y=player.position().y();
            var floor=collision.supportY(x,z,y+.6,2);
            if(floor.isEmpty()||y-floor.getAsDouble()>.6)return false;
            Vec3 feet=new Vec3(x,floor.getAsDouble(),z);
            return collision.clear(feet,true)&&!world.isWater(new Vec3(x,feet.y()+1.62,z).toBlockPos());
        }
        BlockPos head = body.up();
        BlockPos floor = body.down();
        boolean open = computeLaneOpen(body, head, floor);
        // VERBOSE: world probe — block ids + the passable/floor decision (only built when verbose).
        if (dev.veltraph.client.log.VeltraphLog.shouldLog(dev.veltraph.client.log.DebugLogMode.VERBOSE)) {
            dev.veltraph.client.log.VeltraphLog.verbose("FarmGeometry.laneOpen", String.format(
                    "%s open=%b body=%s(pass=%b) head=pass=%b floor=%s(solid=%b)", dir, open,
                    world.blockId(body), world.isPassable(body), world.isPassable(head),
                    world.blockId(floor), world.isSolidFloor(floor)));
        }
        return open;
    }

    private boolean computeLaneOpen(BlockPos body, BlockPos head, BlockPos floor) {
        if (!world.isLoaded(body) || !world.isLoaded(head) || !world.isLoaded(floor)) {
            return false;
        }
        // Crops are non-colliding, so the body/head being "passable" includes walking through a crop;
        // the floor must support standing (farm-aware).
        if (!world.isPassable(body) || !world.isPassable(head)) {
            return false;
        }
        if (world.isSolidFloor(floor)) {
            return true;
        }
        // Tolerate a single step-down corner only when irregular corners are permitted.
        return config.get(FarmingConfig.ALLOW_IRREGULAR_CORNERS) && world.isSolidFloor(floor.down());
    }

    private boolean isAir(BlockPos pos) {
        String id = world.blockId(pos);
        return id.isEmpty() || id.equals("minecraft:air") || id.equals("minecraft:cave_air")
                || id.equals("minecraft:void_air");
    }

    private static double frac(double v) {
        double f = v - Math.floor(v);
        return f;
    }

    /** Passive exact maintenance checkpoint. No services, geometry, clocks or planner callbacks run. */
    public dev.veltraph.client.farming.module.FarmMaintenanceState maintenanceState() {
        return dev.veltraph.client.farming.module.FarmMaintenanceState.of(this,
                "config", config,
                "player", player,
                "world", world,
                "heading", heading,
                "selectedCrop", selectedCrop,
                "normalProfileRefusal", normalProfileRefusal,
                "collision", collision);
    }
}
