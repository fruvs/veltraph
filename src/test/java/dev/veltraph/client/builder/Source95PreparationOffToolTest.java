package dev.veltraph.client.builder;
import dev.veltraph.client.config.*;
import dev.veltraph.client.world.BlockPos;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class Source95PreparationOffToolTest {
 static final String CONTRACT="{\"toolItem\":\"minecraft:blaze_rod\",\"toolName\":\"Blaze Rod\",\"targetBlock\":\"minecraft:stone\",\"cooldownMillis\":100,\"acknowledgementTimeoutMillis\":3000,\"outputs\":[{\"offset\":[0,0,0],\"before\":\"minecraft:stone\",\"after\":\"minecraft:dirt\"},{\"offset\":[1,0,0],\"before\":\"minecraft:stone\",\"after\":\"minecraft:dirt\"}]}";
 private Source95ToolRuntime.Facts f(boolean output,boolean receipt,boolean settled,boolean shifting,long now){return new Source95ToolRuntime.Facts(true,true,output,receipt,settled,true,true,true,true,shifting,now);}
 @Test void boundGraphPreservesPairOrderAndNativeBoundary() {
  var c=new MapConfigService();c.set(GeneratedBuilderConfig.PREPARATION,false);c.set(GeneratedBuilderConfig.SOURCE95_PREPARATION_OFF_TOOL_BINDING,CONTRACT);
  var p=SourceGeneratedPlans.source95(c);for(int i=0;i<p.processes().size();i++)if(p.processes().get(i).operation().name().equals("TOOL_USE")){assertEquals("source95 preparation-off clear",p.processes().get(i-1).source());assertTrue(p.processes().get(i).unresolved().isEmpty());}
  assertTrue(p.processes().stream().anyMatch(x->x.source().equals("source95 group6 chunk scan")&&x.unresolved().isPresent()));
  assertEquals(92,SourceGeneratedPlans.source92(c).processes().size());
 }
 @Test void predictionPartialReceiptsAndPendingSettlementNeverComplete() {
  var b=Source95ToolBinding.decode(CONTRACT);var r=new Source95ToolRuntime();assertEquals(Source95ToolRuntime.Action.SHIFT,r.tick(b,f(false,false,true,false,0)));
  assertEquals(Source95ToolRuntime.Action.USE,r.tick(b,f(false,false,true,true,1)));
  assertEquals(Source95ToolRuntime.Action.NONE,r.tick(b,f(true,false,true,false,101)));
  assertEquals(Source95ToolRuntime.Action.NONE,r.tick(b,f(true,true,false,false,102)));
  assertEquals(Source95ToolRuntime.Action.COMPLETE,r.tick(b,f(true,true,true,false,103)));
 }
 @Test void timeoutAndChangedPreconditionsRetainCheckpoint() {
  var b=Source95ToolBinding.decode(CONTRACT);var r=new Source95ToolRuntime();r.tick(b,f(false,false,true,true,0));assertEquals(Source95ToolRuntime.Action.STOP,r.tick(b,f(true,false,true,false,3001)));
  r=new Source95ToolRuntime();assertEquals(Source95ToolRuntime.Action.STOP,r.tick(b,new Source95ToolRuntime.Facts(true,false,false,false,true,true,true,true,true,true,0)));
 }
 @Test void acknowledgementsAreScopedToOwnerConnectionCellsAndActualAfterState() {
  Object connection=new Object(),other=new Object(),owner=new Object();var b=Source95ToolBinding.decode(CONTRACT);var cells=b.at(new BlockPos(0,70,0),new BlockPos(-1,0,0));
  BuilderToolOutputReceipts.received(connection,cells.getFirst().offset(),"minecraft:dirt");
  BuilderToolOutputReceipts.attach(connection,owner,cells.stream().map(Source95ToolBinding.Output::offset).collect(java.util.stream.Collectors.toSet()));
  try{assertFalse(BuilderToolOutputReceipts.matches(connection,owner,cells));BuilderToolOutputReceipts.received(other,cells.getFirst().offset(),"minecraft:dirt");assertFalse(BuilderToolOutputReceipts.matches(connection,owner,cells));
   BuilderToolOutputReceipts.received(connection,cells.getFirst().offset(),"minecraft:dirt");assertFalse(BuilderToolOutputReceipts.matches(connection,owner,cells));BuilderToolOutputReceipts.received(connection,cells.getLast().offset(),"minecraft:dirt");assertFalse(BuilderToolOutputReceipts.matches(connection,other,cells));assertTrue(BuilderToolOutputReceipts.matches(connection,owner,cells));
   BuilderToolOutputReceipts.received(connection,cells.getLast().offset(),"minecraft:stone");assertFalse(BuilderToolOutputReceipts.matches(connection,owner,cells));
  }finally{BuilderToolOutputReceipts.detach(connection,owner);}
 }
 @Test void explicitFootprintRotatesWithOriginalDescriptorAndRejectsFakeSingleton() {
  var b=Source95ToolBinding.decode(CONTRACT);assertEquals(new BlockPos(0,70,1),b.at(new BlockPos(0,70,0),new BlockPos(0,0,-1)).getLast().offset());
  assertThrows(IllegalArgumentException.class,()->new Source95ToolBinding(b.toolItem(),b.toolName(),b.targetBlock(),100,3000,java.util.List.of(b.outputs().getFirst())));
  assertThrows(IllegalArgumentException.class,()->Source95ToolBinding.decode(CONTRACT.replace("[1,0,0]","[0,0,0]")));
 }

 @Test void preparationOffHasRealToolSubprocessRatherThanGenericContract() {
  var c=new MapConfigService();c.set(GeneratedBuilderConfig.PREPARATION,false);
  var p=SourceGeneratedPlans.source95(c);
  var tools=p.processes().stream().filter(s->s.source().equals("source95 preparation-off tool")).toList();
  assertEquals(18,tools.size());assertEquals("TOOL_USE",tools.getFirst().operation().name());
  assertEquals(new BlockPos(93,0,1),tools.getFirst().first());assertEquals(new BlockPos(8,0,286),tools.getLast().first());
  assertEquals(new BlockPos(-1,0,0),tools.getFirst().direction());
  assertTrue(tools.getFirst().unresolved().orElseThrow().contains("PREPARATION_OFF_TOOL_BINDING"));
 }
}
