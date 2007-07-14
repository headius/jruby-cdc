/*
 * VariableCompiler.java
 * 
 * Created on Jul 13, 2007, 11:03:45 PM
 * 
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package org.jruby.compiler;

/**
 *
 * @author headius
 */
public interface VariableCompiler {
    public void assignLocalVariable(int index);
    public void retrieveLocalVariable(int index);
    public void assignLastLine();
    public void retrieveLastLine();
    public void retrieveBackRef();
    public void assignLocalVariable(int index, int depth);
    public void retrieveLocalVariable(int index, int depth);
}
