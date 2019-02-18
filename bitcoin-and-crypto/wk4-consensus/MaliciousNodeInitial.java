import java.util.ArrayList;
import java.util.Set;
import java.util.HashSet;

public class MaliciousNode implements Node {

    private Set<Transaction> _originalTransactions;

    public MaliciousNode(double p_graph, double p_malicious, double p_txDistribution, int numRounds) {
    }

    public void setFollowees(boolean[] followees) {
        return;
    }

    public void setPendingTransaction(Set<Transaction> pendingTransactions) {
        _originalTransactions = pendingTransactions;
    }

    public Set<Transaction> sendToFollowers() {
        return new HashSet<>(_originalTransactions);
    }

    public void receiveFromFollowees(Set<Candidate> candidates) {
        return;
    }
}
