package bootcamp;

import co.paralleluniverse.fibers.Suspendable;
import net.corda.core.flows.CollectSignaturesFlow;
import net.corda.core.flows.FinalityFlow;
import net.corda.core.flows.FlowException;
import net.corda.core.flows.FlowLogic;
import net.corda.core.flows.FlowSession;
import net.corda.core.flows.InitiatedBy;
import net.corda.core.flows.ReceiveFinalityFlow;
import net.corda.core.flows.SignTransactionFlow;
import net.corda.core.transactions.SignedTransaction;
import net.corda.core.utilities.ProgressTracker;
import net.corda.core.utilities.ProgressTracker.Step;

@InitiatedBy(TokenRedeemFlowInitiator.class)
public class TokenRedeemFlowResponder extends FlowLogic<SignedTransaction> {

    private final FlowSession otherside;

    public TokenRedeemFlowResponder(FlowSession otherside) {
        this.otherside = otherside;
    }

    private final Step SIGNING_TRANSACTION = new Step("Counter-signing transaction with our private key.") {
        @Override
        public ProgressTracker childProgressTracker() {
            return CollectSignaturesFlow.Companion.tracker();
        }
    };
    private final Step FINALIZING_TRANSACTION = new Step("Obtaining notary signature and recording transaction.") {
        @Override
        public ProgressTracker childProgressTracker() {
            return FinalityFlow.Companion.tracker();
        }
    };

    private final ProgressTracker progressTracker =
            new ProgressTracker(SIGNING_TRANSACTION,
                    FINALIZING_TRANSACTION);
    @Override
    public ProgressTracker getProgressTracker() {
        return progressTracker;
    }


    @Override
    @Suspendable
    public SignedTransaction call() throws FlowException {

        SignedTransaction signedTransaction = subFlow(new SignTransactionFlow(otherside) {
            @Suspendable
            @Override
            protected void checkTransaction(SignedTransaction stx) throws FlowException {
                
                // Implement responder flow transaction checks here
            }
        });

    // We wait for the finalized transaction with our new tokens

        return subFlow(new ReceiveFinalityFlow(otherside, signedTransaction.getId()));
    }
    
    
}