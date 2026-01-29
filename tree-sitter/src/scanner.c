#include <tree_sitter/parser.h>
#include <ctype.h>
#include <stdio.h>
#include <stdlib.h>
#include <stdbool.h>

enum TokenType {
    SYMBOL,
    QUALIFIED_SYMBOL,
    DOT_SYMBOL,
    INT,
    FLOAT,
    BIGINT,
    BIGDEC,
    CARET,
    INDENT,
    DEDENT,
    NEWLINE
};

#define MAX_INDENT_DEPTH 100

typedef struct {
    uint16_t indent_stack[MAX_INDENT_DEPTH];
    uint8_t stack_size;
    int16_t queued_dedent_target;  // -1 if not dedenting, else indent level we're dedenting towards
} Scanner;

void *tree_sitter_bridje_external_scanner_create() {
    Scanner *scanner = malloc(sizeof(Scanner));
    scanner->stack_size = 1;
    scanner->indent_stack[0] = 0;
    scanner->queued_dedent_target = -1;
    return scanner;
}

void tree_sitter_bridje_external_scanner_destroy(void *payload) {
    free(payload);
}

unsigned tree_sitter_bridje_external_scanner_serialize(void *payload, char *buffer) {
    Scanner *scanner = (Scanner *)payload;
    unsigned size = 0;

    buffer[size++] = scanner->stack_size;
    buffer[size++] = scanner->queued_dedent_target & 0xFF;
    buffer[size++] = (scanner->queued_dedent_target >> 8) & 0xFF;

    for (uint8_t i = 0; i < scanner->stack_size; i++) {
        buffer[size++] = scanner->indent_stack[i] & 0xFF;
        buffer[size++] = (scanner->indent_stack[i] >> 8) & 0xFF;
    }

    return size;
}

