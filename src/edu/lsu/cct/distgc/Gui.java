package edu.lsu.cct.distgc;

//import com.sun.org.apache.xpath.internal.operations.Bool;

import java.awt.*;
import java.awt.event.*;
import javax.swing.*;
import java.awt.image.BufferedImage;
import java.util.HashMap;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.Collections;
import java.math.*;
import java.util.*;
import java.io.File;
import javax.imageio.ImageIO;
import java.awt.geom.AffineTransform;

public class Gui {
    public final static int nodeDiameter = 100;
    public static Color arrowColor;
    public static int baseOffset = 5;

    static class NodePos {
        int x, y; // the position
    }

    static <T> ArrayList<T> makeCopy(Collection<T> col) {
        while(true) {
            try {
                ArrayList<T> li = new ArrayList<>();
                if(col != null)
                    li.addAll(col);
                return li;
            } catch(ConcurrentModificationException e) {
            }
        }
    }

    static NodePos getNodePos(int nodeId) {
        int nodeAmount = Node.nodeMap.size();
        double angleSeparating = (2 * Math.PI / nodeAmount);
        NodePos np = new NodePos();
        np.x = (int) (((circleDiameter / 2) * (double) Math.cos(angleSeparating*nodeId)) + (circleDiameter/2));

        np.y = (int) (((circleDiameter / 2) * (double) Math.sin(angleSeparating*nodeId)) + (circleDiameter/2));
        np.x += nodeDiameter / 2;
        np.y += nodeDiameter / 2;
        return np;
    }

    static class MessageHolder {
        Message m;
        int step;
        int phase;
    }

    static boolean waitForMouse = true;
    synchronized static void waitForMouse() {
        while(waitForMouse) {
            try {
                Gui.class.wait();
            } catch(InterruptedException ie) {}
        }
        waitForMouse = true;
    }
    synchronized static void mouseIsClicked() {
        waitForMouse = false;
        Gui.class.notifyAll();
        //Message.waitForNoGui();
        getButtonText();
        for (int i = 0; i < buttonMessage.size() && i < buttons.length; i++) {
            Message m = buttonMessage.get(i);
            if(m == null || m.done()) {
                buttons[i].setEnabled(false);
                buttons[i].setText("Button #"+i);
            } else {
                buttons[i].setEnabled(true);
                String s = m.toString();
                int index = s.indexOf("(");
                if(index != -1) {
                    buttons[i].setText(s.substring(0, index));
                }else{
                    buttons[i].setText(s);
                }
            }
        }
    }

    static Color nodeColor[] = {Color.white, Color.gray, Color.cyan, Color.green, Color.orange, Color.pink, Color.yellow,
            (new Color(193, 135, 227)),
            (new Color(12, 143, 0)),
            (new Color(255, 116, 56)),
            (new Color(12, 158, 129)),
            (new Color(154, 60, 109)),
            (new Color(157, 144, 12)),
            (new Color(86, 127, 189)),
            (new Color(189, 151, 86)),
            (new Color(223, 165, 181)),
            (new Color(250, 255, 148)),
            (new Color(142, 31, 31)),
            (new Color(122, 122, 122)),
            (new Color(207, 207, 207)),
            (new Color(121, 169, 130))
    };


    //Making a hashmap to associate colors with CIDs
    static HashMap<String, Integer> cidColor = new HashMap<String, Integer>();

    static int currentColor = 2;
    static {
        cidColor.put("blank",0);
        cidColor.put("dead",1);
    }



    static int circleDiameter = 500;

