(ns bridje.analyser-test
  (:require [bridje.test-util :refer [fake-forms]]
            [bridje.type-checker :as tc]
            [bridje.reader :refer [read-forms]]
            [bridje.analyser :as ana :refer [analyse]]
            [bridje.compiler :refer [base-env]]
            [clojure.spec.alpha :as s]
            [clojure.test :as t]
            [bridje.quoter :as quoter]))

(defn fake-form [form]
  (first (read-forms (fake-forms form))))

(t/deftest analyses-handling
  (t/is (= '{:expr-type :handling,
             :handlers
             [{:effect FileIO,
               :handler-exprs
               [{:expr-type :fn,
                 :sym read-file!,
                 :locals (file)
                 :body-expr {:expr-type :string, :string "Foo"}}]}],
             :body-expr
             {:expr-type :call,
              :exprs
              [{:expr-type :effect-fn, :effect-fn read-file!}
               {:expr-type :string, :string "/tmp/foo.txt"}]}}

           (binding [ana/gen-local identity]
             (analyse (fake-form '(handling ((FileIO (fn (read-file! file)
                                                       "Foo")))
                                    (read-file! "/tmp/foo.txt")))
                      {:env {:effects {'FileIO #{'read-file!}}
                             :effect-fns {'read-file! {:effect 'FileIO}}}})))))

(t/deftest analyses-typedef
  (t/is (= {:expr-type :typedef
            :sym 'foo
            ::tc/mono-type (tc/->fn-type [(tc/primitive-type :int)] (tc/primitive-type :int))}

           (analyse (fake-form '("::" (foo Int) Int))
                    {}))))

(t/deftest varargs-macro)
(analyse (-> (fake-form "(defmacro (foo bar & more) `(+ ~bar ~@more))")
             (quoter/expand-quotes {}))
         {:env base-env})

(def fmap-poly-type
  (tc/mono->poly (tc/->fn-type [(tc/->applied-type (tc/->type-var 'f) [(tc/->type-var 'a)])
                                (tc/->fn-type [(tc/->type-var 'a)] (tc/->type-var 'b))]
                               (tc/->applied-type (tc/->type-var 'f) [(tc/->type-var 'b)]))))

(t/deftest analyses-defclass
  (binding [tc/new-type-var identity]
    (t/is (= {:expr-type :defclass
              :sym 'Functor
              ::tc/type-var 'f
              :members [{:sym 'fmap
                         ::tc/poly-type fmap-poly-type}]}

             (analyse (fake-form '(defclass (Functor f)
                                    ("::" (fmap (f a) (Fn (a) b)) (f b))))
                      {})))))

(t/deftest analyses-definstance
  (binding [tc/new-type-var identity
            ana/gen-local identity]
    (t/is (= {:expr-type :definstance
              :class 'Functor
              :instance-type (tc/->adt 'Maybe)
              :members [{:expr-type :fn
                         :sym 'fmap
                         :locals '[maybe f]
                         :body-expr {:expr-type :bool
                                     :bool true}}]}

             (analyse (fake-form '(definstance (Functor Maybe)
                                    (fn (fmap maybe f)
                                      ;; obv doesn't type-check, but we're not testing that here
                                      true)))
                      {:env {:adts {'Maybe {}}
                             :classes {'Functor {}}}})))))
