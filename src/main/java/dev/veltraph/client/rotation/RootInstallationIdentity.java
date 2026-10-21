package dev.veltraph.client.rotation;
/** Inert exact original-installation equality only. Actual native Root factory alone mints the private handle. */
public final class RootInstallationIdentity {
 private RootInstallationIdentity(){}
 public record State(Object installation,Object client,Object provider,boolean nativeThread){}
 public static boolean sameOriginal(State original,State live){return original!=null&&live!=null&&original.installation()!=null&&original.client()!=null&&original.provider()!=null
  &&live.nativeThread()&&original.installation()==live.installation()&&original.client()==live.client()&&original.provider()==live.provider();}
}
