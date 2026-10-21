package dev.veltraph.client.builder;
import dev.veltraph.client.config.*;
import java.util.*;
/** Latches observed authorization changes, including supported per-key write ABA. Never grants authority from dirty bits. */
final class Source95AcquisitionPolicy {
 record Snapshot(boolean buy,boolean ironman,BuilderCropType crop,String contract,Map<String,Long> revisions){Snapshot{revisions=Map.copyOf(revisions);}}
 private final Source95AcquisitionBinding loaded;private Snapshot initial,last;private long generation;private boolean revoked;private long cap;private String reason="Ready";
 Source95AcquisitionPolicy(Source95AcquisitionBinding binding){loaded=binding;cap=binding.maxTotalCoins();}
 static Snapshot read(ConfigService config){var keys=List.<ConfigKey<?>>of(BuilderConfig.BUILDER_BUY_RESOURCES,GeneratedBuilderConfig.IRONMAN_MODE,BuilderConfig.BUILDER_CROP_TYPE,GeneratedBuilderConfig.SOURCE95_CROP_ACQUISITION_BINDING);var before=new LinkedHashMap<String,Long>();for(var k:keys)config.revision(k).ifPresent(v->before.put(k.name(),v));var s=new Snapshot(config.get(BuilderConfig.BUILDER_BUY_RESOURCES),config.get(GeneratedBuilderConfig.IRONMAN_MODE),config.get(BuilderConfig.BUILDER_CROP_TYPE),config.get(GeneratedBuilderConfig.SOURCE95_CROP_ACQUISITION_BINDING),before);for(var k:keys){var now=config.revision(k);if(before.containsKey(k.name())&&(!now.isPresent()||now.getAsLong()!=before.get(k.name())))throw new IllegalStateException("Source policy changed during observation");}return s;}
 boolean validate(ConfigService config,Source95AcquisitionAuthority authority){try{if(!observe(read(config))){authority.revoke(reason);return false;}authority.reduceCap(cap);return !revoked&&authority.state()!=Source95AcquisitionAuthority.State.UNKNOWN;}catch(RuntimeException changed){revoke("Source policy observation unavailable or changed");authority.revoke(reason);return false;}}
 boolean observe(Snapshot s){
  if(last!=null&&!last.equals(s))generation++;last=s;
  if(initial==null){initial=s;try{var b=Source95AcquisitionBinding.decode(s.contract(),loaded.crop());if(!s.buy()||s.ironman()||s.crop()!=loaded.crop()||!sameIdentity(b,loaded))return revoke("Source policy/contract differs at intent start");cap=Math.min(cap,b.maxTotalCoins());}catch(RuntimeException invalid){return revoke("Source contract unavailable at intent start");}return !revoked;}
  if(!initial.equals(s))revoke("Observed source policy generation/contract/cap/crop/purchase permission changed");
  return !revoked;
 }
 private boolean sameIdentity(Source95AcquisitionBinding a,Source95AcquisitionBinding b){return a.crop()==b.crop()&&a.productName().equals(b.productName())&&a.resourceComponents().equals(b.resourceComponents())&&a.titles().equals(b.titles())&&a.controls().equals(b.controls())&&a.clickDelayMillis()==b.clickDelayMillis()&&a.timeoutMillis()==b.timeoutMillis();}
 boolean revoke(String why){revoked=true;reason=why;return false;}
 long cap(){return cap;}boolean revoked(){return revoked;}long generation(){return generation;}String reason(){return reason;}
 boolean revisionEvidenceAvailable(){return initial!=null&&initial.revisions().size()==4;}
}
