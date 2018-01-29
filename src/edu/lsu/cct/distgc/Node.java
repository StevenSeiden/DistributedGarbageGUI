package edu.lsu.cct.distgc;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Node {

    static Map<Integer, Node> nodeMap = new HashMap<>();

    LocalState lstate = new LocalState();

    static int id_seq = 1;
    final int id = id_seq++;

    int weight = 1, max_weight, strong_count, weak_count;
    CollectionData cd;
    List<Integer> edges = new ArrayList<>();

    // Keep track of edges deleted by the SWP
    // method so Mark & Sweep can follow them
    List<Integer> del_edges = new ArrayList<>();
    // Used by the Mark & Sweep collector for
    // verification purposes
    public boolean marked;

    public Node(Integer... forceId) {
        if (forceId.length == 0) {
            nodeMap.put(id, this);
        } else {
            assert forceId.length == 1;
            assert nodeMap.containsKey(forceId[0]) == false : "Node Id already exists!";
            nodeMap.put(forceId[0], this);
        }
    }

    void preCheck(int sender, boolean isReturn) {
        assert cd == null || cd.state != CollectorState.dead_state : this;
        lstate = new LocalState();
        lstate.old_weight = weight;
        if (cd != null) {
            lstate.original_parent = cd.parent;
        }
    }

    void postCheck(int sender, boolean action) {
        int parent = -1;
        if (cd != null) {
            parent = cd.parent;
        }
        if (parent < 0) {
            parent = lstate.sent_to_parent;
        }
        // If this assertion triggers, it means
        // that return_to_parent() should have
        // been called.
        if (ready() && cd != null && cd.parent > 0) {
            assert false;
        }
        if (action) {
            if (cd != null && lstate.original_parent != cd.parent && cd.parent == sender)
				; // the parent was set by this message
            else if (lstate.returned_to_parent)
				; else // If the message was one of: Phantomize, Recover, Build and
            // the sender did not become the parent, a return should be
            // sent to the sender.
            {
                assert lstate.returned_to_sender;
            }
        }
        lstate = null;
    }

    /**
     * Used by checkCounts() for bookkeeping.
     *
     * @author sbrandt
     *
     */
    static class Counts {

        int strong, weak;
        boolean marked;

        public String toString() {
            return "Counts(s=" + strong + "/w=" + weak + "/m=" + marked + ")";
        }
    }

    // \Procedure{OnEdgeCreation}{sender_weight,is_phantom_edge,sender_cid}
    public void incr(int sender_weight, boolean is_phantom_edge, CID sender_cid) {
        if (is_phantom_edge) {
            if (cd == null) {
                cd = new CollectionData(sender_cid);
            }
            cd.phantom_count++;
            assert cd.rcc >= 0;
            assert cd.phantom_count >= cd.rcc;
        } else if (sender_weight < weight) {
            strong_count++;
        } else {
            weak_count++;
        }
        if (sender_weight > max_weight) {
            max_weight = sender_weight;
        }
    }

    public boolean has_edges() {
        for (Integer edge : edges) {
            if (edge != null) {
                return true;
            }
        }
        return false;
    }

    public int createEdge(int rid) {
        int edgeNum = -1;
        for (int e = 0; e < edges.size(); e++) {
            // Find a free slot
            if (edges.get(e) == null) {
                edges.set(e, rid);
                edgeNum = e;
            }
        }
        if (edgeNum < 0) {
            edgeNum = edges.size();
            edges.add(rid);
        }
        Message m = null;
        if (cd != null) {
            m = new IncrMessage(id, rid, weight, phantom_flag(), cd.getCid());
        } else {
            m = new IncrMessage(id, rid, weight, false, null);
        }
        m.runMe();
        return edgeNum;
    }

    boolean has_no_outgoing_edges() {
        return out() == 0;
    }

    // \Procedure{OnEdgeDeletion}{sender,sender_weight,is_phantom_edge,sender_cid}
    public void decr(int sender, int sender_weight, boolean phantom_flag, CID sender_cid) {
        if (phantom_flag) {
            cd.phantom_count--;
            if (sender_cid.equals(cd.getCid())) {
                cd.rcc--;
            }
            assert cd.rcc >= 0;
            assert cd.phantom_count >= cd.rcc;
            assert cd.phantom_count >= 0;
        } else if (sender_weight < weight) {
            strong_count--;
            assert strong_count >= 0;
        } else {
            weak_count--;
            assert weak_count >= 0;
        }
        if (cd == null) {
            if (strong_count > 0) {
                return;
            } else if (weak_count > 0) {
                if (has_no_outgoing_edges()) {
                    toggle();
                } else {
                    cd = new CollectionData(id);
                    toggle();
                    PhantomizeAll();
                }
            } else {
                delete_outgoing_edges();
                cd = new CollectionData(id);
                cd.state = CollectorState.dead_state;
            }
        } else {
            return_to_parent_force();
            cd.setCid(new CID(cd.getCid().majorId + 1, id, 0));
            cd.parent = 0;
            if (cd.wait_count == 0) {
                action(sender);
            }
        }
    }

    // \Procedure{Toggle}{}
    private void toggle() {
        Here.log("toggle=" + weight);
        if (weak_count > 0 && strong_count == 0) {
            Here.log("toggle");
            strong_count = weak_count;
            weak_count = 0;
            weight = max_weight + 1;
        }
    }

    // \Procedure{Phantomize}{sender,sender_cid}
    public void phantomize(int sender, int w, CID sender_cid) {
        if (w < weight) {
            strong_count--;
            assert strong_count >= 0 : "Negative strong count";
        } else {
            weak_count--;
            assert weak_count >= 0 : "Negative weak count";
        }
        boolean do_action = cidCheck(sender, sender_cid);
        if (lstate.parent_was_set) {
            cd.recv = CollectorState.phantom_state;
        }
        cd.phantom_count++;
        if (do_action) {
            if (!is_initiator()) {
                action(sender);
            }
        }
        return_to_sender(sender);
    }

    public boolean removeEdge(int rid) {
        for (int i = 0; i < edges.size(); i++) {
            Integer edge = edges.get(i);
            if (edge == null || edge != rid) {
                continue;
            } else {
                Message m = new DecrMessage(id, edge, weight, false, null);
                m.queueMe();
                edges.set(i, null);
                return true;
            }
        }
        return false;
    }

    void delete_outgoing_edges() {
        for (int i = 0; i < edges.size(); i++) {
            Integer edge = edges.get(i);
            if (edge == null) {
                continue;
            }
            Message m = null;
            if (cd == null) {
                m = new DecrMessage(id, edge, weight, false, null);
            } else {
                m = new DecrMessage(id, edge, weight, phantom_flag(), cd.getCid());
            }
            m.runMe();
            edges.set(i, null);
        }
    }

    // \Procedure{PhantomizeAll}{sender_weight,is_phantom_edge,sender_cid}
    private void PhantomizeAll() {
        assert strong_count == 0 || lstate.old_weight < weight;
        if (phantom_flag()) {
            ClaimAll();
            return;
        }
        assert cd.wait_count == 0;
        assert cd.state == CollectorState.healthy_state || cd.state == CollectorState.build_state;
        cd.state = CollectorState.phantom_state;
        for (Integer edge : edges) { // xx(For): each edge
            if (edge != null) { // xskip
                Message m = new PhantomizeMessage(id, edge, lstate.old_weight, cd.getCid());
                m.queueMe();
                cd.wait_count++;
            }
        }
        if (cd.wait_count == 0) {
            return_to_parent();
        }
        cd.incrRCC = false;
    }

    // \Procedure{Claim}{sender,sender_cid}
    public void claim(int sender, CID sender_cid) {
        boolean do_action = cidCheck(sender, sender_cid);
        if (lstate.parent_was_set) {
            cd.recv = CollectorState.phantom_state;
        }
        if (do_action) {
            if (!is_initiator()) {
                action(sender);
            }
        }
        return_to_sender(sender);
    }

    // \Procedure{ClaimAll}{}
    public void ClaimAll() {
        assert cd.wait_count == 0;
        assert phantom_flag() : this.toString();
        cd.state = CollectorState.phantom_state;
        for (Integer edge : edges) { // xx(For): each edge
            if (edge != null) { // xskip
                Message m = new ClaimMessage(id, edge, cd.getCid());
                m.queueMe();
                cd.wait_count++;
            }
        }
        if (cd.wait_count == 0) {
            return_to_parent();
        }
        cd.incrRCC = false;
    }

    // \Procedure{Recover}{sender_cid,sender,incrRCC,mandate}
    public void recover(CID sender_cid, int sender, boolean incrRCC) {
        // If any strong count , also return.
        // Put at the top of every operation

        boolean incr = CID.lessThan(cd.getCid(), sender_cid);
        boolean do_action = cidCheck(sender, sender_cid);
        if (lstate.parent_was_set) {
            cd.recv = CollectorState.recover_state;
        }
        if (incrRCC && CID.equals(sender_cid, cd.getCid()) && cd.phantom_count > 0) {
            cd.rcc++;
        }
        assert cd.rcc >= 0;
        assert cd.phantom_count >= cd.rcc;
        if (do_action) {
            cd.incrRCC = false;
            if (!is_initiator()) {
                action(sender);
            }
        }
        return_to_sender(sender);
    }

    // \Procedure{RecoverAll}{}
    private void RecoverAll() {
        if (cd.incrRCC) {
            ClaimAll();
            return;
        }
        assert cd.wait_count == 0;
        assert cd.getCid() != null;
        assert phantom_flag();
        boolean incrRCC = !(CID.equals(cd.recoverCid, cd.getCid()));
        cd.state = CollectorState.recover_state;
        cd.recoverCid = cd.getCid();
        for (Integer edge : edges) { // xx(For): each edge
            if (edge != null) { // xskip
                cd.wait_count++;
                Message m = new RecoverMessage(id, edge, cd.getCid(), incrRCC);
                m.queueMe();
            }
        }
        cd.incrRCC = false;
        if (ready()) {
            // cd.wait_count = 1;
            return_to_parent();
            // ret(cd.start_over);
        }
    }

    // \Procedure{Build}{sender_weight,sender_cid,sender,decrRCC,mandate}
    public void build(int sender, int sender_weight, CID sender_cid, boolean incrRCC, boolean mandate, boolean decrRCC) {

        assert cd != null;

        assert cd.phantom_count > 0;

        // Add check on strong and weak count here. If they aren't zero,
        // changing the weights could mess everything up.
        if (cd != null && phantom_flag() && strong_count == 0 && weak_count == 0) {
            Here.log("weight adjust");
            weight = sender_weight + 1;
            max_weight = sender_weight;
        }
        if (sender_weight < weight) {
            strong_count++;
        } else {
            weak_count++;
        }
        if (sender_weight > max_weight) {
            max_weight = sender_weight;
        }
        cd.phantom_count--;

        boolean do_action = cidCheck(sender, sender_cid);
        if (lstate.parent_was_set) {
            cd.recv = CollectorState.build_state;
        }
        if (lstate.in_collection_message && decrRCC) {
            cd.rcc--;
            assert cd.rcc >= 0;
        }
        // If a lower CID rebuilds one of our links and
        // we're ready to go, we should go.
        if (ready() && cd.parent >= 0) {
            action(sender);
            return_to_sender(sender);
            return;
        }
        if (do_action) {
            if (!is_initiator()) {
                action(sender);
            }
        }
        return_to_sender(sender);
    }

    // \Procedure{BuildAll}{sender}
    private void BuildAll(int sender) {
        assert cd.wait_count == 0;
        if (!phantom_flag()) {
            if (!is_initiator()) {
                return_to_parent();
            }
            return;
        }
        // boolean decrRCC = (cd.state == CollectorState.recover_state);
        boolean decrRCC = CID.equals(cd.recoverCid, cd.getCid());
        cd.recoverCid = null;
        cd.state = CollectorState.build_state;
        for (Integer edge : edges) { // xx(For): for each
            if (edge != null) {
                cd.wait_count++;
                Message m = new BuildMessage(id, edge, weight, cd.getCid(), cd.incrRCC, lstate.parent_was_set, decrRCC);
                m.queueMe();
            }
        }
        cd.incrRCC = false;
        if (ready()) {
            // cd.wait_count = 1;
            // ret(cd.start_over);
            return_to_parent();
            return_to_sender(sender);
            cd = null;
        }
    }

    // \Procedure{plague}{sender,sender_cid}
    public void plague(int sender, CID sender_cid, boolean message) {
        cd.phantom_count--;
        if (CID.equals(sender_cid, cd.getCid())) {
            cd.rcc--;
        }
        assert cd.phantom_count >= 0;
        if (strong_count == 0 && weak_count == 0 && cd.phantom_count == cd.rcc && cd.wait_count == 0) {
            InfectAll();
            return;
        }
        if (ready() && cd.parent > 0) {
            return_to_parent();
        }
    }

    // \Procedure{InfectAll}{}
    public void InfectAll() {
        assert phantom_flag();
        assert strong_count == 0 && weak_count == 0;
        for (int i = 0; i < edges.size(); i++) { // xx(For): each edge
            Integer edge = edges.get(i); // xskip
            if (edge != null) { // xskip
                Message m = new PlagueMessage(id, edge, cd.getCid());
                m.queueMe();
            } // xskip
            del_edges.add(edge); // xx: set edge to null
            edges.set(i, null); // xskip
        }
        if (strong_count == 0 && weak_count == 0 && cd.phantom_count == 0 && cd.wait_count == 0) {
            cd.state = CollectorState.dead_state;
        } else {
            cd.state = CollectorState.infected_state;
        }
    }

    // \Procedure{Return}{start_over_cid}
    public void ret(CID start_over_cid) {
        assert cd != null : "Return message sent to healthy node";
        cd.wait_count--;
        if (start_over_cid != null) {
            if (cd.start_over == null || CID.lessThan(cd.start_over, start_over_cid)) {
                cd.start_over = start_over_cid;
            }
            if (is_initiator() && CID.equals(start_over_cid, cd.getCid())) {
                CID mycid = cd.getCid();
                cd.setCid(new CID(mycid.majorId, mycid.objId, mycid.minorId + 1));
            }
        }
        assert cd.wait_count >= 0 : this;
        if (cd.wait_count == 0) {
            if (start_over_cid != null && is_initiator()) {
                if (!CID.equals(start_over_cid, cd.getCid())) {
                    start_over_cid = null;
                }
                // Doesn't make sense, initiator has no parent
                // return_to_parent_force(true);
                CID c = cd.getCid();
                cd.setCid(new CID(c.majorId, c.objId, c.minorId + 1));
                if (cd.phantom_count == 0 && strong_count > 0 && ready()) {
                    if (phantom_flag()) {
                        BuildAll(-1);
                    } else {
                        return_to_sender(-1);
                        cd = null;
                    }
                } else if (phantom_flag()) {
                    RecoverAll();
                } else if (strong_count > 0) {
                    BuildAll(-1);
                } else {
                    toggle();
                    PhantomizeAll();
                }
            } else {
                action(-1);
            }
        } else {
            CID mycid = cd.getCid();
            if (CID.equals(mycid, start_over_cid)) {
                if (is_initiator()) {
                    cd.setCid(new CID(mycid.majorId, mycid.objId, mycid.minorId + 1));
                    cd.parent = 0;
                    if (cd.wait_count == 0) {
                        action(-1);
                    }
                }
            }
        }
    }

    void return_to_parent_force() {
        return_to_parent_base(cd.start_over, true);
    }

    void return_to_parent_force(CID start_over) {
        return_to_parent_base(start_over, true);
    }

    void return_to_parent(CID start_over) {
        return_to_parent_base(start_over, false);
    }

    // rocedure{Return_to_parent}{start_over_cid,force}
    private void return_to_parent_base(CID start_over_cid, boolean force) {
        if (!force && !ready()) {
            return;
        }
        if (cd.parent <= 0) {
            return;
        }
        Message msg = new RetMessage(id, cd.parent, start_over_cid);
        cd.start_over = null;
        msg.queueMe();
        lstate.sent_to_parent = cd.parent;
        cd.parent = -1;
        cd.recv = CollectorState.healthy_state;
        lstate.returned_to_parent = true;
    }

    void return_to_parent() {
        return_to_parent_base(cd.start_over, false);
    }

    // rocedure{Return_to_sender}{start_over_cid}
    private void return_to_sender(int sender) {
        if (cd == null) {
            ; // xx: pass
        } else if (cd != null && lstate.original_parent != cd.parent && cd.parent == sender && sender != -1) {
            // xx: pass
        } else if (lstate.returned_to_parent && cd != null && cd.parent == sender) {
            // xx: pass
        } else if (sender == -1) {
            // xx: pass
        } else if (sender != lstate.sent_to_parent) {
            Message msg = new RetMessage(id, sender, null);
            msg.queueMe();
            lstate.returned_to_sender = true;
        }
    }

    private boolean is_initiator() {
        return cd != null && cd.parent == 0;
    }

    public int out() {
        int count = 0;
        for (Integer edge : edges) {
            if (edge != null) {
                count++;
            }
        }
        return count;
    }

    public String toString() {
        return String.format("id=%d sender_weight=%d/%d out=%d sc=%d wc=%d", id, weight, max_weight, out(), strong_count,
                weak_count) + (cd == null ? ":" : cd.toString());
    }

    // \Procedure{cidCheck}{}
    private boolean cidCheck(int sender, CID sender_cid) {

        // Put at the top of every operation
        if (cd == null) {
            if (sender_cid == null) {
                sender_cid = new CID(0, this.id, 0);
            }
            cd = new CollectionData(sender_cid);
            cd.parent = sender;
            lstate.parent_was_set = true;
        } else if (sender == 0) {
            // xx: pass
            assert sender_cid == null;
        } else if (CID.equals(sender_cid, cd.getCid())) {
            lstate.in_collection_message = true;
            if (cd.wait_count > 0) {
                return false;
            }
            if (cd.parent < 0) {
                cd.parent = sender;
                lstate.parent_was_set = true;
            }
        } else if (CID.lessThan(sender_cid, cd.getCid())) {
            // plague message, no return needed
            return false;
        } else if (cd.wait_count > 0) {
            if (!is_initiator()) {
                return_to_parent_force(cd.getCid());
            }
            cd.parent = sender;
            lstate.parent_was_set = true;
            cd.setCid(sender_cid);
            cd.start_over = null;
            // If we updated the sender_cid, then previous recovers
            // are no longer good enough. We'll have to recover
            // again.
            if (cd.state == CollectorState.recover_state) {
                cd.state = CollectorState.phantom_state;
            }
            return false;
        } else if (CID.lessThan(cd.getCid(), sender_cid)) {
            assert sender != 0;
            if (cd.parent > 0) {
                return_to_parent_force(cd.getCid());
            } else {
                cd.start_over = null;
            }
            cd.parent = sender;
            lstate.parent_was_set = true;
            cd.setCid(sender_cid);
        }
        return true;
    }

    // \Procedure{Ready}{sender_weight,is_phantom_edge,sender_cid}
    public boolean ready() {
        if (cd == null) {
            return true;
        }
        if (cd.wait_count > 0) {
            return false;
        }
        if (cd.rcc < cd.phantom_count) {
            if (is_initiator()) {
                if (cd.state == CollectorState.recover_state || cd.state == CollectorState.build_state) {
                    return false;
                }
            } else if (cd.recv == CollectorState.recover_state || cd.recv == CollectorState.build_state) {
                return false;
            }
        }
        assert cd.rcc <= cd.phantom_count : this;
        return true;
    }

    // \Procedure{action}{sender}
    public void action(int sender) {
        if (cd == null) {
            return;
        }
        toggle();
        assert cd.wait_count == 0;
        if (lstate.old_weight < weight) {
            PhantomizeAll();
        } else if (is_initiator()) {
            if (ready()) {
                if (cd.state == CollectorState.healthy_state) {
                    if (!phantom_flag() && lstate.old_weight < weight || strong_count == 0) {
                        PhantomizeAll();
                    }
                } else if (cd.state == CollectorState.phantom_state) {
                    if (strong_count > 0) {
                        BuildAll(sender);
                    } else if (has_no_outgoing_edges() && weak_count == 0 && cd.phantom_count == 0) {
                        cd.state = CollectorState.dead_state;
                    } else {
                        assert weak_count == 0;
                        RecoverAll();
                    }
                } else if (cd.state == CollectorState.recover_state) {
                    if (strong_count == 0) {
                        InfectAll();
                    } else {
                        BuildAll(sender);
                    }
                } else if (cd.state == CollectorState.infected_state) {
                    if (cd.phantom_count == 0 && strong_count == 0 && weak_count == 0) {
                        cd.state = CollectorState.dead_state;
                    }
                } else if (cd.phantom_count == 0) {
                    return_to_sender(sender);
                    cd = null;
                }
            } else if (phantom_flag()) {
                if (cd.incrRCC) {
                    ClaimAll();
                }
            } else if (strong_count == 0) {
                PhantomizeAll();
            } else if (!phantom_flag()) {
                if (strong_count == 0) {
                    PhantomizeAll();
                } else { // xskip
                }
            } else { // xskip
            }
        } else if (cd.recv == CollectorState.healthy_state) {
        } else if (cd.recv == CollectorState.phantom_state) {
            if (!phantom_flag() && (lstate.old_weight < weight || strong_count == 0)) {
                PhantomizeAll();
            } else if (cd.incrRCC && phantom_flag()) {
                ClaimAll();
            } else {
                return_to_parent();
            }
        } else if (cd.recv == CollectorState.recover_state) {
            if (cd.state != CollectorState.recover_state) {
                if (strong_count > 0) {
                    BuildAll(sender);
                } else if (phantom_flag()) {
                    RecoverAll();
                } else {
                    PhantomizeAll();
                }
            } else if (strong_count > 0) {
                BuildAll(sender);
            } else if (weak_count > 0) {
                PhantomizeAll();
            } else {
                return_to_parent();
            }
        } else if (cd.recv == CollectorState.build_state) {
            if (phantom_flag()) {
                BuildAll(sender);
            } else {
                return_to_parent();
                if (cd.phantom_count == 0) {
                    return_to_sender(sender);
                    cd = null;
                }
            }
        } else if (cd.recv == CollectorState.infected_state) {
            if (cd.state != CollectorState.infected_state) {
                if (phantom_flag()) {
                    InfectAll();
                }
            }
            if (ready() && strong_count == 0 && weak_count == 0 && cd.phantom_count == 0) {
                cd.state = CollectorState.dead_state;
            }
        }
    }

    // \Procedure{Phantom_Flag}{}
    private boolean phantom_flag() {
        return cd.state == CollectorState.phantom_state || cd.state == CollectorState.recover_state || cd.state == CollectorState.infected_state;
    }
}
