package bootcamp;

import co.paralleluniverse.fibers.Suspendable;

import com.google.common.collect.ImmutableList;

import net.corda.core.flows.*;
import net.corda.core.identity.Party;
import net.corda.core.node.StatesToRecord;
import net.corda.core.transactions.SignedTransaction;
import net.corda.core.transactions.TransactionBuilder;
import net.corda.core.utilities.ProgressTracker;
import net.corda.core.utilities.ProgressTracker.Step;

import java.security.PublicKey;
import java.util.List;
import java.util.stream.Collectors;
import kotlin.Pair;

@InitiatingFlow
@StartableByRPC
public class TokenMultiIssueFlowInitiator extends FlowLogic<SignedTransaction> {
    private final List<Pair<Party, Long>> heldQuantities;

    public TokenMultiIssueFlowInitiator(List<Pair<Party, Long>> heldQuantities) {
        this.heldQuantities = heldQuantities;
    }

    private final Step GENERATING_TRANSACTION = new Step("Generating transaction based on new token issuance.");
    private final Step VERIFYING_TRANSACTION = new Step("Verifying contract constraints.");
    private final Step SIGNING_TRANSACTION = new Step("Signing transaction with our private key.");
    private final Step GATHERING_SIGS = new Step("Gathering the partner's signature.") {
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
        // We choose our transaction's notary (the notary prevents double-spends).
        Party notary = getServiceHub().getNetworkMapCache().getNotaryIdentities().get(0);
        // We get a reference to our own identity.
        Party issuer = getOurIdentity();

        /* ============================================================================
         *         TODO 1 - Create our TokenState to represent on-ledger tokens!
         * ===========================================================================*/
        // We create our new TokenState.
        progressTracker.setCurrentStep(GENERATING_TRANSACTION);
        final List<TokenState> outputTokens = heldQuantities
                .stream()
                .map(it -> new TokenState(issuer, it.getFirst(), it.getSecond()))
                .collect(Collectors.toList());


        /* ============================================================================
         *      TODO 3 - Build our token issuance transaction to update the ledger!
         * ===========================================================================*/
        // We build our transaction.
        TransactionBuilder txnBuilder = new TransactionBuilder();
        txnBuilder.setNotary(notary);
        List<PublicKey> requiredSigners = ImmutableList.of(issuer.getOwningKey());
        txnBuilder.addCommand(new TokenContract.Commands.Issue(), requiredSigners);
        outputTokens.forEach(it -> txnBuilder.addOutputState(it, TokenContract.TOKEN_CONTRACT_ID));

        /* ============================================================================
         *          TODO 2 - Write our TokenContract to control token issuance!
         * ===========================================================================*/
        progressTracker.setCurrentStep(VERIFYING_TRANSACTION);
        // We check our transaction is valid based on its contracts.
        txnBuilder.verify(getServiceHub());

        progressTracker.setCurrentStep(SIGNING_TRANSACTION);
        // We sign the transaction with our private key, making it immutable.
        SignedTransaction signedTransaction = getServiceHub().signInitialTransaction(txnBuilder);

        progressTracker.setCurrentStep(GATHERING_SIGS);
        List<FlowSession> sessionList = outputTokens.stream()
            .map(TokenState::getHolder)        //get all the holders from the stream
            .distinct()                        //filter out dupes
            .filter(it -> !it.equals(issuer))  //filter out any issuers here
            .map(it -> this.initiateFlow(it))  //start flow for each of the holders
            .collect(Collectors.toList());     //return the sessions to the list (could be written this::initiateFlow)


        // The counterparty signs the transaction
        SignedTransaction fullySignedTransaction = subFlow(new CollectSignaturesFlow(signedTransaction, sessionList, GATHERING_SIGS.childProgressTracker()));

        progressTracker.setCurrentStep(FINALIZING_TRANSACTION);
        // We get the transaction notarised and recorded automatically by the platform.
        
        SignedTransaction notarisedTxn = subFlow(new FinalityFlow(fullySignedTransaction, sessionList, FINALIZING_TRANSACTION.childProgressTracker()));
        
        // We want our issuer to have a trace of the amounts that have been issued, whether it is a holder or not,
        // in order to know the total supply. Since the issuer is not in the participants, it needs to be done
        // manually. We do it after the FinalityFlow as this is the better way to do, after notarisation, even if
        // here there is no notarisation.
        getServiceHub().recordTransactions(StatesToRecord.ALL_VISIBLE, ImmutableList.of(notarisedTxn));
        
        return notarisedTxn;
    }
}
