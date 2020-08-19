package bootcamp;

import java.security.PublicKey;
import java.util.List;

import com.google.common.collect.ImmutableList;

import co.paralleluniverse.fibers.Suspendable;
import net.corda.core.contracts.StateAndRef;
import net.corda.core.flows.CollectSignaturesFlow;
import net.corda.core.flows.FinalityFlow;
import net.corda.core.flows.FlowException;
import net.corda.core.flows.FlowLogic;
import net.corda.core.flows.FlowSession;
import net.corda.core.flows.InitiatingFlow;
import net.corda.core.flows.StartableByRPC;
import net.corda.core.identity.Party;
import net.corda.core.node.StatesToRecord;
import net.corda.core.transactions.SignedTransaction;
import net.corda.core.transactions.TransactionBuilder;
import net.corda.core.utilities.ProgressTracker;
import net.corda.core.utilities.ProgressTracker.Step;

@InitiatingFlow
@StartableByRPC
public class TokenRedeemFlowInitiator extends FlowLogic<SignedTransaction> {

    private final Party owner;
    private final int quantity;

    public TokenRedeemFlowInitiator(Party owner, int quantity) {
        this.owner = owner;
        this.quantity = quantity;
    }

    private final Step GENERATING_TRANSACTION = new Step("Generating transaction based on new token issuance.");
    private final Step VERIFYING_TRANSACTION = new Step("Verifying contract constraints.");
    private final Step SIGNING_TRANSACTION = new Step("Signing transaction with our private key.");
    private final Step GATHERING_SIGS = new Step("Gathering signatures from the holder") {
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
            new ProgressTracker(GENERATING_TRANSACTION,
                    VERIFYING_TRANSACTION,
                    SIGNING_TRANSACTION,
                    GATHERING_SIGS,
                    FINALIZING_TRANSACTION);
    @Override
    public ProgressTracker getProgressTracker() {
        return progressTracker;
    }

    @Suspendable
    @Override
    public SignedTransaction call() throws FlowException {
        // establish our identity
        final Party issuer = getOurIdentity();

        /*
         * ============================================================================
         * Phase 1 - Find token that needs to be redeemed
         * ===========================================================================
         */
        progressTracker.setCurrentStep(GENERATING_TRANSACTION);
        // Extract all the tokens from the vault.
        final List<StateAndRef<TokenState>> tokenStateAndRefs = getServiceHub().getVaultService()
                .queryBy(TokenState.class).getStates();

        System.out.println(tokenStateAndRefs);
        // We find the `Token` with the correct issuer and amount.
        final StateAndRef<TokenState> inputTokenStateAndRef = tokenStateAndRefs.stream().filter(tokenStateAndRef -> {
            final TokenState tokenState = tokenStateAndRef.getState().getData();
            return tokenState.getIssuer().equals(issuer) && ((int) tokenState.getQuantity() == quantity && tokenState.getHolder().equals(owner));
        }).findAny().orElseThrow(() -> new FlowException("No matching token state found."));

        // Find the token that matches the quantity
        //final TokenState inputTokenState = inputTokenStateAndRef.getState().getData();

        /*
         * ============================================================================
         * Phase 2 - Build our token redeem transaction to update the ledger!
         * ===========================================================================
         */

        final TransactionBuilder txnBuilder = new TransactionBuilder();

        // Now that we have the matching input state, lets extract notary and create the correct output states.

        final Party notary = inputTokenStateAndRef.getState().getNotary();
        txnBuilder.setNotary(notary);

        // add the current token as the input state
        txnBuilder.addInputState(inputTokenStateAndRef);    

        final List<PublicKey> requiredSigners = ImmutableList.of(issuer.getOwningKey(), owner.getOwningKey());
        txnBuilder.addCommand(new TokenContract.Commands.Redeem(), requiredSigners);

        /*
         * ============================================================================
         * Phase 3 - Write our TokenContract to control token issuance!
         * ===========================================================================
         */
        progressTracker.setCurrentStep(VERIFYING_TRANSACTION);
        // We check our transaction is valid based on its contracts.
        txnBuilder.verify(getServiceHub());

        final FlowSession session = initiateFlow(owner); 

        progressTracker.setCurrentStep(SIGNING_TRANSACTION);
        
        // We sign the transaction with our private key, making it immutable.
        final SignedTransaction signedTransaction = getServiceHub().signInitialTransaction(txnBuilder);

        progressTracker.setCurrentStep(GATHERING_SIGS);

        final SignedTransaction fullySignedTransaction = subFlow(new CollectSignaturesFlow(signedTransaction, ImmutableList.of(session), GATHERING_SIGS.childProgressTracker()));

        progressTracker.setCurrentStep(FINALIZING_TRANSACTION);

        // We get the transaction notarised and recorded automatically by the platform.
        SignedTransaction notarisedTxn = subFlow(new FinalityFlow(fullySignedTransaction, ImmutableList.of(session), FINALIZING_TRANSACTION.childProgressTracker()));
        
        return notarisedTxn;
    }


}