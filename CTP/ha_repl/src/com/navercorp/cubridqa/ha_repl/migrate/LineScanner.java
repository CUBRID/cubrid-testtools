/**
 * Copyright (c) 2016, Search Solution Corporation. All rights reserved.
 * 
 * Redistribution and use in source and binary forms, with or without 
 * modification, are permitted provided that the following conditions are met:
 * 
 *   * Redistributions of source code must retain the above copyright notice, 
 *     this list of conditions and the following disclaimer.
 * 
 *   * Redistributions in binary form must reproduce the above copyright 
 *     notice, this list of conditions and the following disclaimer in 
 *     the documentation and/or other materials provided with the distribution.
 * 
 *   * Neither the name of the copyright holder nor the names of its contributors may be used to endorse or promote products 
 *     derived from this software without specific prior written permission.
 * 
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, 
 * INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE 
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, 
 * SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR 
 * SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY,
 * WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE 
 * USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE. 
 */
package com.navercorp.cubridqa.ha_repl.migrate;

// Copied from CTP/sql/src/com/navercorp/cubridqa/cqt/common/LineScanner.java
// This class is a porting of csql_walk_statement() function
// contained in $CUBRID/src/executables/csql_support.c

class LineScanner {

    LineScanner() {
        clear();
    }

    void clear() {
        state = State.GENERAL;
        substate = Substate.INITIAL;
        plcsqlBeginEndBalance = 0;
        plcsqlNestLevel = 0;
    }

    boolean isStatementComplete() {
        return (state == State.STATEMENT_END);
    }

    boolean isInPlcsqlText() {
        return (substate == Substate.PLCSQL_TEXT || substate == Substate.SEEN_END);
    }

    void scan(String line) {

        assert (line != null);
        assert (state
                != State.STATEMENT_END); // the state must be cleared to State.GENERAL before this
        // call
        assert ((plcsqlBeginEndBalance == 0 && plcsqlNestLevel == 0)
                || (substate == Substate.PLCSQL_TEXT || substate == Substate.SEEN_END));

        boolean stmtComplete = false; // corresponds to !is_last_stmt_valid in csql_walk_statement()

        if (state == State.CPP_COMMENT || state == State.SQL_COMMENT) {
            // the previous line is a single line comment and we're parsing a new line
            state = State.GENERAL;
        }

        int lineLen = line.length();
        main_loop:
        for (int i = 0; i < lineLen; i++) {
            char c = line.charAt(i);

            switch (state) {
                case GENERAL:

                    // eat up blanks
                    if (isBlankLetter(c)) {
                        continue;
                    }

                    int d = doSubstateTransition(line, i);
                    if (d >= 0) {
                        i += d;
                        continue;
                    }

                    if (isIdentifierLetter(c)) {
                        if (stmtComplete) {
                            stmtComplete = false;
                        }

                        while (i + 1 < lineLen && isIdentifierLetter(line.charAt(i + 1))) {
                            i++;
                        }
                        continue;
                    }

                    char cc;
                    switch (c) {
                        case '/':
                            cc = charAtStr(line, i + 1);
                            if (cc == '/') {
                                state = State.CPP_COMMENT;
                                i++;
                                break;
                            }
                            if (cc == '*') {
                                state = State.C_COMMENT;
                                i++;
                                break;
                            }

                            stmtComplete = false;
                            break;

                        case '-':
                            if (charAtStr(line, i + 1) == '-') {
                                state = State.CPP_COMMENT;
                                i++;
                                break;
                            }

                            stmtComplete = false;
                            break;

                        case '\'':
                            state = State.SINGLE_QUOTE;
                            stmtComplete = false;
                            break;

                        case '"':
                            /*if (prm_get_bool_value (PRM_ID_ANSI_QUOTES) == false) {   TODO: can we see the parameter?
                              state = State.MYSQL_QUOTE;
                            } else*/ {
                                state = State.DOUBLE_QUOTE_IDENTIFIER;
                            }

                            stmtComplete = false;
                            break;

                        case '`':
                            state = State.BACKTICK_IDENTIFIER;
                            stmtComplete = false;
                            break;

                        case '[':
                            state = State.BRACKET_IDENTIFIER;
                            stmtComplete = false;
                            break;

                        case ';':
                            if (substate != Substate.PLCSQL_TEXT) {
                                assert (substate != Substate.SEEN_END);

                                stmtComplete = true;
                                // initialize the state variables used to identify PL/CSQL text
                                substate = Substate.INITIAL;
                                plcsqlBeginEndBalance = 0;
                                plcsqlNestLevel = 0;
                            }
                            break;

                        case ' ':
                        case '\t':
                        case '\r':
                        case '\n':
                            assert (false);
                            break;
                        default:
                            if (stmtComplete) {
                                stmtComplete = false;
                            }
                    }

                    break;

                case C_COMMENT:
                    if (c == '*' && charAtStr(line, i + 1) == '/') {
                        state = State.GENERAL;
                        i++;
                    }
                    break;

                case CPP_COMMENT:
                case SQL_COMMENT:
                    if (c == '\n') {
                        state = State.GENERAL;
                    }
                    break;

                case SINGLE_QUOTE:
                    /*if (prm_get_bool_value (PRM_ID_NO_BACKSLASH_ESCAPES) == false &&
                          c == '\\') {      TODO: can we see the parameter?
                        i++;
                    } else */ if (c == '\'') {
                        if (charAtStr(line, i + 1) == '\'') {
                            // escape by ''
                            i++;
                        } else {
                            state = State.GENERAL;
                        }
                    }
                    break;

                case MYSQL_QUOTE:
                    /*if (prm_get_bool_value (PRM_ID_NO_BACKSLASH_ESCAPES) == false &&
                          c == '\\') {      TODO: can we see the parameter?
                        i++;
                    } else */ if (c == '"') {
                        if (charAtStr(line, i + 1) == '"') {
                            // escape by ""
                            i++;
                        } else {
                            state = State.GENERAL;
                        }
                    }
                    break;

                case DOUBLE_QUOTE_IDENTIFIER:
                    if (c == '"') {
                        state = State.GENERAL;
                    }
                    break;

                case BACKTICK_IDENTIFIER:
                    if (c == '`') {
                        state = State.GENERAL;
                    }
                    break;

                case BRACKET_IDENTIFIER:
                    if (c == ']') {
                        state = State.GENERAL;
                    }
                    break;

                default:
                    assert false;
            }
        }

        switch (state) {
            case SQL_COMMENT:
            case CPP_COMMENT:
            case GENERAL:
                if (stmtComplete) {
                    state = State.STATEMENT_END;
                }
        }
    }