    static void paintMe(Dimension d, Graphics g, MessageHolder mh) {
        g.setColor(Color.white);
        g.fillRect(0,0,d.width,d.height);

        AffineTransform af = new AffineTransform();
        af.translate(50,0);
        Graphics2D g2 = (Graphics2D)g;
        g2.setTransform(af);
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        if(mh.m != null)
            System.out.println("paintMe: mh.m="+mh.m);


        // The big circle
        g.setColor(Color.black);
        g.drawOval(50, 50, circleDiameter, circleDiameter);
        //The amount of nodes
        int nodeAmount = Node.nodeMap.size();

        //The angle between each node
        double angleSeparating = (2 * Math.PI / nodeAmount);
        if (mh.m != null) {
            g.setFont(new Font("default", Font.BOLD, 20));
            String m = mh.m.toString();
            int pos = m.indexOf('(');
            //Removing extraneous information from buttons
            if(pos > 0) {
                m = m.substring(0, pos);
            }
            g.drawString(m, 0, 25);
        }
        int n = 0;

        try {
            for (Node node : makeCopy(Node.nodeMap.values())) {
                n = node.id;
                NodePos np = getNodePos(node.id);
                int nodeX = np.x - nodeDiameter/2;
                int nodeY = np.y - nodeDiameter/2;

                String key = "blank";
                if (node.cd != null && node.cd.getCid() != null) {
                    key = node.cd.getCid().toString();
                }
                if(node.cd != null && node.cd.state == CollectorState.dead_state) {
                    key = "dead";
                }

                if (!cidColor.containsKey(key)) {
                    cidColor.put(key, currentColor);
                    System.out.println("COLOR: "+key+" => "+currentColor);
                    currentColor++;
                }
                g.setColor(Color.black);

                boolean rootNode = false;

                for(Root r : makeCopy(Root.roots)) {
                    Node nn = r.get();
                    if(nn != null && r.getId() == node.id){
                        rootNode = true;
                    }
                }

                //Adding marker for root node
                if(rootNode){
                    g.fillOval((nodeX-4), (nodeY-4), nodeDiameter+8, nodeDiameter+8);
                }
                //Retrieving node color and drawing node
                int color = cidColor.get(key);
                g.setColor(nodeColor[color]);
                g.fillOval(nodeX, nodeY, nodeDiameter, nodeDiameter);
                //Adding black border to node
                g.setColor(Color.black);
                g.drawOval(nodeX, nodeY, nodeDiameter, nodeDiameter);

                //Setting default label values
                int pc = 0;
                int rcc = 0;
                int wc = 0;
                //Setting values from node if applicable
                if (node.cd != null) {
                    pc = node.cd.phantom_count;
                    rcc = node.cd.rcc;
                    wc = node.cd.wait_count;
                }

                //Labeling nodes
                g.setFont(new Font("default", Font.BOLD, 16));
                g.drawString("id=" + node.id, nodeX + 27, nodeY + 30);

                g.setFont(new Font("default", Font.BOLD, 12));
                if(node.cd != null) {
                    g.drawString(node.cd.getCid().toString() + " " + node.cd.state, nodeX + 2, nodeY + 55);
                }
                g.setFont(new Font("default", Font.BOLD, 14));
                //g.drawString("Placeholder", nodeX + 10, nodeY + 32);
                g.drawString("[" + node.strong_count + "," + node.weak_count + "," + pc + "," + rcc + "]", nodeX + 15, nodeY + 75);
                g.drawString("(" + node.weight + "/" + node.max_weight + ") " + wc, nodeX + 23, nodeY + 90);

                //Adding arrows
                for (Integer out : makeCopy(node.edges)) {
                    if (out != null) {
                        Node child = Node.nodeMap.get(out);
                        //System.out.printf("There's an edge from %d to %d%n", node.id, child.id);
                        int startNode = node.id;
                        int endNode = child.id;
                        nodeArrow(angleSeparating, startNode, endNode,nodeX, nodeY, g, mh, baseOffset, Color.black);
                    }
                }

                if(node.cd != null && node.cd.parent > 0) {
                    nodeArrow(0.0, node.id, node.cd.parent, 0, 0, g, mh, baseOffset*3, Color.blue);
                }
            }
            if(mh.m != null) {
                if(mh.m.sender != 0) {
                    nodeArrow(0.0, mh.m.sender, mh.m.recipient, 0, 0, g, mh, baseOffset*3, arrowColor);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
            System.exit(2);
        }
    }

    static volatile Image image;
    static Container jcomp;
    public static void paintMe(MessageHolder mh) {
        Dimension d = jcomp.getSize();
        image = jcomp.createImage(d.width, d.height);
        paintMe(d, image.getGraphics(), mh);
    }

    //Array of messages
    static ArrayList<Message> buttonMessage = new ArrayList<>();

    static JButton[] buttons = new JButton[15];

    //Getting the message for each button
    static void getButtonText() {
        System.out.println("BUTTONS START");
        //Clearing out the buttonMessage array
        for(int c = 0; c<buttonMessage.size();c++){
            buttonMessage.set(c,null);
        }
        int count = 0;
        for(Message m : Message.msgs) {
            if(m.done()) {
                continue;
            }
            System.out.println(" >> BUTTON: "+m.msg_id+": "+m);
            boolean messageUsed = false;
            //Checking to see if a message has already been added
            for(int c = 0; c<buttonMessage.size();c++){
                if(m==buttonMessage.get(c)){
                    messageUsed = true;
                }
            }
            if(!messageUsed) {
                buttonMessage.add(count, m);
                count++;
            }
        }

        if(buttonMessage.size()>0) {
            System.out.println(buttonMessage.get(0));
        }
        System.out.println("BUTTONS END");

    }

    static JFrame jf;
    public static void main(String[] args) throws Exception {
        jf = new JFrame("Distributed GC GUI");
        jf.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
        JPanel buttonPanel = new JPanel();
        jf.add(buttonPanel, BorderLayout.LINE_END);
        Container c = jf.getContentPane();
        c.setPreferredSize(new Dimension(800, 600));
        final MessageHolder mh = new MessageHolder();
        //Creating buttons and disabling them
        for(int i=0; i<buttons.length; i++) {
            buttons[i] = new JButton("Button #"+i);
            buttons[i].setEnabled(false);
        }

        getButtonText();


        GridLayout layout = new GridLayout(15, 1);
        layout.setVgap(10);
        buttonPanel.setLayout(layout);


        for(int i=0; i<buttons.length; i++) {
            buttonPanel.add(buttons[i]);
        }


        jf.pack();
        buttonPanel.setVisible(true);
        buttonPanel.revalidate();
        buttonPanel.repaint();

        for(final int[] i = {0}; i[0]<buttons.length; i[0]++) {
            int ii = i[0];
            //Taking action when a button is pressed
            buttons[i[0]].addActionListener(a -> {
                System.out.println("Button pressed!");
                Message.setGuiMessage(buttonMessage.get(ii));
                mouseIsClicked();
            });
        }




        jf.add(jcomp = new Container() {
            @Override
            public void paint(Graphics g) {
                System.out.println("Paint called");


                /*for (int i = 0; i < buttonMessage.size() && i < buttons.length; i++) {
                    Message m = buttonMessage.get(i);
                    if(m == null || m.done()) {
                        buttons[i].setEnabled(false);
                        buttons[i].setText("Button #"+i);
                    } else {
                        buttons[i].setEnabled(true);
                        String s = m.toString();
                        int index = s.indexOf("(");
                        if(index != -1) {
                            buttons[i].setText(s.substring(0, index));
                        }else{
                            buttons[i].setText(s);
                        }
                    }
                }*/

                Dimension d = getSize();
                g.setColor(Color.white);
                g.fillRect(0,0,d.width,d.height);
                Image bi = createImage(d.width, d.height);
                paintMe(d, bi.getGraphics(), mh);
                g.drawImage(image, 0, 0, null);

            }

        });


        jf.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent me) {
                System.out.println("mouse pressed");
                mouseIsClicked();
            }
        });
        jf.pack();
        jf.setVisible(true);

