package dev.veltraph.client.garden.visitor.history;

import com.google.gson.*;
import java.io.IOException;
import java.io.StringReader;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/** Local atomic seen-name history. Only a fresh complete book permits absence to mean NEW. */
public final class VisitorHistoryStore {
    public enum Newness { NEW, SEEN, UNKNOWN }
    public record Observation(Newness newness, String provenance) {}
    private record Coverage(VisitorHistoryContext context, long time, long sequence, String provenance, Set<String> knownNames) {}
    private static final int MAX_SCOPES=64, MAX_NAMES=4096, MAX_BYTES=1_048_576;
    private final Path path;
    private final Map<VisitorHistoryScope,Set<String>> seen=new LinkedHashMap<>();
    private final Map<VisitorHistoryScope,Coverage> coverage=new HashMap<>();
    private final Map<VisitorHistoryScope,Map<String,String>> provenance=new LinkedHashMap<>();
    private final Map<VisitorHistoryScope,Pending> pending=new LinkedHashMap<>();
    private final Map<VisitorHistoryScope,InvalidatedCoverage> invalidated=new LinkedHashMap<>();
    private final Thread ownerThread=Thread.currentThread();
    private boolean unreadable,coverageEvidenceOverflow;
    private String overflowProvenance="";
    public record PersistenceStatus(boolean pending,String provenance,long originalObservedAt,String failure) {}
    public record CoverageReconciliationStatus(String provenance,long latestObservedAt,long latestReceivedSequence,
            Set<String> requiredSeenNames,boolean boundExceeded) {
        public CoverageReconciliationStatus {requiredSeenNames=Set.copyOf(requiredSeenNames);}
    }
    private static final class InvalidatedCoverage {
        final VisitorHistoryContext context;final long at,sequence;final Set<String> required;final String origin;final boolean overflow;
        InvalidatedCoverage(VisitorHistoryContext context,long at,long sequence,Set<String> required,String origin,boolean overflow){
            this.context=context;this.at=at;this.sequence=sequence;this.required=Set.copyOf(required);this.origin=origin;this.overflow=overflow;
        }
    }
    private static final class Pending {
        final VisitorHistoryContext context;final Set<String> additions;final Coverage coverage;final String origin;final long observedAt;
        final VisitorLogbookReader.CompletedBook book;final VisitorServedHistoryJournal.Served serve;
        String failure="";
        Pending(VisitorHistoryContext context,Set<String> additions,Coverage coverage,String origin,long at,
                VisitorLogbookReader.CompletedBook book,VisitorServedHistoryJournal.Served serve){
            this.context=context;this.additions=Set.copyOf(additions);this.coverage=coverage;this.origin=origin;observedAt=at;this.book=book;this.serve=serve;
        }
        boolean available(){return book!=null?book.canReservePersistence():serve.canReservePersistence();}
        long evidenceSequence(){return book!=null?book.lastReceivedSequence():serve.evidenceSequence();}
        boolean reserve(){return book!=null?book.reservePersistence(this):serve.reservePersistence(this);}
        boolean retained(){return book!=null?book.pendingPersistence(this):serve.pendingPersistence(this);}
        boolean durable(){return book!=null?book.finishPersistence(this):serve.finishPersistence(this);}
    }
    private void requireOwner(){if(Thread.currentThread()!=ownerThread)throw new IllegalStateException("Visitor history persistence requires its owning client thread");}
    public VisitorHistoryStore(Path path) { this.path=Objects.requireNonNull(path); }
    public static String name(String value) {
        if (value==null) throw new IllegalArgumentException("Unknown visitor name");
        // Canonicalize only formatting and surrounding whitespace, matching readable source.
        var out=new StringBuilder();
        for(int i=0;i<value.length();i++) {
            char c=value.charAt(i);
            if(c=='\u00a7' && i+1<value.length()) { i++; continue; }
            out.append(c);
        }
        String s=out.toString().trim();
        if(s.isBlank()||s.length()>160||s.chars().anyMatch(Character::isISOControl))
            throw new IllegalArgumentException("Invalid visitor name");
        return s;
    }
    public Observation observe(VisitorHistoryContext context,String visitor,long now,long maxAge) {
        if(Thread.currentThread()!=ownerThread||context==null||unreadable||coverageEvidenceOverflow||now<0||maxAge<0) return unknown("History scope or local store unknown");
        var reconciliation=invalidated.get(context.scope());
        if(reconciliation!=null)return unknown("Coverage reconciliation required; "+reconciliation.origin);
        var waiting=pending.get(context.scope());
        if(waiting!=null)return unknown("Scoped persistence pending; "+waiting.origin+"; "+waiting.failure);
        final String n;try{n=name(visitor);}catch(IllegalArgumentException bad){return unknown("Unknown visitor name");}
        if(seen.getOrDefault(context.scope(),Set.of()).contains(n))
            return new Observation(Newness.SEEN,provenance.getOrDefault(context.scope(),Map.of()).getOrDefault(n,"Scoped persisted/confirmed seen name"));
        var c=coverage.get(context.scope());
        if(c==null||!c.context.sameSession(context)||now<c.time||now-c.time>maxAge||!c.knownNames.contains(n))
            return unknown("No fresh complete scoped logbook coverage");
        return new Observation(Newness.NEW,c.provenance+"; received sequence="+c.sequence);
    }
    private static Observation unknown(String why){return new Observation(Newness.UNKNOWN,why);}
    /** A proof is reserved before writing, consumed only after durability, and retained for local retry on failure. */
    public boolean commitBook(VisitorLogbookReader.CompletedBook proof,VisitorHistoryContext current,long now)throws IOException {
        requireOwner();
        if(proof==null||!proof.context().sameSession(current)||now!=proof.completedAt())return false;
        var operation=new Pending(current,proof.seenNames(),
                new Coverage(current,now,proof.lastReceivedSequence(),proof.provenance(),proof.knownNames()),
                proof.provenance()+"; at="+now+"; received="+proof.lastReceivedSequence(),now,proof,null);
        return beginPersistence(operation);
    }
    public boolean commitServe(VisitorServedHistoryJournal.Served proof,VisitorHistoryContext current,long now)throws IOException {
        requireOwner();
        if(proof==null||!proof.context().sameSession(current)||now!=proof.completedAt())return false;
        var operation=new Pending(current,Set.of(proof.visitorName()),coverage.get(current.scope()),proof.provenance(),now,null,proof);
        return beginPersistence(operation);
    }
    private boolean beginPersistence(Pending operation)throws IOException {
        var scope=operation.context.scope();
        if(pending.containsKey(scope)){
            if(operation.available())invalidateForCompetingFact(operation);
            return false; // Explicit denial: this new fact has not been persisted or consumed.
        }
        if(pending.size()>=MAX_SCOPES*2){if(operation.available())invalidateForCompetingFact(operation);return false;}
        if(!operation.reserve())return false; // Reservation does not mean the fact is durably saved.
        pending.put(scope,operation);return persist(operation);
    }
    private void invalidateForCompetingFact(Pending operation){
        var scope=operation.context.scope();var prior=invalidated.get(scope);
        if(prior==null&&invalidated.size()>=MAX_SCOPES*3){
            coverageEvidenceOverflow=true;overflowProvenance="Coverage evidence bound; scope="+scope+"; "+operation.origin;
            coverage.clear();return;
        }
        var required=new LinkedHashSet<String>();if(prior!=null)required.addAll(prior.required);
        boolean overflow=prior!=null&&prior.overflow;
        for(String n:operation.additions){if(required.size()>=MAX_NAMES&&!required.contains(n)){overflow=true;break;}required.add(n);}
        boolean sameSession=prior==null||prior.context.sameSession(operation.context);
        long at=prior==null?operation.observedAt:Math.max(prior.at,operation.observedAt);
        long sequence=prior==null||!sameSession?operation.evidenceSequence():Math.max(prior.sequence,operation.evidenceSequence());
        invalidated.put(scope,new InvalidatedCoverage(operation.context,at,sequence,required,
                "Unpersisted competing fact: "+operation.origin,overflow));
        coverage.remove(scope); // Retrying the older fact cannot restore coverage superseded by this observation.
    }
    private boolean reconciles(Pending operation){
        var prior=invalidated.get(operation.context.scope());if(prior==null)return true;
        return operation.book!=null&&!prior.overflow&&prior.context.sameSession(operation.context)
                &&operation.observedAt>=prior.at&&operation.evidenceSequence()>prior.sequence
                &&operation.additions.containsAll(prior.required);
    }
    public Optional<String> reconciliationBoundFailure(){requireOwner();return coverageEvidenceOverflow?Optional.of(overflowProvenance):Optional.empty();}
    public Optional<CoverageReconciliationStatus> reconciliationStatus(VisitorHistoryScope scope){
        requireOwner();var c=invalidated.get(scope);
        return c==null?Optional.empty():Optional.of(new CoverageReconciliationStatus(c.origin,c.at,c.sequence,c.required,c.overflow));
    }
    /** Retry only the already accepted fact, in its exact confirmed session. No gameplay, menu or receipt is reissued. */
    public boolean retryPending(VisitorHistoryContext current,long now)throws IOException {
        requireOwner();if(current==null)return false;var operation=pending.get(current.scope());
        if(operation==null||!operation.context.sameSession(current)||now<operation.observedAt||!operation.retained())return false;
        if(unreadable)recoverReadability();
        return persist(operation); // Coverage retains its original observation time; retry never freshens it.
    }
    /** Repairs only the readability latch after the owner has supplied a valid file or removed the bad file. */
    private void recoverReadability()throws IOException {
        if(!Files.exists(path)){seen.clear();provenance.clear();coverage.clear();unreadable=false;return;}
        try{
            if(Files.isSymbolicLink(path)||Files.size(path)>MAX_BYTES)throw new IOException("Invalid visitor history file");
            var decoded=decode(Files.readString(path));seen.clear();provenance.clear();coverage.clear();
            for(var row:decoded.entrySet()){seen.put(row.getKey(),row.getValue().keySet());provenance.put(row.getKey(),row.getValue());}unreadable=false;
        }catch(IOException|RuntimeException failure){throw new IOException("Pending history repair is unreadable; file and fact preserved",failure);}
    }
    public Optional<PersistenceStatus> pendingStatus(VisitorHistoryScope scope){
        requireOwner();var operation=pending.get(scope);
        return operation==null?Optional.empty():Optional.of(new PersistenceStatus(true,operation.origin,operation.observedAt,operation.failure));
    }
    private boolean persist(Pending operation)throws IOException {
        var scope=operation.context.scope();
        if(pending.get(scope)!=operation||!operation.retained())return false;
        var prior=new LinkedHashMap<>(seen);var priorCoverage=new HashMap<>(coverage);var priorProvenance=new LinkedHashMap<>(provenance);
        try{
            if(unreadable)throw new IOException("Unreadable visitor history preserved");
            var merged=new LinkedHashSet<>(seen.getOrDefault(scope,Set.of()));
            for(String value:operation.additions)merged.add(name(value));
            if(merged.size()>MAX_NAMES||(!seen.containsKey(scope)&&seen.size()>=MAX_SCOPES))throw new IOException("Visitor history bound exceeded");
            seen.put(scope,Set.copyOf(merged));
            if(operation.coverage!=null&&reconciles(operation))coverage.put(scope,operation.coverage);else coverage.remove(scope);
            var origins=new LinkedHashMap<>(provenance.getOrDefault(scope,Map.of()));
            for(String n:operation.additions)origins.put(name(n),operation.origin);provenance.put(scope,Map.copyOf(origins));
            save();
        }catch(IOException|RuntimeException failure){
            seen.clear();seen.putAll(prior);coverage.clear();coverage.putAll(priorCoverage);provenance.clear();provenance.putAll(priorProvenance);
            coverage.remove(scope); // Even a previously fresh NEW roster is unsafe after an unsaved confirmed serve.
            operation.failure=failure.getClass().getSimpleName();
            throw failure;
        }
        // All methods run serially on the constructing client thread; save() invokes no game callbacks.
        if(!operation.durable()){coverage.remove(scope);operation.failure="Saved fact reservation changed";return false;}
        if(reconciles(operation))invalidated.remove(scope);
        pending.remove(scope);return true;
    }
    public void load()throws IOException {
        requireOwner();
        if(!pending.isEmpty())throw new IOException("Pending visitor facts must be retried; reload cannot discard them");
        coverage.clear(); // A persisted old snapshot cannot authorize absent names after a reconnect/restart.
        if(!Files.exists(path)){seen.clear();provenance.clear();unreadable=false;return;}
        try{
            if(Files.isSymbolicLink(path)||Files.size(path)>MAX_BYTES)throw new IOException("Invalid visitor history file");
            var decoded=decode(Files.readString(path));seen.clear();provenance.clear();
            for(var row:decoded.entrySet()){seen.put(row.getKey(),row.getValue().keySet());provenance.put(row.getKey(),row.getValue());}unreadable=false;
        }catch(IOException|RuntimeException failure){seen.clear();provenance.clear();unreadable=true;throw new IOException("Cannot read visitor history; original file preserved",failure);}
    }
    private void save()throws IOException {
        if(Files.isSymbolicLink(path))throw new IOException("Visitor history path is a symlink");
        Path parent=path.toAbsolutePath().getParent();Files.createDirectories(parent);
        var root=new JsonObject();root.addProperty("version",1);var scopes=new JsonArray();
        for(var row:seen.entrySet()){
            var o=new JsonObject();o.addProperty("account",row.getKey().accountId().toString());
            o.addProperty("profile",row.getKey().confirmedProfileId());o.addProperty("garden",row.getKey().confirmedGardenId());
            var names=new JsonObject();row.getValue().stream().sorted().forEach(n->names.addProperty(n,provenance.getOrDefault(row.getKey(),Map.of()).getOrDefault(n,"Scoped persisted seen name")));o.add("seen",names);scopes.add(o);
        }
        root.add("scopes",scopes);String data=root.toString();
        if(data.getBytes(StandardCharsets.UTF_8).length>MAX_BYTES)throw new IOException("Visitor history file bound");
        Path temp=Files.createTempFile(parent,"visitor-history-",".tmp");
        try{
            try(var channel=java.nio.channels.FileChannel.open(temp,StandardOpenOption.WRITE,StandardOpenOption.TRUNCATE_EXISTING)){
                var bytes=java.nio.ByteBuffer.wrap(data.getBytes(StandardCharsets.UTF_8));while(bytes.hasRemaining())channel.write(bytes);channel.force(true);
            }
            // No non-atomic fallback: failure preserves both the previous file and memory.
            Files.move(temp,path,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);
        }finally{Files.deleteIfExists(temp);}
    }
    private static Map<VisitorHistoryScope,Map<String,String>> decode(String data)throws IOException {
        if(data.getBytes(StandardCharsets.UTF_8).length>MAX_BYTES)throw new IOException("Visitor file bound");
        final JsonObject root;
        try(var reader=new JsonReader(new StringReader(data))){reader.setStrictness(Strictness.STRICT);root=parse(reader,0).getAsJsonObject();if(reader.peek()!=JsonToken.END_DOCUMENT)throw new IOException("Trailing visitor history data");}
        if(!root.keySet().equals(Set.of("version","scopes"))||!root.get("version").isJsonPrimitive()||!root.get("version").getAsJsonPrimitive().isNumber()||!root.get("version").getAsString().equals("1"))throw new IOException("Unknown visitor history format");
        var rows=root.getAsJsonArray("scopes");if(rows.size()>MAX_SCOPES)throw new IOException("Visitor scope count bound");
        var decoded=new LinkedHashMap<VisitorHistoryScope,Map<String,String>>();
        for(var value:rows){
            var o=value.getAsJsonObject();if(!o.keySet().equals(Set.of("account","profile","garden","seen")))throw new IOException("Malformed history row");
            for(String k:List.of("account","profile","garden"))if(!o.get(k).isJsonPrimitive()||!o.get(k).getAsJsonPrimitive().isString())throw new IOException("Non-string visitor scope");
            var scope=new VisitorHistoryScope(UUID.fromString(o.get("account").getAsString()),o.get("profile").getAsString(),o.get("garden").getAsString());
            var names=new LinkedHashMap<String,String>();var list=o.getAsJsonObject("seen");if(list.size()>MAX_NAMES)throw new IOException("Visitor name count bound");
            for(var entry:list.entrySet()){var v=entry.getValue();if(!v.isJsonPrimitive()||!v.getAsJsonPrimitive().isString()||v.getAsString().isBlank()||v.getAsString().length()>2048||names.put(name(entry.getKey()),v.getAsString())!=null)throw new IOException("Invalid/duplicate visitor name provenance");}
            if(decoded.put(scope,Map.copyOf(names))!=null)throw new IOException("Duplicate visitor scope");
        }
        return decoded;
    }
    private static JsonElement parse(JsonReader r,int depth)throws IOException {
        if(depth>8)throw new IOException("Visitor JSON nesting bound");
        return switch(r.peek()){
            case BEGIN_OBJECT->{var o=new JsonObject();r.beginObject();while(r.hasNext()){
                String key=r.nextName();if(key.length()>2048||o.has(key)||o.size()>=4096)throw new IOException("Duplicate/unbounded visitor JSON member");
                o.add(key,parse(r,depth+1));}r.endObject();yield o;}
            case BEGIN_ARRAY->{var a=new JsonArray();r.beginArray();while(r.hasNext()){if(a.size()>=4096)throw new IOException("Visitor JSON array bound");a.add(parse(r,depth+1));}r.endArray();yield a;}
            case STRING->{String value=r.nextString();if(value.length()>2048)throw new IOException("Visitor JSON string bound");yield new JsonPrimitive(value);}
            case NUMBER->new JsonPrimitive(new java.math.BigDecimal(r.nextString()));
            case BOOLEAN->new JsonPrimitive(r.nextBoolean());
            case NULL->{r.nextNull();yield JsonNull.INSTANCE;}
            default->throw new IOException("Invalid visitor JSON token");
        };
    }
}