    private int doSubstateTransition(String line, int i) {

        int d;

        substate_transition:
        while (true) {

            switch (substate) {
                case INITIAL:
                    d = matchWordCI(line, "create", i);
                    if (d >= 0) {
                        substate = Substate.SEEN_CREATE;
                        return d;
                    } else {
                        // keep the substate Substate.INITIAL
                    }
                    break;

                case SEEN_CREATE:
                    d = matchWordCI(line, "or", i);
                    if (d >= 0) {
                        substate = Substate.SEEN_OR;
                        return d;
                    } else if ((d = matchWordCI(line, "procedure", i)) >= 0
                            || (d = matchWordCI(line, "function", i)) >= 0) {
                        substate = Substate.EXPECTING_IS_OR_AS;
                        return d;
                    } else {
                        substate = Substate.INITIAL;
                    }
                    break;

                case SEEN_OR:
                    d = matchWordCI(line, "replace", i);
                    if (d >= 0) {
                        substate = Substate.SEEN_REPLACE;
                        return d;
                    } else {
                        substate = Substate.INITIAL;
                    }
                    break;

                case SEEN_REPLACE:
                    if ((d = matchWordCI(line, "procedure", i)) >= 0
                            || (d = matchWordCI(line, "function", i)) >= 0) {
                        substate = Substate.EXPECTING_IS_OR_AS;
                        return d;
                    } else {
                        substate = Substate.INITIAL;
                    }
                    break;

                case EXPECTING_IS_OR_AS:
                    if ((d = matchWordCI(line, "is", i)) >= 0
                            || (d = matchWordCI(line, "as", i)) >= 0) {
                        substate = Substate.PL_LANG_SPEC;
                        return d;
                    } else {
                        // keep the substate Substate.EXPECTING_IS_OR_AS
                    }
                    break;

                case PL_LANG_SPEC:
                    d = matchWordCI(line, "language", i);
                    if (d >= 0) {
                        substate = Substate.SEEN_LANGUAGE;
                        return d;
                    } else {
                        substate = Substate.PLCSQL_TEXT;
                        plcsqlBeginEndBalance = 0;
                        plcsqlNestLevel = 0;
                        continue substate_transition; // repeat a substate transition without
                        // increasing i
                    }

                    // break; unreachable

                case SEEN_LANGUAGE:
                    d = matchWordCI(line, "java", i);
                    if (d >= 0) {
                        substate = Substate.INITIAL;
                        return d;
                    } else if ((d = matchWordCI(line, "plcsql", i)) >= 0) {
                        substate = Substate.PLCSQL_TEXT;
                        plcsqlBeginEndBalance = 0;
                        plcsqlNestLevel = 0;
                        return d;
                    } else {
                        // syntax error
                        substate = Substate.INITIAL;
                    }
                    break;

                case PLCSQL_TEXT:
                    if ((d = matchWordCI(line, "procedure", i)) >= 0
                            || (d = matchWordCI(line, "function", i)) >= 0) {
                        if (plcsqlBeginEndBalance == 0) {
                            plcsqlNestLevel++;
                        }
                        return d;
                    } else if ((d = matchWordCI(line, "case", i)) >= 0) {
                        // case can start an expression and can appear in a balance 0 area
                        if (plcsqlBeginEndBalance == 0) {
                            plcsqlNestLevel++;
                        }
                        plcsqlBeginEndBalance++;
                        return d;
                    } else if ((d = matchWordCI(line, "begin", i)) >= 0
                            || (d = matchWordCI(line, "if", i)) >= 0
                            || (d = matchWordCI(line, "loop", i)) >= 0) {
                        plcsqlBeginEndBalance++;
                        return d;
                    } else if ((d = matchWordCI(line, "end", i)) >= 0) {
                        substate = Substate.SEEN_END;
                        return d;
                    } else {
                        // keep the substate Substate.PLCSQL_TEXT
                    }
                    break;

                case SEEN_END:
                    plcsqlBeginEndBalance--;
                    if (plcsqlBeginEndBalance < 0) {
                        // syntax error
                        plcsqlBeginEndBalance = 0;
                    }
                    if (plcsqlBeginEndBalance == 0) {
                        plcsqlNestLevel--;
                        if (plcsqlNestLevel < 0) {
                            // the last END closing PL/CSQL text was found
                            substate = Substate.INITIAL;
                            plcsqlBeginEndBalance = 0;
                            plcsqlNestLevel = 0;
                            continue substate_transition; // repeat a substate transition without
                            // increasing i
                        }
                    }

                    substate = Substate.PLCSQL_TEXT;

                    // match if/case/loop if exists, but just advance p and ignore them
                    if ((d = matchWordCI(line, "if", i)) >= 0
                            || (d = matchWordCI(line, "case", i)) >= 0
                            || (d = matchWordCI(line, "loop", i)) >= 0) {
                        return d;
                    } else {
                        continue substate_transition; // repeat a substate transition without
                        // increasing i
                    }

                    // break; unreachable

                default:
                    assert (false);
            }

            break;
        }

        return -1; // no match
    }

