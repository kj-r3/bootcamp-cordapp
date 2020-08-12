package bootcamp;

import net.corda.core.contracts.CommandData;
import net.corda.core.contracts.CommandWithParties;
import net.corda.core.contracts.Contract;
import net.corda.core.identity.Party;
import net.corda.core.transactions.LedgerTransaction;
import org.jetbrains.annotations.NotNull;

import java.security.PublicKey;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static net.corda.core.contracts.ContractsDSL.requireSingleCommand;
import static net.corda.core.contracts.ContractsDSL.requireThat;

// ************
// * Contract *
// ************

/**
 * Implementation of a basic token contract in Corda
 *
 * This contract enforces rules regarding the creation of tokens [TokenState], which encapsulates tokens
 *
 * For a new [Token] to be issued, a transaction is required which takes:
 *  - Zero input states.
 *  - One or more output states:  The new [Token]
 *  - A Issue() command with the public keys of the issuers of the token
 *
 *  For a [Token] to be transferred, a transaction is required which takes:
 *  - One input state (issued tokens) that has not been consumed
 *  - Two output state: One output for the issuer of the move and another for the holder
 *  - A Move() command with the public keys of the issuer (initiator) and the holder (receiver) of the token
 *
 *  For a [Token] to be redeemed, a transaction is required which takes:
 *  - One input state (issued tokens) that has not been consumed
 *  - One output state: One output just for the issuer (initiator) removing the quantity of the tokens
 *  - A Redeem() command with the public key of the issuer
 */
public final class TokenContract implements Contract {
    // This is used to identify our contract when building a transaction.
    public static final String TOKEN_CONTRACT_ID = "bootcamp.TokenContract";

    // A transaction is valid if the verify() function of the contract of all the transaction's input and output states
    // does not throw an exception.
    @Override
    public void verify(@NotNull final LedgerTransaction tx) {
        final CommandWithParties<Commands> command = requireSingleCommand(tx.getCommands(), Commands.class);

        // This contract does not care about states it has no knowledge about
        // The following variables are used for various verifications
        final List<TokenState> inputs = tx.inputsOfType(TokenState.class);
        final List<TokenState> outputs = tx.outputsOfType(TokenState.class);
        final boolean hasPositiveQuantities =
                inputs.stream().allMatch(it -> 0 < it.getQuantity()) &&
                        outputs.stream().allMatch(it -> 0 < it.getQuantity());
        final Set<PublicKey> allInputHolderKeys = inputs.stream()
                .map(it-> it.getHolder().getOwningKey())
                .collect(Collectors.toSet());

        if(command.getValue() instanceof Commands.Issue){
            requireThat(require -> {
                //Generic constraints around the Issue Token command.
                require.using("No tokens should be consumed, in inputs, when creating a token.",
                        inputs.isEmpty());
                require.using("Token output state should be created when issuing new tokens.",
                        !outputs.isEmpty());

                //Constraints of the tokens themselves.
                require.using("All quantities must be above 0.",
                        hasPositiveQuantities);

                //Constraints on the signers of the issuance.
                require.using("The issuers should sign.",
                        command.getSigners().containsAll(outputs.stream()
                                .map(it -> it.getIssuer().getOwningKey())
                                .collect(Collectors.toSet())
                        ));

                return null;
            });
        } else if(command.getValue() instanceof Commands.Move) {
            requireThat(require -> {
                //Generic constraints around the Move Token command.
                require.using("At least one inputs should be consumed when moving tokens.,",
                        !inputs.isEmpty());
                require.using("At least one output should be created when moving tokens.",
                        !outputs.isEmpty());

                //Constraints of the tokens themselves.
                require.using("All quantities must be above 0.",
                        hasPositiveQuantities);
                final Map<Party, Long> inputSums = TokenStateUtilities.mapSumByIssuer(inputs);
                final Map<Party, Long> outputSums = TokenStateUtilities.mapSumByIssuer(outputs);
                require.using("The list of issuers should be conserved.",
                        inputSums.keySet().equals(outputSums.keySet()));
                require.using("The sum of quantities for each issuer should be conserved.",
                        inputSums.entrySet().stream()
                                .allMatch(entry -> outputSums.get(entry.getKey()).equals(entry.getValue())));

                //Constraints on the signers of the move.
                require.using("The current holders should sign.",
                        command.getSigners().containsAll(allInputHolderKeys));

                return null;
            });
        } else if (command.getValue() instanceof Commands.Redeem){
            requireThat(require -> {
                //Generic constraints around the Redeem Token command.
                require.using("Inputs should be consumed when redeeming Tokens,",
                        !inputs.isEmpty());
                require.using("No output states should be created when redeeming Tokens.",
                        outputs.isEmpty());

                //Constraints around the tokens themselves
                require.using("All quantities must be above 0.", hasPositiveQuantities);

                //Constraints on the signers.
                require.using("The issuers should sign.",
                        command.getSigners().containsAll(inputs.stream()
                                .map(it -> it.getIssuer().getOwningKey())
                                .collect(Collectors.toSet())
                        ));
                require.using("The current holders should sign.",
                        command.getSigners().containsAll(allInputHolderKeys));

                return null;
            });
        } else {
            throw new IllegalArgumentException("Unknown command " + command.getValue());
        }
    }

    // Used to indicate the transaction's intent.
    public interface Commands extends CommandData {
        class Issue implements Commands {}
        class Move implements Commands {}
        class Redeem implements Commands {}
    }
}