package bootcamp;

import co.paralleluniverse.fibers.Suspendable;
import net.corda.core.flows.*;
import net.corda.core.transactions.SignedTransaction;

@InitiatedBy(TokenIssueFlowInitiator.class)
public class TokenIssueFlowResponder extends FlowLogic<SignedTransaction> {

    private final FlowSession otherSide;

    public TokenIssueFlowResponder(FlowSession otherSide) {
        this.otherSide = otherSide;
    }

    @Override
    @Suspendable
    public SignedTransaction call() throws FlowException {

    /* ===================================================================================================
    *  The following is commented out as the holder of a newly issued token does not need to sign the txn
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

        //Wait for the notary and transaction finalization.
        return subFlow(new ReceiveFinalityFlow(otherSide));

    }
}