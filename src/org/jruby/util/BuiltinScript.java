/*
 * Copyright (C) 2002 Anders Bengtsson
 * Copyright (C) 2004 Thomas E Enebo
 * Anders Bengtsson <ndrsbngtssn@yahoo.se>
 * Thomas E Enebo <enebo@acm.org>
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
package org.jruby.util;

import java.io.BufferedInputStream;
import java.io.InputStream;

import org.ablaf.ast.IAstDecoder;
import org.jruby.Ruby;
import org.jruby.ast.Node;
import org.jruby.ast.util.RubyAstMarshal;
import org.jruby.exceptions.IOError;
import org.jruby.runtime.load.Library;

/**
 * Loading of pre-parsed, serialized, Ruby scripts that are built into JRuby.
 */
public class BuiltinScript implements Library {
    private final String name;

    public BuiltinScript(String name) {
        this.name = name;
    }

    public void load(Ruby runtime) {
        runtime.loadNode("jruby builtin", getNode(runtime), false);
    }

    private Node getNode(Ruby runtime) {
        String resourceName = "/builtin/" + name + ".rb.ast.ser";
        InputStream in = getClass().getResourceAsStream(resourceName);
        if (in == null) {
            throw new IOError(runtime, "Resource not found: " + resourceName);
        }
        in = new BufferedInputStream(in);
        IAstDecoder decoder = RubyAstMarshal.getInstance().openDecoder(in);
        Node result = decoder.readNode();
        decoder.close();
        return result;
    }
}
