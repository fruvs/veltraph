package dev.veltraph.client.greenhouse;

import dev.veltraph.client.gui.MenuSnapshot;
import java.util.*;
import java.util.function.*;
import dev.veltraph.client.inventory.NativeStackIdentity;
import static dev.veltraph.client.greenhouse.GreenhouseModel.*;

/** One submitted native use and its isolated received menu, or an explicitly selected historical echo contract. */
public final class GreenhouseDiagnosticAssociation {
    public enum State { IDLE, BEFORE_USE, WAIT_MENU, ASSOCIATED, UNKNOWN }
    public record Selection(GreenhouseBindings bindings,Cell cell,int receiptSlot,String receiptItemId,
                            String sequencePrefix,String nativeTargetPrefix,long timeoutMs,boolean fullCapturedDiagnostics,boolean isolatedNativeMenu) {
        public Selection(GreenhouseBindings bindings,Cell cell,int receiptSlot,String receiptItemId,String sequencePrefix,String nativeTargetPrefix,long timeoutMs,boolean fullCapturedDiagnostics){
            this(bindings,cell,receiptSlot,receiptItemId,sequencePrefix,nativeTargetPrefix,timeoutMs,fullCapturedDiagnostics,false);
        }
        public Selection(GreenhouseBindings bindings,Cell cell,int receiptSlot,String receiptItemId,String sequencePrefix,String nativeTargetPrefix,long timeoutMs){
            this(bindings,cell,receiptSlot,receiptItemId,sequencePrefix,nativeTargetPrefix,timeoutMs,false,false);
        }
        public Selection {
            Objects.requireNonNull(bindings);Objects.requireNonNull(cell);
            if(receiptSlot<0||receiptSlot>255||receiptItemId==null||receiptItemId.isBlank()||timeoutMs<1||timeoutMs>30000)throw new IllegalArgumentException("Bounded native receipt selection required");
            if(isolatedNativeMenu){
                if(!fullCapturedDiagnostics||receiptSlot!=4||!bindings.menuTitle().equals("Crop Diagnostics")
                        ||bindings.profileId()==null||bindings.profileId().isBlank()||bindings.plotId()==null||bindings.plotId().isBlank()
                        ||bindings.diagnosticToolIdentity()==null||bindings.diagnosticToolIdentity().isBlank()
                        ||bindings.diagnosticToolSlot()<0||bindings.diagnosticToolSlot()>8||bindings.cells().size()!=1
                        ||sequencePrefix!=null||nativeTargetPrefix!=null||bindings.profileLine()!=null||bindings.plotLine()!=null)
                    throw new IllegalArgumentException("Isolated native inspection uses runtime context, not invented menu scope/echo lore");
            }else {
                if(sequencePrefix==null||sequencePrefix.isBlank()||nativeTargetPrefix==null||nativeTargetPrefix.isBlank())throw new IllegalArgumentException("Observed receipt sequence/target grammar required");
                GreenhouseBindings.parse(new com.google.gson.Gson().toJson(bindings));
            }
            if(fullCapturedDiagnostics&&(receiptSlot!=4||!bindings.menuTitle().equals("Crop Diagnostics")))throw new IllegalArgumentException("Full captured diagnostics require exact plant-row4 receipt and captured menu grammar");
            var c=bindings.cells().stream().filter(v->v.cell().equals(cell)).toList();
            if(c.size()!=1||c.getFirst().diagnosticTargetCell()==null)throw new IllegalArgumentException("Explicit selected cell and actual diagnostic target required");
            // The ordinary server menu has no spatial anchor. Record only the actual clicked block;
            // a distinct support/center/footprint anchor needs an independent observed mapping.
            if(isolatedNativeMenu&&!cell.equals(c.getFirst().diagnosticTargetCell()))throw new IllegalArgumentException("Native diagnostics can associate only the actual clicked block; a distinct plant anchor needs observed mapping evidence");
            if(isolatedNativeMenu&&(c.getFirst().slotName()==null||c.getFirst().slotName().isBlank()
                    ||!receiptItemId.equals(c.getFirst().itemId())||c.getFirst().cellLine()!=null))throw new IllegalArgumentException("Exact observed plant row required; no invented cell lore");
        }
        public GreenhouseBindings.CellBinding binding(){return bindings.cells().stream().filter(c->c.cell().equals(cell)).findFirst().orElseThrow();}
        public String targetLine(){Cell p=binding().diagnosticTargetCell();return nativeTargetPrefix+p.x()+" "+p.y()+" "+p.z();}
    }
    /** Ordinary player selection has no server profile/cell/request echoes. Values are explicit local scope and observed row/tool metadata. */
    public record NativeInspection(String profile,String plot,Cell plantCell,Cell interactionTarget,
                                   String cropName,String menuItemId,String exactTool,int toolSlot,long timeoutMs) {
        public Selection selection(){
            if(profile==null||profile.isBlank()||plot==null||plot.isBlank()||plantCell==null||interactionTarget==null
                    ||cropName==null||cropName.isBlank()||menuItemId==null||menuItemId.isBlank())throw new IllegalArgumentException("Explicit local scope, target and observed crop row required");
            var cell=new GreenhouseBindings.CellBinding(plantCell,cropName,menuItemId,null,null,null,null,null,null,null,null,null,null,null,interactionTarget);
            var bindings=new GreenhouseBindings("Crop Diagnostics",profile,plot,null,null,exactTool,toolSlot,List.of(cell));
            return new Selection(bindings,plantCell,4,menuItemId,null,null,timeoutMs,true,true);
        }
    }
    /** Native runtime references deliberately never serialized into provenance. */
    public record Custody(Object player,Object world,Object connection,Object owner,UUID token,long epoch,Context context,
                          Position position,float yaw,float pitch,int selectedSlot,NativeStackIdentity.Snapshot toolIdentity,GreenhouseNativeInventory inventory,
                          Cell rayCell,String face,Position hitPosition,String targetFingerprint,String plantFingerprint,boolean loaded,boolean alive,boolean cursorEmpty) {
        public Custody {Objects.requireNonNull(inventory);}
        public boolean complete(){return player!=null&&world!=null&&connection!=null&&owner!=null&&token!=null&&epoch>0&&context!=null&&context.identified()
                &&position!=null&&position.finite()&&Float.isFinite(yaw)&&Float.isFinite(pitch)&&toolIdentity!=null&&inventory.selectedSlot()==selectedSlot&&inventory.tool()!=null&&!inventory.tool().empty()&&toolIdentity.equals(inventory.tool().identity())
                &&rayCell!=null&&face!=null&&!face.isBlank()&&hitPosition!=null&&hitPosition.finite()&&targetFingerprint!=null&&!targetFingerprint.isBlank()&&plantFingerprint!=null&&!plantFingerprint.isBlank()&&loaded&&alive&&cursorEmpty&&inventory.cursorEmpty();}
        public boolean same(Custody c){return c!=null&&player==c.player&&world==c.world&&connection==c.connection&&owner==c.owner&&token.equals(c.token)&&epoch==c.epoch
                &&context.equals(c.context)&&position.equals(c.position)&&Float.compare(yaw,c.yaw)==0&&Float.compare(pitch,c.pitch)==0
                &&selectedSlot==c.selectedSlot&&toolIdentity.equals(c.toolIdentity)&&inventory.equals(c.inventory)&&rayCell.equals(c.rayCell)&&face.equals(c.face)&&hitPosition.equals(c.hitPosition)
                &&targetFingerprint.equals(c.targetFingerprint)&&plantFingerprint.equals(c.plantFingerprint)&&c.loaded&&c.alive&&c.cursorEmpty;}
    }
    public record Evidence(UUID request,Context context,Cell cell,Cell actualNativeTarget,String actualHand,String actualRayFace,Position actualRayHit,String targetFingerprint,
                           String plantFingerprint,String exactDiagnosticTool,GreenhouseNativeInventory.Projection exactInventory,int diagnosticSlot,long activationEpoch,long issuedAt,int nativeSequence,
                           long receivedAt,long menuEpoch,int containerId,int revision,String title,int matchedRowSlot,String matchedRowItem,int matchedRowCount,String matchedRowNativeComponents,String matchedRowPersistentIdentity,String receiptGrammar,Plant plant) {}
    /** Runtime receipt token: exact references and immutable native row identity, never restored from archive. */
    public record ReceiptToken(Object screen,Object menu,int containerId,int revision,long menuEpoch,String title,MenuSnapshot.Slot row,NativeStackIdentity.Snapshot nativeRow,List<MenuSnapshot.Slot> allRows,Map<Integer,NativeStackIdentity.Snapshot> allNativeRows) {
        public ReceiptToken(Object screen,Object menu,int containerId,int revision,long menuEpoch,String title,MenuSnapshot.Slot row,NativeStackIdentity.Snapshot nativeRow){this(screen,menu,containerId,revision,menuEpoch,title,row,nativeRow,List.of(),Map.of());}
        public ReceiptToken {Objects.requireNonNull(screen);Objects.requireNonNull(menu);Objects.requireNonNull(title);Objects.requireNonNull(row);Objects.requireNonNull(nativeRow);allRows=List.copyOf(allRows);allNativeRows=Map.copyOf(allNativeRows);
            if(allRows.size()>150||allRows.stream().anyMatch(MenuSnapshot.Slot::playerInventory)||allRows.stream().map(MenuSnapshot.Slot::id).distinct().count()!=allRows.size()
                    ||!allNativeRows.keySet().equals(new HashSet<>(allRows.stream().map(MenuSnapshot.Slot::id).toList())))throw new IllegalArgumentException("Bounded unique complete native non-player rows required");}
        public boolean completeFor(MenuSnapshot c){return !allRows.isEmpty()&&allRows.equals(c.slots().stream().filter(r->!r.playerInventory()).toList());}
        public boolean same(ReceiptToken c){return c!=null&&screen==c.screen&&menu==c.menu&&containerId==c.containerId&&revision==c.revision&&menuEpoch==c.menuEpoch&&title.equals(c.title)&&row.equals(c.row)&&nativeRow.equals(c.nativeRow)&&allRows.equals(c.allRows)&&allNativeRows.equals(c.allNativeRows);}
        public boolean matchesSnapshot(MenuSnapshot c,long epoch,int selectedRow){return c!=null&&containerId==c.id()&&revision==c.revision()&&menuEpoch==epoch&&title.equals(c.title())&&row.id()==selectedRow&&c.slots().stream().filter(r->r.id()==selectedRow&&!r.playerInventory()).toList().equals(List.of(row));}
    }
    public record Associated(Evidence evidence,Custody custody,Optional<GreenhouseCapturedMenus.Diagnostics> diagnostic) {
        public Associated(Evidence evidence,Custody custody){this(evidence,custody,Optional.empty());}
        public Associated {Objects.requireNonNull(diagnostic);}
    }
    private State state=State.IDLE;private String reason="No selected native diagnostic request";
    private Selection selection;private Custody before;private UUID request;private long issuedAt,last,minimumMenuEpoch;private int nativeSequence=-1;
    private Associated associated;private ReceiptToken receivedToken;
    private Object unresolvedNativeConnection;
    private Object receivedListener,openedMenu,openedScreen;private int openedContainer=-1;private ReceiptToken nativeContentToken;private long requestVersion;private boolean confirming,durable;
    public boolean begin(Selection s,Custody c,long now,long menuEpoch,boolean screenFree){
        if(state==State.BEFORE_USE||state==State.WAIT_MENU)return false;
        if(s!=null&&s.isolatedNativeMenu()&&c!=null&&c.connection()==unresolvedNativeConnection){reason="Unresolved prior native response on this connection; reconnect before a new inspection";return false;}
        requestVersion++;durable=false;associated=null;receivedToken=null;
        receivedListener=openedMenu=openedScreen=null;openedContainer=-1;nativeContentToken=null;
        if(s==null||c==null||!c.complete()||!screenFree||now<0||menuEpoch<0||c.selectedSlot()!=s.bindings().diagnosticToolSlot()
                ||!c.toolIdentity().persistentCanonicalEncoding().equals(s.bindings().diagnosticToolIdentity())||!c.rayCell().equals(s.binding().diagnosticTargetCell())
                ||(s.isolatedNativeMenu()&&!s.cell().equals(c.rayCell()))
                ||!c.context().profile().equals(s.bindings().profileId())||!c.context().plot().equals(s.bindings().plotId())){unknown("Exact native selection/custody/target is unavailable");return false;}
        selection=s;before=c;issuedAt=last=now;minimumMenuEpoch=menuEpoch;nativeSequence=-1;request=UUID.randomUUID();state=State.BEFORE_USE;reason="Target/tool/custody recorded before native use";return true;
    }
    /** Called at the real client Connection submission boundary, never by issue result or a timer. */
    public boolean submitted(Custody c,int sequence,Cell target,String face,Position actualHit,long now){
        if(state!=State.BEFORE_USE||nativeSequence>=0||sequence<0||!before.same(c)||now<issuedAt||!before.rayCell().equals(target)||!before.face().equals(face)||!before.hitPosition().equals(actualHit)){
            if(active())unknown("Unowned, duplicate or wrong native use packet");return false;
        }
        nativeSequence=sequence;return true;
    }
    public void nativeCallReturned(boolean returnedNormally){
        if(state!=State.BEFORE_USE)return;
        if(!returnedNormally||nativeSequence<0){unknown("No confirmed native submission or transport failed");return;}
        state=State.WAIT_MENU;reason="Awaiting a newer exact native sequence/target receipt";
    }
    /** Actual inbound handler observations. They associate an isolated transaction operationally; they are not a server target echo. */
    public void nativeMenuOpened(Object listener,Object screen,Object menu,int container,String title,Custody custody,long now){
        if(selection==null||!selection.isolatedNativeMenu()||state!=State.WAIT_MENU)return;
        if(!check(custody,now))return;
        if(listener!=before.connection()||screen==null||menu==null||container<=0||!"Crop Diagnostics".equals(title)||openedMenu!=null){
            unknown("Foreign or competing native menu response");return;
        }
        receivedListener=listener;openedScreen=screen;openedMenu=menu;openedContainer=container;
    }
    public void nativeMenuContents(Object listener,ReceiptToken token,Custody custody,long now){
        if(selection==null||!selection.isolatedNativeMenu()||state!=State.WAIT_MENU)return;
        if(!check(custody,now))return;
        if(listener!=receivedListener||token==null||token.screen()!=openedScreen||token.menu()!=openedMenu
                ||token.containerId()!=openedContainer||!token.title().equals("Crop Diagnostics")||token.allRows().size()!=45){
            unknown("Native content did not belong to the actual received diagnostics menu");return;
        }
        nativeContentToken=token;
    }
    public Optional<Associated> observe(MenuSnapshot menu,Map<Cell,String> actualWorldStates,Custody c,long now,long menuEpoch,ReceiptToken token){
        if(state!=State.WAIT_MENU)return Optional.empty();
        if(!check(c,now))return Optional.empty();
        if(menu==null)return Optional.empty();
        if(selection.isolatedNativeMenu()&&nativeContentToken==null)return Optional.empty(); // Genuine open may precede complete content by several ticks.
        if(token==null||!token.matchesSnapshot(menu,menuEpoch,selection.receiptSlot())){unknown("Received native screen/menu/row token does not match the snapshot");return Optional.empty();}
        if(menuEpoch<=minimumMenuEpoch||now<=issuedAt||!menu.title().equals(selection.bindings().menuTitle())){unknown("Old or foreign diagnostic menu");return Optional.empty();}
        var receipt=menu.slots().stream().filter(s->!s.playerInventory()&&!s.locked()&&s.id()==selection.receiptSlot()&&s.count()>0&&s.itemId().equals(selection.receiptItemId())).toList();
        if(receipt.size()!=1){unknown("Exact native receipt row missing or ambiguous");return Optional.empty();}
        var lore=receipt.getFirst().lore();var b=selection.bindings();var matched=receipt.getFirst();
        GreenhouseBindings single=null;
        if(selection.isolatedNativeMenu()){
            if(nativeContentToken==null||!nativeContentToken.same(token)||!token.completeFor(menu)){
                unknown("No matching normally applied native diagnostics open/content transaction");return Optional.empty();
            }
            if(!matched.name().equals(selection.binding().slotName())||matched.componentIdentity().isBlank()){
                unknown("Received plant row differs from the explicitly selected observed species");return Optional.empty();
            }
        }else {
            var sequences=lore.stream().filter(l->l.startsWith(selection.sequencePrefix())).toList();
            var targets=lore.stream().filter(l->l.startsWith(selection.nativeTargetPrefix())).toList();
            if(sequences.size()!=1||targets.size()!=1||!sequences.getFirst().equals(selection.sequencePrefix()+nativeSequence)||!targets.getFirst().equals(selection.targetLine())){
                unknown("Diagnostic sequence/target does not match this issued request");return Optional.empty();
            }
            single=new GreenhouseBindings(b.menuTitle(),b.profileId(),b.plotId(),b.profileLine(),b.plotLine(),b.diagnosticToolIdentity(),b.diagnosticToolSlot(),List.of(selection.binding()));
            if(matched.componentIdentity().isBlank()||Collections.frequency(lore,b.profileLine())!=1||Collections.frequency(lore,b.plotLine())!=1
                    ||b.cells().stream().map(GreenhouseBindings.CellBinding::cellLine).filter(lore::contains).count()!=1){unknown("Ambiguous or incomplete matched receipt row identity/scope");return Optional.empty();}
        }
        var scoped=new MenuSnapshot(menu.id(),menu.title(),List.of(matched),menu.revision());
        Optional<GreenhouseCapturedDiagnosticsPlant.Observed> full=Optional.empty();
        Optional<Map<Cell,Plant>> plants;
        if(selection.fullCapturedDiagnostics()){
            if(!token.completeFor(menu)){unknown("Full native diagnostic row identities are unavailable");return Optional.empty();}
            full=GreenhouseCapturedDiagnosticsPlant.read(selection,menu,actualWorldStates,now);
            plants=full.map(d->Map.of(selection.cell(),d.plant()));
        }else plants=single.read(scoped,actualWorldStates);
        if(plants.isEmpty()||!before.plantFingerprint().equals(actualWorldStates.get(selection.cell()))){unknown("Wrong profile/plot/cell/species or changed world fingerprint");return Optional.empty();}
        var p=plants.get().get(selection.cell());
        if(p==null||!p.identified()){unknown("Returned plant remains UNKNOWN");return Optional.empty();}
        var evidence=new Evidence(request,before.context(),selection.cell(),before.rayCell(),"MAIN_HAND",before.face(),before.hitPosition(),before.targetFingerprint(),before.plantFingerprint(),before.toolIdentity().persistentCanonicalEncoding(),before.inventory().persistentProjection(),before.selectedSlot(),before.epoch(),issuedAt,nativeSequence,now,menuEpoch,menu.id(),menu.revision(),menu.title(),matched.id(),matched.itemId(),matched.count(),matched.componentIdentity(),token.nativeRow().persistentCanonicalEncoding(),selection.isolatedNativeMenu()?"ISOLATED_NATIVE_USE_OPEN_CONTENT: runtime context; no server target echo":selection.sequencePrefix()+" / "+selection.nativeTargetPrefix(),p);
        receivedToken=token;associated=new Associated(evidence,before,full.map(GreenhouseCapturedDiagnosticsPlant.Observed::diagnostic));state=State.ASSOCIATED;reason=selection.isolatedNativeMenu()?"Isolated actual use/open/content inspection associated; no server target echo or inferred care permission":"Exact echoed native request receipt associated; no inferred recipe or timer";return Optional.of(associated);
    }
    public boolean check(Custody c,long now){
        if(!active())return false;
        if(now<last||now-issuedAt>=selection.timeoutMs()||!before.same(c)){unknown("Diagnostic timeout or custody/tool/actor/pose/cursor/context changed");return false;}
        last=now;return true;
    }
    public void foreignNativeAction(){if(active()||awaitingDurability())unknown("Another native use/break made receipt origin ambiguous");}
    private record Commit(long version,UUID request,Associated accepted,Custody custody,ReceiptToken receipt,Selection selection,long issuedAt) {}
    private boolean current(Commit c){return c!=null&&requestVersion==c.version()&&Objects.equals(request,c.request())&&state==State.ASSOCIATED
        &&associated==c.accepted()&&before==c.custody()&&receivedToken==c.receipt()&&selection==c.selection()&&!durable;}
    private void refuse(Commit c){if(current(c))unknown("Association persistence was not confirmed; no selected-plan permission");}
    /** Pure callers provide snapshot suppliers. Native publication additionally requires its trusted direct final leaf. */
    public boolean confirmDurable(BooleanSupplier persistence,Supplier<Custody> captureCurrent,Supplier<ReceiptToken> captureReceipt,LongSupplier clock){
        return confirmDurable(persistence,captureCurrent,captureReceipt,clock,()->true);
    }
    public boolean confirmDurable(BooleanSupplier persistence,Supplier<Custody> captureCurrent,Supplier<ReceiptToken> captureReceipt,LongSupplier clock,BooleanSupplier finalNativeLeaf){
        if(confirming||state!=State.ASSOCIATED||associated==null||receivedToken==null||durable)return false;
        var captured=new Commit(requestVersion,request,associated,before,receivedToken,selection,issuedAt);confirming=true;
        try{
            if(!commitFrame(captured,captureCurrent,captureReceipt,clock)||!current(captured)){refuse(captured);return false;}
            boolean saved=persistence.getAsBoolean();
            if(!current(captured)||!saved){refuse(captured);return false;}
            if(!commitFrame(captured,captureCurrent,captureReceipt,clock)||!current(captured)){refuse(captured);return false;}
            boolean nativeCurrent=finalNativeLeaf.getAsBoolean();
            if(!current(captured)||!nativeCurrent){refuse(captured);return false;}
            durable=true;return true;
        }catch(RuntimeException failure){refuse(captured);return false;}
        finally{confirming=false;}
    }
    private boolean commitFrame(Commit captured,Supplier<Custody> captureCurrent,Supplier<ReceiptToken> captureReceipt,LongSupplier clock){
        if(!current(captured))return false;
        // A public clock callback must precede, never follow, the native snapshots used for this frame.
        long now=clock.getAsLong();if(!current(captured))return false;
        if(now<last||now<captured.accepted().evidence().receivedAt()||now-captured.issuedAt()>=captured.selection().timeoutMs())return false;
        var custody=captureCurrent.get();if(!current(captured))return false;
        var receipt=captureReceipt.get();if(!current(captured))return false;
        if(!captured.custody().same(custody)||!captured.receipt().same(receipt))return false;
        last=now;return true;
    }
    public boolean stillMatchedCustody(Custody now){return state==State.ASSOCIATED&&before!=null&&before.same(now);}
    public void refuseDurability(){unknown("Association persistence was not confirmed; no selected-plan permission");}
    /** A normal completed inspection keeps its immutable readonly observation. Pending save cancellation revokes it. */
    public void cancel(){if(active()||awaitingDurability())unknown("Selected diagnostic cancelled; late replies cannot associate");}
    public boolean awaitingDurability(){return state==State.ASSOCIATED&&!durable;}
    public boolean durable(){return state==State.ASSOCIATED&&durable;}
    private void unknown(String why){if(selection!=null&&selection.isolatedNativeMenu()&&before!=null&&nativeSequence>=0)unresolvedNativeConnection=before.connection();requestVersion++;durable=false;state=State.UNKNOWN;reason=why;associated=null;receivedToken=null;}
    public boolean active(){return state==State.BEFORE_USE||state==State.WAIT_MENU;}
    public Object pendingRequestIdentity(){return active()?request:null;}
    public boolean hasUnresolvedNativeResponse(Object connection){return connection!=null&&connection==unresolvedNativeConnection;}
    public State state(){return state;}public String status(){return state+": "+reason;}
    public Optional<Associated> associated(){return Optional.ofNullable(associated);}
    public int submittedSequence(){return nativeSequence;}public Optional<Selection> selection(){return Optional.ofNullable(selection);}
}
