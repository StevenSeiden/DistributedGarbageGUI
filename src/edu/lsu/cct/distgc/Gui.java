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

                int circleDiameter;

                Dimension d = getSize();
                if(d.height>d.width){
                    circleDiameter = (d.width)-60;
                }
                else{
                    circleDiameter = (d.height)-60;
                    }

                int n = 0;


                g.drawOval(30,30, circleDiameter, circleDiameter);

                int nodeAmount = Node.nodeMap.size();
                double angleSeparating = (2*Math.PI/nodeAmount);
                    g.drawString(mh.m.toString(),0,25);

                    //g.setColor(Color.RED);
                    //g.fillOval((circleDiameter/2)+10,(circleDiameter/2)+10,20,20);



                    //VirtualNode FirstNode = new VirtualNode();
                    for(Node node : Node.nodeMap.values()) {
                        int nodeX = (int)(((circleDiameter/2)*(double)java.lang.Math.cos(angleSeparating*n))+5);
                        int nodeY = (int)(((circleDiameter/2)*(double)java.lang.Math.sin(angleSeparating*n))+5);
                        g.setColor(Color.BLUE);
                        g.fillOval(nodeX, nodeY, 50, 50);

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