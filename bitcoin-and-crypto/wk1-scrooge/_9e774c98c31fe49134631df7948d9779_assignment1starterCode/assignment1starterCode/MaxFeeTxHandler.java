import java.security.PublicKey;
import java.util.*;

public class MaxFeeTxHandler {
    private UTXOPool _utxoPool;

    /**
     * Creates a public ledger whose current UTXOPool (collection of unspent transaction outputs) is
     * {@code utxoPool}. This should make a copy of utxoPool by using the UTXOPool(UTXOPool uPool)
     * constructor.
     */
    public MaxFeeTxHandler(UTXOPool utxoPool) {
        if (utxoPool == null) {
            _utxoPool = new UTXOPool();
        } else {
            _utxoPool = new UTXOPool(utxoPool);
        }
    }

    /**
     * @return true if:
     * (1) all outputs claimed by {@code tx} are in the current UTXO pool,
     * (2) the signatures on each input of {@code tx} are valid,
     * (3) no UTXO is claimed multiple times by {@code tx},
     * (4) all of {@code tx}s output values are non-negative, and
     * (5) the sum of {@code tx}s input values is greater than or equal to the sum of its output
     *     values; and false otherwise.
     */
    public static boolean isValidTx(UTXOPool utxoPool, Transaction tx) {
        double inputSum = 0.0;
        double outputSum = 0.0;

        for (int outputIndex = 0; outputIndex < tx.numOutputs(); outputIndex++) {
            Transaction.Output transactionOutput = tx.getOutput(outputIndex);

            double outputValue = transactionOutput.value;

            // (4) Check all transaction output values are non-negative
            if (outputValue < 0) {
                return false;
            }

            outputSum += outputValue;
        }

        Set<UTXO> claimedUTXOs = new HashSet<>();
        for (int inputIndex = 0; inputIndex < tx.numInputs(); inputIndex++) {
            Transaction.Input transactionInput = tx.getInput(inputIndex);

            UTXO claimedUTXO = new UTXO(transactionInput.prevTxHash, transactionInput.outputIndex);

            // (3) No UTXOs are claimed more than once
            if (claimedUTXOs.contains(claimedUTXO)) {
                return false;
            } else {
                claimedUTXOs.add(claimedUTXO);
            }

            Transaction.Output inputUTXOTransactionOutput = utxoPool.getTxOutput(claimedUTXO);
            // (1) Check all outputs claimed by transaction are in the UTXO pool
            if (inputUTXOTransactionOutput == null) {
                return false;
            } else {
                inputSum += inputUTXOTransactionOutput.value;
            }

            byte[] inputTransactionData = tx.getRawDataToSign(inputIndex);
            PublicKey publicKey = inputUTXOTransactionOutput.address;
            byte[] inputSignature = transactionInput.signature;

            // (3) All input signatures are valid
            if (!Crypto.verifySignature(publicKey, inputTransactionData, inputSignature)) {
                return false;
            }
        }

        // (5) Check sum of outputs is less than sum of inputs
        return inputSum >= outputSum;
    }

    /**
     * Handles each epoch by receiving an unordered array of proposed transactions, checking each
     * transaction for correctness, returning a mutually valid array of accepted transactions, and
     * updating the current UTXO pool as appropriate.
     */
    public Transaction[] handleTxs(Transaction[] possibleTxs) {
        // IMPLEMENT THIS
        if (possibleTxs.length == 0) {
            return new Transaction[0];
        }

        List<List<Transaction>> transactionPermutations = getPermutations(Arrays.asList(possibleTxs));

        double maxFee = 0.0;
        List<Transaction> maxTransactionPermutation = transactionPermutations.get(0);
        UTXOPool maxUTXOPool = _utxoPool;

        for (List<Transaction> transactionPermutation : transactionPermutations) {
            TransactionsResult currentPermutationResult = new TransactionsResult(_utxoPool);
            currentPermutationResult.processTransactions(transactionPermutation);

            if (currentPermutationResult.totalFee > maxFee) {
                maxFee = currentPermutationResult.totalFee;
                maxTransactionPermutation = currentPermutationResult.validTransactions;
                maxUTXOPool = currentPermutationResult.utxoPool;
            }
        }

        this._utxoPool = maxUTXOPool;
        return maxTransactionPermutation.toArray(new Transaction[0]);
    }

