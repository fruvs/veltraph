package dev.veltraph.client.builder;
import java.util.*;
/** Native receive chronology only. Local event IDs are NOT server purchase transaction IDs. */
public final class Source95ChatReceipts {
 public record Scope(Object player,Object world,Object connection,Object menu,Object screen,int menuId){
  boolean same(Source95AcquisitionAuthority.Scope s){return s!=null&&player==s.player()&&world==s.world()&&connection==s.connection()&&menu==s.menu()&&screen==s.screen()&&menuId==s.menuId();}
 }
 public record Receipt(long eventId,Scope scope,String text,boolean overlay){}
 private static long event;private record Watch(Object token,List<Receipt> entries,Set<Long> consumed){}
 private static final Map<Object,Watch> watches=new IdentityHashMap<>();private Source95ChatReceipts(){}
 public static synchronized void attach(Object connection,Object token){if(watches.containsKey(connection))throw new IllegalStateException("Source native chat journal already owned");watches.put(connection,new Watch(token,new ArrayList<>(),new HashSet<>()));}
 public static synchronized void received(Object connection,Scope scope,String text,boolean overlay){long sequence=++event;var w=watches.get(connection);if(w==null||text==null||text.length()>2048)return;w.entries().add(new Receipt(sequence,scope,text,overlay));while(w.entries().size()>256){long first=w.entries().removeFirst().eventId();w.consumed().remove(first);}}
 public static synchronized long floor(){return event;}
 static synchronized List<Receipt> after(Object connection,Object token,long floor,Source95AcquisitionAuthority.Scope scope){var w=watches.get(connection);if(w==null||w.token()!=token)return List.of();return w.entries().stream().filter(r->r.eventId()>floor&&!w.consumed().contains(r.eventId())&&!r.overlay()&&r.scope().same(scope)).toList();}
 static synchronized boolean consume(Object connection,Object token,long id){var w=watches.get(connection);return w!=null&&w.token()==token&&w.entries().stream().anyMatch(r->r.eventId()==id)&&w.consumed().add(id);}
 public static synchronized void detach(Object connection,Object token){var w=watches.get(connection);if(w!=null&&w.token()==token)watches.remove(connection);}
}
