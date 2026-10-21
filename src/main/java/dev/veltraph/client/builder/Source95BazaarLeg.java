package dev.veltraph.client.builder;
import dev.veltraph.client.gui.MenuSnapshot;
import dev.veltraph.client.garden.visitor.VisitorBazaarFlow;
import java.util.*;
import java.math.*;
import java.util.regex.Pattern;
/** Builder source leg strategy. Visitors keep their existing flow unchanged; all Builder control labels are explicitly bound. */
final class Source95BazaarLeg {
 enum State { PRODUCT, AMOUNT, CUSTOM_SIGN, CUSTOM_CONFIRM, ACK, COMPLETE, FAILED }
 enum Kind { NONE, CLICK, SIGN }
 record Action(Kind kind,MenuSnapshot menu,MenuSnapshot.Slot slot,String text){}
 private State state=State.PRODUCT;private Source95PurchaseChooser.Choice choice;private int quantity,before;private long cost=-1,charged=-1;private boolean message;private long matchedMessageEvent=-1;private MenuSnapshot previous;private String status="Waiting product";
 Source95BazaarLeg(Source95PurchaseChooser.Choice choice,int before,boolean sameAmountMenu){this.choice=choice;this.quantity=choice.quantity();this.before=before;if(sameAmountMenu)state=State.AMOUNT;}
 Action tick(Source95AcquisitionBinding b,MenuSnapshot menu,boolean sign,int inventory,long remainingCap){
  if(state==State.FAILED||state==State.COMPLETE)return none();
  if(state==State.ACK){if(message&&inventory==(long)before+quantity){state=State.COMPLETE;status="Source leg message and inventory delta observed";}return none();}
  if(state==State.CUSTOM_SIGN){if(sign){state=State.CUSTOM_CONFIRM;previous=null;return new Action(Kind.SIGN,null,null,String.valueOf(quantity));}return none();}
  if(menu==null||menu.equals(previous))return none();if(!b.titles().contains(menu.title()))return fail("Unbound source market title");
  String label=state==State.PRODUCT?b.controls().get("instant"):state==State.CUSTOM_CONFIRM?b.controls().get("confirm"):b.controls().get(choice.kind().name().toLowerCase(Locale.ROOT));
  if(label==null||label.isBlank())return fail("Unresolved source control label for "+state+"/"+choice.kind());
  var matches=menu.slots().stream().filter(s->!s.playerInventory()&&!s.locked()&&s.count()>0&&s.name().equals(label)).toList();
  if(matches.size()!=1){if(state==State.PRODUCT){var products=menu.slots().stream().filter(s->!s.playerInventory()&&!s.locked()&&s.count()>0&&s.name().equals(b.productName())).toList();if(products.size()==1)return click(menu,products.getFirst(),State.PRODUCT);}return fail("Ambiguous/missing bound source control");}
  var slot=matches.getFirst();
  if(state==State.PRODUCT){boolean product=menu.title().equals(b.productName())||menu.slots().stream().anyMatch(s->!s.playerInventory()&&s.name().equals(b.productName()));if(!product)return fail("Source product identity absent");return click(menu,slot,State.AMOUNT);}
  if(choice.kind()==Source95PurchaseChooser.Kind.CUSTOM&&state==State.AMOUNT)return click(menu,slot,State.CUSTOM_SIGN);
  var quote=VisitorBazaarFlow.readQuote(slot.lore(),b.productName(),quantity);if(quote.isEmpty()||quote.get().total()>remainingCap)return fail("Source leg product/quantity/aggregate cap quote invalid");cost=quote.get().total();message=false;charged=-1;return click(menu,slot,State.ACK);
 }
 void message(Source95ChatReceipts.Receipt receipt,Source95AcquisitionBinding b){if(receipt==null)return;String line=receipt.text();if((state!=State.ACK&&state!=State.COMPLETE)||line==null)return;var m=Pattern.compile("^\\[Bazaar\\] Bought ([0-9]+(?:,[0-9]{3})*)x (.+) for ([0-9]+(?:,[0-9]{3})*(?:\\.[0-9]+)?) coins!$",Pattern.CASE_INSENSITIVE).matcher(MenuSnapshot.clean(line));if(!m.matches())return;try{int count=Integer.parseInt(m.group(1).replace(",",""));long paid=new BigDecimal(m.group(3).replace(",","")).setScale(0,RoundingMode.CEILING).longValueExact();if(count!=quantity||!m.group(2).equals(b.productName())||paid>cost){fail("Source leg acknowledgement differs from quoted product/quantity/cost");return;}if(matchedMessageEvent>=0&&matchedMessageEvent!=receipt.eventId()){fail("Multiple matching native source purchase events; outcome ambiguous");return;}matchedMessageEvent=receipt.eventId();charged=paid;message=true;}catch(RuntimeException invalid){fail("Invalid source purchase receipt");}}
 private Action click(MenuSnapshot menu,MenuSnapshot.Slot slot,State next){previous=menu;state=next;return new Action(Kind.CLICK,menu,slot,"");}
 private Action none(){return new Action(Kind.NONE,null,null,"");}private Action fail(String why){state=State.FAILED;status=why;return none();}
 long messageEvent(){return matchedMessageEvent;}
 boolean customStrategy(){return choice.kind()==Source95PurchaseChooser.Kind.CUSTOM;}
 State state(){return state;}int quantity(){return quantity;}long quotedCost(){return cost;}long charged(){return charged;}String status(){return state+": "+status;}
}
