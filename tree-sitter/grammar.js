/**
 * @file Bridje grammar for tree-sitter
 * @author James Henderson <james@bridje.dev>
 * @license MPL
 */

/// <reference types="tree-sitter-cli/dsl" />
// @ts-nocheck

const SYMBOL_HEAD = /[a-zA-Z*\-_+=?!<>&]/;
const SYMBOL_CHAR = /[a-zA-Z*\-_+=?!<>&0-9]/;
const SYMBOL_BODY = seq(SYMBOL_HEAD, repeat(SYMBOL_CHAR));

module.exports = grammar({
  name: "bridje",

  conflicts: $ => [
  ],

  externals: $ => [
    $._indent, $._dedent, $._newline,
  ],

  rules: {
    source_file: $ => repeat($._form),

    _form: $ => choice(
      $.int, $.float, $.bigint, $.bigdec,
      $.string, $.symbol, $.qualified_symbol,
      $.keyword,
      $.list, $.vector, $.map, $.set,
      $.call,
      $.record_sugar,
      $.block_call,
      $.quote,
      $.unquote_splice,
      $.unquote,
      $.metadata,
    ),

    // ^keyword or ^{map} attached to following form
    metadata: $ => seq('^', choice($.keyword, $.map), $._form),

    string: _ => token(/"([^"]|\\")*"/),

    symbol: _ => token(seq(SYMBOL_BODY, optional('#'))),

    qualified_symbol: _ => token(choice(
      // ns/member or ns.seg/member
      seq(SYMBOL_BODY, repeat(seq('.', SYMBOL_BODY)), '/', SYMBOL_BODY, optional('#')),
      // dotted namespace name (no /member)
      seq(SYMBOL_BODY, repeat1(seq('.', SYMBOL_BODY)), optional('#')),
    )),

    keyword: _ => token(choice(
      // qualified: ns/.member or ns.seg/.member
      seq(SYMBOL_BODY, repeat(seq('.', SYMBOL_BODY)), '/', '.', SYMBOL_BODY),
      // simple: .member
      seq('.', SYMBOL_BODY),
    )),

    int: _ => token(/[0-9]+/),
    float: _ => token(/[0-9]+\.[0-9]+/),
    bigint: _ => token(seq(/[0-9]+/, /[nN]/)),
    bigdec: _ => token(seq(/[0-9]+/, optional(seq('.', /[0-9]+/)), /[mM]/)),

    // foo(a, b) or ns:foo(a, b)
    call: $ => seq(choice($.symbol, $.qualified_symbol, $.keyword), token.immediate('('), repeat($._form), ')'),

    // Foo{a b} — record construction sugar, desugars to Foo({a b})
    record_sugar: $ => seq(choice($.symbol, $.qualified_symbol), token.immediate('{'), repeat($._form), '}'),

    // foo: args
    //   body
    block_call: $ => prec.right(seq(
      choice($.symbol, $.qualified_symbol), token.immediate(':'),
      repeat($._form),
      optional($._newline),
      optional($.block_body)
    )),

    block_body: $ => seq(
      $._indent,
      repeat1($._form),
      $._dedent
    ),

    list: $ => seq('(', repeat($._form), ')'),
    vector: $ => seq('[', repeat($._form), ']'),
    map: $ => seq('{', repeat(seq($._form)), '}'),
    set: $ => seq('#{', repeat($._form), '}'),

    comment: _ => token(/\/\/[^\n]*/),
    discard: $ => seq('#_', $._form),
    quote: $ => seq("'", $._form),
    unquote: $ => seq("~", $._form),
    unquote_splice: $ => seq("~@", $._form),
  },

  extras: $ => [
    /\s/,
    /,/,
    $.comment,
  ],
});
