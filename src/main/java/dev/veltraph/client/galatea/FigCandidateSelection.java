package dev.veltraph.client.galatea;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;

/** Source selection order only. Caller owns mode lists, exclusions, score and native ray. */
public final class FigCandidateSelection {
    private FigCandidateSelection() {}

    public static <T> Optional<T> select(List<T> modeCandidates, Predicate<T> eligible,
                                       Comparator<T> score, Predicate<T> sourceRayAccepted) {
        List<T> ranked = modeCandidates.stream().filter(eligible).sorted(score).toList();
        return selectRanked(ranked,sourceRayAccepted);
    }
    public static <T> Optional<T> selectRanked(List<T> ranked,Predicate<T> sourceRayAccepted) {
        for (int i = 0; i < Math.min(5, ranked.size()); i++) {
            T candidate = ranked.get(i);
            if (sourceRayAccepted.test(candidate)) return Optional.of(candidate);
        }
        return ranked.stream().findFirst();
    }
}
