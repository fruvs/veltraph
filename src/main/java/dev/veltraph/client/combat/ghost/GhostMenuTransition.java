package dev.veltraph.client.combat.ghost;

import java.util.*;

/** Exact pending native menu transition. A compatible old page is a wait, never its own successor. */
public final class GhostMenuTransition {
    public enum Decision { OWNED, SUCCESSOR, REJECT }
    public record Scope(Object player,Object world,Object connection,Object configuration,int slot) {}
    public record Menu(Object screen,Object menu,int id,int revision,String title) {
        public Menu {title=Objects.requireNonNull(title);}
    }
    public record Observation(Scope scope,Menu menu,boolean alive,boolean cursorEmpty,
            boolean ownedPrediction,boolean authoritativeSameMenuEffect,long now) {}
    public record Request(Object token,long generation,Menu origin,String expected,boolean prefix,
            boolean page,long issuedAt,long deadline) {}
    private final Scope scope;private final long deadline;
    private final Set<Object> seenMenus=Collections.newSetFromMap(new IdentityHashMap<>());
    private final Set<Object> seenScreens=Collections.newSetFromMap(new IdentityHashMap<>());
    private Menu owned;private Request pending;private long generation;private boolean revoked;
    public GhostMenuTransition(Scope scope,long deadline){
        this.scope=Objects.requireNonNull(scope);this.deadline=deadline;
        if(scope.player()==null||scope.world()==null||scope.connection()==null||scope.configuration()==null||scope.slot()<0||scope.slot()>8)throw new IllegalArgumentException("Exact native menu owner scope required");
    }
    public Optional<Request> pending(){return Optional.ofNullable(pending);}public Menu owned(){return owned;}
    public Request issue(Menu origin,String expected,boolean prefix,boolean page,long now){
        if(revoked||expected==null||expected.isBlank()||now>deadline)throw new IllegalStateException("Live explicit menu request required");
        if(origin!=null&&!sameIdentity(origin,owned))throw new IllegalStateException("Request origin must be the exact owned native menu");
        pending=new Request(new Object(),++generation,origin,expected,prefix,page,now,deadline);return pending;
    }
    public Decision decision(Observation o){
        if(!valid(o)||o.menu()==null||o.menu().screen()==null||o.menu().menu()==null)return Decision.REJECT;
        var current=o.menu();
        if(sameIdentity(current,owned)){
            if(!o.cursorEmpty()&&!o.ownedPrediction())return Decision.REJECT;
            // A partial receipt/cleared prediction on the old page does not acknowledge its successor.
            if(pending!=null&&o.cursorEmpty()&&o.authoritativeSameMenuEffect()
                    &&pending.origin()!=null&&current.revision()>pending.origin().revision()
                    &&!current.title().equals(pending.origin().title())&&matches(current.title()))return Decision.SUCCESSOR;
            return current.title().equals(owned.title())?Decision.OWNED:Decision.REJECT;
        }
        if(pending==null||!o.cursorEmpty()||!matches(current.title())||seenMenus.contains(current.menu())||seenScreens.contains(current.screen()))return Decision.REJECT;
        if(pending.origin()!=null&&(current.id()==pending.origin().id()||current.menu()==pending.origin().menu()||current.screen()==pending.origin().screen()
                ||pending.page()&&current.title().equals(pending.origin().title())))return Decision.REJECT;
        return Decision.SUCCESSOR;
    }
    /** Read-only Root permission; consuming a request occurs only in observe with its exact token. */
    public boolean allows(Observation o){return decision(o)!=Decision.REJECT;}
    public boolean observe(Observation o){
        var d=decision(o);if(d==Decision.REJECT)return false;
        if(d==Decision.SUCCESSOR)return acknowledge(pending,o);
        return true;
    }
    public boolean acknowledge(Request request,Observation o){
        if(request==null||pending!=request||decision(o)!=Decision.SUCCESSOR)return false;
        owned=o.menu();seenMenus.add(owned.menu());seenScreens.add(owned.screen());pending=null;return true;
    }
    public void closed(){owned=null;pending=null;}
    public void cancel(){revoked=true;owned=null;pending=null;}
    private boolean matches(String title){return pending!=null&&(pending.prefix()?title.startsWith(pending.expected()):title.equals(pending.expected()));}
    private boolean valid(Observation o){
        if(revoked||o==null||o.scope()==null||!o.alive()||o.now()>deadline||pending!=null&&o.now()<pending.issuedAt())return false;
        var s=o.scope();return s.player()==scope.player()&&s.world()==scope.world()&&s.connection()==scope.connection()&&s.configuration()==scope.configuration()&&s.slot()==scope.slot();
    }
    private static boolean sameIdentity(Menu a,Menu b){return a!=null&&b!=null&&a.screen()==b.screen()&&a.menu()==b.menu()&&a.id()==b.id();}
}
