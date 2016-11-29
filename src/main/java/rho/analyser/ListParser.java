package rho.analyser;

import org.pcollections.Empty;
import org.pcollections.PVector;
import rho.analyser.ParseException.MultipleParseFailsException;
import rho.reader.Form;
import rho.types.Type;
import rho.util.Pair;

import java.util.function.Function;

import static rho.analyser.ListParser.ParseResult.fail;
import static rho.analyser.ListParser.ParseResult.success;
import static rho.util.Pair.pair;

interface ListParser<T> {

    interface ParseResult<T> {
        <U> ParseResult<U> bind(Function<T, ParseResult<U>> fn);

        ParseResult<T> fmapError(Function<ParseException, ParseException> fn);

        T orThrow();

        <U> ParseResult<U> fmap(Function<T, U> fn);

        <U> U accept(Function<Success<T>, U> successFn, Function<Fail<T>, U> failFn);

        static <T> ParseResult<T> success(T t) {
            return new Success<>(t);
        }

        static <T> ParseResult<T> fail(ParseException e) {
            return new Fail<>(e);
        }

        class Success<T> implements ParseResult<T> {

            final T result;

            public Success(T result) {
                this.result = result;
            }

            @Override
            public <U> ParseResult<U> bind(Function<T, ParseResult<U>> fn) {
                return fn.apply(result);
            }

            @Override
            public ParseResult<T> fmapError(Function<ParseException, ParseException> fn) {
                return this;
            }

            @Override
            public T orThrow() {
                return result;
            }

            @Override
            public <U> ParseResult<U> fmap(Function<T, U> fn) {
                return new Success<>(fn.apply(result));
            }

            @Override
            public <U> U accept(Function<Success<T>, U> successFn, Function<Fail<T>, U> failFn) {
                return successFn.apply(this);
            }
        }

        class Fail<T> implements ParseResult<T> {

            final ParseException error;

            public Fail(ParseException error) {
                this.error = error;
            }

            @Override
            public <U> ParseResult<U> bind(Function<T, ParseResult<U>> fn) {
                return new Fail<>(error);
            }

            @Override
            public ParseResult<T> fmapError(Function<ParseException, ParseException> fn) {
                return new Fail<>(fn.apply(error));
            }

            @Override
            public T orThrow() {
                throw error;
            }

            @Override
            public <U> ParseResult<U> fmap(Function<T, U> fn) {
                return new Fail<>(error);
            }

            @Override
            public <U> U accept(Function<Success<T>, U> successFn, Function<Fail<T>, U> failFn) {
                return failFn.apply(this);
            }
        }
    }

    ParseResult<Pair<T, PVector<Form>>> parse(PVector<Form> forms);

    static <T> ListParser<T> pure(T t) {
        return forms ->
            success(pair(t, forms));
    }

    default <U> ListParser<U> bind(Function<T, ListParser<U>> fn) {
        return forms -> this.parse(forms).bind(success -> fn.apply(success.left).parse(success.right));
    }

    default <U> ListParser<U> fmap(Function<T, U> fn) {
        return forms -> parse(forms).fmap(result -> result.fmapLeft(fn));
    }

    default ListParser<T> fmapError(Function<ParseException, ParseException> fn) {
        return forms -> parse(forms).fmapError(fn);
    }

    static <T> ListParser<T> parseEnd(T result) {
        return forms -> {
            if (forms.isEmpty()) {
                return success(pair(result, forms));
            } else {
                return fail(new ParseException.ExtraFormsException(forms));
            }
        };
    }

    interface FormParser<T> {
        ParseResult<T> parseForm(Form form);
    }

    static <T extends Form> ListParser<T> formTypeParser(Class<T> clazz) {
        return oneOf(form -> {
            if (clazz.isAssignableFrom(form.getClass())) {
                return success(clazz.cast(form));
            } else {
                return fail(new ParseException.UnexpectedFormTypeException(form));
            }
        });
    }

    ListParser<Form.SymbolForm> SYMBOL_PARSER = formTypeParser(Form.SymbolForm.class);
    ListParser<Form.ListForm> LIST_PARSER = formTypeParser(Form.ListForm.class);
    ListParser<Form.VectorForm> VECTOR_PARSER = formTypeParser(Form.VectorForm.class);

    static ListParser<Type> typeParser(LocalTypeEnv localTypeEnv) {
        return oneOf(form -> {
            try {
                return success(TypeAnalyser.analyzeType(form, localTypeEnv));
            } catch (Exception e) {
                return fail(new ParseException.TypeParseException(e));
            }
        });
    }

    static <T> ListParser<T> oneOf(FormParser<T> formParser) {
        return forms -> {
            if (forms.isEmpty()) {
                return fail(new ParseException());
            } else {
                return formParser.parseForm(forms.get(0)).fmap(t -> pair(t, forms.minus(0)));
            }
        };
    }

    static <T, U> ListParser<U> nestedListParser(PVector<Form> nestedForms, ListParser<T> nestedParser, Function<T, ListParser<U>> fn) {
        return forms ->
            nestedParser.parse(nestedForms).bind(pair ->
                fn.apply(pair.left).parse(forms));
    }

    static <T> ListParser<PVector<T>> manyOf(ListParser<T> parser) {
        return forms -> {
            PVector<T> result = Empty.vector();
            while (!forms.isEmpty()) {
                ParseResult<Pair<T, PVector<Form>>> res = parser.parse(forms);
                if (res instanceof ParseResult.Success) {
                    Pair<T, PVector<Form>> success = ((ParseResult.Success<Pair<T, PVector<Form>>>) res).result;
                    result = result.plus(success.left);
                    forms = success.right;
                } else {
                    return fail(((ParseResult.Fail) res).error);
                }
            }

            return success(pair(result, forms));
        };
    }

    static <T> ListParser<T> anyOf(ListParser<T>... parsers) {
        return forms -> {
            PVector<ParseResult.Fail<?>> fails = Empty.vector();

            for (ListParser<T> parser : parsers) {
                ParseResult<Pair<T, PVector<Form>>> parseResult = parser.parse(forms);
                if (parseResult instanceof ParseResult.Success) {
                    return parseResult;
                } else {
                    fails = fails.plus(((ParseResult.Fail) parseResult));
                }
            }

            return fail(new MultipleParseFailsException(fails));
        };
    }
}
