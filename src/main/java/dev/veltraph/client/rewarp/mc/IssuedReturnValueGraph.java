package dev.veltraph.client.rewarp.mc;

import java.util.ArrayList;
import java.util.List;

/** Detached immutable values. Never interprets item data as configuration, cost, ability or authority. */
final class IssuedReturnValueGraph {
    private IssuedReturnValueGraph() {}
    static boolean exactClass(Object value,Class<?> expected,ClassLoader loader) {
        return value!=null && value.getClass()==expected && expected.getClassLoader()==loader;
    }
    static boolean oneOf(Object observed,Object... known) {
        for(Object value:known) if(observed==value) return true;
        return false;
    }
    record Value(String kind,String scalar,List<Value> children) {
        Value { children=List.copyOf(children); }
    }
    static final class Unsupported extends RuntimeException { }
    static final class Writer {
        static final int NODES=4096, DEPTH=16, STRING=8192, TEXT=65536, LORE_LINES=128;
        private int nodes,text;
        void depth(int depth) { if(depth<0 || depth>DEPTH) throw new Unsupported(); }
        void length(int length) { if(length<0 || length>NODES-nodes-1) throw new Unsupported(); }
        Value atom(String kind,String scalar,int depth) {
            depth(depth);
            if(kind==null || ++nodes>NODES) throw new Unsupported();
            if(scalar!=null) { if(scalar.length()>STRING || scalar.length()>TEXT-text) throw new Unsupported(); text+=scalar.length(); }
            return new Value(kind,scalar,List.of());
        }
        Value branch(String kind,List<Value> children,int depth) {
            depth(depth); if(kind==null || ++nodes>NODES || children.size()>NODES) throw new Unsupported();
            return new Value(kind,null,children);
        }
        Value bytes(byte[] array,int depth) {
            depth(depth); length(array.length); var children=new ArrayList<Value>(array.length);
            for(byte value:array) children.add(atom("byte",Byte.toString(value),depth+1));
            return branch("nbt-byte-array",children,depth);
        }
        Value ints(int[] array,int depth) {
            depth(depth); length(array.length); var children=new ArrayList<Value>(array.length);
            for(int value:array) children.add(atom("int",Integer.toString(value),depth+1));
            return branch("nbt-int-array",children,depth);
        }
        Value longs(long[] array,int depth) {
            depth(depth); length(array.length); var children=new ArrayList<Value>(array.length);
            for(long value:array) children.add(atom("long",Long.toString(value),depth+1));
            return branch("nbt-long-array",children,depth);
        }
    }
}
