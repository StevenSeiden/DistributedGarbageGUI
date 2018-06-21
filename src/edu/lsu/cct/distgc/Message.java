package edu.lsu.cct.distgc;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.SwingUtilities;

public abstract class Message {

    private static int msg_seq = 1;
    public final static boolean CONGEST_mode =
        Props.get("CONGEST_mode").equals("yes");
    final int msg_id = msg_seq++;
    final int sender, recipient;
    private boolean done;
    boolean action = true;

    Message(int s, int r) {
        sender = s;
        recipient = r;
    }

    public boolean isDone() {
        return done;
    }

    static int roundCount = 0;
    static int messageCount = 0;

    public void run() {
        //assert !done : this;
        if(done)
            return;
        done = true;
        Node r = Node.nodeMap.get(recipient);
        if (Here.VERBOSE) {
            System.out.printf("Before %s: %s%n", sendStr(), r);
        }
        r.preCheck(sender, action);
        runTask(r);
        if (Here.VERBOSE) {
            System.out.printf("After %s: %s%n", sendStr(), r);
        }
        r.postCheck(sender, action, this instanceof IncrMessage);
        messageCount++;
    }

    public abstract void runTask(Node n);

    public String getName() {
        String nm = getClass().getName();
        int b = nm.lastIndexOf('.') + 1;
        int e = nm.indexOf("Message");
        return nm.substring(b, e);
    }

    final static Random RAND = new Random();

    static {
        int s = RAND.nextInt(10000);
        String seedStr = Props.get("seed");
        if (seedStr != null) {
            s = Integer.parseInt(seedStr);
        }
        System.out.printf("seed=%d%n", s);
        RAND.setSeed(s);
    }

    /**
     * The global message table. Once messages are enqueued, they run in a
     * completely random order.
     */
    static MessageQueue msgs = new MessagesOvertake(Message.CONGEST_mode);
    // static MessageQueue msgs = new MessageDoNotOvertake();

    public String sendStr() {
        return String.format("%s(mid=%d,%d->%d)", getName(), msg_id, sender, recipient);
    }

    public static void send(Message m) {
        StackTraceElement[] elems = new Throwable().getStackTrace();
        StackTraceElement elem = null;
        if(elems.length > 2) {
            elem = elems[2];
        } else {
            elem = elems[elems.length-1];
        }
        if (Here.VERBOSE) {
            System.out.printf(" -->%s / (%s:%d)%n", m.sendStr(), elem.getFileName(), elem.getLineNumber());
        }
        msgs.sendMessage(m);
    }

    static synchronized void waitForNoGui() {
        while(guiMessage != null) {
            try {
                Message.class.wait(100);
            } catch(InterruptedException ie) {}
        }
    }

    static synchronized void setGuiMessage(Message m) {
        guiMessage = m;
        Message.class.notifyAll();
    }

    static synchronized Message waitForGui() {
        System.out.println("Call waitForGui()");
        while(guiMessage == null) {
            List<Message> ma = new ArrayList<>();
            for(Message m : msgs) {
                ma.add(m);
            }
            if(ma.size()==1) {
                guiMessage = ma.get(0);
                break;
            }
            try {
                Message.class.wait(100);
            } catch(InterruptedException ie) {}
        }
        Message m = guiMessage;
        guiMessage = null;
        Message.class.notifyAll();
        return m;
    }

    /**
     * Run a single message
     *
     * @return
     */
    public static boolean runOne() {
        if (CONGEST_mode) {
            return runOneRound();
        }
        System.out.println(":: RUN ONE");
        /*
        if(guiMessage == null) {
            System.out.println("No message provided by GUI");
            return false;
        }
        */
        Message m = waitForGui();
        /*
        while(guiMessage == null) {
            System.out.println("Waiting for a message...");
            waitForGui();
            int nodeId = guiMessage.recipient;
            int msgId = guiMessage.msg_id;
            // yyy
    	    m = msgs.getMessage(nodeId, msgId);
            guiMessage = null;
            System.out.println(":: GOT GUI "+m);
        }
        */
        if (m == null) {
            System.out.println(":: No message");
            return false;
        }
        System.out.println(":: Run Message");
        fireBefore(m);
        //waitFor();
        m.run();
        fireAfter(m);
        waitFor();
        return true;
    }
    
