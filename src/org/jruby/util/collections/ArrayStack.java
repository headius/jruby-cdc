/*
 * Copyright (C) 2002 Jan Arne Petersen <jpetersen@uni-bonn.de>
 *
 * JRuby - http://jruby.sourceforge.net
 *
 * This file is part of JRuby
 *
 * JRuby is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License or
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2 of the
 * License, or (at your option) any later version.
 *
 * JRuby is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License and GNU Lesser General Public License
 * for more details.
 *
 * You should have received a copy of the GNU General Public
 * License and GNU Lesser General Public License along with JRuby;
 * if not, write to
 * the Free Software Foundation, Inc., 59 Temple Place, Suite 330,
 * Boston, MA  02111-1307 USA
 */
package org.jruby.util.collections;

import java.util.ArrayList;

/**
 * Simple stack implementation API compatible to java.util.Stack but NOT synchronized. 
 * @author  jpetersen, sma
 * @version $Revision$
 */
public class ArrayStack extends ArrayList{
    public void push(Object element) {
        add(element);
    }
    
    public Object pop() {
        return remove(size() - 1);
    }
    
    public Object peek() {
        return get(size() - 1);
    }

    public boolean empty() {
        return isEmpty();
    }
}