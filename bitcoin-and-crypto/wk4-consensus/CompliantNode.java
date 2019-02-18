import java.util.*;
import java.util.stream.Collectors;

/* CompliantNode refers to a node that follows the rules (not malicious)*/
public class CompliantNode implements Node {
    private final int _numRounds;

    private Set<Integer> _followees = new HashSet<>();
    private Set<Transaction> _originalTransactions = new HashSet<>();
    private int _roundsCompleted = 0;
    private Map<Integer, Set<Transaction>> _lastRoundTransactions;
//    private Map<Integer, Boolean> _transactionsHaveChanged;

    public CompliantNode(double p_graph, double p_malicious, double p_txDistribution, int numRounds) {
      _numRounds = numRounds;
    }

    public void setFollowees(boolean[] followees) {
//        _transactionsHaveChanged = new HashMap<>();

        for (int i = 0; i < followees.length; i++) {
          if (followees[i]) {
            _followees.add(i);
//            _transactionsHaveChanged.put(i, false);
          }
        }
    }

    public void setPendingTransaction(Set<Transaction> pendingTransactions) {
        _originalTransactions.addAll(pendingTransactions);
    }

    public Set<Transaction> sendToFollowers() {
      if (_roundsCompleted >= _numRounds) {
//        Set<Integer> validNodes = _lastRoundTransactions == null ? new HashSet<>(_followees)
//                : _followees
//                .stream()
//                .filter(nodeId -> _lastRoundTransactions.containsKey(nodeId))
//                .collect(Collectors.toSet());

        Map<Transaction, Integer> transactionCounts = new HashMap<>();

        if (_lastRoundTransactions != null) {
            _lastRoundTransactions.forEach((nodeId, transactionSet) -> {
                for (Transaction transaction : transactionSet) {
                    transactionCounts.put(transaction, transactionCounts.getOrDefault(transaction, 0) + 1);
                }
            });
        }

        Set<Transaction> consensusTransactions = new HashSet<>();
        transactionCounts
          .entrySet()
          .stream()
          .filter(entry -> entry.getValue() > _followees.size() / 2)
          .forEach(entry -> consensusTransactions.add(entry.getKey()));
        return consensusTransactions;
      }

      _roundsCompleted++;

      Set<Transaction> finalTransactions = new HashSet<>(_originalTransactions);

      if (_lastRoundTransactions != null) {
          _lastRoundTransactions.values()
                  .stream()
                  .flatMap(Collection::stream)
                  .forEach(finalTransactions::add);
      }

      return finalTransactions;
    }

    public void receiveFromFollowees(Set<Candidate> candidates) {
      Map<Integer, Set<Transaction>> newRoundTransactions = getRoundTransactions(candidates);

      if (_lastRoundTransactions == null) {
        _lastRoundTransactions = newRoundTransactions;
        return;
      }

      for (Integer nodeId : _lastRoundTransactions.keySet()) {
          if (!newRoundTransactions.containsKey(nodeId)) {
              _followees.remove(nodeId);
              continue;
          }

          Set<Transaction> lastRoundNodeTransactions = _lastRoundTransactions.get(nodeId);
          Set<Transaction> newRoundNodeTransactions = newRoundTransactions.get(nodeId);

          lastRoundNodeTransactions.stream()
                  .filter(lastRoundNodeTransaction -> !newRoundNodeTransactions.contains(lastRoundNodeTransaction))
                  .forEach(lastRoundNodeTransaction -> _followees.remove(nodeId));

//          newRoundNodeTransactions.stream()
//                  .filter(newRoundNodeTransaction -> !lastRoundNodeTransactions.contains(newRoundNodeTransaction))
//                  .forEach(newRoundTransaction -> _transactionsHaveChanged.put(nodeId, true));
      }

      _lastRoundTransactions = newRoundTransactions;
    }

    private Map<Integer, Set<Transaction>> getRoundTransactions(Set<Candidate> candidates) {
      Map<Integer, Set<Transaction>> newRoundTransactions = new HashMap<>();

      for (Candidate candidate : candidates) {
        Transaction candidateTransaction = candidate.tx;
        Integer candidateSender = candidate.sender;

        if (!newRoundTransactions.containsKey(candidateSender)) {
          newRoundTransactions.put(candidateSender, new HashSet<>());
        }

        newRoundTransactions.get(candidateSender)
          .add(candidateTransaction);
      }

      return newRoundTransactions;
    }
}
