package dev.veltraph.client.rotation;

/** Recognize only the caller's synchronous publication; replacement is not cleanup authority. */
public final class ParentMotionPublication {
    private ParentMotionPublication(){}
    public static boolean own(RotationReceiptIdentity.Stamp before,RotationReceiptIdentity.Stamp after,int count){
        return before!=null&&after!=null&&count>0&&count<=2&&before.authority()!=null
            &&after.generation()>before.generation()&&after.generation()-before.generation()==count
            &&before.service()==after.service()&&before.authority()==after.authority()
            &&before.player()==after.player()&&before.world()==after.world()&&before.connection()==after.connection();
    }
}