    private static List<MessageListener> watchers = new ArrayList<>();
    public static synchronized void addListener(MessageListener ml) {
        watchers.add(ml);
    }
    static int stepBefore = 0, stepAfter = 0;
    private static synchronized void fireBefore(Message m) {
        for(MessageListener ml : watchers) {
            SwingUtilities.invokeLater(new Runnable() {
                @Override public void run() {
                    ml.before(m,stepBefore);
                }
            });
        }
        stepBefore++;
    }
    private static synchronized void fireAfter(Message m) {
        for(MessageListener ml : watchers) {
            SwingUtilities.invokeLater(new Runnable() {
                @Override public void run() {
                    ml.after(m,stepAfter);
                }
            });
        }
        stepAfter++;
    }
    private static synchronized void waitFor() {
        try {
            while(true) {
                boolean ready = true;
                for(MessageListener ml : watchers) {
                    if(!ml.ready()) {
                        ready = false;
                    }
                }
                if(ready)
                    return;
                Message.class.wait(10);
            }
        } catch (InterruptedException ex) {
        }
    }

    static int timeStep = 1;

    /**
     * Run one message randomly chosen in each node
     * @return true if a message was run
     */
    public static boolean runOneRound() {
        System.out.println("RUN ONE ROUND");
        List<Message> mails = msgs.nextRoundToRun();
        if (mails.isEmpty()) {
            return false;
        } else {
            if (Here.VERBOSE) {
                System.out.printf("Time Step=%d%n", timeStep++);
            }
            roundCount++;
            for (Message msg : mails) {
                fireBefore(msg);
                waitFor();
                msg.run();
                fireAfter(msg);
                waitFor();
            }
            return true;
        }
    }

    /**
     * Send the current message, but don't wait for it to complete.
     */
    public void queueMe() {
        assert sender == 0 || Node.nodeMap.get(sender) != null;
        assert Node.nodeMap.get(recipient) != null : "No such object " + recipient;
        if(this instanceof HasAdversary) {
            HasAdversary ha = (HasAdversary)this;
            Adversary adv = ha.adversary();
            if(adv != null) {
                if(adv.msg != null)
                    adv.msg.mwait();
                adv.msg = this;
            }
        }
        send(this);
    }

    void mwait() {
        if (CONGEST_mode) {
            while (!isDone()) {
                boolean success = runOneRound();
                assert success;
            }
        } else {
            while (!isDone()) {
                boolean success = runOne();
                //assert success;
            }
        }
    }

    static void checkCounts(Map<Integer, Node.Counts> counts, Node node) {
        List<Node> nodesToCheck = new ArrayList<>();
        List<Node> moreNodesToCheck = new ArrayList<>();
        nodesToCheck.add(node);
        while (true) {
            for (Node n : nodesToCheck) {
                checkCounts(counts, n, moreNodesToCheck);
            }
            if (moreNodesToCheck.size() == 0) {
                break;
            } else {
                nodesToCheck = moreNodesToCheck;
                moreNodesToCheck = new ArrayList<>();
            }
        }
    }

    static void checkCounts(Map<Integer, Node.Counts> counts, Node node, List<Node> nodesToCheck) {
        Node.Counts c0 = counts.get(node.id);
        if (c0.marked) {
            return;
        }
        c0.marked = true;
        for (Integer edge : node.edges) {
            if (edge != null) {
                Node target = Node.nodeMap.get(edge);
                Node.Counts c = counts.get(target.id);
                if (c == null) {
                    counts.put(target.id, c = new Node.Counts());
                }
                if (node.weight < target.weight) {
                    c.strong++;
                } else {
                    c.weak++;
                }
                System.out.println("link => " + target.id + " c=" + c);
                // checkCounts(counts,target);
                nodesToCheck.add(target);
            }
        }
    }

    /**
     * Start from the roots and check the strong and weak counts of all nodes
     * and make sure they are correct.
     */
    static void checkCounts() {
        Here.bar("Counts");
        Map<Integer, Node.Counts> counts = new HashMap<>();
        for (Root root : Root.roots) {
            Node node = root.get();
            if (node == null) {
                continue;
            }
            Node.Counts c = counts.get(node.id);
            if (c == null) {
                counts.put(node.id, c = new Node.Counts());
            }
            c.strong++;
            // c.marked=true;
            System.out.println("id=" + node.id + " " + c);
            Message.checkCounts(counts, node);
        }
        stateOfNodes();
        for (Node node : Node.nodeMap.values()) {
            Node.Counts c = counts.get(node.id);
            if (node.cd != null) {
                assert node.cd.state == CollectorState.dead_state : "FAILURE: Node is neither recovered nor dead.";

                if (c == null) {
                    assert node.strong_count == 0 : "strong count on deleted node: " + node;
                    assert node.weak_count == 0 : "weak count on deleted node " + node;
                } else {
                    assert node.strong_count == c.strong : String.format("%d: %d != %d", node.id, node.strong_count,
                            c.strong);
                    assert node.weak_count == c.weak : String.format("%d: %d != %d", node.id, node.weak_count, c.weak);
                }
            } else {
                assert node.strong_count > 0 : "strong count = 0 on live node " + node;
                assert c != null : node;
                assert c.strong == node.strong_count : "Bad strong: " + c + " != " + node;
                assert c.weak == node.weak_count : "Bad weak: " + c + " != " + node;
                assert c.marked;
            }
        }
    }

