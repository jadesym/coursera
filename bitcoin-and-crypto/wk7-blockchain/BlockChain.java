// Block Chain should maintain only limited block nodes to satisfy the functions
// You should not have all the blocks added to the block chain in memory 
// as it would cause a memory overflow.

import java.util.*;

import static java.util.Objects.requireNonNull;

public class BlockChain {
    private class BlockPayload {
        private Block _block;
        private int _blockHeight;
        private UTXOPool _utxoPool;

        public BlockPayload(Block block, int blockHeight, UTXOPool utxoPool) {
            _block = block;
            _blockHeight = blockHeight;
            _utxoPool = utxoPool;
        }

        public Block getBlock() {
            return _block;
        }

        public int getBlockHeight() {
            return _blockHeight;
        }

        public UTXOPool getUTXOPool() {
            return _utxoPool;
        }
    }

    public static final int CUT_OFF_AGE = 10;
    private Map<ByteArrayWrapper, BlockPayload> _blockPayloadsByHash;
    private TreeMap<Integer, List<BlockPayload>> _blockPayloadsByHeight;
    private TransactionPool _transactionPool;

    /**
     * create an empty block chain with just a genesis block. Assume {@code genesisBlock} is a valid
     * block
     */
    public BlockChain(Block genesisBlock) {
        _transactionPool = new TransactionPool();
        _blockPayloadsByHash = new HashMap<>();
        _blockPayloadsByHeight = new TreeMap<>();

        BlockPayload blockPayload
                = requireNonNull(createBlockPayload(genesisBlock, 1, new UTXOPool()),
                    "Block payload should have all valid transactions, so creation should not be non-null.");

        _blockPayloadsByHash.put(
                new ByteArrayWrapper(genesisBlock.getHash()),
                blockPayload);

        List<BlockPayload> maxHeightBlockPayloads = new ArrayList<>();
        maxHeightBlockPayloads.add(blockPayload);
        _blockPayloadsByHeight.put(1, maxHeightBlockPayloads);
    }

    private BlockPayload createBlockPayload(Block block, int blockHeight, UTXOPool utxoPool) {
        TxHandler txHandler = new TxHandler(utxoPool);
        List<Transaction> blockTransactions = block.getTransactions();

        for (Transaction blockTransaction : blockTransactions) {
            if (!txHandler.isValidTx(blockTransaction)) {
                return null;
            }
        }

        Transaction[] blockTransactionsArray
                = blockTransactions.toArray(new Transaction[0]);
        txHandler.handleTxs(blockTransactionsArray);
        UTXOPool blockUTXOPool = txHandler.getUTXOPool();

        return new BlockPayload(block, blockHeight, blockUTXOPool);
    }

    private BlockPayload getMaxHeightPayload() {
        Map.Entry<Integer, List<BlockPayload>> highestBlockPayloadsEntry
                = _blockPayloadsByHeight.lastEntry();

        if (highestBlockPayloadsEntry == null) {
            throw new IllegalArgumentException(
                    "Last Entry function should not return null since map should be non-empty.");
        }

        List<BlockPayload> highestBlocks = highestBlockPayloadsEntry.getValue();

        if (highestBlocks.isEmpty()) {
            throw new IllegalArgumentException(
                    "Highest blocks list should be non-empty there should always be a block at the max height.");
        }

        return highestBlocks.get(0);
    }

    /** Get the maximum height block */
    public Block getMaxHeightBlock() {
        return getMaxHeightPayload().getBlock();
    }

    /** Get the UTXOPool for mining a new block on top of max height block */
    public UTXOPool getMaxHeightUTXOPool() {
        return getMaxHeightPayload().getUTXOPool();
    }

    /** Get the transaction pool to mine a new block */
    public TransactionPool getTransactionPool() {
        return _transactionPool;
    }

    /**
     * Add {@code block} to the block chain if it is valid. For validity, all transactions should be
     * valid and block should be at {@code height > (maxHeight - CUT_OFF_AGE)}.
     * 
     * <p>
     * For example, you can try creating a new block over the genesis block (block height 2) if the
     * block chain height is {@code <=
     * CUT_OFF_AGE + 1}. As soon as {@code height > CUT_OFF_AGE + 1}, you cannot create a new block
     * at height 2.
     * 
     * @return true if block is successfully added
     */
    public boolean addBlock(Block block) {
        byte[] previousHash = block.getPrevBlockHash();

        if (previousHash == null) {
            return false;
        }

        BlockPayload previousBlockPayload = _blockPayloadsByHash.get(new ByteArrayWrapper(previousHash));

        // Return false if there is not previous block payload aka attempting to add another genesis block
        if (previousBlockPayload == null) {
            return false;
        }

        Integer currentMaxHeight = _blockPayloadsByHeight.lastKey();

        if (currentMaxHeight == null) {
            throw new IllegalArgumentException(
                    "Current max height should be non-null since block payloads by height should be non-empty.");
        }

        int previousBlockPayloadHeight = previousBlockPayload.getBlockHeight();
        int newBlockPayloadHeight = previousBlockPayloadHeight + 1;

        // If new block payload height is below the cutoff height, reject the block
        if (newBlockPayloadHeight <= (currentMaxHeight - CUT_OFF_AGE)) {
            return false;
        }

        BlockPayload newBlockPayload = createBlockPayload(
                block,
                newBlockPayloadHeight,
                previousBlockPayload.getUTXOPool());

        // If the transactions are not valid within the block, then reject the block
        if (newBlockPayload == null) {
            return false;
        }

        // Adding the block payload to storage
        _blockPayloadsByHash.put(
                new ByteArrayWrapper(block.getHash()),
                newBlockPayload);
        List<BlockPayload> newBlockPayloadHeightBlockPayloads = _blockPayloadsByHeight
                .computeIfAbsent(newBlockPayloadHeight, payloadHeight -> new ArrayList<>());
        newBlockPayloadHeightBlockPayloads.add(newBlockPayload);
        block.getTransactions().forEach(transaction -> _transactionPool.removeTransaction(transaction.getHash()));

        // Handle case where new block increases max height; remove block payloads that are too old
        if (newBlockPayloadHeight > currentMaxHeight) {
            int unavailablePreviousHeight = newBlockPayloadHeight - CUT_OFF_AGE - 1;
            List<BlockPayload> removableBlockPayloads = _blockPayloadsByHeight.get(unavailablePreviousHeight);

            if (removableBlockPayloads != null) {
                // Remove blocks that are keyed by hash in storage
                removableBlockPayloads.stream()
                        .map(BlockPayload::getBlock)
                        .map(Block::getHash)
                        .filter(Objects::nonNull)
                        .forEach(_blockPayloadsByHash::remove);

            }

            // Remove blocks that are at a height that is no longer available
            _blockPayloadsByHeight.remove(unavailablePreviousHeight);
        }

        return true;
    }

    /** Add a transaction to the transaction pool */
    public void addTransaction(Transaction tx) {
        _transactionPool.addTransaction(tx);
    }
}