        Message.addListener(new MessageListener() {
            boolean ready = false;

            @Override
            public void before(Message m,int step) {
                Runnable r = ()->{
                mh.m = m;
                mh.step = step;
                mh.phase = 1;
                System.out.println("m="+m+" "+Node.nodeMap.size());

                paintMe(mh);
                Thread.yield();
                jf.getContentPane().requestFocus();
                SwingUtilities.invokeLater( ()->{
                    jf.getContentPane().repaint();
                });
                waitForMouse();
                getButtonText();
                SwingUtilities.invokeLater( ()->{
                    jf.getContentPane().repaint();
                });
                ready = true;
                File file = new File(String.format("frame-%d-1.png", step));
                System.out.println(" FILE: "+file);
                try {
                    ImageIO.write((BufferedImage)image,"png",file);
                } catch(Exception e) {
                    e.printStackTrace(); System.exit(0);
                }
                };
                new Thread(r).start();

                arrowColor = Color.red;
            }

            @Override
            public void after(Message m,int step) {
                Runnable r = ()->{
                mh.m = m;
                mh.step = step;
                mh.phase = 2;
                System.out.println("m="+m+" "+Node.nodeMap.size());

                paintMe(mh);
                Thread.yield();
                jf.getContentPane().requestFocus();
                SwingUtilities.invokeLater( ()->{
                    jf.getContentPane().repaint();
                });
                waitForMouse();
                SwingUtilities.invokeLater( ()->{
                    jf.getContentPane().repaint();
                });
                File file = new File(String.format("frame-%d-2.png", step));
                System.out.println(" FILE: "+file);
                try {
                    ImageIO.write((BufferedImage)image,"png",file);
                } catch(Exception e) {
                    e.printStackTrace(); System.exit(0);
                }
                ready = true;
                };
                new Thread(r).start();
                arrowColor = nodeColor[8];

            }

            @Override
            public boolean ready() {
                boolean r = ready;
                ready = false;
                return r;
            }
        });

