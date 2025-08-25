/**
 * @file Bridje grammar for tree-sitter
 * @author James Henderson <james@bridje.dev>
 * @license MPL
 */

/// <reference types="tree-sitter-cli/dsl" />
// @ts-nocheck

module.exports = grammar({
  name: "bridje",

  externals: $ => [
    $.symbol, $.keyword,
    $.int, $.float,
    $.bigint, $.bigdec
  ],

  rules: {
    source_file: $ => repeat($._form),

    _form: $ => choice(
      $.int, $.float, $.bigint, $.bigdec,
      $.string, $.keyword, $.symbol,
      $.list, $.vector, $.map, $.set
    ),

    string: _ => token(/"([^"]|\\")*"/),

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
