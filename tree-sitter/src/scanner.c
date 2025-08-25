#include <tree_sitter/parser.h>
#include <ctype.h>
#include <stdio.h>

enum TokenType {
    SYMBOL,
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
    case '%':
    case '*':
    case '-':
    case '_':
    case '+':
    case '=':
    case '?':
    case '!':
    case '<':
    case '>':
    case '|':
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

static bool read_symbol(TSLexer *lexer, const bool *valid_symbols) {
    int32_t ch;
    lexer->advance(lexer, false);

    while (true) {
        ch = lexer->lookahead;
        if (!is_symbol_char(ch)) {
            if (lexer->eof || is_whitespace(ch)) break;
            return false;
        }
        lexer->advance(lexer, false);
    }

    lexer->result_symbol = SYMBOL;
    return true;
}

static bool is_end_of_number(TSLexer *lexer, int32_t ch) {
    return is_whitespace(ch) || is_closing_bracket(ch) || lexer->eof;
}

static bool read_number(TSLexer *lexer, const bool *valid_symbols) {
    int32_t ch;
    lexer->advance(lexer, false);

    bool is_float = false;

    while (true) {
        ch = lexer->lookahead;
        if (isdigit(ch)) {
            lexer->advance(lexer, false);
            continue;
        }

        if (ch == '.' && is_float) return false;

        if (ch == '.' && !is_float) {
            is_float = true;
            lexer->advance(lexer, false);
            continue;
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
    if (isdigit(ch)) return read_number(lexer, valid_symbols);

    return false;
}
