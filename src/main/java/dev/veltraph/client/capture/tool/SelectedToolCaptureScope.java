package dev.veltraph.client.capture.tool;

/** Exact in-memory native boundary. Account identity is compared in memory and never written. */
public final class SelectedToolCaptureScope {
    public record Frame(Object player,Object world,Object connection,Object account,int slot,String nativeId,boolean gameplay,boolean manual) {}
    private Frame admitted;
    public boolean start(Frame f){if(admitted!=null||!eligible(f))return false;admitted=f;return true;}
    public boolean current(Frame f){return admitted!=null&&eligible(f)&&admitted.player==f.player&&admitted.world==f.world&&admitted.connection==f.connection&&admitted.account.equals(f.account)&&admitted.slot==f.slot&&admitted.nativeId.equals(f.nativeId);}
    public void end(){admitted=null;}
    public void writerEnded(byte[] salt){end();if(salt!=null)java.util.Arrays.fill(salt,(byte)0);}
    public boolean active(){return admitted!=null;}
    private static boolean eligible(Frame f){return f!=null&&f.player!=null&&f.world!=null&&f.connection!=null&&f.account!=null&&f.slot>=0&&f.slot<=8&&f.nativeId!=null&&!f.nativeId.equals("minecraft:air")&&f.gameplay&&f.manual;}
}
