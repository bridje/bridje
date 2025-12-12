/**
 * @file Bridje grammar for tree-sitter
 * @author James Henderson <james@bridje.dev>
 * @license MPL
 */

/// <reference types="tree-sitter-cli/dsl" />
// @ts-nocheck

module.exports = grammar({
  name: "bridje",

  conflicts: $ => [
    [$.block_call, $.method_call, $.field_access],
  ],

  externals: $ => [
    $.symbol,
    $.dot_symbol,
    $.keyword,
    $.int, $.float,
    $.bigint, $.bigdec,
    $._indent, $._dedent
  ],

  rules: {
    source_file: $ => repeat($._form),

    _form: $ => choice(
      $.int, $.float, $.bigint, $.bigdec,
      $.string, $.keyword, $.symbol,
      $.list, $.vector, $.map, $.set,
      $.call,
      $.block_call,
      $.method_call,
      $.field_access,
    ),

    string: _ => token(/"([^"]|\\")*"/),

    // foo(a, b)
    call: $ => seq($.symbol, token.immediate('('), repeat($._form), ')'),

    // foo: args
    //   body
    block_call: $ => prec.right(seq(
      $.symbol, token.immediate(':'),
      repeat($._form),
      optional($.block_body)
    )),

    block_body: $ => seq(
      $._indent,
      repeat1($._form),
      $._dedent
    ),

    // expr.bar(a, b)
    method_call: $ => seq($._form, $.dot_symbol, token.immediate('('), repeat($._form), ')'),

    // expr.field
    field_access: $ => seq($._form, $.dot_symbol),

    list: $ => seq('(', repeat($._form), ')'),
    vector: $ => seq('[', repeat($._form), ']'),
    map: $ => seq('{', repeat(seq($._form)), '}'),
    set: $ => seq('#{', repeat($._form), '}'),

    comment: _ => token(/\/\/[^\n]*/),
    discard: $ => seq('#_', $._form),
  },

  extras: $ => [
    /\s/,
    /,/,
    $.comment,
  ],
});
