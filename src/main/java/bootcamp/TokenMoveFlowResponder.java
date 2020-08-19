package bootcamp;

import co.paralleluniverse.fibers.Suspendable;
// import net.corda.core.crypto.SecureHash;
import net.corda.core.flows.*;
// import net.corda.core.serialization.CordaSerializable;
import net.corda.core.transactions.SignedTransaction;
// import net.corda.core.utilities.ProgressTracker;
// import net.corda.core.utilities.ProgressTracker.Step;

// @CordaSerializable
// enum TransactionRole {SIGNER,PARTICIPANT}

@InitiatedBy(TokenMoveFlowInitiator.class)
public class TokenMoveFlowResponder extends FlowLogic<SignedTransaction> {

    private final FlowSession otherSide;

    public TokenMoveFlowResponder(FlowSession otherSide) {
        this.otherSide = otherSide;
    }

    // private final Step RECEIVING_ROLE = new Step("Extracting responder role from incoming session");
    // private final Step SIGNING_TRANSACTION = new Step("Counter-signing transaction with our private key.") {
    //     @Override
    //     public ProgressTracker childProgressTracker() {
    //         return CollectSignaturesFlow.Companion.tracker();
    //     }
    // };
    // private final Step FINALIZING_TRANSACTION = new Step("Obtaining notary signature and recording transaction.") {
    //     @Override
    //     public ProgressTracker childProgressTracker() {
    //         return FinalityFlow.Companion.tracker();
    //     }
    // };

    // private final ProgressTracker progressTracker =
    //         new ProgressTracker(RECEIVING_ROLE,
    //                 SIGNING_TRANSACTION,
    //                 FINALIZING_TRANSACTION);
    // @Override
    // public ProgressTracker getProgressTracker() {
    //     return progressTracker;
    // }


    @Override
    @Suspendable
    public SignedTransaction call() throws FlowException {

    /* ===================================================================================================
    *  The following is commented out as the holder is validated in the initiator flow.  the receiver is not required to sign
    *  If a responder is required to sign, (as dictated by the signers list in the transaction command)
    *  the following code is necessary.
    * ====================================================================================================*/
    //    
    //     SignedTransaction signedTransaction = subFlow(new SignTransactionFlow(otherSideSession) {
    //         @Suspendable
    //         @Override
    //         protected void checkTransaction(SignedTransaction stx) throws FlowException {
                
    //             // Implement responder flow transaction checks here
    //         }
    //     });

    // We wait for the finalized transaction with our new tokens

        return subFlow(new ReceiveFinalityFlow(otherSide));
    }
}