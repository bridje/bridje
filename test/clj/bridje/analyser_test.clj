(ns bridje.analyser-test
  (:require [bridje.test-util :refer [fake-forms]]
            [bridje.type-checker :as tc]
            [bridje.reader :refer [read-forms]]
            [bridje.analyser :as ana :refer [analyse]]
            [clojure.spec.alpha :as s]
            [clojure.test :as t]))

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

(t/deftest analyses-defclass
  (binding [tc/new-type-var identity]
    (t/is (= {:expr-type :defclass
              :sym 'Functor
              ::tc/type-var 'f
              :members [{:sym 'fmap
                         ::tc/poly-type (tc/mono->poly (tc/->fn-type [(tc/->applied-type (tc/->type-var 'f) [(tc/->type-var 'a)])
                                                                      (tc/->fn-type [(tc/->type-var 'a)] (tc/->type-var 'b))]
                                                                     (tc/->applied-type (tc/->type-var 'f) [(tc/->type-var 'b)])))}]}

             (analyse (fake-form '(defclass (Functor f)
                                    ("::" (fmap (f a) (Fn (a) b)) (f b))))
                      {})))))
