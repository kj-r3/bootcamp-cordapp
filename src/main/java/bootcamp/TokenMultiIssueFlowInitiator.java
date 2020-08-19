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
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import kotlin.Pair;

@InitiatingFlow
@StartableByRPC
public class TokenMultiIssueFlowInitiator extends FlowLogic<SignedTransaction> {
    private final List<Pair<Party,Long>> heldQuantities;

    public TokenMultiIssueFlowInitiator(List<Pair<Party,Long>> heldQuantities) {
        this.heldQuantities = heldQuantities;
    }

    //This is the command line friendly constructor, makes sure that we can test in the IDE
    //Using Complex objects in the constructor is not testable in the IDE
    public TokenMultiIssueFlowInitiator(final Party holder, final long quantity) {
        this(Collections.singletonList(new Pair<>(holder, quantity)));
    }

    private final Step GENERATING_TRANSACTION = new Step("Generating transaction based on new token issuance.");
    private final Step VERIFYING_TRANSACTION = new Step("Verifying contract constraints.");
    private final Step SIGNING_TRANSACTION = new Step("Signing transaction with our private key.");
    private final Step GATHERING_HOLDERS = new Step("Gathering all the holders") {
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
                    GATHERING_HOLDERS,
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
         *  Phase 1 - Create our TokenState(s) to represent new on-ledger tokens!
         * ===========================================================================*/
        
        // We create our new TokenState.
        progressTracker.setCurrentStep(GENERATING_TRANSACTION);
        final List<TokenState> outputTokens = heldQuantities
            //piple list into a stream
            .stream()
            //extract each element into a new token state
            .map(it -> new TokenState(issuer, it.getFirst(), it.getSecond()))
            //collate each new element into a list
            .collect(Collectors.toList());


        /* ============================================================================
         *  Phase 2 - Build our token issuance transaction to update the ledger!
         * ===========================================================================*/
        // We build our transaction.
        TransactionBuilder txnBuilder = new TransactionBuilder();
        txnBuilder.setNotary(notary);
        List<PublicKey> requiredSigners = ImmutableList.of(issuer.getOwningKey());
        txnBuilder.addCommand(new TokenContract.Commands.Issue(), requiredSigners);
        outputTokens.forEach(it -> txnBuilder.addOutputState(it, TokenContract.TOKEN_CONTRACT_ID));

        /* ============================================================================
         *  Phase 3 - Verify new tokens meet the Contract standard and send out transaction for signature!
         * ===========================================================================*/
        progressTracker.setCurrentStep(VERIFYING_TRANSACTION);
        // We check our transaction is valid based on its contracts.
        txnBuilder.verify(getServiceHub());

        progressTracker.setCurrentStep(SIGNING_TRANSACTION);
        // We sign the transaction as the issuer with our private key, making it immutable.
        SignedTransaction signedTransaction = getServiceHub().signInitialTransaction(txnBuilder);

        progressTracker.setCurrentStep(GATHERING_HOLDERS);
        List<FlowSession> sessionList = outputTokens.stream()
            .map(TokenState::getHolder)        //get all the holders from the stream
            .distinct()                        //filter out dupes
            .filter(it -> !it.equals(issuer))  //filter ourselves out as we have signed and are the issuer
            .map(it -> this.initiateFlow(it))  //start flow for each of the holders
            .collect(Collectors.toList());     //return the sessions to the list (could be written this::initiateFlow)

        progressTracker.setCurrentStep(FINALIZING_TRANSACTION);
        // We get the transaction notarised and recorded automatically by the platform.
        
        SignedTransaction notarisedTxn = subFlow(new FinalityFlow(signedTransaction, sessionList, FINALIZING_TRANSACTION.childProgressTracker()));
        
        // We want our issuer to have a trace of the amounts that have been issued, whether it is a holder or not,
        // in order to know the total supply. Since the issuer is not in the participants, it needs to be done
        // manually. We do it after the FinalityFlow as this is the better way to do, after notarisation, even if
        // here there is no notarisation.
        getServiceHub().recordTransactions(StatesToRecord.ALL_VISIBLE, ImmutableList.of(notarisedTxn));
        
        return notarisedTxn;
    }
}
