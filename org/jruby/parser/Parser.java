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

/**
 * Serves as a simple facade for all the parsing magic.
 */

package org.jruby.parser;

import org.ablaf.ast.INode;
import org.ablaf.lexer.*;
import org.ablaf.parser.IParser;
import org.jruby.Ruby;
import org.jruby.common.RubyErrorHandler;

import java.util.ArrayList;
import java.io.Reader;
import java.io.StringReader;

public class Parser {
    private final Ruby ruby;
    private IParser internalParser = new DefaultRubyParser();

    public Parser(Ruby ruby) {
        this.ruby = ruby;
        internalParser.setErrorHandler(new RubyErrorHandler(ruby, ruby.isVerbose()));
    }

    public INode parse(String file, String content) {
        return parse(file, new StringReader(content));
    }

    public INode parse(String file, Reader content) {
        return parse(file, content, new RubyParserConfiguration());
    }

    public INode parse(String file, String content, RubyParserConfiguration config) {
        return parse(file, new StringReader(content), config);
    }

    public INode parse(String file, Reader content, RubyParserConfiguration config) {
        config.setLocalVariables(ruby.getScope().getLocalNames());
        internalParser.init(config);
        ILexerSource lexerSource = LexerFactory.getInstance().getSource(file, content);
        IRubyParserResult result = (IRubyParserResult) internalParser.parse(lexerSource);
        if (result.getLocalVariables() != null) {
            ruby.getScope().setLocalNames(new ArrayList(result.getLocalVariables()));
        }
        return result.getAST();
    }
}
