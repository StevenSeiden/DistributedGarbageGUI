package edu.lsu.cct.distgc;

import java.awt.Container;
import java.awt.Graphics;
import javax.swing.JComponent;
import javax.swing.JFrame;
import java.awt.Dimension;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.math.*;
import java.awt.Color;

public class Gui {
    
    static class MessageHolder {
        Message m;
    }

    public static void main(String[] args) throws Exception {

        final JFrame jf = new JFrame("Distributed GC GUI");
        Container c = jf.getContentPane();
        c.setPreferredSize(new Dimension(800,600));
        final MessageHolder mh = new MessageHolder();
        //MyObject my1 = new MyObject();
        c.add(new JComponent() {
            @Override
            public void paint(Graphics g) {
                if(mh.m != null) {


                Dimension d = getSize();
                int circleDiameter = (d.height)-40;
                //int circleDiameter = 500;
                int n = 0;


                g.drawOval(20,20, circleDiameter, circleDiameter);

                int nodeAmount = Node.nodeMap.size();
                double angleSeparating = (2*Math.PI/nodeAmount);


                    g.drawString(mh.m.toString(),0,25);
                    //my1.draw(g,x,y);


                    //Drawing the graphics

                    g.setColor(Color.RED);
                    g.fillOval((circleDiameter/2),(circleDiameter/2),20,20);



                    //VirtualNode FirstNode = new VirtualNode();
                    for(Node node : Node.nodeMap.values()) {
                        int nodeX = (int)((250*(double)java.lang.Math.cos(angleSeparating*n)+(circleDiameter/2))+20);
                        int nodeY = (int)((250*(double)java.lang.Math.sin(angleSeparating*n)+(circleDiameter/2))+20);
                        g.setColor(Color.BLUE);
                        g.fillOval(nodeX, nodeY, 10, 10);

                        System.out.println("id="+node.id);
                        for(Integer out : node.edges) {
                            if(out != null) {
                                Node child = Node.nodeMap.get(out);
                                System.out.printf("There's an edge from %d to %d%n",node.id,child.id);
                                n++;
                            }
                        }
                    }
                }
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
                    Thread.sleep(50);
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
        System.setProperty("test","cycle");
        System.setProperty("size", "4");
        System.setProperty("verbose","yes");
        
        Main.main(new String[0]);
    }
    /*public void virtualNode extends paint(int xPos, int yPos){
        paint(Graphics g) {
            g.Graphics.setColor(Color.BLUE);
            g.Graphics.fillOval(xPos, yPos, 10, 10);
        }
    }*/
}