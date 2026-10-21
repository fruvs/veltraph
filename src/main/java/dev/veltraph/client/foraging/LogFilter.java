package dev.veltraph.client.foraging;
import java.util.Locale;
/** Exact registry paths or terminal log/wood/stem tokens. Minecraft adapter also requires LOGS tag. */
public final class LogFilter {
 private LogFilter() {}
 public static boolean matches(String blockId,String csv) {
  if(blockId==null||csv==null)return false;
  String id=blockId.toLowerCase(Locale.ROOT),path=id.substring(id.indexOf(':')+1);
  for(String raw:csv.split(",")) {
   String key=raw.trim().toLowerCase(Locale.ROOT);
   if(!key.isEmpty()&&(id.equals(key)||path.equals(key)||path.endsWith("_"+key)||ForagingRegion.speciesOf(id).equals(key)))return true;
  }
  return false;
 }
}
