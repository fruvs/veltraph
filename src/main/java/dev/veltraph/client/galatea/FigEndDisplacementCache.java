package dev.veltraph.client.galatea;

/** Cached measured END displacement. Local adapter refreshes before the Fig handler; original
 * equal-priority native listener registration order is unavailable and is not asserted here. */
public final class FigEndDisplacementCache {
    public record Sample(double x,double y,double z) {}
    private Object player,world,connection;
    private Sample sample;
    public void bind(Object player,Object world,Object connection) {
        clear();if(player==null||world==null||connection==null)return;
        this.player=player;this.world=world;this.connection=connection;
    }
    public boolean refresh(Object player,Object world,Object connection,double dx,double dy,double dz) {
        if(player==null||world==null||connection==null){clear();return false;}
        if(this.player!=player||this.world!=world||this.connection!=connection){clear();return false;}
        sample=new Sample(dx,dy,dz);return true;
    }
    public Sample sample(){return sample;}
    public void clear(){player=world=connection=null;sample=null;}
}
