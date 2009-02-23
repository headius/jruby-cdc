// created by jay 1.0.2 (c) 2002-2004 ats@cs.rit.edu
// skeleton Java 1.0 (c) 2002 ats@cs.rit.edu

					// line 2 "Ruby19Parser.y"
/***** BEGIN LICENSE BLOCK *****
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
 * Copyright (C) 2008-2009 Thomas E Enebo <enebo@acm.org>
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

import java.io.IOException;

import org.jruby.ast.AliasNode;
import org.jruby.ast.ArgsNode;
import org.jruby.ast.ArgumentNode;
import org.jruby.ast.ArrayNode;
import org.jruby.ast.AssignableNode;
import org.jruby.ast.BackRefNode;
import org.jruby.ast.BeginNode;
import org.jruby.ast.BlockAcceptingNode;
import org.jruby.ast.BlockArgNode;
import org.jruby.ast.BlockNode;
import org.jruby.ast.BlockPassNode;
import org.jruby.ast.BreakNode;
import org.jruby.ast.CaseNode;
import org.jruby.ast.ClassNode;
import org.jruby.ast.ClassVarNode;
import org.jruby.ast.Colon3Node;
import org.jruby.ast.ConstDeclNode;
import org.jruby.ast.DRegexpNode;
import org.jruby.ast.DStrNode;
import org.jruby.ast.DSymbolNode;
import org.jruby.ast.DXStrNode;
import org.jruby.ast.DefinedNode;
import org.jruby.ast.DefnNode;
import org.jruby.ast.DefsNode;
import org.jruby.ast.DotNode;
import org.jruby.ast.EnsureNode;
import org.jruby.ast.EvStrNode;
import org.jruby.ast.FCallNoArgBlockNode;
import org.jruby.ast.FCallNoArgNode;
import org.jruby.ast.FixnumNode;
import org.jruby.ast.FloatNode;
import org.jruby.ast.ForNode;
import org.jruby.ast.GlobalVarNode;
import org.jruby.ast.HashNode;
import org.jruby.ast.IfNode;
import org.jruby.ast.InstVarNode;
import org.jruby.ast.IterNode;
import org.jruby.ast.LambdaNode;
import org.jruby.ast.ListNode;
import org.jruby.ast.ModuleNode;
import org.jruby.ast.MultipleAsgn19Node;
import org.jruby.ast.NewlineNode;
import org.jruby.ast.NextNode;
import org.jruby.ast.NilImplicitNode;
import org.jruby.ast.NilNode;
import org.jruby.ast.Node;
import org.jruby.ast.NotNode;
import org.jruby.ast.OpAsgnAndNode;
import org.jruby.ast.OpAsgnNode;
import org.jruby.ast.OpAsgnOrNode;
import org.jruby.ast.OptArgNode;
import org.jruby.ast.PostExeNode;
import org.jruby.ast.PreExeNode;
import org.jruby.ast.RedoNode;
import org.jruby.ast.RegexpNode;
import org.jruby.ast.RescueBodyNode;
import org.jruby.ast.RescueNode;
import org.jruby.ast.RestArgNode;
import org.jruby.ast.RetryNode;
import org.jruby.ast.ReturnNode;
import org.jruby.ast.SClassNode;
import org.jruby.ast.SValueNode;
import org.jruby.ast.SelfNode;
import org.jruby.ast.SplatNode;
import org.jruby.ast.StarNode;
import org.jruby.ast.StrNode;
import org.jruby.ast.SymbolNode;
import org.jruby.ast.ToAryNode;
import org.jruby.ast.TypedArgumentNode;
import org.jruby.ast.UndefNode;
import org.jruby.ast.UnnamedRestArgNode;
import org.jruby.ast.UntilNode;
import org.jruby.ast.VAliasNode;
import org.jruby.ast.WhileNode;
import org.jruby.ast.XStrNode;
import org.jruby.ast.YieldNode;
import org.jruby.ast.ZArrayNode;
import org.jruby.ast.ZSuperNode;
import org.jruby.ast.types.ILiteralNode;
import org.jruby.common.IRubyWarnings;
import org.jruby.common.IRubyWarnings.ID;
import org.jruby.lexer.yacc.ISourcePosition;
import org.jruby.lexer.yacc.ISourcePositionHolder;
import org.jruby.lexer.yacc.LexerSource;
import org.jruby.lexer.yacc.RubyYaccLexer;
import org.jruby.lexer.yacc.RubyYaccLexer.LexState;
import org.jruby.lexer.yacc.StrTerm;
import org.jruby.lexer.yacc.SyntaxException;
import org.jruby.lexer.yacc.SyntaxException.PID;
import org.jruby.lexer.yacc.Token;
import org.jruby.util.ByteList;

public class Ruby19Parser implements RubyParser {
    protected ParserSupport19 support;
    protected RubyYaccLexer lexer;
    protected IRubyWarnings warnings;

    public Ruby19Parser() {
        this(new ParserSupport19());
    }

    public Ruby19Parser(ParserSupport19 support) {
        this.support = support;
        lexer = new RubyYaccLexer(false);
        lexer.setParserSupport(support);
    }

    public void setWarnings(IRubyWarnings warnings) {
        this.warnings = warnings;

        support.setWarnings(warnings);
        lexer.setWarnings(warnings);
    }
					// line 150 "-"
  // %token constants
  public static final int kCLASS = 257;
  public static final int kMODULE = 258;
  public static final int kDEF = 259;
  public static final int kUNDEF = 260;
  public static final int kBEGIN = 261;
  public static final int kRESCUE = 262;
  public static final int kENSURE = 263;
  public static final int kEND = 264;
  public static final int kIF = 265;
  public static final int kUNLESS = 266;
  public static final int kTHEN = 267;
  public static final int kELSIF = 268;
  public static final int kELSE = 269;
  public static final int kCASE = 270;
  public static final int kWHEN = 271;
  public static final int kWHILE = 272;
  public static final int kUNTIL = 273;
  public static final int kFOR = 274;
  public static final int kBREAK = 275;
  public static final int kNEXT = 276;
  public static final int kREDO = 277;
  public static final int kRETRY = 278;
  public static final int kIN = 279;
  public static final int kDO = 280;
  public static final int kDO_COND = 281;
  public static final int kDO_BLOCK = 282;
  public static final int kRETURN = 283;
  public static final int kYIELD = 284;
  public static final int kSUPER = 285;
  public static final int kSELF = 286;
  public static final int kNIL = 287;
  public static final int kTRUE = 288;
  public static final int kFALSE = 289;
  public static final int kAND = 290;
  public static final int kOR = 291;
  public static final int kNOT = 292;
  public static final int kIF_MOD = 293;
  public static final int kUNLESS_MOD = 294;
  public static final int kWHILE_MOD = 295;
  public static final int kUNTIL_MOD = 296;
  public static final int kRESCUE_MOD = 297;
  public static final int kALIAS = 298;
  public static final int kDEFINED = 299;
  public static final int klBEGIN = 300;
  public static final int klEND = 301;
  public static final int k__LINE__ = 302;
  public static final int k__FILE__ = 303;
  public static final int k__ENCODING__ = 304;
  public static final int kDO_LAMBDA = 305;
  public static final int tIDENTIFIER = 306;
  public static final int tFID = 307;
  public static final int tGVAR = 308;
  public static final int tIVAR = 309;
  public static final int tCONSTANT = 310;
  public static final int tCVAR = 311;
  public static final int tLABEL = 312;
  public static final int tCHAR = 313;
  public static final int tUPLUS = 314;
  public static final int tUMINUS = 315;
  public static final int tUMINUS_NUM = 316;
  public static final int tPOW = 317;
  public static final int tCMP = 318;
  public static final int tEQ = 319;
  public static final int tEQQ = 320;
  public static final int tNEQ = 321;
  public static final int tGEQ = 322;
  public static final int tLEQ = 323;
  public static final int tANDOP = 324;
  public static final int tOROP = 325;
  public static final int tMATCH = 326;
  public static final int tNMATCH = 327;
  public static final int tDOT = 328;
  public static final int tDOT2 = 329;
  public static final int tDOT3 = 330;
  public static final int tAREF = 331;
  public static final int tASET = 332;
  public static final int tLSHFT = 333;
  public static final int tRSHFT = 334;
  public static final int tCOLON2 = 335;
  public static final int tCOLON3 = 336;
  public static final int tOP_ASGN = 337;
  public static final int tASSOC = 338;
  public static final int tLPAREN = 339;
  public static final int tLPAREN2 = 340;
  public static final int tRPAREN = 341;
  public static final int tLPAREN_ARG = 342;
  public static final int tLBRACK = 343;
  public static final int tRBRACK = 344;
  public static final int tLBRACE = 345;
  public static final int tLBRACE_ARG = 346;
  public static final int tSTAR = 347;
  public static final int tSTAR2 = 348;
  public static final int tAMPER = 349;
  public static final int tAMPER2 = 350;
  public static final int tTILDE = 351;
  public static final int tPERCENT = 352;
  public static final int tDIVIDE = 353;
  public static final int tPLUS = 354;
  public static final int tMINUS = 355;
  public static final int tLT = 356;
  public static final int tGT = 357;
  public static final int tPIPE = 358;
  public static final int tBANG = 359;
  public static final int tCARET = 360;
  public static final int tLCURLY = 361;
  public static final int tRCURLY = 362;
  public static final int tBACK_REF2 = 363;
  public static final int tSYMBEG = 364;
  public static final int tSTRING_BEG = 365;
  public static final int tXSTRING_BEG = 366;
  public static final int tREGEXP_BEG = 367;
  public static final int tWORDS_BEG = 368;
  public static final int tQWORDS_BEG = 369;
  public static final int tSTRING_DBEG = 370;
  public static final int tSTRING_DVAR = 371;
  public static final int tSTRING_END = 372;
  public static final int tLAMBDA = 373;
  public static final int tLAMBEG = 374;
  public static final int tNTH_REF = 375;
  public static final int tBACK_REF = 376;
  public static final int tSTRING_CONTENT = 377;
  public static final int tINTEGER = 378;
  public static final int tFLOAT = 379;
  public static final int tREGEXP_END = 380;
  public static final int tLOWEST = 381;
  public static final int yyErrorCode = 256;

  /** number of final state.
    */
  protected static final int yyFinal = 1;

  /** parser tables.
      Order is mandated by <i>jay</i>.
    */
  protected static final short[] yyLhs = {
//yyLhs 541
    -1,   117,     0,    35,    34,    36,    36,    36,    36,   120,
    37,    37,    37,    37,    37,    37,    37,    37,    37,    37,
   121,    37,    37,    37,    37,    37,    37,    37,    37,    37,
    37,    37,    37,    37,    37,    38,    38,    38,    38,    38,
    38,    42,    33,    33,    33,    33,    33,    56,    56,    56,
   122,    95,    41,    41,    41,    41,    41,    41,    41,    41,
    96,    96,   105,   105,    97,    97,    97,    97,    97,    97,
    97,    97,    97,    97,    68,    68,    82,    82,    86,    86,
    69,    69,    69,    69,    69,    69,    69,    69,    74,    74,
    74,    74,    74,    74,    74,    74,     8,     8,    32,    32,
    32,     9,     9,     9,     9,     9,   112,   112,     2,     2,
    58,   123,    58,    10,    10,    10,    10,    10,    10,    10,
    10,    10,    10,    10,    10,    10,    10,    10,    10,    10,
    10,    10,    10,    10,    10,    10,    10,    10,    10,    10,
    10,    10,   115,   115,   115,   115,   115,   115,   115,   115,
   115,   115,   115,   115,   115,   115,   115,   115,   115,   115,
   115,   115,   115,   115,   115,   115,   115,   115,   115,   115,
   115,   115,   115,   115,   115,   115,   115,   115,   115,   115,
   115,   115,   115,   115,    39,    39,    39,    39,    39,    39,
    39,    39,    39,    39,    39,    39,    39,    39,    39,    39,
    39,    39,    39,    39,    39,    39,    39,    39,    39,    39,
    39,    39,    39,    39,    39,    39,    39,    39,    39,    39,
    39,    39,    39,    39,    39,    39,    39,    39,    39,    70,
    73,    73,    73,    73,    50,    54,    54,   108,   108,    48,
    48,    48,    48,    48,   126,    52,    89,    88,    88,    76,
    76,    76,    76,    67,    67,    67,    40,    40,    40,    40,
    40,    40,    40,    40,    40,    40,   127,    40,    40,    40,
    40,    40,    40,    40,    40,    40,    40,    40,    40,    40,
    40,    40,    40,    40,    40,    40,   129,   131,    40,   132,
   133,    40,    40,    40,   134,   135,    40,   136,    40,   138,
   139,    40,   140,    40,   141,    40,   142,   143,    40,    40,
    40,    40,    40,    43,   128,   128,   128,   130,   130,    46,
    46,    44,    44,   107,   107,   109,   109,    81,    81,   110,
   110,   110,   110,   110,   110,   110,   110,   110,    64,    64,
    64,    64,    64,    64,    64,    64,    64,    64,    64,    64,
    64,    64,    64,    66,    66,    65,    65,    65,   102,   102,
   101,   101,   111,   111,   144,   104,    63,    63,   103,   103,
   145,    94,    55,    55,    55,    25,    25,    25,    25,    25,
    25,    25,    25,    25,   146,    93,   147,    93,    71,    45,
    45,    98,    98,    72,    72,    72,    47,    47,    49,    49,
    29,    29,    29,    17,    18,    18,    18,    19,    20,    21,
    26,    26,    78,    78,    28,    28,    27,    27,    77,    77,
    22,    22,    23,    23,    24,   148,    24,   149,    24,    59,
    59,    59,    59,     4,     3,     3,     3,     3,    31,    30,
    30,    30,    30,     1,     1,     1,     1,     1,     1,     1,
     1,     1,     1,     1,     1,    53,    99,    60,    60,    51,
   150,    51,    51,    62,    62,    61,    61,    61,    61,    61,
    61,    61,    61,    61,    61,    61,    61,    61,    61,    61,
   116,   116,   116,   116,    11,    11,   100,   100,    79,    79,
    57,   106,    87,    87,    80,    80,    13,    13,    15,    15,
    14,    14,    92,    91,    91,    16,   151,    16,    85,    85,
    83,    83,    84,    84,     5,     5,     5,     6,     6,     6,
     6,     7,     7,     7,    12,    12,   118,   118,   124,   124,
   113,   114,   125,   125,   125,   137,   137,   119,   119,    75,
    90,
    }, yyLen = {
//yyLen 541
     2,     0,     2,     4,     2,     1,     1,     3,     2,     0,
     4,     3,     3,     3,     2,     3,     3,     3,     3,     3,
     0,     5,     4,     3,     3,     3,     6,     5,     5,     5,
     3,     3,     3,     3,     1,     1,     3,     3,     2,     2,
     1,     1,     1,     1,     2,     2,     2,     1,     4,     4,
     0,     5,     2,     3,     4,     5,     4,     5,     2,     2,
     1,     3,     1,     3,     1,     2,     3,     5,     2,     4,
     2,     4,     1,     3,     1,     3,     2,     3,     1,     3,
     1,     4,     3,     3,     3,     3,     2,     1,     1,     4,
     3,     3,     3,     3,     2,     1,     1,     1,     2,     1,
     3,     1,     1,     1,     1,     1,     1,     1,     1,     1,
     1,     0,     4,     1,     1,     1,     1,     1,     1,     1,
     1,     1,     1,     1,     1,     1,     1,     1,     1,     1,
     1,     1,     1,     1,     1,     1,     1,     1,     1,     1,
     1,     1,     1,     1,     1,     1,     1,     1,     1,     1,
     1,     1,     1,     1,     1,     1,     1,     1,     1,     1,
     1,     1,     1,     1,     1,     1,     1,     1,     1,     1,
     1,     1,     1,     1,     1,     1,     1,     1,     1,     1,
     1,     1,     1,     1,     3,     5,     3,     5,     6,     5,
     5,     5,     5,     4,     3,     3,     3,     3,     3,     3,
     3,     3,     3,     4,     4,     2,     2,     3,     3,     3,
     3,     3,     3,     3,     3,     3,     3,     3,     3,     3,
     2,     2,     3,     3,     3,     3,     3,     6,     1,     1,
     1,     2,     4,     2,     3,     1,     1,     1,     1,     1,
     2,     2,     4,     1,     0,     2,     2,     2,     1,     1,
     2,     3,     4,     3,     4,     2,     1,     1,     1,     1,
     1,     1,     1,     1,     1,     3,     0,     4,     3,     3,
     2,     3,     3,     1,     4,     3,     1,     5,     4,     3,
     2,     1,     2,     2,     6,     6,     0,     0,     7,     0,
     0,     7,     5,     4,     0,     0,     9,     0,     6,     0,
     0,     8,     0,     5,     0,     6,     0,     0,     9,     1,
     1,     1,     1,     1,     1,     1,     2,     1,     1,     1,
     5,     1,     2,     1,     1,     1,     3,     1,     3,     1,
     4,     6,     3,     5,     2,     4,     1,     3,     6,     8,
     4,     6,     4,     2,     6,     2,     4,     6,     2,     4,
     2,     4,     1,     1,     1,     3,     1,     4,     1,     2,
     1,     3,     1,     1,     0,     3,     4,     2,     3,     3,
     0,     5,     2,     4,     4,     2,     4,     4,     3,     3,
     3,     2,     1,     4,     0,     5,     0,     5,     5,     1,
     1,     6,     0,     1,     1,     1,     2,     1,     2,     1,
     1,     1,     1,     1,     1,     1,     2,     3,     3,     3,
     3,     3,     0,     3,     1,     2,     3,     3,     0,     3,
     0,     2,     0,     2,     1,     0,     3,     0,     4,     1,
     1,     1,     1,     2,     1,     1,     1,     1,     3,     1,
     1,     2,     2,     1,     1,     1,     1,     1,     1,     1,
     1,     1,     1,     1,     1,     1,     1,     1,     1,     1,
     0,     4,     2,     3,     2,     6,     8,     4,     6,     4,
     6,     2,     4,     6,     2,     4,     2,     4,     1,     0,
     1,     1,     1,     1,     1,     1,     1,     3,     1,     3,
     3,     3,     1,     3,     1,     3,     1,     1,     2,     1,
     1,     1,     2,     2,     0,     1,     0,     4,     1,     2,
     1,     3,     3,     2,     1,     1,     1,     1,     1,     1,
     1,     1,     1,     1,     1,     1,     0,     1,     0,     1,
     2,     2,     0,     1,     1,     1,     1,     1,     2,     0,
     0,
    }, yyDefRed = {
//yyDefRed 945
     1,     0,     0,     0,     0,     0,     0,     0,     0,     0,
     0,     0,   286,   289,     0,     0,     0,   311,   312,     0,
     0,     0,   449,   448,   450,   451,     0,     0,     0,    20,
     0,   453,   452,   454,     0,     0,   445,   444,     0,   447,
   404,     0,     0,     0,     0,     0,     0,     0,     0,     0,
     0,     0,     0,   420,   422,   422,     0,     0,   364,   457,
   458,   439,   440,     0,   401,     0,   257,     0,   405,   258,
   259,     0,   260,   261,   256,   400,   402,    35,     2,     0,
     0,     0,     0,     0,     0,     0,   262,     0,    43,     0,
     0,    74,     0,     5,     0,     0,    60,     0,     0,   309,
   310,   273,     0,     0,     0,     0,     0,     0,     0,     0,
     0,     0,   455,     0,    99,     0,   313,     0,   263,   302,
   152,   163,   153,   176,   149,   169,   159,   158,   174,   157,
   156,   151,   177,   161,   150,   164,   168,   170,   162,   155,
   171,   178,   173,     0,     0,     0,     0,   148,   167,   166,
   179,   180,   181,   182,   183,   147,   154,   145,   146,     0,
     0,     0,     0,   103,     0,   137,   138,   134,   116,   117,
   118,   125,   122,   124,   119,   120,   139,   140,   126,   127,
   506,   131,   130,   115,   136,   133,   132,   128,   129,   123,
   121,   113,   135,   114,   141,   304,   104,     0,   505,   105,
   172,   165,   175,   160,   142,   143,   144,   101,   102,   110,
   107,   106,   109,     0,   108,     0,     0,     0,     0,     0,
     0,     0,     0,     0,     0,     0,   535,   536,     0,     0,
     0,   537,     0,     0,     0,     0,     0,     0,   323,   324,
     0,     0,     0,     0,     0,     0,   239,    45,     0,     0,
     0,   510,   243,    46,    44,     0,    59,     0,     0,   381,
    58,     0,    38,     0,     9,   529,     0,     0,     0,   205,
     0,     0,     0,     0,     0,     0,     0,     0,     0,     0,
     0,   230,     0,     0,     0,   508,     0,     0,     0,     0,
     0,     0,     0,     0,   221,    39,   220,   436,   435,   437,
   433,   434,     0,     0,     0,     0,     0,     0,     0,     0,
   283,     0,   386,   384,   375,     0,   280,   406,   282,     4,
     0,     0,     0,     0,     0,     0,     0,     0,     0,     0,
     0,     0,     0,     0,     0,     0,     0,     0,     0,     0,
     0,     0,     0,     0,     0,     0,     0,     0,     0,     0,
     0,     0,     0,     0,   370,   372,     0,     0,     0,     0,
     0,     0,    76,     0,     0,     0,     0,     0,     0,     0,
   441,   442,     0,    96,     0,    98,     0,   460,   297,   459,
     0,     0,     0,     0,     0,     0,   524,   525,   306,   111,
     0,     0,   265,     0,   315,     0,     0,     0,     0,     0,
     0,     0,     0,     0,     0,     0,     0,   538,     0,     0,
     0,     0,     0,     0,   294,   513,   250,   246,     0,     0,
   240,   248,     0,   241,     0,   275,     0,   245,   238,   237,
     0,     0,   279,    11,    13,    12,     0,     0,     0,     0,
     0,     0,     0,     0,     0,     0,     0,   268,     0,     0,
   271,     0,   533,   231,     0,   233,   509,   272,     0,    78,
     0,     0,     0,     0,     0,   427,   425,   438,   424,   423,
   407,   421,   408,   409,   410,   411,   414,     0,   416,   417,
     0,     0,   482,   481,   480,   483,     0,     0,   497,   496,
   501,   500,   486,     0,     0,     0,   494,     0,     0,     0,
     0,   478,   488,   484,     0,     0,    50,    53,     0,    15,
    16,    17,    18,    19,    36,    37,     0,     0,     0,     0,
     0,     0,     0,     0,     0,     0,     0,     0,     0,     0,
     0,     0,     0,     0,     0,     0,     0,     0,     0,     0,
     0,     0,     0,     0,   519,     0,     0,   520,   379,     0,
     0,     0,     0,   378,     0,   380,     0,   517,   518,     0,
     0,    30,     0,     0,    23,     0,    31,   249,     0,     0,
     0,     0,    77,    24,    33,     0,    25,     0,     0,   462,
     0,     0,     0,     0,     0,     0,   100,     0,     0,     0,
     0,     0,     0,     0,     0,   394,     0,     0,   395,     0,
     0,   321,     0,   316,     0,     0,     0,     0,     0,     0,
     0,     0,     0,   293,   318,   287,   317,   290,     0,     0,
     0,     0,     0,     0,   512,     0,     0,     0,   247,   511,
   274,   530,   234,   278,    10,     0,     0,    22,     0,     0,
     0,     0,   267,     0,     0,     0,     0,     0,     0,     0,
     0,     0,     0,   413,   415,   419,     0,   485,     0,     0,
   325,     0,   327,     0,     0,   498,   502,     0,   476,     0,
   358,   367,     0,     0,   365,     0,   471,     0,   474,   356,
     0,   354,     0,   353,     0,     0,     0,     0,     0,     0,
   236,     0,   376,   235,     0,     0,   377,     0,     0,     0,
    48,   373,    49,   374,     0,     0,     0,    75,     0,     0,
     0,   300,     0,     0,   383,   303,   507,     0,   464,     0,
   307,   112,     0,     0,   397,   322,     0,     3,   399,     0,
   319,     0,     0,     0,     0,     0,     0,   292,     0,     0,
     0,     0,     0,     0,   252,   242,   277,    21,   232,    79,
     0,     0,   429,   430,   431,   426,   432,   490,     0,     0,
     0,     0,   487,     0,     0,   503,   362,     0,   360,   363,
     0,     0,     0,     0,   489,     0,   495,     0,     0,     0,
     0,     0,     0,   352,     0,   492,     0,     0,     0,     0,
     0,    27,     0,    28,     0,    55,    29,     0,     0,    57,
     0,   531,     0,     0,     0,     0,     0,     0,   461,   298,
   463,   305,     0,     0,     0,     0,     0,   396,     0,   398,
     0,   284,     0,   285,   251,     0,     0,     0,   295,   428,
   326,     0,     0,     0,   328,   366,     0,   477,     0,   369,
   368,     0,   469,     0,   467,     0,   472,   475,     0,     0,
   350,     0,     0,   345,     0,   348,   355,   387,   385,     0,
     0,   371,    26,     0,     0,     0,     0,     0,     0,     0,
     0,     0,     0,   389,   388,   390,   288,   291,     0,     0,
     0,     0,     0,   361,     0,     0,     0,     0,     0,     0,
     0,   357,     0,     0,     0,     0,   493,    51,   301,     0,
     0,     0,     0,     0,     0,   391,     0,     0,     0,     0,
   470,     0,   465,   468,   473,   270,     0,   351,     0,   342,
     0,   340,     0,   346,   349,   308,     0,   320,   296,     0,
     0,     0,     0,     0,     0,     0,     0,   466,   344,     0,
   338,   341,   347,     0,   339,
    }, yyDgoto = {
//yyDgoto 152
     1,   218,   209,   300,    64,   113,   585,   553,   114,   211,
   547,   492,   388,   493,   494,   495,   197,    66,    67,    68,
    69,    70,   303,   302,   469,    71,    72,    73,   477,    74,
    75,    76,   115,    77,   215,   216,    79,    80,    81,    82,
    83,    84,   220,   270,   730,   874,   731,   723,   428,   727,
   555,   378,   256,    86,   692,    87,    88,   496,   213,   755,
   222,   591,   592,   498,   780,   681,   682,   566,    90,    91,
   248,   406,   597,   280,   223,    93,   249,   309,   307,   499,
   500,   661,    94,   250,   251,   287,   460,   782,   420,   252,
   421,   668,   765,   316,   355,   507,    95,    96,   391,   224,
   502,   767,   671,   674,   310,   278,   785,   240,   430,   662,
   663,   768,   214,   425,   698,   199,   503,     2,   229,   230,
   436,   267,   685,   594,   426,   453,   257,   449,   395,   232,
   615,   740,   233,   741,   623,   878,   581,   396,   578,   807,
   383,   385,   593,   812,   311,   542,   505,   504,   652,   651,
   580,   384,
    }, yySindex = {
//yySindex 945
     0,     0, 14162, 14409, 16746, 17115, 17823, 17715, 14162, 16377,
 16377, 12613,     0,     0, 16869, 14532, 14532,     0,     0, 14532,
  -250,  -238,     0,     0,     0,     0, 15270, 17607,   133,     0,
  -201,     0,     0,     0,     0,     0,     0,     0,     0,     0,
     0, 16500, 16500,  -124,  -141, 14286, 16377, 14901, 15393,  5733,
 16500, 16623, 17930,     0,     0,     0,   185,   262,     0,     0,
     0,     0,     0,     0,     0,   -45,     0,   -48,     0,     0,
     0,  -188,     0,     0,     0,     0,     0,     0,     0,   216,
   740,   -22,  4610,     0,    58,   -44,     0,  -224,     0,    11,
   313,     0,   312,     0, 16992,   393,     0,   176,   740,     0,
     0,     0,  -250,  -238,   165,   133,     0,     0,  -101, 16377,
    54, 14162,     0,   -45,     0,     7,     0,   -32,     0,     0,
     0,     0,     0,     0,     0,     0,     0,     0,     0,     0,
     0,     0,     0,     0,     0,     0,     0,     0,     0,     0,
     0,     0,     0,     0,     0,     0,     0,     0,     0,     0,
     0,     0,     0,     0,     0,     0,     0,     0,     0,     0,
     0,     0,     0,     0,     0,     0,     0,     0,     0,     0,
     0,     0,     0,     0,     0,     0,     0,     0,     0,     0,
     0,     0,     0,     0,     0,     0,     0,     0,     0,     0,
     0,     0,     0,     0,     0,     0,     0,   -15,     0,     0,
     0,     0,     0,     0,     0,     0,     0,     0,     0,     0,
     0,     0,     0,   472,     0,   264,   269,   236,     0,   -22,
    55,   219,   225,   512,   242,    55,     0,     0,   216,   323,
   540,     0, 16377, 16377,   297,     0,   475,     0,     0,     0,
   340, 16500, 16500, 16500, 16500,  4610,     0,     0,   284,   585,
   591,     0,     0,     0,     0,  3181,     0, 14532, 14532,     0,
     0,  4167,     0,   -74,     0,     0, 15516,   288, 14162,     0,
   477,   350,   351,   353,   342, 14286,   337,     0,   133,   -22,
   338,     0,   119,   289,   284,     0,   289,   327,   387, 17238,
     0,   509,     0,   657,     0,     0,     0,     0,     0,     0,
     0,     0,   -90,   444,   542,   273,   331,   554,   347,   171,
     0,  2130,     0,     0,     0,   366,     0,     0,     0,     0,
 14038, 16377, 16377, 16377, 16377, 14409, 16377, 16377, 16500, 16500,
 16500, 16500, 16500, 16500, 16500, 16500, 16500, 16500, 16500, 16500,
 16500, 16500, 16500, 16500, 16500, 16500, 16500, 16500, 16500, 16500,
 16500, 16500, 16500, 16500,     0,     0,  2198,  2643, 14532, 18477,
 18477, 16623,     0, 15639, 14286, 13915,   681, 15639, 16623,   391,
     0,     0,   -22,     0,     0,     0,   216,     0,     0,     0,
  3666,  4740, 14532, 14162, 16377,  2263,     0,     0,     0,     0,
 15762,   467,     0,   342,     0, 14162,   470,  5233,  6353, 14532,
 16500, 16500, 16500, 14162,   323, 15885,   474,     0,    38,    38,
     0, 18092, 18147, 14532,     0,     0,     0,     0, 16500, 14655,
     0,     0, 15024,     0,   133,     0,   398,     0,     0,     0,
   133,    46,     0,     0,     0,     0, 17715, 16377,  4610, 14162,
   386,  5233,  6353, 16500, 16500, 16500,   133,     0,     0,   133,
     0, 15147,     0,     0, 15393,     0,     0,     0,     0,     0,
   715, 18202, 18257, 14532, 17238,     0,     0,     0,     0,     0,
     0,     0,     0,     0,     0,     0,     0,   101,     0,     0,
   735,   701,     0,     0,     0,     0,  1160,  1671,     0,     0,
     0,     0,     0,   462,   476,   739,     0,   725,  -121,   741,
   744,     0,     0,     0,  -183,  -183,     0,     0,   740,     0,
     0,     0,     0,     0,     0,     0,   350,  2770,  2770,  2770,
  2770,  1218,  1218,  4244,  3714,  2770,  2770,  2728,  2728,   728,
   728,   350,  1556,   350,   350,   354,   354,  1218,  1218,  1452,
  1452,  2588,  -183,   459,     0,   460,  -238,     0,     0,   461,
     0,   481,  -238,     0,     0,     0,   133,     0,     0,  -238,
  -238,     0,  4610, 16500,     0,  4679,     0,     0,   755,   133,
 17238,   775,     0,     0,     0,     0,     0,  5170,   216,     0,
 16377, 14162,  -238,     0,     0,  -238,     0,   133,   562,    46,
  1671,   216, 14162, 18037, 17715,     0,     0,   498,     0, 14162,
   575,     0,   341,     0,   517,   519,   520,   481,   133,  4679,
   467,   584,    78,     0,     0,     0,     0,     0,     0,     0,
     0,     0,   133, 16377,     0, 16500,   284,   591,     0,     0,
     0,     0,     0,     0,     0,    46,   496,     0,   350,   350,
  4610,     0,     0,   289, 17238,     0,     0,     0,     0,   133,
   715, 14162,   376,     0,     0,     0, 16500,     0,  1160,   650,
     0,   818,     0,   133,   725,     0,     0,   889,     0,  1023,
     0,     0, 14162, 14162,     0,  1671,     0,  1671,     0,     0,
  1751,     0, 14162,     0, 14162,  -183,   817, 14162, 16623, 16623,
     0,   366,     0,     0, 16623, 16500,     0,   366,   541,   546,
     0,     0,     0,     0,     0, 16500, 16008,     0,   715, 17238,
 16500,     0,   216,   621,     0,     0,     0,   133,     0,   628,
     0,     0, 17361,    55,     0,     0, 14162,     0,     0, 16377,
     0,   633, 16500, 16500, 16500,   567,   641,     0, 16131, 14162,
 14162, 14162,     0,    38,     0,     0,     0,     0,     0,     0,
     0,   549,     0,     0,     0,     0,     0,     0,   133,  1628,
   864,  1615,     0,   133,   871,     0,     0,   877,     0,     0,
   664,   574,   896,   902,     0,   903,     0,   871,   872,   904,
   725,   906,   907,     0,   594,     0,   691,   600, 14162, 16500,
   700,     0,  4610,     0,  4610,     0,     0,  4610,  4610,     0,
 16623,     0,  4610, 16500,     0,   715,  4610, 14162,     0,     0,
     0,     0,  2263,   656,     0,   532,     0,     0, 14162,     0,
    55,     0, 16500,     0,     0,    36,   706,   708,     0,     0,
     0,   930,  1628,   679,     0,     0,   889,     0,  1023,     0,
     0,   889,     0,  1671,     0,   889,     0,     0, 17484,   889,
     0,   618,  2281,     0,  2281,     0,     0,     0,     0,   616,
  4610,     0,     0,  4610,     0,   717, 14162,     0, 18312, 18367,
 14532,   264, 14162,     0,     0,     0,     0,     0, 14162,  1628,
   930,  1628,   935,     0,   871,   938,   871,   871,   673,   560,
   871,     0,   947,   952,   953,   871,     0,     0,     0,   737,
     0,     0,     0,     0,   133,     0,   341,   743,   930,  1628,
     0,   889,     0,     0,     0,     0, 18422,     0,   889,     0,
  2281,     0,   889,     0,     0,     0,     0,     0,     0,   930,
   871,     0,     0,   871,   964,   871,   871,     0,     0,   889,
     0,     0,     0,   871,     0,
    }, yyRindex = {
//yyRindex 945
     0,     0,   168,     0,     0,     0,     0,     0,   294,     0,
     0,   742,     0,     0,     0, 12951, 13057,     0,     0, 13199,
  4495,  4002,     0,     0,     0,     0,     0,     0, 16254,     0,
     0,     0,     0,     0,  1907,  3016,     0,     0,  2030,     0,
     0,     0,     0,     0,     0,   136,     0,   666,   649,    42,
     0,     0,   698,     0,     0,     0,   797,   214,     0,     0,
     0,     0,     0, 13302,     0, 14778,     0,  6717,     0,     0,
     0,  6818,     0,     0,     0,     0,     0,     0,     0,   370,
   761, 11139,  1251,  6962,  1587,     0,     0, 13765,     0, 13416,
     0,     0,     0,     0,    63,     0,     0,     0,  1006,     0,
     0,     0,  7066,  6023,     0,   675, 11578, 11704,     0,     0,
     0,   136,     0,     0,     0,     0,     0,     0,     0,     0,
     0,     0,     0,     0,     0,     0,     0,     0,     0,     0,
     0,     0,     0,     0,     0,     0,     0,     0,     0,     0,
     0,     0,     0,  1424,  2146,  2269,  2762,     0,     0,     0,
     0,     0,     0,     0,     0,     0,     0,     0,     0,  3255,
  3606,  3748,  4241,     0,  5076,     0,     0,     0,     0,     0,
     0,     0,     0,     0,     0,     0,     0,     0,     0,     0,
     0,     0,     0,     0,     0,     0,     0,     0,     0,     0,
     0,     0,     0,     0,     0,     0,     0,     0,     0,     0,
     0,     0,     0,     0,     0,     0,     0,     0,     0,     0,
     0,     0,     0, 12674,     0,   362,     0,     0,  6137,  5102,
     0,     0,  6469,     0,     0,     0,     0,     0,   742,     0,
   751,     0,     0,     0,     0,   511,     0,   836,     0,     0,
     0,     0,     0,     0,     0,  1651,     0,     0, 13661,  1787,
  1787,     0,     0,     0,     0,   686,     0,     0,    35,     0,
     0,   686,     0,     0,     0,     0,     0,     0,    28,     0,
     0,  7427,  7179,  7311, 13550,   136,     0,   120,   686,   129,
     0,     0,   684,   684,     0,     0,   677,     0,     0,     0,
  1557,     0,  1559,    64,     0,     0,     0,     0,     0,     0,
     0,     0,     0,     0,     0,     0,     0,     0,     0,     0,
     0,    53,     0,     0,     0,   843,     0,     0,     0,     0,
   446,     0,     0,     0,     0,     0,     0,     0,     0,     0,
     0,     0,     0,     0,     0,     0,     0,     0,     0,     0,
     0,     0,     0,     0,     0,     0,     0,     0,     0,     0,
     0,     0,     0,     0,     0,     0,     0,     0,     6,     0,
     0,     0,     0,     0,   136,   116,   139,     0,     0,     0,
     0,     0,   233,     0, 12094,     0,     0,     0,     0,     0,
     0,     0,     6,   294,     0,   241,     0,     0,     0,     0,
    51,   383,     0,  6603,     0,   730, 12220,     0,     0,     6,
     0,     0,     0,    75,     0,     0,     0,     0,     0,     0,
   847,     0,     0,     6,     0,     0,     0,     0,     0,     0,
     0,     0,     0,     0,   686,     0,     0,     0,     0,     0,
   686,   686,     0,     0,     0,     0,     0,     0, 10403,    28,
     0,     0,     0,     0,     0,     0,   686,     0,   169,   686,
     0,   702,     0,     0,   -77,     0,     0,     0,  1774,     0,
   149,     0,     0,     6,     0,     0,     0,     0,     0,     0,
     0,     0,     0,     0,     0,     0,     0,     0,     0,     0,
     0,    79,     0,     0,     0,     0,     0,   140,     0,     0,
     0,     0,     0,   117,     0,    50,     0,   -91,     0,    50,
    50,     0,     0,     0, 12352, 12489,     0,     0,  1144,     0,
     0,     0,     0,     0,     0,     0,  7528,  9492,  9614,  9732,
  9829,  9037,  9157,  9915, 10188, 10005, 10102, 10278, 10318,  8457,
  8579,  7643,  8702,  7776,  7891,  8240,  8353,  9277,  9374,  8820,
  8928,   986, 12352,  4856,     0,  4979,  4372,     0,     0,  5349,
  3386,  5472, 14778,     0,  3509,     0,   707,     0,     0,  5595,
  5595,     0, 10499,     0,     0, 12770,     0,     0,     0,   686,
     0,   154,     0,     0,     0, 10226,     0, 11395,     0,     0,
     0,   294,  6271, 11836, 11962,     0,     0,   707,     0,   686,
   141,     0,   294,     0,     0,     0,    47,   197,     0,   571,
   788,     0,   788,     0,  2400,  2523,  2893,  3879,   707, 11456,
   788,     0,     0,     0,     0,     0,     0,     0,   561,  1121,
  1650,   674,   707,     0,     0,     0, 13704,  1787,     0,     0,
     0,     0,     0,     0,     0,   686,     0,     0,  7992,  8108,
 10584,   121,     0,   684,     0,   781,  1149,  1222,  1468,   707,
   181,    28,     0,     0,     0,     0,     0,     0,     0,   142,
     0,   144,     0,   686,    35,     0,     0,     0,     0,     0,
     0,     0,    59,    28,     0,     0,     0,     0,     0,     0,
   695,     0,    59,     0,    28, 12489,     0,    59,     0,     0,
     0,  1363,     0,     0,     0,     0,     0,  4613, 12850,     0,
     0,     0,     0,     0, 11241,     0,     0,     0,   187,     0,
     0,     0,     0,     0,     0,     0,     0,   686,     0,     0,
     0,     0,     0,     0,     0,     0,    59,     0,     0,     0,
     0,     0,     0,     0,     0,  5922,     0,     0,     0,    85,
    59,    59,   850,     0,     0,     0,     0,     0,     0,     0,
  1499,     0,     0,     0,     0,     0,     0,     0,   686,     0,
   146,     0,     0,   686,    50,     0,     0,   118,     0,     0,
     0,     0,    50,    50,     0,    50,     0,    50,   161,    82,
   695,    82,    82,     0,     0,     0,     0,     0,    28,     0,
     0,     0, 10645,     0, 10742,     0,     0, 10828, 10937,     0,
     0,     0, 10998,     0, 13805,   209, 11084,   294,     0,     0,
     0,     0,   241,     0,   736,     0,   764,     0,   294,     0,
     0,     0,     0,     0,     0,   788,     0,     0,     0,     0,
     0,   152,     0,   173,     0,     0,     0,     0,     0,     0,
     0,     0,     0,     0,     0,     0,     0,     0,     0,     0,
     0,     0,    86,     0,     0,     0,     0,     0,     0,     0,
 11181,     0,     0, 11305, 13822,     0,   294,   792,     0,     0,
     6,   362,   730,     0,     0,     0,     0,     0,    59,     0,
   184,     0,   188,     0,    50,    50,    50,    50,     0,   174,
    82,     0,    82,    82,    82,    82,     0,     0,     0,     0,
  1757,  1785,  2148,   614,   707,     0,   788,     0,   198,     0,
     0,     0,     0,     0,     0,     0,     0,     0,     0,     0,
     0,     0,     0,     0,     0,     0,   665,     0,     0,   201,
    50,   927,  1281,    82,    82,    82,    82,     0,     0,     0,
     0,     0,     0,    82,     0,
    }, yyGindex = {
//yyGindex 152
     0,   504,   -23,     0,    -5,   177,  -240,     0,   -47,    20,
    -6,  -312,     0,     0,     0,   620,     0,     0,     0,   987,
     0,     0,     0,   607,  -228,     0,     0,     0,     0,     0,
     0,    27,  1051,    33,   406,  -342,     0,   164,  1170,  1338,
    72,    16,    57,     4,  -386,     0,   160,     0,   680,     0,
   195,     0,    18,  1066,   105,     0,     0,  -599,     0,     0,
   655,  -249,   261,     0,     0,     0,  -370,  -126,   -43,   -13,
   799,  -375,     0,     0,    91,   759,   106,     0,     0,  5452,
   402,  -677,     0,   -37,  -197,     0,  -400,   232,  -208,   -87,
     0,   936,  -278,  1015,     0,  -439,  1073,   167,   218,   609,
  -605,     0,  -474,     0,     0,  -175,  -773,     0,  -355,  -613,
   433,   257,     0,   304,  -311,     0,  -598,     0,     1,  1018,
     0,     0,     0,     0,    -4,  -246,     0,     0,  -200,     0,
  -366,     0,     0,     0,     0,     0,     0,    17,     0,     0,
     0,     0,     0,     0,     0,     0,     0,     0,     0,     0,
     0,     0,
    };
    protected static final short[] yyTable = Ruby19YyTables.yyTable();
    protected static final short[] yyCheck = Ruby19YyTables.yyCheck();

  /** maps symbol value to printable name.
      @see #yyExpecting
    */
  protected static final String[] yyNames = {
    "end-of-file",null,null,null,null,null,null,null,null,null,"'\\n'",
    null,null,null,null,null,null,null,null,null,null,null,null,null,null,
    null,null,null,null,null,null,null,"' '",null,null,null,null,null,
    null,null,null,null,null,null,"','",null,null,null,null,null,null,
    null,null,null,null,null,null,null,"':'","';'",null,"'='",null,"'?'",
    null,null,null,null,null,null,null,null,null,null,null,null,null,null,
    null,null,null,null,null,null,null,null,null,null,null,null,null,
    "'['",null,null,null,null,null,null,null,null,null,null,null,null,
    null,null,null,null,null,null,null,null,null,null,null,null,null,null,
    null,null,null,null,null,null,null,null,null,null,null,null,null,null,
    null,null,null,null,null,null,null,null,null,null,null,null,null,null,
    null,null,null,null,null,null,null,null,null,null,null,null,null,null,
    null,null,null,null,null,null,null,null,null,null,null,null,null,null,
    null,null,null,null,null,null,null,null,null,null,null,null,null,null,
    null,null,null,null,null,null,null,null,null,null,null,null,null,null,
    null,null,null,null,null,null,null,null,null,null,null,null,null,null,
    null,null,null,null,null,null,null,null,null,null,null,null,null,null,
    null,null,null,null,null,null,null,null,null,null,null,null,null,null,
    null,null,null,null,null,null,null,null,null,null,null,null,null,
    "kCLASS","kMODULE","kDEF","kUNDEF","kBEGIN","kRESCUE","kENSURE",
    "kEND","kIF","kUNLESS","kTHEN","kELSIF","kELSE","kCASE","kWHEN",
    "kWHILE","kUNTIL","kFOR","kBREAK","kNEXT","kREDO","kRETRY","kIN",
    "kDO","kDO_COND","kDO_BLOCK","kRETURN","kYIELD","kSUPER","kSELF",
    "kNIL","kTRUE","kFALSE","kAND","kOR","kNOT","kIF_MOD","kUNLESS_MOD",
    "kWHILE_MOD","kUNTIL_MOD","kRESCUE_MOD","kALIAS","kDEFINED","klBEGIN",
    "klEND","k__LINE__","k__FILE__","k__ENCODING__","kDO_LAMBDA",
    "tIDENTIFIER","tFID","tGVAR","tIVAR","tCONSTANT","tCVAR","tLABEL",
    "tCHAR","tUPLUS","tUMINUS","tUMINUS_NUM","tPOW","tCMP","tEQ","tEQQ",
    "tNEQ","tGEQ","tLEQ","tANDOP","tOROP","tMATCH","tNMATCH","tDOT",
    "tDOT2","tDOT3","tAREF","tASET","tLSHFT","tRSHFT","tCOLON2","tCOLON3",
    "tOP_ASGN","tASSOC","tLPAREN","tLPAREN2","tRPAREN","tLPAREN_ARG",
    "tLBRACK","tRBRACK","tLBRACE","tLBRACE_ARG","tSTAR","tSTAR2","tAMPER",
    "tAMPER2","tTILDE","tPERCENT","tDIVIDE","tPLUS","tMINUS","tLT","tGT",
    "tPIPE","tBANG","tCARET","tLCURLY","tRCURLY","tBACK_REF2","tSYMBEG",
    "tSTRING_BEG","tXSTRING_BEG","tREGEXP_BEG","tWORDS_BEG","tQWORDS_BEG",
    "tSTRING_DBEG","tSTRING_DVAR","tSTRING_END","tLAMBDA","tLAMBEG",
    "tNTH_REF","tBACK_REF","tSTRING_CONTENT","tINTEGER","tFLOAT",
    "tREGEXP_END","tLOWEST",
    };

  /** printable rules for debugging.
    */
  protected static final String [] yyRule = {
    "$accept : program",
    "$$1 :",
    "program : $$1 compstmt",
    "bodystmt : compstmt opt_rescue opt_else opt_ensure",
    "compstmt : stmts opt_terms",
    "stmts : none",
    "stmts : stmt",
    "stmts : stmts terms stmt",
    "stmts : error stmt",
    "$$2 :",
    "stmt : kALIAS fitem $$2 fitem",
    "stmt : kALIAS tGVAR tGVAR",
    "stmt : kALIAS tGVAR tBACK_REF",
    "stmt : kALIAS tGVAR tNTH_REF",
    "stmt : kUNDEF undef_list",
    "stmt : stmt kIF_MOD expr_value",
    "stmt : stmt kUNLESS_MOD expr_value",
    "stmt : stmt kWHILE_MOD expr_value",
    "stmt : stmt kUNTIL_MOD expr_value",
    "stmt : stmt kRESCUE_MOD stmt",
    "$$3 :",
    "stmt : klBEGIN $$3 tLCURLY compstmt tRCURLY",
    "stmt : klEND tLCURLY compstmt tRCURLY",
    "stmt : lhs '=' command_call",
    "stmt : mlhs '=' command_call",
    "stmt : var_lhs tOP_ASGN command_call",
    "stmt : primary_value '[' opt_call_args rbracket tOP_ASGN command_call",
    "stmt : primary_value tDOT tIDENTIFIER tOP_ASGN command_call",
    "stmt : primary_value tDOT tCONSTANT tOP_ASGN command_call",
    "stmt : primary_value tCOLON2 tIDENTIFIER tOP_ASGN command_call",
    "stmt : backref tOP_ASGN command_call",
    "stmt : lhs '=' mrhs",
    "stmt : mlhs '=' arg_value",
    "stmt : mlhs '=' mrhs",
    "stmt : expr",
    "expr : command_call",
    "expr : expr kAND expr",
    "expr : expr kOR expr",
    "expr : kNOT expr",
    "expr : tBANG command_call",
    "expr : arg",
    "expr_value : expr",
    "command_call : command",
    "command_call : block_command",
    "command_call : kRETURN call_args",
    "command_call : kBREAK call_args",
    "command_call : kNEXT call_args",
    "block_command : block_call",
    "block_command : block_call tDOT operation2 command_args",
    "block_command : block_call tCOLON2 operation2 command_args",
    "$$4 :",
    "cmd_brace_block : tLBRACE_ARG $$4 opt_block_param compstmt tRCURLY",
    "command : operation command_args",
    "command : operation command_args cmd_brace_block",
    "command : primary_value tDOT operation2 command_args",
    "command : primary_value tDOT operation2 command_args cmd_brace_block",
    "command : primary_value tCOLON2 operation2 command_args",
    "command : primary_value tCOLON2 operation2 command_args cmd_brace_block",
    "command : kSUPER command_args",
    "command : kYIELD command_args",
    "mlhs : mlhs_basic",
    "mlhs : tLPAREN mlhs_inner rparen",
    "mlhs_inner : mlhs_basic",
    "mlhs_inner : tLPAREN mlhs_inner rparen",
    "mlhs_basic : mlhs_head",
    "mlhs_basic : mlhs_head mlhs_item",
    "mlhs_basic : mlhs_head tSTAR mlhs_node",
    "mlhs_basic : mlhs_head tSTAR mlhs_node ',' mlhs_post",
    "mlhs_basic : mlhs_head tSTAR",
    "mlhs_basic : mlhs_head tSTAR ',' mlhs_post",
    "mlhs_basic : tSTAR mlhs_node",
    "mlhs_basic : tSTAR mlhs_node ',' mlhs_post",
    "mlhs_basic : tSTAR",
    "mlhs_basic : tSTAR ',' mlhs_post",
    "mlhs_item : mlhs_node",
    "mlhs_item : tLPAREN mlhs_inner rparen",
    "mlhs_head : mlhs_item ','",
    "mlhs_head : mlhs_head mlhs_item ','",
    "mlhs_post : mlhs_item",
    "mlhs_post : mlhs_post ',' mlhs_item",
    "mlhs_node : variable",
    "mlhs_node : primary_value '[' opt_call_args rbracket",
    "mlhs_node : primary_value tDOT tIDENTIFIER",
    "mlhs_node : primary_value tCOLON2 tIDENTIFIER",
    "mlhs_node : primary_value tDOT tCONSTANT",
    "mlhs_node : primary_value tCOLON2 tCONSTANT",
    "mlhs_node : tCOLON3 tCONSTANT",
    "mlhs_node : backref",
    "lhs : variable",
    "lhs : primary_value '[' opt_call_args rbracket",
    "lhs : primary_value tDOT tIDENTIFIER",
    "lhs : primary_value tCOLON2 tIDENTIFIER",
    "lhs : primary_value tDOT tCONSTANT",
    "lhs : primary_value tCOLON2 tCONSTANT",
    "lhs : tCOLON3 tCONSTANT",
    "lhs : backref",
    "cname : tIDENTIFIER",
    "cname : tCONSTANT",
    "cpath : tCOLON3 cname",
    "cpath : cname",
    "cpath : primary_value tCOLON2 cname",
    "fname : tIDENTIFIER",
    "fname : tCONSTANT",
    "fname : tFID",
    "fname : op",
    "fname : reswords",
    "fsym : fname",
    "fsym : symbol",
    "fitem : fsym",
    "fitem : dsym",
    "undef_list : fitem",
    "$$5 :",
    "undef_list : undef_list ',' $$5 fitem",
    "op : tPIPE",
    "op : tCARET",
    "op : tAMPER2",
    "op : tCMP",
    "op : tEQ",
    "op : tEQQ",
    "op : tMATCH",
    "op : tNMATCH",
    "op : tGT",
    "op : tGEQ",
    "op : tLT",
    "op : tLEQ",
    "op : tNEQ",
    "op : tLSHFT",
    "op : tRSHFT",
    "op : tPLUS",
    "op : tMINUS",
    "op : tSTAR2",
    "op : tSTAR",
    "op : tDIVIDE",
    "op : tPERCENT",
    "op : tPOW",
    "op : tBANG",
    "op : tTILDE",
    "op : tUPLUS",
    "op : tUMINUS",
    "op : tAREF",
    "op : tASET",
    "op : tBACK_REF2",
    "reswords : k__LINE__",
    "reswords : k__FILE__",
    "reswords : k__ENCODING__",
    "reswords : klBEGIN",
    "reswords : klEND",
    "reswords : kALIAS",
    "reswords : kAND",
    "reswords : kBEGIN",
    "reswords : kBREAK",
    "reswords : kCASE",
    "reswords : kCLASS",
    "reswords : kDEF",
    "reswords : kDEFINED",
    "reswords : kDO",
    "reswords : kELSE",
    "reswords : kELSIF",
    "reswords : kEND",
    "reswords : kENSURE",
    "reswords : kFALSE",
    "reswords : kFOR",
    "reswords : kIN",
    "reswords : kMODULE",
    "reswords : kNEXT",
    "reswords : kNIL",
    "reswords : kNOT",
    "reswords : kOR",
    "reswords : kREDO",
    "reswords : kRESCUE",
    "reswords : kRETRY",
    "reswords : kRETURN",
    "reswords : kSELF",
    "reswords : kSUPER",
    "reswords : kTHEN",
    "reswords : kTRUE",
    "reswords : kUNDEF",
    "reswords : kWHEN",
    "reswords : kYIELD",
    "reswords : kIF_MOD",
    "reswords : kUNLESS_MOD",
    "reswords : kWHILE_MOD",
    "reswords : kUNTIL_MOD",
    "reswords : kRESCUE_MOD",
    "arg : lhs '=' arg",
    "arg : lhs '=' arg kRESCUE_MOD arg",
    "arg : var_lhs tOP_ASGN arg",
    "arg : var_lhs tOP_ASGN arg kRESCUE_MOD arg",
    "arg : primary_value '[' opt_call_args rbracket tOP_ASGN arg",
    "arg : primary_value tDOT tIDENTIFIER tOP_ASGN arg",
    "arg : primary_value tDOT tCONSTANT tOP_ASGN arg",
    "arg : primary_value tCOLON2 tIDENTIFIER tOP_ASGN arg",
    "arg : primary_value tCOLON2 tCONSTANT tOP_ASGN arg",
    "arg : tCOLON3 tCONSTANT tOP_ASGN arg",
    "arg : backref tOP_ASGN arg",
    "arg : arg tDOT2 arg",
    "arg : arg tDOT3 arg",
    "arg : arg tPLUS arg",
    "arg : arg tMINUS arg",
    "arg : arg tSTAR2 arg",
    "arg : arg tDIVIDE arg",
    "arg : arg tPERCENT arg",
    "arg : arg tPOW arg",
    "arg : tUMINUS_NUM tINTEGER tPOW arg",
    "arg : tUMINUS_NUM tFLOAT tPOW arg",
    "arg : tUPLUS arg",
    "arg : tUMINUS arg",
    "arg : arg tPIPE arg",
    "arg : arg tCARET arg",
    "arg : arg tAMPER2 arg",
    "arg : arg tCMP arg",
    "arg : arg tGT arg",
    "arg : arg tGEQ arg",
    "arg : arg tLT arg",
    "arg : arg tLEQ arg",
    "arg : arg tEQ arg",
    "arg : arg tEQQ arg",
    "arg : arg tNEQ arg",
    "arg : arg tMATCH arg",
    "arg : arg tNMATCH arg",
    "arg : tBANG arg",
    "arg : tTILDE arg",
    "arg : arg tLSHFT arg",
    "arg : arg tRSHFT arg",
    "arg : arg tANDOP arg",
    "arg : arg tOROP arg",
    "arg : kDEFINED opt_nl arg",
    "arg : arg '?' arg opt_nl ':' arg",
    "arg : primary",
    "arg_value : arg",
    "aref_args : none",
    "aref_args : args trailer",
    "aref_args : args ',' assocs trailer",
    "aref_args : assocs trailer",
    "paren_args : tLPAREN2 opt_call_args rparen",
    "opt_paren_args : none",
    "opt_paren_args : paren_args",
    "opt_call_args : none",
    "opt_call_args : call_args",
    "call_args : command",
    "call_args : args opt_block_arg",
    "call_args : assocs opt_block_arg",
    "call_args : args ',' assocs opt_block_arg",
    "call_args : block_arg",
    "$$6 :",
    "command_args : $$6 call_args",
    "block_arg : tAMPER arg_value",
    "opt_block_arg : ',' block_arg",
    "opt_block_arg : none_block_pass",
    "args : arg_value",
    "args : tSTAR arg_value",
    "args : args ',' arg_value",
    "args : args ',' tSTAR arg_value",
    "mrhs : args ',' arg_value",
    "mrhs : args ',' tSTAR arg_value",
    "mrhs : tSTAR arg_value",
    "primary : literal",
    "primary : strings",
    "primary : xstring",
    "primary : regexp",
    "primary : words",
    "primary : qwords",
    "primary : var_ref",
    "primary : backref",
    "primary : tFID",
    "primary : kBEGIN bodystmt kEND",
    "$$7 :",
    "primary : tLPAREN_ARG expr $$7 rparen",
    "primary : tLPAREN compstmt tRPAREN",
    "primary : primary_value tCOLON2 tCONSTANT",
    "primary : tCOLON3 tCONSTANT",
    "primary : tLBRACK aref_args tRBRACK",
    "primary : tLBRACE assoc_list tRCURLY",
    "primary : kRETURN",
    "primary : kYIELD tLPAREN2 call_args rparen",
    "primary : kYIELD tLPAREN2 rparen",
    "primary : kYIELD",
    "primary : kDEFINED opt_nl tLPAREN2 expr rparen",
    "primary : kNOT tLPAREN2 expr rparen",
    "primary : kNOT tLPAREN2 rparen",
    "primary : operation brace_block",
    "primary : method_call",
    "primary : method_call brace_block",
    "primary : tLAMBDA lambda",
    "primary : kIF expr_value then compstmt if_tail kEND",
    "primary : kUNLESS expr_value then compstmt opt_else kEND",
    "$$8 :",
    "$$9 :",
    "primary : kWHILE $$8 expr_value do $$9 compstmt kEND",
    "$$10 :",
    "$$11 :",
    "primary : kUNTIL $$10 expr_value do $$11 compstmt kEND",
    "primary : kCASE expr_value opt_terms case_body kEND",
    "primary : kCASE opt_terms case_body kEND",
    "$$12 :",
    "$$13 :",
    "primary : kFOR for_var kIN $$12 expr_value do $$13 compstmt kEND",
    "$$14 :",
    "primary : kCLASS cpath superclass $$14 bodystmt kEND",
    "$$15 :",
    "$$16 :",
    "primary : kCLASS tLSHFT expr $$15 term $$16 bodystmt kEND",
    "$$17 :",
    "primary : kMODULE cpath $$17 bodystmt kEND",
    "$$18 :",
    "primary : kDEF fname $$18 f_arglist bodystmt kEND",
    "$$19 :",
    "$$20 :",
    "primary : kDEF singleton dot_or_colon $$19 fname $$20 f_arglist bodystmt kEND",
    "primary : kBREAK",
    "primary : kNEXT",
    "primary : kREDO",
    "primary : kRETRY",
    "primary_value : primary",
    "then : term",
    "then : kTHEN",
    "then : term kTHEN",
    "do : term",
    "do : kDO_COND",
    "if_tail : opt_else",
    "if_tail : kELSIF expr_value then compstmt if_tail",
    "opt_else : none",
    "opt_else : kELSE compstmt",
    "for_var : lhs",
    "for_var : mlhs",
    "f_marg : f_norm_arg",
    "f_marg : tLPAREN f_margs rparen",
    "f_marg_list : f_marg",
    "f_marg_list : f_marg_list ',' f_marg",
    "f_margs : f_marg_list",
    "f_margs : f_marg_list ',' tSTAR f_norm_arg",
    "f_margs : f_marg_list ',' tSTAR f_norm_arg ',' f_marg_list",
    "f_margs : f_marg_list ',' tSTAR",
    "f_margs : f_marg_list ',' tSTAR ',' f_marg_list",
    "f_margs : tSTAR f_norm_arg",
    "f_margs : tSTAR f_norm_arg ',' f_marg_list",
    "f_margs : tSTAR",
    "f_margs : tSTAR ',' f_marg_list",
    "block_param : f_arg ',' f_block_optarg ',' f_rest_arg opt_f_block_arg",
    "block_param : f_arg ',' f_block_optarg ',' f_rest_arg ',' f_arg opt_f_block_arg",
    "block_param : f_arg ',' f_block_optarg opt_f_block_arg",
    "block_param : f_arg ',' f_block_optarg ',' f_arg opt_f_block_arg",
    "block_param : f_arg ',' f_rest_arg opt_f_block_arg",
    "block_param : f_arg ','",
    "block_param : f_arg ',' f_rest_arg ',' f_arg opt_f_block_arg",
    "block_param : f_arg opt_f_block_arg",
    "block_param : f_block_optarg ',' f_rest_arg opt_f_block_arg",
    "block_param : f_block_optarg ',' f_rest_arg ',' f_arg opt_f_block_arg",
    "block_param : f_block_optarg opt_f_block_arg",
    "block_param : f_block_optarg ',' f_arg opt_f_block_arg",
    "block_param : f_rest_arg opt_f_block_arg",
    "block_param : f_rest_arg ',' f_arg opt_f_block_arg",
    "block_param : f_block_arg",
    "opt_block_param : none",
    "opt_block_param : block_param_def",
    "block_param_def : tPIPE opt_bv_decl tPIPE",
    "block_param_def : tOROP",
    "block_param_def : tPIPE block_param opt_bv_decl tPIPE",
    "opt_bv_decl : none",
    "opt_bv_decl : ';' bv_decls",
    "bv_decls : bvar",
    "bv_decls : bv_decls ',' bvar",
    "bvar : tIDENTIFIER",
    "bvar : f_bad_arg",
    "$$21 :",
    "lambda : $$21 f_larglist lambda_body",
    "f_larglist : tLPAREN2 f_args opt_bv_decl rparen",
    "f_larglist : f_args opt_bv_decl",
    "lambda_body : tLAMBEG compstmt tRCURLY",
    "lambda_body : kDO_LAMBDA compstmt kEND",
    "$$22 :",
    "do_block : kDO_BLOCK $$22 opt_block_param compstmt kEND",
    "block_call : command do_block",
    "block_call : block_call tDOT operation2 opt_paren_args",
    "block_call : block_call tCOLON2 operation2 opt_paren_args",
    "method_call : operation paren_args",
    "method_call : primary_value tDOT operation2 opt_paren_args",
    "method_call : primary_value tCOLON2 operation2 paren_args",
    "method_call : primary_value tCOLON2 operation3",
    "method_call : primary_value tDOT paren_args",
    "method_call : primary_value tCOLON2 paren_args",
    "method_call : kSUPER paren_args",
    "method_call : kSUPER",
    "method_call : primary_value '[' opt_call_args rbracket",
    "$$23 :",
    "brace_block : tLCURLY $$23 opt_block_param compstmt tRCURLY",
    "$$24 :",
    "brace_block : kDO $$24 opt_block_param compstmt kEND",
    "case_body : kWHEN args then compstmt cases",
    "cases : opt_else",
    "cases : case_body",
    "opt_rescue : kRESCUE exc_list exc_var then compstmt opt_rescue",
    "opt_rescue :",
    "exc_list : arg_value",
    "exc_list : mrhs",
    "exc_list : none",
    "exc_var : tASSOC lhs",
    "exc_var : none",
    "opt_ensure : kENSURE compstmt",
    "opt_ensure : none",
    "literal : numeric",
    "literal : symbol",
    "literal : dsym",
    "strings : string",
    "string : tCHAR",
    "string : string1",
    "string : string string1",
    "string1 : tSTRING_BEG string_contents tSTRING_END",
    "xstring : tXSTRING_BEG xstring_contents tSTRING_END",
    "regexp : tREGEXP_BEG xstring_contents tREGEXP_END",
    "words : tWORDS_BEG ' ' tSTRING_END",
    "words : tWORDS_BEG word_list tSTRING_END",
    "word_list :",
    "word_list : word_list word ' '",
    "word : string_content",
    "word : word string_content",
    "qwords : tQWORDS_BEG ' ' tSTRING_END",
    "qwords : tQWORDS_BEG qword_list tSTRING_END",
    "qword_list :",
    "qword_list : qword_list tSTRING_CONTENT ' '",
    "string_contents :",
    "string_contents : string_contents string_content",
    "xstring_contents :",
    "xstring_contents : xstring_contents string_content",
    "string_content : tSTRING_CONTENT",
    "$$25 :",
    "string_content : tSTRING_DVAR $$25 string_dvar",
    "$$26 :",
    "string_content : tSTRING_DBEG $$26 compstmt tRCURLY",
    "string_dvar : tGVAR",
    "string_dvar : tIVAR",
    "string_dvar : tCVAR",
    "string_dvar : backref",
    "symbol : tSYMBEG sym",
    "sym : fname",
    "sym : tIVAR",
    "sym : tGVAR",
    "sym : tCVAR",
    "dsym : tSYMBEG xstring_contents tSTRING_END",
    "numeric : tINTEGER",
    "numeric : tFLOAT",
    "numeric : tUMINUS_NUM tINTEGER",
    "numeric : tUMINUS_NUM tFLOAT",
    "variable : tIDENTIFIER",
    "variable : tIVAR",
    "variable : tGVAR",
    "variable : tCONSTANT",
    "variable : tCVAR",
    "variable : kNIL",
    "variable : kSELF",
    "variable : kTRUE",
    "variable : kFALSE",
    "variable : k__FILE__",
    "variable : k__LINE__",
    "variable : k__ENCODING__",
    "var_ref : variable",
    "var_lhs : variable",
    "backref : tNTH_REF",
    "backref : tBACK_REF",
    "superclass : term",
    "$$27 :",
    "superclass : tLT $$27 expr_value term",
    "superclass : error term",
    "f_arglist : tLPAREN2 f_args rparen",
    "f_arglist : f_args term",
    "f_args : f_arg ',' f_optarg ',' f_rest_arg opt_f_block_arg",
    "f_args : f_arg ',' f_optarg ',' f_rest_arg ',' f_arg opt_f_block_arg",
    "f_args : f_arg ',' f_optarg opt_f_block_arg",
    "f_args : f_arg ',' f_optarg ',' f_arg opt_f_block_arg",
    "f_args : f_arg ',' f_rest_arg opt_f_block_arg",
    "f_args : f_arg ',' f_rest_arg ',' f_arg opt_f_block_arg",
    "f_args : f_arg opt_f_block_arg",
    "f_args : f_optarg ',' f_rest_arg opt_f_block_arg",
    "f_args : f_optarg ',' f_rest_arg ',' f_arg opt_f_block_arg",
    "f_args : f_optarg opt_f_block_arg",
    "f_args : f_optarg ',' f_arg opt_f_block_arg",
    "f_args : f_rest_arg opt_f_block_arg",
    "f_args : f_rest_arg ',' f_arg opt_f_block_arg",
    "f_args : f_block_arg",
    "f_args :",
    "f_bad_arg : tCONSTANT",
    "f_bad_arg : tIVAR",
    "f_bad_arg : tGVAR",
    "f_bad_arg : tCVAR",
    "f_norm_arg : f_bad_arg",
    "f_norm_arg : tIDENTIFIER",
    "f_arg_item : f_norm_arg",
    "f_arg_item : tLPAREN f_margs rparen",
    "f_arg : f_arg_item",
    "f_arg : f_arg ',' f_arg_item",
    "f_opt : tIDENTIFIER '=' arg_value",
    "f_block_opt : tIDENTIFIER '=' primary_value",
    "f_block_optarg : f_block_opt",
    "f_block_optarg : f_block_optarg ',' f_block_opt",
    "f_optarg : f_opt",
    "f_optarg : f_optarg ',' f_opt",
    "restarg_mark : tSTAR2",
    "restarg_mark : tSTAR",
    "f_rest_arg : restarg_mark tIDENTIFIER",
    "f_rest_arg : restarg_mark",
    "blkarg_mark : tAMPER2",
    "blkarg_mark : tAMPER",
    "f_block_arg : blkarg_mark tIDENTIFIER",
    "opt_f_block_arg : ',' f_block_arg",
    "opt_f_block_arg :",
    "singleton : var_ref",
    "$$28 :",
    "singleton : tLPAREN2 $$28 expr rparen",
    "assoc_list : none",
    "assoc_list : assocs trailer",
    "assocs : assoc",
    "assocs : assocs ',' assoc",
    "assoc : arg_value tASSOC arg_value",
    "assoc : tLABEL arg_value",
    "operation : tIDENTIFIER",
    "operation : tCONSTANT",
    "operation : tFID",
    "operation2 : tIDENTIFIER",
    "operation2 : tCONSTANT",
    "operation2 : tFID",
    "operation2 : op",
    "operation3 : tIDENTIFIER",
    "operation3 : tFID",
    "operation3 : op",
    "dot_or_colon : tDOT",
    "dot_or_colon : tCOLON2",
    "opt_terms :",
    "opt_terms : terms",
    "opt_nl :",
    "opt_nl : '\\n'",
    "rparen : opt_nl tRPAREN",
    "rbracket : opt_nl tRBRACK",
    "trailer :",
    "trailer : '\\n'",
    "trailer : ','",
    "term : ';'",
    "term : '\\n'",
    "terms : term",
    "terms : terms ';'",
    "none :",
    "none_block_pass :",
    };

  /** debugging support, requires the package <tt>jay.yydebug</tt>.
      Set to <tt>null</tt> to suppress debugging messages.
    */
  protected jay.yydebug.yyDebug yydebug;

  /** index-checked interface to {@link #yyNames}.
      @param token single character or <tt>%token</tt> value.
      @return token name or <tt>[illegal]</tt> or <tt>[unknown]</tt>.
    */
  public static final String yyName (int token) {
    if (token < 0 || token > yyNames.length) return "[illegal]";
    String name;
    if ((name = yyNames[token]) != null) return name;
    return "[unknown]";
  }

  /** thrown for irrecoverable syntax errors and stack overflow.
      Nested for convenience, does not depend on parser class.
    */
  public static class yyException extends java.lang.Exception {
    private static final long serialVersionUID = 1L;
    public yyException (String message) {
      super(message);
    }
  }

  /** must be implemented by a scanner object to supply input to the parser.
      Nested for convenience, does not depend on parser class.
    */
  public interface yyInput {

    /** move on to next token.
        @return <tt>false</tt> if positioned beyond tokens.
        @throws IOException on input error.
      */
    boolean advance () throws java.io.IOException;

    /** classifies current token.
        Should not be called if {@link #advance()} returned <tt>false</tt>.
        @return current <tt>%token</tt> or single character.
      */
    int token ();

    /** associated with current token.
        Should not be called if {@link #advance()} returned <tt>false</tt>.
        @return value for {@link #token()}.
      */
    Object value ();
  }

  /** simplified error message.
      @see #yyerror(java.lang.String, java.lang.String[])
    */
  public void yyerror (String message) {
    //new Exception().printStackTrace();
    throw new SyntaxException(PID.GRAMMAR_ERROR, getPosition(null), message);
  }

  /** (syntax) error message.
      Can be overwritten to control message format.
      @param message text to be displayed.
      @param expected list of acceptable tokens, if available.
    */
  public void yyerror (String message, String[] expected, String found) {
    String text = ", unexpected " + found + "\n"; 
    //new Exception().printStackTrace();
    throw new SyntaxException(PID.GRAMMAR_ERROR, getPosition(null), text, found);
  }

  /** computes list of expected tokens on error by tracing the tables.
      @param state for which to compute the list.
      @return list of token names.
    */
  protected String[] yyExpecting (int state) {
    int token, n, len = 0;
    boolean[] ok = new boolean[yyNames.length];

    if ((n = yySindex[state]) != 0)
      for (token = n < 0 ? -n : 0;
           token < yyNames.length && n+token < yyTable.length; ++ token)
        if (yyCheck[n+token] == token && !ok[token] && yyNames[token] != null) {
          ++ len;
          ok[token] = true;
        }
    if ((n = yyRindex[state]) != 0)
      for (token = n < 0 ? -n : 0;
           token < yyNames.length && n+token < yyTable.length; ++ token)
        if (yyCheck[n+token] == token && !ok[token] && yyNames[token] != null) {
          ++ len;
          ok[token] = true;
        }

    String result[] = new String[len];
    for (n = token = 0; n < len;  ++ token)
      if (ok[token]) result[n++] = yyNames[token];
    return result;
  }

  /** the generated parser, with debugging messages.
      Maintains a dynamic state and value stack.
      @param yyLex scanner.
      @param yydebug debug message writer implementing <tt>yyDebug</tt>, or <tt>null</tt>.
      @return result of the last reduction, if any.
      @throws yyException on irrecoverable parse error.
    */
  public Object yyparse (RubyYaccLexer yyLex, Object ayydebug)
				throws java.io.IOException, yyException {
    this.yydebug = (jay.yydebug.yyDebug)ayydebug;
    return yyparse(yyLex);
  }

  /** initial size and increment of the state/value stack [default 256].
      This is not final so that it can be overwritten outside of invocations
      of {@link #yyparse}.
    */
  protected int yyMax;

  /** executed at the beginning of a reduce action.
      Used as <tt>$$ = yyDefault($1)</tt>, prior to the user-specified action, if any.
      Can be overwritten to provide deep copy, etc.
      @param first value for <tt>$1</tt>, or <tt>null</tt>.
      @return first.
    */
  protected Object yyDefault (Object first) {
    return first;
  }

  /** the generated parser.
      Maintains a dynamic state and value stack.
      @param yyLex scanner.
      @return result of the last reduction, if any.
      @throws yyException on irrecoverable parse error.
    */
  public Object yyparse (RubyYaccLexer yyLex) throws java.io.IOException, yyException {
    if (yyMax <= 0) yyMax = 256;			// initial size
    int yyState = 0, yyStates[] = new int[yyMax];	// state stack
    Object yyVal = null, yyVals[] = new Object[yyMax];	// value stack
    int yyToken = -1;					// current input
    int yyErrorFlag = 0;				// #tokens to shift

    yyLoop: for (int yyTop = 0;; ++ yyTop) {
      if (yyTop >= yyStates.length) {			// dynamically increase
        int[] i = new int[yyStates.length+yyMax];
        System.arraycopy(yyStates, 0, i, 0, yyStates.length);
        yyStates = i;
        Object[] o = new Object[yyVals.length+yyMax];
        System.arraycopy(yyVals, 0, o, 0, yyVals.length);
        yyVals = o;
      }
      yyStates[yyTop] = yyState;
      yyVals[yyTop] = yyVal;
      if (yydebug != null) yydebug.push(yyState, yyVal);

      yyDiscarded: for (;;) {	// discarding a token does not change stack
        int yyN;
        if ((yyN = yyDefRed[yyState]) == 0) {	// else [default] reduce (yyN)
          if (yyToken < 0) {
            yyToken = yyLex.advance() ? yyLex.token() : 0;
            if (yydebug != null)
              yydebug.lex(yyState, yyToken, yyName(yyToken), yyLex.value());
          }
          if ((yyN = yySindex[yyState]) != 0 && (yyN += yyToken) >= 0
              && yyN < yyTable.length && yyCheck[yyN] == yyToken) {
            if (yydebug != null)
              yydebug.shift(yyState, yyTable[yyN], yyErrorFlag-1);
            yyState = yyTable[yyN];		// shift to yyN
            yyVal = yyLex.value();
            yyToken = -1;
            if (yyErrorFlag > 0) -- yyErrorFlag;
            continue yyLoop;
          }
          if ((yyN = yyRindex[yyState]) != 0 && (yyN += yyToken) >= 0
              && yyN < yyTable.length && yyCheck[yyN] == yyToken)
            yyN = yyTable[yyN];			// reduce (yyN)
          else
            switch (yyErrorFlag) {
  
            case 0:
              yyerror("syntax error", yyExpecting(yyState), yyNames[yyToken]);
              if (yydebug != null) yydebug.error("syntax error");
  
            case 1: case 2:
              yyErrorFlag = 3;
              do {
                if ((yyN = yySindex[yyStates[yyTop]]) != 0
                    && (yyN += yyErrorCode) >= 0 && yyN < yyTable.length
                    && yyCheck[yyN] == yyErrorCode) {
                  if (yydebug != null)
                    yydebug.shift(yyStates[yyTop], yyTable[yyN], 3);
                  yyState = yyTable[yyN];
                  yyVal = yyLex.value();
                  continue yyLoop;
                }
                if (yydebug != null) yydebug.pop(yyStates[yyTop]);
              } while (-- yyTop >= 0);
              if (yydebug != null) yydebug.reject();
              throw new yyException("irrecoverable syntax error");
  
            case 3:
              if (yyToken == 0) {
                if (yydebug != null) yydebug.reject();
                throw new yyException("irrecoverable syntax error at end-of-file");
              }
              if (yydebug != null)
                yydebug.discard(yyState, yyToken, yyName(yyToken),
  							yyLex.value());
              yyToken = -1;
              continue yyDiscarded;		// leave stack alone
            }
        }
        int yyV = yyTop + 1-yyLen[yyN];
        if (yydebug != null)
          yydebug.reduce(yyState, yyStates[yyV-1], yyN, yyRule[yyN], yyLen[yyN]);
        yyVal = yyDefault(yyV > yyTop ? null : yyVals[yyV]);
        switch (yyN) {
case 1: yyVal = case1_line278(yyVal, yyVals, yyTop); // line 278
break;
case 2: yyVal = case2_line281(yyVal, yyVals, yyTop); // line 281
break;
case 3: yyVal = case3_line294(yyVal, yyVals, yyTop); // line 294
break;
case 4: yyVal = case4_line311(yyVal, yyVals, yyTop); // line 311
break;
case 6: yyVal = case6_line319(yyVal, yyVals, yyTop); // line 319
break;
case 7: yyVal = case7_line322(yyVal, yyVals, yyTop); // line 322
break;
case 8: yyVal = case8_line325(yyVal, yyVals, yyTop); // line 325
break;
case 9: yyVal = case9_line329(yyVal, yyVals, yyTop); // line 329
break;
case 10: yyVal = case10_line331(yyVal, yyVals, yyTop); // line 331
break;
case 11: yyVal = case11_line334(yyVal, yyVals, yyTop); // line 334
break;
case 12: yyVal = case12_line337(yyVal, yyVals, yyTop); // line 337
break;
case 13: yyVal = case13_line340(yyVal, yyVals, yyTop); // line 340
break;
case 14: yyVal = case14_line343(yyVal, yyVals, yyTop); // line 343
break;
case 15: yyVal = case15_line346(yyVal, yyVals, yyTop); // line 346
break;
case 16: yyVal = case16_line349(yyVal, yyVals, yyTop); // line 349
break;
case 17: yyVal = case17_line352(yyVal, yyVals, yyTop); // line 352
break;
case 18: yyVal = case18_line359(yyVal, yyVals, yyTop); // line 359
break;
case 19: yyVal = case19_line366(yyVal, yyVals, yyTop); // line 366
break;
case 20: yyVal = case20_line370(yyVal, yyVals, yyTop); // line 370
break;
case 21: yyVal = case21_line375(yyVal, yyVals, yyTop); // line 375
break;
case 22: yyVal = case22_line380(yyVal, yyVals, yyTop); // line 380
break;
case 23: yyVal = case23_line386(yyVal, yyVals, yyTop); // line 386
break;
case 24: yyVal = case24_line390(yyVal, yyVals, yyTop); // line 390
break;
case 25: yyVal = case25_line395(yyVal, yyVals, yyTop); // line 395
break;
case 26: yyVal = case26_line411(yyVal, yyVals, yyTop); // line 411
break;
case 27: yyVal = case27_line415(yyVal, yyVals, yyTop); // line 415
break;
case 28: yyVal = case28_line418(yyVal, yyVals, yyTop); // line 418
break;
case 29: yyVal = case29_line421(yyVal, yyVals, yyTop); // line 421
break;
case 30: yyVal = case30_line424(yyVal, yyVals, yyTop); // line 424
break;
case 31: yyVal = case31_line427(yyVal, yyVals, yyTop); // line 427
break;
case 32: yyVal = case32_line430(yyVal, yyVals, yyTop); // line 430
break;
case 33: yyVal = case33_line434(yyVal, yyVals, yyTop); // line 434
break;
case 36: yyVal = case36_line443(yyVal, yyVals, yyTop); // line 443
break;
case 37: yyVal = case37_line446(yyVal, yyVals, yyTop); // line 446
break;
case 38: yyVal = case38_line449(yyVal, yyVals, yyTop); // line 449
break;
case 39: yyVal = case39_line452(yyVal, yyVals, yyTop); // line 452
break;
case 41: yyVal = case41_line457(yyVal, yyVals, yyTop); // line 457
break;
case 44: yyVal = case44_line464(yyVal, yyVals, yyTop); // line 464
break;
case 45: yyVal = case45_line467(yyVal, yyVals, yyTop); // line 467
break;
case 46: yyVal = case46_line470(yyVal, yyVals, yyTop); // line 470
break;
case 48: yyVal = case48_line476(yyVal, yyVals, yyTop); // line 476
break;
case 49: yyVal = case49_line479(yyVal, yyVals, yyTop); // line 479
break;
case 50: yyVal = case50_line484(yyVal, yyVals, yyTop); // line 484
break;
case 51: yyVal = case51_line486(yyVal, yyVals, yyTop); // line 486
break;
case 52: yyVal = case52_line492(yyVal, yyVals, yyTop); // line 492
break;
case 53: yyVal = case53_line495(yyVal, yyVals, yyTop); // line 495
break;
case 54: yyVal = case54_line498(yyVal, yyVals, yyTop); // line 498
break;
case 55: yyVal = case55_line501(yyVal, yyVals, yyTop); // line 501
break;
case 56: yyVal = case56_line504(yyVal, yyVals, yyTop); // line 504
break;
case 57: yyVal = case57_line507(yyVal, yyVals, yyTop); // line 507
break;
case 58: yyVal = case58_line510(yyVal, yyVals, yyTop); // line 510
break;
case 59: yyVal = case59_line513(yyVal, yyVals, yyTop); // line 513
break;
case 61: yyVal = case61_line519(yyVal, yyVals, yyTop); // line 519
break;
case 62: yyVal = case62_line524(yyVal, yyVals, yyTop); // line 524
break;
case 63: yyVal = case63_line527(yyVal, yyVals, yyTop); // line 527
break;
case 64: yyVal = case64_line532(yyVal, yyVals, yyTop); // line 532
break;
case 65: yyVal = case65_line535(yyVal, yyVals, yyTop); // line 535
break;
case 66: yyVal = case66_line538(yyVal, yyVals, yyTop); // line 538
break;
case 67: yyVal = case67_line541(yyVal, yyVals, yyTop); // line 541
break;
case 68: yyVal = case68_line544(yyVal, yyVals, yyTop); // line 544
break;
case 69: yyVal = case69_line547(yyVal, yyVals, yyTop); // line 547
break;
case 70: yyVal = case70_line550(yyVal, yyVals, yyTop); // line 550
break;
case 71: yyVal = case71_line553(yyVal, yyVals, yyTop); // line 553
break;
case 72: yyVal = case72_line556(yyVal, yyVals, yyTop); // line 556
break;
case 73: yyVal = case73_line559(yyVal, yyVals, yyTop); // line 559
break;
case 75: yyVal = case75_line564(yyVal, yyVals, yyTop); // line 564
break;
case 76: yyVal = case76_line569(yyVal, yyVals, yyTop); // line 569
break;
case 77: yyVal = case77_line572(yyVal, yyVals, yyTop); // line 572
break;
case 78: yyVal = case78_line577(yyVal, yyVals, yyTop); // line 577
break;
case 79: yyVal = case79_line580(yyVal, yyVals, yyTop); // line 580
break;
case 80: yyVal = case80_line584(yyVal, yyVals, yyTop); // line 584
break;
case 81: yyVal = case81_line587(yyVal, yyVals, yyTop); // line 587
break;
case 82: yyVal = case82_line590(yyVal, yyVals, yyTop); // line 590
break;
case 83: yyVal = case83_line593(yyVal, yyVals, yyTop); // line 593
break;
case 84: yyVal = case84_line596(yyVal, yyVals, yyTop); // line 596
break;
case 85: yyVal = case85_line599(yyVal, yyVals, yyTop); // line 599
break;
case 86: yyVal = case86_line608(yyVal, yyVals, yyTop); // line 608
break;
case 87: yyVal = case87_line617(yyVal, yyVals, yyTop); // line 617
break;
case 88: yyVal = case88_line621(yyVal, yyVals, yyTop); // line 621
break;
case 89: yyVal = case89_line625(yyVal, yyVals, yyTop); // line 625
break;
case 90: yyVal = case90_line628(yyVal, yyVals, yyTop); // line 628
break;
case 91: yyVal = case91_line631(yyVal, yyVals, yyTop); // line 631
break;
case 92: yyVal = case92_line634(yyVal, yyVals, yyTop); // line 634
break;
case 93: yyVal = case93_line637(yyVal, yyVals, yyTop); // line 637
break;
case 94: yyVal = case94_line646(yyVal, yyVals, yyTop); // line 646
break;
case 95: yyVal = case95_line655(yyVal, yyVals, yyTop); // line 655
break;
case 96: yyVal = case96_line659(yyVal, yyVals, yyTop); // line 659
break;
case 98: yyVal = case98_line664(yyVal, yyVals, yyTop); // line 664
break;
case 99: yyVal = case99_line667(yyVal, yyVals, yyTop); // line 667
break;
case 100: yyVal = case100_line670(yyVal, yyVals, yyTop); // line 670
break;
case 104: yyVal = case104_line676(yyVal, yyVals, yyTop); // line 676
break;
case 105: yyVal = case105_line680(yyVal, yyVals, yyTop); // line 680
break;
case 106: yyVal = case106_line686(yyVal, yyVals, yyTop); // line 686
break;
case 107: yyVal = case107_line689(yyVal, yyVals, yyTop); // line 689
break;
case 108: yyVal = case108_line694(yyVal, yyVals, yyTop); // line 694
break;
case 109: yyVal = case109_line697(yyVal, yyVals, yyTop); // line 697
break;
case 110: yyVal = case110_line701(yyVal, yyVals, yyTop); // line 701
break;
case 111: yyVal = case111_line704(yyVal, yyVals, yyTop); // line 704
break;
case 112: yyVal = case112_line706(yyVal, yyVals, yyTop); // line 706
break;
case 184: yyVal = case184_line725(yyVal, yyVals, yyTop); // line 725
break;
case 185: yyVal = case185_line730(yyVal, yyVals, yyTop); // line 730
break;
case 186: yyVal = case186_line735(yyVal, yyVals, yyTop); // line 735
break;
case 187: yyVal = case187_line751(yyVal, yyVals, yyTop); // line 751
break;
case 188: yyVal = case188_line770(yyVal, yyVals, yyTop); // line 770
break;
case 189: yyVal = case189_line774(yyVal, yyVals, yyTop); // line 774
break;
case 190: yyVal = case190_line777(yyVal, yyVals, yyTop); // line 777
break;
case 191: yyVal = case191_line780(yyVal, yyVals, yyTop); // line 780
break;
case 192: yyVal = case192_line783(yyVal, yyVals, yyTop); // line 783
break;
case 193: yyVal = case193_line786(yyVal, yyVals, yyTop); // line 786
break;
case 194: yyVal = case194_line789(yyVal, yyVals, yyTop); // line 789
break;
case 195: yyVal = case195_line792(yyVal, yyVals, yyTop); // line 792
break;
case 196: yyVal = case196_line799(yyVal, yyVals, yyTop); // line 799
break;
case 197: yyVal = case197_line806(yyVal, yyVals, yyTop); // line 806
break;
case 198: yyVal = case198_line809(yyVal, yyVals, yyTop); // line 809
break;
case 199: yyVal = case199_line812(yyVal, yyVals, yyTop); // line 812
break;
case 200: yyVal = case200_line815(yyVal, yyVals, yyTop); // line 815
break;
case 201: yyVal = case201_line818(yyVal, yyVals, yyTop); // line 818
break;
case 202: yyVal = case202_line821(yyVal, yyVals, yyTop); // line 821
break;
case 203: yyVal = case203_line824(yyVal, yyVals, yyTop); // line 824
break;
case 204: yyVal = case204_line827(yyVal, yyVals, yyTop); // line 827
break;
case 205: yyVal = case205_line830(yyVal, yyVals, yyTop); // line 830
break;
case 206: yyVal = case206_line837(yyVal, yyVals, yyTop); // line 837
break;
case 207: yyVal = case207_line840(yyVal, yyVals, yyTop); // line 840
break;
case 208: yyVal = case208_line843(yyVal, yyVals, yyTop); // line 843
break;
case 209: yyVal = case209_line846(yyVal, yyVals, yyTop); // line 846
break;
case 210: yyVal = case210_line849(yyVal, yyVals, yyTop); // line 849
break;
case 211: yyVal = case211_line852(yyVal, yyVals, yyTop); // line 852
break;
case 212: yyVal = case212_line855(yyVal, yyVals, yyTop); // line 855
break;
case 213: yyVal = case213_line858(yyVal, yyVals, yyTop); // line 858
break;
case 214: yyVal = case214_line861(yyVal, yyVals, yyTop); // line 861
break;
case 215: yyVal = case215_line864(yyVal, yyVals, yyTop); // line 864
break;
case 216: yyVal = case216_line867(yyVal, yyVals, yyTop); // line 867
break;
case 217: yyVal = case217_line870(yyVal, yyVals, yyTop); // line 870
break;
case 218: yyVal = case218_line873(yyVal, yyVals, yyTop); // line 873
break;
case 219: yyVal = case219_line882(yyVal, yyVals, yyTop); // line 882
break;
case 220: yyVal = case220_line885(yyVal, yyVals, yyTop); // line 885
break;
case 221: yyVal = case221_line888(yyVal, yyVals, yyTop); // line 888
break;
case 222: yyVal = case222_line891(yyVal, yyVals, yyTop); // line 891
break;
case 223: yyVal = case223_line894(yyVal, yyVals, yyTop); // line 894
break;
case 224: yyVal = case224_line897(yyVal, yyVals, yyTop); // line 897
break;
case 225: yyVal = case225_line900(yyVal, yyVals, yyTop); // line 900
break;
case 226: yyVal = case226_line903(yyVal, yyVals, yyTop); // line 903
break;
case 227: yyVal = case227_line907(yyVal, yyVals, yyTop); // line 907
break;
case 228: yyVal = case228_line910(yyVal, yyVals, yyTop); // line 910
break;
case 229: yyVal = case229_line914(yyVal, yyVals, yyTop); // line 914
break;
case 231: yyVal = case231_line920(yyVal, yyVals, yyTop); // line 920
break;
case 232: yyVal = case232_line923(yyVal, yyVals, yyTop); // line 923
break;
case 233: yyVal = case233_line926(yyVal, yyVals, yyTop); // line 926
break;
case 234: yyVal = case234_line930(yyVal, yyVals, yyTop); // line 930
break;
case 239: yyVal = case239_line939(yyVal, yyVals, yyTop); // line 939
break;
case 240: yyVal = case240_line942(yyVal, yyVals, yyTop); // line 942
break;
case 241: yyVal = case241_line945(yyVal, yyVals, yyTop); // line 945
break;
case 242: yyVal = case242_line949(yyVal, yyVals, yyTop); // line 949
break;
case 243: yyVal = case243_line953(yyVal, yyVals, yyTop); // line 953
break;
case 244: yyVal = case244_line956(yyVal, yyVals, yyTop); // line 956
break;
case 245: yyVal = case245_line958(yyVal, yyVals, yyTop); // line 958
break;
case 246: yyVal = case246_line963(yyVal, yyVals, yyTop); // line 963
break;
case 247: yyVal = case247_line967(yyVal, yyVals, yyTop); // line 967
break;
case 249: yyVal = case249_line972(yyVal, yyVals, yyTop); // line 972
break;
case 250: yyVal = case250_line975(yyVal, yyVals, yyTop); // line 975
break;
case 251: yyVal = case251_line978(yyVal, yyVals, yyTop); // line 978
break;
case 252: yyVal = case252_line987(yyVal, yyVals, yyTop); // line 987
break;
case 253: yyVal = case253_line999(yyVal, yyVals, yyTop); // line 999
break;
case 254: yyVal = case254_line1008(yyVal, yyVals, yyTop); // line 1008
break;
case 255: yyVal = case255_line1018(yyVal, yyVals, yyTop); // line 1018
break;
case 264: yyVal = case264_line1030(yyVal, yyVals, yyTop); // line 1030
break;
case 265: yyVal = case265_line1033(yyVal, yyVals, yyTop); // line 1033
break;
case 266: yyVal = case266_line1036(yyVal, yyVals, yyTop); // line 1036
break;
case 267: yyVal = case267_line1038(yyVal, yyVals, yyTop); // line 1038
break;
case 268: yyVal = case268_line1042(yyVal, yyVals, yyTop); // line 1042
break;
case 269: yyVal = case269_line1049(yyVal, yyVals, yyTop); // line 1049
break;
case 270: yyVal = case270_line1052(yyVal, yyVals, yyTop); // line 1052
break;
case 271: yyVal = case271_line1055(yyVal, yyVals, yyTop); // line 1055
break;
case 272: yyVal = case272_line1064(yyVal, yyVals, yyTop); // line 1064
break;
case 273: yyVal = case273_line1067(yyVal, yyVals, yyTop); // line 1067
break;
case 274: yyVal = case274_line1070(yyVal, yyVals, yyTop); // line 1070
break;
case 275: yyVal = case275_line1073(yyVal, yyVals, yyTop); // line 1073
break;
case 276: yyVal = case276_line1076(yyVal, yyVals, yyTop); // line 1076
break;
case 277: yyVal = case277_line1079(yyVal, yyVals, yyTop); // line 1079
break;
case 278: yyVal = case278_line1082(yyVal, yyVals, yyTop); // line 1082
break;
case 279: yyVal = case279_line1085(yyVal, yyVals, yyTop); // line 1085
break;
case 280: yyVal = case280_line1088(yyVal, yyVals, yyTop); // line 1088
break;
case 282: yyVal = case282_line1092(yyVal, yyVals, yyTop); // line 1092
break;
case 283: yyVal = case283_line1100(yyVal, yyVals, yyTop); // line 1100
break;
case 284: yyVal = case284_line1103(yyVal, yyVals, yyTop); // line 1103
break;
case 285: yyVal = case285_line1106(yyVal, yyVals, yyTop); // line 1106
break;
case 286: yyVal = case286_line1109(yyVal, yyVals, yyTop); // line 1109
break;
case 287: yyVal = case287_line1111(yyVal, yyVals, yyTop); // line 1111
break;
case 288: yyVal = case288_line1113(yyVal, yyVals, yyTop); // line 1113
break;
case 289: yyVal = case289_line1117(yyVal, yyVals, yyTop); // line 1117
break;
case 290: yyVal = case290_line1119(yyVal, yyVals, yyTop); // line 1119
break;
case 291: yyVal = case291_line1121(yyVal, yyVals, yyTop); // line 1121
break;
case 292: yyVal = case292_line1125(yyVal, yyVals, yyTop); // line 1125
break;
case 293: yyVal = case293_line1128(yyVal, yyVals, yyTop); // line 1128
break;
case 294: yyVal = case294_line1131(yyVal, yyVals, yyTop); // line 1131
break;
case 295: yyVal = case295_line1133(yyVal, yyVals, yyTop); // line 1133
break;
case 296: yyVal = case296_line1135(yyVal, yyVals, yyTop); // line 1135
break;
case 297: yyVal = case297_line1139(yyVal, yyVals, yyTop); // line 1139
break;
case 298: yyVal = case298_line1144(yyVal, yyVals, yyTop); // line 1144
break;
case 299: yyVal = case299_line1150(yyVal, yyVals, yyTop); // line 1150
break;
case 300: yyVal = case300_line1153(yyVal, yyVals, yyTop); // line 1153
break;
case 301: yyVal = case301_line1157(yyVal, yyVals, yyTop); // line 1157
break;
case 302: yyVal = case302_line1163(yyVal, yyVals, yyTop); // line 1163
break;
case 303: yyVal = case303_line1168(yyVal, yyVals, yyTop); // line 1168
break;
case 304: yyVal = case304_line1174(yyVal, yyVals, yyTop); // line 1174
break;
case 305: yyVal = case305_line1177(yyVal, yyVals, yyTop); // line 1177
break;
case 306: yyVal = case306_line1185(yyVal, yyVals, yyTop); // line 1185
break;
case 307: yyVal = case307_line1187(yyVal, yyVals, yyTop); // line 1187
break;
case 308: yyVal = case308_line1191(yyVal, yyVals, yyTop); // line 1191
break;
case 309: yyVal = case309_line1199(yyVal, yyVals, yyTop); // line 1199
break;
case 310: yyVal = case310_line1202(yyVal, yyVals, yyTop); // line 1202
break;
case 311: yyVal = case311_line1205(yyVal, yyVals, yyTop); // line 1205
break;
case 312: yyVal = case312_line1208(yyVal, yyVals, yyTop); // line 1208
break;
case 313: yyVal = case313_line1212(yyVal, yyVals, yyTop); // line 1212
break;
case 320: yyVal = case320_line1226(yyVal, yyVals, yyTop); // line 1226
break;
case 322: yyVal = case322_line1231(yyVal, yyVals, yyTop); // line 1231
break;
case 324: yyVal = case324_line1236(yyVal, yyVals, yyTop); // line 1236
break;
case 325: yyVal = case325_line1239(yyVal, yyVals, yyTop); // line 1239
break;
case 326: yyVal = case326_line1242(yyVal, yyVals, yyTop); // line 1242
break;
case 327: yyVal = case327_line1246(yyVal, yyVals, yyTop); // line 1246
break;
case 328: yyVal = case328_line1249(yyVal, yyVals, yyTop); // line 1249
break;
case 329: yyVal = case329_line1253(yyVal, yyVals, yyTop); // line 1253
break;
case 330: yyVal = case330_line1256(yyVal, yyVals, yyTop); // line 1256
break;
case 331: yyVal = case331_line1259(yyVal, yyVals, yyTop); // line 1259
break;
case 332: yyVal = case332_line1262(yyVal, yyVals, yyTop); // line 1262
break;
case 333: yyVal = case333_line1265(yyVal, yyVals, yyTop); // line 1265
break;
case 334: yyVal = case334_line1268(yyVal, yyVals, yyTop); // line 1268
break;
case 335: yyVal = case335_line1271(yyVal, yyVals, yyTop); // line 1271
break;
case 336: yyVal = case336_line1274(yyVal, yyVals, yyTop); // line 1274
break;
case 337: yyVal = case337_line1277(yyVal, yyVals, yyTop); // line 1277
break;
case 338: yyVal = case338_line1281(yyVal, yyVals, yyTop); // line 1281
break;
case 339: yyVal = case339_line1284(yyVal, yyVals, yyTop); // line 1284
break;
case 340: yyVal = case340_line1287(yyVal, yyVals, yyTop); // line 1287
break;
case 341: yyVal = case341_line1290(yyVal, yyVals, yyTop); // line 1290
break;
case 342: yyVal = case342_line1293(yyVal, yyVals, yyTop); // line 1293
break;
case 343: yyVal = case343_line1296(yyVal, yyVals, yyTop); // line 1296
break;
case 344: yyVal = case344_line1300(yyVal, yyVals, yyTop); // line 1300
break;
case 345: yyVal = case345_line1303(yyVal, yyVals, yyTop); // line 1303
break;
case 346: yyVal = case346_line1306(yyVal, yyVals, yyTop); // line 1306
break;
case 347: yyVal = case347_line1309(yyVal, yyVals, yyTop); // line 1309
break;
case 348: yyVal = case348_line1312(yyVal, yyVals, yyTop); // line 1312
break;
case 349: yyVal = case349_line1315(yyVal, yyVals, yyTop); // line 1315
break;
case 350: yyVal = case350_line1318(yyVal, yyVals, yyTop); // line 1318
break;
case 351: yyVal = case351_line1321(yyVal, yyVals, yyTop); // line 1321
break;
case 352: yyVal = case352_line1324(yyVal, yyVals, yyTop); // line 1324
break;
case 353: yyVal = case353_line1328(yyVal, yyVals, yyTop); // line 1328
break;
case 354: yyVal = case354_line1332(yyVal, yyVals, yyTop); // line 1332
break;
case 355: yyVal = case355_line1337(yyVal, yyVals, yyTop); // line 1337
break;
case 356: yyVal = case356_line1340(yyVal, yyVals, yyTop); // line 1340
break;
case 357: yyVal = case357_line1343(yyVal, yyVals, yyTop); // line 1343
break;
case 359: yyVal = case359_line1349(yyVal, yyVals, yyTop); // line 1349
break;
case 360: yyVal = case360_line1354(yyVal, yyVals, yyTop); // line 1354
break;
case 361: yyVal = case361_line1357(yyVal, yyVals, yyTop); // line 1357
break;
case 362: yyVal = case362_line1361(yyVal, yyVals, yyTop); // line 1361
break;
case 363: yyVal = case363_line1364(yyVal, yyVals, yyTop); // line 1364
break;
case 364: yyVal = case364_line1368(yyVal, yyVals, yyTop); // line 1368
break;
case 365: yyVal = case365_line1372(yyVal, yyVals, yyTop); // line 1372
break;
case 366: yyVal = case366_line1378(yyVal, yyVals, yyTop); // line 1378
break;
case 367: yyVal = case367_line1382(yyVal, yyVals, yyTop); // line 1382
break;
case 368: yyVal = case368_line1387(yyVal, yyVals, yyTop); // line 1387
break;
case 369: yyVal = case369_line1390(yyVal, yyVals, yyTop); // line 1390
break;
case 370: yyVal = case370_line1394(yyVal, yyVals, yyTop); // line 1394
break;
case 371: yyVal = case371_line1396(yyVal, yyVals, yyTop); // line 1396
break;
case 372: yyVal = case372_line1401(yyVal, yyVals, yyTop); // line 1401
break;
case 373: yyVal = case373_line1412(yyVal, yyVals, yyTop); // line 1412
break;
case 374: yyVal = case374_line1415(yyVal, yyVals, yyTop); // line 1415
break;
case 375: yyVal = case375_line1419(yyVal, yyVals, yyTop); // line 1419
break;
case 376: yyVal = case376_line1422(yyVal, yyVals, yyTop); // line 1422
break;
case 377: yyVal = case377_line1425(yyVal, yyVals, yyTop); // line 1425
break;
case 378: yyVal = case378_line1428(yyVal, yyVals, yyTop); // line 1428
break;
case 379: yyVal = case379_line1431(yyVal, yyVals, yyTop); // line 1431
break;
case 380: yyVal = case380_line1434(yyVal, yyVals, yyTop); // line 1434
break;
case 381: yyVal = case381_line1437(yyVal, yyVals, yyTop); // line 1437
break;
case 382: yyVal = case382_line1440(yyVal, yyVals, yyTop); // line 1440
break;
case 383: yyVal = case383_line1443(yyVal, yyVals, yyTop); // line 1443
break;
case 384: yyVal = case384_line1451(yyVal, yyVals, yyTop); // line 1451
break;
case 385: yyVal = case385_line1453(yyVal, yyVals, yyTop); // line 1453
break;
case 386: yyVal = case386_line1457(yyVal, yyVals, yyTop); // line 1457
break;
case 387: yyVal = case387_line1459(yyVal, yyVals, yyTop); // line 1459
break;
case 388: yyVal = case388_line1465(yyVal, yyVals, yyTop); // line 1465
break;
case 391: yyVal = case391_line1471(yyVal, yyVals, yyTop); // line 1471
break;
case 392: yyVal = case392_line1484(yyVal, yyVals, yyTop); // line 1484
break;
case 393: yyVal = case393_line1488(yyVal, yyVals, yyTop); // line 1488
break;
case 394: yyVal = case394_line1491(yyVal, yyVals, yyTop); // line 1491
break;
case 396: yyVal = case396_line1497(yyVal, yyVals, yyTop); // line 1497
break;
case 398: yyVal = case398_line1502(yyVal, yyVals, yyTop); // line 1502
break;
case 401: yyVal = case401_line1508(yyVal, yyVals, yyTop); // line 1508
break;
case 403: yyVal = case403_line1514(yyVal, yyVals, yyTop); // line 1514
break;
case 404: yyVal = case404_line1527(yyVal, yyVals, yyTop); // line 1527
break;
case 405: yyVal = case405_line1530(yyVal, yyVals, yyTop); // line 1530
break;
case 406: yyVal = case406_line1533(yyVal, yyVals, yyTop); // line 1533
break;
case 407: yyVal = case407_line1537(yyVal, yyVals, yyTop); // line 1537
break;
case 408: yyVal = case408_line1554(yyVal, yyVals, yyTop); // line 1554
break;
case 409: yyVal = case409_line1570(yyVal, yyVals, yyTop); // line 1570
break;
case 410: yyVal = case410_line1585(yyVal, yyVals, yyTop); // line 1585
break;
case 411: yyVal = case411_line1588(yyVal, yyVals, yyTop); // line 1588
break;
case 412: yyVal = case412_line1592(yyVal, yyVals, yyTop); // line 1592
break;
case 413: yyVal = case413_line1595(yyVal, yyVals, yyTop); // line 1595
break;
case 415: yyVal = case415_line1600(yyVal, yyVals, yyTop); // line 1600
break;
case 416: yyVal = case416_line1604(yyVal, yyVals, yyTop); // line 1604
break;
case 417: yyVal = case417_line1607(yyVal, yyVals, yyTop); // line 1607
break;
case 418: yyVal = case418_line1612(yyVal, yyVals, yyTop); // line 1612
break;
case 419: yyVal = case419_line1615(yyVal, yyVals, yyTop); // line 1615
break;
case 420: yyVal = case420_line1619(yyVal, yyVals, yyTop); // line 1619
break;
case 421: yyVal = case421_line1622(yyVal, yyVals, yyTop); // line 1622
break;
case 422: yyVal = case422_line1626(yyVal, yyVals, yyTop); // line 1626
break;
case 423: yyVal = case423_line1629(yyVal, yyVals, yyTop); // line 1629
break;
case 424: yyVal = case424_line1633(yyVal, yyVals, yyTop); // line 1633
break;
case 425: yyVal = case425_line1636(yyVal, yyVals, yyTop); // line 1636
break;
case 426: yyVal = case426_line1640(yyVal, yyVals, yyTop); // line 1640
break;
case 427: yyVal = case427_line1644(yyVal, yyVals, yyTop); // line 1644
break;
case 428: yyVal = case428_line1648(yyVal, yyVals, yyTop); // line 1648
break;
case 429: yyVal = case429_line1654(yyVal, yyVals, yyTop); // line 1654
break;
case 430: yyVal = case430_line1657(yyVal, yyVals, yyTop); // line 1657
break;
case 431: yyVal = case431_line1660(yyVal, yyVals, yyTop); // line 1660
break;
case 433: yyVal = case433_line1666(yyVal, yyVals, yyTop); // line 1666
break;
case 438: yyVal = case438_line1675(yyVal, yyVals, yyTop); // line 1675
break;
case 439: yyVal = case439_line1707(yyVal, yyVals, yyTop); // line 1707
break;
case 440: yyVal = case440_line1710(yyVal, yyVals, yyTop); // line 1710
break;
case 441: yyVal = case441_line1713(yyVal, yyVals, yyTop); // line 1713
break;
case 442: yyVal = case442_line1716(yyVal, yyVals, yyTop); // line 1716
break;
case 448: yyVal = case448_line1721(yyVal, yyVals, yyTop); // line 1721
break;
case 449: yyVal = case449_line1724(yyVal, yyVals, yyTop); // line 1724
break;
case 450: yyVal = case450_line1727(yyVal, yyVals, yyTop); // line 1727
break;
case 451: yyVal = case451_line1730(yyVal, yyVals, yyTop); // line 1730
break;
case 452: yyVal = case452_line1733(yyVal, yyVals, yyTop); // line 1733
break;
case 453: yyVal = case453_line1736(yyVal, yyVals, yyTop); // line 1736
break;
case 454: yyVal = case454_line1739(yyVal, yyVals, yyTop); // line 1739
break;
case 455: yyVal = case455_line1743(yyVal, yyVals, yyTop); // line 1743
break;
case 456: yyVal = case456_line1747(yyVal, yyVals, yyTop); // line 1747
break;
case 457: yyVal = case457_line1751(yyVal, yyVals, yyTop); // line 1751
break;
case 458: yyVal = case458_line1754(yyVal, yyVals, yyTop); // line 1754
break;
case 459: yyVal = case459_line1758(yyVal, yyVals, yyTop); // line 1758
break;
case 460: yyVal = case460_line1761(yyVal, yyVals, yyTop); // line 1761
break;
case 461: yyVal = case461_line1763(yyVal, yyVals, yyTop); // line 1763
break;
case 462: yyVal = case462_line1766(yyVal, yyVals, yyTop); // line 1766
break;
case 463: yyVal = case463_line1772(yyVal, yyVals, yyTop); // line 1772
break;
case 464: yyVal = case464_line1777(yyVal, yyVals, yyTop); // line 1777
break;
case 465: yyVal = case465_line1781(yyVal, yyVals, yyTop); // line 1781
break;
case 466: yyVal = case466_line1784(yyVal, yyVals, yyTop); // line 1784
break;
case 467: yyVal = case467_line1787(yyVal, yyVals, yyTop); // line 1787
break;
case 468: yyVal = case468_line1790(yyVal, yyVals, yyTop); // line 1790
break;
case 469: yyVal = case469_line1793(yyVal, yyVals, yyTop); // line 1793
break;
case 470: yyVal = case470_line1796(yyVal, yyVals, yyTop); // line 1796
break;
case 471: yyVal = case471_line1799(yyVal, yyVals, yyTop); // line 1799
break;
case 472: yyVal = case472_line1802(yyVal, yyVals, yyTop); // line 1802
break;
case 473: yyVal = case473_line1805(yyVal, yyVals, yyTop); // line 1805
break;
case 474: yyVal = case474_line1808(yyVal, yyVals, yyTop); // line 1808
break;
case 475: yyVal = case475_line1811(yyVal, yyVals, yyTop); // line 1811
break;
case 476: yyVal = case476_line1814(yyVal, yyVals, yyTop); // line 1814
break;
case 477: yyVal = case477_line1817(yyVal, yyVals, yyTop); // line 1817
break;
case 478: yyVal = case478_line1820(yyVal, yyVals, yyTop); // line 1820
break;
case 479: yyVal = case479_line1823(yyVal, yyVals, yyTop); // line 1823
break;
case 480: yyVal = case480_line1827(yyVal, yyVals, yyTop); // line 1827
break;
case 481: yyVal = case481_line1830(yyVal, yyVals, yyTop); // line 1830
break;
case 482: yyVal = case482_line1833(yyVal, yyVals, yyTop); // line 1833
break;
case 483: yyVal = case483_line1836(yyVal, yyVals, yyTop); // line 1836
break;
case 485: yyVal = case485_line1842(yyVal, yyVals, yyTop); // line 1842
break;
case 486: yyVal = case486_line1852(yyVal, yyVals, yyTop); // line 1852
break;
case 487: yyVal = case487_line1859(yyVal, yyVals, yyTop); // line 1859
break;
case 488: yyVal = case488_line1874(yyVal, yyVals, yyTop); // line 1874
break;
case 489: yyVal = case489_line1877(yyVal, yyVals, yyTop); // line 1877
break;
case 490: yyVal = case490_line1882(yyVal, yyVals, yyTop); // line 1882
break;
case 491: yyVal = case491_line1891(yyVal, yyVals, yyTop); // line 1891
break;
case 492: yyVal = case492_line1900(yyVal, yyVals, yyTop); // line 1900
break;
case 493: yyVal = case493_line1903(yyVal, yyVals, yyTop); // line 1903
break;
case 494: yyVal = case494_line1907(yyVal, yyVals, yyTop); // line 1907
break;
case 495: yyVal = case495_line1910(yyVal, yyVals, yyTop); // line 1910
break;
case 498: yyVal = case498_line1916(yyVal, yyVals, yyTop); // line 1916
break;
case 499: yyVal = case499_line1923(yyVal, yyVals, yyTop); // line 1923
break;
case 502: yyVal = case502_line1930(yyVal, yyVals, yyTop); // line 1930
break;
case 503: yyVal = case503_line1940(yyVal, yyVals, yyTop); // line 1940
break;
case 504: yyVal = case504_line1943(yyVal, yyVals, yyTop); // line 1943
break;
case 505: yyVal = case505_line1947(yyVal, yyVals, yyTop); // line 1947
break;
case 506: yyVal = case506_line1953(yyVal, yyVals, yyTop); // line 1953
break;
case 507: yyVal = case507_line1955(yyVal, yyVals, yyTop); // line 1955
break;
case 508: yyVal = case508_line1965(yyVal, yyVals, yyTop); // line 1965
break;
case 509: yyVal = case509_line1968(yyVal, yyVals, yyTop); // line 1968
break;
case 511: yyVal = case511_line1973(yyVal, yyVals, yyTop); // line 1973
break;
case 512: yyVal = case512_line1977(yyVal, yyVals, yyTop); // line 1977
break;
case 513: yyVal = case513_line1987(yyVal, yyVals, yyTop); // line 1987
break;
case 530: yyVal = case530_line1997(yyVal, yyVals, yyTop); // line 1997
break;
case 531: yyVal = case531_line2000(yyVal, yyVals, yyTop); // line 2000
break;
case 535: yyVal = case535_line2005(yyVal, yyVals, yyTop); // line 2005
break;
case 538: yyVal = case538_line2011(yyVal, yyVals, yyTop); // line 2011
break;
case 539: yyVal = case539_line2015(yyVal, yyVals, yyTop); // line 2015
break;
case 540: yyVal = case540_line2019(yyVal, yyVals, yyTop); // line 2019
break;
// ACTIONS_END
        }
        yyTop -= yyLen[yyN];
        yyState = yyStates[yyTop];
        int yyM = yyLhs[yyN];
        if (yyState == 0 && yyM == 0) {
          if (yydebug != null) yydebug.shift(0, yyFinal);
          yyState = yyFinal;
          if (yyToken < 0) {
            yyToken = yyLex.advance() ? yyLex.token() : 0;
            if (yydebug != null)
               yydebug.lex(yyState, yyToken,yyName(yyToken), yyLex.value());
          }
          if (yyToken == 0) {
            if (yydebug != null) yydebug.accept(yyVal);
            return yyVal;
          }
          continue yyLoop;
        }
        if ((yyN = yyGindex[yyM]) != 0 && (yyN += yyState) >= 0
            && yyN < yyTable.length && yyCheck[yyN] == yyState)
          yyState = yyTable[yyN];
        else
          yyState = yyDgoto[yyM];
        if (yydebug != null) yydebug.shift(yyStates[yyTop], yyState);
        continue yyLoop;
      }
    }
  }

