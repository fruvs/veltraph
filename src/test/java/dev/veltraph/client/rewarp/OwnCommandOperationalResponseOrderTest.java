package dev.veltraph.client.rewarp;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
/** Actual production ordering model only. No command receipt or native ACK permission is constructed. */
class OwnCommandOperationalResponseOrderTest {
 @Test void onePostNativeSendHandlerNormalCompletionCanBeConsumedOnce(){var o=new OwnCommandOperationalResponseOrder(40);assertTrue(o.begin(41));assertFalse(o.complete());assertTrue(o.returned(41,true));assertTrue(o.complete());assertTrue(o.consume(41));assertFalse(o.complete());assertFalse(o.consume(41));assertTrue(o.refused());}
 @Test void queuedPreCommandIngressCannotBeBorrowedByLaterResponse(){var o=new OwnCommandOperationalResponseOrder(40);assertFalse(o.begin(40));assertTrue(o.refused());assertFalse(o.begin(41));assertFalse(o.returned(41,true));}
 @Test void strictlyOlderReplayPermanentlyRefuses(){var o=new OwnCommandOperationalResponseOrder(40);assertFalse(o.begin(39));assertFalse(o.begin(41));assertFalse(o.complete());}
 @Test void duplicateHandlerBeforeOriginalReturnCannotAdoptEither(){var o=new OwnCommandOperationalResponseOrder(40);assertTrue(o.begin(41));assertFalse(o.begin(42));assertFalse(o.returned(41,true));assertFalse(o.returned(42,true));assertFalse(o.complete());}
 @Test void handlerThrowCannotBeRearmedByLaterNormalReply(){var o=new OwnCommandOperationalResponseOrder(40);assertTrue(o.begin(41));assertFalse(o.returned(41,false));assertFalse(o.begin(42));assertFalse(o.returned(42,true));assertTrue(o.refused());}
 @Test void prematureConsumerBeforeHandlerReturnCannotBeRetried(){var o=new OwnCommandOperationalResponseOrder(40);assertTrue(o.begin(41));assertFalse(o.consume(41));assertFalse(o.returned(41,true));assertFalse(o.consume(41));}
 @Test void anotherIngressCannotCloseTheOriginalNativeHandler(){var o=new OwnCommandOperationalResponseOrder(40);assertTrue(o.begin(41));assertFalse(o.returned(42,true));assertFalse(o.returned(41,true));}
 @Test void completionWithoutActualClaimDoesNotCreateObservation(){var o=new OwnCommandOperationalResponseOrder(40);assertFalse(o.returned(41,true));assertFalse(o.complete());}
 @Test void doubleHandlerExitRevokesCompletedObservation(){var o=new OwnCommandOperationalResponseOrder(40);assertTrue(o.begin(41));assertTrue(o.returned(41,true));assertFalse(o.returned(41,true));assertFalse(o.complete());}
 @Test void laterNativeHandlerBeforeConsumptionRevokesEarlierCompleteObservation(){var o=new OwnCommandOperationalResponseOrder(40);assertTrue(o.begin(41));assertTrue(o.returned(41,true));assertFalse(o.begin(42));assertFalse(o.consume(41));}
 @Test void wrongConsumeCannotBeRetriedWithTheRightOrigin(){var o=new OwnCommandOperationalResponseOrder(40);assertTrue(o.begin(41));assertTrue(o.returned(41,true));assertFalse(o.consume(42));assertFalse(o.consume(41));}
 @Test void explicitContextDeadlineOrManualRefusalNeverRearms(){var o=new OwnCommandOperationalResponseOrder(40);assertTrue(o.begin(41));assertTrue(o.returned(41,true));o.refuse();assertFalse(o.complete());assertFalse(o.consume(41));assertFalse(o.begin(42));}
 @Test void absentOrExhaustedNativeSendBaselineCannotProduceReply(){for(long b:new long[]{-1,Long.MAX_VALUE}){var o=new OwnCommandOperationalResponseOrder(b);assertTrue(o.refused());assertFalse(o.begin(0));assertFalse(o.begin(Long.MAX_VALUE));assertFalse(o.complete());}}
 @Test void unrelatedIngressOnAnotherConnectionDoesNotRequireGlobalPlusOne(){var o=new OwnCommandOperationalResponseOrder(40);assertTrue(o.begin(44));assertTrue(o.returned(44,true));assertTrue(o.consume(44));}
}
