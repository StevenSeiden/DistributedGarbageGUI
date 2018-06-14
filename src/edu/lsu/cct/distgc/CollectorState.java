package edu.lsu.cct.distgc;

/**
 * The "Collector State" is defined by the last message sent to all outgoing
 * links. Healthy means no message has been sent.
 */
public enum CollectorState {
    healthy_state("good"), phantom_state("phant"), recover_state("recov"),
        build_state("build"), infected_state("infect"), dead_state("dead");
    final String nm;
    CollectorState(String nm) {
        this.nm = nm;
    }
    public String toString() { return nm; }
}
