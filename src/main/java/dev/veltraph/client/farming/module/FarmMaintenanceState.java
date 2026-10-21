package dev.veltraph.client.farming.module;

import java.util.*;
import dev.veltraph.client.world.BlockPos;
import dev.veltraph.client.world.Vec3;

/** Immutable, explicitly named source fields. No reflection or live service reads. */
public final class FarmMaintenanceState {
    private final Identity engine;
    private final Map<String,Object> fields;
    private FarmMaintenanceState(Object engine, Map<String,Object> fields) {
        this.engine=new Identity(Objects.requireNonNull(engine));
        this.fields=Collections.unmodifiableMap(fields);
    }
    public static FarmMaintenanceState of(Object engine,Object... namedFields) {
        if(namedFields.length%2!=0)throw new IllegalArgumentException("Expected field names and values");
        var fields=new LinkedHashMap<String,Object>();
        for(int i=0;i<namedFields.length;i+=2) {
            String name=(String)namedFields[i];
            if(fields.containsKey(name))throw new IllegalArgumentException("Duplicate checkpoint field: "+name);
            fields.put(name,freeze(namedFields[i+1]));
        }
        return new FarmMaintenanceState(engine,fields);
    }
    public static Object identity(Object value) { return value==null?null:new Identity(value); }
    private static Object freeze(Object value) {
        if(value==null||value instanceof FarmMaintenanceState||value instanceof Identity
                ||value instanceof Enum<?>||value instanceof String||value instanceof Boolean
                ||value instanceof Byte||value instanceof Short||value instanceof Integer||value instanceof Long
                ||value instanceof Float||value instanceof Double||value instanceof Character
                ||value instanceof Vec3||value instanceof BlockPos)return value;
        if(value instanceof Optional<?> optional)return optional.map(FarmMaintenanceState::freeze);
        if(value instanceof Set<?> set){var copy=new HashSet<Object>();for(var v:set)copy.add(freeze(v));return Collections.unmodifiableSet(copy);}
        if(value instanceof List<?> list){var copy=new ArrayList<Object>();for(var v:list)copy.add(freeze(v));return Collections.unmodifiableList(copy);}
        if(value instanceof Map<?,?> map){var copy=new LinkedHashMap<Object,Object>();map.forEach((k,v)->copy.put(freeze(k),freeze(v)));return Collections.unmodifiableMap(copy);}
        // Immutable ports/callbacks/settings and RNG objects retain their original object.
        // Mutable source engines are explicitly expanded by each named maintenanceState method.
        return new Identity(value);
    }
    public Object engineIdentity(){return engine.value;}
    public Map<String,Object> fields(){return fields;}
    @Override public boolean equals(Object other){return other instanceof FarmMaintenanceState state&&engine.equals(state.engine)&&fields.equals(state.fields);}
    @Override public int hashCode(){return 31*engine.hashCode()+fields.hashCode();}
    private static final class Identity {
        private final Object value;
        private Identity(Object value){this.value=value;}
        @Override public boolean equals(Object other){return other instanceof Identity id&&value==id.value;}
        @Override public int hashCode(){return System.identityHashCode(value);}
    }
}
