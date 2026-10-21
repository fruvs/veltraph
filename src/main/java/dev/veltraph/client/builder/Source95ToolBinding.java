package dev.veltraph.client.builder;
import com.google.gson.*;
import dev.veltraph.client.world.BlockPos;
import java.util.*;
/** Explicit private output footprint; the original native row expansion is not inferred. */
public record Source95ToolBinding(String toolItem,String toolName,String targetBlock,long cooldownMillis,long acknowledgementTimeoutMillis,List<Output> outputs) {
 public record Output(BlockPos offset,String before,String after) {}
 public Source95ToolBinding {
  outputs=List.copyOf(outputs);id(toolItem);id(targetBlock);
  if(toolName.isBlank()||toolName.length()>256||targetBlock.equals("minecraft:air"))throw new IllegalArgumentException("Exact named tool and non-air target required");
  if(cooldownMillis<1||cooldownMillis>30000||acknowledgementTimeoutMillis<1||acknowledgementTimeoutMillis>30000)throw new IllegalArgumentException("Explicit bounded tool timing required");
  // This adapter accepts a declared multi-cell footprint, not an invented single-cell source equivalent.
  if(outputs.size()<2||outputs.size()>4096)throw new IllegalArgumentException("Declared multi-cell output footprint requires 2..4096 cells");
  var unique=new HashSet<BlockPos>();for(var o:outputs){id(o.before());id(o.after());if(o.before().equals(o.after())||!unique.add(o.offset())||Math.abs((long)o.offset().x())>95||Math.abs((long)o.offset().z())>287||Math.abs((long)o.offset().y())>16)throw new IllegalArgumentException("Distinct changed, bounded output cells required");}
 }
 private static void id(String s){if(s==null||!s.matches("[a-z0-9_.-]+:[a-z0-9_/.-]+"))throw new IllegalArgumentException("Registry id required");}
 public static Source95ToolBinding decode(String json){
  if(json==null||json.isBlank())throw new IllegalArgumentException("Bind BUILDER_GENERATED_SOURCE95_PREPARATION_OFF_TOOL_BINDING; native tool row geometry remains unresolved");
  if(json.length()>524288)throw new IllegalArgumentException("Oversized source95 tool footprint");
  try{var o=JsonParser.parseString(json).getAsJsonObject();var cells=new ArrayList<Output>();for(var e:o.getAsJsonArray("outputs")){var c=e.getAsJsonObject();var a=c.getAsJsonArray("offset");if(a.size()!=3)throw new IllegalArgumentException("Three integral offset coordinates required");cells.add(new Output(new BlockPos(a.get(0).getAsBigDecimal().intValueExact(),a.get(1).getAsBigDecimal().intValueExact(),a.get(2).getAsBigDecimal().intValueExact()),c.get("before").getAsString(),c.get("after").getAsString()));}
   return new Source95ToolBinding(o.get("toolItem").getAsString(),o.get("toolName").getAsString(),o.get("targetBlock").getAsString(),o.get("cooldownMillis").getAsBigDecimal().longValueExact(),o.get("acknowledgementTimeoutMillis").getAsBigDecimal().longValueExact(),cells);
  }catch(RuntimeException invalid){throw new IllegalArgumentException("Invalid source95 preparation-off tool contract: "+invalid.getMessage(),invalid);}
 }
 public List<Output> at(BlockPos anchor,BlockPos direction){
  if(direction==null||direction.y()!=0||Math.abs(direction.x())+Math.abs(direction.z())!=1)throw new IllegalArgumentException("Horizontal source tool direction required");
  // Original descriptor (-1,0,0); rotate the supplied source-local offsets together with it.
  return outputs.stream().map(o->{var p=o.offset();return new Output(anchor.add(-direction.x()*p.x()+direction.z()*p.z(),p.y(),-direction.z()*p.x()-direction.x()*p.z()),o.before(),o.after());}).toList();
 }
}
