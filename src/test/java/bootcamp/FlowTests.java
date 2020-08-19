package bootcamp;

import net.corda.core.concurrent.CordaFuture;
import net.corda.core.contracts.Command;
import net.corda.core.contracts.TransactionState;
import net.corda.core.transactions.SignedTransaction;
import net.corda.testing.node.MockNetwork;
import net.corda.testing.node.MockNetworkParameters;
import net.corda.testing.node.StartedMockNode;
import net.corda.testing.node.TestCordapp;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.Collections;
import java.util.concurrent.ExecutionException;

import static org.junit.Assert.*;

public class FlowTests {
    private MockNetwork network;
    private StartedMockNode nodeA;
    private StartedMockNode nodeB;
    private StartedMockNode nodeC;

    @Before
    public void setup() {
        network = new MockNetwork(
                new MockNetworkParameters(
                        Collections.singletonList(TestCordapp.findCordapp("bootcamp"))
                )
        );
        nodeA = network.createPartyNode(null);
        nodeB = network.createPartyNode(null);
        nodeC = network.createPartyNode(null);
        network.runNetwork();
    }

    @After
    public void tearDown() {
        network.stopNodes();
    }

   @Test
   public void transactionConstructedByFlowUsesTheCorrectNotary() throws Exception {
       TokenIssueFlowInitiator flow = new TokenIssueFlowInitiator(nodeB.getInfo().getLegalIdentities().get(0), 99);
       CordaFuture<SignedTransaction> future = nodeA.startFlow(flow);
       network.runNetwork();
       SignedTransaction signedTransaction = future.get();

       assertEquals(1, signedTransaction.getTx().getOutputStates().size());
       TransactionState output = signedTransaction.getTx().getOutputs().get(0);

       assertEquals(network.getNotaryNodes().get(0).getInfo().getLegalIdentities().get(0), output.getNotary());
   }

   @Test
   public void transactionConstructedByFlowHasOneTokenStateOutputWithTheCorrectAmountAndOwner() throws Exception {
       TokenIssueFlowInitiator flow = new TokenIssueFlowInitiator(nodeB.getInfo().getLegalIdentities().get(0), 99);
       CordaFuture<SignedTransaction> future = nodeA.startFlow(flow);
       network.runNetwork();
       SignedTransaction signedTransaction = future.get();

       assertEquals(1, signedTransaction.getTx().getOutputStates().size());
       TokenState output = signedTransaction.getTx().outputsOfType(TokenState.class).get(0);

       assertEquals(nodeB.getInfo().getLegalIdentities().get(0), output.getHolder());
       assertEquals(99, output.getQuantity());
   }

   @Test
   public void transactionConstructedByFlowHasOneOutputUsingTheCorrectContract() throws Exception {
       TokenIssueFlowInitiator flow = new TokenIssueFlowInitiator(nodeB.getInfo().getLegalIdentities().get(0), 99);
       CordaFuture<SignedTransaction> future = nodeA.startFlow(flow);
       network.runNetwork();
       SignedTransaction signedTransaction = future.get();

       assertEquals(1, signedTransaction.getTx().getOutputStates().size());
       TransactionState output = signedTransaction.getTx().getOutputs().get(0);

       assertEquals("bootcamp.TokenContract", output.getContract());
   }

   @Test
   public void transactionConstructedByFlowHasOneIssueCommand() throws Exception {
       TokenIssueFlowInitiator flow = new TokenIssueFlowInitiator(nodeB.getInfo().getLegalIdentities().get(0), 99);
       CordaFuture<SignedTransaction> future = nodeA.startFlow(flow);
       network.runNetwork();
       SignedTransaction signedTransaction = future.get();

       assertEquals(1, signedTransaction.getTx().getCommands().size());
       Command command = signedTransaction.getTx().getCommands().get(0);

       assert (command.getValue() instanceof TokenContract.Commands.Issue);
   }

