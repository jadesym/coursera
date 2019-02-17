import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/* CompliantNode refers to a node that follows the rules (not malicious)*/
public class CompliantNode implements Node {
    private final double _graphConnectivityProbability;
    private final double _mailiciousNodeProbability;
    private final double _transactionsCommunicatedProbability;
    private final int _numRounds;

    private Set<Integer> _followees = new HashSet<>();
    private Set<Transaction> _validTransactions = new HashSet<>();
    private int _roundsCompleted = 0;
    private Map<Transaction, Integer> _lastRoundTransactions;

    public CompliantNode(double p_graph, double p_malicious, double p_txDistribution, int numRounds) {
      _graphConnectivityProbability = p_graph;
      _mailiciousNodeProbability = p_malicious;
      _transactionsCommunicatedProbability = p_txDistribution;
      _numRounds = numRounds;
    }

    public void setFollowees(boolean[] followees) {
        // IMPLEMENT THIS
        for (int i = 0; i < followees.length; i++) {
          if (followees[i]) {
            _followees.add(i);
          };
        }
    }

    public void setPendingTransaction(Set<Transaction> pendingTransactions) {
      for (Transaction transaction : pendingTransactions) {
        _validTransactions.add(transaction);
      }
    }

    public Set<Transaction> sendToFollowers() {
      if (_roundsCompleted >= _numRounds) {
        Set<Transaction> consensusTransactions = new HashSet<>();
        _lastRoundTransactions
          .entrySet()
          .stream()
          .filter(entry -> entry.getValue() > _followees.size() / 2)
          .forEach(entry -> consensusTransactions.add(entry.getKey()));
        return consensusTransactions;
      }

      _roundsCompleted++;

      return _validTransactions;
    }

    public void receiveFromFollowees(Set<Candidate> candidates) {
      _lastRoundTransactions = new HashMap<>();
      for (Candidate candidate : candidates) {
        Transaction candidateTransaction = candidate.tx;
        Integer candidateSender = candidate.sender;

        _validTransactions.add(candidateTransaction);

        _lastRoundTransactions.put(candidateTransaction, _lastRoundTransactions.getOrDefault(candidateTransaction, 0) + 1);
      }
    }
}
