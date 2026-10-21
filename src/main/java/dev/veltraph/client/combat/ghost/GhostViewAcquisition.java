package dev.veltraph.client.combat.ghost;

/** Retains the one mapped result before native selection publication. No permission survives a fresh refusal. */
public final class GhostViewAcquisition<T> {
    private GhostSourcePathJobTransaction.Scope scope;
    private GhostSourcePathJobTransaction.Target target;
    private T result;private boolean commitReady;
    public boolean pending(){return result!=null;}
    public void hold(GhostSourcePathJobTransaction.Scope s,GhostSourcePathJobTransaction.Target t,T r){
        if(pending()||s==null||t==null||r==null)throw new IllegalStateException("One actual mapped result required");
        scope=s;target=t;result=r;commitReady=false;
    }
    public boolean current(GhostSourcePathJobTransaction.Scope actual,GhostSourcePathJobTransaction.Target body,boolean freshGeometry){
        boolean current=pending()&&scope.same(actual)&&target.same(body)&&freshGeometry;
        if(!current)clear();return current;
    }
    public boolean observe(GhostSourcePathJobTransaction.Scope actual,GhostSourcePathJobTransaction.Target body,boolean freshGeometry,GhostFarTargetView.Frame view){
        commitReady=current(actual,body,freshGeometry)&&GhostFarTargetView.acquisitionReady(view);return commitReady;
    }
    public T resultForCommit(){if(!pending()||!commitReady)throw new IllegalStateException("Fresh actual aligned view required");return result;}
    public boolean ready(){return pending()&&commitReady;}
    public void clear(){scope=null;target=null;result=null;commitReady=false;}
}
