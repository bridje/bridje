/**
 * @file Bridje grammar for tree-sitter
 * @author James Henderson <james@bridje.dev>
 * @license MPL
 */

/// <reference types="tree-sitter-cli/dsl" />
// @ts-nocheck

const SYMBOL_HEAD = '[a-zA-Z_*!?\\-+<>=]';
const SYMBOL_TAIL = '[a-zA-Z0-9_*!?\\-+<>=\\/]';

const SIGN = '[-+]';
const INT = '(0|[1-9][0-9]*)';
const FRACTION = '[0-9]+';

module.exports = grammar({
  name: "bridje",

  rules: {
    source_file: $ => repeat($._form),

    _form: $ => choice(
      $.nil, $.boolean,
      $.int, $.float, $.bigint, $.bigdec,
      $.string, $.keyword, $.symbol, $.symbol_dot, $.dot_symbol,
      $.list, $.vector, $.map, $.set
    ),

    nil: _ => token(/nil/),
    boolean: _ => token(/true|false/),

    int: _ => token(new RegExp(`${SIGN}?${INT}`)),

    float: _ => token(new RegExp(`${SIGN}?${INT}(\\.${FRACTION})?`)),
    bigint: _ => token(new RegExp(`${SIGN}?${INT}?N`)),
    bigdec: _ => token(new RegExp(`${SIGN}?${INT}(\\.${FRACTION})?M`)),

    string: _ => token(/"([^"]|\\")*"/),

    keyword: _ => token(new RegExp(`:${SYMBOL_HEAD}${SYMBOL_TAIL}*`)),

    dot_symbol: _ => token(new RegExp(`\\.${SYMBOL_HEAD}${SYMBOL_TAIL}*`)),
    symbol_dot: _ => token(new RegExp(`${SYMBOL_HEAD}${SYMBOL_TAIL}*\\.`)),
    symbol: _ => token(new RegExp(`${SYMBOL_HEAD}${SYMBOL_TAIL}*`)),

    list: $ => seq('(', repeat($._form), ')'),
    vector: $ => seq('[', repeat($._form), ']'),
    map: $ => seq('{', repeat(seq($._form)), '}'),
    set: $ => seq('#{', repeat($._form), '}'),

    comment: _ => token(/;[^\n]*/),
    discard: $ => seq('#_', $._form),
  },

  extras: $ => [
    /\s/,
    /,/,
    $.comment,
    $.discard,
  ],
});
