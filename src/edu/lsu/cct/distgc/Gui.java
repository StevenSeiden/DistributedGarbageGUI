package edu.lsu.cct.distgc;

import java.awt.*;
import javax.swing.*;
import java.awt.image.BufferedImage;
import java.util.HashMap;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.Collections;
import java.math.*;
import java.util.Arrays;

public class Gui {

    static class MessageHolder {
        Message m;
    }


    static Color nodeColor[] = {Color.white, Color.gray, Color.cyan, Color.green, Color.orange, Color.pink, Color.yellow,
            (new Color(125, 0, 214)),
            (new Color(12, 143, 0)),
            (new Color(255, 116, 56)),
            (new Color(12, 158, 129)),
            (new Color(142, 31, 31)),
            (new Color(157, 144, 12)),
            (new Color(154, 60, 109)),
            (new Color(86, 127, 189)),
            (new Color(189, 151, 86)),
            (new Color(223, 165, 181)),
            (new Color(250, 255, 148)),
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

    static void paintMe(Dimension d, Graphics g, MessageHolder mh) {

        int circleDiameter;
        if (d.height > d.width) {
            circleDiameter = (d.width) - 60;
        } else {
            circleDiameter = (d.height) - 60;
        }


        // The big circle
        g.drawOval(30, 30, circleDiameter, circleDiameter);
        int nodeAmount = Node.nodeMap.size();
        double angleSeparating = (2 * Math.PI / nodeAmount);
        if (mh.m != null) {
            g.drawString(mh.m.toString(), 0, 25);
        }
        int n = 0;

        try {
            for (Node node : Collections.unmodifiableCollection(Node.nodeMap.values())) {
                int nodeX = (int) (((circleDiameter / 2) * (double) Math.cos(angleSeparating * n)) - 20) + circleDiameter / 2;
                int nodeY = (int) (((circleDiameter / 2) * (double) Math.sin(angleSeparating * n)) - 20) + circleDiameter / 2;
                int nodeDiameter = 100;
                n++;
                //g.setColor(nodeColor[cidColor.get(key)]);

                String key = "blank";
                if (node.cd != null && node.cd.getCid() != null) {
                    key = node.cd.getCid().toString();
                }
                /*
                if(node.cd != null && node.cd.state == CollectorState.infected_state) {
                    key = "dead";
                }
                */
                if(node.cd != null && node.cd.state == CollectorState.dead_state) {
                    key = "dead";
                }

                if (!cidColor.containsKey(key)) {
                    cidColor.put(key, currentColor);
                    System.out.println("COLOR: "+key+" => "+currentColor);
                    currentColor++;
                }
                g.setColor(Color.black);
                if(node.id == 1){
                    g.fillOval((nodeX-5), (nodeY-5), nodeDiameter+10, nodeDiameter+10);
                }
                int color = cidColor.get(key);
                g.setColor(nodeColor[color]);
                g.fillOval(nodeX, nodeY, nodeDiameter, nodeDiameter);
                g.setColor(Color.black);
                g.drawOval(nodeX, nodeY, nodeDiameter, nodeDiameter);
                g.setFont(new Font("default", Font.BOLD, 14));
                g.drawString("id=" + node.id, nodeX + 30, nodeY + 20);
                if(node.cd != null) {
                    g.drawString("" + node.cd.state, nodeX + 10, nodeY + 32);
                }
                //g.drawString("Placeholder", nodeX + 10, nodeY + 32);
                /*int pc = 0;
                if (node.cd != null) {
                    pc = node.cd.phantom_count;
                }
                g.drawString("[" + node.strong_count + ", " + node.weak_count + ", " + pc + ", " +  "]", nodeX + 5, nodeY + 44);*/
                for (Integer out : Collections.unmodifiableList(node.edges)) {
                    if (out != null) {
                        Node child = Node.nodeMap.get(out);

                        //System.out.println("cid="+key);
                        //System.out.println(nodeColor[currentColor]);


                        //System.out.printf("There's an edge from %d to %d%n", node.id, child.id);
                        int startNode = node.id - 1;
                        int endNode = child.id - 1;
                        //System.out.printf("You are going from %d to %d", startNode, endNode);

                        int fromNodeX = (int) (((((circleDiameter / 2)-40) * (double) Math.cos(angleSeparating * startNode)) + 30) + circleDiameter / 2);
                        int fromNodeY = (int) (((((circleDiameter / 2)-40) * (double) Math.sin(angleSeparating * startNode)) + 30) + circleDiameter / 2);
                        int toNodeX = (int) (((((circleDiameter / 2)-40) * (double) Math.cos(angleSeparating * endNode)) + 30) + circleDiameter / 2);
                        int toNodeY = (int) (((((circleDiameter / 2)-40) * (double) Math.sin(angleSeparating * endNode)) + 30) + circleDiameter / 2);

                        nodeArrow(fromNodeX, fromNodeY, toNodeX, toNodeY, g);
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
            System.exit(2);
        }
    }

    public static void main(String[] args) throws Exception {
        final JFrame jf = new JFrame("Distributed GC GUI");
        jf.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
        Container c = jf.getContentPane();
        c.setPreferredSize(new Dimension(800, 600));
        final MessageHolder mh = new MessageHolder();
        c.add(new JComponent() {
            @Override
            public void paint(Graphics g) {
                Dimension d = getSize();
                Image bi = createImage(d.width, d.height);
                paintMe(d, bi.getGraphics(), mh);
                g.drawImage(bi, 0, 0, null);

            }

        });
        jf.pack();
        jf.setVisible(true);

        Message.addListener(new MessageListener() {
            boolean ready = true;

            @Override
            public void before(Message m) {
            }

            @Override
            public void after(Message m) {
                mh.m = m;
                jf.repaint();

                try {
                    Thread.sleep(250);
                } catch (InterruptedException ex) {
                }

                ready = true;
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

    public static void nodeArrow(int x1, int y1, int x2, int y2, Graphics g) {
        double theta = Math.atan2(y2 - y1, x2 - x1);
        double d = Math.sqrt((double) ((x1 - x2) * (x1 - x2)) + (double) ((y1 - y2) * (y1 - y2)));
        double L = 10;
        double h = 5;
        double Radius = 30;
        double offset = 5;

        Rotate r1 = new Rotate();
        r1.x1 = x1;
        r1.y1 = y1;
        r1.x2 = (int) (x1 + Radius);
        r1.y2 = (int) (y1 + offset);
        rotate(r1, theta);

        Rotate r2 = new Rotate();
        r2.x1 = x1;//(int)(x1+Radius);
        r2.y1 = y1;//(int)(y1 + offset);
        r2.x2 = (int) (d + x1 - Radius);
        r2.y2 = (int) (y1 + offset);
        rotate(r2, theta);

        Rotate r3 = new Rotate();
        r3.x1 = x1;//(int)(x1+Radius);
        r3.y1 = y1;//(int)(y1+offset);
        //r3.x2 = (int) (d - L + x1);
        //r3.y2 = (int) (y1 - L);
        r3.x2 = (int) (d - L + x1 - Radius);
        r3.y2 = (int) (y1 - L + offset);
        rotate(r3, theta);

        Rotate r4 = new Rotate();
        r4.x1 = x1;//(int)(x1+Radius);
        r4.y1 = y1;//(int)(y1 + offset);
        r4.x2 = (int) (d - L + x1 - Radius);
        r4.y2 = (int) (y1 + 10 + offset);
        rotate(r4, theta);

        /*int x3 = x1 - 10;
        int y3 = y1 - 10;
        double theta = Math.atan2(y2-y1,x2-x1);

        double x2_2 = r12*Math.cos(theta);
        double x2_3 = x2_2+x1;*/


        //double x3_2 = r13*Math.cos(theta);


        g.drawLine(r1.x2, r1.y2, r2.x2, r2.y2);
        //g.fillPolygon(new int[] {x2, x2-10, x2-10}, new int[] {y2, y2-10, y2+10}, 3);
        g.fillPolygon(new int[]{r2.x2, r3.x2, r4.x2}, new int[]{r2.y2, r3.y2, r4.y2}, 3);
    }
}
