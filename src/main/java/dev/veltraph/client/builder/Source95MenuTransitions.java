package dev.veltraph.client.builder;
import java.util.*;
/** Actually received container/sign UI transitions. Local sequence is chronology, never a wire purchase ID. */
public final class Source95MenuTransitions {
 public enum Kind { CONTAINER, SIGN }
 public record Event(long sequence,Kind kind,Object player,Object world,Object connection,Object menu,Object screen,int menuId){}
 private static long sequence;private static final Map<Object,Watch> watches=new IdentityHashMap<>();private record Watch(Object token,List<Event> events){}
 private Source95MenuTransitions(){}
 public static synchronized void attach(Object connection,Object token){if(watches.containsKey(connection))throw new IllegalStateException("Owned source UI journal already attached");watches.put(connection,new Watch(token,new ArrayList<>()));}
 public static synchronized long floor(){return sequence;}
 public static synchronized void received(Object connection,Kind kind,Object player,Object world,Object menu,Object screen,int id){long n=++sequence;var w=watches.get(connection);if(w!=null){w.events().add(new Event(n,kind,player,world,connection,menu,screen,id));while(w.events().size()>128)w.events().removeFirst();}}
 static synchronized Optional<Event> exactAfter(Object connection,Object token,long floor,Kind kind,Object player,Object world,Object menu,Object screen,int id){var w=watches.get(connection);if(w==null||w.token()!=token)return Optional.empty();var matches=w.events().stream().filter(e->e.sequence()>floor&&e.player()==player&&e.world()==world).toList();if(matches.size()!=1)return Optional.empty();var e=matches.getFirst();return e.kind()==kind&&e.menu()==menu&&e.screen()==screen&&e.menuId()==id?Optional.of(e):Optional.empty();}
 static synchronized boolean changedAfter(Object connection,Object token,long floor){var w=watches.get(connection);return w==null||w.token()!=token||w.events().stream().anyMatch(e->e.sequence()>floor);}
 public static synchronized void detach(Object connection,Object token){var w=watches.get(connection);if(w!=null&&w.token()==token)watches.remove(connection);}
}
