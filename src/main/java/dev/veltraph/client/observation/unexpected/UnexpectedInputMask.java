package dev.veltraph.client.observation.unexpected;
/** Stable local input summary. Attack/use are observations, never displacement evidence. */
final class UnexpectedInputMask {
    static final int KEYS=9,MOVEMENT=0x7f,ATTACK=1<<7,USE=1<<8,ALL=(1<<KEYS)-1;
    private UnexpectedInputMask(){}
    static long expected(long before,long matched,boolean down){if(!valid(before)||!valid(matched))throw new IllegalArgumentException("Invalid native input mask");return down?before|matched:before&~matched;}
    static boolean valid(long bits){return bits>=0&&(bits&~ALL)==0;}
    static boolean movement(long bits){return (bits&MOVEMENT)!=0;}
}
