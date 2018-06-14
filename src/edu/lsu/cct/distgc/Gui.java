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
import java.util.Arrays;
import java.util.Scanner;

public class Gui {
    public final static int nodeDiameter = 100;

    static class NodePos {
        int x, y; // the position
        double angleSeparating; //Angle between each node
    }

    NodePos getNodePos(int nodeId, double angleSeparating) {
        // Fill in
        int x = (int) (((circleDiameter / 2) * (double) Math.cos(angleSeparating*nodeId)) + (circleDiameter/2));
        int y = (int) (((circleDiameter / 2) * (double) Math.sin(angleSeparating*nodeId)) + (circleDiameter/2));



        return null;
    }

    static class MessageHolder {
        Message m;
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


    static HashMap<String, Integer> cidColor = new HashMap<String, Integer>();

    static int currentColor = 2;
    static {
        cidColor.put("blank",0);
        cidColor.put("dead",1);
    }



    static int circleDiameter = 500;

    static void paintMe(Dimension d, Graphics g, MessageHolder mh) {
        if(mh.m != null)
            System.out.println("paintMe: mh.m="+mh.m);

        /*int circleDiameter;
        if (d.height > d.width) {
            circleDiameter = (d.width) - 100;
        } else {
            circleDiameter = (d.height) - 100;
        }*/


        // The big circle
        g.drawOval(50, 50, circleDiameter, circleDiameter);
        int nodeAmount = Node.nodeMap.size();
        double angleSeparating = (2 * Math.PI / nodeAmount);
        if (mh.m != null) {
            g.setFont(new Font("default", Font.BOLD, 26));
            g.drawString(mh.m.toString(), 0, 25);
        }
        int n = 0;

        try {
            for (Node node : Node.nodeMap.values()) {
                n = node.id;
                int nodeX = (int) ((circleDiameter / 2) * (double) Math.cos(angleSeparating * n)) + circleDiameter / 2;
                int nodeY = (int) ((circleDiameter / 2) * (double) Math.sin(angleSeparating * n)) + circleDiameter / 2;
                //g.setColor(nodeColor[cidColor.get(key)]);

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

                for(Root r : Root.roots) {
                    Node nn = r.get();
                    if(nn != null && r.getId() == node.id){
                        rootNode = true;
                    }
                }


                if(rootNode){
                    g.fillOval((nodeX-4), (nodeY-4), nodeDiameter+8, nodeDiameter+8);
                }

                int color = cidColor.get(key);
                g.setColor(nodeColor[color]);
                g.fillOval(nodeX, nodeY, nodeDiameter, nodeDiameter);
                g.setColor(Color.black);

                g.drawOval(nodeX, nodeY, nodeDiameter, nodeDiameter);

                int pc = 0;
                int rcc = 0;
                int wc = 0;
                if (node.cd != null) {
                    pc = node.cd.phantom_count;
                    rcc = node.cd.rcc;
                    wc = node.cd.wait_count;
                }

                g.setFont(new Font("default", Font.BOLD, 16));

                g.drawString("id=" + node.id, nodeX + 27, nodeY + 30);

                g.setFont(new Font("default", Font.BOLD, 12));

                if(node.cd != null) {
                    g.drawString(node.cd.getCid().toString() + " " + node.cd.state, nodeX + 2, nodeY + 55);
                }
                g.setFont(new Font("default", Font.BOLD, 14));
                //g.drawString("Placeholder", nodeX + 10, nodeY + 32);
                g.drawString("[" + node.strong_count + ", " + node.weak_count + ", " + pc + ", " + rcc + "]", nodeX + 15, nodeY + 75);
                g.drawString("(" + node.weight + "/" + node.max_weight + ") " + wc, nodeX + 23, nodeY + 90);


                for (Integer out : node.edges) {
                    if (out != null) {
                        Node child = Node.nodeMap.get(out);

                        //System.out.println("cid="+key);
                        //System.out.println(nodeColor[currentColor]);


                        //System.out.printf("There's an edge from %d to %d%n", node.id, child.id);
                        int startNode = node.id;
                        int endNode = child.id;
                        nodeArrow(angleSeparating, startNode, endNode,nodeX, nodeY, g, mh);
                    }
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
    static JFrame jf;
    public static void main(String[] args) throws Exception {
        jf = new JFrame("Distributed GC GUI");
        jf.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
        Container c = jf.getContentPane();
        c.setPreferredSize(new Dimension(800, 600));
        final MessageHolder mh = new MessageHolder();
        c.add(jcomp = new Container() {
            @Override
            public void paint(Graphics g) {
                System.out.println("Paint called");
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
            boolean ready = true;
            //Scanner sc = new Scanner(System.in);

            @Override
            public void before(Message m) {
                Runnable r = ()->{
                mh.m = m;
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
                ready = true;
                };
                new Thread(r).start();
            }

            @Override
            public void after(Message m) {
                Runnable r = ()->{
                mh.m = m;
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
                ready = true;
                };
                new Thread(r).start();
            }

            @Override
            public boolean ready() {
                boolean r = ready;
                ready = false;
                return r;
            }
        });

        System.setProperty("CONGEST_mode", "yes");
        System.setProperty("test", "clique");
        System.setProperty("size", "20");
        System.setProperty("verbose", "yes");

        Main.main(new String[0]);
    }

    static class Rotate {
        int x1, y1, x2, y2;
    }

    static void rotate(Rotate r, double theta) {
        double d = Math.sqrt(((r.x2 - r.x1) * (r.x2 - r.x1)) + ((r.y2 - r.y1) * (r.y2 - r.y1)));
        double alpha = Math.atan2(r.y2 - r.y1, r.x2 - r.x1);
        double beta = alpha + theta;
        double xn = d * Math.cos(beta) + r.x1;
        double yn = d * Math.sin(beta) + r.y1;
        r.x2 = (int) xn;
        r.y2 = (int) yn;
    }


    public static void nodeArrow(double angleSeparating, int startNode, int endNode, int nodeX, int nodeY, Graphics g, MessageHolder mh){
        NodePos np1 = new NodePos;
        getNodePos(startNode, angleSeparating);
        NodePos np2 = new NodePos;
        getNodePos(endNode, angleSeparating);

        int x1 = np1.x; //nodeX+60;//(int) (((((circleDiameter / 2)-30) * (double) Math.cos(angleSeparating * (startNode-1))) + 50) + circleDiameter / 2);
        int y1 = np1.y;//(int) (((((circleDiameter / 2)-30) * (double) Math.sin(angleSeparating * (startNode-1))) + 50) + circleDiameter / 2);
        int x2 = np2.x;// (((((circleDiameter / 2)-30) * (double) Math.cos(angleSeparating * (endNode-1))) + 50) + circleDiameter / 2);
        int y2 = np2.y;// (((((circleDiameter / 2)-30) * (double) Math.sin(angleSeparating * (endNode-1))) + 50) + circleDiameter / 2);
        int arrowColor;

        /*if(){
            arrowColor = false;
        }*/


        if((mh.m.sender == startNode) && (mh.m.recipient == endNode)){
            //if(!(Boolean arrowColor)) {
                g.setColor(Color.red);
                /*arrowColor = true;
            } else{
                g.setColor(Color.green);
                arrowColor = false;
            }*/
        }else{
            g.setColor(Color.black);
        }

        double theta = Math.atan2(y2 - y1, x2 - x1);
        double d = Math.sqrt((double) ((x1 - x2) * (x1 - x2)) + (double) ((y1 - y2) * (y1 - y2)));
        double L = 10; // The length of the arrow head
        double h = 5;
        double Radius = nodeDiameter/2;
        double offset = 5;


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
        r3.y2 = (int) (y1 - L + offset);
        rotate(r3, theta);

        Rotate r4 = new Rotate();
        r4.x1 = x1;
        r4.y1 = y1;
        r4.x2 = (int) (d - L + x1 - Radius);
        r4.y2 = (int) (y1 + L + offset);
        rotate(r4, theta);
        g.drawLine(r1.x2, r1.y2, r2.x2, r2.y2);
        g.fillPolygon(new int[]{r2.x2, r3.x2, r4.x2}, new int[]{r2.y2, r3.y2, r4.y2}, 3);
    }
}
