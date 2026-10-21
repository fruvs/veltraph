package dev.veltraph.client.farming.design;

import java.util.*;

/** Pure source-derived declared reconstruction: north banks and alternating natural end chambers. */
public final class ClassicalGourdAuthoredGeometry {
 public static final int ROWS=12,Y=70;public static final double BASE_YAW=-60;
 public record Cell(int x,int y,int z) {}
 public record Pose(double x,double y,double z) {}
 public record Box(double minX,double minY,double minZ,double maxX,double maxY,double maxZ){public boolean overlaps(Box b){return minX<b.maxX-1e-7&&maxX>b.minX+1e-7&&minY<b.maxY-1e-7&&maxY>b.minY+1e-7&&minZ<b.maxZ-1e-7&&maxZ>b.minZ+1e-7;}}
 public record Turn(int tick,Pose pose,boolean left,double yaw) {}
 public record Simulation(boolean completed,List<Turn> turns,Pose terminal,int ticks) {}
 private ClassicalGourdAuthoredGeometry(){}
 public static int band(int row){if(row<0||row>=ROWS)throw new IllegalArgumentException();return 80-row*6;}
 public static int bankMin(int row){return row%2==0?2:3;}
 public static int bankMax(int row){return row%2==0?92:93;}
 public static Pose start(){return new Pose(2.3,71,81.3);}
 public static Pose end(){return new Pose(2.3,71,15.3);}
 public static List<Pose> corners(){var out=new ArrayList<Pose>();for(int row=0;row<ROWS-1;row++)out.add(new Pose(row%2==0?93.7:2.3,71,band(row+1)+1.3));return List.copyOf(out);}
 public static Box body(Pose p){return new Box(p.x-.3,p.y,p.z-.3,p.x+.3,p.y+1.8,p.z+.3);}
 public static boolean corner(Pose p){return fraction(p.x)&&fraction(p.z);}
 private static boolean fraction(double v){long f=Math.round((v-Math.floor(v))*1000);return f==300||f==700;}
 public static Set<Cell> banks(){var out=new LinkedHashSet<Cell>();for(int row=0;row<ROWS;row++)for(int x=bankMin(row);x<=bankMax(row);x++)out.add(new Cell(x,71,band(row)));for(int x=2;x<=93;x++)out.add(new Cell(x,71,14));for(int z=10;z<=85;z++){out.add(new Cell(1,71,z));out.add(new Cell(94,71,z));}return Set.copyOf(out);}
 public static boolean bodyClear(Pose p){var b=body(p);for(var c:banks())if(b.overlaps(new Box(c.x,c.y,c.z,c.x+1,c.y+3,c.z+1)))return false;return true;}
 public static boolean supported(Pose p){return p.y==71&&p.x>=2.3-1e-7&&p.x<=93.7+1e-7&&p.z>=10.3&&p.z<=85.7&&bodyClear(p);}
 public static double[] vector(double yaw,boolean forward,boolean left){double a=Math.toRadians(yaw),s=left?1:-1,f=forward?1:0,x=s*Math.cos(a)-f*Math.sin(a),z=f*Math.cos(a)+s*Math.sin(a),length=Math.hypot(x,z);return new double[]{x/length,z/length};}
 static Pose move(Pose p,double dx,double dz){double x=p.x,z=p.z;boolean zFirst=Math.abs(dx)<Math.abs(dz);for(int n=0;n<2;n++){boolean axisX=zFirst?n==1:n==0;double amount=axisX?dx:dz;for(var c:banks()){if(axisX&&z+.3>c.z+1e-9&&z-.3<c.z+1-1e-9){if(amount>0&&x+.3<=c.x+1e-9)amount=Math.min(amount,c.x-(x+.3));if(amount<0&&x-.3>=c.x+1-1e-9)amount=Math.max(amount,c.x+1-(x-.3));}if(!axisX&&x+.3>c.x+1e-9&&x-.3<c.x+1-1e-9){if(amount>0&&z+.3<=c.z+1e-9)amount=Math.min(amount,c.z-(z+.3));if(amount<0&&z-.3>=c.z+1-1e-9)amount=Math.max(amount,c.z+1-(z-.3));}}if(axisX)x+=amount;else z+=amount;}return new Pose(x,p.y,z);}
 static Cell probe(Pose p,double yaw,boolean left){int facing=(int)Math.floor(yaw/90+.5)&3,i=(facing+(left?-1:1))&3;int[][] dirs={{0,1},{-1,0},{0,-1},{1,0}};return new Cell((int)Math.floor(p.x)+dirs[i][0],71,(int)Math.floor(p.z)+dirs[i][1]);}
 public static Simulation simulate(double delta,int limit){Pose p=start(),previous=null;boolean left=true,end=false;double yaw=BASE_YAW;int farming=0;var turns=new ArrayList<Turn>();for(int tick=0;tick<limit;tick++){Pose old=p;boolean forward=true;if(!end&&++farming>=20&&!banks().contains(probe(p,yaw,left))){end=true;forward=false;}if(end){forward=false;if(previous!=null&&corner(p)&&corner(previous)){turns.add(new Turn(tick,p,left,yaw));left=!left;yaw=left?BASE_YAW:BASE_YAW+delta;end=false;farming=0;previous=old;if(turns.size()==11){for(int t=tick+1;t<limit;t++){double[] v=vector(yaw,true,left);p=move(p,v[0]*.12,v[1]*.12);if(Math.abs(p.x-end().x)<.05&&Math.abs(p.z-end().z)<.05)return new Simulation(true,List.copyOf(turns),p,t);}return new Simulation(false,List.copyOf(turns),p,limit);}continue;}}double[] v=vector(yaw,forward,left);p=move(p,v[0]*.12,v[1]*.12);if(!supported(p))throw new AssertionError("Simulation escaped supported body-clear geometry");previous=old;}return new Simulation(false,List.copyOf(turns),p,limit);}
 public static void validate(){if(!supported(start())||!supported(end()))throw new AssertionError("Endpoints invalid");for(var p:corners())if(!supported(p)||!corner(p))throw new AssertionError("Corner invalid");}
}
