/*
 * kwtable.java - No description
 * Created on 10. September 2001, 17:51
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
package org.jruby.parser;

public class Keyword implements Token, LexState {

    String name;
    int id0, id1;
    int state;

    private Keyword() {
        this("", 0, 0, 0);
    }

    private Keyword(String name, int id0, int id1, int state) {
        this.name = name;
        this.id0 = id0;
        this.id1 = id1;
        this.state = state;
    }

    private static final int TOTAL_KEYWORDS = 40;
    private static final int MIN_WORD_LENGTH = 2;
    private static final int MAX_WORD_LENGTH = 8;
    private static final int MIN_HASH_VALUE = 6;
    private static final int MAX_HASH_VALUE = 55;

    private static final byte asso_values[] = {
        56, 56, 56, 56, 56, 56, 56, 56, 56, 56,
        56, 56, 56, 56, 56, 56, 56, 56, 56, 56,
        56, 56, 56, 56, 56, 56, 56, 56, 56, 56,
        56, 56, 56, 56, 56, 56, 56, 56, 56, 56,
        56, 56, 56, 56, 56, 56, 56, 56, 56, 56,
        56, 56, 56, 56, 56, 56, 56, 56, 56, 56,
        56, 56, 56, 11, 56, 56, 36, 56,  1, 37,
        31,  1, 56, 56, 56, 56, 29, 56,  1, 56,
        56, 56, 56, 56, 56, 56, 56, 56, 56, 56,
        56, 56, 56, 56, 56,  1, 56, 32,  1,  2,
        1,  1,  4, 23, 56, 17, 56, 20,  9,  2,
        9, 26, 14, 56,  5,  1,  1, 16, 56, 21,
        20,  9, 56, 56, 56, 56, 56, 56, 56, 56,
        56, 56, 56, 56, 56, 56, 56, 56, 56, 56,
        56, 56, 56, 56, 56, 56, 56, 56, 56, 56,
        56, 56, 56, 56, 56, 56, 56, 56, 56, 56,
        56, 56, 56, 56, 56, 56, 56, 56, 56, 56,
        56, 56, 56, 56, 56, 56, 56, 56, 56, 56,
        56, 56, 56, 56, 56, 56, 56, 56, 56, 56,
        56, 56, 56, 56, 56, 56, 56, 56, 56, 56,
        56, 56, 56, 56, 56, 56, 56, 56, 56, 56,
        56, 56, 56, 56, 56, 56, 56, 56, 56, 56,
        56, 56, 56, 56, 56, 56, 56, 56, 56, 56,
        56, 56, 56, 56, 56, 56, 56, 56, 56, 56,
        56, 56, 56, 56, 56, 56, 56, 56, 56, 56,
        56, 56, 56, 56, 56, 56
    };

    private static int hash(String str, int len) {
        int hval = len;
        switch (hval) {
        default:
        case 3:
            hval += asso_values[str.charAt(2) & 255];
        case 2:
        case 1:
            hval += asso_values[str.charAt(0) & 255];
            break;
        }
        return hval + asso_values[str.charAt(len - 1) & 255];
    }

    private static final Keyword[] wordlist = {
        new Keyword(),
        new Keyword(),
        new Keyword(),
        new Keyword(),
        new Keyword(),
        new Keyword(),
        new Keyword("end", kEND, kEND, EXPR_END),
        new Keyword("else", kELSE, kELSE, EXPR_BEG),
        new Keyword("case", kCASE, kCASE, EXPR_BEG),
        new Keyword("ensure", kENSURE, kENSURE, EXPR_BEG),
        new Keyword("module", kMODULE, kMODULE, EXPR_BEG),
        new Keyword("elsif", kELSIF, kELSIF, EXPR_BEG),
        new Keyword("def", kDEF, kDEF, EXPR_FNAME),
        new Keyword("rescue", kRESCUE, kRESCUE_MOD, EXPR_END),
        new Keyword("not", kNOT, kNOT, EXPR_BEG),
        new Keyword("then", kTHEN, kTHEN, EXPR_BEG),
        new Keyword("yield", kYIELD, kYIELD, EXPR_ARG),
        new Keyword("for", kFOR, kFOR, EXPR_BEG),
        new Keyword("self", kSELF, kSELF, EXPR_END),
        new Keyword("false", kFALSE, kFALSE, EXPR_END),
        new Keyword("retry", kRETRY, kRETRY, EXPR_END),
        new Keyword("return", kRETURN, kRETURN, EXPR_MID),
        new Keyword("true", kTRUE, kTRUE, EXPR_END),
        new Keyword("if", kIF, kIF_MOD, EXPR_BEG),
        new Keyword("defined?", kDEFINED, kDEFINED, EXPR_ARG),
        new Keyword("super", kSUPER, kSUPER, EXPR_ARG),
        new Keyword("undef", kUNDEF, kUNDEF, EXPR_FNAME),
        new Keyword("break", kBREAK, kBREAK, EXPR_END),
        new Keyword("in", kIN, kIN, EXPR_BEG),
        new Keyword("do", kDO, kDO, EXPR_BEG),
        new Keyword("nil", kNIL, kNIL, EXPR_END),
        new Keyword("until", kUNTIL, kUNTIL_MOD, EXPR_BEG),
        new Keyword("unless", kUNLESS, kUNLESS_MOD, EXPR_BEG),
        new Keyword("or", kOR, kOR, EXPR_BEG),
        new Keyword("next", kNEXT, kNEXT, EXPR_END),
        new Keyword("when", kWHEN, kWHEN, EXPR_BEG),
        new Keyword("redo", kREDO, kREDO, EXPR_END),
        new Keyword("and", kAND, kAND, EXPR_BEG),
        new Keyword("begin", kBEGIN, kBEGIN, EXPR_BEG),
        new Keyword("__LINE__", k__LINE__, k__LINE__, EXPR_END),
        new Keyword("class", kCLASS, kCLASS, EXPR_CLASS),
        new Keyword("__FILE__", k__FILE__, k__FILE__, EXPR_END),
        new Keyword("END", klEND, klEND, EXPR_END),
        new Keyword("BEGIN", klBEGIN, klBEGIN, EXPR_END),
        new Keyword("while", kWHILE, kWHILE_MOD, EXPR_BEG),
        new Keyword(),
        new Keyword(),
        new Keyword(),
        new Keyword(),
        new Keyword(),
        new Keyword(),
        new Keyword(),
        new Keyword(),
        new Keyword(),
        new Keyword(),
        new Keyword("alias", kALIAS, kALIAS, EXPR_FNAME)
    };

    public static Keyword rb_reserved_word(String str, int len) {
        if (len <= MAX_WORD_LENGTH && len >= MIN_WORD_LENGTH) {
            int key = hash(str, len);
            if (key <= MAX_HASH_VALUE && key >= MIN_HASH_VALUE) {
                if (str.equals(wordlist[key].name))
                    return wordlist[key];
            }
        }
        return null;
    }
}