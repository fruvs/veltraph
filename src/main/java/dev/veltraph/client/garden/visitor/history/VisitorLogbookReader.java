package dev.veltraph.client.garden.visitor.history;

import java.util.*;

/** Bounded prepared-menu reader. No guessed caption, encrypted marker, slot or timer is baked in. */
public final class VisitorLogbookReader {
    public record Bindings(String title,String nextCaptionFragment,String pagePrefix,
            List<String> excludedLoreFragments,List<String> notServedLoreFragments,
            Set<String> excludedItemKeys,String provenance) {
        public Bindings {
            excludedLoreFragments=List.copyOf(excludedLoreFragments);notServedLoreFragments=List.copyOf(notServedLoreFragments);
            excludedItemKeys=Set.copyOf(excludedItemKeys);
            if(blank(title)||blank(nextCaptionFragment)||blank(pagePrefix)||blank(provenance)
                    ||excludedLoreFragments.size()!=2||notServedLoreFragments.size()!=2
                    ||excludedLoreFragments.stream().anyMatch(VisitorLogbookReader::blank)
                    ||notServedLoreFragments.stream().anyMatch(VisitorLogbookReader::blank))
                throw new IllegalArgumentException("Unknown actual logbook bindings");
        }
    }
    public record Row(int slot,boolean serverSlot,boolean hasItem,String itemKey,String displayName,List<String> lore) {
        public Row {
            lore=List.copyOf(lore);
            if(slot<0||slot>511||lore.size()>64||lore.stream().anyMatch(s->s==null||s.length()>512)
                    ||itemKey==null||itemKey.length()>160||displayName==null||displayName.length()>512)
                throw new IllegalArgumentException("Invalid bounded logbook row");
        }
    }
    /** Sequence is from an actual normal inbound full-container handler, not menu state or a local click. */
    public record Page(VisitorHistoryContext context,Object menu,int menuId,int revision,String title,
            boolean cursorEmpty,boolean fullServerContent,long receivedSequence,long receivedAt,
            long requestSequence,List<Row> rows) {
        public Page {
            Objects.requireNonNull(context);Objects.requireNonNull(menu);rows=List.copyOf(rows);
            if(rows.size()>512||menuId<1||revision<0||receivedSequence<1||requestSequence<1||receivedAt<0)
                throw new IllegalArgumentException("Unknown server page provenance");
        }
    }
    public record NativeRequest(VisitorHistoryContext context,Object menu,int menuId,long sequence,
            long submittedAt,int clickedSlot,boolean opensConfirmedFirstPage) {
        public NativeRequest {
            Objects.requireNonNull(context);Objects.requireNonNull(menu);
            if(menuId<1||sequence<1||submittedAt<0||clickedSlot< -1||clickedSlot>511)throw new IllegalArgumentException("Unknown native page request");
        }
    }
    public enum State { IDLE, READING, WAITING_PAGE, COMPLETE, CANCELLED, FAILED }
    public static final class CompletedBook {
        private final VisitorLogbookReader issuer;private final long generation;private boolean consumed;private Object persistenceReservation;
        private final VisitorHistoryContext context;private final Set<String> seenNames,knownNames;
        private final long completedAt,lastReceivedSequence;private final String provenance;
        private CompletedBook(VisitorLogbookReader issuer,long generation,VisitorHistoryContext context,Set<String> seen,Set<String> known,long now,long sequence,String provenance){
            this.issuer=issuer;this.generation=generation;this.context=context;this.seenNames=Set.copyOf(seen);this.knownNames=Set.copyOf(known);this.completedAt=now;this.lastReceivedSequence=sequence;this.provenance=provenance;
        }
        public VisitorHistoryContext context(){return context;} public Set<String> seenNames(){return seenNames;} public Set<String> knownNames(){return knownNames;}
        public long completedAt(){return completedAt;} public long lastReceivedSequence(){return lastReceivedSequence;}
        public String provenance(){return provenance;}
        boolean canReservePersistence(){return !consumed&&persistenceReservation==null&&issuer.state==State.COMPLETE&&issuer.completion==this&&issuer.generation==generation;}
        boolean reservePersistence(Object operation){
            if(operation==null||!canReservePersistence())return false;
            persistenceReservation=operation;return true;
        }
        boolean pendingPersistence(Object operation){return operation!=null&&persistenceReservation==operation&&!consumed;}
        boolean finishPersistence(Object operation){if(!pendingPersistence(operation))return false;consumed=true;return true;}
    }
    private final Bindings bindings;private final int maxPages;private final long timeoutMs;
    private final Map<String,Boolean> entries=new LinkedHashMap<>();
    private State state=State.IDLE;private String status="Idle";private NativeRequest request;
    private VisitorHistoryContext context;private Object menu;private int menuId,expectedPage=1,nextSlot=-1;
    private long started,lastTick,lastSequence,generation;private int lastRevision=-1;private CompletedBook completion;
    public VisitorLogbookReader(Bindings bindings,int maxPages,long timeoutMs){
        this.bindings=Objects.requireNonNull(bindings);
        if(maxPages<1||maxPages>128||timeoutMs<1||timeoutMs>120_000)throw new IllegalArgumentException("Logbook read bound");
        this.maxPages=maxPages;this.timeoutMs=timeoutMs;
    }
    public boolean start(NativeRequest actualFirstPageRequest,long now){
        if(active())return false;
        if(generation==Long.MAX_VALUE){finish(State.FAILED,"Reader generation exhausted");return false;}generation++;
        entries.clear();completion=null;nextSlot=-1;lastSequence=0;lastRevision=-1;expectedPage=1;
        if(actualFirstPageRequest==null||!actualFirstPageRequest.opensConfirmedFirstPage()||actualFirstPageRequest.clickedSlot()!=-1||now<actualFirstPageRequest.submittedAt()
                ||now-actualFirstPageRequest.submittedAt()>=timeoutMs){
            finish(State.FAILED,"Actual first-page opening request unknown");return false;
        }
        request=actualFirstPageRequest;context=request.context();menu=request.menu();menuId=request.menuId();
        started=request.submittedAt();lastTick=now;state=State.READING;status="Awaiting complete received first page";return true;
    }
    public void observe(Page page,VisitorHistoryContext current,long now){
        if(!active())return;
        if(now<lastTick||now-started>=timeoutMs){finish(State.FAILED,"Original logbook deadline or clock failed");return;}
        lastTick=now;
        if(!context.sameSession(current)){finish(State.CANCELLED,"Account/profile/Garden or session changed");return;}
        if(page==null)return;
        if(!context.sameSession(page.context())||page.menu()!=menu||page.menuId()!=menuId
                ||!bindings.title().equals(page.title())||!page.cursorEmpty()){
            finish(State.CANCELLED,"Owned logbook menu or context changed");return;
        }
        if(!page.fullServerContent()||page.receivedSequence()<=lastSequence||page.receivedAt()<request.submittedAt()
                ||page.receivedAt()>now||page.requestSequence()!=request.sequence())return;
        if(state==State.WAITING_PAGE){return;} // A request must be bound before accepting another page.
        if(lastRevision>=0&&page.revision()<=lastRevision){finish(State.FAILED,"Page revision did not advance");return;}
        var slots=new HashSet<Integer>();var pageEntries=new LinkedHashMap<String,Boolean>();var next=new ArrayList<Row>();
        for(var row:page.rows()){
            if(!row.serverSlot()||!row.hasItem())continue;
            if(!slots.add(row.slot())){finish(State.FAILED,"Duplicate server slot");return;}
            String display;
            try{display=VisitorHistoryStore.name(row.displayName());}catch(IllegalArgumentException ignored){display="";}
            if(row.itemKey().equals("minecraft:arrow")&&display.contains(bindings.nextCaptionFragment())){next.add(row);continue;}
            if(row.slot()<10||row.slot()>=45||bindings.excludedItemKeys().contains(row.itemKey()))continue;
            if(contains(row.lore(),bindings.excludedLoreFragments()))continue;
            if(display.isEmpty()||pageEntries.containsKey(display)||entries.containsKey(display)){
                finish(State.FAILED,"Ambiguous or duplicate visitor name");return;
            }
            pageEntries.put(display,!contains(row.lore(),bindings.notServedLoreFragments()));
        }
        if(next.size()>1){finish(State.FAILED,"Ambiguous next-page button");return;}
        if(!next.isEmpty()){
            var reported=parsePageMinusOne(next.getFirst().lore(),bindings.pagePrefix());
            if(reported.isEmpty()||reported.getAsInt()!=expectedPage){finish(State.FAILED,"Unreadable or skipped page number");return;}
            if(expectedPage>=maxPages){finish(State.FAILED,"Logbook page bound reached before completion");return;}
            nextSlot=next.getFirst().slot();
        }
        entries.putAll(pageEntries);lastSequence=page.receivedSequence();lastRevision=page.revision();
        if(next.isEmpty()){
            var seen=new LinkedHashSet<String>();entries.forEach((n,s)->{if(s)seen.add(n);});
            completion=new CompletedBook(this,generation,context,seen,entries.keySet(),now,lastSequence,bindings.provenance()+"; complete pages="+expectedPage);
            state=State.COMPLETE;status="Complete received logbook coverage";request=null;nextSlot=-1;
        }else{state=State.WAITING_PAGE;status="Prepared next native page request";}
    }
    /** Called only with the actual submitted native next-button request; it grants no click permission. */
    public boolean bindNextRequest(NativeRequest actualRequest,VisitorHistoryContext current,long now){
        if(state!=State.WAITING_PAGE||!context.sameSession(current)||actualRequest==null
                ||!context.sameSession(actualRequest.context())||actualRequest.menu()!=menu||actualRequest.menuId()!=menuId
                ||actualRequest.opensConfirmedFirstPage()||actualRequest.clickedSlot()!=nextSlot||actualRequest.sequence()<=request.sequence()
                ||actualRequest.submittedAt()<lastTick||actualRequest.submittedAt()>now
                ||now<lastTick||now-started>=timeoutMs){finish(State.CANCELLED,"Native next request provenance changed");return false;}
        request=actualRequest;expectedPage++;state=State.READING;nextSlot=-1;lastTick=now;return true;
    }
    public static OptionalInt parsePageMinusOne(List<String> lore,String prefix){
        if(blank(prefix))return OptionalInt.empty();String match=null;
        for(String line:lore)if(line!=null&&line.startsWith(prefix)){if(match!=null)return OptionalInt.empty();match=line.trim();}
        if(match==null)return OptionalInt.empty();int i=match.lastIndexOf(' ');if(i<0)return OptionalInt.empty();
        try{int n=Integer.parseInt(match.substring(i+1));return n>1?OptionalInt.of(n-1):OptionalInt.empty();}
        catch(NumberFormatException bad){return OptionalInt.empty();}
    }
    private static boolean contains(List<String> lore,List<String> fragments){for(String line:lore)for(String marker:fragments)if(line.contains(marker))return true;return false;}
    private static boolean blank(String s){return s==null||s.isBlank()||s.length()>512;}
    private void finish(State next,String why){state=next;status=why;entries.clear();completion=null;request=null;nextSlot=-1;}
    public void cancel(){finish(State.CANCELLED,"Cancelled; prior history remains untouched");}
    public boolean active(){return state==State.READING||state==State.WAITING_PAGE;}
    public State state(){return state;}public String status(){return status;}
    public OptionalInt preparedNextSlot(){return nextSlot>=0?OptionalInt.of(nextSlot):OptionalInt.empty();}
    public Optional<CompletedBook> completion(){return Optional.ofNullable(completion);}
}
