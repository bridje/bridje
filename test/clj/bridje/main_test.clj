(ns bridje.main-test
  (:require [bridje.main :as sut]
            [bridje.runtime :as rt]
            [bridje.compiler :as compiler]
            [bridje.fake-io :refer [fake-file fake-io]]
            [clojure.string :as s]
            [clojure.test :as t]
            [clojure.walk :as w])
  (:import [bridje.runtime ADT]))

(defn without-loc [v]
  (w/postwalk (fn [v]
                (cond-> v
                  (instance? ADT v) (update :params dissoc :loc-range)))
              v))

(t/deftest e2e-test
  (let [fake-files {'bridje.kernel.foo (fake-file
                                        '(ns bridje.kernel.foo)

                                        '(def (flip x y)
                                           [y x])

                                        #_'(defmacro (if-not pred then else)
                                             `(if ~pred
                                                ~else
                                                ~then))

                                        '(defdata Nothing)
                                        '(defdata (Just a))
                                        '(defdata (Mapped #{a b})))

                    'bridje.kernel.bar (fake-file
                                        '(ns bridje.kernel.bar
                                           {aliases {foo bridje.kernel.foo}})

                                        '(def (main args)
                                           (let [seq ["ohno"]
                                                 just (foo/->Just "just")
                                                 nothing foo/Nothing]
                                             {message (foo/flip "World" "Hello")
                                              seq seq
                                              mapped (foo/->Mapped {a "Hello", b "World"})
                                              the-nothing nothing
                                              empty? ((clj empty?) seq)
                                              just just
                                              justtype (match just
                                                         foo/Just "it's a just"
                                                         foo/Nothing "it's nothing"
                                                         "it's something else")
                                              loop-rec (loop [x 5
                                                              res []]
                                                         (if ((clj zero?) x)
                                                           res
                                                           (recur ((clj dec) x)
                                                                  ((clj conj) res x))))
                                              justval (foo/Just->a just)})))}

        {:keys [compiler-io !compiled-files]} (fake-io {:source-files fake-files})
        bootstrap-env (sut/bootstrap-env {:io compiler-io})]

    (compiler/compile! 'bridje.kernel.bar {:io compiler-io, :env bootstrap-env})

    (t/is (= (sut/run-main {:main-ns 'bridje.kernel.bar}
                           {:io compiler-io, :env bootstrap-env})

             {:message ["Hello" "World"],
              :seq ["ohno"],
              :empty? false
              :the-nothing (rt/->ADT 'bridje.kernel.foo/Nothing {}),
              :mapped (rt/->ADT 'bridje.kernel.foo/Mapped {:a "Hello", :b "World"}),
              :just (rt/->ADT 'bridje.kernel.foo/Just {:a "just"}),
              :justtype "it's a just"
              :loop-rec [5 4 3 2 1]
              :justval "just"}))))

(t/deftest quoting-test
  (let [{:keys [compiler-io !compiled-files]} (fake-io {:source-files {'bridje.kernel.baz (s/join "\n" [(pr-str '(ns bridje.kernel.baz))
                                                                                                        "(def simple-quote '(foo 4 [2 3]))"
                                                                                                        "(def double-quote ''[foo 3])"
                                                                                                        "(def syntax-quote `[1 ~'2 ~@['3 '4 '5]])"
                                                                                                        (pr-str '(def (main args)
                                                                                                                   {simple-quote simple-quote
                                                                                                                    double-quote double-quote
                                                                                                                    syntax-quote syntax-quote}))])}})
        bootstrap-env (sut/bootstrap-env {:io compiler-io})]

    (compiler/compile! 'bridje.kernel.baz {:io compiler-io, :env bootstrap-env})

    (t/is (= (-> (sut/run-main {:main-ns 'bridje.kernel.baz} {:io compiler-io, :env bootstrap-env})
                 (without-loc))

             {:simple-quote (rt/->ADT 'bridje.kernel.forms/ListForm,
                                      {:forms [(rt/->ADT 'bridje.kernel.forms/SymbolForm {:sym 'foo})
                                               (rt/->ADT 'bridje.kernel.forms/IntForm {:number 4})
                                               (rt/->ADT 'bridje.kernel.forms/VectorForm {:forms [(rt/->ADT 'bridje.kernel.forms/IntForm {:number 2})
                                                                                                  (rt/->ADT 'bridje.kernel.forms/IntForm {:number 3})]})]})

              :double-quote (rt/->ADT 'bridje.kernel.forms/ListForm
                                      {:forms [(rt/->ADT 'bridje.kernel.forms/NamespacedSymbolForm
                                                         '{:ns bridje.kernel.forms, :sym ->VectorForm})
                                               (rt/->ADT 'bridje.kernel.forms/RecordForm
                                                         {:forms [(rt/->ADT 'bridje.kernel.forms/SymbolForm,
                                                                            {:sym 'forms})
                                                                  (rt/->ADT 'bridje.kernel.forms/VectorForm,
                                                                            {:forms [(rt/->ADT 'bridje.kernel.forms/ListForm,
                                                                                               {:forms [(rt/->ADT 'bridje.kernel.forms/NamespacedSymbolForm,
                                                                                                                  '{:ns bridje.kernel.forms, :sym ->SymbolForm})
                                                                                                        (rt/->ADT 'bridje.kernel.forms/RecordForm,
                                                                                                                  {:forms [(rt/->ADT 'bridje.kernel.forms/SymbolForm,
                                                                                                                                     '{:sym sym})
                                                                                                                           (rt/->ADT 'bridje.kernel.forms/ListForm,
                                                                                                                                     {:forms [(rt/->ADT 'bridje.kernel.forms/NamespacedSymbolForm,
                                                                                                                                                        '{:ns bridje.kernel.forms, :sym symbol})
                                                                                                                                              (rt/->ADT 'bridje.kernel.forms/StringForm,
                                                                                                                                                        {:string "foo"})]})]})]})
                                                                                     (rt/->ADT 'bridje.kernel.forms/ListForm,
                                                                                               {:forms [(rt/->ADT 'bridje.kernel.forms/NamespacedSymbolForm,
                                                                                                                  '{:ns bridje.kernel.forms, :sym ->IntForm})
                                                                                                        (rt/->ADT 'bridje.kernel.forms/RecordForm,
                                                                                                                  {:forms [(rt/->ADT 'bridje.kernel.forms/SymbolForm,
                                                                                                                                     '{:sym number})
                                                                                                                           (rt/->ADT 'bridje.kernel.forms/IntForm,
                                                                                                                                     {:number 3})]})]})]})]})]})

              :syntax-quote (rt/->ADT 'bridje.kernel.forms/VectorForm,
                                      {:forms [(rt/->ADT 'bridje.kernel.forms/IntForm {:number 1})
                                               (rt/->ADT 'bridje.kernel.forms/IntForm {:number 2})
                                               (rt/->ADT 'bridje.kernel.forms/IntForm {:number 3})
                                               (rt/->ADT 'bridje.kernel.forms/IntForm {:number 4})
                                               (rt/->ADT 'bridje.kernel.forms/IntForm {:number 5})]})}))))
