/*
 * RescueNode.java - No description
 * Created on 05. November 2001, 21:46
 * 
 * Copyright (C) 2001 Jan Arne Petersen, Stefan Matthias Aust, Alan Moore, Benoit Cerrina
 * Jan Arne Petersen <japetersen@web.de>
 * Stefan Matthias Aust <sma@3plus4.de>
 * Alan Moore <alan_moore@gmx.net>
 * Benoit Cerrina <b.cerrina@wanadoo.fr>
 * 
 * JRuby - http://jruby.sourceforge.net
 * 
 * This file is part of JRuby
 * 
 * JRuby is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 * 
 * JRuby is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License
 * along with JRuby; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
 * 
 */

package org.jruby.nodes;

import org.jruby.*;
import org.jruby.nodes.util.*;
import org.jruby.exceptions.*;
import org.jruby.runtime.*;

/**
 *
 * @author  jpetersen
 * @version
 */
public class RescueNode extends Node {
    public RescueNode(Node headNode, Node rescueNode, Node elseNode) {
        super(Constants.NODE_RESCUE, headNode, rescueNode, elseNode);
    }
    
    public RubyObject eval(Ruby ruby, RubyObject self) {
        RescuedBlock: while (true) {
            try {
                // Execute recued Block
                RubyObject result = getHeadNode().eval(ruby, self);

                // If no exception is thrown execute else Block
                if (getElseNode() != null) {
                    return getElseNode().eval(ruby, self);
                }

                return result;
            } catch (RaiseException raExcptn) {
                ruby.setSourceLine(getLine());

                Node body = getResqNode();
                while (body != null) {
                    if (isRescueHandled(ruby, self, body)) {
                        try {
                            return body.eval(ruby, self);
                        } catch (RetryException rExcptn) {
                            ruby.setActException(ruby.getNil());
                            continue RescuedBlock;
                        }
                    }
                    body = body.getHeadNode();
                }
                throw new RaiseException();
            }
        }
    }
    
    private boolean isRescueHandled(Ruby ruby, RubyObject self, Node node) {
        // TMP_PROTECT;
        
        if (node.getArgsNode() == null) {
            // return ruby.getActException().m_kind_of(ruby.getExceptions().getStandardError()).isTrue();
        }

        RubyBlock tmpBlock = ArgsUtil.beginCallArgs(ruby);
        RubyObject[] args = ArgsUtil.setupArgs(ruby, self, node.getArgsNode());
        ArgsUtil.endCallArgs(ruby, tmpBlock);
        
        for (int i = 0; i < args.length; i++) {
            if (args[i].m_kind_of(ruby.getClasses().getModuleClass()).isFalse()) {
                throw new RubyTypeException("class or module required for rescue clause");
            }
            if (ruby.getActException().m_kind_of((RubyModule)args[i]).isTrue()) {
                return true;
            }
        }
        return false;
    }
}