package dev.veltraph.client.builder;
import com.google.gson.*;
import dev.veltraph.client.config.BuilderCropType;
import java.util.*;
/** Source provider/registry item are recovered; salted labels/cap/timing require an explicit observed contract. */
public record Source95AcquisitionBinding(BuilderCropType crop,String productName,String resourceComponents,Set<String> titles,Map<String,String> controls,long maxTotalCoins,long clickDelayMillis,long timeoutMillis) {
 public Source95AcquisitionBinding {
  Objects.requireNonNull(crop);Objects.requireNonNull(resourceComponents);if(resourceComponents.length()>16384||!resourceComponents.equals("@vanilla")&&!resourceComponents.startsWith("{"))throw new IllegalArgumentException("Explicit crop component identity required");titles=Set.copyOf(titles);controls=Map.copyOf(controls);if(controls.size()>6||controls.entrySet().stream().anyMatch(e->!Set.of("instant","one","fill","custom","stack","confirm").contains(e.getKey())||e.getValue().isBlank()||e.getValue().length()>256))throw new IllegalArgumentException("Explicit source strategy control labels required");
  if(!productName.matches("[\\p{L}\\p{N} '\\-]{1,128}")||titles.isEmpty()||titles.size()>12||!titles.contains(productName)||titles.stream().anyMatch(t->t.isBlank()||t.length()>256)||maxTotalCoins<1||maxTotalCoins>1_000_000_000_000L||clickDelayMillis<1||clickDelayMillis>5000||timeoutMillis<1||timeoutMillis>120000)throw new IllegalArgumentException("Exact source product/menu/cap/timing contract required");
 }
 public String item(){return Source95CropResource.item(crop);}
 public static Source95AcquisitionBinding decode(String json,BuilderCropType crop){
  if(json==null||json.isBlank())throw new IllegalArgumentException("Bind BUILDER_GENERATED_SOURCE95_CROP_ACQUISITION_BINDING: source requires128 newly acquired crop units, not an owned stack");
  if(json.length()>16384)throw new IllegalArgumentException("Oversized source95 acquisition binding");
  try{var p=JsonParser.parseString(json).getAsJsonObject().getAsJsonObject(crop.name());if(p==null)throw new IllegalArgumentException("No explicit product/menu/cap/timing binding for "+crop);var titles=new LinkedHashSet<String>();for(var e:p.getAsJsonArray("menuTitles"))if(!titles.add(e.getAsString()))throw new IllegalArgumentException("Duplicate acquisition menu title");var controls=new LinkedHashMap<String,String>();if(p.has("controls"))for(var e:p.getAsJsonObject("controls").entrySet())controls.put(e.getKey(),e.getValue().getAsString());return new Source95AcquisitionBinding(crop,p.get("productName").getAsString(),p.get("resourceComponents").getAsString(),titles,controls,p.get("maxTotalCoins").getAsBigDecimal().longValueExact(),p.get("clickDelayMillis").getAsBigDecimal().longValueExact(),p.get("timeoutMillis").getAsBigDecimal().longValueExact());}catch(RuntimeException invalid){throw new IllegalArgumentException("Unresolved source95 crop acquisition: "+invalid.getMessage(),invalid);}
 }
}
