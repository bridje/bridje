/**
 * @file Bridje grammar for tree-sitter
 * @author James Henderson <james@bridje.dev>
 * @license MPL
 */

/// <reference types="tree-sitter-cli/dsl" />
// @ts-nocheck

const SYMBOL_HEAD = /[a-zA-Z*_+=?!<>&#]/;
const SYMBOL_CHAR = /[a-zA-Z*\-_+=?!<>&0-9]/;
const SYMBOL_CHAR_NON_DIGIT = /[a-zA-Z*\-_+=?!<>&]/;
const SYMBOL_BODY = choice(
  seq(SYMBOL_HEAD, repeat(SYMBOL_CHAR)),
  seq('-', SYMBOL_CHAR_NON_DIGIT, repeat(SYMBOL_CHAR)),
  '-',
);

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
      $.dot_symbol, $.qualified_dot_symbol,
      $.list, $.vector, $.record, $.set,
      $.call,
      $.record_sugar,
      $.block_call,
      $.quote,
      $.syntax_quote,
      $.unquote_splice,
      $.unquote,
      $.metadata,
    ),

    // ^.member or ^{record} attached to following form
    metadata: $ => seq('^', choice($.dot_symbol, $.qualified_dot_symbol, $.record), $._form),

    string: _ => token(/"([^"]|\\")*"/),

    // foo, or a dotted namespace/class name: brj.core, java.time.Instant
    symbol: _ => token(seq(SYMBOL_BODY, repeat(seq('.', SYMBOL_BODY)), optional('#'))),

    // ns/member or ns.seg/member
    qualified_symbol: _ => token(
      seq(SYMBOL_BODY, repeat(seq('.', SYMBOL_BODY)), '/', SYMBOL_BODY, optional('#')),
    ),

    // .member — a record key or a host member. '?' is a SYMBOL_HEAD character,
    // so optional access (.?member) needs no rule of its own.
    dot_symbol: _ => token(seq('.', SYMBOL_BODY)),

    // ns/.member — qualified by a require alias for a record key, by an import
    // alias for a host member. The alias table tells them apart.
    qualified_dot_symbol: _ => token(
      seq(SYMBOL_BODY, repeat(seq('.', SYMBOL_BODY)), '/', '.', SYMBOL_BODY),
    ),

    int: _ => token(/-?[0-9]+/),
    float: _ => token(/-?[0-9]+\.[0-9]+/),
    bigint: _ => token(seq(/-?[0-9]+/, /[nN]/)),
    bigdec: _ => token(seq(/-?[0-9]+/, optional(seq('.', /[0-9]+/)), /[mM]/)),

    call: $ => seq(
      choice($.symbol, $.qualified_symbol, $.dot_symbol, $.qualified_dot_symbol),
      token.immediate('('), repeat($._form), ')'
    ),

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
    record: $ => seq('{', repeat(seq($._form)), '}'),
    set: $ => seq('#{', repeat($._form), '}'),

    comment: _ => token(/\/\/[^\n]*/),
    discard: $ => seq('#_', $._form),
    quote: $ => seq("'", $._form),
    syntax_quote: $ => seq("`", $._form),
    unquote: $ => seq("~", $._form),
    unquote_splice: $ => seq("~@", $._form),
  },

  extras: $ => [
    /\s/,
    /,/,
    $.comment,
  ],
});