        //System.setProperty("test", "cycle");
        //System.setProperty("size", "2"); //How many nodes
        //System.setProperty("verbose", "no");

        Main.main(new String[0]);
    }

    static class Rotate {
        int x1, y1, x2, y2;
    }

    //Rotate arrows using rotate()
    static void rotate(Rotate r, double theta) {
        double d = Math.sqrt(((r.x2 - r.x1) * (r.x2 - r.x1)) + ((r.y2 - r.y1) * (r.y2 - r.y1)));
        double alpha = Math.atan2(r.y2 - r.y1, r.x2 - r.x1);
        double beta = alpha + theta;
        double xn = d * Math.cos(beta) + r.x1;
        double yn = d * Math.sin(beta) + r.y1;
        r.x2 = (int) xn;
        r.y2 = (int) yn;
    }


    public static void nodeArrow(double angleSeparating, int startNode, int endNode, int nodeX, int nodeY, Graphics g, MessageHolder mh,int offset, Color color){

        NodePos np1 = getNodePos(startNode);

        int x1 = np1.x;
        int y1 = np1.y;


        NodePos np2 = getNodePos(endNode);

        int x2 = np2.x;
        int y2 = np2.y;


        if(color != null)
            g.setColor(color);

        double theta = Math.atan2(y2 - y1, x2 - x1);
        double d = Math.sqrt((double) ((x1 - x2) * (x1 - x2)) + (double) ((y1 - y2) * (y1 - y2)));
        double L = 10*2; // The length of the arrow head
        double h = L/2;
        double Radius = nodeDiameter/2+5;
        //double offset = 5;


        Rotate r1 = new Rotate();
        r1.x1 = x1;
        r1.y1 = y1;
        r1.x2 = (int) (x1 + Radius);
        r1.y2 = (int) (y1 + offset);
        rotate(r1, theta);

        Rotate r2 = new Rotate();
        r2.x1 = x1;
        r2.y1 = y1;
        r2.x2 = (int) (d + x1 - Radius);
        r2.y2 = (int) (y1 + offset);
        rotate(r2, theta);

        Rotate r3 = new Rotate();
        r3.x1 = x1;
        r3.y1 = y1;
        r3.x2 = (int) (d - L + x1 - Radius);
        r3.y2 = (int) (y1 - h + offset);
        rotate(r3, theta);

        Rotate r4 = new Rotate();
        r4.x1 = x1;
        r4.y1 = y1;
        r4.x2 = (int) (d - L + x1 - Radius);
        r4.y2 = (int) (y1 + h + offset);
        rotate(r4, theta);
        g.drawLine(r1.x2, r1.y2, r2.x2, r2.y2);
        g.fillPolygon(new int[]{r2.x2, r3.x2, r4.x2}, new int[]{r2.y2, r3.y2, r4.y2}, 3);
    }
}
