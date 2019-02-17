javac Simulation.java

for graph_edge_probability in ".1" ".2" ".3"
do
    for malicious_node_probability in ".15" ".30" ".45"
    do
        for initial_transactions_communicated_probability in ".01" ".05" ".10"
        do
            for num_rounds in "10" "20"
            do
                java Simulation $graph_edge_probability $malicious_node_probability $initial_transactions_communicated_probability $num_rounds
            done
        done
    done
done