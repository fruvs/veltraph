package dev.veltraph.client.builder;
/** Tool action policy. A predicted state and a click receipt cannot acknowledge native output. */
public final class Source95ToolRuntime {
 public enum Action { NONE, MOVE, SELECT, SHIFT, USE, COMPLETE, STOP }
 public record Facts(boolean valid,boolean initialMatches,boolean outputMatches,boolean independentReceipts,boolean settled,boolean available,boolean selected,boolean reachable,boolean aligned,boolean shifting,long now) {}
 private long started=-1,issued=-1;private String status="Ready";
 public Action tick(Source95ToolBinding binding,Facts f){
  if(!f.valid())return stop("Tool context/footprint changed or contains data; checkpoint retained");
  if(started<0)started=f.now();
  if(issued>=0){if(f.now()-issued>binding.acknowledgementTimeoutMillis())return stop("Native tool output not independently acknowledged; checkpoint retained");
   if(f.outputMatches()&&f.independentReceipts()&&f.settled()&&f.now()-issued>=binding.cooldownMillis()){status="All declared native output cells independently acknowledged";return Action.COMPLETE;}return Action.NONE;}
  if(f.now()-started>30000)return stop("Tool approach timed out; checkpoint retained");
  if(!f.initialMatches())return stop("Declared tool target/output preconditions differ; checkpoint retained");
  if(!f.available())return stop("Exact bound source tool missing; checkpoint retained");
  if(!f.reachable())return Action.MOVE;if(!f.selected())return Action.SELECT;if(!f.aligned())return Action.NONE;if(!f.shifting())return Action.SHIFT;
  issued=f.now();status="Await independent server output receipts for declared footprint";return Action.USE;
 }
 public boolean issued(){return issued>=0;}public String status(){return status;}private Action stop(String why){status=why;return Action.STOP;}
}
