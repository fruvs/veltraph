package dev.veltraph.client.chocolate;
import java.util.List;
/** Original SDK predicate algebra over already-read raw fields; no native/provider callbacks. */
final class ChocolateClickPrerequisiteRules {
    private ChocolateClickPrerequisiteRules(){}
    record Title(String literal,List<Title> siblings){Title{siblings=List.copyOf(siblings);}}
    static boolean alive(Object removal,Object health){return removal==null&&health instanceof Float value&&value.floatValue()>0;}
    static boolean empty(boolean canonicalEmpty,int count,boolean air){return canonicalEmpty||count<=0||air;}
    static boolean menu(int id,int revision,int size,int expectedId,int expectedRevision,int expectedSize){return id==expectedId&&revision==expectedRevision&&size==expectedSize;}
    static boolean title(Title original,Title actual){return original!=null&&actual!=null&&original.equals(actual);}
}
