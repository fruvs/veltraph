package dev.veltraph.client.combat.ghost;

/** Source parent-area helper is a sampled scalar field, not Euclidean distance to an area. */
public final class GhostSourceParentField {
    private final int x,y,z;
    private final double[][][] values;
    public GhostSourceParentField(int x,int y,int z,double[][][] observedValues){
        this.x=x;this.y=y;this.z=z;
        if(observedValues==null)throw new IllegalArgumentException("Observed parent field required");
        values=new double[observedValues.length][][];
        for(int i=0;i<values.length;i++){
            if(observedValues[i]==null)throw new IllegalArgumentException("Complete observed field rows required");
            values[i]=new double[observedValues[i].length][];
            for(int j=0;j<values[i].length;j++){
                if(observedValues[i][j]==null)throw new IllegalArgumentException("Complete observed field columns required");
                values[i][j]=observedValues[i][j].clone();
                for(double value:values[i][j])if(!Double.isFinite(value))throw new IllegalArgumentException("Finite observed field value required");
            }
        }
    }
    public double at(int cellX,int cellY,int cellZ){
        long a=(long)cellX-x,b=(long)cellY-y,c=(long)cellZ-z;
        if(a<0||a>=values.length||b<0||b>=values[(int)a].length||c<0||c>=values[(int)a][(int)b].length)return 1;
        return values[(int)a][(int)b][(int)c];
    }
}
