package dev.veltraph.client.builder;
import java.util.*;
/** One source128 intent with consecutive acknowledged source legs. Partial64 is progress, not completion. */
public final class Source95AcquisitionAuthority {
 public enum State { READY, ISSUED, PROGRESS, CONFIRMED, UNKNOWN, REJECTED }
 public record Scope(Object player,Object world,Object connection,Object owner,Object menu,Object screen,int menuId,int revision){
  public Scope {Objects.requireNonNull(player);Objects.requireNonNull(world);Objects.requireNonNull(connection);Objects.requireNonNull(owner);Objects.requireNonNull(menu);Objects.requireNonNull(screen);if(menuId<0||revision<0)throw new IllegalArgumentException("Exact native source purchase scope required");}
  public boolean sameOwnerAndMenu(Scope o){return o!=null&&player==o.player&&world==o.world&&connection==o.connection&&owner==o.owner&&menu==o.menu&&screen==o.screen&&menuId==o.menuId;}
 }
 private final UUID intent=UUID.randomUUID();private State state=State.READY;private Scope pending,originalAmount,customConfirmation;private boolean revoked;
 private int initial=-1,before,quantity,acquired,legs;private long floor,quote,spent,issuedAt,timeout,cap=Long.MAX_VALUE;private String reason="Ready";
 public boolean issueLeg(Source95AcquisitionBinding binding,Scope scope,int requested,long cost,int currentCount,long packetFloor,long now){
  if(revoked||state!=State.READY&&state!=State.PROGRESS)return false;
  if(originalAmount!=null&&!originalAmount.sameOwnerAndMenu(scope)&&(customConfirmation==null||!customConfirmation.sameOwnerAndMenu(scope))){revoke("Original owned amount menu/screen/PWC changed between source legs");return false;}
  cap=Math.min(cap,binding.maxTotalCoins());if(initial<0)initial=currentCount;
  if(requested<1||requested>128-acquired||currentCount!=(long)initial+acquired||cost<0||cost>cap-spent||packetFloor<0){unknown("Source leg quantity/aggregate cap/current stock differs; no purchase replay");return false;}
  pending=Objects.requireNonNull(scope);if(originalAmount==null)originalAmount=scope;customConfirmation=null;quantity=requested;quote=cost;before=currentCount;floor=packetFloor;issuedAt=now;timeout=binding.timeoutMillis();state=State.ISSUED;reason="Await exact source leg"+(legs+1)+" delta"+quantity;return true;
 }
 public boolean acknowledgeLeg(Scope scope,int currentCount,int messageQuantity,long charged,boolean product,long serial,boolean applied,boolean protectedUnchanged,long now){
  if(revoked||state!=State.ISSUED||now<issuedAt||now-issuedAt>=timeout||!pending.sameOwnerAndMenu(scope)||!product||messageQuantity!=quantity||charged<0||charged>quote||charged>cap-spent||currentCount!=(long)before+quantity||serial<=floor||!applied||!protectedUnchanged)return false;
  spent=Math.addExact(spent,charged);acquired=Math.addExact(acquired,quantity);legs++;state=acquired==128?State.CONFIRMED:State.PROGRESS;reason=state==State.CONFIRMED?"All newly acquired128source crop units confirmed":"Confirmed"+acquired+"/128; remaining"+remaining();return true;
 }
 public void reduceCap(long lower){if(lower<1)unknown("Source cap became invalid");else{cap=Math.min(cap,lower);if(spent>cap)unknown("Reduced source cap below acknowledged partial spend");}}
 public void timedOut(long now){if(state==State.ISSUED&&now-issuedAt>=timeout)unknown("Source leg acknowledgement timed out; no replay");}
 public void cancel(){if(state==State.ISSUED||state==State.PROGRESS)unknown("Cancelled source128intent after issued/proven partial leg; no replay");else if(state==State.READY)state=State.REJECTED;}
 public void revoke(String why){revoked=true;state=State.UNKNOWN;reason=why;}
 public record UnknownOutcome(UUID intent,int acknowledgedUnits,long acknowledgedSpend,int pendingUnits,long quotedPendingCost,String reason){}
 public Optional<UnknownOutcome> unknownOutcome(){return state==State.UNKNOWN?Optional.of(new UnknownOutcome(intent,acquired,spent,quantity,quote,reason)):Optional.empty();}
 public void unknown(String why){if(state!=State.CONFIRMED){state=State.UNKNOWN;reason=why;}}
 public boolean bindOriginalAmount(Scope scope){if(originalAmount==null){originalAmount=Objects.requireNonNull(scope);return true;}if(!originalAmount.sameOwnerAndMenu(scope)){revoke("Owned source amount menu/screen changed");return false;}return true;}
 public boolean authorizeCustomConfirmation(Scope confirmation,Source95MenuTransitions.Event sign,Source95MenuTransitions.Event opened,long customClickFloor,long submitFloor){
  if(revoked||originalAmount==null||sign==null||opened==null||sign.kind()!=Source95MenuTransitions.Kind.SIGN||opened.kind()!=Source95MenuTransitions.Kind.CONTAINER||sign.sequence()<=customClickFloor||opened.sequence()<=submitFloor||confirmation.player()!=originalAmount.player()||confirmation.world()!=originalAmount.world()||confirmation.connection()!=originalAmount.connection()||confirmation.owner()!=originalAmount.owner()||opened.player()!=confirmation.player()||opened.world()!=confirmation.world()||opened.connection()!=confirmation.connection()||opened.menu()!=confirmation.menu()||opened.screen()!=confirmation.screen()||opened.menuId()!=confirmation.menuId()||sign.player()!=originalAmount.player()||sign.world()!=originalAmount.world()||sign.connection()!=originalAmount.connection()){revoke("Unproven source CUSTOM sign/confirmation transition");return false;}customConfirmation=confirmation;return true;
 }
 public boolean validateOwnedBoundary(Object player,Object world,Object connection,Object owner,Object menu,Object screen,int id,boolean nativeUiChanged,boolean pendingValidatedCustomStrategy){
  if(revoked)return false;if(originalAmount==null)return true;
  if(originalAmount.player()!=player||originalAmount.world()!=world||originalAmount.connection()!=connection||originalAmount.owner()!=owner){revoke("Original source PWC/owner changed during menu strategy");return false;}
  if(pendingValidatedCustomStrategy&&state!=State.ISSUED&&state!=State.CONFIRMED)return true;
  var expected=(state==State.ISSUED||state==State.CONFIRMED)&&pending!=null?pending:customConfirmation!=null?customConfirmation:originalAmount;
  if(nativeUiChanged||expected.player()!=player||expected.world()!=world||expected.connection()!=connection||expected.owner()!=owner||expected.menu()!=menu||expected.screen()!=screen||expected.menuId()!=id){revoke("Observed owned source menu/screen close/reopen/ABA; no adoption");return false;}return true;
 }
 public boolean hasOriginalAmount(){return originalAmount!=null;}
 public boolean originalAmountMatches(Object player,Object world,Object connection,Object owner,Object menu,Object screen,int id){return originalAmount!=null&&originalAmount.player()==player&&originalAmount.world()==world&&originalAmount.connection()==connection&&originalAmount.owner()==owner&&originalAmount.menu()==menu&&originalAmount.screen()==screen&&originalAmount.menuId()==id;}
 public State state(){return state;}public UUID intent(){return intent;}public int acquired(){return acquired;}public int remaining(){return 128-acquired;}public int legs(){return legs;}public int pendingQuantity(){return quantity;}public long floor(){return floor;}public long spent(){return spent;}public long remainingCap(){return cap-spent;}public String reason(){return reason;}
 public UUID legReceiptId(){return UUID.nameUUIDFromBytes((intent.toString()+"/source-leg/"+legs).getBytes(java.nio.charset.StandardCharsets.UTF_8));}
}
