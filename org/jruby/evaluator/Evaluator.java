/*
 * Copyright (C) 2002 Anders Bengtsson <ndrsbngtssn@yahoo.se>
 *
 * JRuby - http://jruby.sourceforge.net
 *
 * This file is part of JRuby
 *
 * JRuby is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License as
 * published by the Free Software Foundation; either version 2 of the
 * License, or (at your option) any later version.
 *
 * JRuby is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public
 * License along with JRuby; if not, write to
 * the Free Software Foundation, Inc., 59 Temple Place, Suite 330,
 * Boston, MA  02111-1307 USA
 */

package org.jruby.evaluator;

import org.ablaf.ast.INode;
import org.jruby.Ruby;
import org.jruby.RubyArray;
import org.jruby.RubyModule;
import org.jruby.RubyObject;
import org.jruby.ast.MultipleAsgnNode;
import org.jruby.ast.ZeroArgNode;
import org.jruby.exceptions.ArgumentError;
import org.jruby.exceptions.NextJump;
import org.jruby.exceptions.RaiseException;
import org.jruby.exceptions.RedoJump;
import org.jruby.exceptions.ReturnException;
import org.jruby.runtime.Block;
import org.jruby.runtime.BlockStack;
import org.jruby.runtime.Frame;
import org.jruby.runtime.FrameStack;
import org.jruby.runtime.ICallable;
import org.jruby.runtime.Iter;
import org.jruby.runtime.Namespace;
import org.jruby.runtime.RubyVarmap;
import org.jruby.runtime.Scope;
import org.jruby.runtime.ScopeStack;
import org.jruby.runtime.builtin.IRubyObject;
import org.jruby.util.collections.CollectionFactory;
import org.jruby.util.collections.IStack;

public class Evaluator {
    private final Ruby ruby;

    private ScopeStack scopeStack;
    private FrameStack frameStack;
    private IStack iterStack;

    public Evaluator(Ruby ruby) {
        this.ruby = ruby;
        scopeStack = new ScopeStack(ruby);
        frameStack = new FrameStack(ruby);
        iterStack = CollectionFactory.getInstance().newStack();
    }

    public ScopeStack getScopeStack() {
        return scopeStack;
    }

    public FrameStack getFrameStack() {
        return frameStack;
    }

    public Frame getCurrentFrame() {
        return (Frame) getFrameStack().peek();
    }

    public IStack getIterStack() {
        return iterStack;
    }

    public Iter getCurrentIter() {
        return (Iter) getIterStack().peek();
    }

    public IRubyObject eval(INode node) {
        return EvaluateVisitor.createVisitor(ruby.getRubyTopSelf()).eval(node);
    }
}
