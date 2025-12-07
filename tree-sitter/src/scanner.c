#include <tree_sitter/parser.h>
#include <ctype.h>
#include <stdio.h>

enum TokenType {
    SYMBOL,
    SYMBOL_PAREN,
    DOT_SYMBOL,
    DOT_SYMBOL_PAREN,
    KEYWORD,
    INT,
    FLOAT,
    BIGINT,
    BIGDEC
};

void *tree_sitter_bridje_external_scanner_create() {
    return NULL;
}

void tree_sitter_bridje_external_scanner_destroy(void *payload) {
}

unsigned tree_sitter_bridje_external_scanner_serialize(void *payload, char *buffer) {
    return 0;
}

void tree_sitter_bridje_external_scanner_deserialize(void *payload, const char *buffer, unsigned length) {
}

static bool is_symbol_head_char(int32_t ch) {
    if (isalpha(ch)) return true;

    switch(ch) {
        case '*':
        case '-':
        case '_':
        case '+':
        case '=':
        case '?':
        case '!':
        case '<':
        case '>':
        case '&':
            return true;
        default:
            return false;
    }
}

static bool is_symbol_char(int32_t ch) {
    return is_symbol_head_char(ch) || isdigit(ch);
}

static bool is_whitespace(int32_t ch) {
    return (isspace(ch) || ch == ',');
}

static bool is_closing_bracket(int32_t ch) {
    return (ch == ']' || ch == ')' || ch == '}');
}

static bool read_symbol_core(TSLexer *lexer) {
    // Assumes first char already validated, advances past it
    lexer->advance(lexer, false);

    while (true) {
        int32_t ch = lexer->lookahead;
        if (!is_symbol_char(ch)) break;
        lexer->advance(lexer, false);
    }
    return true;
}

static bool read_symbol(TSLexer *lexer, const bool *valid_symbols) {
    read_symbol_core(lexer);

    if (lexer->lookahead == '(') {
        lexer->advance(lexer, false);
        lexer->result_symbol = SYMBOL_PAREN;
    } else {
        lexer->result_symbol = SYMBOL;
    }
    return true;
}

static bool read_dot_symbol(TSLexer *lexer, const bool *valid_symbols) {
    // Skip the '.'
    lexer->advance(lexer, false);

    int32_t ch = lexer->lookahead;
    if (!is_symbol_head_char(ch)) return false;

    read_symbol_core(lexer);

    if (lexer->lookahead == '(') {
        lexer->advance(lexer, false);
        lexer->result_symbol = DOT_SYMBOL_PAREN;
    } else {
        lexer->result_symbol = DOT_SYMBOL;
    }
    return true;
}

static bool is_end_of_number(TSLexer *lexer, int32_t ch) {
    return is_whitespace(ch) || is_closing_bracket(ch) || lexer->eof;
}

static bool read_number(TSLexer *lexer, const bool *valid_symbols) {
    int32_t ch;
    lexer->advance(lexer, false);

    bool is_float = false;
    bool needs_mark = false;

    while (true) {
        ch = lexer->lookahead;
        if (isdigit(ch)) {
            lexer->advance(lexer, false);
            continue;
        }

        if (ch == '.' && is_float) return false;

        if (ch == '.' && !is_float) {
            // Peek ahead to see if this is a float or int.method
            lexer->mark_end(lexer);  // Mark before the dot
            lexer->advance(lexer, false);
            if (isdigit(lexer->lookahead)) {
                // It's a float - continue parsing, but need to re-mark at end
                is_float = true;
                needs_mark = true;
                lexer->advance(lexer, false);
                continue;
            }
            // It's an int followed by .symbol - return int (mark_end already set)
            lexer->result_symbol = INT;
            return true;
        }

        if (ch == 'n' || ch == 'N') {
            lexer->advance(lexer, false);
            if (is_float) return false;
            ch = lexer->lookahead;
            lexer->result_symbol = BIGINT;
            return is_end_of_number(lexer, ch);
        }

        if (ch == 'm'|| ch == 'M') {
            lexer->advance(lexer, false);
            ch = lexer->lookahead;
            if (!is_end_of_number(lexer, ch)) return false;
            lexer->result_symbol = BIGDEC;
            return true;
        }

        if (!is_end_of_number(lexer, ch)) return false;

        break;
    }

    if (needs_mark) {
        lexer->mark_end(lexer);
    }
    lexer->result_symbol = is_float ? FLOAT : INT;
    return true;

}

bool tree_sitter_bridje_external_scanner_scan(void *payload, TSLexer *lexer, const bool *valid_symbols) {
    int32_t ch = lexer->lookahead;

    while (is_whitespace(ch)) {
        lexer->advance(lexer, true);
        ch = lexer->lookahead;
    }

    if (is_symbol_head_char(ch)) return read_symbol(lexer, valid_symbols);
    if (ch == '.') return read_dot_symbol(lexer, valid_symbols);
    if (isdigit(ch)) return read_number(lexer, valid_symbols);

    return false;
}
