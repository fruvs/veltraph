package dev.veltraph.client.garden.jacob;

import java.nio.file.*;
import java.nio.channels.*;
import java.nio.charset.StandardCharsets;
import java.nio.ByteBuffer;
import java.io.IOException;
import java.util.*;

/** Dedicated local journal, independent of settings resets. Data writes alone grant no native authority. */
public final class JacobRewardJournalFile {
    public static final class Snapshot {
        private final JacobRewardJournalFile origin;private final boolean present;private final String encoded;
        private final JacobRewardJournal value;
        private Snapshot(JacobRewardJournalFile origin,boolean present,String encoded,JacobRewardJournal value){this.origin=origin;this.present=present;this.encoded=encoded;this.value=value;}
        public JacobRewardJournal value(){return value;}
    }
    /** Privately constructed only after actual forced atomic write and exact native-file readback. */
    public static final class VerifiedWrite {
        private final JacobRewardJournalFile origin;private final Snapshot after;
        private VerifiedWrite(JacobRewardJournalFile origin,Snapshot after){this.origin=origin;this.after=after;}
        public Snapshot after(){return after;}
    }
    private final Path file;private boolean missingAfterObserved;
    private static void forceDirectory(Path directory)throws IOException {try(var channel=FileChannel.open(directory,StandardOpenOption.READ)){channel.force(true);}}
    public JacobRewardJournalFile(Path file){this.file=Objects.requireNonNull(file).toAbsolutePath().normalize();}
    public Path path(){return file;}
    public synchronized Snapshot read()throws IOException {
        if(!Files.exists(file)){if(missingAfterObserved)throw new IOException("Previously observed claim journal disappeared; no reset/replay");return new Snapshot(this,false,null,JacobRewardJournal.empty());}
        missingAfterObserved=true;long size=Files.size(file);if(size<1||size>JacobRewardJournal.MAX_BYTES)throw new IOException("Claim journal byte bound invalid");
        String raw=Files.readString(file,StandardCharsets.UTF_8);try{return new Snapshot(this,true,raw,JacobRewardJournal.decode(raw));}catch(RuntimeException corrupt){throw new IOException("Corrupt claim journal; no claims or automatic repair",corrupt);}
    }
    private static boolean same(Snapshot a,Snapshot b){return a.origin==b.origin&&a.present==b.present&&Objects.equals(a.encoded,b.encoded);}
    public synchronized boolean current(VerifiedWrite saved)throws IOException{return saved!=null&&saved.origin==this&&same(saved.after,read());}
    public synchronized VerifiedWrite write(Snapshot expected,JacobRewardJournal next)throws IOException {
        if(expected==null||expected.origin!=this||next.generation()!=expected.value.generation()+1)throw new IOException("Exact original journal/generation required");
        byte[] encoded=next.encode().getBytes(StandardCharsets.UTF_8);var parent=file.getParent();if(parent==null)throw new IOException("Local journal directory missing");var created=new ArrayList<Path>();for(Path directory=parent;directory!=null&&!Files.exists(directory);directory=directory.getParent())created.add(directory);
        Files.createDirectories(parent);
        // Persist newly created directory entries up to the first existing parent before any effect.
        for(int i=created.size()-1;i>=0;i--){var directory=created.get(i);if(directory.getParent()!=null)forceDirectory(directory.getParent());forceDirectory(directory);}
        var lockPath=file.resolveSibling(file.getFileName()+".lock");Path temp=file.resolveSibling(file.getFileName()+"."+UUID.randomUUID()+".tmp");
        try(var lockChannel=FileChannel.open(lockPath,StandardOpenOption.CREATE,StandardOpenOption.WRITE)){
            try(var lock=lockChannel.tryLock()){
                if(lock==null||!same(expected,read()))throw new IOException("Another journal writer or changed source; no native claim");
                try(var output=FileChannel.open(temp,StandardOpenOption.CREATE_NEW,StandardOpenOption.WRITE)){var bytes=ByteBuffer.wrap(encoded);while(bytes.hasRemaining())output.write(bytes);output.force(true);}
                // Unsupported atomic move refuses; it does not fall back to a partial replacement.
                Files.move(temp,file,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);
                // Readback alone cannot persist a renamed directory entry across a host crash.
                // Unsupported directory force refuses before returning a native dispatch gate.
                forceDirectory(parent);
                var after=read();if(!after.present||!Arrays.equals(encoded,after.encoded.getBytes(StandardCharsets.UTF_8)))throw new IOException("Journal readback disagrees; uncertainty retained");
                return new VerifiedWrite(this,after);
            }catch(OverlappingFileLockException busy){throw new IOException("Journal lock busy",busy);}
        }finally{Files.deleteIfExists(temp);}
    }
}
