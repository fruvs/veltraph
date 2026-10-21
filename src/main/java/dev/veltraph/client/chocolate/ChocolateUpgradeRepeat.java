package dev.veltraph.client.chocolate;
import java.util.Objects;
import java.util.function.BooleanSupplier;
/** Deliberate finite employee progression. Every next level uses a fresh actual quote after a durable ACK. */
final class ChocolateUpgradeRepeat {
    enum Result { WAITING, SELECTED, STOPPED }
    private final String target;private final int requested;private final long maximumWait;
    private int completed;private String lastReceiptId="",reason;private long waitSince;
    private ChocolateFactoryObservation receipt;private ChocolateFactoryObservation.Quote next;
    ChocolateUpgradeRepeat(String target,int count,long maximumWait){if(target==null||target.isBlank()||count<1||count>16||maximumWait<1000||maximumWait>300000)throw new IllegalArgumentException("Select one employee,1-16 promotions and1-300 seconds per fresh quote wait");this.target=target;requested=count;this.maximumWait=maximumWait;reason="Selected up to "+count+" promotions; exact future prices are not inferred";}
    String diagnostics(){return "Repeat "+target+" "+completed+"/"+requested+": "+reason;}
    boolean needsAcknowledgment(ChocolateFactoryRuntime rt){return rt.state()==ChocolateFactoryRuntime.State.ACKNOWLEDGED&&!rt.acknowledgedUpgrades().isEmpty()&&!rt.acknowledgedUpgrades().getLast().id().equals(lastReceiptId);}
    void acknowledgedDurably(ChocolateFactoryRuntime rt,long now){
        if(!needsAcknowledgment(rt))return;var ack=rt.acknowledgedUpgrades().getLast();if(!ack.selection().quote().id().equals(target)||ack.selection().quote().kind()!=ChocolateFactoryProfile.Kind.EMPLOYEE)throw new IllegalStateException("Repeat cannot adopt another target receipt");
        lastReceiptId=ack.id();completed++;receipt=ack.receipt();next=receipt.quotes().get(target);waitSince=now;reason=completed>=requested?"Selected promotion count completed":"Waiting for the next actual affordable quote; no request pending";
    }
    Result advance(ChocolateFactoryRuntime rt,ChocolateFactoryProfile profile,ChocolateFactoryObservation current,int hotbar,long now,BooleanSupplier authority){return advance(rt,profile,current,hotbar,now,authority,null);}
    Result advance(ChocolateFactoryRuntime rt,ChocolateFactoryProfile profile,ChocolateFactoryObservation current,int hotbar,long now,BooleanSupplier authority,ChocolateFactoryModule.RepeatTick episode){
        if(rt.state()!=ChocolateFactoryRuntime.State.ACKNOWLEDGED||receipt==null)return stop("Original acknowledged step unavailable; never rearm");
        if(completed>=requested)return stop("Selected promotion count completed");
        if(now<waitSince||now-waitSince>maximumWait)return stop("Original per-step fresh quote wait expired; no future request");
        if(current==null||current.session()!=receipt.session()||current.menu()!=receipt.menu()||current.screen()!=receipt.screen()||!current.scope().equals(receipt.scope())||!current.title().equals(receipt.title())||!current.contextBound()||!current.cursorEmpty()||hotbar!=rt.selection().selectedHotbar()||!protectedRows(receipt,current))return stop("Native context, cursor, hotbar or protected inventory changed");
        if(!current.fresh(now,profile.freshnessMillis())||!current.profileVerified()){reason="Waiting for a fresh complete native menu within the original wait";return Result.WAITING;}
        if(next==null)return stop("No next promotion quote observed; cap/unlock/future prices are not inferred");
        var q=current.quotes().get(target);var t=current.targets().get(target);var expected=receipt.targets().get(target);
        if(q==null||t==null||expected==null||q.kind()!=ChocolateFactoryProfile.Kind.EMPLOYEE||q.slot()!=next.slot()||q.level()!=next.level()||t.level()!=expected.level()||q.price().compareTo(next.price())!=0||!Objects.equals(q.fullIdentity(),next.fullIdentity()))return stop("Observed next quote/target changed outside this repeat; select again");
        var selected=rt.selection();
        if(rt.spent().add(rt.manualDeclaredDebit()).add(q.price()).compareTo(selected.budget())>0)return stop("Next observed quote exceeds selected cumulative budget");
        if(current.balance().isEmpty()||current.balance().get().subtract(q.price()).compareTo(selected.reserve())<0){reason="Waiting for observed wallet to fund "+q.price()+" while preserving reserve "+selected.reserve();return Result.WAITING;}
        if(!authority.getAsBoolean())return stop("Original module/config authority ended");
        if(!rt.select(profile,current,target,selected.budget(),selected.reserve(),now,episode))return stop("Fresh next quote refused: "+rt.reason());
        reason="Fresh exact quote selected; durable write-ahead required before next native input";return Result.SELECTED;
    }
    private Result stop(String text){reason=text;return Result.STOPPED;}
    private static boolean protectedRows(ChocolateFactoryObservation before,ChocolateFactoryObservation after){var rows=before.rows().values().stream().filter(ChocolateFactoryObservation.Row::playerInventory).toList();return after.rows().values().stream().filter(ChocolateFactoryObservation.Row::playerInventory).count()==rows.size()&&rows.stream().allMatch(r->Objects.equals(r,after.rows().get(r.slot())));}
}