   @Test
   public void transactionConstructedByFlowHasOneCommandWithTheIssuerASigner() throws Exception {
       TokenIssueFlowInitiator flow = new TokenIssueFlowInitiator(nodeB.getInfo().getLegalIdentities().get(0), 99);
       CordaFuture<SignedTransaction> future = nodeA.startFlow(flow);
       network.runNetwork();
       SignedTransaction signedTransaction = future.get();

       assertEquals(1, signedTransaction.getTx().getCommands().size());
       Command command = signedTransaction.getTx().getCommands().get(0);

       assertEquals(1, command.getSigners().size());
       assertTrue(command.getSigners().contains(nodeA.getInfo().getLegalIdentities().get(0).getOwningKey()));
       assertFalse(command.getSigners().contains(nodeB.getInfo().getLegalIdentities().get(0).getOwningKey()));
   }

   @Test
   public void transactionConstructedByFlowHasNoInputsAttachmentsOrTimeWindows() throws Exception {
       TokenIssueFlowInitiator flow = new TokenIssueFlowInitiator(nodeB.getInfo().getLegalIdentities().get(0), 99);
       CordaFuture<SignedTransaction> future = nodeA.startFlow(flow);
       network.runNetwork();
       SignedTransaction signedTransaction = future.get();

       assertEquals(0, signedTransaction.getTx().getInputs().size());
       // The single attachment is the contract attachment.
       assertEquals(1, signedTransaction.getTx().getAttachments().size());
       assertNull(signedTransaction.getTx().getTimeWindow());
   }

   @Test
   public void transactionFullyMovingIssuedTokensHasCorrectOwnerAndOutputs() throws Exception {
       TokenIssueFlowInitiator flow = new TokenIssueFlowInitiator(nodeB.getInfo().getLegalIdentities().get(0), 99);
       CordaFuture<SignedTransaction> future = nodeA.startFlow(flow);
       network.runNetwork();
       SignedTransaction signedTransaction = future.get();

       assertEquals(1, signedTransaction.getTx().getOutputStates().size());

       TokenMoveFlowInitiator moveFlow = new TokenMoveFlowInitiator(nodeA.getInfo().getLegalIdentities().get(0), 99, nodeC.getInfo().getLegalIdentities().get(0));
       CordaFuture<SignedTransaction> future2 = nodeB.startFlow(moveFlow);
       network.runNetwork();
       SignedTransaction signedTransaction2 = future2.get();
       TokenState output = signedTransaction2.getTx().outputsOfType(TokenState.class).get(0);

       assertEquals(1, signedTransaction2.getTx().getOutputStates().size());
       assertEquals(output.getHolder(), nodeC.getInfo().getLegalIdentities().get(0));
   }

   @Test
   public void transactionPartiallyMovingIssuedTokensHasCorrectOwnerAndOutputs() throws Exception {
       TokenIssueFlowInitiator flow = new TokenIssueFlowInitiator(nodeB.getInfo().getLegalIdentities().get(0), 99);
       CordaFuture<SignedTransaction> future = nodeA.startFlow(flow);
       network.runNetwork();
       SignedTransaction signedTransaction = future.get();

       assertEquals(1, signedTransaction.getTx().getOutputStates().size());

       TokenMoveFlowInitiator moveFlow = new TokenMoveFlowInitiator(nodeA.getInfo().getLegalIdentities().get(0), 98, nodeC.getInfo().getLegalIdentities().get(0));
       CordaFuture<SignedTransaction> future2 = nodeB.startFlow(moveFlow);
       network.runNetwork();
       SignedTransaction signedTransaction2 = future2.get();
       TokenState output1 = signedTransaction2.getTx().outputsOfType(TokenState.class).get(0);
       TokenState output2 = signedTransaction2.getTx().outputsOfType(TokenState.class).get(1);

       assertEquals(2, signedTransaction2.getTx().getOutputStates().size());
       assertEquals(output1.getHolder(), nodeC.getInfo().getLegalIdentities().get(0));
       assertEquals(output2.getHolder(), nodeB.getInfo().getLegalIdentities().get(0));
   }

