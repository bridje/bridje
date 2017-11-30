(ns bridje.compiler-test
  (:require [bridje.compiler :as sut]
            [bridje.test-util :refer [fake-forms]]
            [bridje.runtime :as rt]
            [clojure.string :as s]
            [clojure.test :as t]
            [clojure.walk :as w])
  (:import [bridje.runtime ADT]))

(t/deftest e2e-test
  (let [env (sut/compile-str (fake-forms
                              '(def (flip x y)
                                 [y x])

                              ;; '(defdata Nothing)
                              ;; '(defdata (Just a))
                              ;; '(defdata (Mapped #{a b}))

                              '(def (main args)
                                 (flip "World" "Hello")
                                 #_(let [seq ["ohno"]
                                       ;; just (Just "just")
                                       ;; nothing Nothing
                                       ]
                                   {message (flip "World" "Hello")
                                    seq seq
                                    fn-call ((fn (foo a b) [b a]) "World" "Hello")
                                    ;; mapped (Mapped {a "Hello", b "World"})
                                    ;; the-nothing nothing
                                    empty? ((clj empty?) seq)
                                    ;; just just
                                    ;; justtype (match just
                                    ;;                 Just "it's a just"
                                    ;;                 Nothing "it's nothing"
                                    ;;                 "it's something else")
                                    loop-rec (loop [x 5
                                                    res []]
                                               (if ((clj zero?) x)
                                                 res
                                                 (recur ((clj dec) x)
                                                        ((clj conj) res x))))
                                    ;; justval (Just->a just)
                                    })))

                             {})]

    (t/is (= (sut/run-main env)
             ["Hello" "World"]

             #_{:message ["Hello" "World"],
              :fn-call ["Hello" "World"],
              :seq ["ohno"],
              :empty? false
              :loop-rec [5 4 3 2 1]
              ;; :the-nothing (rt/->ADT 'Nothing {}),
              ;; :mapped (rt/->ADT 'Mapped {:a "Hello", :b "World"}),
              ;; :just (rt/->ADT 'Just {:a "just"}),
              ;; :justtype "it's a just"
              ;; :justval "just"
              }))))

#_(t/deftest quoting-test
  (let [env (sut/compile-str
             (s/join "\n" ["(def simple-quote '(foo 4 [2 3]))"
                           "(def double-quote ''[foo 3])"
                           "(def syntax-quote `[1 ~'2 ~@['3 '4 '5]])"
                           (pr-str '(def (main args)
                                      {simple-quote simple-quote
                                       double-quote double-quote
                                       syntax-quote syntax-quote}))])
             {})]

    (t/is (= (sut/run-main env)
             {:simple-quote (rt/->ADT 'ListForm,
                                      {:forms [(rt/->ADT 'SymbolForm {:sym 'foo})
                                               (rt/->ADT 'IntForm {:number 4})
                                               (rt/->ADT 'VectorForm {:forms [(rt/->ADT 'IntForm {:number 2})
                                                                              (rt/->ADT 'IntForm {:number 3})]})]})

              :double-quote (rt/->ADT 'ListForm
                                      {:forms [(rt/->ADT 'SymbolForm
                                                         '{:sym VectorForm})
                                               (rt/->ADT 'RecordForm
                                                         {:forms [(rt/->ADT 'SymbolForm,
                                                                            {:sym 'forms})
                                                                  (rt/->ADT 'VectorForm,
                                                                            {:forms [(rt/->ADT 'ListForm,
                                                                                               {:forms [(rt/->ADT 'SymbolForm '{:sym SymbolForm})
                                                                                                        (rt/->ADT 'RecordForm,
                                                                                                                  {:forms [(rt/->ADT 'SymbolForm,
                                                                                                                                     '{:sym sym})
                                                                                                                           (rt/->ADT 'ListForm,
                                                                                                                                     {:forms [(rt/->ADT 'SymbolForm '{:sym symbol})
                                                                                                                                              (rt/->ADT 'StringForm,
                                                                                                                                                        {:string "foo"})]})]})]})
                                                                                     (rt/->ADT 'ListForm,
                                                                                               {:forms [(rt/->ADT 'SymbolForm '{:sym IntForm})
                                                                                                        (rt/->ADT 'RecordForm,
                                                                                                                  {:forms [(rt/->ADT 'SymbolForm,
                                                                                                                                     '{:sym number})
                                                                                                                           (rt/->ADT 'IntForm,
                                                                                                                                     {:number 3})]})]})]})]})]})

              :syntax-quote (rt/->ADT 'VectorForm,
                                      {:forms [(rt/->ADT 'IntForm {:number 1})
                                               (rt/->ADT 'IntForm {:number 2})
                                               (rt/->ADT 'IntForm {:number 3})
                                               (rt/->ADT 'IntForm {:number 4})
                                               (rt/->ADT 'IntForm {:number 5})]})}))))
