package dev.veltraph.client.navigation.mc;
import dev.veltraph.client.navigation.*;
import dev.veltraph.client.world.Vec3;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.*;
/** Native loaded body/full actual footprint, with the complete official collision-query halo. */
final class McNativeFlightGeometry {
 private McNativeFlightGeometry(){}
 static final class Budget {
  private final long began=System.nanoTime();private int cells;
  boolean spend(int amount){if(amount<0||amount>4096-cells)return false;cells+=amount;long elapsed=System.nanoTime()-began;return elapsed>=0&&elapsed<=50_000_000L;}
 }
 /** Created anew after each publication's final provider callback; never retained across publications.
  * Overlapping reads may share a native state/shape only inside this callback-free single-thread leaf. */
 static final class ReadView {
  final Minecraft client;final Budget budget;private final Object player,world,connection;
  private final CollisionContext context;private final Map<BlockPos,BlockState> states=new HashMap<>();
  private final Map<BlockPos,VoxelShape> shapes=new HashMap<>();private boolean refused;private String reason="";
  ReadView(Minecraft client,Budget budget){this.client=client;this.budget=budget;player=client.player;world=client.level;connection=client.getConnection();context=client.player==null?null:CollisionContext.of(client.player);}
  boolean current(){return !refused&&client.isSameThread()&&client.player==player&&client.level==world&&client.getConnection()==connection&&client.player!=null&&client.level!=null&&context!=null&&(budget==null||budget.spend(0));}
  BlockState state(BlockPos position){
   if(!current())return null;var cached=states.get(position);if(cached!=null)return cached;
   if(!client.level.getChunkSource().hasChunk(position.getX()>>4,position.getZ()>>4)){refused=true;reason="UNLOADED_COLLISION_DOMAIN";return null;}
   if(states.size()>=4096||budget!=null&&!budget.spend(1)){refused=true;reason="QUERY_BUDGET";return null;}
   var value=client.level.getBlockState(position);states.put(position,value);return value;
  }
  String refusalReason(){return reason;}
  boolean refuse(String cause){refused=true;reason=cause;return false;}
  VoxelShape shape(BlockPos position,BlockState state){
   if(!current())return null;var cached=shapes.get(position);if(cached!=null)return cached;
   var value=state.getCollisionShape(client.level,position,context);if(!current())return null;shapes.put(position,value);return value;
  }
 }
 static double radius(Minecraft c){return c.player==null?Double.NaN:c.player.getDimensions(net.minecraft.world.entity.Pose.STANDING).width()/2.0;}
 static boolean body(Minecraft c,McNavigator terrain,Vec3 point,double extra){return body(c,terrain,point,extra,null);}
 static boolean body(Minecraft c,McNavigator terrain,Vec3 point,double extra,Budget budget){return bodyIn(c,point,extra,new ReadView(c,budget));}
 static boolean bodyIn(Minecraft c,Vec3 point,double extra,ReadView view){
  double r=radius(c);if(!Double.isFinite(r)||r<=0||r>=2||!SharedMovementPolicy.finite(point)||c.player==null)return false;
  double h=c.player.getDimensions(net.minecraft.world.entity.Pose.STANDING).height();
  return boxClear(c,new AABB(point.x()-r-extra,point.y()+.001,point.z()-r-extra,point.x()+r+extra,point.y()+h,point.z()+r+extra),view);
 }
 static boolean standing(Minecraft c,McNavigator terrain,Vec3 point){return standing(c,terrain,point,null);}
 static boolean standing(Minecraft c,McNavigator terrain,Vec3 point,Budget budget){return standingIn(c,point,new ReadView(c,budget));}
 static boolean standingIn(Minecraft c,Vec3 point,ReadView view){
  double r=radius(c);return SharedMovementPolicy.finite(point)&&bodyIn(c,point,0,view)
   &&supportIn(c,new FlightFootprintCoverage.Face(point.x()-r,point.x()+r,point.z()-r,point.z()+r),point.y(),view);
 }
 static boolean support(Minecraft c,FlightFootprintCoverage.Face footprint,double y){return support(c,footprint,y,null);}
 static boolean support(Minecraft c,FlightFootprintCoverage.Face footprint,double y,Budget budget){return supportIn(c,footprint,y,new ReadView(c,budget));}
 static boolean supportIn(Minecraft c,FlightFootprintCoverage.Face footprint,double y,ReadView view){
  if(c.player==null||c.level==null||!Double.isFinite(y)||!Double.isFinite(footprint.minX())||!Double.isFinite(footprint.maxX())
   ||!Double.isFinite(footprint.minZ())||!Double.isFinite(footprint.maxZ())||footprint.maxX()-footprint.minX()>16||footprint.maxZ()-footprint.minZ()>16)return false;
  int low=(int)Math.floor(y-1.001),high=(int)Math.floor(y);var faces=new ArrayList<FlightFootprintCoverage.Face>();int queried=0;
  for(int x=(int)Math.floor(footprint.minX());x<=Math.floor(footprint.maxX());x++)for(int z=(int)Math.floor(footprint.minZ());z<=Math.floor(footprint.maxZ());z++)for(int by=low;by<=high;by++){
   if(++queried>1024)return false;var pos=new BlockPos(x,by,z);var state=view.state(pos);if(state==null)return false;if(dangerous(state))continue;
   var shape=view.shape(pos,state);if(shape==null)return false;
   for(var b:shape.toAabbs())if(Math.abs(by+b.maxY-y)<=.001){if(faces.size()>=512)return false;faces.add(new FlightFootprintCoverage.Face(x+b.minX,x+b.maxX,z+b.minZ,z+b.maxZ));}
  }
  return FlightFootprintCoverage.covers(footprint,faces)&&view.current();
 }
 static boolean swept(Minecraft c,McNavigator terrain,Vec3 a,Vec3 b,double extra){return swept(c,terrain,a,b,extra,null);}
 static boolean swept(Minecraft c,McNavigator terrain,Vec3 a,Vec3 b,double extra,Budget budget){return sweptIn(c,a,b,extra,new ReadView(c,budget));}
 static boolean sweptIn(Minecraft c,Vec3 a,Vec3 b,double extra,ReadView view){
  if(!SharedMovementPolicy.finite(a)||!SharedMovementPolicy.finite(b)||a.distanceTo(b)>512)return false;
  double r=radius(c)+extra,h=c.player.getDimensions(net.minecraft.world.entity.Pose.STANDING).height();
  return boxClear(c,new AABB(Math.min(a.x(),b.x())-r,Math.min(a.y(),b.y())+.001,Math.min(a.z(),b.z())-r,
    Math.max(a.x(),b.x())+r,Math.max(a.y(),b.y())+h,Math.max(a.z(),b.z())+r),view);
 }
 static boolean envelope(Minecraft c,FlightMotionEnvelope.Bounds motion,Vec3 goal,Budget budget){return envelopeIn(c,motion,goal,new ReadView(c,budget));}
 static boolean envelopeIn(Minecraft c,FlightMotionEnvelope.Bounds motion,Vec3 goal,ReadView view){
  if(c.player==null||c.level==null||motion==null||!SharedMovementPolicy.finite(goal))return false;
  double r=radius(c),h=c.player.getDimensions(net.minecraft.world.entity.Pose.STANDING).height();if(!Double.isFinite(r)||r<=0||r>=2||!Double.isFinite(h)||h<=0||h>=4)return false;
  double minY=motion.min().y(),floor=c.player.onGround()?c.player.getY():goal.y();
  var footprint=new FlightFootprintCoverage.Face(motion.min().x()-r,motion.max().x()+r,motion.min().z()-r,motion.max().z()+r);
  if(minY<floor){if(!supportIn(c,footprint,floor,view))return false;minY=floor;}
  return boxClear(c,new AABB(footprint.minX(),minY+.001,footprint.minZ(),footprint.maxX(),motion.max().y()+h,footprint.maxZ()),view);
 }
 /** Exact shape intersection over ALL loaded native Cursor3D cells, including overhang neighbors.
  * Entity collisions remain native. NoCollision's silent skip of missing chunks is never used. */
 private static boolean boxClear(Minecraft c,AABB box,ReadView view){
  if(c.player==null||c.level==null||box.getXsize()>16||box.getYsize()>16||box.getZsize()>16)return false;
  var domain=FlightCollisionDomain.of(box.minX,box.minY,box.minZ,box.maxX,box.maxY,box.maxZ);
  if(domain==null||domain.cells()>4096||!view.current())return false;var body=Shapes.create(box);
  for(int x=domain.minX();x<=domain.maxX();x++)for(int y=domain.minY();y<=domain.maxY();y++)for(int z=domain.minZ();z<=domain.maxZ();z++){
   var pos=new BlockPos(x,y,z);var state=view.state(pos);if(state==null)return false;
   // The halo can contain harmless hazards outside the body, but never an unobserved chunk.
   if(dangerous(state)&&box.intersects(x,y,z,x+1,y+1,z+1))return false;
   var shape=view.shape(pos,state);if(shape==null)return false;
   if(!shape.isEmpty()&&Shapes.joinIsNotEmpty(shape.move(x,y,z),body,BooleanOp.AND))return false;
  }
  for(var collision:c.level.getEntityCollisions(c.player,box))if(Shapes.joinIsNotEmpty(collision,body,BooleanOp.AND))return false;
  if(!c.level.noBorderCollision(c.player,box))return view.refuse("WORLD_BORDER_COLLISION");
  return view.current();
 }
 /** Unproved future descending contexts are explicitly unavailable to this flight contract. */
 private static boolean contextDependent(BlockState s){return s.is(Blocks.SCAFFOLDING)||s.is(Blocks.POWDER_SNOW)||!s.getFluidState().isEmpty();}
 private static boolean dangerous(BlockState s){return contextDependent(s)||s.is(Blocks.FIRE)||s.is(Blocks.SOUL_FIRE)||s.is(Blocks.COBWEB)||s.is(Blocks.SWEET_BERRY_BUSH)||s.is(Blocks.MAGMA_BLOCK)||s.is(Blocks.CACTUS)||s.is(Blocks.CAMPFIRE)||s.is(Blocks.SOUL_CAMPFIRE);}
}
