/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package edu.lsu.cct.distgc;

/**
 *
 * @author sbrandt
 */
public interface MessageListener {
    void before(Message m,int step);
    void after(Message m,int step);
    boolean ready();
}
