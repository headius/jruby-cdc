/*
 * Copyright (C) 2002 Jan Arne Petersen
 * Copyright (C) 2004 Thomas E Enebo
 * Jan Arne Petersen <jpetersen@uni-bonn.de>
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
package org.jruby.main;

import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.OutputStream;
import java.io.Reader;
import java.util.ArrayList;

import org.ablaf.ast.IAstEncoder;
import org.jruby.ast.util.RubyAstMarshal;
import org.jruby.common.NullWarnings;
import org.jruby.lexer.yacc.LexerSource;
import org.jruby.lexer.yacc.SyntaxException;
import org.jruby.parser.DefaultRubyParser;
import org.jruby.parser.RubyParserConfiguration;
import org.jruby.parser.RubyParserPool;
import org.jruby.parser.RubyParserResult;

/**
 * 
 * @author jpetersen
 * @version $Revision$
 */
public class ASTSerializer {
    public ASTSerializer() {
        super();
    }
    
    public static void serialize(File input, File outputFile) throws IOException {
        OutputStream output = new BufferedOutputStream(new FileOutputStream(outputFile));
        IAstEncoder encoder = RubyAstMarshal.getInstance().openEncoder(output);
        try {
        	serialize(input, encoder);
        } finally {
        	encoder.close();
        }
    }
    
    public static void serialize(File input, IAstEncoder encoder) throws IOException {
        Reader reader = new BufferedReader(new FileReader(input));
        RubyParserConfiguration config = new RubyParserConfiguration();
        config.setBlockVariables(new ArrayList());
        config.setLocalVariables(new ArrayList());

        DefaultRubyParser parser = null;
        RubyParserResult result = null;
        try {
            parser = RubyParserPool.getInstance().borrowParser();
            parser.setWarnings(new NullWarnings());
            parser.init(config);
            result = parser.parse(LexerSource.getSource(input.toString(), reader));
        } catch (SyntaxException e) {
            // ignore the syntax exception
        } finally {
            RubyParserPool.getInstance().returnParser(parser);
        }
        reader.close();
        
        encoder.writeNode(result.getAST());
    }
}