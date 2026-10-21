package dev.veltraph.client.builder;

import dev.veltraph.client.config.*;
import dev.veltraph.client.gui.MenuSnapshot;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Exercises the production pre-advance helper with actual leg state mutations and policy/authority adapter ordering. */
class Source95CustomAdapterOrderTest {
 private static final String CONTRACT="{\"WHEAT\":{\"productName\":\"Seeds\",\"resourceComponents\":\"@vanilla\",\"menuTitles\":[\"Seeds\",\"Confirm\"],\"controls\":{\"instant\":\"Instant\",\"custom\":\"Custom\",\"confirm\":\"Pay\"},\"maxTotalCoins\":500,\"clickDelayMillis\":100,\"timeoutMillis\":30000}}";
 private final class Adapter implements AutoCloseable {
  final MapConfigService config=new MapConfigService();
  final Source95AcquisitionBinding binding=Source95AcquisitionBinding.decode(CONTRACT,BuilderCropType.WHEAT);
  final Source95AcquisitionPolicy policy=new Source95AcquisitionPolicy(binding);
  final Source95AcquisitionAuthority authority=new Source95AcquisitionAuthority();
  final Object p=new Object(),w=new Object(),c=new Object(),owner=new Object(),amountMenu=new Object(),amountScreen=new Object(),signScreen=new Object(),confirmMenu=new Object(),confirmScreen=new Object(),token=new Object();
  final Source95AcquisitionAuthority.Scope original=new Source95AcquisitionAuthority.Scope(p,w,c,owner,amountMenu,amountScreen,4,1);
  final Source95AcquisitionAuthority.Scope confirmation=new Source95AcquisitionAuthority.Scope(p,w,c,owner,confirmMenu,confirmScreen,6,1);
  Source95AcquisitionAuthority.Scope current=original;
  Source95MenuTransitions.Event signEvent,confirmationEvent;
  long amountUiFloor,clickFloor,submitFloor,chatFloor;boolean customPending;int paidIssues;
  final Source95BazaarLeg leg;
  Adapter(){
   config.set(BuilderConfig.BUILDER_BUY_RESOURCES,true);config.set(GeneratedBuilderConfig.IRONMAN_MODE,false);config.set(BuilderConfig.BUILDER_CROP_TYPE,BuilderCropType.WHEAT);config.set(GeneratedBuilderConfig.SOURCE95_CROP_ACQUISITION_BINDING,CONTRACT);
   assertTrue(policy.validate(config,authority));assertTrue(authority.bindOriginalAmount(original));
   // Generic source chooser branch operands; not a claim that vanilla wheat seeds have native max-stack32.
   var choice=Source95PurchaseChooser.choose(128,true,false,32,20);assertEquals(Source95PurchaseChooser.Kind.CUSTOM,choice.kind());assertEquals(128,choice.quantity());leg=new Source95BazaarLeg(choice,1000,true);
   Source95MenuTransitions.attach(c,token);Source95ChatReceipts.attach(c,token);amountUiFloor=Source95MenuTransitions.floor();
  }
  MenuSnapshot amount(){return new MenuSnapshot(4,"Seeds",List.of(new MenuSnapshot.Slot(12,"Custom",List.of(),false,"minecraft:paper",1,false)),1);}
  MenuSnapshot confirm(){return new MenuSnapshot(6,"Confirm",List.of(new MenuSnapshot.Slot(13,"Pay",List.of("Item: Seeds","Amount: 128x","Price: 384 Coins"),false,"minecraft:paper",1,false)),1);}
  boolean guard(){return policy.validate(config,authority)&&authority.validateOwnedBoundary(current.player(),current.world(),current.connection(),current.owner(),current.menu(),current.screen(),current.menuId(),Source95MenuTransitions.changedAfter(c,token,amountUiFloor),customPending&&(leg.state()==Source95BazaarLeg.State.CUSTOM_SIGN||leg.state()==Source95BazaarLeg.State.CUSTOM_CONFIRM)&&authority.state()!=Source95AcquisitionAuthority.State.ISSUED);}
  void receivedSignAndConfirmation(){
   assertTrue(guard());var customClick=leg.tick(binding,amount(),false,1000,authority.remainingCap());assertEquals(Source95BazaarLeg.Kind.CLICK,customClick.kind());assertEquals(Source95BazaarLeg.State.CUSTOM_SIGN,leg.state());clickFloor=Source95MenuTransitions.floor();customPending=true;
   current=new Source95AcquisitionAuthority.Scope(p,w,c,owner,amountMenu,signScreen,4,1);Source95MenuTransitions.received(c,Source95MenuTransitions.Kind.SIGN,p,w,current.menu(),current.screen(),current.menuId());signEvent=Source95MenuTransitions.exactAfter(c,token,clickFloor,Source95MenuTransitions.Kind.SIGN,p,w,current.menu(),current.screen(),current.menuId()).orElseThrow();
   assertTrue(guard());var signSubmit=leg.tick(binding,null,true,1000,authority.remainingCap());assertEquals(Source95BazaarLeg.Kind.SIGN,signSubmit.kind());assertEquals("128",signSubmit.text());assertEquals(Source95BazaarLeg.State.CUSTOM_CONFIRM,leg.state());submitFloor=Source95MenuTransitions.floor();
   current=confirmation;Source95MenuTransitions.received(c,Source95MenuTransitions.Kind.CONTAINER,p,w,current.menu(),current.screen(),current.menuId());confirmationEvent=Source95MenuTransitions.exactAfter(c,token,submitFloor,Source95MenuTransitions.Kind.CONTAINER,p,w,current.menu(),current.screen(),current.menuId()).orElseThrow();
  }
  boolean preAdvance(){if(!guard())return false;if(!Source95CustomTransition.bindBeforeAdvance(authority,leg,current,signEvent,confirmationEvent,clickFloor,submitFloor))return false;amountUiFloor=Source95MenuTransitions.floor();customPending=false;return guard();}
  boolean pay(){if(!guard())return false;var action=leg.tick(binding,confirm(),false,1000,authority.remainingCap());if(action.kind()!=Source95BazaarLeg.Kind.CLICK)return false;assertEquals(Source95BazaarLeg.State.ACK,leg.state());if(!guard())return false;chatFloor=Source95ChatReceipts.floor();if(!authority.issueLeg(binding,current,leg.quantity(),leg.quotedCost(),1000,11,10))return false;paidIssues++;return true;}
  boolean acknowledge(){if(!guard())return false;Source95ChatReceipts.received(c,new Source95ChatReceipts.Scope(p,w,c,current.menu(),current.screen(),current.menuId()),"[Bazaar] Bought 128x Seeds for 384 coins!",false);for(var receipt:Source95ChatReceipts.after(c,token,chatFloor,current))leg.message(receipt,binding);if(!guard())return false;leg.tick(binding,confirm(),false,1128,authority.remainingCap());assertEquals(Source95BazaarLeg.State.COMPLETE,leg.state());if(!guard())return false;boolean accepted=authority.acknowledgeLeg(current,1128,128,leg.charged(),true,12,true,true,11);return accepted&&Source95ChatReceipts.consume(c,token,leg.messageEvent());}
  @Override public void close(){Source95MenuTransitions.detach(c,token);Source95ChatReceipts.detach(c,token);}
 }
 @Test void sourcePolicySignConfirmationPreAdvanceClickIssueAckCompletionOrderWorks(){try(var a=new Adapter()){a.receivedSignAndConfirmation();assertTrue(a.preAdvance());assertTrue(a.pay());assertEquals(1,a.paidIssues);assertTrue(a.acknowledge());assertTrue(a.guard());assertEquals(Source95AcquisitionAuthority.State.CONFIRMED,a.authority.state());assertEquals(128,a.authority.acquired());assertEquals(384,a.authority.spent());}}
 @Test void oldPostAdvanceAuthorizationOrderReproducesM5BlockerWithoutPayment(){try(var a=new Adapter()){a.receivedSignAndConfirmation();assertTrue(a.guard());var action=a.leg.tick(a.binding,a.confirm(),false,1000,500);assertEquals(Source95BazaarLeg.Kind.CLICK,action.kind());assertEquals(Source95BazaarLeg.State.ACK,a.leg.state());assertFalse(a.guard());assertEquals(Source95AcquisitionAuthority.State.UNKNOWN,a.authority.state());assertEquals(0,a.paidIssues);assertFalse(a.pay());}}
 @Test void missingOrWrongNativeConfirmationCannotEmitPaidAction(){for(int variant=0;variant<3;variant++)try(var a=new Adapter()){a.receivedSignAndConfirmation();if(variant==0)a.confirmationEvent=null;else if(variant==1)a.current=new Source95AcquisitionAuthority.Scope(a.p,a.w,a.c,a.owner,new Object(),a.confirmScreen,6,1);else a.submitFloor=a.signEvent.sequence()-1;assertFalse(a.preAdvance());assertEquals(Source95BazaarLeg.State.CUSTOM_CONFIRM,a.leg.state());assertEquals(0,a.paidIssues);assertFalse(a.pay());}}
 @Test void extraReceivedConfirmationIsAmbiguousBeforePayment(){try(var a=new Adapter()){a.receivedSignAndConfirmation();Source95MenuTransitions.received(a.c,Source95MenuTransitions.Kind.CONTAINER,a.p,a.w,new Object(),new Object(),7);a.confirmationEvent=Source95MenuTransitions.exactAfter(a.c,a.token,a.submitFloor,Source95MenuTransitions.Kind.CONTAINER,a.p,a.w,a.confirmMenu,a.confirmScreen,6).orElse(null);assertFalse(a.preAdvance());assertEquals(0,a.paidIssues);assertFalse(a.pay());}}
 @Test void boundConfirmationCannotAdoptSameTitleReplacementBeforeIssue(){try(var a=new Adapter()){a.receivedSignAndConfirmation();assertTrue(a.preAdvance());a.current=new Source95AcquisitionAuthority.Scope(a.p,a.w,a.c,a.owner,new Object(),new Object(),6,1);assertFalse(a.pay());assertEquals(0,a.paidIssues);assertEquals(Source95AcquisitionAuthority.State.UNKNOWN,a.authority.state());}}
 @Test void policyRevocationBeforeCustomPaymentOrAfterIssueNeverAuthorizesCompletion(){for(boolean issued:List.of(false,true))try(var a=new Adapter()){a.receivedSignAndConfirmation();assertTrue(a.preAdvance());if(issued)assertTrue(a.pay());a.config.set(GeneratedBuilderConfig.IRONMAN_MODE,true);assertFalse(a.guard());if(issued)assertFalse(a.acknowledge());else assertFalse(a.pay());assertEquals(issued?1:0,a.paidIssues);assertEquals(Source95AcquisitionAuthority.State.UNKNOWN,a.authority.state());assertEquals(0,a.authority.acquired());}}
 @Test void authorizationConsumesOnlyExactConfirmationAndCannotReplayCustomPayment(){try(var a=new Adapter()){a.receivedSignAndConfirmation();assertTrue(a.preAdvance());assertTrue(a.pay());assertFalse(a.authority.issueLeg(a.binding,a.confirmation,128,384,1000,11,10));assertEquals(1,a.paidIssues);assertTrue(a.acknowledge());assertFalse(a.pay());assertEquals(1,a.paidIssues);}}
}
