package bootcamp;

import java.security.PublicKey;
import java.util.List;
import java.util.stream.Collectors;

import com.google.common.collect.ImmutableList;

import co.paralleluniverse.fibers.Suspendable;
import net.corda.core.contracts.StateAndRef;
import net.corda.core.flows.*;
import net.corda.core.identity.Party;
import net.corda.core.node.StatesToRecord;
import net.corda.core.serialization.CordaSerializable;
import net.corda.core.transactions.SignedTransaction;
import net.corda.core.transactions.TransactionBuilder;
import net.corda.core.utilities.ProgressTracker;
import net.corda.core.utilities.ProgressTracker.Step;

@CordaSerializable
enum TransactionRole {SIGNER,PARTICIPANT}

@InitiatingFlow
@StartableByRPC
public class TokenMoveFlowInitiator extends FlowLogic<SignedTransaction> {
    private final Party issuer;
    private final int quantity;
    private final Party newOwner;

    public TokenMoveFlowInitiator (final Party issuer, final int quantity, final Party newOwner) {
        this.issuer = issuer;
        this.quantity = quantity;
        this.newOwner = newOwner;
    }

    private final Step GENERATING_TRANSACTION = new Step("Generating transaction based on tokens available.");
    private final Step VERIFYING_TRANSACTION = new Step("Verifying contract constraints.");
    private final Step SIGNING_TRANSACTION = new Step("Signing transaction with our private key.");
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
                    FINALIZING_TRANSACTION);
    @Override
    public ProgressTracker getProgressTracker() {
        return progressTracker;
    }

    @Suspendable
    @Override
    public SignedTransaction call() throws FlowException {
        // establish our identity
        final Party owner = getOurIdentity();

        if(owner.equals(newOwner)) throw new FlowException("Cannot move tokens between same entity.  The new owner cannot be the same as the initiator of the flow.");

        /*
         * ============================================================================
         * Phase 1 - Find token that needs to be transferred
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
            return tokenState.getIssuer().equals(issuer) && ((int) tokenState.getQuantity() >= quantity && tokenState.getHolder().equals(owner));
        }).findAny().orElseThrow(() -> new FlowException("Not enough tokens were found."));

        // Find the token that matches the quantity
        final TokenState inputTokenState = inputTokenStateAndRef.getState().getData();

        /*
         * ============================================================================
         * Phase 2 - Build our token move transaction to update the ledger!
         * ===========================================================================
         */

        final TransactionBuilder txnBuilder = new TransactionBuilder();

        // Now that we have the matching input state, lets extract notary and create the correct output states.

        final Party notary = inputTokenStateAndRef.getState().getNotary();
        txnBuilder.setNotary(notary);

        // add the current token as the input state
        txnBuilder.addInputState(inputTokenStateAndRef);    

        // Build new TokenState that transfers tokens to new owner and add to transaction.
        final TokenState newOwnerState = new TokenState(inputTokenState.getIssuer(), newOwner, quantity);
        txnBuilder.addOutputState(newOwnerState, TokenContract.TOKEN_CONTRACT_ID);

        // If there is a remaining quantity, we must create a state that will remain with the current owner
        if((int) inputTokenState.getQuantity() - quantity > 0) {
            final TokenState ownerNewState = new TokenState(inputTokenState.getIssuer(), owner, (int) inputTokenState.getQuantity() - quantity);
            txnBuilder.addOutputState(ownerNewState, TokenContract.TOKEN_CONTRACT_ID);
        }

        final List<PublicKey> requiredSigners = ImmutableList.of(owner.getOwningKey());
        txnBuilder.addCommand(new TokenContract.Commands.Move(), requiredSigners);

        /*
         * ============================================================================
         * Phase 3 - Write our TokenContract to control token issuance!
         * ===========================================================================
         */
        progressTracker.setCurrentStep(VERIFYING_TRANSACTION);
        // We check our transaction is valid based on its contracts.
        txnBuilder.verify(getServiceHub());

        // final List<FlowSession> sigSession = Collections.singletonList(initiateFlow(newOwner));
        final List<FlowSession> finalSessionList = ImmutableList.of(newOwner, issuer)
            .stream().map(it -> this.initiateFlow(it)).collect(Collectors.toList());

        progressTracker.setCurrentStep(SIGNING_TRANSACTION);
        
        // We sign the transaction with our private key, making it immutable.
        final SignedTransaction signedTransaction = getServiceHub().signInitialTransaction(txnBuilder);

        progressTracker.setCurrentStep(FINALIZING_TRANSACTION);

        // We get the transaction notarised and recorded automatically by the platform.
        SignedTransaction notarisedTxn = subFlow(new FinalityFlow(signedTransaction, finalSessionList, FINALIZING_TRANSACTION.childProgressTracker()));
        
        // We want our issuer to have a trace of the amounts that have been issued, whether it is a holder or not,
        // in order to know the total supply. Since the issuer is not in the participants, it needs to be done
        // manually. We do it after the FinalityFlow as this is the better way to do, after notarisation, even if
        // here there is no notarisation.
        getServiceHub().recordTransactions(StatesToRecord.ALL_VISIBLE, ImmutableList.of(notarisedTxn));
        
        return notarisedTxn;

    }
    
}