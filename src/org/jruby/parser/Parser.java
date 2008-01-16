/*
 ***** BEGIN LICENSE BLOCK *****
 * Version: CPL 1.0/GPL 2.0/LGPL 2.1
 *
 * The contents of this file are subject to the Common Public
 * License Version 1.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of
 * the License at http://www.eclipse.org/legal/cpl-v10.html
 *
 * Software distributed under the License is distributed on an "AS
 * IS" basis, WITHOUT WARRANTY OF ANY KIND, either express or
 * implied. See the License for the specific language governing
 * rights and limitations under the License.
 *
 * Copyright (C) 2002-2004 Anders Bengtsson <ndrsbngtssn@yahoo.se>
 * Copyright (C) 2002-2004 Jan Arne Petersen <jpetersen@uni-bonn.de>
 * Copyright (C) 2004 Thomas E Enebo <enebo@acm.org>
 * Copyright (C) 2004 Stefan Matthias Aust <sma@3plus4.de>
 * 
 * Alternatively, the contents of this file may be used under the terms of
 * either of the GNU General Public License Version 2 or later (the "GPL"),
 * or the GNU Lesser General Public License Version 2.1 or later (the "LGPL"),
 * in which case the provisions of the GPL or the LGPL are applicable instead
 * of those above. If you wish to allow use of your version of this file only
 * under the terms of either the GPL or the LGPL, and not to allow others to
 * use your version of this file under the terms of the CPL, indicate your
 * decision by deleting the provisions above and replace them with the notice
 * and other provisions required by the GPL or the LGPL. If you do not delete
 * the provisions above, a recipient may use your version of this file under
 * the terms of any one of the CPL, the GPL or the LGPL.
 ***** END LICENSE BLOCK *****/
package org.jruby.parser;

import java.io.ByteArrayInputStream;
import java.io.InputStream;

import org.jruby.Ruby;
import org.jruby.RubyArray;
import org.jruby.RubyFile;
import org.jruby.RubyHash;
import org.jruby.RubyString;
import org.jruby.ast.Node;
import org.jruby.common.NullWarnings;
//import org.jruby.lexer.yacc.ByteListLexerSource;
import org.jruby.lexer.yacc.LexerSource;
import org.jruby.lexer.yacc.SyntaxException;
import org.jruby.runtime.DynamicScope;
import org.jruby.runtime.builtin.IRubyObject;
import org.jruby.util.ByteList;

/**
 * Serves as a simple facade for all the parsing magic.
 */
public class Parser {
    private final Ruby runtime;

    public Parser(Ruby runtime) {
        this.runtime = runtime;
    }
    
    public Node parseRewriter(String file, InputStream content, 
            ParserConfiguration configuration) throws SyntaxException {

        DefaultRubyParser parser = RubyParserPool.getInstance().borrowParser();
        try {
            parser.setWarnings(new NullWarnings());
            LexerSource lexerSource = LexerSource.getSource(file, content, null, configuration);
            
            return parser.parse(configuration, lexerSource).getAST();
        } finally {
            RubyParserPool.getInstance().returnParser(parser);
        }
    }
    