   @Test(expected = ExecutionException.class)
   public void transactionCannotMoveMoreTokensThanIssued() throws Exception {
       TokenIssueFlowInitiator flow = new TokenIssueFlowInitiator(nodeB.getInfo().getLegalIdentities().get(0), 99);
       CordaFuture<SignedTransaction> future = nodeA.startFlow(flow);
       network.runNetwork();
       SignedTransaction signedTransaction = future.get();

       assertEquals(1, signedTransaction.getTx().getOutputStates().size());

       TokenMoveFlowInitiator moveFlow = new TokenMoveFlowInitiator(nodeA.getInfo().getLegalIdentities().get(0), 100, nodeC.getInfo().getLegalIdentities().get(0));
       CordaFuture<SignedTransaction> future2 = nodeB.startFlow(moveFlow);
       network.runNetwork();
       SignedTransaction signedTransaction2 = future2.get();
   }


   @Test(expected = ExecutionException.class)
   public void transactionMovingIssuedTokensCannotBeToSameEntity() throws Exception {
       TokenIssueFlowInitiator flow = new TokenIssueFlowInitiator(nodeB.getInfo().getLegalIdentities().get(0), 99);
       CordaFuture<SignedTransaction> future = nodeA.startFlow(flow);
       network.runNetwork();
       SignedTransaction signedTransaction = future.get();

       assertEquals(1, signedTransaction.getTx().getOutputStates().size());

       TokenMoveFlowInitiator moveFlow = new TokenMoveFlowInitiator(nodeA.getInfo().getLegalIdentities().get(0), 98, nodeB.getInfo().getLegalIdentities().get(0));
       CordaFuture<SignedTransaction> future2 = nodeB.startFlow(moveFlow);
       network.runNetwork();
       SignedTransaction signedTransaction2 = future2.get();
   }

   @Test
   public void transactionRedeemTokensHasCorrectOutputs() throws Exception {
       TokenIssueFlowInitiator flow = new TokenIssueFlowInitiator(nodeB.getInfo().getLegalIdentities().get(0), 99);
       CordaFuture<SignedTransaction> future = nodeA.startFlow(flow);
       network.runNetwork();
       SignedTransaction signedTransaction = future.get();

       assertEquals(1, signedTransaction.getTx().getOutputStates().size());

       TokenRedeemFlowInitiator redeemFlow = new TokenRedeemFlowInitiator(nodeB.getInfo().getLegalIdentities().get(0), 99);
       CordaFuture<SignedTransaction> future2 = nodeA.startFlow(redeemFlow);
       network.runNetwork();
       SignedTransaction signedTransaction2 = future2.get();

       assertEquals(0, signedTransaction2.getTx().getOutputStates().size());
   }

   @Test(expected = ExecutionException.class)
   public void transactionRedeemTokensCanOnlyBeFromIssuer() throws Exception {
       TokenIssueFlowInitiator flow = new TokenIssueFlowInitiator(nodeB.getInfo().getLegalIdentities().get(0), 99);
       CordaFuture<SignedTransaction> future = nodeA.startFlow(flow);
       network.runNetwork();
       SignedTransaction signedTransaction = future.get();

       TokenRedeemFlowInitiator redeemFlow = new TokenRedeemFlowInitiator(nodeB.getInfo().getLegalIdentities().get(0), 99);
       CordaFuture<SignedTransaction> future2 = nodeB.startFlow(redeemFlow);
       network.runNetwork();
       SignedTransaction signedTransaction2 = future2.get();
   }

   @Test(expected = ExecutionException.class)
   public void transactionCannotRedeemMoreTokensThanIssued() throws Exception {
       TokenIssueFlowInitiator flow = new TokenIssueFlowInitiator(nodeB.getInfo().getLegalIdentities().get(0), 99);
       CordaFuture<SignedTransaction> future = nodeA.startFlow(flow);
       network.runNetwork();
       SignedTransaction signedTransaction = future.get();

       assertEquals(1, signedTransaction.getTx().getOutputStates().size());

       TokenRedeemFlowInitiator redeemFlow = new TokenRedeemFlowInitiator(nodeB.getInfo().getLegalIdentities().get(0), 100);
       CordaFuture<SignedTransaction> future2 = nodeA.startFlow(redeemFlow);
       network.runNetwork();
       SignedTransaction signedTransaction2 = future2.get();
   }

   
}