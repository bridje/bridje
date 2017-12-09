(ns bridje.compiler-test
  (:require [bridje.compiler :as sut]
            [bridje.test-util :refer [fake-forms]]
            [bridje.runtime :as rt]
            [clojure.string :as s]
            [clojure.test :as t]
            [clojure.walk :as w]
            [bridje.type-checker :as tc])
  (:import [bridje.runtime ADT]))

(t/deftest fn-calls
  (let [{:keys [env]} (sut/interpret-str (fake-forms
                                          '(def (flip x y)
                                             [y x])

                                          '(def flipped (flip "World" "Hello")))

                                         {})
        {:syms [flipped]} (:vars env)]

    (t/is (= flipped
             {:value ["Hello" "World"]
              ::tc/poly-type (tc/mono->poly (tc/vector-of (tc/primitive-type :string)))}))))

(t/deftest basic-interop
  (let [{:keys [env]} (sut/interpret-str (fake-forms
                                          "(defclj bridje.interop
                                             (: (concat [[a]]) [a])
                                             (: (++ [String]) String))"

                                          '(def hello-world
                                             (let [hello "hello "
                                                   world "world"]
                                               (++ [hello world]))))

                                         {})
        {:syms [hello-world]} (:vars env)]

    (t/is (= hello-world
             {:value "hello world"
              ::tc/poly-type (tc/mono->poly (tc/primitive-type :string))}))))

(def clj-core-interop
  "(defclj clojure.core
     (: (conj [a] a) [a])
     (: (dec Int) Int)
     (: (zero? Int) Bool))")

(t/deftest records
  (let [{:keys [env]} (sut/interpret-str (fake-forms
                                          '(defattrs User
                                             {:id Int
                                              :first-name String
                                              :last-name String})

                                          '(def james
                                             {:User.first-name "James"
                                              :User.last-name "Henderson"})

                                          '(def james-first-name
                                             (:User.first-name james)))

                                         {})
        {:syms [james james-first-name]} (:vars env)
        {first-name :User.first-name} (:attributes env)]

    (t/is (= (::tc/mono-type first-name)
             (tc/primitive-type :string)))

    (t/is (= james
             {:value {:User.first-name "James"
                      :User.last-name "Henderson"}
              ::tc/poly-type (tc/mono->poly #::tc{:type :record, :attributes #{:User.last-name :User.first-name}})}))

    (t/is (= james-first-name
             {:value "James"
              ::tc/poly-type (tc/mono->poly (tc/primitive-type :string))}))))

(t/deftest adts
  (let [{:keys [env]} (sut/interpret-str (fake-forms
                                          clj-core-interop

                                          '(defadt MaybeInt
                                             (JustInt {:val Int})
                                             NothingInt)

                                          '(def (->MaybeInt x)
                                             (if (zero? x)
                                               NothingInt
                                               (JustInt {:JustInt.val x})))

                                          '(def maybes
                                             [(->MaybeInt 4) (->MaybeInt 0)]))

                                         {})
        {:syms [maybes]} (:vars env)]

    (t/is (= maybes
             {:value [{:brj/adt 'JustInt
                       :JustInt.val 4}
                      {:brj/adt 'NothingInt}]
              ::tc/poly-type (tc/mono->poly (tc/vector-of #::tc{:type :adt
                                                                :adt 'MaybeInt}))}))))

(t/deftest loop-recur
  (let [{:keys [env]} (sut/interpret-str (fake-forms
                                          clj-core-interop

                                          '(def loop-recur
                                             (loop [x 5
                                                    res []]
                                               (if (zero? x)
                                                 res
                                                 (recur (dec x) (conj res x))))))

                                         {})
        {:syms [loop-recur]} (:vars env)]

    (t/is (= loop-recur
             {:value [5 4 3 2 1]
              ::tc/poly-type (tc/mono->poly (tc/vector-of (tc/primitive-type :int)))}))))

#_(t/deftest simple-effects
  (let [{:keys [env]} (sut/interpret-str (fake-forms
                                          "(defeffect FileIO
                                             (: (*read-file* File) String)
                                             (: (*write-file* File String) Unit))" ; eventually there'll be a File type

                                          '(def (copy-file src dest)
                                             (*write-file* dest (*read-file* src)))

                                          '(def res
                                             (copy-file "/home/james/foo" "/home/james/bar"))

                                          ;; 1. message passing, 'agent' style
                                          ;; 2. if the handler's stateful, it's only ever single-threaded, otherwise, it _may_ be run in parallel
                                          ;; 3. function to update state afterwards
                                          '(def mocked-res
                                             (with-handle res
                                               (handler FileIO
                                                        (fn (*read-file* src cb)
                                                          (cb "fake-file" identity))

                                                        (fn (*write-file* dest content cb)
                                                          (cb Unit)))

                                               (fn (return v cb cnt)
                                                 v))))

                                         {})
        {:syms [res]} (:vars env)]

    (t/is (= res
             {:value [5 4 3 2 1]
              ::tc/poly-type (tc/mono->poly (tc/vector-of (tc/primitive-type :int)))}))))

#_
(t/deftest quoting-test
  (let [{:keys [env]} (sut/interpret-str
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