void tree_sitter_bridje_external_scanner_deserialize(void *payload, const char *buffer, unsigned length) {
    Scanner *scanner = (Scanner *)payload;

    if (length == 0) {
        scanner->stack_size = 1;
        scanner->indent_stack[0] = 0;
        scanner->queued_dedent_target = -1;
        return;
    }

    unsigned pos = 0;
    scanner->stack_size = buffer[pos++];
    scanner->queued_dedent_target = (int16_t)((uint8_t)buffer[pos] | ((uint8_t)buffer[pos + 1] << 8));
    pos += 2;

    for (uint8_t i = 0; i < scanner->stack_size; i++) {
        scanner->indent_stack[i] = (uint8_t)buffer[pos] | ((uint8_t)buffer[pos + 1] << 8);
        pos += 2;
    }
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

static bool is_inline_whitespace(int32_t ch) {
    return (ch == ' ' || ch == '\t' || ch == ',');
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

static bool read_symbol(TSLexer *lexer) {
    read_symbol_core(lexer);
    bool has_colon = false;

    // Check for colon - namespace separator (ns:member) or block call (foo:)
    while (lexer->lookahead == ':') {
        lexer->mark_end(lexer);  // Mark before colon in case it's a block call
        lexer->advance(lexer, false);
        if (is_symbol_head_char(lexer->lookahead)) {
            // Qualified symbol - continue reading
            has_colon = true;
            read_symbol_core(lexer);
        } else {
            // Block call - return symbol without the colon (mark_end already set)
            lexer->result_symbol = SYMBOL;
            return true;
        }
    }

    // Check for trailing # (gensym syntax)
    if (lexer->lookahead == '#') {
        lexer->advance(lexer, false);
    }

    lexer->mark_end(lexer);  // Mark final position
    lexer->result_symbol = has_colon ? QUALIFIED_SYMBOL : SYMBOL;
    return true;
}

static bool read_dot_symbol(TSLexer *lexer) {
    // Skip the '.'
    lexer->advance(lexer, false);

    int32_t ch = lexer->lookahead;
    if (!is_symbol_head_char(ch)) return false;

    read_symbol_core(lexer);
    lexer->result_symbol = DOT_SYMBOL;
    return true;
}

static bool is_end_of_number(TSLexer *lexer, int32_t ch) {
    return is_inline_whitespace(ch) || is_closing_bracket(ch) || ch == '\n' || lexer->eof(lexer);
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
            if (!is_end_of_number(lexer, ch)) return false;
            lexer->mark_end(lexer);
            lexer->result_symbol = BIGINT;
            return true;
        }

        if (ch == 'm'|| ch == 'M') {
            lexer->advance(lexer, false);
            ch = lexer->lookahead;
            if (!is_end_of_number(lexer, ch)) return false;
            lexer->mark_end(lexer);
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

static uint16_t get_current_indent(Scanner *scanner) {
    return scanner->indent_stack[scanner->stack_size - 1];
}

static void push_indent(Scanner *scanner, uint16_t indent) {
    if (scanner->stack_size < MAX_INDENT_DEPTH) {
        scanner->indent_stack[scanner->stack_size++] = indent;
    }
}

static void pop_indent(Scanner *scanner) {
    if (scanner->stack_size > 1) {
        scanner->stack_size--;
    }
}

bool tree_sitter_bridje_external_scanner_scan(void *payload, TSLexer *lexer, const bool *valid_symbols) {
    Scanner *scanner = (Scanner *)payload;
    int32_t ch = lexer->lookahead;

    // If we're in the middle of emitting dedents, continue until done
    if (scanner->queued_dedent_target >= 0) {
        uint16_t current_indent = get_current_indent(scanner);
        if (valid_symbols[DEDENT] && scanner->queued_dedent_target < current_indent) {
            pop_indent(scanner);
            lexer->result_symbol = DEDENT;
            return true;
        }
        // Done dedenting
        scanner->queued_dedent_target = -1;
    }

    // Skip inline whitespace only (not newlines)
    while (is_inline_whitespace(ch)) {
        lexer->advance(lexer, true);
        ch = lexer->lookahead;
    }

    // Handle newline - this is where indentation logic happens
    if (ch == '\n') {
        // Skip the newline and any following blank lines
        while (ch == '\n') {
            lexer->advance(lexer, true);
            ch = lexer->lookahead;
            // Skip inline whitespace after newline
            while (is_inline_whitespace(ch)) {
                lexer->advance(lexer, true);
                ch = lexer->lookahead;
            }
        }

        uint16_t indent = lexer->get_column(lexer);
        uint16_t current_indent = get_current_indent(scanner);

        // Check for INDENT
        if (valid_symbols[INDENT] && indent > current_indent) {
            push_indent(scanner, indent);
            lexer->result_symbol = INDENT;
            return true;
        }

        // Check for DEDENT - start dedenting
        if (valid_symbols[DEDENT] && indent < current_indent) {
            scanner->queued_dedent_target = indent;
            pop_indent(scanner);
            lexer->result_symbol = DEDENT;
            return true;
        }

        // Check for NEWLINE at same indentation
        if (valid_symbols[NEWLINE] && indent == current_indent) {
            lexer->result_symbol = NEWLINE;
            return true;
        }
    }

    // Handle EOF - if grammar expects DEDENT, emit it
    if (lexer->eof(lexer)) {
        if (valid_symbols[DEDENT]) {
            pop_indent(scanner);
            lexer->result_symbol = DEDENT;
            return true;
        }
        return false;
    }

    // Handle closing brackets - if grammar expects DEDENT, emit it
    if (is_closing_bracket(ch) && valid_symbols[DEDENT]) {
        pop_indent(scanner);
        lexer->result_symbol = DEDENT;
        return true;
    }

    // Now scan actual tokens
    if (ch == '^' && valid_symbols[CARET]) {
        lexer->advance(lexer, false);
        ch = lexer->lookahead;
        if (is_symbol_head_char(ch) || ch == '{') {
            lexer->result_symbol = CARET;
            return true;
        }
        return false;
    }
    if (is_symbol_head_char(ch)) return read_symbol(lexer);
    if (ch == '.') return read_dot_symbol(lexer);
    if (isdigit(ch)) return read_number(lexer, valid_symbols);

    return false;
}
