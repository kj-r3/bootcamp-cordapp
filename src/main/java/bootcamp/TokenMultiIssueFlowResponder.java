package bootcamp;

import co.paralleluniverse.fibers.Suspendable;
import net.corda.core.flows.FlowException;
import net.corda.core.flows.FlowLogic;
import net.corda.core.flows.FlowSession;
import net.corda.core.flows.InitiatedBy;
import net.corda.core.flows.ReceiveFinalityFlow;
import net.corda.core.flows.SignTransactionFlow;
import net.corda.core.transactions.SignedTransaction;

@InitiatedBy(TokenMultiIssueFlowInitiator.class)
public class TokenMultiIssueFlowResponder extends FlowLogic<SignedTransaction> {
 
    private final FlowSession otherSideSession;

    public TokenMultiIssueFlowResponder(FlowSession otherSideSession) {
        this.otherSideSession = otherSideSession;
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

        return subFlow(new ReceiveFinalityFlow(otherSideSession));
	}
    
}