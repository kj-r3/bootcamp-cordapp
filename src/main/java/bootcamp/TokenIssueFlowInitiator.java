package bootcamp;

import co.paralleluniverse.fibers.Suspendable;
import com.google.common.collect.ImmutableList;
import net.corda.core.flows.*;
import net.corda.core.identity.Party;
import net.corda.core.transactions.SignedTransaction;
import net.corda.core.transactions.TransactionBuilder;
import net.corda.core.utilities.ProgressTracker;

import java.security.PublicKey;
import java.util.List;

import static java.util.Collections.singletonList;

@InitiatingFlow
@StartableByRPC
public class TokenIssueFlowInitiator extends FlowLogic<SignedTransaction> {
    private final Party owner;
    private final int amount;

    public TokenIssueFlowInitiator(Party owner, int amount) {
        this.owner = owner;
        this.amount = amount;
    }

    private final ProgressTracker progressTracker = new ProgressTracker();

    @Override
    public ProgressTracker getProgressTracker() {
        return progressTracker;
    }

    @Suspendable
    @Override
    public SignedTransaction call() throws FlowException {
        // We choose our transaction's notary (the notary prevents double-spends).
        Party notary = getServiceHub().getNetworkMapCache().getNotaryIdentities().get(0);
        // We get a reference to our own identity.
        Party issuer = getOurIdentity();

        /* ============================================================================
         *  Phase 1 - Create our TokenState to represent on-ledger tokens!
         * ===========================================================================*/
        // We create our new TokenState.
        TokenState tokenState = new TokenState(issuer, owner, amount);


        /* ============================================================================
         *  Phase 2 - Build our token issuance transaction to update the ledger!
         * ===========================================================================*/
        // We build our transaction.
        TransactionBuilder txnBuilder = new TransactionBuilder();
        txnBuilder.setNotary(notary);
        txnBuilder.addOutputState(tokenState, TokenContract.TOKEN_CONTRACT_ID);
        List<PublicKey> requiredSigners = ImmutableList.of(tokenState.getIssuer().getOwningKey());
        txnBuilder.addCommand(new TokenContract.Commands.Issue(), requiredSigners);

        /* ============================================================================
         *  Phase 3 - Verify the transaction and notify the owner of the new tokens!
         * ===========================================================================*/
        // We check our transaction is valid based on its contracts.
        txnBuilder.verify(getServiceHub());

        FlowSession session = initiateFlow(owner);

        // We sign the transaction with our private key, making it immutable.
        SignedTransaction signedTransaction = getServiceHub().signInitialTransaction(txnBuilder);

        // We get the transaction notarised and recorded automatically by the platform.
        return subFlow(new FinalityFlow(signedTransaction, singletonList(session)));
    }
}