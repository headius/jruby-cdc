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
 * Copyright (C) 2002 Jan Arne Petersen <jpetersen@uni-bonn.de>
 * Copyright (C) 2004 Thomas E Enebo <enebo@acm.org>
 * Copyright (C) 2004 Anders Bengtsson <ndrsbngtssn@yahoo.se>
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

import org.jruby.runtime.DynamicScope;

public class ParserConfiguration {
    private DynamicScope existingScope = null;
    private boolean asBlock = false;
    // What linenumber will the source think it starts from?
    private int lineNumber = 0;
    // Is this inline source (aka -e "...source...")
    private boolean inlineSource = false;
    // We parse evals more often in source so assume an eval parse.
    private boolean isEvalParse = true;
    // Should positions added extra IDE-friendly information and leave in all newline nodes
    private boolean extraPositionInformation = false;
    
    public ParserConfiguration(int lineNumber, boolean inlineSource) {
        this(lineNumber, false, inlineSource);
    }
    
    public ParserConfiguration(int lineNumber, boolean extraPositionInformation, boolean inlineSource) {
        this.inlineSource = inlineSource;
        this.lineNumber = lineNumber;
        this.extraPositionInformation = extraPositionInformation;
    }

    public ParserConfiguration(int lineNumber, boolean extraPositionInformation, boolean inlineSource, boolean isFileParse) {
        this.inlineSource = inlineSource;
        this.lineNumber = lineNumber;
        this.extraPositionInformation = extraPositionInformation;
        this.isEvalParse = !isFileParse;
    }

    /**
     * Set whether this is an parsing of an eval() or not.
     * 
     * @param isEvalParse says how we should look at it
     */
    public void setEvalParse(boolean isEvalParse) {
        this.isEvalParse = isEvalParse;
    }

    /**
     * Should positions of nodes provide additional information in them (like character offsets).
     * @param extraPositionInformation
     */
    public void setExtraPositionInformation(boolean extraPositionInformation) {
        this.extraPositionInformation = extraPositionInformation;
    }
    
    /**
     * Should positions of nodes provide addition information?
     * @return true if they should
     */
    public boolean hasExtraPositionInformation() {
        return extraPositionInformation;
    }

    /**
     * Is the requested parse for an eval()?
     * 
     * @return true if for eval
     */
    public boolean isEvalParse() {
        return isEvalParse;
    }
    
    public int getLineNumber() {
        return lineNumber;
    }

    /**
     * If we are performing an eval we should pass existing scope in.
     * Calling this lets the parser know we need to do this.
     * 
     * @param existingScope is the scope that captures new vars, etc...
     */
    public void parseAsBlock(DynamicScope existingScope) {
        this.asBlock = true;
        this.existingScope = existingScope;
    }
    
    /**
     * This method returns the appropriate first scope for the parser.
     * 
     * @return correct top scope for source to be parsed
     */
    public DynamicScope getScope() {
        if (asBlock) return existingScope;
        
        // FIXME: We should really not be creating the dynamic scope for the root
        // of the AST before parsing.  This makes us end up needing to readjust
        // this dynamic scope coming out of parse (and for local static scopes it
        // will always happen because of $~ and $_).
        return DynamicScope.newDynamicScope(new LocalStaticScope(null), existingScope);
    }
    
    /**
     * Are we parsing source provided as part of the '-e' option to Ruby.
     * 
     * @return true if source is from -e option
     */
    public boolean isInlineSource() {
        return inlineSource;
    }
}
