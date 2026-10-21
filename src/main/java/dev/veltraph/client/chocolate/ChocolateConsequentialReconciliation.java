package dev.veltraph.client.chocolate;

/** A manual decision names its original intent/scope and a current loaded native inspection. */
final class ChocolateConsequentialReconciliation {
    private ChocolateConsequentialReconciliation() {}
    static String inspect(ChocolateActionJournal journal,String declaredScope,ChocolateFactoryObservation o,long now){
        if(journal==null||!journal.pending()||!journal.scope().equals(declaredScope))throw new IllegalArgumentException("Repeat the exact pending intent and its original local scope from actions");
        if(o==null||o.session()==null||o.menu()==null||o.screen()==null||!o.cursorEmpty()||!o.fresh(now,5000)||!CapturedChocolateFactoryParser.parse(o.title(),o.rows()).complete()&&!ChocolateConsequentialParser.preview(o)&&!(journal.action().startsWith("SELECTED_")&&ChocolateSelectedTransactions.complete(o)&&(o.title().equals("Confirm Purchase")||java.util.Arrays.stream(ChocolateSelectedTransactions.Action.values()).anyMatch(a->ChocolateSelectedTransactions.title(a).equals(o.title())))))throw new IllegalArgumentException("Inspect a fresh complete loaded Factory or exact captured prestige preview before declaring the result");
        if(o.contextBound()&&!o.scope().equals("unconfigured")&&!o.scope().equals(declaredScope))throw new IllegalArgumentException("Inspection is already bound to a different local scope; inspect the intended account/menu first");
        var state=CapturedChocolateFactoryParser.parse(o.title(),o.rows());return "Manual scope declaration="+declaredScope+"; captured account identity remains unproven; refreshed native title="+o.title()+"; revision="+o.revision()+"; inspectedMillis="+o.observedMillis()+"; Factory="+state.factoryLevel()+"; wallet="+state.balance()+"; earned="+state.prestigeProgress()+"; all-time="+state.allTime()+"; Tower="+CapturedChocolateFactoryParser.tower(o.rows());
    }
}
