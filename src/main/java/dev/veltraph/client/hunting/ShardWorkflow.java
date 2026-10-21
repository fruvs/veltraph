package dev.veltraph.client.hunting;

import dev.veltraph.client.gui.MenuSnapshot;
import java.util.*;
import java.util.regex.Pattern;
import java.math.BigDecimal;

/** Bounded source-shaped shard workflow. Requests advance only on fresh server menus/inventory. */
public final class ShardWorkflow {
    public enum State { IDLE, OPEN_SHARDS_MENU, CLICK_MASS_SYPHON, CLICK_CONFIRM_MASS_SYPHON,
        WAIT_SYPHON_ACK, EXIT_SHARDS_MENU, OPEN_ATTRIBUTES, ENABLE_ADVANCED_MODE, READ_PAGE, EXIT_MENU,
        OPEN_BAZAAR_MENU, CLICK_SHARD, CLICK_BUY_INSTANTLY, CLICK_BUY_ONE, CLICK_CUSTOM_AMOUNT,
        ENTER_AMOUNT, CONFIRM_PURCHASE_CUSTOM_AMOUNT, AWAIT_BOUGHT_ONE, CLOSE_BAZAAR, CONSUME_SHARD,
        WAIT_CONSUME_ACK, COMPLETE, FAILED, CANCELLED }
    public record Settings(boolean enabled,boolean purchase,boolean consume,boolean mass,int wanted,long maxPpa,
            long maxSessionCost,int maxQuantity,long timeout,ShardCatalog catalog,List<String> wantedNames,
            String shardTitle,String confirmTitle,String massButton,String confirmButton,
            String attributeTitle,String advancedButton,String advancedMarker,String nextButton,String progressRegex,
            String buyTitle,String buyButton,String quantityTitle,String buyOne,String custom,
            String purchaseConfirmTitle,String purchaseConfirmButton,String priceRegex) {
        public Settings { wantedNames=List.copyOf(wantedNames); }
    }
    public record Observation(MenuSnapshot menu,Map<String,Integer> inventory,boolean cursorEmpty,boolean signOpen){
        public Observation { inventory=Map.copyOf(inventory); }
    }
    public record Job(String name,ShardCatalog.Rarity rarity,int quantity) {}
    public interface Actions {
        boolean openShards(); boolean openAttributes(); boolean searchShard(String name);
        boolean click(MenuSnapshot menu,MenuSnapshot.Slot slot); boolean close(MenuSnapshot menu);
        boolean enterAmount(int quantity); boolean consume(String name,int quantity); void cleanup();
    }
    private final Actions actions;
    private State state=State.IDLE;private String reason="Idle";
    private Settings settings;private Observation before;
    private MenuSnapshot sentMenu;private State afterMenu;
    private long entered,lastTick;private boolean issued,purchaseChat,awaitingOwnClick;
    private final Map<String,Level> levels=new LinkedHashMap<>();
    private final Set<String> pages=new HashSet<>();
    private final ArrayDeque<Job> jobs=new ArrayDeque<>();
    private long sessionCost,quote;private BigDecimal sessionExact=BigDecimal.ZERO,unitQuote=BigDecimal.ZERO,totalQuote=BigDecimal.ZERO;
    private int pagesRead;private boolean skipped;
    private Object scheduledTicket;private long scheduledAt;private boolean scheduledRebound;
    private Map<String,Integer> scheduledInventory=Map.of();
    private record Level(int level,Integer progress) {}
    public ShardWorkflow(Actions actions){this.actions=Objects.requireNonNull(actions);}
    public Optional<String> validate(Settings cfg){
        if(!cfg.enabled())return Optional.of("Shard workflow requires explicit opt-in");
        if(cfg.timeout()<1||cfg.maxQuantity()<1||cfg.maxQuantity()>2304||cfg.maxPpa()<1||cfg.maxSessionCost()<1||cfg.wanted()<0||cfg.wanted()>10)
            return Optional.of("Invalid shard limits/level");
        if(cfg.catalog().names().isEmpty())return Optional.of("Configure the local shard catalog");
        if(cfg.mass()){
            if(!cfg.consume())return Optional.of("Mass syphon requires consumption opt-in");
            if(blank(cfg.shardTitle(),cfg.confirmTitle(),cfg.massButton(),cfg.confirmButton()))return Optional.of("Configure source-unrecovered syphon menu/button strings");
            if(cfg.shardTitle().equals(cfg.confirmTitle()))return Optional.of("Syphon menu and confirmation titles must differ");
        }else{
            if(!cfg.purchase()||!cfg.consume())return Optional.of("Attribute leveling requires purchase and consumption opt-ins");
            if(cfg.wantedNames().isEmpty()||cfg.wantedNames().stream().anyMatch(n->cfg.catalog().identity(n).isEmpty()))return Optional.of("Configure catalog-known wanted shard identities");
            if(cfg.wantedNames().stream().map(n->cfg.catalog().identity(n).orElseThrow()).distinct().count()!=cfg.wantedNames().size())return Optional.of("Duplicate wanted shard identity");
            if(blank(cfg.attributeTitle(),cfg.advancedButton(),cfg.advancedMarker(),cfg.nextButton(),cfg.progressRegex(),cfg.buyTitle(),cfg.buyButton(),cfg.quantityTitle(),cfg.buyOne(),cfg.custom(),cfg.purchaseConfirmTitle(),cfg.purchaseConfirmButton(),cfg.priceRegex()))
                return Optional.of("Configure source-unrecovered attribute/Bazaar strings and progress/price regexes");
            try{if(Pattern.compile(cfg.progressRegex()).matcher("").groupCount()!=1||Pattern.compile(cfg.priceRegex()).matcher("").groupCount()!=1)return Optional.of("Progress/price regexes must each have one capture group");}
            catch(RuntimeException bad){return Optional.of("Invalid shard lore regex");}
        }
        return Optional.empty();
    }
    private static boolean blank(String... values){return Arrays.stream(values).anyMatch(s->s==null||s.isBlank());}
    public boolean start(Settings cfg,Observation obs,long now){
        cancel();settings=cfg;var invalid=validate(cfg);
        if(invalid.isPresent()){finish(State.FAILED,invalid.get());return false;}
        if(!obs.cursorEmpty()){finish(State.FAILED,"Empty cursor required");return false;}
        levels.clear();pages.clear();jobs.clear();pagesRead=0;sessionCost=0;sessionExact=BigDecimal.ZERO;purchaseChat=false;skipped=false;
        before=obs;lastTick=now;go(cfg.mass()?State.OPEN_SHARDS_MENU:State.OPEN_ATTRIBUTES,now);return true;
    }
    public void tick(boolean valid,Settings cfg,Observation obs,long now){
        if(!active()||scheduledTicket!=null)return;
        if(!valid||!Objects.equals(settings,cfg)){finish(State.CANCELLED,"Context, identity or configuration changed");return;}
        if(now<lastTick||now-entered>=cfg.timeout()){finish(State.FAILED,"Timed out awaiting "+state+"; no retry of uncertain mutation");return;}
        lastTick=now;
        // Vanilla PICKUP predicts a carried button. Await the server's correction after our own
        // request; no further click/use/close is issued until the observed cursor is empty again.
        if(!obs.cursorEmpty()){if(awaitingOwnClick)return;finish(State.CANCELLED,"Cursor changed during workflow");return;}
        var menu=obs.menu();
        if(sentMenu!=null){
            if(menu==null)return;
            if(menu.id()==sentMenu.id()&&menu.revision()<=sentMenu.revision())return;
            if(menu.id()==sentMenu.id()&&menu.equals(sentMenu))return;
            sentMenu=null;go(afterMenu,now);return;
        }
        switch(state){
            case OPEN_SHARDS_MENU -> open(menu,cfg.shardTitle(),State.CLICK_MASS_SYPHON,now,actions::openShards);
            case CLICK_MASS_SYPHON -> {
                if(!title(menu,cfg.shardTitle())){unexpected(menu);return;}
                before=obs;
                int quantity=obs.inventory().values().stream().mapToInt(Integer::intValue).sum();
                if(quantity<1){finish(State.COMPLETE,"No catalog-known shards to syphon");return;}
                if(quantity>cfg.maxQuantity()){finish(State.FAILED,"Syphon quantity exceeds configured cap");return;}
                click(menu,cfg.massButton(),State.CLICK_CONFIRM_MASS_SYPHON,now);
            }
            case CLICK_CONFIRM_MASS_SYPHON -> {
                if(!title(menu,cfg.confirmTitle())){unexpected(menu);return;}
                if(!obs.inventory().equals(before.inventory())){finish(State.FAILED,"Shard inventory changed before syphon confirmation");return;}
                // A mutation is sent once. Inventory + returned menu must both acknowledge it.
                var slot=button(menu,cfg.confirmButton());if(slot==null)return;
                go(State.WAIT_SYPHON_ACK,now);sentMenu=null;awaitingOwnClick=true;
                try{if(!actions.click(menu,slot))finish(State.FAILED,"Syphon confirm rejected by menu guard");}
                catch(RuntimeException failed){finish(State.FAILED,"Syphon confirm failed");}
            }
            case WAIT_SYPHON_ACK -> {
                if(title(menu,cfg.shardTitle())&&exactlyRemoved(before.inventory(),obs.inventory()))go(State.EXIT_SHARDS_MENU,now);
            }
            case EXIT_SHARDS_MENU -> {if(menu!=null&&!title(menu,cfg.shardTitle()))unexpected(menu);else close(menu,State.COMPLETE,now);}
            case OPEN_ATTRIBUTES -> open(menu,cfg.attributeTitle(),State.ENABLE_ADVANCED_MODE,now,actions::openAttributes);
            case ENABLE_ADVANCED_MODE -> {
                if(!title(menu,cfg.attributeTitle())){unexpected(menu);return;}
                if(button(menu,cfg.advancedMarker())!=null)go(State.READ_PAGE,now);
                else click(menu,cfg.advancedButton(),State.ENABLE_ADVANCED_MODE,now);
            }
            case READ_PAGE -> readPage(menu,now);
            case EXIT_MENU -> {if(menu!=null&&!title(menu,cfg.attributeTitle()))unexpected(menu);else close(menu,State.OPEN_BAZAAR_MENU,now);}
            case OPEN_BAZAAR_MENU -> {
                if(jobs.isEmpty()){finish(State.COMPLETE,"No Attributes To Level; acknowledged purchases="+sessionCost+" coins");return;}
                int owned=obs.inventory().getOrDefault(jobs.getFirst().name(),0);
                if(owned>0){
                    if(owned!=jobs.getFirst().quantity()){finish(State.FAILED,"Existing shard quantity requires partial-stack selection; no new purchase sent");return;}
                    if(menu!=null){finish(State.CANCELLED,"Close the unrelated menu before inventory-ready shard recovery");return;}
                    go(State.CONSUME_SHARD,now);return;
                }
                if(title(menu,cfg.buyTitle()))go(State.CLICK_SHARD,now);
                else if(!issued){issued=true;if(!actions.searchShard(jobs.getFirst().name()))finish(State.FAILED,"Shard search command unavailable");}
            }
            case CLICK_SHARD -> {
                if(!title(menu,cfg.buyTitle())){unexpected(menu);return;}
                if(button(menu,cfg.buyButton())!=null)go(State.CLICK_BUY_INSTANTLY,now);
                else click(menu,jobs.getFirst().name()+" Shard",State.CLICK_BUY_INSTANTLY,now);
            }
            case CLICK_BUY_INSTANTLY -> {
                if(!title(menu,cfg.buyTitle())){unexpected(menu);return;}
                var slot=button(menu,cfg.buyButton());if(slot==null)return;
                var price=price(slot,cfg.priceRegex());
                if(price.isEmpty()){finish(State.FAILED,"Unknown/ambiguous unit price");return;}
                quote=price.getAsLong();
                if(quote>cfg.maxPpa()){jobs.removeFirst();skipped=true;go(State.CLOSE_BAZAAR,now);reason="Skipped shard above SHARD_MAX_PPA";return;}
                try{unitQuote=rawPrice(slot,cfg.priceRegex()).orElseThrow();totalQuote=unitQuote.multiply(BigDecimal.valueOf(jobs.getFirst().quantity()));
                    if(sessionExact.add(totalQuote).compareTo(BigDecimal.valueOf(cfg.maxSessionCost()))>0)throw new ArithmeticException();}
                catch(ArithmeticException tooMuch){finish(State.FAILED,"Shard purchase exceeds session budget");return;}
                click(menu,cfg.buyButton(),jobs.getFirst().quantity()==1?State.CLICK_BUY_ONE:State.CLICK_CUSTOM_AMOUNT,now);
            }
            case CLICK_BUY_ONE -> {
                if(!title(menu,cfg.quantityTitle())){unexpected(menu);return;}
                var slot=button(menu,cfg.buyOne());if(slot==null)return;
                var price=rawPrice(slot,cfg.priceRegex());
                if(price.isEmpty()||price.get().compareTo(unitQuote)!=0){finish(State.FAILED,"Buy-one final quote changed or unreadable");return;}
                requestPurchase(obs,cfg.buyOne(),now);
            }
            case CLICK_CUSTOM_AMOUNT -> {
                if(!title(menu,cfg.quantityTitle())){unexpected(menu);return;}
                // Sign is expected only after the guarded custom-quantity request.
                var slot=button(menu,cfg.custom());if(slot==null)return;
                go(State.ENTER_AMOUNT,now);awaitingOwnClick=true;if(!actions.click(menu,slot))finish(State.FAILED,"Custom quantity request refused");
            }
            case ENTER_AMOUNT -> {
                if(!obs.signOpen())return;
                go(State.CONFIRM_PURCHASE_CUSTOM_AMOUNT,now);
                if(!actions.enterAmount(jobs.getFirst().quantity()))finish(State.FAILED,"Sign quantity entry refused");
            }
            case CONFIRM_PURCHASE_CUSTOM_AMOUNT -> {
                if(!title(menu,cfg.purchaseConfirmTitle())){unexpected(menu);return;}
                var slot=button(menu,cfg.purchaseConfirmButton());if(slot==null)return;
                var total=rawPrice(slot,cfg.priceRegex());
                if(total.isEmpty()||total.get().compareTo(totalQuote)!=0){finish(State.FAILED,"Custom purchase quote changed");return;}
                requestPurchase(obs,cfg.purchaseConfirmButton(),now);
            }
            case AWAIT_BOUGHT_ONE -> {
                Job job=jobs.getFirst();int previous=before.inventory().getOrDefault(job.name(),0);
                if(purchaseChat&&obs.inventory().getOrDefault(job.name(),0)==previous+job.quantity()){
                    for(String key:cfg.catalog().names())if(!key.equals(job.name())&&!Objects.equals(before.inventory().getOrDefault(key,0),obs.inventory().getOrDefault(key,0))){finish(State.FAILED,"Other shard inventory changed during purchase");return;}
                    sessionExact=sessionExact.add(totalQuote);sessionCost=round(sessionExact);go(State.CLOSE_BAZAAR,now);
                }
            }
            case CLOSE_BAZAAR -> {if(menu!=null&&!title(menu,cfg.buyTitle())&&!title(menu,cfg.quantityTitle())&&!title(menu,cfg.purchaseConfirmTitle())){unexpected(menu);return;}
                State next=jobs.isEmpty()?State.COMPLETE:skipped?State.OPEN_BAZAAR_MENU:State.CONSUME_SHARD;
                close(menu,next,now);if(state==State.OPEN_BAZAAR_MENU)skipped=false;}
            case CONSUME_SHARD -> {
                if(menu!=null){finish(State.CANCELLED,"Container opened before shard consumption");return;}
                before=obs;go(State.WAIT_CONSUME_ACK,now);
                if(!actions.consume(jobs.getFirst().name(),jobs.getFirst().quantity()))finish(State.FAILED,"Shard unavailable in inventory/hotbar or use refused");
            }
            case WAIT_CONSUME_ACK -> {
                Job job=jobs.getFirst();
                if(obs.inventory().getOrDefault(job.name(),0)==before.inventory().getOrDefault(job.name(),0)-job.quantity()){
                    for(String key:cfg.catalog().names())if(!key.equals(job.name())&&!Objects.equals(before.inventory().getOrDefault(key,0),obs.inventory().getOrDefault(key,0))){finish(State.FAILED,"Other shard inventory changed during consumption");return;}
                    jobs.removeFirst();actions.cleanup();go(State.OPEN_BAZAAR_MENU,now);
                }
            }
            default -> {}
        }
    }
    private void readPage(MenuSnapshot menu,long now){
        if(!title(menu,settings.attributeTitle())){unexpected(menu);return;}
        String signature=menu.slots().stream().filter(s->!s.playerInventory()).map(s->s.id()+":"+s.name()+s.lore()).reduce("",String::concat);
        if(!pages.add(signature)){finish(State.FAILED,"Attribute page did not advance");return;}
        if(++pagesRead>100){finish(State.FAILED,"Attribute page limit exceeded");return;}
        Pattern progress=Pattern.compile(settings.progressRegex());
        for(var slot:menu.slots()){
            if(slot.playerInventory()||slot.itemId().equals("minecraft:gray_dye"))continue;
            String name=ShardCatalog.clean(slot.name());int split=name.lastIndexOf(' ');if(split<1)continue;
            var identity=settings.catalog().identity(name.substring(0,split));if(identity.isEmpty())continue;
            int level=roman(name.substring(split+1));if(level<1){finish(State.FAILED,"Unknown attribute level: "+name);return;}
            Integer paid=null;
            for(String line:slot.lore()){
                var matcher=progress.matcher(ShardCatalog.clean(line));if(!matcher.matches())continue;
                try{int value=Integer.parseInt(matcher.group(1).replace(",",""));if(paid!=null&&paid!=value)throw new NumberFormatException();paid=value;}
                catch(NumberFormatException invalid){finish(State.FAILED,"Unknown/ambiguous attribute progress");return;}
            }
            var entry=new Level(level,paid);var old=levels.putIfAbsent(identity.get(),entry);
            if(old!=null&&!old.equals(entry)){finish(State.FAILED,"Conflicting attribute pages");return;}
        }
        var next=button(menu,settings.nextButton());
        if(next!=null){click(menu,settings.nextButton(),State.READ_PAGE,now);return;}
        for(String requested:settings.wantedNames()){
            String name=settings.catalog().identity(requested).orElseThrow();if(name.equals("Reptiloid"))continue;
            var level=levels.getOrDefault(name,new Level(0,null));var rarity=settings.catalog().rarity(name);
            int need=ShardCosts.needed(rarity,level.level(),settings.wanted(),level.progress());
            if(need>settings.maxQuantity()){finish(State.FAILED,"Needed shard quantity exceeds configured cap: "+name);return;}
            if(need>0)jobs.add(new Job(name,rarity,need));
        }
        go(State.EXIT_MENU,now);
    }
    public static int roman(String value){return switch(value.toUpperCase(Locale.ROOT)){case "I"->1;case "II"->2;case "III"->3;case "IV"->4;case "V"->5;case "VI"->6;case "VII"->7;case "VIII"->8;case "IX"->9;case "X"->10;default->0;};}
    private void requestPurchase(Observation obs,String label,long now){
        var slot=button(obs.menu(),label);if(slot==null)return;
        before=obs;purchaseChat=false;go(State.AWAIT_BOUGHT_ONE,now);awaitingOwnClick=true;
        if(!actions.click(obs.menu(),slot))finish(State.FAILED,"Purchase request rejected");
    }
    public void message(String text){
        String line=ShardCatalog.clean(text);
        if(!active()||scheduledTicket!=null)return;
        if(line.equals("You do not have the requirement to use this item!")){finish(State.FAILED,"Requirement Issue");return;}
        if(line.equals("You don't have any Shards to Syphon!")&&settings.mass()){finish(State.FAILED,"Server rejected mass syphon: no shards");return;}
        if(state==State.AWAIT_BOUGHT_ONE&&line.startsWith("[Bazaar] Bought ")&&line.contains(jobs.getFirst().name()+" Shard"))purchaseChat=true;
    }
    public static OptionalLong price(MenuSnapshot.Slot slot,String regex){
        var raw=rawPrice(slot,regex);return raw.isEmpty()?OptionalLong.empty():OptionalLong.of(round(raw.get()));
    }
    private static long round(BigDecimal value){return value.setScale(0,java.math.RoundingMode.HALF_UP).longValueExact();}
    public static Optional<BigDecimal> rawPrice(MenuSnapshot.Slot slot,String regex){
        if(slot.locked())return Optional.empty();
        BigDecimal value=null;
        try{var pattern=Pattern.compile(regex);for(String line:slot.lore()){
            var matcher=pattern.matcher(ShardCatalog.clean(line));if(!matcher.matches())continue;
            var parsed=new BigDecimal(matcher.group(1).replace(",",""));round(parsed);
            if(parsed.signum()<0||value!=null&&value.compareTo(parsed)!=0)return Optional.empty();value=parsed;
        }}catch(RuntimeException invalid){return Optional.empty();}
        return Optional.ofNullable(value);
    }
    private static boolean exactlyRemoved(Map<String,Integer> before,Map<String,Integer> after){
        return before.values().stream().mapToInt(Integer::intValue).sum()>0&&before.keySet().stream().allMatch(k->after.getOrDefault(k,0)==0)&&after.values().stream().allMatch(n->n==0);
    }
    public static boolean matchesTitle(String observed,String configured){return configured!=null&&Arrays.stream(configured.split(";",-1)).map(ShardCatalog::clean).filter(s->!s.isBlank()).anyMatch(s->s.equals(ShardCatalog.clean(observed)));}
    private boolean title(MenuSnapshot menu,String title){return menu!=null&&matchesTitle(menu.title(),title);}
    private MenuSnapshot.Slot button(MenuSnapshot menu,String label){
        if(menu==null||label.isBlank())return null;
        var slots=menu.slots().stream().filter(s->!s.playerInventory()&&!s.locked()&&ShardCatalog.clean(s.name()).equalsIgnoreCase(label)).toList();
        if(slots.size()>1){finish(State.FAILED,"Ambiguous menu button: "+label);return null;}
        return slots.isEmpty()?null:slots.getFirst();
    }
    private void click(MenuSnapshot menu,String label,State next,long now){
        var slot=button(menu,label);if(slot==null)return;
        sentMenu=menu;afterMenu=next;entered=now;awaitingOwnClick=true;
        if(!actions.click(menu,slot))finish(State.FAILED,"Stale menu/button request refused");
    }
    private void open(MenuSnapshot menu,String title,State next,long now,java.util.function.BooleanSupplier opener){
        if(title(menu,title)){go(next,now);return;}
        if(menu!=null){finish(State.CANCELLED,"Unrelated menu opened");return;}
        if(!issued){issued=true;if(!opener.getAsBoolean())finish(State.FAILED,"Configured opening command unavailable");}
    }
    private void close(MenuSnapshot menu,State next,long now){
        if(menu==null){go(next,now);if(next==State.COMPLETE)finish(State.COMPLETE,"Server inventory and menu outcome observed");return;}
        if(!issued){issued=true;if(!actions.close(menu))finish(State.FAILED,"Container close refused");}
    }
    private void unexpected(MenuSnapshot menu){if(menu!=null)finish(State.CANCELLED,"Unexpected menu: "+menu.title());}
    private void go(State next,long now){state=next;entered=now;issued=false;awaitingOwnClick=false;reason=next.name();}
    private void finish(State next,String why){state=next;reason=why;sentMenu=null;issued=false;awaitingOwnClick=false;clearScheduledPause();actions.cleanup();}
    /** No menu/currency/sign/receipt/consumption request may be parked and replayed after rest. */
    public boolean scheduledBoundary(){return active()&&scheduledTicket==null&&!issued&&!awaitingOwnClick&&sentMenu==null
        &&(state==State.OPEN_SHARDS_MENU||state==State.OPEN_ATTRIBUTES||state==State.OPEN_BAZAAR_MENU);}
    public boolean pauseForScheduledRest(Object ticket,Observation observed,long now){
        if(ticket==null||!scheduledBoundary()||observed==null||observed.menu()!=null||!observed.cursorEmpty()||observed.signOpen()
            ||now<lastTick||now-entered>=settings.timeout())return false;
        scheduledTicket=ticket;scheduledAt=now;scheduledRebound=false;scheduledInventory=observed.inventory();
        actions.cleanup();return true;
    }
    public boolean scheduledOwnedBy(Object ticket){return ticket!=null&&scheduledTicket==ticket&&active();}
    public boolean isScheduledPaused(){return scheduledTicket!=null;}
    /** Family/native proof precedes this flush; counts must match, never synthesized purchase credits. */
    public boolean rebindForScheduledRest(Object ticket,Observation observed){
        if(!scheduledOwnedBy(ticket)||observed==null||observed.menu()!=null||!observed.cursorEmpty()||observed.signOpen()
            ||!scheduledInventory.equals(observed.inventory()))return false;
        before=observed;sentMenu=null;afterMenu=null;scheduledRebound=true;return true;
    }
    public boolean resumeAfterScheduledRest(Object ticket,long now){
        if(!scheduledOwnedBy(ticket)||!scheduledRebound)return false;
        if(now<scheduledAt){finish(State.FAILED,"Scheduled clock moved backwards");return false;}
        try{entered=Math.addExact(entered,Math.subtractExact(now,scheduledAt));lastTick=now;}
        catch(ArithmeticException invalid){finish(State.FAILED,"Scheduled clock overflow");return false;}
        clearScheduledPause();return true;
    }
    private void clearScheduledPause(){scheduledTicket=null;scheduledRebound=false;scheduledInventory=Map.of();scheduledAt=0;}
    public void cancel(){if(active())finish(State.CANCELLED,"Manual stop");else actions.cleanup();}
    public void fail(String why){finish(State.FAILED,why);}
    public boolean active(){return state!=State.IDLE&&state!=State.COMPLETE&&state!=State.FAILED&&state!=State.CANCELLED;}
    public State state(){return state;}
    public String status(){return state+": "+reason;}
    public List<Job> jobs(){return List.copyOf(jobs);}
    public long sessionCost(){return sessionCost;}
}
