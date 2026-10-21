package dev.veltraph.client.gui;

import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ExperimentsParentRuntimeTest {
    static final ExperimentSettings GAME=new ExperimentSettings(0,1000,"Remember","Timer:","?","Claim Rewards","Rewards Claimed","Experiment Failed");
    static ExperimentsParentSettings.Button button(String title,String name,String next){return new ExperimentsParentSettings.Button(title,name,"minecraft:paper","Available",next);}
    static ExperimentsParentSettings profile(boolean renew,int purchases,long budget){
        return new ExperimentsParentSettings("Table","",List.of(new ExperimentsParentSettings.Stage(button("Table","Game","Tier"),button("Tier","Chosen","Superpairs"),"Required: ","Cost: ",button("Tier","Back","Table"))),
                List.of(button("Table","XP","XP Store")),List.of(button("XP Store","Back","Table")),"XP Bottle","Available",ExperimentsParentSettings.XpCreditMode.FINAL_LEVEL,"Levels: ","Price:"," coins","Bought {levels} levels for {cost}","Coins: ",
                renew,1,button("Table","Renew","Table"),"Renew cost: ","Renewals: ","Bits: ","Renewed {renewals} for {cost}",budget,50,purchases,0,1000,30000);
    }
    static MenuSnapshot.Slot slot(int id,String name,String... lore){return new MenuSnapshot.Slot(id,name,List.of(lore),false,"minecraft:paper",1,false);}
    static MenuSnapshot menu(String title,int rev,MenuSnapshot.Slot... slots){return new MenuSnapshot(title.hashCode()&1023,title,List.of(slots),rev);}
    static ExperimentsParentRuntime.Observation obs(MenuSnapshot m,int level,long coins,long bits){return new ExperimentsParentRuntime.Observation(m,level,List.of("protected tool"),List.of("Coins: "+coins,"Bits: "+bits),true);}
    static final MenuSnapshot TABLE=menu("Table",1,slot(10,"Game","Available"),slot(11,"XP","Available"));
    static final MenuSnapshot TIER=menu("Tier",1,slot(10,"Chosen","Available","Required: 10","Cost: 5"),slot(11,"Back","Available"));
    static final MenuSnapshot XP=menu("XP Store",1,new MenuSnapshot.Slot(10,"XP Bottle",List.of("Available","Levels: 11","Price:","100 coins"),false,"minecraft:experience_bottle",1,false),slot(11,"Back","Available"));
    long now;
    ExperimentsParentRuntime runtime=new ExperimentsParentRuntime();
    ExperimentsParentRuntime.Action tick(MenuSnapshot m,int lvl,long coins,long bits){return runtime.tick(obs(m,lvl,coins,bits),GAME,++now);}
    void atPurchase(long budget) {
        runtime.start(profile(false,2,budget),0);
        assertEquals(10,tick(TABLE,0,1000,100).slot());tick(TIER,0,1000,100);tick(TIER,0,1000,100);
        assertEquals(11,tick(TIER,0,1000,100).slot());tick(TABLE,0,1000,100);
        assertEquals(11,tick(TABLE,0,1000,100).slot());tick(XP,0,1000,100);tick(XP,0,1000,100);
    }
    void completeGame(int before,long coins,long bits) {
        assertEquals(10,tick(TABLE,before,coins,bits).slot());tick(TIER,before,coins,bits);
        assertEquals(10,tick(TIER,before,coins,bits).slot());
        var reward=menu("Superpairs",1,slot(49,"Claim Rewards"));tick(reward,before-5,coins,bits);
        assertEquals(49,tick(reward,before-5,coins,bits).slot());
        assertEquals(ExperimentsParentRuntime.Kind.CLOSE,tick(menu("Superpairs",2,slot(49,"Rewards Claimed")),before-5,coins,bits).kind());
    }
    @Test void purchaseNeedsExactFreshReceiptXpCurrencyAndPreservedInventory() {
        atPurchase(100);assertEquals(10,tick(XP,0,1000,100).slot());
        runtime.onServerMessage("Bought 11 levels for 99");tick(XP,11,900,100);assertEquals(ExperimentsParentRuntime.State.WAIT_FOR_PURCHASE,runtime.state());
        runtime.onServerMessage("Bought 11 levels for 100");tick(XP,0,900,100);assertEquals(0,runtime.spent());
        tick(XP,11,1000,100);assertEquals(0,runtime.spent());tick(XP,11,900,100);assertEquals(100,runtime.spent());
        assertEquals(11,tick(XP,11,900,100).slot());tick(TABLE,11,900,100);tick(TABLE,11,900,100);
        completeGame(11,900,100);tick(TABLE,6,900,100);
        assertEquals(ExperimentsParentRuntime.State.COMPLETE,runtime.state());
    }
    @Test void repeatsActualGameOnlyAfterRenewalReceiptCounterAndResourceAgree() {
        runtime.start(profile(true,0,0),0);completeGame(20,1000,100);
        var renewal=menu("Table",2,slot(10,"Game","Available"),slot(12,"Renew","Available","Renew cost: 50","Renewals: 0"));
        tick(renewal,15,1000,100);assertEquals(12,tick(renewal,15,1000,100).slot());
        runtime.onServerMessage("Renewed 1 for 50");tick(renewal,15,1000,50);assertEquals(0,runtime.renewals());
        var confirmed=menu("Table",3,slot(10,"Game","Available"),slot(12,"Renew","Available","Renew cost: 50","Renewals: 1"));
        tick(confirmed,15,1000,100);assertEquals(0,runtime.renewals());tick(confirmed,15,1000,50);assertEquals(1,runtime.renewals());
        completeGame(15,1000,50);tick(TABLE,10,1000,50);assertEquals(ExperimentsParentRuntime.State.COMPLETE,runtime.state());
    }
    @Test void cumulativeBudgetAndProtectedInventoryStopWithoutRetry() {
        atPurchase(99);assertEquals(ExperimentsParentRuntime.Kind.NONE,tick(XP,0,1000,100).kind());assertEquals(ExperimentsParentRuntime.State.FAILED,runtime.state());
        runtime=new ExperimentsParentRuntime();now=0;atPurchase(100);tick(XP,0,1000,100);
        runtime.tick(new ExperimentsParentRuntime.Observation(XP,11,List.of("changed tool"),List.of("Coins: 900","Bits: 100"),true),GAME,++now);
        assertEquals(ExperimentsParentRuntime.State.FAILED,runtime.state());
    }
    @Test void staleReceiptsTimeoutAndCancellationNeverRetryPurchase() {
        atPurchase(100);runtime.onServerMessage("Bought 11 levels for 100");tick(XP,0,1000,100);tick(XP,11,900,100);
        assertEquals(ExperimentsParentRuntime.State.WAIT_FOR_PURCHASE,runtime.state());
        assertEquals(ExperimentsParentRuntime.Kind.NONE,runtime.tick(obs(XP,11,900,100),GAME,now+1001).kind());assertEquals(ExperimentsParentRuntime.State.FAILED,runtime.state());
        runtime=new ExperimentsParentRuntime();now=0;atPurchase(100);tick(XP,0,1000,100);runtime.cancel();
        runtime.onServerMessage("Bought 11 levels for 100");assertEquals(ExperimentsParentRuntime.Kind.NONE,tick(XP,11,900,100).kind());assertEquals(ExperimentsParentRuntime.State.CANCELLED,runtime.state());
    }
    @Test void unrelatedMenuReplacementFailsAndMenuOnlyCannotProveLevelCharge() {
        runtime.start(profile(false,0,0),0);tick(TABLE,20,1000,100);
        tick(menu("XP Store",1,slot(11,"Back","Available")),20,1000,100);assertEquals(ExperimentsParentRuntime.State.FAILED,runtime.state());
        runtime.start(profile(false,0,0),0);tick(TABLE,20,1000,100);tick(TIER,20,1000,100);tick(TIER,20,1000,100);
        tick(menu("Superpairs",1,slot(49,"Claim Rewards")),20,1000,100);assertEquals(ExperimentsParentRuntime.State.WAITING_ACK,runtime.state());
        tick(menu("Superpairs",1,slot(49,"Claim Rewards")),15,1000,100);assertEquals(ExperimentsParentRuntime.State.PLAYING,runtime.state());
    }
    @Test void parserAndButtonContractsRejectAmbiguousMalformedAndPlayerLookalikes() {
        assertNull(ExperimentsParentRuntime.number(List.of("Coins: 10","Coins: 10"),"Coins: ",""));
        assertNull(ExperimentsParentRuntime.number(List.of("Coins: 1,00"),"Coins: ",""));
        assertEquals(1000L,ExperimentsParentRuntime.number(List.of("§aCoins: 1,000"),"Coins: ",""));
        runtime.start(profile(false,0,0),0);
        tick(menu("Table",1,new MenuSnapshot.Slot(10,"Game",List.of("Available"),true,"minecraft:paper",1,false)),20,1000,100);
        assertEquals(ExperimentsParentRuntime.State.FAILED,runtime.state());
        assertThrows(IllegalArgumentException.class,()->new ExperimentsParentSettings.Button("Table","", "minecraft:paper","Available","Tier"));
    }
    @Test void duplicateQuotesAndOverrunDeductionsCannotAcknowledgePurchase() {
        atPurchase(1000);var duplicated=menu("XP Store",2,XP.slots().getFirst(),new MenuSnapshot.Slot(12,"XP Bottle",XP.slots().getFirst().lore(),false,"minecraft:experience_bottle",1,false));
        tick(duplicated,0,1000,100);assertEquals(ExperimentsParentRuntime.State.FAILED,runtime.state());
        runtime=new ExperimentsParentRuntime();now=0;atPurchase(1000);tick(XP,0,1000,100);runtime.onServerMessage("Bought 11 levels for 100");tick(XP,11,899,100);
        assertEquals(ExperimentsParentRuntime.State.FAILED,runtime.state());assertEquals(0,runtime.spent());
    }
    @Test void orderedStagesContinueUnderOneParentBeforeFinalCompletion() {
        var p=profile(false,0,0);var second=new ExperimentsParentSettings.Stage(button("Table","Next Game","Tier"),p.stages().getFirst().tier(),"Required: ","Cost: ",null);
        var multi=new ExperimentsParentSettings(p.tableTitle(),p.tableCommand(),List.of(p.stages().getFirst(),second),p.xpPath(),p.xpReturnPath(),p.xpBottleName(),p.xpAvailableLore(),p.xpCreditMode(),p.xpLevelsPrefix(),p.xpCostHeader(),p.xpCostSuffix(),p.purchaseReceipt(),p.currencyPrefix(),false,0,p.renewButton(),p.renewalCostPrefix(),p.renewalCountPrefix(),p.renewalResourcePrefix(),p.renewalReceipt(),0,0,0,0,1000,30000);
        runtime.start(multi,0);completeGame(20,1000,100);
        var next=menu("Table",2,slot(13,"Next Game","Available"));tick(next,15,1000,100);
        assertEquals(ExperimentsParentRuntime.State.TABLE,runtime.state());assertEquals(13,tick(next,15,1000,100).slot());tick(TIER,15,1000,100);tick(TIER,15,1000,100);
        var reward=menu("Superpairs",1,slot(49,"Claim Rewards"));tick(reward,10,1000,100);tick(reward,10,1000,100);
        tick(menu("Superpairs",2,slot(49,"Rewards Claimed")),10,1000,100);tick(TABLE,10,1000,100);
        assertEquals(ExperimentsParentRuntime.State.COMPLETE,runtime.state());
    }
    @Test void explicitlyActiveBottleLoreIsRequiredAndBlankParentCannotEnable() {
        atPurchase(100);var disabled=menu("XP Store",2,new MenuSnapshot.Slot(10,"XP Bottle",List.of("Unavailable","Levels: 11","Price:","100 coins"),false,"minecraft:experience_bottle",1,false));
        tick(disabled,0,1000,100);assertEquals(ExperimentsParentRuntime.State.FAILED,runtime.state());
        var config=new dev.veltraph.client.config.MapConfigService();assertNull(ExperimentsParentConfig.settings(config));
        config.set(ExperimentsParentConfig.EXPERIMENTS_PARENT_ENABLED,true);assertThrows(IllegalArgumentException.class,()->ExperimentsParentConfig.settings(config));
        config.set(ExperimentsParentConfig.EXPERIMENTS_PARENT_PROFILE,new com.google.gson.Gson().toJson(profile(true,2,100)));
        assertEquals(profile(true,2,100),ExperimentsParentConfig.settings(config));
    }

    @Test void explicitlyBoundAddedLevelsNeedTheExactNativeOutcome() {
        var p=profile(false,1,100);var added=new ExperimentsParentSettings(p.tableTitle(),p.tableCommand(),p.stages(),p.xpPath(),p.xpReturnPath(),p.xpBottleName(),p.xpAvailableLore(),ExperimentsParentSettings.XpCreditMode.ADDED_LEVELS,p.xpLevelsPrefix(),p.xpCostHeader(),p.xpCostSuffix(),p.purchaseReceipt(),p.currencyPrefix(),false,0,p.renewButton(),p.renewalCostPrefix(),p.renewalCountPrefix(),p.renewalResourcePrefix(),p.renewalReceipt(),100,0,1,0,1000,30000);
        runtime.start(added,0);tick(TABLE,5,1000,100);tick(TIER,5,1000,100);tick(TIER,5,1000,100);tick(TIER,5,1000,100);tick(TABLE,5,1000,100);tick(TABLE,5,1000,100);tick(XP,5,1000,100);tick(XP,5,1000,100);tick(XP,5,1000,100);
        runtime.onServerMessage("Bought 11 levels for 100");tick(XP,11,900,100);assertEquals(ExperimentsParentRuntime.State.WAIT_FOR_PURCHASE,runtime.state());tick(XP,16,900,100);assertEquals(100,runtime.spent());
    }

    @Test void exactOwnedPickupPredictionWaitsWithoutAcknowledgingOrRepeatingNavigation() {
        runtime.start(profile(false,0,0),0);assertEquals(10,tick(TABLE,20,1000,100).slot());
        var predicted=new ExperimentsParentRuntime.Observation(TABLE,20,List.of("protected tool"),List.of("Coins: 1000","Bits: 100"),false,true,false);
        assertEquals(ExperimentsParentRuntime.Kind.NONE,runtime.tick(predicted,GAME,++now).kind());assertEquals(ExperimentsParentRuntime.State.WAITING_ACK,runtime.state());
        runtime.tick(predicted,GAME,++now);assertEquals(ExperimentsParentRuntime.State.WAITING_ACK,runtime.state());
        tick(TIER,20,1000,100);assertEquals(ExperimentsParentRuntime.State.SELECT_TIER,runtime.state());
        assertEquals(10,tick(TIER,20,1000,100).slot());
    }
    @Test void cursorAttributionDoesNotPermitForeignCursorNewRevisionOrReplacedMenu() {
        for(int kind=0;kind<3;kind++) {
            runtime.start(profile(false,0,0),0);tick(TABLE,20,1000,100);
            var menu=kind==2?new MenuSnapshot(TABLE.id()+1,TABLE.title(),TABLE.slots(),TABLE.revision()):TABLE;
            var foreign=new ExperimentsParentRuntime.Observation(menu,20,List.of("protected tool"),List.of("Coins: 1000","Bits: 100"),false,kind!=0,kind==1);
            assertEquals(ExperimentsParentRuntime.Kind.NONE,runtime.tick(foreign,GAME,++now).kind());assertEquals(ExperimentsParentRuntime.State.FAILED,runtime.state());
        }
    }
    @Test void ownedPredictionStillRejectsProtectedInventoryChangeAndCorrectionTimeout() {
        runtime.start(profile(false,0,0),0);tick(TABLE,20,1000,100);
        var mutated=new ExperimentsParentRuntime.Observation(TABLE,20,List.of("changed tool"),List.of("Coins: 1000","Bits: 100"),false,true,false);
        runtime.tick(mutated,GAME,++now);assertEquals(ExperimentsParentRuntime.State.FAILED,runtime.state());
        runtime.start(profile(false,0,0),0);now=0;tick(TABLE,20,1000,100);
        var predicted=new ExperimentsParentRuntime.Observation(TABLE,20,List.of("protected tool"),List.of("Coins: 1000","Bits: 100"),false,true,false);
        assertEquals(ExperimentsParentRuntime.Kind.NONE,runtime.tick(predicted,GAME,1002).kind());assertEquals(ExperimentsParentRuntime.State.FAILED,runtime.state());
    }
    @Test void predictionWithoutExplicitServerRevisionCannotAcknowledgePurchaseOrNavigation() {
        atPurchase(100);tick(XP,0,1000,100);runtime.onServerMessage("Bought 11 levels for 100");
        var local=new ExperimentsParentRuntime.Observation(XP,11,List.of("protected tool"),List.of("Coins: 900","Bits: 100"),true,false,false);
        runtime.tick(local,GAME,++now);assertEquals(ExperimentsParentRuntime.State.WAIT_FOR_PURCHASE,runtime.state());assertEquals(0,runtime.spent());
        tick(XP,11,900,100);assertEquals(100,runtime.spent());
        runtime.start(profile(false,0,0),0);tick(TABLE,20,1000,100);
        runtime.tick(new ExperimentsParentRuntime.Observation(TIER,20,List.of("protected tool"),List.of("Coins: 1000","Bits: 100"),true,false,false),GAME,++now);
        assertEquals(ExperimentsParentRuntime.State.WAITING_ACK,runtime.state());tick(TIER,20,1000,100);assertEquals(ExperimentsParentRuntime.State.SELECT_TIER,runtime.state());
    }
    @Test void ownedPredictionCannotOutliveCancellationOrGameMoveAcknowledgmentBound() {
        runtime.start(profile(false,0,0),0);tick(TABLE,20,1000,100);runtime.cancel();
        runtime.tick(new ExperimentsParentRuntime.Observation(TABLE,20,List.of("protected tool"),List.of("Coins: 1000","Bits: 100"),false,true,false),GAME,++now);
        assertEquals(ExperimentsParentRuntime.State.CANCELLED,runtime.state());
        runtime.start(profile(false,0,0),0);now=0;tick(TABLE,20,1000,100);tick(TIER,20,1000,100);tick(TIER,20,1000,100);
        var board=menu("Superpairs",1,new MenuSnapshot.Slot(10,"?",List.of(),false,"minecraft:gray_stained_glass_pane",1,false));
        tick(board,15,1000,100);assertEquals(10,tick(board,15,1000,100).slot());
        var prediction=new ExperimentsParentRuntime.Observation(board,15,List.of("protected tool"),List.of("Coins: 1000","Bits: 100"),false,true,false);
        assertEquals(ExperimentsParentRuntime.Kind.NONE,runtime.tick(prediction,GAME,now+1).kind());assertEquals(ExperimentsParentRuntime.State.PLAYING,runtime.state());
        runtime.tick(prediction,GAME,now+1001);assertEquals(ExperimentsParentRuntime.State.FAILED,runtime.state());
    }

}
