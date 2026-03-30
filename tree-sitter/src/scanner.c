#include <tree_sitter/parser.h>
#include <stdlib.h>
#include <stdbool.h>

enum TokenType {
    INDENT,
    DEDENT,
    NEWLINE,
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

static bool is_inline_whitespace(int32_t ch) {
    return (ch == ' ' || ch == '\t' || ch == ',');
}

static bool is_closing_bracket(int32_t ch) {
    return (ch == ']' || ch == ')' || ch == '}');
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

// Consume newlines and following inline whitespace, return the column of the next content.
static uint16_t skip_to_next_content(TSLexer *lexer) {
    int32_t ch = lexer->lookahead;
    while (ch == '\n') {
        lexer->advance(lexer, true);
        ch = lexer->lookahead;
        while (is_inline_whitespace(ch)) {
            lexer->advance(lexer, true);
            ch = lexer->lookahead;
        }
    }
    return lexer->get_column(lexer);
}

bool tree_sitter_bridje_external_scanner_scan(void *payload, TSLexer *lexer, const bool *valid_symbols) {
    Scanner *scanner = (Scanner *)payload;
    int32_t ch = lexer->lookahead;

    // If we're in the middle of emitting dedents, emit zero-width
    if (scanner->queued_dedent_target >= 0) {
        uint16_t current_indent = get_current_indent(scanner);
        if (valid_symbols[DEDENT] && scanner->queued_dedent_target < current_indent) {
            lexer->mark_end(lexer);
            pop_indent(scanner);
            lexer->result_symbol = DEDENT;
            return true;
        }
        scanner->queued_dedent_target = -1;
    }

    // Skip inline whitespace only (not newlines)
    while (is_inline_whitespace(ch)) {
        lexer->advance(lexer, true);
        ch = lexer->lookahead;
    }

    // Handle newline - this is where indentation logic happens.
    // Always mark_end before consuming whitespace so DEDENT tokens are zero-width.
    // Re-mark after for INDENT/NEWLINE.
    if (ch == '\n') {
        lexer->mark_end(lexer);
        uint16_t indent = skip_to_next_content(lexer);
        uint16_t current_indent = get_current_indent(scanner);

        if (valid_symbols[INDENT] && indent > current_indent) {
            lexer->mark_end(lexer);
            push_indent(scanner, indent);
            lexer->result_symbol = INDENT;
            return true;
        }

        if (valid_symbols[DEDENT] && indent < current_indent) {
            scanner->queued_dedent_target = indent;
            pop_indent(scanner);
            lexer->result_symbol = DEDENT;
            return true;
        }

        if (valid_symbols[NEWLINE] && indent == current_indent) {
            lexer->mark_end(lexer);
            lexer->result_symbol = NEWLINE;
            return true;
        }
    }

    // Handle EOF - if grammar expects DEDENT, emit it
    if (lexer->eof(lexer)) {
        if (valid_symbols[DEDENT]) {
            lexer->mark_end(lexer);
            pop_indent(scanner);
            lexer->result_symbol = DEDENT;
            return true;
        }
        return false;
    }

    // Handle closing brackets - if grammar expects DEDENT, emit it
    if (is_closing_bracket(ch) && valid_symbols[DEDENT]) {
        lexer->mark_end(lexer);
        pop_indent(scanner);
        lexer->result_symbol = DEDENT;
        return true;
    }

    return false;
}
