import java.security.PublicKey;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class TxHandler {
    protected final UTXOPool _utxoPool;

    /**
     * Creates a public ledger whose current UTXOPool (collection of unspent transaction outputs) is
     * {@code utxoPool}. This should make a copy of utxoPool by using the UTXOPool(UTXOPool uPool)
     * constructor.
     */
    public TxHandler(UTXOPool utxoPool) {
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
    public boolean isValidTx(Transaction tx) {
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

            Transaction.Output inputUTXOTransactionOutput = _utxoPool.getTxOutput(claimedUTXO);
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
        List<Transaction> validTransactions = new ArrayList<>(possibleTxs.length);

        for (int transactionIndex = 0; transactionIndex < possibleTxs.length; transactionIndex++) {
            Transaction currentTransaction = possibleTxs[transactionIndex];

            if (isValidTx(currentTransaction)) {
                validTransactions.add(currentTransaction);

                for (int inputIndex = 0; inputIndex < currentTransaction.numInputs(); inputIndex++) {
                    Transaction.Input currentTransactionInput = currentTransaction.getInput(inputIndex);
                    UTXO inputUTXO = new UTXO(
                            currentTransactionInput.prevTxHash,
                            currentTransactionInput.outputIndex);
                    _utxoPool.removeUTXO(inputUTXO);
                }

                for (int outputIndex = 0; outputIndex < currentTransaction.numOutputs(); outputIndex++) {
                    UTXO newUTXO = new UTXO(currentTransaction.getHash(), outputIndex);

                    _utxoPool.addUTXO(newUTXO, currentTransaction.getOutput(outputIndex));
                }
            }
        }

        return validTransactions.toArray(new Transaction[0]);
    }
}
