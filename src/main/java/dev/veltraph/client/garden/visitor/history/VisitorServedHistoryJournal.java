package dev.veltraph.client.garden.visitor.history;

import java.util.*;

/** Pure journal for the native offer transaction. Local COMPLETE, click or predicted counts cannot mint Served. */
public final class VisitorServedHistoryJournal {
    /** Native adapter's immutable full component identity, not display name; empty slots have identity "" and zero. */
    public record Stack(String identity,int count) {
        public Stack {if(identity==null||identity.length()>16384||count<0||count>999_999||(identity.isEmpty()!=(count==0)))throw new IllegalArgumentException("Invalid inventory identity/count");}
    }
    public record Request(VisitorHistoryContext context,String visitorName,Object menu,int menuId,
            int originalRevision,long actualOutboundSequence,long requestedAt,Map<String,Integer> required,
            List<Stack> before,boolean emptyCursor) {
        public Request {
            Objects.requireNonNull(context);Objects.requireNonNull(menu);visitorName=VisitorHistoryStore.name(visitorName);
            required=Map.copyOf(required);before=List.copyOf(before);
            if(menuId<1||originalRevision<0||actualOutboundSequence<1||requestedAt<0||before.size()!=36||required.isEmpty()
                    ||required.size()>36||!emptyCursor||required.entrySet().stream().anyMatch(e->e.getKey().isBlank()||e.getValue()<1))
                throw new IllegalArgumentException("Unknown submitted serve request");
        }
    }
    /** Supplied only after the original actual inbound handler returns normally on the client thread. */
    public record InventoryReceipt(VisitorHistoryContext context,long nativeReceiveSequence,long requestSequence,
            long receivedAt,List<Stack> inventory,boolean normalHandlerReturn,boolean full36ServerInventory,boolean emptyCursor) {
        public InventoryReceipt {Objects.requireNonNull(context);inventory=List.copyOf(inventory);
            if(nativeReceiveSequence<1||requestSequence<1||receivedAt<0||inventory.size()>36)throw new IllegalArgumentException("Unknown native inventory receipt");}
    }
    public record MenuReceipt(VisitorHistoryContext context,Object ownedMenu,int menuId,int revision,
            long nativeReceiveSequence,long requestSequence,long receivedAt,boolean normalHandlerReturn,
            boolean serverClosedOwnedMenu,boolean originalAcceptAbsent,boolean emptyCursor) {
        public MenuReceipt {Objects.requireNonNull(context);Objects.requireNonNull(ownedMenu);
            if(menuId<1||revision<0||nativeReceiveSequence<1||requestSequence<1||receivedAt<0)throw new IllegalArgumentException("Unknown native menu receipt");}
    }
    public static final class Served {
        private final VisitorHistoryContext context;private final String name;private final long completedAt,requestSequence,inventorySequence,menuSequence;private boolean consumed;private Object persistenceReservation;
        private Served(VisitorHistoryContext context,String name,long now,long requestSequence,long inventorySequence,long menuSequence){this.context=context;this.name=name;completedAt=now;this.requestSequence=requestSequence;this.inventorySequence=inventorySequence;this.menuSequence=menuSequence;}
        public VisitorHistoryContext context(){return context;}public String visitorName(){return name;}public long completedAt(){return completedAt;}
        public String provenance(){return "Native full36 consumption + menu outcome; request="+requestSequence+", inventory="+inventorySequence+", menu="+menuSequence+", at="+completedAt;}
        long evidenceSequence(){return Math.max(inventorySequence,menuSequence);}
        boolean canReservePersistence(){return !consumed&&persistenceReservation==null;}
        boolean reservePersistence(Object operation){if(operation==null||!canReservePersistence())return false;persistenceReservation=operation;return true;}
        boolean pendingPersistence(Object operation){return operation!=null&&persistenceReservation==operation&&!consumed;}
        boolean finishPersistence(Object operation){if(!pendingPersistence(operation))return false;consumed=true;return true;}
    }
    private final long timeoutMs;private Request request;private InventoryReceipt inventory;private MenuReceipt menu;
    private long lastTick;private boolean retired;
    public VisitorServedHistoryJournal(long timeoutMs){if(timeoutMs<1||timeoutMs>120_000)throw new IllegalArgumentException("Serve history deadline");this.timeoutMs=timeoutMs;}
    public boolean begin(Request actualSubmittedRequest,VisitorHistoryContext current,long now){
        if(request!=null||retired||actualSubmittedRequest==null||!actualSubmittedRequest.context().sameSession(current)
                ||now<actualSubmittedRequest.requestedAt()||now-actualSubmittedRequest.requestedAt()>=timeoutMs)return false;
        var counts=counts(actualSubmittedRequest.before());
        for(var need:actualSubmittedRequest.required().entrySet())if(counts.getOrDefault(need.getKey(),0)<need.getValue())return false;
        request=actualSubmittedRequest;lastTick=now;return true;
    }
    public void receiveInventory(InventoryReceipt actual,VisitorHistoryContext current,long now){
        if(!live(current,now)||actual==null)return;
        if(!actual.context().sameSession(current)||!actual.normalHandlerReturn()||!actual.full36ServerInventory()||!actual.emptyCursor()
                ||actual.inventory().size()!=36||actual.requestSequence()!=request.actualOutboundSequence()
                ||actual.nativeReceiveSequence()<=request.actualOutboundSequence()||actual.receivedAt()<request.requestedAt()||actual.receivedAt()>now){retire();return;}
        if(inventory!=null&&actual.nativeReceiveSequence()<=inventory.nativeReceiveSequence()){retire();return;}
        if(!exactConsumption(request.before(),actual.inventory(),request.required())){retire();return;}
        inventory=actual;
    }
    public void receiveMenu(MenuReceipt actual,VisitorHistoryContext current,long now){
        if(!live(current,now)||actual==null)return;
        boolean changed=actual.serverClosedOwnedMenu()||(actual.revision()>request.originalRevision()&&actual.originalAcceptAbsent());
        if(!actual.context().sameSession(current)||actual.ownedMenu()!=request.menu()||actual.menuId()!=request.menuId()
                ||!actual.normalHandlerReturn()||!actual.emptyCursor()||!changed||actual.requestSequence()!=request.actualOutboundSequence()
                ||actual.nativeReceiveSequence()<=request.actualOutboundSequence()||actual.receivedAt()<request.requestedAt()||actual.receivedAt()>now){retire();return;}
        if(menu!=null&&actual.nativeReceiveSequence()<=menu.nativeReceiveSequence()){retire();return;}menu=actual;
    }
    /** Existing offer runtime must finish successfully too; this boolean alone is never enough. */
    public Optional<Served> finish(boolean existingOfferCompleted,VisitorHistoryContext current,long now){
        if(!live(current,now)||!existingOfferCompleted||inventory==null||menu==null)return Optional.empty();
        var proof=new Served(request.context(),request.visitorName(),now,request.actualOutboundSequence(),inventory.nativeReceiveSequence(),menu.nativeReceiveSequence());retire();return Optional.of(proof);
    }
    private boolean live(VisitorHistoryContext current,long now){
        if(request==null||retired)return false;
        if(!request.context().sameSession(current)||now<lastTick||now-request.requestedAt()>=timeoutMs){retire();return false;}
        lastTick=now;return true;
    }
    public void cancel(){retire();}
    private void retire(){retired=true;request=null;inventory=null;menu=null;}
    private static Map<String,Integer> counts(List<Stack> stacks){var counts=new HashMap<String,Integer>();for(var s:stacks)if(!s.identity().isEmpty())counts.merge(s.identity(),s.count(),Math::addExact);return counts;}
    private static boolean exactConsumption(List<Stack> before,List<Stack> after,Map<String,Integer> required){
        var consumed=new HashMap<String,Integer>();
        for(int i=0;i<36;i++){
            var b=before.get(i);var a=after.get(i);
            if(b.equals(a))continue;
            if(b.identity().isEmpty()||(!a.identity().isEmpty()&&!a.identity().equals(b.identity()))||a.count()>b.count()||!required.containsKey(b.identity()))return false;
            consumed.merge(b.identity(),b.count()-a.count(),Math::addExact);
        }
        return consumed.equals(required);
    }
}
