package dev.veltraph.client.navigation;
/** Evidence only; actual native admission requires private cleanup/Core/Parent receipts and its final leaves. */
public final class LegacyParentCleanupReentryIdentity {
 private LegacyParentCleanupReentryIdentity(){}
 /** Authentic existing core stored-field producer only. Enabled observers conservatively refuse this scoped proof. */
 public static boolean storedOnlyEnabled(dev.veltraph.client.core.ModuleManager root,dev.veltraph.client.core.ModuleManager.LifecycleSnapshot snapshot,dev.veltraph.client.core.Module expected){
  if(root==null||snapshot==null||!snapshot.known()||expected==null||!root.lifecycleCurrent(snapshot))return false;
  boolean found=false;
  for(var module:root.all()){
   if(!(module instanceof dev.veltraph.client.core.AbstractModule actual))return false;
   if(actual.isEnabled()){if(actual!=expected||found)return false;found=true;}
  }
  return found&&root.lifecycleCurrent(snapshot);
 }

 public static boolean sameControlScope(LegacyTravelPublicationIdentity.State original,LegacyTravelPublicationIdentity.State before,
     LegacyTravelPublicationIdentity.State after,Object parent){return same(original,before,parent,original==null?-1:original.epoch())&&same(original,after,parent,original==null?-1:original.epoch());}
 /** Numeric evidence only: native callers require the actual private original-token certificate before passing its epoch. */
 public static boolean sameControlScopeAtCertifiedEpoch(LegacyTravelPublicationIdentity.State original,LegacyTravelPublicationIdentity.State before,LegacyTravelPublicationIdentity.State after,Object parent,long certifiedEpoch){
  return certifiedEpoch>=0&&same(original,before,parent,certifiedEpoch)&&same(original,after,parent,certifiedEpoch);
 }
 private static boolean same(LegacyTravelPublicationIdentity.State a,LegacyTravelPublicationIdentity.State b,Object parent,long epoch){
  return a!=null&&b!=null&&parent!=null&&a.root()!=null&&a.player()!=null&&a.world()!=null&&a.connection()!=null&&a.config()!=null&&a.service()!=null
   &&a.registered()&&!a.enabled()&&a.activeLeg()==null&&a.inputOwner()==null&&a.cameraAuthority()==null&&a.parent()==parent&&b.parent()==parent&&a.root()==b.root()&&a.player()==b.player()&&a.world()==b.world()
   &&a.connection()==b.connection()&&a.config()==b.config()&&a.service()==b.service()&&a.revision()==b.revision()
   &&a.requestGeneration()==b.requestGeneration()&&epoch==b.epoch()&&a.publicationSerial()==b.publicationSerial()
   &&b.activeLeg()==null&&b.registered()&&b.enabled()&&b.usable()&&b.inputOwner()==parent&&b.cameraAuthority()==parent;
 }
}