    static void stateOfNodes() {
        Here.bar("State of nodes");
        for (Node node : Node.nodeMap.values()) {
            if (node.cd == null || node.cd.state != CollectorState.dead_state) {
                System.out.println(node);
            }
        }
    }

    static int edgeCount() {
        int edges = 0;
        for (Node node : Node.nodeMap.values()) {
            for (Integer edge : node.edges) {
                if (edge != null) {
                    edges++;
                }
            }
            for (Integer edge : node.del_edges) {
                if (edge != null) {
                    edges++;
                }
            }
        }
        return edges;
    }

    /**
     * Execute all messages.
     */
    public static int runAll() {
        int rcount = roundCount;
        int mcount = messageCount;
        int edges = edgeCount();

        while (runOne())
			;
        Here.bar("Collection Summary");
        System.out.printf("Number of edges: %d%n", edges);
        System.out.printf("Number of rounds to converge: %d/%d%n", roundCount - rcount, roundCount);
        System.out.printf("Number of messages to converge: %d/%d%n", messageCount - mcount, messageCount);
        Message.checkCounts();
        Message.markAndSweep();
        return rcount;
    }

    private static volatile Message guiMessage;

    /**
     * Execute msg specified
     */
    public static void runMsg(int nodeId, int msgId) {
    	Message msg = msgs.getMessage(nodeId, msgId);
        if(msg == null)
            throw new NoSuchMessage();
        System.out.println("----->runMsg()");
        fireBefore(msg);
        waitFor();
        msg.run();
        fireAfter(msg);
        waitFor();
    }

    /**
     * Performs post collection checks
     */
    public static void checkStat(){
    	Message.checkCounts();
    	Message.markAndSweep();
    }

    private static void markAndSweep() {
        for (Node node : Node.nodeMap.values()) {
            node.marked = false;
        }
        for (Root root : Root.roots) {
            if (root.get() != null) {
                markAndSweep(root.get());
            }
        }
        for (Node node : Node.nodeMap.values()) {
            if (node.marked) {
                assert node.cd == null && node.strong_count > 0 : "" + node;
            } else {
                assert node.cd.state == CollectorState.dead_state && node.cd.wait_count == 0;
            }
        }
    }

    private static void markAndSweep(Node startNode) {
        List<Node> nodesToSweep = new ArrayList<>();
        List<Node> moreNodesToSweep = new ArrayList<>();
        nodesToSweep.add(startNode);
        while (true) {
            for (Node node : nodesToSweep) {
                markAndSweep(node, moreNodesToSweep);
            }
            if (moreNodesToSweep.size() == 0) {
                break;
            } else {
                nodesToSweep = moreNodesToSweep;
                moreNodesToSweep = new ArrayList<>();
            }
        }
    }

    private static void markAndSweep(Node node, List<Node> nodesToSweep) {
        if (node.marked) {
            return;
        }
        node.marked = true;
        for (Integer edge : node.edges) {
            if (edge != null) {
                Node edgeNode = node.nodeMap.get(edge);
                if (!edgeNode.marked) {
                    nodesToSweep.add(edgeNode);
                }
            }
        }
        for (Integer edge : node.del_edges) {
            if (edge != null) {
                Node edgeNode = node.nodeMap.get(edge);
                if (!edgeNode.marked) {
                    nodesToSweep.add(edgeNode);
                }
            }
        }
    }

    public static int count() {
        return msgs.size();
    }

    public String toString() {
        if(this instanceof CidMessage) {
            CidMessage c = (CidMessage)this;
            return String.format("%s %d->%d (cd=%s)",shortName(),sender,recipient,c.getCid());
        } else {
            return String.format("%s %d->%d",shortName(),sender,recipient);
        }
    }

    public boolean done() { return done; }

    public abstract String shortName();
}