    private class TransactionsResult {
        private List<Transaction> validTransactions;
        private double totalFee;
        private UTXOPool utxoPool;
        private TransactionsResult(final UTXOPool utxoPool) {
            this.validTransactions = new ArrayList<>();
            this.totalFee = 0;
            this.utxoPool = new UTXOPool(utxoPool);
        }
        private void processTransactions(List<Transaction> transactionsToProcess) {
            transactionsToProcess.stream()
                    .filter(transaction -> MaxFeeTxHandler.isValidTx(this.utxoPool, transaction))
                    .forEach(transaction -> {
                        validTransactions.add(transaction);
                        updatedUTXOPool(transaction, this.utxoPool);
                        totalFee += getFee(transaction);
                    });
        }
    }

    private List<List<Transaction>> getPermutations(List<Transaction> possibleTxs) {
        List<List<Transaction>> permutations = new ArrayList<>();
        List<Transaction> currentTransactions = Collections.emptyList();

        addPermutations(currentTransactions, possibleTxs, permutations);

        return permutations;
    }

    private void addPermutations(
            final List<Transaction> currentTransactions,
            final List<Transaction> leftoverTransactions,
            List<List<Transaction>> resultingArray) {
        if (leftoverTransactions.isEmpty()) {
            resultingArray.add(currentTransactions);
            return;
        }

        for (int leftoverIndex = 0; leftoverIndex < leftoverTransactions.size(); leftoverIndex++) {
            Transaction currentLeftoverTransaction = leftoverTransactions.get(leftoverIndex);

            List<Transaction> newLeftoverTransactions = new ArrayList<>();
            if (leftoverIndex > 0) {
                newLeftoverTransactions.addAll(leftoverTransactions.subList(0, leftoverIndex));
            }

            if (leftoverIndex < leftoverTransactions.size() - 1) {
                newLeftoverTransactions
                        .addAll(leftoverTransactions.subList(leftoverIndex + 1, leftoverTransactions.size()));
            }

            List<Transaction> newCurrentTransactions = new ArrayList<>(currentTransactions);
            newCurrentTransactions.add(currentLeftoverTransaction);

            addPermutations(newCurrentTransactions, newLeftoverTransactions, resultingArray);
        }
    }

    private void updatedUTXOPool(Transaction transactionToApply, UTXOPool utxoPool) {
        for (int inputIndex = 0; inputIndex < transactionToApply.numInputs(); inputIndex++) {
            Transaction.Input currentTransactionInput = transactionToApply.getInput(inputIndex);
            UTXO inputUTXO = new UTXO(
                    currentTransactionInput.prevTxHash,
                    currentTransactionInput.outputIndex);
            utxoPool.removeUTXO(inputUTXO);
        }

        for (int outputIndex = 0; outputIndex < transactionToApply.numOutputs(); outputIndex++) {
            UTXO newUTXO = new UTXO(transactionToApply.getHash(), outputIndex);

            utxoPool.addUTXO(newUTXO, transactionToApply.getOutput(outputIndex));
        }
    }

    private double getFee(Transaction tx) {
        double inputSum = 0.0;
        double outputSum = 0.0;

        for (Transaction.Output output : tx.getOutputs()) {
            outputSum += output.value;
        }

        for (Transaction.Input input : tx.getInputs()) {
            UTXO claimedUTXO = new UTXO(input.prevTxHash, input.outputIndex);
            Transaction.Output inputUTXOTransactionOutput = _utxoPool.getTxOutput(claimedUTXO);

            inputSum += inputUTXOTransactionOutput.value;
        }

        return inputSum - outputSum;
    }
}