public Object case530_line1997(Object yyVal, Object[] yyVals, int yyTop) {
                    yyVal = ((Token)yyVals[0+yyTop]);
    return yyVal;
}
public Object case494_line1907(Object yyVal, Object[] yyVals, int yyTop) {
                    yyVal = new BlockNode(getPosition(((Node)yyVals[0+yyTop]))).add(((Node)yyVals[0+yyTop]));
    return yyVal;
}
public Object case487_line1859(Object yyVal, Object[] yyVals, int yyTop) {
                    yyVal = ((Node)yyVals[-1+yyTop]);
                    /*		    {
			ID tid = internal_id();
			arg_var(tid);
			if (dyna_in_block()) {
			    $2->nd_value = NEW_DVAR(tid);
			}
			else {
			    $2->nd_value = NEW_LVAR(tid);
			}
			$$ = NEW_ARGS_AUX(tid, 1);
			$$->nd_next = $2;*/
    return yyVal;
}
public Object case464_line1777(Object yyVal, Object[] yyVals, int yyTop) {
                    yyVal = ((ArgsNode)yyVals[-1+yyTop]);
    return yyVal;
}
public Object case222_line891(Object yyVal, Object[] yyVals, int yyTop) {
                    yyVal = support.getOperatorCallNode(((Node)yyVals[-2+yyTop]), "<<", ((Node)yyVals[0+yyTop]), getPosition(null));
    return yyVal;
}
public Object case211_line852(Object yyVal, Object[] yyVals, int yyTop) {
                    yyVal = support.getOperatorCallNode(((Node)yyVals[-2+yyTop]), ">", ((Node)yyVals[0+yyTop]), getPosition(null));
    return yyVal;
}
public Object case225_line900(Object yyVal, Object[] yyVals, int yyTop) {
                    yyVal = support.newOrNode(getPosition(((Token)yyVals[-1+yyTop])), ((Node)yyVals[-2+yyTop]), ((Node)yyVals[0+yyTop]));
    return yyVal;
}
public Object case99_line667(Object yyVal, Object[] yyVals, int yyTop) {
                    yyVal = support.new_colon2(((Token)yyVals[0+yyTop]).getPosition(), null, (String) ((Token)yyVals[0+yyTop]).getValue());
    return yyVal;
}
public Object case24_line390(Object yyVal, Object[] yyVals, int yyTop) {
                    support.checkExpression(((Node)yyVals[0+yyTop]));
                    ((MultipleAsgn19Node)yyVals[-2+yyTop]).setValueNode(((Node)yyVals[0+yyTop]));
                    yyVal = ((MultipleAsgn19Node)yyVals[-2+yyTop]);
    return yyVal;
}
public Object case454_line1739(Object yyVal, Object[] yyVals, int yyTop) {
                    yyVal = new Token("__ENCODING__", Tokens.k__LINE__, ((Token)yyVals[0+yyTop]).getPosition());
    return yyVal;
}
public Object case408_line1554(Object yyVal, Object[] yyVals, int yyTop) {
                    ISourcePosition position = support.union(((Token)yyVals[-2+yyTop]), ((Token)yyVals[0+yyTop]));

                    if (((Node)yyVals[-1+yyTop]) == null) {
                        yyVal = new XStrNode(position, null);
                    } else if (((Node)yyVals[-1+yyTop]) instanceof StrNode) {
                        yyVal = new XStrNode(position, (ByteList) ((StrNode)yyVals[-1+yyTop]).getValue().clone());
                    } else if (((Node)yyVals[-1+yyTop]) instanceof DStrNode) {
                        yyVal = new DXStrNode(position, ((DStrNode)yyVals[-1+yyTop]));

                        ((Node)yyVal).setPosition(position);
                    } else {
                        yyVal = new DXStrNode(position).add(((Node)yyVals[-1+yyTop]));
                    }
    return yyVal;
}
public Object case367_line1382(Object yyVal, Object[] yyVals, int yyTop) {
                    yyVal = ((ArgsNode)yyVals[-1+yyTop]);
                    ((ISourcePositionHolder)yyVal).setPosition(support.union(((ArgsNode)yyVals[-1+yyTop]), ((Node)yyVals[0+yyTop])));
    return yyVal;
}
public Object case298_line1144(Object yyVal, Object[] yyVals, int yyTop) {
                    Node body = ((Node)yyVals[-1+yyTop]) == null ? NilImplicitNode.NIL : ((Node)yyVals[-1+yyTop]);

                    yyVal = new ClassNode(support.union(((Token)yyVals[-5+yyTop]), ((Token)yyVals[0+yyTop])), ((Colon3Node)yyVals[-4+yyTop]), support.getCurrentScope(), body, ((Node)yyVals[-3+yyTop]));
                    support.popCurrentScope();
    return yyVal;
}
public Object case198_line809(Object yyVal, Object[] yyVals, int yyTop) {
                    yyVal = support.getOperatorCallNode(((Node)yyVals[-2+yyTop]), "-", ((Node)yyVals[0+yyTop]), getPosition(null));
    return yyVal;
}
public Object case264_line1030(Object yyVal, Object[] yyVals, int yyTop) {
                    yyVal = new FCallNoArgNode(((Token)yyVals[0+yyTop]).getPosition(), (String) ((Token)yyVals[0+yyTop]).getValue());
    return yyVal;
}
public Object case33_line434(Object yyVal, Object[] yyVals, int yyTop) {
                    ((AssignableNode)yyVals[-2+yyTop]).setValueNode(((Node)yyVals[0+yyTop]));
                    yyVal = ((MultipleAsgn19Node)yyVals[-2+yyTop]);
                    ((MultipleAsgn19Node)yyVals[-2+yyTop]).setPosition(support.union(((MultipleAsgn19Node)yyVals[-2+yyTop]), ((Node)yyVals[0+yyTop])));
    return yyVal;
}
public Object case503_line1940(Object yyVal, Object[] yyVals, int yyTop) {
                    yyVal = ((BlockArgNode)yyVals[0+yyTop]);
    return yyVal;
}
public Object case459_line1758(Object yyVal, Object[] yyVals, int yyTop) {
                    yyVal = null;
    return yyVal;
}
public Object case422_line1626(Object yyVal, Object[] yyVals, int yyTop) {
                    yyVal = null;
    return yyVal;
}
public Object case388_line1465(Object yyVal, Object[] yyVals, int yyTop) {
                    yyVal = support.newWhenNode(support.union(((Token)yyVals[-4+yyTop]), support.unwrapNewlineNode(((Node)yyVals[-1+yyTop]))), ((Node)yyVals[-3+yyTop]), ((Node)yyVals[-1+yyTop]), ((Node)yyVals[0+yyTop]));
    return yyVal;
}
public Object case357_line1343(Object yyVal, Object[] yyVals, int yyTop) {
                    yyVal = ((ArgsNode)yyVals[-2+yyTop]);
    return yyVal;
}
public Object case306_line1185(Object yyVal, Object[] yyVals, int yyTop) {
                    lexer.setState(LexState.EXPR_FNAME);
    return yyVal;
}
public Object case491_line1891(Object yyVal, Object[] yyVals, int yyTop) {
                    if (!support.is_local_id(((Token)yyVals[-2+yyTop]))) {
                        yyerror("formal argument must be local variable");
                    }
                    support.shadowing_lvar(((Token)yyVals[-2+yyTop]));
                    support.arg_var(((Token)yyVals[-2+yyTop]));
                    yyVal = new OptArgNode(getPosition(((Token)yyVals[-2+yyTop])), support.assignable(((Token)yyVals[-2+yyTop]), ((Node)yyVals[0+yyTop])));
    return yyVal;
}
public Object case475_line1811(Object yyVal, Object[] yyVals, int yyTop) {
                    yyVal = support.new_args(support.union(((ListNode)yyVals[-3+yyTop]), ((BlockArgNode)yyVals[0+yyTop])), null, ((ListNode)yyVals[-3+yyTop]), null, ((ListNode)yyVals[-1+yyTop]), ((BlockArgNode)yyVals[0+yyTop]));
    return yyVal;
}
public Object case413_line1595(Object yyVal, Object[] yyVals, int yyTop) {
                     yyVal = ((ListNode)yyVals[-2+yyTop]).add(((Node)yyVals[-1+yyTop]) instanceof EvStrNode ? new DStrNode(getPosition(((ListNode)yyVals[-2+yyTop]))).add(((Node)yyVals[-1+yyTop])) : ((Node)yyVals[-1+yyTop]));
    return yyVal;
}
public Object case393_line1488(Object yyVal, Object[] yyVals, int yyTop) {
                    yyVal = support.newArrayNode(((Node)yyVals[0+yyTop]).getPosition(), ((Node)yyVals[0+yyTop]));
    return yyVal;
}
public Object case348_line1312(Object yyVal, Object[] yyVals, int yyTop) {
                    yyVal = support.new_args(support.union(((ListNode)yyVals[-1+yyTop]), ((BlockArgNode)yyVals[0+yyTop])), null, ((ListNode)yyVals[-1+yyTop]), null, null, ((BlockArgNode)yyVals[0+yyTop]));
    return yyVal;
}
public Object case268_line1042(Object yyVal, Object[] yyVals, int yyTop) {
                    if (((Node)yyVals[-1+yyTop]) != null) {
                        /* compstmt position includes both parens around it*/
                        ((ISourcePositionHolder) ((Node)yyVals[-1+yyTop])).setPosition(support.union(((Token)yyVals[-2+yyTop]), ((Token)yyVals[0+yyTop])));
                    }
                    yyVal = ((Node)yyVals[-1+yyTop]);
    return yyVal;
}
public Object case293_line1128(Object yyVal, Object[] yyVals, int yyTop) {
                    yyVal = support.newCaseNode(support.union(((Token)yyVals[-3+yyTop]), ((Token)yyVals[0+yyTop])), null, ((Node)yyVals[-1+yyTop]));
    return yyVal;
}
public Object case278_line1082(Object yyVal, Object[] yyVals, int yyTop) {
                    yyVal = support.getOperatorCallNode(support.getConditionNode(((Node)yyVals[-1+yyTop])), "!");
    return yyVal;
}
public Object case417_line1607(Object yyVal, Object[] yyVals, int yyTop) {
                    yyVal = ((ListNode)yyVals[-1+yyTop]);
                    ((ISourcePositionHolder)yyVal).setPosition(support.union(((Token)yyVals[-2+yyTop]), ((Token)yyVals[0+yyTop])));
    return yyVal;
}
public Object case364_line1368(Object yyVal, Object[] yyVals, int yyTop) {
                    support.pushBlockScope();
                    yyVal = lexer.getLeftParenBegin();
                    lexer.setLeftParenBegin(lexer.incrementParenNest());
    return yyVal;
}
public Object case244_line956(Object yyVal, Object[] yyVals, int yyTop) {
                    yyVal = new Long(lexer.getCmdArgumentState().begin());
    return yyVal;
}
public Object case280_line1088(Object yyVal, Object[] yyVals, int yyTop) {
                    yyVal = new FCallNoArgBlockNode(support.union(((Token)yyVals[-1+yyTop]), ((IterNode)yyVals[0+yyTop])), (String) ((Token)yyVals[-1+yyTop]).getValue(), ((IterNode)yyVals[0+yyTop]));
    return yyVal;
}
public Object case535_line2005(Object yyVal, Object[] yyVals, int yyTop) {
                      yyerrok();
    return yyVal;
}
public Object case493_line1903(Object yyVal, Object[] yyVals, int yyTop) {
                    yyVal = support.appendToBlock(((ListNode)yyVals[-2+yyTop]), ((Node)yyVals[0+yyTop]));
    return yyVal;
}
public Object case93_line637(Object yyVal, Object[] yyVals, int yyTop) {
                    if (support.isInDef() || support.isInSingle()) {
                        yyerror("dynamic constant assignment");
                    }

                    ISourcePosition position = support.union(((Node)yyVals[-2+yyTop]), ((Token)yyVals[0+yyTop]));

                    yyVal = new ConstDeclNode(position, null, support.new_colon2(position, ((Node)yyVals[-2+yyTop]), (String) ((Token)yyVals[0+yyTop]).getValue()), NilImplicitNode.NIL);
    return yyVal;
}
public Object case273_line1067(Object yyVal, Object[] yyVals, int yyTop) {
                    yyVal = new ReturnNode(((Token)yyVals[0+yyTop]).getPosition(), NilImplicitNode.NIL);
    return yyVal;
}
public Object case512_line1977(Object yyVal, Object[] yyVals, int yyTop) {
                    ISourcePosition position;
                    if (((Node)yyVals[-2+yyTop]) == null && ((Node)yyVals[0+yyTop]) == null) {
                        position = getPosition(((Token)yyVals[-1+yyTop]));
                    } else {
                        position = support.union(((Node)yyVals[-2+yyTop]), ((Node)yyVals[0+yyTop]));
                    }

                    yyVal = support.newArrayNode(position, ((Node)yyVals[-2+yyTop])).add(((Node)yyVals[0+yyTop]));
    return yyVal;
}
public Object case507_line1955(Object yyVal, Object[] yyVals, int yyTop) {
                    if (((Node)yyVals[-1+yyTop]) == null) {
                        yyerror("can't define single method for ().");
                    } else if (((Node)yyVals[-1+yyTop]) instanceof ILiteralNode) {
                        yyerror("can't define single method for literals.");
                    }
                    support.checkExpression(((Node)yyVals[-1+yyTop]));
                    yyVal = ((Node)yyVals[-1+yyTop]);
    return yyVal;
}
public Object case452_line1733(Object yyVal, Object[] yyVals, int yyTop) {
                    yyVal = new Token("__FILE__", Tokens.k__FILE__, ((Token)yyVals[0+yyTop]).getPosition());
    return yyVal;
}
public Object case339_line1284(Object yyVal, Object[] yyVals, int yyTop) {
                    yyVal = support.new_args(support.union(((ListNode)yyVals[-7+yyTop]), ((BlockArgNode)yyVals[0+yyTop])), ((ListNode)yyVals[-7+yyTop]), ((ListNode)yyVals[-5+yyTop]), ((RestArgNode)yyVals[-3+yyTop]), ((ListNode)yyVals[-1+yyTop]), ((BlockArgNode)yyVals[0+yyTop]));
    return yyVal;
}
public Object case330_line1256(Object yyVal, Object[] yyVals, int yyTop) {
                    yyVal = new MultipleAsgn19Node(getPosition(((ListNode)yyVals[-3+yyTop])), ((ListNode)yyVals[-3+yyTop]), support.assignable(((Token)yyVals[0+yyTop]), null), null);
    return yyVal;
}
public Object case108_line694(Object yyVal, Object[] yyVals, int yyTop) {
                    yyVal = ((Token)yyVals[0+yyTop]);
    return yyVal;
}
public Object case246_line963(Object yyVal, Object[] yyVals, int yyTop) {
                    yyVal = new BlockPassNode(support.union(((Token)yyVals[-1+yyTop]), ((Node)yyVals[0+yyTop])), ((Node)yyVals[0+yyTop]));
    return yyVal;
}
public Object case186_line735(Object yyVal, Object[] yyVals, int yyTop) {
                    support.checkExpression(((Node)yyVals[0+yyTop]));
                    String asgnOp = (String) ((Token)yyVals[-1+yyTop]).getValue();

                    if (asgnOp.equals("||")) {
                        ((AssignableNode)yyVals[-2+yyTop]).setValueNode(((Node)yyVals[0+yyTop]));
                        yyVal = new OpAsgnOrNode(support.union(((AssignableNode)yyVals[-2+yyTop]), ((Node)yyVals[0+yyTop])), support.gettable2(((AssignableNode)yyVals[-2+yyTop])), ((AssignableNode)yyVals[-2+yyTop]));
                    } else if (asgnOp.equals("&&")) {
                        ((AssignableNode)yyVals[-2+yyTop]).setValueNode(((Node)yyVals[0+yyTop]));
                        yyVal = new OpAsgnAndNode(support.union(((AssignableNode)yyVals[-2+yyTop]), ((Node)yyVals[0+yyTop])), support.gettable2(((AssignableNode)yyVals[-2+yyTop])), ((AssignableNode)yyVals[-2+yyTop]));
                    } else {
                        ((AssignableNode)yyVals[-2+yyTop]).setValueNode(support.getOperatorCallNode(support.gettable2(((AssignableNode)yyVals[-2+yyTop])), asgnOp, ((Node)yyVals[0+yyTop])));
                        ((AssignableNode)yyVals[-2+yyTop]).setPosition(support.union(((AssignableNode)yyVals[-2+yyTop]), ((Node)yyVals[0+yyTop])));
                        yyVal = ((AssignableNode)yyVals[-2+yyTop]);
                    }
    return yyVal;
}
public Object case359_line1349(Object yyVal, Object[] yyVals, int yyTop) {
                    yyVal = null;
    return yyVal;
}
public Object case84_line596(Object yyVal, Object[] yyVals, int yyTop) {
                    yyVal = support.attrset(((Node)yyVals[-2+yyTop]), (String) ((Token)yyVals[0+yyTop]).getValue());
    return yyVal;
}
public Object case300_line1153(Object yyVal, Object[] yyVals, int yyTop) {
                    yyVal = new Integer(support.getInSingle());
                    support.setInSingle(0);
                    support.pushLocalScope();
    return yyVal;
}
public Object case68_line544(Object yyVal, Object[] yyVals, int yyTop) {
                    yyVal = new MultipleAsgn19Node(getPosition(((ListNode)yyVals[-1+yyTop])), ((ListNode)yyVals[-1+yyTop]), new StarNode(getPosition(null)), null);
    return yyVal;
}
public Object case291_line1121(Object yyVal, Object[] yyVals, int yyTop) {
                    Node body = ((Node)yyVals[-1+yyTop]) == null ? NilImplicitNode.NIL : ((Node)yyVals[-1+yyTop]);
                    yyVal = new UntilNode(getPosition(((Token)yyVals[-6+yyTop])), support.getConditionNode(((Node)yyVals[-4+yyTop])), body);
    return yyVal;
}
public Object case458_line1754(Object yyVal, Object[] yyVals, int yyTop) {
                    yyVal = ((Node)yyVals[0+yyTop]);
    return yyVal;
}
public Object case334_line1268(Object yyVal, Object[] yyVals, int yyTop) {
                    yyVal = new MultipleAsgn19Node(getPosition(((Token)yyVals[-1+yyTop])), null, support.assignable(((Token)yyVals[0+yyTop]), null), null);
    return yyVal;
}
public Object case57_line507(Object yyVal, Object[] yyVals, int yyTop) {
                    yyVal = support.new_call(((Node)yyVals[-4+yyTop]), ((Token)yyVals[-2+yyTop]), ((Node)yyVals[-1+yyTop]), ((IterNode)yyVals[0+yyTop]));
    return yyVal;
}
public Object case54_line498(Object yyVal, Object[] yyVals, int yyTop) {
                    yyVal = support.new_call(((Node)yyVals[-3+yyTop]), ((Token)yyVals[-1+yyTop]), ((Node)yyVals[0+yyTop]), null);
    return yyVal;
}
public Object case73_line559(Object yyVal, Object[] yyVals, int yyTop) {
                      yyVal = new MultipleAsgn19Node(getPosition(((Token)yyVals[-2+yyTop])), null, new StarNode(getPosition(null)), ((ListNode)yyVals[0+yyTop]));
    return yyVal;
}
public Object case250_line975(Object yyVal, Object[] yyVals, int yyTop) {
                    yyVal = support.newSplatNode(support.union(((Token)yyVals[-1+yyTop]), ((Node)yyVals[0+yyTop])), ((Node)yyVals[0+yyTop]));
    return yyVal;
}
public Object case383_line1443(Object yyVal, Object[] yyVals, int yyTop) {
                    if (((Node)yyVals[-3+yyTop]) instanceof SelfNode) {
                        yyVal = support.new_fcall(new Token("[]", support.union(((Node)yyVals[-3+yyTop]), ((Token)yyVals[0+yyTop]))), ((Node)yyVals[-1+yyTop]), null);
                    } else {
                        yyVal = support.new_call(((Node)yyVals[-3+yyTop]), new Token("[]", support.union(((Node)yyVals[-3+yyTop]), ((Token)yyVals[0+yyTop]))), ((Node)yyVals[-1+yyTop]), null);
                    }
    return yyVal;
}
public Object case111_line704(Object yyVal, Object[] yyVals, int yyTop) {
                    lexer.setState(LexState.EXPR_FNAME);
    return yyVal;
}
public Object case212_line855(Object yyVal, Object[] yyVals, int yyTop) {
                    yyVal = support.getOperatorCallNode(((Node)yyVals[-2+yyTop]), ">=", ((Node)yyVals[0+yyTop]), getPosition(null));
    return yyVal;
}
public Object case216_line867(Object yyVal, Object[] yyVals, int yyTop) {
                    yyVal = support.getOperatorCallNode(((Node)yyVals[-2+yyTop]), "===", ((Node)yyVals[0+yyTop]), getPosition(null));
    return yyVal;
}
public Object case72_line556(Object yyVal, Object[] yyVals, int yyTop) {
                      yyVal = new MultipleAsgn19Node(getPosition(((Token)yyVals[0+yyTop])), null, new StarNode(getPosition(null)), null);
    return yyVal;
}
public Object case188_line770(Object yyVal, Object[] yyVals, int yyTop) {
  /* FIXME: arg_concat missing for opt_call_args*/
                    yyVal = support.new_opElementAsgnNode(getPosition(((Node)yyVals[-5+yyTop])), ((Node)yyVals[-5+yyTop]), (String) ((Token)yyVals[-1+yyTop]).getValue(), ((Node)yyVals[-3+yyTop]), ((Node)yyVals[0+yyTop]));
    return yyVal;
}
public Object case308_line1191(Object yyVal, Object[] yyVals, int yyTop) {
                    /* TODO: We should use implicit nil for body, but problem (punt til later)*/
                    Node body = ((Node)yyVals[-1+yyTop]); /*$8 == null ? NilImplicitNode.NIL : $8;*/

                    yyVal = new DefsNode(support.union(((Token)yyVals[-8+yyTop]), ((Token)yyVals[0+yyTop])), ((Node)yyVals[-7+yyTop]), new ArgumentNode(((Token)yyVals[-4+yyTop]).getPosition(), (String) ((Token)yyVals[-4+yyTop]).getValue()), ((ArgsNode)yyVals[-2+yyTop]), support.getCurrentScope(), body);
                    support.popCurrentScope();
                    support.setInSingle(support.getInSingle() - 1);
    return yyVal;
}
public Object case295_line1133(Object yyVal, Object[] yyVals, int yyTop) {
                    lexer.getConditionState().end();
    return yyVal;
}
public Object case412_line1592(Object yyVal, Object[] yyVals, int yyTop) {
                    yyVal = new ArrayNode(getPosition(null));
    return yyVal;
}
public Object case409_line1570(Object yyVal, Object[] yyVals, int yyTop) {
                    int options = ((RegexpNode)yyVals[0+yyTop]).getOptions();
                    Node node = ((Node)yyVals[-1+yyTop]);

                    if (node == null) {
                        yyVal = new RegexpNode(getPosition(((Token)yyVals[-2+yyTop])), ByteList.create(""), options & ~ReOptions.RE_OPTION_ONCE);
                    } else if (node instanceof StrNode) {
                        yyVal = new RegexpNode(((Node)yyVals[-1+yyTop]).getPosition(), (ByteList) ((StrNode) node).getValue().clone(), options & ~ReOptions.RE_OPTION_ONCE);
                    } else if (node instanceof DStrNode) {
                        yyVal = new DRegexpNode(getPosition(((Token)yyVals[-2+yyTop])), (DStrNode) node, options, (options & ReOptions.RE_OPTION_ONCE) != 0);
                    } else {
                        yyVal = new DRegexpNode(getPosition(((Token)yyVals[-2+yyTop])), options, (options & ReOptions.RE_OPTION_ONCE) != 0).add(node);
                    }
    return yyVal;
}
public Object case401_line1508(Object yyVal, Object[] yyVals, int yyTop) {
                    /* FIXME: We may be intern'ing more than once.*/
                    yyVal = new SymbolNode(((Token)yyVals[0+yyTop]).getPosition(), ((String) ((Token)yyVals[0+yyTop]).getValue()).intern());
    return yyVal;
}
public Object case376_line1422(Object yyVal, Object[] yyVals, int yyTop) {
                    yyVal = support.new_call(((Node)yyVals[-3+yyTop]), ((Token)yyVals[-1+yyTop]), ((Node)yyVals[0+yyTop]), null);
    return yyVal;
}
public Object case61_line519(Object yyVal, Object[] yyVals, int yyTop) {
                    yyVal = ((Node)yyVals[-1+yyTop]);
    return yyVal;
}
public Object case226_line903(Object yyVal, Object[] yyVals, int yyTop) {
                    /* ENEBO: arg surrounded by in_defined set/unset*/
                    yyVal = new DefinedNode(getPosition(((Token)yyVals[-2+yyTop])), ((Node)yyVals[0+yyTop]));
    return yyVal;
}
public Object case58_line510(Object yyVal, Object[] yyVals, int yyTop) {
                    yyVal = support.new_super(((Node)yyVals[0+yyTop]), ((Token)yyVals[-1+yyTop])); /* .setPosFrom($2);*/
    return yyVal;
}
public Object case282_line1092(Object yyVal, Object[] yyVals, int yyTop) {
                    if (((Node)yyVals[-1+yyTop]) != null && 
                          ((BlockAcceptingNode)yyVals[-1+yyTop]).getIterNode() instanceof BlockPassNode) {
                        throw new SyntaxException(PID.BLOCK_ARG_AND_BLOCK_GIVEN, getPosition(((Node)yyVals[-1+yyTop])), "Both block arg and actual block given.");
                    }
                    yyVal = ((BlockAcceptingNode)yyVals[-1+yyTop]).setIterNode(((IterNode)yyVals[0+yyTop]));
                    ((Node)yyVal).setPosition(support.union(((Node)yyVals[-1+yyTop]), ((IterNode)yyVals[0+yyTop])));
    return yyVal;
}
public Object case70_line550(Object yyVal, Object[] yyVals, int yyTop) {
                    yyVal = new MultipleAsgn19Node(getPosition(((Token)yyVals[-1+yyTop])), null, ((Node)yyVals[0+yyTop]), null);
    return yyVal;
}
public Object case20_line370(Object yyVal, Object[] yyVals, int yyTop) {
                    if (support.isInDef() || support.isInSingle()) {
                        yyerror("BEGIN in method");
                    }
                    support.pushLocalScope();
    return yyVal;
}
public Object case201_line818(Object yyVal, Object[] yyVals, int yyTop) {
                    yyVal = support.getOperatorCallNode(((Node)yyVals[-2+yyTop]), "%", ((Node)yyVals[0+yyTop]), getPosition(null));
    return yyVal;
}
public Object case502_line1930(Object yyVal, Object[] yyVals, int yyTop) {
                    String identifier = (String) ((Token)yyVals[0+yyTop]).getValue();

                    if (!support.is_local_id(((Token)yyVals[0+yyTop]))) {
                        yyerror("block argument must be local variable");
                    }
                    support.shadowing_lvar(((Token)yyVals[0+yyTop]));
                    yyVal = new BlockArgNode(support.union(((Token)yyVals[-1+yyTop]), ((Token)yyVals[0+yyTop])), support.arg_var(((Token)yyVals[0+yyTop])), identifier);
    return yyVal;
}
public Object case404_line1527(Object yyVal, Object[] yyVals, int yyTop) {
                    yyVal = new StrNode(((Token)yyVals[-1+yyTop]).getPosition(), ByteList.create((String) ((Token)yyVals[0+yyTop]).getValue()));
    return yyVal;
}
public Object case200_line815(Object yyVal, Object[] yyVals, int yyTop) {
                    yyVal = support.getOperatorCallNode(((Node)yyVals[-2+yyTop]), "/", ((Node)yyVals[0+yyTop]), getPosition(null));
    return yyVal;
}
public Object case325_line1239(Object yyVal, Object[] yyVals, int yyTop) {
                     yyVal = support.assignable(((Token)yyVals[0+yyTop]), NilImplicitNode.NIL);
    return yyVal;
}
public Object case44_line464(Object yyVal, Object[] yyVals, int yyTop) {
                    yyVal = new ReturnNode(support.union(((Token)yyVals[-1+yyTop]), ((Node)yyVals[0+yyTop])), support.ret_args(((Node)yyVals[0+yyTop]), getPosition(((Token)yyVals[-1+yyTop]))));
    return yyVal;
}
public Object case217_line870(Object yyVal, Object[] yyVals, int yyTop) {
                    yyVal = support.getOperatorCallNode(((Node)yyVals[-2+yyTop]), "!=", ((Node)yyVals[0+yyTop]), getPosition(null));
    return yyVal;
}
public Object case38_line449(Object yyVal, Object[] yyVals, int yyTop) {
                    yyVal = new NotNode(support.union(((Token)yyVals[-1+yyTop]), ((Node)yyVals[0+yyTop])), support.getConditionNode(((Node)yyVals[0+yyTop])));
    return yyVal;
}
public Object case284_line1103(Object yyVal, Object[] yyVals, int yyTop) {
                    yyVal = new IfNode(support.union(((Token)yyVals[-5+yyTop]), ((Token)yyVals[0+yyTop])), support.getConditionNode(((Node)yyVals[-4+yyTop])), ((Node)yyVals[-2+yyTop]), ((Node)yyVals[-1+yyTop]));
    return yyVal;
}
public Object case485_line1842(Object yyVal, Object[] yyVals, int yyTop) {
    /* FIXME: Resolve what the hell is going on*/
    /*                    if (support.is_local_id($1)) {
                        yyerror("formal argument must be local variable");
                        }*/
                     
                    support.shadowing_lvar(((Token)yyVals[0+yyTop]));
                    yyVal = ((Token)yyVals[0+yyTop]);
    return yyVal;
}
public Object case416_line1604(Object yyVal, Object[] yyVals, int yyTop) {
                     yyVal = new ZArrayNode(support.union(((Token)yyVals[-2+yyTop]), ((Token)yyVals[0+yyTop])));
    return yyVal;
}
public Object case371_line1396(Object yyVal, Object[] yyVals, int yyTop) {
                    yyVal = new IterNode(getPosition(((Token)yyVals[-4+yyTop])), ((ArgsNode)yyVals[-2+yyTop]), ((Node)yyVals[-1+yyTop]), support.getCurrentScope());
                    support.popCurrentScope();
    return yyVal;
}
public Object case352_line1324(Object yyVal, Object[] yyVals, int yyTop) {
                    yyVal = support.new_args(getPosition(((BlockArgNode)yyVals[0+yyTop])), null, null, null, null, ((BlockArgNode)yyVals[0+yyTop]));
    return yyVal;
}
public Object case312_line1208(Object yyVal, Object[] yyVals, int yyTop) {
                    yyVal = new RetryNode(((Token)yyVals[0+yyTop]).getPosition());
    return yyVal;
}
public Object case192_line783(Object yyVal, Object[] yyVals, int yyTop) {
                    yyerror("constant re-assignment");
    return yyVal;
}
public Object case202_line821(Object yyVal, Object[] yyVals, int yyTop) {
                    yyVal = support.getOperatorCallNode(((Node)yyVals[-2+yyTop]), "**", ((Node)yyVals[0+yyTop]), getPosition(null));
    return yyVal;
}
public Object case345_line1303(Object yyVal, Object[] yyVals, int yyTop) {
                    yyVal = support.new_args(support.union(((ListNode)yyVals[-1+yyTop]), ((BlockArgNode)yyVals[0+yyTop])), ((ListNode)yyVals[-1+yyTop]), null, null, null, ((BlockArgNode)yyVals[0+yyTop]));
    return yyVal;
}
public Object case4_line311(Object yyVal, Object[] yyVals, int yyTop) {
                    if (((Node)yyVals[-1+yyTop]) instanceof BlockNode) {
                        support.checkUselessStatements(((BlockNode)yyVals[-1+yyTop]));
                    }
                    yyVal = ((Node)yyVals[-1+yyTop]);
    return yyVal;
}
public Object case94_line646(Object yyVal, Object[] yyVals, int yyTop) {
                    if (support.isInDef() || support.isInSingle()) {
                        yyerror("dynamic constant assignment");
                    }

                    ISourcePosition position = support.union(((Token)yyVals[-1+yyTop]), ((Token)yyVals[0+yyTop]));

                    yyVal = new ConstDeclNode(position, null, support.new_colon3(position, (String) ((Token)yyVals[0+yyTop]).getValue()), NilImplicitNode.NIL);
    return yyVal;
}
public Object case39_line452(Object yyVal, Object[] yyVals, int yyTop) {
                    yyVal = new NotNode(support.union(((Token)yyVals[-1+yyTop]), ((Node)yyVals[0+yyTop])), support.getConditionNode(((Node)yyVals[0+yyTop])));
    return yyVal;
}
public Object case305_line1177(Object yyVal, Object[] yyVals, int yyTop) {
                    /* TODO: We should use implicit nil for body, but problem (punt til later)*/
                    Node body = ((Node)yyVals[-1+yyTop]); /*$5 == null ? NilImplicitNode.NIL : $5;*/

                    yyVal = new DefnNode(support.union(((Token)yyVals[-5+yyTop]), ((Token)yyVals[0+yyTop])), new ArgumentNode(((Token)yyVals[-4+yyTop]).getPosition(), (String) ((Token)yyVals[-4+yyTop]).getValue()), ((ArgsNode)yyVals[-2+yyTop]), support.getCurrentScope(), body);
                    support.popCurrentScope();
                    support.setInDef(false);
    return yyVal;
}
public Object case451_line1730(Object yyVal, Object[] yyVals, int yyTop) {
                    yyVal = new Token("false", Tokens.kFALSE, ((Token)yyVals[0+yyTop]).getPosition());
    return yyVal;
}
public Object case338_line1281(Object yyVal, Object[] yyVals, int yyTop) {
                    yyVal = support.new_args(support.union(((ListNode)yyVals[-5+yyTop]), ((BlockArgNode)yyVals[0+yyTop])), ((ListNode)yyVals[-5+yyTop]), ((ListNode)yyVals[-3+yyTop]), ((RestArgNode)yyVals[-1+yyTop]), null, ((BlockArgNode)yyVals[0+yyTop]));
    return yyVal;
}
public Object case86_line608(Object yyVal, Object[] yyVals, int yyTop) {
                    if (support.isInDef() || support.isInSingle()) {
                        yyerror("dynamic constant assignment");
                    }

                    ISourcePosition position = support.union(((Token)yyVals[-1+yyTop]), ((Token)yyVals[0+yyTop]));

                    yyVal = new ConstDeclNode(position, null, support.new_colon3(position, (String) ((Token)yyVals[0+yyTop]).getValue()), NilImplicitNode.NIL);
    return yyVal;
}
public Object case465_line1781(Object yyVal, Object[] yyVals, int yyTop) {
                    yyVal = support.new_args(support.union(((ListNode)yyVals[-5+yyTop]), ((BlockArgNode)yyVals[0+yyTop])), ((ListNode)yyVals[-5+yyTop]), ((ListNode)yyVals[-3+yyTop]), ((RestArgNode)yyVals[-1+yyTop]), null, ((BlockArgNode)yyVals[0+yyTop]));
    return yyVal;
}
public Object case387_line1459(Object yyVal, Object[] yyVals, int yyTop) {
                    yyVal = new IterNode(support.union(((Token)yyVals[-4+yyTop]), ((Token)yyVals[0+yyTop])), ((ArgsNode)yyVals[-2+yyTop]), ((Node)yyVals[-1+yyTop]), support.getCurrentScope());
                    ((ISourcePositionHolder)yyVals[-5+yyTop]).setPosition(support.union(((ISourcePositionHolder)yyVals[-5+yyTop]), ((ISourcePositionHolder)yyVal)));
                    support.popCurrentScope();
    return yyVal;
}
public Object case1_line278(Object yyVal, Object[] yyVals, int yyTop) {
                  lexer.setState(LexState.EXPR_BEG);
                  support.initTopLocalVariables();
    return yyVal;
}
public Object case297_line1139(Object yyVal, Object[] yyVals, int yyTop) {
                    if (support.isInDef() || support.isInSingle()) {
                        yyerror("class definition in method body");
                    }
                    support.pushLocalScope();
    return yyVal;
}
public Object case100_line670(Object yyVal, Object[] yyVals, int yyTop) {
                    yyVal = support.new_colon2(support.union(((Node)yyVals[-2+yyTop]), ((Token)yyVals[0+yyTop])), ((Node)yyVals[-2+yyTop]), (String) ((Token)yyVals[0+yyTop]).getValue());
    return yyVal;
}
public Object case232_line923(Object yyVal, Object[] yyVals, int yyTop) {
                    yyVal = support.arg_append(((Node)yyVals[-3+yyTop]), new HashNode(getPosition(null), ((ListNode)yyVals[-1+yyTop])));
    return yyVal;
}
public Object case439_line1707(Object yyVal, Object[] yyVals, int yyTop) {
                    yyVal = ((Node)yyVals[0+yyTop]);
    return yyVal;
}
public Object case378_line1428(Object yyVal, Object[] yyVals, int yyTop) {
                    yyVal = support.new_call(((Node)yyVals[-2+yyTop]), ((Token)yyVals[0+yyTop]), null, null);
    return yyVal;
}
public Object case366_line1378(Object yyVal, Object[] yyVals, int yyTop) {
                    yyVal = ((ArgsNode)yyVals[-2+yyTop]);
                    ((ISourcePositionHolder)yyVal).setPosition(support.union(((Token)yyVals[-3+yyTop]), ((Node)yyVals[-1+yyTop])));
    return yyVal;
}
public Object case340_line1287(Object yyVal, Object[] yyVals, int yyTop) {
                    yyVal = support.new_args(support.union(((ListNode)yyVals[-3+yyTop]), ((BlockArgNode)yyVals[0+yyTop])), ((ListNode)yyVals[-3+yyTop]), ((ListNode)yyVals[-1+yyTop]), null, null, ((BlockArgNode)yyVals[0+yyTop]));
    return yyVal;
}
public Object case12_line337(Object yyVal, Object[] yyVals, int yyTop) {
                    yyVal = new VAliasNode(getPosition(((Token)yyVals[-2+yyTop])), (String) ((Token)yyVals[-1+yyTop]).getValue(), "$" + ((BackRefNode)yyVals[0+yyTop]).getType());
    return yyVal;
}
public Object case231_line920(Object yyVal, Object[] yyVals, int yyTop) {
                    yyVal = ((Node)yyVals[-1+yyTop]);
    return yyVal;
}
public Object case382_line1440(Object yyVal, Object[] yyVals, int yyTop) {
                    yyVal = new ZSuperNode(((Token)yyVals[0+yyTop]).getPosition());
    return yyVal;
}
public Object case11_line334(Object yyVal, Object[] yyVals, int yyTop) {
                    yyVal = new VAliasNode(getPosition(((Token)yyVals[-2+yyTop])), (String) ((Token)yyVals[-1+yyTop]).getValue(), (String) ((Token)yyVals[0+yyTop]).getValue());
    return yyVal;
}
public Object case286_line1109(Object yyVal, Object[] yyVals, int yyTop) {
                    lexer.getConditionState().begin();
    return yyVal;
}
public Object case26_line411(Object yyVal, Object[] yyVals, int yyTop) {
  /* FIXME: arg_concat logic missing for opt_call_args*/
                    yyVal = support.new_opElementAsgnNode(getPosition(((Node)yyVals[-5+yyTop])), ((Node)yyVals[-5+yyTop]), (String) ((Token)yyVals[-1+yyTop]).getValue(), ((Node)yyVals[-3+yyTop]), ((Node)yyVals[0+yyTop]));
    return yyVal;
}
public Object case28_line418(Object yyVal, Object[] yyVals, int yyTop) {
                    yyVal = new OpAsgnNode(getPosition(((Node)yyVals[-4+yyTop])), ((Node)yyVals[-4+yyTop]), ((Node)yyVals[0+yyTop]), (String) ((Token)yyVals[-2+yyTop]).getValue(), (String) ((Token)yyVals[-1+yyTop]).getValue());
    return yyVal;
}
public Object case462_line1766(Object yyVal, Object[] yyVals, int yyTop) {
                   yyerrok();
                   yyVal = null;
    return yyVal;
}
public Object case13_line340(Object yyVal, Object[] yyVals, int yyTop) {
                    yyerror("can't make alias for the number variables");
    return yyVal;
}
public Object case310_line1202(Object yyVal, Object[] yyVals, int yyTop) {
                    yyVal = new NextNode(((Token)yyVals[0+yyTop]).getPosition(), NilImplicitNode.NIL);
    return yyVal;
}
public Object case347_line1309(Object yyVal, Object[] yyVals, int yyTop) {
                    yyVal = support.new_args(support.union(((ListNode)yyVals[-5+yyTop]), ((BlockArgNode)yyVals[0+yyTop])), null, ((ListNode)yyVals[-5+yyTop]), ((RestArgNode)yyVals[-3+yyTop]), ((ListNode)yyVals[-1+yyTop]), ((BlockArgNode)yyVals[0+yyTop]));
    return yyVal;
}
public Object case251_line978(Object yyVal, Object[] yyVals, int yyTop) {
                    Node node = support.splat_array(((Node)yyVals[-2+yyTop]));

                    if (node != null) {
                        yyVal = support.list_append(node, ((Node)yyVals[0+yyTop]));
                    } else {
                        yyVal = support.arg_append(((Node)yyVals[-2+yyTop]), ((Node)yyVals[0+yyTop]));
                    }
    return yyVal;
}
public Object case299_line1150(Object yyVal, Object[] yyVals, int yyTop) {
                    yyVal = new Boolean(support.isInDef());
                    support.setInDef(false);
    return yyVal;
}
public Object case453_line1736(Object yyVal, Object[] yyVals, int yyTop) {
                    yyVal = new Token("__LINE__", Tokens.k__LINE__, ((Token)yyVals[0+yyTop]).getPosition());
    return yyVal;
}
public Object case351_line1321(Object yyVal, Object[] yyVals, int yyTop) {
                    yyVal = support.new_args(support.union(((RestArgNode)yyVals[-3+yyTop]), ((BlockArgNode)yyVals[0+yyTop])), null, null, ((RestArgNode)yyVals[-3+yyTop]), ((ListNode)yyVals[-1+yyTop]), ((BlockArgNode)yyVals[0+yyTop]));
    return yyVal;
}
public Object case239_line939(Object yyVal, Object[] yyVals, int yyTop) {
                    yyVal = support.newArrayNode(getPosition(((Node)yyVals[0+yyTop])), ((Node)yyVals[0+yyTop]));
    return yyVal;
}
public Object case269_line1049(Object yyVal, Object[] yyVals, int yyTop) {
                    yyVal = support.new_colon2(support.union(((Node)yyVals[-2+yyTop]), ((Token)yyVals[0+yyTop])), ((Node)yyVals[-2+yyTop]), (String) ((Token)yyVals[0+yyTop]).getValue());
    return yyVal;
}
public Object case30_line424(Object yyVal, Object[] yyVals, int yyTop) {
                    support.backrefAssignError(((Node)yyVals[-2+yyTop]));
    return yyVal;
}
public Object case467_line1787(Object yyVal, Object[] yyVals, int yyTop) {
                    yyVal = support.new_args(support.union(((ListNode)yyVals[-3+yyTop]), ((BlockArgNode)yyVals[0+yyTop])), ((ListNode)yyVals[-3+yyTop]), ((ListNode)yyVals[-1+yyTop]), null, null, ((BlockArgNode)yyVals[0+yyTop]));
    return yyVal;
}
public Object case370_line1394(Object yyVal, Object[] yyVals, int yyTop) {
                    support.pushBlockScope();
    return yyVal;
}
public Object case365_line1372(Object yyVal, Object[] yyVals, int yyTop) {
                    yyVal = new LambdaNode(support.union(((ArgsNode)yyVals[-1+yyTop]), ((Node)yyVals[0+yyTop])), ((ArgsNode)yyVals[-1+yyTop]), ((Node)yyVals[0+yyTop]), support.getCurrentScope());
                    support.popCurrentScope();
                    lexer.setLeftParenBegin(((Integer)yyVals[-2+yyTop]));
    return yyVal;
}
public Object case356_line1340(Object yyVal, Object[] yyVals, int yyTop) {
                    yyVal = support.new_args(getPosition(null), null, null, null, null, null);
    return yyVal;
}
public Object case59_line513(Object yyVal, Object[] yyVals, int yyTop) {
                    yyVal = support.new_yield(support.union(((Token)yyVals[-1+yyTop]), ((Node)yyVals[0+yyTop])), ((Node)yyVals[0+yyTop]));
    return yyVal;
}
public Object case304_line1174(Object yyVal, Object[] yyVals, int yyTop) {
                    support.setInDef(true);
                    support.pushLocalScope();
    return yyVal;
}
public Object case96_line659(Object yyVal, Object[] yyVals, int yyTop) {
                    yyerror("class/module name must be CONSTANT");
    return yyVal;
}
public Object case418_line1612(Object yyVal, Object[] yyVals, int yyTop) {
                    yyVal = new ArrayNode(getPosition(null));
    return yyVal;
}
public Object case301_line1157(Object yyVal, Object[] yyVals, int yyTop) {
                    yyVal = new SClassNode(support.union(((Token)yyVals[-7+yyTop]), ((Token)yyVals[0+yyTop])), ((Node)yyVals[-5+yyTop]), support.getCurrentScope(), ((Node)yyVals[-1+yyTop]));
                    support.popCurrentScope();
                    support.setInDef(((Boolean)yyVals[-4+yyTop]).booleanValue());
                    support.setInSingle(((Integer)yyVals[-2+yyTop]).intValue());
    return yyVal;
}
public Object case255_line1018(Object yyVal, Object[] yyVals, int yyTop) {
                     yyVal = support.newSplatNode(getPosition(((Token)yyVals[-1+yyTop])), ((Node)yyVals[0+yyTop]));  
    return yyVal;
}
public Object case45_line467(Object yyVal, Object[] yyVals, int yyTop) {
                    yyVal = new BreakNode(support.union(((Token)yyVals[-1+yyTop]), ((Node)yyVals[0+yyTop])), support.ret_args(((Node)yyVals[0+yyTop]), getPosition(((Token)yyVals[-1+yyTop]))));
    return yyVal;
}
public Object case49_line479(Object yyVal, Object[] yyVals, int yyTop) {
                    yyVal = support.new_call(((Node)yyVals[-3+yyTop]), ((Token)yyVals[-1+yyTop]), ((Node)yyVals[0+yyTop]), null);
    return yyVal;
}
public Object case218_line873(Object yyVal, Object[] yyVals, int yyTop) {
                    yyVal = support.getMatchNode(((Node)yyVals[-2+yyTop]), ((Node)yyVals[0+yyTop]));
                  /* ENEBO
                        $$ = match_op($1, $3);
                        if (nd_type($1) == NODE_LIT && TYPE($1->nd_lit) == T_REGEXP) {
                            $$ = reg_named_capture_assign($1->nd_lit, $$);
                        }
                  */
    return yyVal;
}
public Object case105_line680(Object yyVal, Object[] yyVals, int yyTop) {
                   lexer.setState(LexState.EXPR_END);
                   yyVal = ((Token)yyVals[0+yyTop]);
    return yyVal;
}
public Object case296_line1135(Object yyVal, Object[] yyVals, int yyTop) {
                      /* ENEBO: Lots of optz in 1.9 parser here*/
                    yyVal = new ForNode(support.union(((Token)yyVals[-8+yyTop]), ((Token)yyVals[0+yyTop])), ((Node)yyVals[-7+yyTop]), ((Node)yyVals[-1+yyTop]), ((Node)yyVals[-4+yyTop]));
    return yyVal;
}
public Object case430_line1657(Object yyVal, Object[] yyVals, int yyTop) {
                     yyVal = new InstVarNode(((Token)yyVals[0+yyTop]).getPosition(), (String) ((Token)yyVals[0+yyTop]).getValue());
    return yyVal;
}
public Object case193_line786(Object yyVal, Object[] yyVals, int yyTop) {
                    yyerror("constant re-assignment");
    return yyVal;
}
public Object case241_line945(Object yyVal, Object[] yyVals, int yyTop) {
                    yyVal = support.newArrayNode(getPosition(((ListNode)yyVals[-1+yyTop])), new HashNode(getPosition(null), ((ListNode)yyVals[-1+yyTop])));
                    yyVal = support.arg_blk_pass((Node)yyVal, ((BlockPassNode)yyVals[0+yyTop]));
    return yyVal;
}
public Object case538_line2011(Object yyVal, Object[] yyVals, int yyTop) {
                      yyerrok();
    return yyVal;
}
public Object case478_line1820(Object yyVal, Object[] yyVals, int yyTop) {
                    yyVal = support.new_args(((BlockArgNode)yyVals[0+yyTop]).getPosition(), null, null, null, null, ((BlockArgNode)yyVals[0+yyTop]));
    return yyVal;
}
public Object case425_line1636(Object yyVal, Object[] yyVals, int yyTop) {
                    yyVal = lexer.getStrTerm();
                    lexer.setStrTerm(null);
                    lexer.setState(LexState.EXPR_BEG);
    return yyVal;
}
public Object case396_line1497(Object yyVal, Object[] yyVals, int yyTop) {
                    yyVal = ((Node)yyVals[0+yyTop]);
    return yyVal;
}
public Object case377_line1425(Object yyVal, Object[] yyVals, int yyTop) {
                    yyVal = support.new_call(((Node)yyVals[-3+yyTop]), ((Token)yyVals[-1+yyTop]), ((Node)yyVals[0+yyTop]), null);
    return yyVal;
}
public Object case342_line1293(Object yyVal, Object[] yyVals, int yyTop) {
                    yyVal = support.new_args(support.union(((ListNode)yyVals[-3+yyTop]), ((BlockArgNode)yyVals[0+yyTop])), ((ListNode)yyVals[-3+yyTop]), null, ((RestArgNode)yyVals[-1+yyTop]), null, ((BlockArgNode)yyVals[0+yyTop]));
    return yyVal;
}
public Object case46_line470(Object yyVal, Object[] yyVals, int yyTop) {
                    yyVal = new NextNode(support.union(((Token)yyVals[-1+yyTop]), ((Node)yyVals[0+yyTop])), support.ret_args(((Node)yyVals[0+yyTop]), getPosition(((Token)yyVals[-1+yyTop]))));
    return yyVal;
}
public Object case107_line689(Object yyVal, Object[] yyVals, int yyTop) {
                    yyVal = ((Token)yyVals[0+yyTop]);
    return yyVal;
}
public Object case511_line1973(Object yyVal, Object[] yyVals, int yyTop) {
                    yyVal = ((ListNode)yyVals[-2+yyTop]).addAll(((ListNode)yyVals[0+yyTop]));
    return yyVal;
}
public Object case460_line1761(Object yyVal, Object[] yyVals, int yyTop) {
                   lexer.setState(LexState.EXPR_BEG);
    return yyVal;
}
public Object case420_line1619(Object yyVal, Object[] yyVals, int yyTop) {
                    yyVal = new StrNode(((Token)yyVals[0+yyTop]).getPosition(), ByteList.create(""));
    return yyVal;
}
public Object case272_line1064(Object yyVal, Object[] yyVals, int yyTop) {
                    yyVal = new HashNode(support.union(((Token)yyVals[-2+yyTop]), ((Token)yyVals[0+yyTop])), ((ListNode)yyVals[-1+yyTop]));
    return yyVal;
}
public Object case285_line1106(Object yyVal, Object[] yyVals, int yyTop) {
                    yyVal = new IfNode(support.union(((Token)yyVals[-5+yyTop]), ((Token)yyVals[0+yyTop])), support.getConditionNode(((Node)yyVals[-4+yyTop])), ((Node)yyVals[-1+yyTop]), ((Node)yyVals[-2+yyTop]));
    return yyVal;
}
public Object case78_line577(Object yyVal, Object[] yyVals, int yyTop) {
                    yyVal = support.newArrayNode(((Node)yyVals[0+yyTop]).getPosition(), ((Node)yyVals[0+yyTop]));
    return yyVal;
}
public Object case89_line625(Object yyVal, Object[] yyVals, int yyTop) {
                    yyVal = support.aryset(((Node)yyVals[-3+yyTop]), ((Node)yyVals[-1+yyTop]));
    return yyVal;
}
public Object case63_line527(Object yyVal, Object[] yyVals, int yyTop) {
                    yyVal = new MultipleAsgn19Node(getPosition(((Token)yyVals[-2+yyTop])), support.newArrayNode(getPosition(((Token)yyVals[-2+yyTop])), ((Node)yyVals[-1+yyTop])), null, null);
    return yyVal;
}
public Object case290_line1119(Object yyVal, Object[] yyVals, int yyTop) {
                  lexer.getConditionState().end();
    return yyVal;
}
public Object case322_line1231(Object yyVal, Object[] yyVals, int yyTop) {
                    yyVal = ((Node)yyVals[0+yyTop]);
    return yyVal;
}
public Object case51_line486(Object yyVal, Object[] yyVals, int yyTop) {
                    yyVal = new IterNode(getPosition(((Token)yyVals[-4+yyTop])), ((ArgsNode)yyVals[-2+yyTop]), ((Node)yyVals[-1+yyTop]), support.getCurrentScope());
                    support.popCurrentScope();
    return yyVal;
}
public Object case90_line628(Object yyVal, Object[] yyVals, int yyTop) {
                    yyVal = support.attrset(((Node)yyVals[-2+yyTop]), (String) ((Token)yyVals[0+yyTop]).getValue());
    return yyVal;
}
public Object case506_line1953(Object yyVal, Object[] yyVals, int yyTop) {
                    lexer.setState(LexState.EXPR_BEG);
    return yyVal;
}
public Object case473_line1805(Object yyVal, Object[] yyVals, int yyTop) {
                    yyVal = support.new_args(support.union(((ListNode)yyVals[-5+yyTop]), ((BlockArgNode)yyVals[0+yyTop])), null, ((ListNode)yyVals[-5+yyTop]), ((RestArgNode)yyVals[-3+yyTop]), ((ListNode)yyVals[-1+yyTop]), ((BlockArgNode)yyVals[0+yyTop]));
    return yyVal;
}
public Object case346_line1306(Object yyVal, Object[] yyVals, int yyTop) {
                    yyVal = support.new_args(support.union(((ListNode)yyVals[-3+yyTop]), ((BlockArgNode)yyVals[0+yyTop])), null, ((ListNode)yyVals[-3+yyTop]), ((RestArgNode)yyVals[-1+yyTop]), null, ((BlockArgNode)yyVals[0+yyTop]));
    return yyVal;
}
public Object case276_line1076(Object yyVal, Object[] yyVals, int yyTop) {
                    yyVal = new YieldNode(((Token)yyVals[0+yyTop]).getPosition(), null, false);
    return yyVal;
}
public Object case326_line1242(Object yyVal, Object[] yyVals, int yyTop) {
                    yyVal = ((Node)yyVals[-1+yyTop]);
    return yyVal;
}
public Object case9_line329(Object yyVal, Object[] yyVals, int yyTop) {
                    lexer.setState(LexState.EXPR_FNAME);
    return yyVal;
}
public Object case252_line987(Object yyVal, Object[] yyVals, int yyTop) {
                    Node node = null;

                    /* FIXME: lose syntactical elements here (and others like this)*/
                    if (((Node)yyVals[0+yyTop]) instanceof ArrayNode &&
                        (node = support.splat_array(((Node)yyVals[-3+yyTop]))) != null) {
                        yyVal = support.list_concat(node, ((Node)yyVals[0+yyTop]));
                    } else {
                        yyVal = support.arg_concat(support.union(((Node)yyVals[-3+yyTop]), ((Node)yyVals[0+yyTop])), ((Node)yyVals[-3+yyTop]), ((Node)yyVals[0+yyTop]));
                    }
    return yyVal;
}
public Object case540_line2019(Object yyVal, Object[] yyVals, int yyTop) {
                  yyVal = null;
    return yyVal;
}
public Object case480_line1827(Object yyVal, Object[] yyVals, int yyTop) {
                    yyerror("formal argument cannot be a constant");
    return yyVal;
}
public Object case391_line1471(Object yyVal, Object[] yyVals, int yyTop) {
                    Node node;
                    if (((Node)yyVals[-3+yyTop]) != null) {
                        node = support.appendToBlock(support.node_assign(((Node)yyVals[-3+yyTop]), new GlobalVarNode(getPosition(((Token)yyVals[-5+yyTop])), "$!")), ((Node)yyVals[-1+yyTop]));
                        if (((Node)yyVals[-1+yyTop]) != null) {
                            node.setPosition(support.unwrapNewlineNode(((Node)yyVals[-1+yyTop])).getPosition());
                        }
                    } else {
                        node = ((Node)yyVals[-1+yyTop]);
                    }
                    Node body = node == null ? NilImplicitNode.NIL : node;
                    yyVal = new RescueBodyNode(getPosition(((Token)yyVals[-5+yyTop]), true), ((Node)yyVals[-4+yyTop]), body, ((RescueBodyNode)yyVals[0+yyTop]));
    return yyVal;
}
public Object case333_line1265(Object yyVal, Object[] yyVals, int yyTop) {
                    yyVal = new MultipleAsgn19Node(getPosition(((ListNode)yyVals[-4+yyTop])), ((ListNode)yyVals[-4+yyTop]), new StarNode(getPosition(null)), ((ListNode)yyVals[0+yyTop]));
    return yyVal;
}
public Object case16_line349(Object yyVal, Object[] yyVals, int yyTop) {
                    yyVal = new IfNode(support.union(((Node)yyVals[-2+yyTop]), ((Node)yyVals[0+yyTop])), support.getConditionNode(((Node)yyVals[0+yyTop])), null, ((Node)yyVals[-2+yyTop]));
    return yyVal;
}
public Object case81_line587(Object yyVal, Object[] yyVals, int yyTop) {
                    yyVal = support.aryset(((Node)yyVals[-3+yyTop]), ((Node)yyVals[-1+yyTop]));
    return yyVal;
}
public Object case50_line484(Object yyVal, Object[] yyVals, int yyTop) {
                    support.pushBlockScope();
    return yyVal;
}
public Object case309_line1199(Object yyVal, Object[] yyVals, int yyTop) {
                    yyVal = new BreakNode(((Token)yyVals[0+yyTop]).getPosition(), NilImplicitNode.NIL);
    return yyVal;
}
public Object case91_line631(Object yyVal, Object[] yyVals, int yyTop) {
                    yyVal = support.attrset(((Node)yyVals[-2+yyTop]), (String) ((Token)yyVals[0+yyTop]).getValue());
    return yyVal;
}
public Object case65_line535(Object yyVal, Object[] yyVals, int yyTop) {
                    yyVal = new MultipleAsgn19Node(support.union(((Node)yyVals[-1+yyTop]), ((Node)yyVals[0+yyTop])), ((ListNode)yyVals[-1+yyTop]).add(((Node)yyVals[0+yyTop])), null, null);
    return yyVal;
}
public Object case206_line837(Object yyVal, Object[] yyVals, int yyTop) {
                    yyVal = support.getOperatorCallNode(((Node)yyVals[0+yyTop]), "-@");
    return yyVal;
}
public Object case466_line1784(Object yyVal, Object[] yyVals, int yyTop) {
                    yyVal = support.new_args(support.union(((ListNode)yyVals[-7+yyTop]), ((BlockArgNode)yyVals[0+yyTop])), ((ListNode)yyVals[-7+yyTop]), ((ListNode)yyVals[-5+yyTop]), ((RestArgNode)yyVals[-3+yyTop]), ((ListNode)yyVals[-1+yyTop]), ((BlockArgNode)yyVals[0+yyTop]));
    return yyVal;
}
public Object case455_line1743(Object yyVal, Object[] yyVals, int yyTop) {
                    yyVal = support.gettable(((Token)yyVals[0+yyTop]));
    return yyVal;
}
public Object case80_line584(Object yyVal, Object[] yyVals, int yyTop) {
                    yyVal = support.assignable(((Token)yyVals[0+yyTop]), NilImplicitNode.NIL);
    return yyVal;
}
public Object case208_line843(Object yyVal, Object[] yyVals, int yyTop) {
                    yyVal = support.getOperatorCallNode(((Node)yyVals[-2+yyTop]), "^", ((Node)yyVals[0+yyTop]), getPosition(null));
    return yyVal;
}
public Object case64_line532(Object yyVal, Object[] yyVals, int yyTop) {
                    yyVal = new MultipleAsgn19Node(getPosition(((ListNode)yyVals[0+yyTop])), ((ListNode)yyVals[0+yyTop]), null, null);
    return yyVal;
}
public Object case498_line1916(Object yyVal, Object[] yyVals, int yyTop) {
                    if (!support.is_local_id(((Token)yyVals[0+yyTop]))) {
                        yyerror("duplicate rest argument name");
                    }
                    support.shadowing_lvar(((Token)yyVals[0+yyTop]));
                    yyVal = new RestArgNode(support.union(((Token)yyVals[-1+yyTop]), ((Token)yyVals[0+yyTop])), (String) ((Token)yyVals[0+yyTop]).getValue(), support.arg_var(((Token)yyVals[0+yyTop])));
    return yyVal;
}
public Object case394_line1491(Object yyVal, Object[] yyVals, int yyTop) {
                    yyVal = support.splat_array(((Node)yyVals[0+yyTop]));
                    if (yyVal == null) yyVal = ((Node)yyVals[0+yyTop]);
    return yyVal;
}
public Object case384_line1451(Object yyVal, Object[] yyVals, int yyTop) {
                    support.pushBlockScope();
    return yyVal;
}
public Object case372_line1401(Object yyVal, Object[] yyVals, int yyTop) {
                    /* Workaround for JRUBY-2326 (MRI does not enter this production for some reason)*/
                    if (((Node)yyVals[-1+yyTop]) instanceof YieldNode) {
                        throw new SyntaxException(PID.BLOCK_GIVEN_TO_YIELD, getPosition(((Node)yyVals[-1+yyTop])), "block given to yield");
                    }
                    if (((BlockAcceptingNode)yyVals[-1+yyTop]).getIterNode() instanceof BlockPassNode) {
                        throw new SyntaxException(PID.BLOCK_ARG_AND_BLOCK_GIVEN, getPosition(((Node)yyVals[-1+yyTop])), "Both block arg and actual block given.");
                    }
                    yyVal = ((BlockAcceptingNode)yyVals[-1+yyTop]).setIterNode(((IterNode)yyVals[0+yyTop]));
                    ((Node)yyVal).setPosition(support.union(((Node)yyVals[-1+yyTop]), ((IterNode)yyVals[0+yyTop])));
    return yyVal;
}
public Object case337_line1277(Object yyVal, Object[] yyVals, int yyTop) {
                    yyVal = new MultipleAsgn19Node(getPosition(((Token)yyVals[-2+yyTop])), null, null, ((ListNode)yyVals[0+yyTop]));
    return yyVal;
}
public Object case294_line1131(Object yyVal, Object[] yyVals, int yyTop) {
                    lexer.getConditionState().begin();
    return yyVal;
}
public Object case17_line352(Object yyVal, Object[] yyVals, int yyTop) {
                    if (((Node)yyVals[-2+yyTop]) != null && ((Node)yyVals[-2+yyTop]) instanceof BeginNode) {
                        yyVal = new WhileNode(getPosition(((Node)yyVals[-2+yyTop])), support.getConditionNode(((Node)yyVals[0+yyTop])), ((BeginNode)yyVals[-2+yyTop]).getBodyNode(), false);
                    } else {
                        yyVal = new WhileNode(getPosition(((Node)yyVals[-2+yyTop])), support.getConditionNode(((Node)yyVals[0+yyTop])), ((Node)yyVals[-2+yyTop]), true);
                    }
    return yyVal;
}
public Object case82_line590(Object yyVal, Object[] yyVals, int yyTop) {
                    yyVal = support.attrset(((Node)yyVals[-2+yyTop]), (String) ((Token)yyVals[0+yyTop]).getValue());
    return yyVal;
}
public Object case219_line882(Object yyVal, Object[] yyVals, int yyTop) {
                    yyVal = new NotNode(support.union(((Node)yyVals[-2+yyTop]), ((Node)yyVals[0+yyTop])), support.getMatchNode(((Node)yyVals[-2+yyTop]), ((Node)yyVals[0+yyTop])));
    return yyVal;
}
public Object case471_line1799(Object yyVal, Object[] yyVals, int yyTop) {
                    yyVal = support.new_args(support.union(((ListNode)yyVals[-1+yyTop]), ((BlockArgNode)yyVals[0+yyTop])), ((ListNode)yyVals[-1+yyTop]), null, null, null, ((BlockArgNode)yyVals[0+yyTop]));
    return yyVal;
}
public Object case450_line1727(Object yyVal, Object[] yyVals, int yyTop) {
                    yyVal = new Token("true", Tokens.kTRUE, ((Token)yyVals[0+yyTop]).getPosition());
    return yyVal;
}
public Object case398_line1502(Object yyVal, Object[] yyVals, int yyTop) {
                    yyVal = ((Node)yyVals[0+yyTop]);
    return yyVal;
}
public Object case283_line1100(Object yyVal, Object[] yyVals, int yyTop) {
                    yyVal = ((LambdaNode)yyVals[0+yyTop]);
    return yyVal;
}
public Object case52_line492(Object yyVal, Object[] yyVals, int yyTop) {
                    yyVal = support.new_fcall(((Token)yyVals[-1+yyTop]), ((Node)yyVals[0+yyTop]), null);
    return yyVal;
}
public Object case224_line897(Object yyVal, Object[] yyVals, int yyTop) {
                    yyVal = support.newAndNode(getPosition(((Token)yyVals[-1+yyTop])), ((Node)yyVals[-2+yyTop]), ((Node)yyVals[0+yyTop]));
    return yyVal;
}
public Object case31_line427(Object yyVal, Object[] yyVals, int yyTop) {
                    yyVal = support.node_assign(((Node)yyVals[-2+yyTop]), ((Node)yyVals[0+yyTop]));
    return yyVal;
}
public Object case424_line1633(Object yyVal, Object[] yyVals, int yyTop) {
                    yyVal = ((Node)yyVals[0+yyTop]);
    return yyVal;
}
public Object case379_line1431(Object yyVal, Object[] yyVals, int yyTop) {
                    yyVal = support.new_call(((Node)yyVals[-2+yyTop]), new Token("call", ((Node)yyVals[-2+yyTop]).getPosition()), ((Node)yyVals[0+yyTop]), null);
    return yyVal;
}
public Object case369_line1390(Object yyVal, Object[] yyVals, int yyTop) {
                    yyVal = ((Node)yyVals[-1+yyTop]);
    return yyVal;
}
public Object case223_line894(Object yyVal, Object[] yyVals, int yyTop) {
                    yyVal = support.getOperatorCallNode(((Node)yyVals[-2+yyTop]), ">>", ((Node)yyVals[0+yyTop]), getPosition(null));
    return yyVal;
}
public Object case311_line1205(Object yyVal, Object[] yyVals, int yyTop) {
                    yyVal = new RedoNode(((Token)yyVals[0+yyTop]).getPosition());
    return yyVal;
}
public Object case287_line1111(Object yyVal, Object[] yyVals, int yyTop) {
                    lexer.getConditionState().end();
    return yyVal;
}
public Object case360_line1354(Object yyVal, Object[] yyVals, int yyTop) {
                    yyVal = null;
    return yyVal;
}
public Object case344_line1300(Object yyVal, Object[] yyVals, int yyTop) {
                    yyVal = support.new_args(support.union(((ListNode)yyVals[-5+yyTop]), ((BlockArgNode)yyVals[0+yyTop])), ((ListNode)yyVals[-5+yyTop]), null, ((RestArgNode)yyVals[-3+yyTop]), ((ListNode)yyVals[-1+yyTop]), ((BlockArgNode)yyVals[0+yyTop]));
    return yyVal;
}
public Object case18_line359(Object yyVal, Object[] yyVals, int yyTop) {
                    if (((Node)yyVals[-2+yyTop]) != null && ((Node)yyVals[-2+yyTop]) instanceof BeginNode) {
                        yyVal = new UntilNode(getPosition(((Node)yyVals[-2+yyTop])), support.getConditionNode(((Node)yyVals[0+yyTop])), ((BeginNode)yyVals[-2+yyTop]).getBodyNode(), false);
                    } else {
                        yyVal = new UntilNode(getPosition(((Node)yyVals[-2+yyTop])), support.getConditionNode(((Node)yyVals[0+yyTop])), ((Node)yyVals[-2+yyTop]), true);
                    }
    return yyVal;
}
public Object case98_line664(Object yyVal, Object[] yyVals, int yyTop) {
                    yyVal = support.new_colon3(support.union(((Token)yyVals[-1+yyTop]), ((Token)yyVals[0+yyTop])), (String) ((Token)yyVals[0+yyTop]).getValue());
    return yyVal;
}
public Object case274_line1070(Object yyVal, Object[] yyVals, int yyTop) {
                    yyVal = support.new_yield(support.union(((Token)yyVals[-3+yyTop]), ((Token)yyVals[0+yyTop])), ((Node)yyVals[-1+yyTop]));
    return yyVal;
}
public Object case32_line430(Object yyVal, Object[] yyVals, int yyTop) {
                    ((MultipleAsgn19Node)yyVals[-2+yyTop]).setValueNode(((Node)yyVals[0+yyTop]));
                    yyVal = ((MultipleAsgn19Node)yyVals[-2+yyTop]);
    return yyVal;
}
public Object case442_line1716(Object yyVal, Object[] yyVals, int yyTop) {
                     yyVal = support.negateFloat(((FloatNode)yyVals[0+yyTop]));
    return yyVal;
}
public Object case407_line1537(Object yyVal, Object[] yyVals, int yyTop) {
                    yyVal = ((Node)yyVals[-1+yyTop]);

                    ((ISourcePositionHolder)yyVal).setPosition(support.union(((Token)yyVals[-2+yyTop]), ((Token)yyVals[0+yyTop])));
                    int extraLength = ((String) ((Token)yyVals[-2+yyTop]).getValue()).length() - 1;

                    /* We may need to subtract addition offset off of first */
                    /* string fragment (we optimistically take one off in*/
                    /* ParserSupport.literal_concat).  Check token length*/
                    /* and subtract as neeeded.*/
                    if ((((Node)yyVals[-1+yyTop]) instanceof DStrNode) && extraLength > 0) {
                      Node strNode = ((DStrNode)((Node)yyVals[-1+yyTop])).get(0);
                      assert strNode != null;
                      strNode.getPosition().adjustStartOffset(-extraLength);
                    }
    return yyVal;
}
public Object case381_line1437(Object yyVal, Object[] yyVals, int yyTop) {
                    yyVal = support.new_super(((Node)yyVals[0+yyTop]), ((Token)yyVals[-1+yyTop]));
    return yyVal;
}
public Object case197_line806(Object yyVal, Object[] yyVals, int yyTop) {
                    yyVal = support.getOperatorCallNode(((Node)yyVals[-2+yyTop]), "+", ((Node)yyVals[0+yyTop]), getPosition(null));
    return yyVal;
}
public Object case25_line395(Object yyVal, Object[] yyVals, int yyTop) {
                    support.checkExpression(((Node)yyVals[0+yyTop]));

                    String asgnOp = (String) ((Token)yyVals[-1+yyTop]).getValue();
                    if (asgnOp.equals("||")) {
                        ((AssignableNode)yyVals[-2+yyTop]).setValueNode(((Node)yyVals[0+yyTop]));
                        yyVal = new OpAsgnOrNode(support.union(((AssignableNode)yyVals[-2+yyTop]), ((Node)yyVals[0+yyTop])), support.gettable2(((AssignableNode)yyVals[-2+yyTop])), ((AssignableNode)yyVals[-2+yyTop]));
                    } else if (asgnOp.equals("&&")) {
                        ((AssignableNode)yyVals[-2+yyTop]).setValueNode(((Node)yyVals[0+yyTop]));
                        yyVal = new OpAsgnAndNode(support.union(((AssignableNode)yyVals[-2+yyTop]), ((Node)yyVals[0+yyTop])), support.gettable2(((AssignableNode)yyVals[-2+yyTop])), ((AssignableNode)yyVals[-2+yyTop]));
                    } else {
                        ((AssignableNode)yyVals[-2+yyTop]).setValueNode(support.getOperatorCallNode(support.gettable2(((AssignableNode)yyVals[-2+yyTop])), asgnOp, ((Node)yyVals[0+yyTop])));
                        ((AssignableNode)yyVals[-2+yyTop]).setPosition(support.union(((AssignableNode)yyVals[-2+yyTop]), ((Node)yyVals[0+yyTop])));
                        yyVal = ((AssignableNode)yyVals[-2+yyTop]);
                    }
    return yyVal;
}
public Object case36_line443(Object yyVal, Object[] yyVals, int yyTop) {
                    yyVal = support.newAndNode(getPosition(((Token)yyVals[-1+yyTop])), ((Node)yyVals[-2+yyTop]), ((Node)yyVals[0+yyTop]));
    return yyVal;
}
public Object case328_line1249(Object yyVal, Object[] yyVals, int yyTop) {
                    yyVal = ((ListNode)yyVals[-2+yyTop]).add(((Node)yyVals[0+yyTop]));
    return yyVal;
}
public Object case490_line1882(Object yyVal, Object[] yyVals, int yyTop) {
                    if (!support.is_local_id(((Token)yyVals[-2+yyTop]))) {
                        yyerror("formal argument must be local variable");
                    }
                    support.shadowing_lvar(((Token)yyVals[-2+yyTop]));
                    support.arg_var(((Token)yyVals[-2+yyTop]));
                    yyVal = new OptArgNode(getPosition(((Token)yyVals[-2+yyTop])), support.assignable(((Token)yyVals[-2+yyTop]), ((Node)yyVals[0+yyTop])));
    return yyVal;
}
public Object case429_line1654(Object yyVal, Object[] yyVals, int yyTop) {
                     yyVal = new GlobalVarNode(((Token)yyVals[0+yyTop]).getPosition(), (String) ((Token)yyVals[0+yyTop]).getValue());
    return yyVal;
}
public Object case303_line1168(Object yyVal, Object[] yyVals, int yyTop) {
                    Node body = ((Node)yyVals[-1+yyTop]) == null ? NilImplicitNode.NIL : ((Node)yyVals[-1+yyTop]);

                    yyVal = new ModuleNode(support.union(((Token)yyVals[-4+yyTop]), ((Token)yyVals[0+yyTop])), ((Colon3Node)yyVals[-3+yyTop]), support.getCurrentScope(), body);
                    support.popCurrentScope();
    return yyVal;
}
public Object case419_line1615(Object yyVal, Object[] yyVals, int yyTop) {
                    yyVal = ((ListNode)yyVals[-2+yyTop]).add(((Node)yyVals[-1+yyTop]));
    return yyVal;
}
public Object case386_line1457(Object yyVal, Object[] yyVals, int yyTop) {
                    support.pushBlockScope();
    return yyVal;
}
public Object case332_line1262(Object yyVal, Object[] yyVals, int yyTop) {
                    yyVal = new MultipleAsgn19Node(getPosition(((ListNode)yyVals[-2+yyTop])), ((ListNode)yyVals[-2+yyTop]), new StarNode(getPosition(null)), null);
    return yyVal;
}
public Object case271_line1055(Object yyVal, Object[] yyVals, int yyTop) {
                    ISourcePosition position = support.union(((Token)yyVals[-2+yyTop]), ((Token)yyVals[0+yyTop]));
                    if (((Node)yyVals[-1+yyTop]) == null) {
                        yyVal = new ZArrayNode(position); /* zero length array */
                    } else {
                        yyVal = ((Node)yyVals[-1+yyTop]);
                        ((ISourcePositionHolder)yyVal).setPosition(position);
                    }
    return yyVal;
}
public Object case245_line958(Object yyVal, Object[] yyVals, int yyTop) {
                    lexer.getCmdArgumentState().reset(((Long)yyVals[-1+yyTop]).longValue());
                    yyVal = ((Node)yyVals[0+yyTop]);
    return yyVal;
}
public Object case468_line1790(Object yyVal, Object[] yyVals, int yyTop) {
                    yyVal = support.new_args(support.union(((ListNode)yyVals[-5+yyTop]), ((BlockArgNode)yyVals[0+yyTop])), ((ListNode)yyVals[-5+yyTop]), ((ListNode)yyVals[-3+yyTop]), null, ((ListNode)yyVals[-1+yyTop]), ((BlockArgNode)yyVals[0+yyTop]));
    return yyVal;
}
public Object case350_line1318(Object yyVal, Object[] yyVals, int yyTop) {
                    yyVal = support.new_args(support.union(((RestArgNode)yyVals[-1+yyTop]), ((BlockArgNode)yyVals[0+yyTop])), null, null, ((RestArgNode)yyVals[-1+yyTop]), null, ((BlockArgNode)yyVals[0+yyTop]));
    return yyVal;
}
public Object case185_line730(Object yyVal, Object[] yyVals, int yyTop) {
                    ISourcePosition position = support.union(((Token)yyVals[-1+yyTop]), ((Node)yyVals[0+yyTop]));
                    Node body = ((Node)yyVals[0+yyTop]) == null ? NilImplicitNode.NIL : ((Node)yyVals[0+yyTop]);
                    yyVal = support.node_assign(((Node)yyVals[-4+yyTop]), new RescueNode(position, ((Node)yyVals[-2+yyTop]), new RescueBodyNode(position, null, body, null), null));
    return yyVal;
}
public Object case479_line1823(Object yyVal, Object[] yyVals, int yyTop) {
                    yyVal = support.new_args(getPosition(null), null, null, null, null, null);
    return yyVal;
}
public Object case461_line1763(Object yyVal, Object[] yyVals, int yyTop) {
                    yyVal = ((Node)yyVals[-1+yyTop]);
    return yyVal;
}
public Object case355_line1337(Object yyVal, Object[] yyVals, int yyTop) {
                    yyVal = support.new_args(getPosition(null), null, null, null, null, null);
    return yyVal;
}
public Object case343_line1296(Object yyVal, Object[] yyVals, int yyTop) {
    /* FIXME, weird unnamed rest*/
                    yyVal = support.new_args(((ListNode)yyVals[-1+yyTop]).getPosition(), ((ListNode)yyVals[-1+yyTop]), null, null, null, null);
    return yyVal;
}
public Object case253_line999(Object yyVal, Object[] yyVals, int yyTop) {
                    Node node = support.splat_array(((Node)yyVals[-2+yyTop]));

                    if (node != null) {
                        yyVal = support.list_append(node, ((Node)yyVals[0+yyTop]));
                    } else {
                        yyVal = support.arg_append(((Node)yyVals[-2+yyTop]), ((Node)yyVals[0+yyTop]));
                    }
    return yyVal;
}
public Object case289_line1117(Object yyVal, Object[] yyVals, int yyTop) {
                  lexer.getConditionState().begin();
    return yyVal;
}
public Object case243_line953(Object yyVal, Object[] yyVals, int yyTop) {
    return yyVal;
}
public Object case539_line2015(Object yyVal, Object[] yyVals, int yyTop) {
                      yyVal = null;
    return yyVal;
}
public Object case470_line1796(Object yyVal, Object[] yyVals, int yyTop) {
                    yyVal = support.new_args(support.union(((ListNode)yyVals[-5+yyTop]), ((BlockArgNode)yyVals[0+yyTop])), ((ListNode)yyVals[-5+yyTop]), null, ((RestArgNode)yyVals[-3+yyTop]), ((ListNode)yyVals[-1+yyTop]), ((BlockArgNode)yyVals[0+yyTop]));
    return yyVal;
}
public Object case431_line1660(Object yyVal, Object[] yyVals, int yyTop) {
                     yyVal = new ClassVarNode(((Token)yyVals[0+yyTop]).getPosition(), (String) ((Token)yyVals[0+yyTop]).getValue());
    return yyVal;
}
public Object case405_line1530(Object yyVal, Object[] yyVals, int yyTop) {
                    yyVal = ((Node)yyVals[0+yyTop]);
    return yyVal;
}
public Object case266_line1036(Object yyVal, Object[] yyVals, int yyTop) {
                    lexer.setState(LexState.EXPR_ENDARG); 
    return yyVal;
}
public Object case92_line634(Object yyVal, Object[] yyVals, int yyTop) {
                    yyVal = support.attrset(((Node)yyVals[-2+yyTop]), (String) ((Token)yyVals[0+yyTop]).getValue());
    return yyVal;
}
public Object case109_line697(Object yyVal, Object[] yyVals, int yyTop) {
                    yyVal = ((Node)yyVals[0+yyTop]);
    return yyVal;
}
public Object case19_line366(Object yyVal, Object[] yyVals, int yyTop) {
                    Node body = ((Node)yyVals[0+yyTop]) == null ? NilImplicitNode.NIL : ((Node)yyVals[0+yyTop]);
                    yyVal = new RescueNode(getPosition(((Node)yyVals[-2+yyTop])), ((Node)yyVals[-2+yyTop]), new RescueBodyNode(getPosition(((Node)yyVals[-2+yyTop])), null, body, null), null);
    return yyVal;
}
public Object case85_line599(Object yyVal, Object[] yyVals, int yyTop) {
                    if (support.isInDef() || support.isInSingle()) {
                        yyerror("dynamic constant assignment");
                    }

                    ISourcePosition position = support.union(((Node)yyVals[-2+yyTop]), ((Token)yyVals[0+yyTop]));

                    yyVal = new ConstDeclNode(position, null, support.new_colon2(position, ((Node)yyVals[-2+yyTop]), (String) ((Token)yyVals[0+yyTop]).getValue()), NilImplicitNode.NIL);
    return yyVal;
}
public Object case247_line967(Object yyVal, Object[] yyVals, int yyTop) {
                    yyVal = ((BlockPassNode)yyVals[0+yyTop]);
    return yyVal;
}
public Object case69_line547(Object yyVal, Object[] yyVals, int yyTop) {
                    yyVal = new MultipleAsgn19Node(getPosition(((ListNode)yyVals[-3+yyTop])), ((ListNode)yyVals[-3+yyTop]), new StarNode(getPosition(null)), ((ListNode)yyVals[0+yyTop]));
    return yyVal;
}
public Object case505_line1947(Object yyVal, Object[] yyVals, int yyTop) {
                    if (!(((Node)yyVals[0+yyTop]) instanceof SelfNode)) {
                        support.checkExpression(((Node)yyVals[0+yyTop]));
                    }
                    yyVal = ((Node)yyVals[0+yyTop]);
    return yyVal;
}
public Object case426_line1640(Object yyVal, Object[] yyVals, int yyTop) {
                    lexer.setStrTerm(((StrTerm)yyVals[-1+yyTop]));
                    yyVal = new EvStrNode(support.union(((Token)yyVals[-2+yyTop]), ((Node)yyVals[0+yyTop])), ((Node)yyVals[0+yyTop]));
    return yyVal;
}
public Object case313_line1212(Object yyVal, Object[] yyVals, int yyTop) {
                    support.checkExpression(((Node)yyVals[0+yyTop]));
                    yyVal = ((Node)yyVals[0+yyTop]);
                    if (yyVal == null) yyVal = NilImplicitNode.NIL;
    return yyVal;
}
public Object case112_line706(Object yyVal, Object[] yyVals, int yyTop) {
                    yyVal = support.appendToBlock(((Node)yyVals[-3+yyTop]), new UndefNode(getPosition(((Node)yyVals[-3+yyTop])), (String) ((Token)yyVals[0+yyTop]).getValue()));
    return yyVal;
}
public Object case277_line1079(Object yyVal, Object[] yyVals, int yyTop) {
                    yyVal = new DefinedNode(getPosition(((Token)yyVals[-4+yyTop])), ((Node)yyVals[-1+yyTop]));
    return yyVal;
}
public Object case474_line1808(Object yyVal, Object[] yyVals, int yyTop) {
                    yyVal = support.new_args(support.union(((ListNode)yyVals[-1+yyTop]), ((BlockArgNode)yyVals[0+yyTop])), null, ((ListNode)yyVals[-1+yyTop]), null, null, ((BlockArgNode)yyVals[0+yyTop]));
    return yyVal;
}
public Object case327_line1246(Object yyVal, Object[] yyVals, int yyTop) {
                    yyVal = support.newArrayNode(((Node)yyVals[0+yyTop]).getPosition(), ((Node)yyVals[0+yyTop]));
    return yyVal;
}
public Object case56_line504(Object yyVal, Object[] yyVals, int yyTop) {
                    yyVal = support.new_call(((Node)yyVals[-3+yyTop]), ((Token)yyVals[-1+yyTop]), ((Node)yyVals[0+yyTop]), null);
    return yyVal;
}
public Object case213_line858(Object yyVal, Object[] yyVals, int yyTop) {
                    yyVal = support.getOperatorCallNode(((Node)yyVals[-2+yyTop]), "<", ((Node)yyVals[0+yyTop]), getPosition(null));
    return yyVal;
}
public Object case75_line564(Object yyVal, Object[] yyVals, int yyTop) {
                    yyVal = ((Node)yyVals[-1+yyTop]);
    return yyVal;
}
public Object case481_line1830(Object yyVal, Object[] yyVals, int yyTop) {
                    yyerror("formal argument cannot be an instance variable");
    return yyVal;
}
public Object case449_line1724(Object yyVal, Object[] yyVals, int yyTop) {
                    yyVal = new Token("self", Tokens.kSELF, ((Token)yyVals[0+yyTop]).getPosition());
    return yyVal;
}
public Object case385_line1453(Object yyVal, Object[] yyVals, int yyTop) {
                    yyVal = new IterNode(getPosition(((Token)yyVals[-4+yyTop])), ((ArgsNode)yyVals[-2+yyTop]), ((Node)yyVals[-1+yyTop]), support.getCurrentScope());
                    support.popCurrentScope();
    return yyVal;
}
public Object case55_line501(Object yyVal, Object[] yyVals, int yyTop) {
                    yyVal = support.new_call(((Node)yyVals[-4+yyTop]), ((Token)yyVals[-2+yyTop]), ((Node)yyVals[-1+yyTop]), ((IterNode)yyVals[0+yyTop])); 
    return yyVal;
}
public Object case110_line701(Object yyVal, Object[] yyVals, int yyTop) {
                    yyVal = new UndefNode(getPosition(((Token)yyVals[0+yyTop])), (String) ((Token)yyVals[0+yyTop]).getValue());
    return yyVal;
}
public Object case215_line864(Object yyVal, Object[] yyVals, int yyTop) {
                    yyVal = support.getOperatorCallNode(((Node)yyVals[-2+yyTop]), "==", ((Node)yyVals[0+yyTop]), getPosition(null));
    return yyVal;
}
public Object case71_line553(Object yyVal, Object[] yyVals, int yyTop) {
                    yyVal = new MultipleAsgn19Node(getPosition(((Token)yyVals[-3+yyTop])), null, ((Node)yyVals[-2+yyTop]), ((ListNode)yyVals[0+yyTop]));
    return yyVal;
}
public Object case456_line1747(Object yyVal, Object[] yyVals, int yyTop) {
                    yyVal = support.assignable(((Token)yyVals[0+yyTop]), NilImplicitNode.NIL);
    return yyVal;
}
public Object case354_line1332(Object yyVal, Object[] yyVals, int yyTop) {
                    lexer.commandStart = true;
                    yyVal = ((ArgsNode)yyVals[0+yyTop]);
    return yyVal;
}
public Object case341_line1290(Object yyVal, Object[] yyVals, int yyTop) {
                    yyVal = support.new_args(support.union(((ListNode)yyVals[-5+yyTop]), ((BlockArgNode)yyVals[0+yyTop])), ((ListNode)yyVals[-5+yyTop]), ((ListNode)yyVals[-3+yyTop]), null, ((ListNode)yyVals[-1+yyTop]), ((BlockArgNode)yyVals[0+yyTop]));
    return yyVal;
}
public Object case227_line907(Object yyVal, Object[] yyVals, int yyTop) {
                    yyVal = new IfNode(getPosition(((Node)yyVals[-5+yyTop])), support.getConditionNode(((Node)yyVals[-5+yyTop])), ((Node)yyVals[-3+yyTop]), ((Node)yyVals[0+yyTop]));
    return yyVal;
}
public Object case214_line861(Object yyVal, Object[] yyVals, int yyTop) {
                    yyVal = support.getOperatorCallNode(((Node)yyVals[-2+yyTop]), "<=", ((Node)yyVals[0+yyTop]), getPosition(null));
    return yyVal;
}
public Object case190_line777(Object yyVal, Object[] yyVals, int yyTop) {
                    yyVal = new OpAsgnNode(getPosition(((Node)yyVals[-4+yyTop])), ((Node)yyVals[-4+yyTop]), ((Node)yyVals[0+yyTop]), (String) ((Token)yyVals[-2+yyTop]).getValue(), (String) ((Token)yyVals[-1+yyTop]).getValue());
    return yyVal;
}
public Object case270_line1052(Object yyVal, Object[] yyVals, int yyTop) {
                    yyVal = support.new_colon3(support.union(((Token)yyVals[-1+yyTop]), ((Token)yyVals[0+yyTop])), (String) ((Token)yyVals[0+yyTop]).getValue());
    return yyVal;
}
public Object case189_line774(Object yyVal, Object[] yyVals, int yyTop) {
                    yyVal = new OpAsgnNode(getPosition(((Node)yyVals[-4+yyTop])), ((Node)yyVals[-4+yyTop]), ((Node)yyVals[0+yyTop]), (String) ((Token)yyVals[-2+yyTop]).getValue(), (String) ((Token)yyVals[-1+yyTop]).getValue());
    return yyVal;
}
public Object case433_line1666(Object yyVal, Object[] yyVals, int yyTop) {
                     lexer.setState(LexState.EXPR_END);
                     yyVal = ((Token)yyVals[0+yyTop]);
                     ((ISourcePositionHolder)yyVal).setPosition(support.union(((Token)yyVals[-1+yyTop]), ((Token)yyVals[0+yyTop])));
    return yyVal;
}
public Object case37_line446(Object yyVal, Object[] yyVals, int yyTop) {
                    yyVal = support.newOrNode(getPosition(((Token)yyVals[-1+yyTop])), ((Node)yyVals[-2+yyTop]), ((Node)yyVals[0+yyTop]));
    return yyVal;
}
public Object case21_line375(Object yyVal, Object[] yyVals, int yyTop) {
                    support.getResult().addBeginNode(new PreExeNode(getPosition(((Node)yyVals[-1+yyTop])), support.getCurrentScope(), ((Node)yyVals[-1+yyTop])));
                    support.popCurrentScope();
                    yyVal = null;
    return yyVal;
}
public Object case288_line1113(Object yyVal, Object[] yyVals, int yyTop) {
                    Node body = ((Node)yyVals[-1+yyTop]) == null ? NilImplicitNode.NIL : ((Node)yyVals[-1+yyTop]);
                    yyVal = new WhileNode(support.union(((Token)yyVals[-6+yyTop]), ((Token)yyVals[0+yyTop])), support.getConditionNode(((Node)yyVals[-4+yyTop])), body);
    return yyVal;
}
public Object case203_line824(Object yyVal, Object[] yyVals, int yyTop) {
                    yyVal = support.getOperatorCallNode(support.getOperatorCallNode(((Node)yyVals[-2+yyTop]), "**", ((Node)yyVals[0+yyTop]), getPosition(null)), "-@");
    return yyVal;
}
public Object case531_line2000(Object yyVal, Object[] yyVals, int yyTop) {
                    yyVal = ((Token)yyVals[0+yyTop]);
    return yyVal;
}
public Object case410_line1585(Object yyVal, Object[] yyVals, int yyTop) {
                    yyVal = new ZArrayNode(support.union(((Token)yyVals[-2+yyTop]), ((Token)yyVals[0+yyTop])));
    return yyVal;
}
public Object case374_line1415(Object yyVal, Object[] yyVals, int yyTop) {
                    yyVal = support.new_call(((Node)yyVals[-3+yyTop]), ((Token)yyVals[-1+yyTop]), ((Node)yyVals[0+yyTop]), null);
    return yyVal;
}
public Object case229_line914(Object yyVal, Object[] yyVals, int yyTop) {
                    support.checkExpression(((Node)yyVals[0+yyTop]));
                    yyVal = ((Node)yyVals[0+yyTop]) != null ? ((Node)yyVals[0+yyTop]) : NilImplicitNode.NIL;
    return yyVal;
}
public Object case228_line910(Object yyVal, Object[] yyVals, int yyTop) {
                    yyVal = ((Node)yyVals[0+yyTop]);
    return yyVal;
}
public Object case41_line457(Object yyVal, Object[] yyVals, int yyTop) {
                    support.checkExpression(((Node)yyVals[0+yyTop]));
    return yyVal;
}
public Object case191_line780(Object yyVal, Object[] yyVals, int yyTop) {
                    yyVal = new OpAsgnNode(getPosition(((Node)yyVals[-4+yyTop])), ((Node)yyVals[-4+yyTop]), ((Node)yyVals[0+yyTop]), (String) ((Token)yyVals[-2+yyTop]).getValue(), (String) ((Token)yyVals[-1+yyTop]).getValue());
    return yyVal;
}
public Object case495_line1910(Object yyVal, Object[] yyVals, int yyTop) {
                    yyVal = support.appendToBlock(((ListNode)yyVals[-2+yyTop]), ((Node)yyVals[0+yyTop]));
    return yyVal;
}
public Object case486_line1852(Object yyVal, Object[] yyVals, int yyTop) {
                    support.arg_var(((Token)yyVals[0+yyTop]));
                    yyVal = new ArgumentNode(((ISourcePositionHolder)yyVals[0+yyTop]).getPosition(), (String) ((Token)yyVals[0+yyTop]).getValue());
  /*
                    $$ = new ArgAuxiliaryNode($1.getPosition(), (String) $1.getValue(), 1);
  */
    return yyVal;
}
public Object case472_line1802(Object yyVal, Object[] yyVals, int yyTop) {
                    yyVal = support.new_args(support.union(((ListNode)yyVals[-3+yyTop]), ((BlockArgNode)yyVals[0+yyTop])), null, ((ListNode)yyVals[-3+yyTop]), ((RestArgNode)yyVals[-1+yyTop]), null, ((BlockArgNode)yyVals[0+yyTop]));
    return yyVal;
}
public Object case361_line1357(Object yyVal, Object[] yyVals, int yyTop) {
                    yyVal = null;
    return yyVal;
}
public Object case307_line1187(Object yyVal, Object[] yyVals, int yyTop) {
                    support.setInSingle(support.getInSingle() + 1);
                    support.pushLocalScope();
                    lexer.setState(LexState.EXPR_END); /* force for args */
    return yyVal;
}
public Object case199_line812(Object yyVal, Object[] yyVals, int yyTop) {
                    yyVal = support.getOperatorCallNode(((Node)yyVals[-2+yyTop]), "*", ((Node)yyVals[0+yyTop]), getPosition(null));
    return yyVal;
}
public Object case265_line1033(Object yyVal, Object[] yyVals, int yyTop) {
                    yyVal = new BeginNode(support.union(((Token)yyVals[-2+yyTop]), ((Token)yyVals[0+yyTop])), ((Node)yyVals[-1+yyTop]) == null ? NilImplicitNode.NIL : ((Node)yyVals[-1+yyTop]));
    return yyVal;
}
public Object case275_line1073(Object yyVal, Object[] yyVals, int yyTop) {
                    yyVal = new YieldNode(support.union(((Token)yyVals[-2+yyTop]), ((Token)yyVals[0+yyTop])), null, false);
    return yyVal;
}
public Object case504_line1943(Object yyVal, Object[] yyVals, int yyTop) {
                    yyVal = null;
    return yyVal;
}
public Object case423_line1629(Object yyVal, Object[] yyVals, int yyTop) {
                    yyVal = support.literal_concat(getPosition(((Node)yyVals[-1+yyTop])), ((Node)yyVals[-1+yyTop]), ((Node)yyVals[0+yyTop]));
    return yyVal;
}
public Object case233_line926(Object yyVal, Object[] yyVals, int yyTop) {
                    yyVal = support.newArrayNode(getPosition(((ListNode)yyVals[-1+yyTop])), new HashNode(getPosition(null), ((ListNode)yyVals[-1+yyTop])));
    return yyVal;
}
public Object case483_line1836(Object yyVal, Object[] yyVals, int yyTop) {
                    yyerror("formal argument cannot be a class variable");
    return yyVal;
}
public Object case476_line1814(Object yyVal, Object[] yyVals, int yyTop) {
                    yyVal = support.new_args(support.union(((RestArgNode)yyVals[-1+yyTop]), ((BlockArgNode)yyVals[0+yyTop])), null, null, ((RestArgNode)yyVals[-1+yyTop]), null, ((BlockArgNode)yyVals[0+yyTop]));
    return yyVal;
}
public Object case463_line1772(Object yyVal, Object[] yyVals, int yyTop) {
                    yyVal = ((ArgsNode)yyVals[-1+yyTop]);
                    ((ISourcePositionHolder)yyVal).setPosition(support.union(((Token)yyVals[-2+yyTop]), ((Token)yyVals[0+yyTop])));
                    lexer.setState(LexState.EXPR_BEG);
    return yyVal;
}
public Object case428_line1648(Object yyVal, Object[] yyVals, int yyTop) {
                   lexer.setStrTerm(((StrTerm)yyVals[-2+yyTop]));

                   yyVal = support.newEvStrNode(support.union(((Token)yyVals[-3+yyTop]), ((Token)yyVals[0+yyTop])), ((Node)yyVals[-1+yyTop]));
    return yyVal;
}
public Object case368_line1387(Object yyVal, Object[] yyVals, int yyTop) {
                    yyVal = ((Node)yyVals[-1+yyTop]);
    return yyVal;
}
public Object case349_line1315(Object yyVal, Object[] yyVals, int yyTop) {
                    yyVal = support.new_args(support.union(((ListNode)yyVals[-3+yyTop]), ((BlockArgNode)yyVals[0+yyTop])), null, ((ListNode)yyVals[-3+yyTop]), null, ((ListNode)yyVals[-1+yyTop]), ((BlockArgNode)yyVals[0+yyTop]));
    return yyVal;
}
public Object case2_line281(Object yyVal, Object[] yyVals, int yyTop) {
  /* ENEBO: Removed !compile_for_eval which probably is to reduce warnings*/
                  if (((Node)yyVals[0+yyTop]) != null) {
                      /* last expression should not be void */
                      if (((Node)yyVals[0+yyTop]) instanceof BlockNode) {
                          support.checkUselessStatement(((BlockNode)yyVals[0+yyTop]).getLast());
                      } else {
                          support.checkUselessStatement(((Node)yyVals[0+yyTop]));
                      }
                  }
                  support.getResult().setAST(support.addRootNode(((Node)yyVals[0+yyTop]), getPosition(((Node)yyVals[0+yyTop]))));
    return yyVal;
}
public Object case279_line1085(Object yyVal, Object[] yyVals, int yyTop) {
                    yyVal = support.getOperatorCallNode(NilImplicitNode.NIL, "!");
    return yyVal;
}
public Object case22_line380(Object yyVal, Object[] yyVals, int yyTop) {
                    if (support.isInDef() || support.isInSingle()) {
                        warnings.warn(ID.END_IN_METHOD, getPosition(((Token)yyVals[-3+yyTop])), "END in method; use at_exit");
                    }
                    yyVal = new PostExeNode(getPosition(((Node)yyVals[-1+yyTop])), ((Node)yyVals[-1+yyTop]));
    return yyVal;
}
public Object case499_line1923(Object yyVal, Object[] yyVals, int yyTop) {
                    yyVal = new UnnamedRestArgNode(((Token)yyVals[0+yyTop]).getPosition(), support.getCurrentScope().getLocalScope().addVariable("*"));
    return yyVal;
}
public Object case336_line1274(Object yyVal, Object[] yyVals, int yyTop) {
                    yyVal = new MultipleAsgn19Node(getPosition(((Token)yyVals[0+yyTop])), null, new StarNode(getPosition(null)), null);
    return yyVal;
}
public Object case469_line1793(Object yyVal, Object[] yyVals, int yyTop) {
                    yyVal = support.new_args(support.union(((ListNode)yyVals[-3+yyTop]), ((BlockArgNode)yyVals[0+yyTop])), ((ListNode)yyVals[-3+yyTop]), null, ((RestArgNode)yyVals[-1+yyTop]), null, ((BlockArgNode)yyVals[0+yyTop]));
    return yyVal;
}
public Object case10_line331(Object yyVal, Object[] yyVals, int yyTop) {
                    yyVal = new AliasNode(support.union(((Token)yyVals[-3+yyTop]), ((Token)yyVals[0+yyTop])), (String) ((Token)yyVals[-2+yyTop]).getValue(), (String) ((Token)yyVals[0+yyTop]).getValue());
    return yyVal;
}
public Object case14_line343(Object yyVal, Object[] yyVals, int yyTop) {
                    yyVal = ((Node)yyVals[0+yyTop]);
    return yyVal;
}
public Object case329_line1253(Object yyVal, Object[] yyVals, int yyTop) {
                    yyVal = new MultipleAsgn19Node(getPosition(((ListNode)yyVals[0+yyTop])), ((ListNode)yyVals[0+yyTop]), null, null);
    return yyVal;
}
public Object case27_line415(Object yyVal, Object[] yyVals, int yyTop) {
                    yyVal = new OpAsgnNode(getPosition(((Node)yyVals[-4+yyTop])), ((Node)yyVals[-4+yyTop]), ((Node)yyVals[0+yyTop]), (String) ((Token)yyVals[-2+yyTop]).getValue(), (String) ((Token)yyVals[-1+yyTop]).getValue());
    return yyVal;
}
public Object case234_line930(Object yyVal, Object[] yyVals, int yyTop) {
                    yyVal = ((Node)yyVals[-1+yyTop]);
                    if (yyVal != null) ((Node)yyVal).setPosition(support.union(((Token)yyVals[-2+yyTop]), ((Token)yyVals[0+yyTop])));
    return yyVal;
}
public Object case403_line1514(Object yyVal, Object[] yyVals, int yyTop) {
                    yyVal = ((Node)yyVals[0+yyTop]) instanceof EvStrNode ? new DStrNode(getPosition(((Node)yyVals[0+yyTop]))).add(((Node)yyVals[0+yyTop])) : ((Node)yyVals[0+yyTop]);
                    /*
                    NODE *node = $1;
                    if (!node) {
                        node = NEW_STR(STR_NEW0());
                    } else {
                        node = evstr2dstr(node);
                    }
                    $$ = node;
                    */
    return yyVal;
}
public Object case320_line1226(Object yyVal, Object[] yyVals, int yyTop) {
                    yyVal = new IfNode(getPosition(((Token)yyVals[-4+yyTop])), support.getConditionNode(((Node)yyVals[-3+yyTop])), ((Node)yyVals[-1+yyTop]), ((Node)yyVals[0+yyTop]));
    return yyVal;
}
public Object case249_line972(Object yyVal, Object[] yyVals, int yyTop) {
                    yyVal = support.newArrayNode(getPosition2(((Node)yyVals[0+yyTop])), ((Node)yyVals[0+yyTop]));
    return yyVal;
}
public Object case267_line1038(Object yyVal, Object[] yyVals, int yyTop) {
                    if (warnings.isVerbose()) warnings.warning(ID.GROUPED_EXPRESSION, getPosition(((Token)yyVals[-3+yyTop])), "(...) interpreted as grouped expression");
                    yyVal = ((Node)yyVals[-2+yyTop]);
    return yyVal;
}
public Object case421_line1622(Object yyVal, Object[] yyVals, int yyTop) {
                    yyVal = support.literal_concat(getPosition(((Node)yyVals[-1+yyTop])), ((Node)yyVals[-1+yyTop]), ((Node)yyVals[0+yyTop]));
    return yyVal;
}
public Object case406_line1533(Object yyVal, Object[] yyVals, int yyTop) {
                    yyVal = support.literal_concat(getPosition(((Node)yyVals[-1+yyTop])), ((Node)yyVals[-1+yyTop]), ((Node)yyVals[0+yyTop]));
    return yyVal;
}
public Object case331_line1259(Object yyVal, Object[] yyVals, int yyTop) {
                    yyVal = new MultipleAsgn19Node(getPosition(((ListNode)yyVals[-5+yyTop])), ((ListNode)yyVals[-5+yyTop]), support.assignable(((Token)yyVals[-2+yyTop]), null), ((ListNode)yyVals[0+yyTop]));
    return yyVal;
}
public Object case220_line885(Object yyVal, Object[] yyVals, int yyTop) {
                    yyVal = new NotNode(support.union(((Token)yyVals[-1+yyTop]), ((Node)yyVals[0+yyTop])), support.getConditionNode(((Node)yyVals[0+yyTop])));
    return yyVal;
}
public Object case302_line1163(Object yyVal, Object[] yyVals, int yyTop) {
                    if (support.isInDef() || support.isInSingle()) { 
                        yyerror("module definition in method body");
                    }
                    support.pushLocalScope();
    return yyVal;
}
public Object case104_line676(Object yyVal, Object[] yyVals, int yyTop) {
                   lexer.setState(LexState.EXPR_END);
                   yyVal = ((Token)yyVals[0+yyTop]);
    return yyVal;
}
public Object case489_line1877(Object yyVal, Object[] yyVals, int yyTop) {
                    ((ListNode)yyVals[-2+yyTop]).add(((Node)yyVals[0+yyTop]));
                    yyVal = ((ListNode)yyVals[-2+yyTop]);
    return yyVal;
}
public Object case441_line1713(Object yyVal, Object[] yyVals, int yyTop) {
                     yyVal = support.negateInteger(((Node)yyVals[0+yyTop]));
    return yyVal;
}
public Object case392_line1484(Object yyVal, Object[] yyVals, int yyTop) {
                    yyVal = null; 
    return yyVal;
}
public Object case380_line1434(Object yyVal, Object[] yyVals, int yyTop) {
                    yyVal = support.new_call(((Node)yyVals[-2+yyTop]), new Token("call", ((Node)yyVals[-2+yyTop]).getPosition()), ((Node)yyVals[0+yyTop]), null);
    return yyVal;
}
public Object case373_line1412(Object yyVal, Object[] yyVals, int yyTop) {
                    yyVal = support.new_call(((Node)yyVals[-3+yyTop]), ((Token)yyVals[-1+yyTop]), ((Node)yyVals[0+yyTop]), null);
    return yyVal;
}
public Object case95_line655(Object yyVal, Object[] yyVals, int yyTop) {
                    support.backrefAssignError(((Node)yyVals[0+yyTop]));
    return yyVal;
}
public Object case254_line1008(Object yyVal, Object[] yyVals, int yyTop) {
                    Node node = null;

                    if (((Node)yyVals[0+yyTop]) instanceof ArrayNode &&
                        (node = support.splat_array(((Node)yyVals[-3+yyTop]))) != null) {
                        yyVal = support.list_concat(node, ((Node)yyVals[0+yyTop]));
                    } else {
                        yyVal = support.arg_concat(support.union(((Node)yyVals[-3+yyTop]), ((Node)yyVals[0+yyTop])), ((Node)yyVals[-3+yyTop]), ((Node)yyVals[0+yyTop]));
                    }
    return yyVal;
}
public Object case509_line1968(Object yyVal, Object[] yyVals, int yyTop) {
                    yyVal = ((ListNode)yyVals[-1+yyTop]);
    return yyVal;
}
public Object case438_line1675(Object yyVal, Object[] yyVals, int yyTop) {
                     lexer.setState(LexState.EXPR_END);

                     /* DStrNode: :"some text #{some expression}"*/
                     /* StrNode: :"some text"*/
                     /* EvStrNode :"#{some expression}"*/
                     if (((Node)yyVals[-1+yyTop]) == null) {
                       yyerror("empty symbol literal");
                     }
                     /* FIXME: No node here seems to be an empty string
                        instead of an error
                        if (!($$ = $2)) {
                        $$ = NEW_LIT(ID2SYM(rb_intern("")));
                        }
                     */

                     if (((Node)yyVals[-1+yyTop]) instanceof DStrNode) {
                         yyVal = new DSymbolNode(support.union(((Token)yyVals[-2+yyTop]), ((Token)yyVals[0+yyTop])), ((DStrNode)yyVals[-1+yyTop]));
                     } else {
                       ISourcePosition position = support.union(((Node)yyVals[-1+yyTop]), ((Token)yyVals[0+yyTop]));

                       /* We substract one since tsymbeg is longer than one*/
                       /* and we cannot union it directly so we assume quote*/
                       /* is one character long and subtract for it.*/
                       position.adjustStartOffset(-1);
                       ((Node)yyVals[-1+yyTop]).setPosition(position);

                       yyVal = new DSymbolNode(support.union(((Token)yyVals[-2+yyTop]), ((Token)yyVals[0+yyTop])));
                       ((DSymbolNode)yyVal).add(((Node)yyVals[-1+yyTop]));
                     }
    return yyVal;
}
public Object case76_line569(Object yyVal, Object[] yyVals, int yyTop) {
                    yyVal = support.newArrayNode(((Node)yyVals[-1+yyTop]).getPosition(), ((Node)yyVals[-1+yyTop]));
    return yyVal;
}
public Object case292_line1125(Object yyVal, Object[] yyVals, int yyTop) {
                    yyVal = support.newCaseNode(support.union(((Token)yyVals[-4+yyTop]), ((Token)yyVals[0+yyTop])), ((Node)yyVals[-3+yyTop]), ((Node)yyVals[-1+yyTop]));
    return yyVal;
}
public Object case87_line617(Object yyVal, Object[] yyVals, int yyTop) {
                    support.backrefAssignError(((Node)yyVals[0+yyTop]));
    return yyVal;
}
public Object case427_line1644(Object yyVal, Object[] yyVals, int yyTop) {
                   yyVal = lexer.getStrTerm();
                   lexer.setStrTerm(null);
                   lexer.setState(LexState.EXPR_BEG);
    return yyVal;
}
public Object case363_line1364(Object yyVal, Object[] yyVals, int yyTop) {
                    yyVal = null;
    return yyVal;
}
public Object case184_line725(Object yyVal, Object[] yyVals, int yyTop) {
                    yyVal = support.node_assign(((Node)yyVals[-2+yyTop]), ((Node)yyVals[0+yyTop]));
                    /* FIXME: Consider fixing node_assign itself rather than single case*/
                    ((Node)yyVal).setPosition(support.union(((Node)yyVals[-2+yyTop]), ((Node)yyVals[0+yyTop])));
    return yyVal;
}
public Object case48_line476(Object yyVal, Object[] yyVals, int yyTop) {
                    yyVal = support.new_call(((Node)yyVals[-3+yyTop]), ((Token)yyVals[-1+yyTop]), ((Node)yyVals[0+yyTop]), null);
    return yyVal;
}
public Object case204_line827(Object yyVal, Object[] yyVals, int yyTop) {
                    yyVal = support.getOperatorCallNode(support.getOperatorCallNode(((FloatNode)yyVals[-2+yyTop]), "**", ((Node)yyVals[0+yyTop]), getPosition(null)), "-@");
    return yyVal;
}
public Object case492_line1900(Object yyVal, Object[] yyVals, int yyTop) {
                    yyVal = new BlockNode(getPosition(((Node)yyVals[0+yyTop]))).add(((Node)yyVals[0+yyTop]));
    return yyVal;
}
public Object case482_line1833(Object yyVal, Object[] yyVals, int yyTop) {
                    yyerror("formal argument cannot be a global variable");
    return yyVal;
}
public Object case187_line751(Object yyVal, Object[] yyVals, int yyTop) {
                    support.checkExpression(((Node)yyVals[-2+yyTop]));
                    ISourcePosition position = support.union(((Token)yyVals[-1+yyTop]), ((Node)yyVals[0+yyTop]));
                    Node body = ((Node)yyVals[0+yyTop]) == null ? NilImplicitNode.NIL : ((Node)yyVals[0+yyTop]);
                    Node rescueNode = new RescueNode(position, ((Node)yyVals[-2+yyTop]), new RescueBodyNode(position, null, body, null), null);

                    String asgnOp = (String) ((Token)yyVals[-3+yyTop]).getValue();
                    if (asgnOp.equals("||")) {
                        ((AssignableNode)yyVals[-4+yyTop]).setValueNode(((Node)yyVals[-2+yyTop]));
                        yyVal = new OpAsgnOrNode(support.union(((AssignableNode)yyVals[-4+yyTop]), ((Node)yyVals[-2+yyTop])), support.gettable2(((AssignableNode)yyVals[-4+yyTop])), ((AssignableNode)yyVals[-4+yyTop]));
                    } else if (asgnOp.equals("&&")) {
                        ((AssignableNode)yyVals[-4+yyTop]).setValueNode(((Node)yyVals[-2+yyTop]));
                        yyVal = new OpAsgnAndNode(support.union(((AssignableNode)yyVals[-4+yyTop]), ((Node)yyVals[-2+yyTop])), support.gettable2(((AssignableNode)yyVals[-4+yyTop])), ((AssignableNode)yyVals[-4+yyTop]));
                    } else {
                        ((AssignableNode)yyVals[-4+yyTop]).setValueNode(support.getOperatorCallNode(support.gettable2(((AssignableNode)yyVals[-4+yyTop])), asgnOp, ((Node)yyVals[-2+yyTop])));
                        ((AssignableNode)yyVals[-4+yyTop]).setPosition(support.union(((AssignableNode)yyVals[-4+yyTop]), ((Node)yyVals[-2+yyTop])));
                        yyVal = ((AssignableNode)yyVals[-4+yyTop]);
                    }
    return yyVal;
}
public Object case77_line572(Object yyVal, Object[] yyVals, int yyTop) {
                    yyVal = ((ListNode)yyVals[-2+yyTop]).add(((Node)yyVals[-1+yyTop]));
    return yyVal;
}
public Object case194_line789(Object yyVal, Object[] yyVals, int yyTop) {
                    support.backrefAssignError(((Node)yyVals[-2+yyTop]));
    return yyVal;
}
public Object case240_line942(Object yyVal, Object[] yyVals, int yyTop) {
                    yyVal = support.arg_blk_pass(((Node)yyVals[-1+yyTop]), ((BlockPassNode)yyVals[0+yyTop]));
    return yyVal;
}
public Object case242_line949(Object yyVal, Object[] yyVals, int yyTop) {
                    yyVal = support.arg_append(((Node)yyVals[-3+yyTop]), new HashNode(getPosition(null), ((ListNode)yyVals[-1+yyTop])));
                    yyVal = support.arg_blk_pass((Node)yyVal, ((BlockPassNode)yyVals[0+yyTop]));
    return yyVal;
}
public Object case335_line1271(Object yyVal, Object[] yyVals, int yyTop) {
                    yyVal = new MultipleAsgn19Node(getPosition(((Token)yyVals[-3+yyTop])), null, support.assignable(((Token)yyVals[-2+yyTop]), null), ((ListNode)yyVals[0+yyTop]));
    return yyVal;
}
public Object case195_line792(Object yyVal, Object[] yyVals, int yyTop) {
                    support.checkExpression(((Node)yyVals[-2+yyTop]));
                    support.checkExpression(((Node)yyVals[0+yyTop]));
    
                    boolean isLiteral = ((Node)yyVals[-2+yyTop]) instanceof FixnumNode && ((Node)yyVals[0+yyTop]) instanceof FixnumNode;
                    yyVal = new DotNode(support.union(((Node)yyVals[-2+yyTop]), ((Node)yyVals[0+yyTop])), ((Node)yyVals[-2+yyTop]), ((Node)yyVals[0+yyTop]), false, isLiteral);
    return yyVal;
}
public Object case106_line686(Object yyVal, Object[] yyVals, int yyTop) {
                    yyVal = ((Token)yyVals[0+yyTop]);
    return yyVal;
}
public Object case88_line621(Object yyVal, Object[] yyVals, int yyTop) {
                      /* if (!($$ = assignable($1, 0))) $$ = NEW_BEGIN(0);*/
                    yyVal = support.assignable(((Token)yyVals[0+yyTop]), NilImplicitNode.NIL);
    return yyVal;
}
public Object case205_line830(Object yyVal, Object[] yyVals, int yyTop) {
                    if (support.isLiteral(((Node)yyVals[0+yyTop]))) {
                        yyVal = ((Node)yyVals[0+yyTop]);
                    } else {
                        yyVal = support.getOperatorCallNode(((Node)yyVals[0+yyTop]), "+@");
                    }
    return yyVal;
}
public Object case6_line319(Object yyVal, Object[] yyVals, int yyTop) {
                    yyVal = support.newline_node(((Node)yyVals[0+yyTop]), getPosition(((Node)yyVals[0+yyTop]), true));
    return yyVal;
}
public Object case62_line524(Object yyVal, Object[] yyVals, int yyTop) {
                    yyVal = ((MultipleAsgn19Node)yyVals[0+yyTop]);
    return yyVal;
}
public Object case411_line1588(Object yyVal, Object[] yyVals, int yyTop) {
                    yyVal = ((ListNode)yyVals[-1+yyTop]);
    return yyVal;
}
public Object case353_line1328(Object yyVal, Object[] yyVals, int yyTop) {
    /* was $$ = null;*/
                   yyVal = support.new_args(getPosition(null), null, null, null, null, null);
    return yyVal;
}
public Object case8_line325(Object yyVal, Object[] yyVals, int yyTop) {
                    yyVal = ((Node)yyVals[0+yyTop]);
    return yyVal;
}
public Object case79_line580(Object yyVal, Object[] yyVals, int yyTop) {
                    yyVal = ((ListNode)yyVals[-2+yyTop]).add(((Node)yyVals[0+yyTop]));
    return yyVal;
}
public Object case457_line1751(Object yyVal, Object[] yyVals, int yyTop) {
                    yyVal = ((Node)yyVals[0+yyTop]);
    return yyVal;
}
public Object case375_line1419(Object yyVal, Object[] yyVals, int yyTop) {
                    yyVal = support.new_fcall(((Token)yyVals[-1+yyTop]), ((Node)yyVals[0+yyTop]), null);
    return yyVal;
}
public Object case7_line322(Object yyVal, Object[] yyVals, int yyTop) {
                    yyVal = support.appendToBlock(((Node)yyVals[-2+yyTop]), support.newline_node(((Node)yyVals[0+yyTop]), getPosition(((Node)yyVals[0+yyTop]), true)));
    return yyVal;
}
public Object case66_line538(Object yyVal, Object[] yyVals, int yyTop) {
                    yyVal = new MultipleAsgn19Node(getPosition(((ListNode)yyVals[-2+yyTop])), ((ListNode)yyVals[-2+yyTop]), ((Node)yyVals[0+yyTop]), (ListNode) null);
    return yyVal;
}
public Object case3_line294(Object yyVal, Object[] yyVals, int yyTop) {
                  Node node = ((Node)yyVals[-3+yyTop]);

                  if (((RescueBodyNode)yyVals[-2+yyTop]) != null) {
                      node = new RescueNode(getPosition(((Node)yyVals[-3+yyTop]), true), ((Node)yyVals[-3+yyTop]), ((RescueBodyNode)yyVals[-2+yyTop]), ((Node)yyVals[-1+yyTop]));
                  } else if (((Node)yyVals[-1+yyTop]) != null) {
                      warnings.warn(ID.ELSE_WITHOUT_RESCUE, getPosition(((Node)yyVals[-3+yyTop])), "else without rescue is useless");
                      node = support.appendToBlock(((Node)yyVals[-3+yyTop]), ((Node)yyVals[-1+yyTop]));
                  }
                  if (((Node)yyVals[0+yyTop]) != null) {
                      if (node == null) node = NilImplicitNode.NIL;
                      node = new EnsureNode(getPosition(((Node)yyVals[-3+yyTop])), node, ((Node)yyVals[0+yyTop]));
                  }

                  yyVal = node;
    return yyVal;
}
public Object case488_line1874(Object yyVal, Object[] yyVals, int yyTop) {
                    yyVal = new ArrayNode(getPosition(null), ((Node)yyVals[0+yyTop]));
    return yyVal;
}
public Object case440_line1710(Object yyVal, Object[] yyVals, int yyTop) {
                     yyVal = ((FloatNode)yyVals[0+yyTop]);
    return yyVal;
}
public Object case15_line346(Object yyVal, Object[] yyVals, int yyTop) {
                    yyVal = new IfNode(support.union(((Node)yyVals[-2+yyTop]), ((Node)yyVals[0+yyTop])), support.getConditionNode(((Node)yyVals[0+yyTop])), ((Node)yyVals[-2+yyTop]), null);
    return yyVal;
}
public Object case196_line799(Object yyVal, Object[] yyVals, int yyTop) {
                    support.checkExpression(((Node)yyVals[-2+yyTop]));
                    support.checkExpression(((Node)yyVals[0+yyTop]));

                    boolean isLiteral = ((Node)yyVals[-2+yyTop]) instanceof FixnumNode && ((Node)yyVals[0+yyTop]) instanceof FixnumNode;
                    yyVal = new DotNode(support.union(((Node)yyVals[-2+yyTop]), ((Node)yyVals[0+yyTop])), ((Node)yyVals[-2+yyTop]), ((Node)yyVals[0+yyTop]), true, isLiteral);
    return yyVal;
}
public Object case209_line846(Object yyVal, Object[] yyVals, int yyTop) {
                    yyVal = support.getOperatorCallNode(((Node)yyVals[-2+yyTop]), "&", ((Node)yyVals[0+yyTop]), getPosition(null));
    return yyVal;
}
public Object case513_line1987(Object yyVal, Object[] yyVals, int yyTop) {
                    yyVal = support.newArrayNode(support.union(((Token)yyVals[-1+yyTop]), ((Node)yyVals[0+yyTop])), new SymbolNode(getPosition(((Token)yyVals[-1+yyTop])), (String) ((Token)yyVals[-1+yyTop]).getValue())).add(((Node)yyVals[0+yyTop]));
    return yyVal;
}
public Object case508_line1965(Object yyVal, Object[] yyVals, int yyTop) {
                    yyVal = new ArrayNode(getPosition(null));
    return yyVal;
}
public Object case477_line1817(Object yyVal, Object[] yyVals, int yyTop) {
                    yyVal = support.new_args(support.union(((RestArgNode)yyVals[-3+yyTop]), ((BlockArgNode)yyVals[0+yyTop])), null, null, ((RestArgNode)yyVals[-3+yyTop]), ((ListNode)yyVals[-1+yyTop]), ((BlockArgNode)yyVals[0+yyTop]));
    return yyVal;
}
public Object case448_line1721(Object yyVal, Object[] yyVals, int yyTop) {
                    yyVal = new Token("nil", Tokens.kNIL, ((Token)yyVals[0+yyTop]).getPosition());
    return yyVal;
}
public Object case83_line593(Object yyVal, Object[] yyVals, int yyTop) {
                    yyVal = support.attrset(((Node)yyVals[-2+yyTop]), (String) ((Token)yyVals[0+yyTop]).getValue());
    return yyVal;
}
public Object case23_line386(Object yyVal, Object[] yyVals, int yyTop) {
                    support.checkExpression(((Node)yyVals[0+yyTop]));
                    yyVal = support.node_assign(((Node)yyVals[-2+yyTop]), ((Node)yyVals[0+yyTop]));
    return yyVal;
}
public Object case210_line849(Object yyVal, Object[] yyVals, int yyTop) {
                    yyVal = support.getOperatorCallNode(((Node)yyVals[-2+yyTop]), "<=>", ((Node)yyVals[0+yyTop]), getPosition(null));
    return yyVal;
}
public Object case207_line840(Object yyVal, Object[] yyVals, int yyTop) {
                    yyVal = support.getOperatorCallNode(((Node)yyVals[-2+yyTop]), "|", ((Node)yyVals[0+yyTop]), getPosition(null));
    return yyVal;
}
public Object case67_line541(Object yyVal, Object[] yyVals, int yyTop) {
                    yyVal = new MultipleAsgn19Node(getPosition(((ListNode)yyVals[-4+yyTop])), ((ListNode)yyVals[-4+yyTop]), ((Node)yyVals[-2+yyTop]), ((ListNode)yyVals[0+yyTop]));
    return yyVal;
}
public Object case415_line1600(Object yyVal, Object[] yyVals, int yyTop) {
                     yyVal = support.literal_concat(getPosition(((Node)yyVals[-1+yyTop])), ((Node)yyVals[-1+yyTop]), ((Node)yyVals[0+yyTop]));
    return yyVal;
}
public Object case362_line1361(Object yyVal, Object[] yyVals, int yyTop) {
                    support.new_bv(((Token)yyVals[0+yyTop]));
    return yyVal;
}
public Object case221_line888(Object yyVal, Object[] yyVals, int yyTop) {
                    yyVal = support.getOperatorCallNode(((Node)yyVals[0+yyTop]), "~");
    return yyVal;
}
public Object case53_line495(Object yyVal, Object[] yyVals, int yyTop) {
                    yyVal = support.new_fcall(((Token)yyVals[-2+yyTop]), ((Node)yyVals[-1+yyTop]), ((IterNode)yyVals[0+yyTop]));
    return yyVal;
}
public Object case324_line1236(Object yyVal, Object[] yyVals, int yyTop) {
    return yyVal;
}
public Object case29_line421(Object yyVal, Object[] yyVals, int yyTop) {
                    yyVal = new OpAsgnNode(getPosition(((Node)yyVals[-4+yyTop])), ((Node)yyVals[-4+yyTop]), ((Node)yyVals[0+yyTop]), (String) ((Token)yyVals[-2+yyTop]).getValue(), (String) ((Token)yyVals[-1+yyTop]).getValue());
    return yyVal;
}
					// line 2024 "Ruby19Parser.y"

    /** The parse method use an lexer stream and parse it to an AST node 
     * structure
     */
    public RubyParserResult parse(ParserConfiguration configuration, LexerSource source) {
        support.reset();
        support.setConfiguration(configuration);
        support.setResult(new RubyParserResult());
        
        lexer.reset();
        lexer.setSource(source);
        lexer.setEncoding(configuration.getKCode().getEncoding());
        try {
   //yyparse(lexer, new jay.yydebug.yyAnim("JRuby", 9));
    //yyparse(lexer, new jay.yydebug.yyDebugAdapter());
            yyparse(lexer, null);
        } catch (IOException e) {
            e.printStackTrace();
        } catch (yyException e) {
            e.printStackTrace();
        }
        
        return support.getResult();
    }

    // +++
    // Helper Methods
    
    void yyerrok() {}

    /**
     * Since we can recieve positions at times we know can be null we
     * need an extra safety net here.
     */
    private ISourcePosition getPosition2(ISourcePositionHolder pos) {
        return pos == null ? lexer.getPosition(null, false) : pos.getPosition();
    }

    private ISourcePosition getPosition(ISourcePositionHolder start) {
        return getPosition(start, false);
    }

    private ISourcePosition getPosition(ISourcePositionHolder start, boolean inclusive) {
        if (start != null) {
            return lexer.getPosition(start.getPosition(), inclusive);
        } 

        return lexer.getPosition(null, inclusive);
    }
}
					// line 8124 "-"
