(ns bridje.analyser-test
  (:require [bridje.test-util :refer [fake-forms]]
            [bridje.type-checker :as tc]
            [bridje.reader :refer [read-forms]]
            [bridje.analyser :as ana :refer [analyse]]
            [clojure.test :as t]))

(defn fake-form [form]
  (first (read-forms (fake-forms form))))

(t/deftest analyses-handling
  (t/is (= '{:expr-type :handling,
             :handlers
             {:effect FileIO,
              :handler-exprs
              [{:expr-type :fn,
                :sym read-file!,
                :locals (file)
                :body-expr {:expr-type :string, :string "Foo"}}]},
             :body
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
