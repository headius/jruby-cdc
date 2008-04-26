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
 * Copyright (C) 2004-2006 Thomas E Enebo <enebo@acm.org>
 * Copyright (C) 2004 Jan Arne Petersen <jpetersen@uni-bonn.de>
 * Copyright (C) 2004 Stefan Matthias Aust <sma@3plus4.de>
 * Copyright (C) 2005 Zach Dennis <zdennis@mktec.com>
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
package org.jruby.lexer.yacc;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;

import org.jruby.parser.ParserConfiguration;
import org.jruby.util.ByteList;

/**
 * This class is what feeds the lexer.  It is primarily a wrapper around a
 * Reader that can unread() data back onto the source.  Originally, I thought
 * about using the PushBackReader to handle read/unread, but I realized that
 * some extremely pathological case could overflow the pushback buffer.  Better
 * safe than sorry.  I could have combined this implementation with a 
 * PushbackBuffer, but the added complexity did not seem worth it.
 * 
 */
public abstract class LexerSource {
	// Where we get new positions from.
	private ISourcePositionFactory positionFactory;
	
    // The name of this source (e.g. a filename: foo.rb)
    private final String sourceName;
    
    // Number of newlines read from the reader
    protected int line = 0;
    
    // How many bytes into the source are we?
    protected int offset = 0;
    
    // Store each line into this list if not null.
    private List<String> list;
    
    // For 'list' and only populated if list is not null.
    private StringBuilder lineBuffer;

    /**
     * Create our food-source for the lexer
     * 
     * @param sourceName is the file we are reading
     * @param reader is what represents the contents of file sourceName
     * @param line starting line number for source (used by eval)
     * @param extraPositionInformation will gives us extra information that an IDE may want
     */
    protected LexerSource(String sourceName, List<String> list, int line, boolean extraPositionInformation) {
        this.sourceName = sourceName;
        this.line = line;

        if (extraPositionInformation) {
            positionFactory = new IDESourcePositionFactory(this, line);
        } else {
            positionFactory = new SimplePositionFactory(this, line);
        }

        this.list = list;
        lineBuffer = new StringBuilder();
    }

    /**
     * What file are we lexing?
     * @return the files name
     */
    public String getFilename() {
    	return sourceName;
    }
    
    /**
     * What line are we at?
     * @return the line number 0...line_size-1
     */
    public int getLine() {
        return line;
    }
    
    /**
     * The location of the last byte we read from the source.
     * 
     * @return current location of source
     */
    public int getOffset() {
        return (offset <= 0 ? 0 : offset);
    }

    /**
     * Where is the reader within the source {filename,row}
     * 
     * @return the current position
     */
    public ISourcePosition getPosition(ISourcePosition startPosition, boolean inclusive) {
    	return positionFactory.getPosition(startPosition, inclusive);
    }
    
    /**
     * Where is the reader within the source {filename,row}
     * 
     * @return the current position
     */
    public ISourcePosition getPosition() {
    	return positionFactory.getPosition(null, false);
    }
    
    public ISourcePositionFactory getPositionFactory() {
        return positionFactory;
    }

    /**
     * Create a source.
     * 
     * @param name the name of the source (e.g a filename: foo.rb)
     * @param content the data of the source
     * @return the new source
     */
    public static LexerSource getSource(String name, InputStream content, List<String> list,
            ParserConfiguration configuration) {
        return new InputStreamLexerSource(name, content, list, configuration.getLineNumber(), 
                configuration.hasExtraPositionInformation());
    }

    protected void captureFeature(int c) {
        // Ruby's OMG capture all source in a Hash feature
        if (list != null) {
            // Only append real characters (EOF does not count). 
            if (c != -1) lineBuffer.append((char) c);
        
            // Add each line to buffer when encountering newline or EOF for first time.
            if (c == '\n' || (c == -1 && lineBuffer.length() > 0)) {
                list.add(lineBuffer.toString());
                lineBuffer.setLength(0);
            }
        }
    }

    /**
     * Match marker against input consumering lexer source as it goes...Unless it does not match
     * then it reverts lexer source back to point when this method was invoked.
     * 
     * @param marker to match against
     * @param indent eat any leading whitespace
     * @param withNewline includes a check that marker is followed by newline or EOF
     * @return true if marker matches...false otherwise
     * @throws IOException if an error occurred reading from underlying IO source
     */
    public abstract boolean matchMarker(ByteList marker, boolean indent, boolean withNewline) throws IOException;

    public abstract int read() throws IOException;
    public abstract ByteList readUntil(char c) throws IOException;
    public abstract ByteList readLineBytes() throws IOException;
    public abstract int skipUntil(int c) throws IOException;
    public abstract void unread(int c);
    public abstract void unreadMany(CharSequence line);
    public abstract boolean peek(int c) throws IOException;
    public abstract boolean lastWasBeginOfLine();
    public abstract boolean wasBeginOfLine();
}