    @SuppressWarnings("unchecked")
    public Node parse(String file, ByteList content, DynamicScope blockScope,
            ParserConfiguration configuration) {
        return parse(file, new ByteArrayInputStream(content.bytes()), blockScope, configuration);
        /*
        // Make a shared copy in case someone modifies this bytelist while we are parsing
        content = new ByteList(content, false);
        IRubyObject scriptLines = runtime.getObject().getConstantAt("SCRIPT_LINES__");
        RubyArray list = null;
        
        if (!configuration.isEvalParse() && scriptLines != null) {
            if (scriptLines instanceof RubyHash) {
                RubyString filename = runtime.newString(file);
                IRubyObject object = ((RubyHash) scriptLines).op_aref(filename);
                
                list = (RubyArray) (object instanceof RubyArray ? object : runtime.newArray()); 
                
                ((RubyHash) scriptLines).op_aset(filename, list);
            }
        }

        // We only need to pass in current scope if we are evaluating as a block (which
        // is only done for evals).  We need to pass this in so that we can appropriately scope
        // down to captured scopes when we are parsing.
        if (blockScope != null) {
            configuration.parseAsBlock(blockScope);
        }
        
        DefaultRubyParser parser = null;
        RubyParserResult result = null;
        try {
            parser = RubyParserPool.getInstance().borrowParser();
            parser.setWarnings(runtime.getWarnings());
            LexerSource lexerSource = ByteListLexerSource.getSource(file, content, list, configuration);
            result = parser.parse(configuration, lexerSource);
            if (result.getEndOffset() >= 0) {
                org.jruby.runtime.builtin.IRubyObject verbose = runtime.getVerbose();
                runtime.setVerbose(runtime.getNil());
                content.begin = content.begin +  result.getEndOffset();
                runtime.defineGlobalConstant("DATA", new RubyFile(runtime, file, new ByteArrayInputStream(content.bytes())));
                runtime.setVerbose(verbose);
                result.setEndOffset(-1);
            }
        } catch (SyntaxException e) {
            StringBuffer buffer = new StringBuffer(100);
            buffer.append(e.getPosition().getFile()).append(':');
            buffer.append(e.getPosition().getEndLine() + 1).append(": ");
            buffer.append(e.getMessage());
            throw runtime.newSyntaxError(buffer.toString());
        } finally {
            RubyParserPool.getInstance().returnParser(parser);
        }
        
        // If variables were added then we may need to grow the dynamic scope to match the static
        // one.
        // FIXME: Make this so we only need to check this for blockScope != null.  We cannot
        // currently since we create the DynamicScope for a LocalStaticScope before parse begins.
        // Refactoring should make this fixable.
        if (result.getScope() != null) {
            result.getScope().growIfNeeded();
        }

        return result.getAST();
        */
    }
    
    @SuppressWarnings("unchecked")
    public Node parse(String file, InputStream content, DynamicScope blockScope,
            ParserConfiguration configuration) {
        IRubyObject scriptLines = runtime.getObject().fastGetConstantAt("SCRIPT_LINES__");
        RubyArray list = null;
        
        if (!configuration.isEvalParse() && scriptLines != null) {
            if (scriptLines instanceof RubyHash) {
                RubyString filename = runtime.newString(file);
                IRubyObject object = ((RubyHash) scriptLines).op_aref(filename);
                
                list = (RubyArray) (object instanceof RubyArray ? object : runtime.newArray()); 
                
                ((RubyHash) scriptLines).op_aset(filename, list);
            }
        }

        // We only need to pass in current scope if we are evaluating as a block (which
        // is only done for evals).  We need to pass this in so that we can appropriately scope
        // down to captured scopes when we are parsing.
        if (blockScope != null) {
            configuration.parseAsBlock(blockScope);
        }
        
        DefaultRubyParser parser = null;
        RubyParserResult result = null;
        try {
            parser = RubyParserPool.getInstance().borrowParser();
            parser.setWarnings(runtime.getWarnings());
            LexerSource lexerSource = LexerSource.getSource(file, content, list, configuration);
            result = parser.parse(configuration, lexerSource);
            if (result.getEndOffset() >= 0) {
                IRubyObject verbose = runtime.getVerbose();
                runtime.setVerbose(runtime.getNil());
            	runtime.defineGlobalConstant("DATA", new RubyFile(runtime, file, content));
                runtime.setVerbose(verbose);
            	result.setEndOffset(-1);
            }
        } catch (SyntaxException e) {
            StringBuffer buffer = new StringBuffer(100);
            buffer.append(e.getPosition().getFile()).append(':');
            buffer.append(e.getPosition().getEndLine() + 1).append(": ");
            buffer.append(e.getMessage());
            throw runtime.newSyntaxError(buffer.toString());
        } finally {
            RubyParserPool.getInstance().returnParser(parser);
        }
        
        // If variables were added then we may need to grow the dynamic scope to match the static
        // one.
        // FIXME: Make this so we only need to check this for blockScope != null.  We cannot
        // currently since we create the DynamicScope for a LocalStaticScope before parse begins.
        // Refactoring should make this fixable.
        if (result.getScope() != null) {
            result.getScope().growIfNeeded();
        }

        return result.getAST();
    }
}