    private enum State {
        GENERAL,
        C_COMMENT,
        CPP_COMMENT,
        SQL_COMMENT,
        SINGLE_QUOTE,
        MYSQL_QUOTE,
        DOUBLE_QUOTE_IDENTIFIER,
        BACKTICK_IDENTIFIER,
        BRACKET_IDENTIFIER,
        STATEMENT_END
    }

    private enum Substate {
        INITIAL,
        SEEN_CREATE,
        SEEN_OR,
        SEEN_REPLACE,
        EXPECTING_IS_OR_AS,
        PL_LANG_SPEC,
        SEEN_LANGUAGE,
        PLCSQL_TEXT,
        SEEN_END
    }

    private State state;
    private Substate substate;
    private int plcsqlBeginEndBalance;
    private int plcsqlNestLevel;

    private boolean isBlankLetter(final char c) {
        switch (c) {
            case ' ':
            case '\t':
            case '\r':
            case '\n':
                return true;
            default:
                return false;
        }
    }

    private boolean isIdentifierLetter(final char c) {
        return (c >= 'a' && c <= 'z')
                || (c >= 'A' && c <= 'Z')
                || (c >= '0' && c <= '9')
                || (c == '_');
    }

    private int matchWordCI(String line, String word, int offset) {
        int len = word.length();
        assert len > 0;

        if (line.regionMatches(true, offset, word, 0, len)
                &&
                // next char is absent or is not an identifier letter
                (offset + len == line.length() || !isIdentifierLetter(line.charAt(offset + len)))) {
            return len - 1;
        } else {
            return -1;
        }
    }

    private char charAtStr(String s, int i) {
        if (i < s.length()) {
            return s.charAt(i);
        } else {
            return 0;
        }
    }
}
