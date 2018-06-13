package edu.lsu.cct.distgc;

/**
 * The "Collector State" is defined by the last message sent to all outgoing
 * links. Healthy means no message has been sent.
 */
public enum CollectorState {
    healthy_state("healthy"), phantom_state("phantom"), recover_state("recover"),
        build_state("build"), infected_state("infected"), dead_state("dead");
    final String nm;
    CollectorState(String nm) {
        this.nm = nm;
    }
    public String toString() { return nm; }
}
