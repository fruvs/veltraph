package dev.veltraph.client.hunting;
import java.util.Objects;
import java.util.function.Predicate;
/** Exact before/after native stack transition; every acknowledged leg retires its before-image. */
final class ShardOriginalStackCustody<T> {
    private T current,afterSwap,afterUse;
    void reset(T value){current=Objects.requireNonNull(value);afterSwap=afterUse=null;}
    boolean present(){return current!=null;}
    boolean matches(Predicate<T> actual){return current!=null&&(actual.test(current)||afterSwap!=null&&actual.test(afterSwap)||afterUse!=null&&actual.test(afterUse));}
    void issuedSwap(T expected){afterSwap=Objects.requireNonNull(expected);}
    boolean acknowledgeSwap(Predicate<T> actual){if(afterSwap==null)return true;if(!actual.test(afterSwap))return false;current=afterSwap;afterSwap=null;return true;}
    void issuedUse(T expected){afterUse=Objects.requireNonNull(expected);}
    boolean acknowledgeUse(Predicate<T> actual){if(afterUse==null)return true;if(!actual.test(afterUse))return false;current=afterUse;afterUse=null;return true;}
    void cleanup(){afterSwap=afterUse=null;}
}
