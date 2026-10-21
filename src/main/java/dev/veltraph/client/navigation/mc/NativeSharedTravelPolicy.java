package dev.veltraph.client.navigation.mc;
import dev.veltraph.client.config.*;
import dev.veltraph.client.navigation.*;
/** Caller-private refusal only. Exact concrete final store values/revisions read without provider callbacks. */
final class NativeSharedTravelPolicy {
 private final ConfigService store;private final DynamicRouteConfigStamp expected;
 private NativeSharedTravelPolicy(ConfigService store,DynamicRouteConfigStamp expected){this.store=store;this.expected=expected;}
 static NativeSharedTravelPolicy capture(ConfigService store,DynamicRouteConfigStamp expected){
  if(expected==null||expected.store()!=store||!(store instanceof MapConfigService||store instanceof JsonConfigService))return null;
  var value=new NativeSharedTravelPolicy(store,expected);return value.current()?value:null;
 }
 private Object value(ConfigKey<?> key){if(store instanceof MapConfigService map)return map.get(key);if(store instanceof JsonConfigService json)return json.get(key);return null;}
 private long revision(ConfigKey<?> key){if(store instanceof MapConfigService map)return map.revision(key).orElse(-1);if(store instanceof JsonConfigService json)return json.revision(key).orElse(-1);return -1;}
 boolean current(){var rotation=value(RotationConfig.ROTATION_SPEED_MULTIPLIER);return dev.veltraph.client.VeltraphBootstrap.config()==store
   &&rotation instanceof Double real&&Double.doubleToLongBits(real)==Double.doubleToLongBits(expected.rotation())
   &&value(TravelConfig.TRAVEL_APPROACH_MODE)==expected.approach()
   &&revision(RotationConfig.ROTATION_SPEED_MULTIPLIER)==expected.rotationRevision()&&revision(TravelConfig.TRAVEL_APPROACH_MODE)==expected.approachRevision();}
 boolean devicesAllowed(){return current()&&expected.approach()!=ApproachMode.WALK_ONLY;}
}
