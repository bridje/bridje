(ns bridje.compiler-test
  (:require [bridje.compiler :as sut]
            [bridje.test-util :refer [fake-forms]]
            [bridje.runtime :as rt]
            [clojure.string :as s]
            [clojure.test :as t]
            [clojure.walk :as w]
            [bridje.type-checker :as tc]))

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
                                          '(defclj bridje.interop
                                             ("::" (concat [[a]]) [a])
                                             ("::" (++ [String]) String))

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
  '(defclj clojure.core
     ("::" (conj [a] a) [a])
     ("::" (inc Int) Int)
     ("::" (dec Int) Int)
     ("::" (zero? Int) Bool)))

(t/deftest records
  (let [{:keys [env]} (sut/interpret-str (fake-forms
                                          '("::" :User/id Int)
                                          '("::" :User/first-name String)
                                          '("::" :User/last-name String)

                                          '(def james
                                             {:User/first-name "James"
                                              :User/last-name "Henderson"})

                                          '(def james-first-name
                                             (:User/first-name james)))

                                         {})
        {:syms [james james-first-name]} (:vars env)
        {first-name :User/first-name} (:attributes env)]

    (t/is (= (::tc/mono-type first-name)
             (tc/primitive-type :string)))

    (t/is (= james
             {:value {:User/first-name "James"
                      :User/last-name "Henderson"}
              ::tc/poly-type (tc/mono->poly #::tc{:type :record, :attributes #{:User/first-name :User/last-name}})}))

    (t/is (= james-first-name
             {:value "James"
              ::tc/poly-type (tc/mono->poly (tc/primitive-type :string))}))
    env))

(t/deftest adts
  (let [{:keys [env]} (sut/interpret-str (fake-forms
                                          clj-core-interop

                                          '(defadt SimpleForm
                                             (BoolForm Bool)
                                             (IntForm Int)
                                             SomethingElse)

                                          '(def forms
                                             [(BoolForm true) (IntForm 43) SomethingElse])

                                          '(def (simple-match o)
                                             (case o
                                               (BoolForm b) (if b -1 -2)
                                               (IntForm i) (inc i)
                                               SomethingElse -3))

                                          '(def matches
                                             [(simple-match (BoolForm false))
                                              (simple-match (IntForm 42))
                                              (simple-match SomethingElse)]))

                                         {})
        {:syms [forms simple-match matches]} (:vars env)]

    (t/is (= (-> forms
                 (update-in [::tc/poly-type ::tc/mono-type ::tc/elem-type] select-keys [::tc/type ::tc/adt-sym]))
             {:value [{:brj/constructor 'BoolForm, :brj/constructor-params [true]}
                      {:brj/constructor 'IntForm, :brj/constructor-params [43]}
                      {:brj/constructor 'SomethingElse}]
              ::tc/poly-type (tc/mono->poly (tc/vector-of (tc/->adt 'SimpleForm)))}))

    (t/is (= (::tc/poly-type simple-match)
             (tc/mono->poly (tc/fn-type [(tc/->adt 'SimpleForm)] (tc/primitive-type :int)))))

    (t/is (= (:value matches) [-2 43 -3]))))

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

(t/deftest simple-effects
  (let [!printed (atom [])
        {:keys [env]} (binding [rt/*effect-fns* {'read-line! (constantly "foo")
                                                 'println! (fn [s]
                                                             (swap! !printed conj s))}]

                        (sut/interpret-str (fake-forms
                                            '(defadt Unit Unit)

                                            '(defeffect ConsoleIO
                                               ("::" (read-line!) String)
                                               ("::" (println! String) Unit))

                                            '(def (echo!)
                                               (println! (read-line!)))

                                            #_'(def res
                                                 (copy-file! (File "/home/james/foo") (File "/home/james/bar")))

                                            #_'(def mocked-res
                                                 (handling res
                                                           (handler
                                                            ConsoleIO
                                                            (fn (read-line!)
                                                              "fake-file")

                                                            (fn (println! line)
                                                              Unit)))))

                                           {}))
        {:syms [echo!]} (:vars env)]

    (t/is (= #{'ConsoleIO} (get-in echo! [::tc/poly-type ::tc/mono-type ::tc/effects])))))

(t/deftest defjava-test
  (let [{:keys [env]} (sut/interpret-str (fake-forms
                                          '(defjava clojure.lang.Symbol
                                             ("::" (intern String) Symbol))

                                          '(def foo
                                             (intern "foo")))
                                         {})]
    (t/is (= (tc/mono->poly (tc/fn-type [(tc/primitive-type :string)] (tc/->class clojure.lang.Symbol)))
             (get-in env [:vars 'intern ::tc/poly-type])))

    (t/is (= {:value 'foo
              ::tc/poly-type (tc/mono->poly (tc/->class clojure.lang.Symbol))}
             (get-in env [:vars 'foo])))))

(defn ->ADT [constructor & params]
  {:brj/constructor constructor
   :brj/constructor-params (vec params)})

(t/deftest quoting-test
  (let [{:keys [env]} (sut/interpret-str
                       (s/join "\n" ["(def simple-quote '(foo 4 [2 3]))"
                                     "(def double-quote ''[foo 24])"
                                     #_"(def syntax-quote `[1 ~'2 ~@['3 '4 '5]])"])
                       {})
        {:syms [simple-quote double-quote syntax-quote]} (:vars env)]

    (t/is (= (->ADT 'ListForm
                    [(->ADT 'SymbolForm 'foo)
                     (->ADT 'IntForm 4)
                     (->ADT 'VectorForm [(->ADT 'IntForm 2) (->ADT 'IntForm 3)])])
             (:value simple-quote)))

    (t/is (= (->ADT 'QuoteForm
                    (->ADT 'ListForm [(->ADT 'SymbolForm 'VectorForm)
                                      (->ADT 'VectorForm [(->ADT 'ListForm [(->ADT 'SymbolForm 'SymbolForm)
                                                                            (->ADT 'SymbolForm 'foo)])
                                                          (->ADT 'ListForm [(->ADT 'SymbolForm 'IntForm)
                                                                            (->ADT 'IntForm 24)])])]))

             (:value double-quote)))

    #_(t/is (= (rt/->ADT 'VectorForm,
                         {:forms [(rt/->ADT 'IntForm {:number 1})
                                  (rt/->ADT 'IntForm {:number 2})
                                  (rt/->ADT 'IntForm {:number 3})
                                  (rt/->ADT 'IntForm {:number 4})
                                  (rt/->ADT 'IntForm {:number 5})]})
               (:value syntax-quote)))))
