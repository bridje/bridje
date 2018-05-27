(ns bridje.compiler-test
  (:require [bridje.compiler :as sut]
            [bridje.emitter :as e]
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
                                             ("::" (++ [String]) String))

                                          '(def hello-world
                                             (let ((hello "hello ")
                                                   (world "world"))
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

(def ^:dynamic *env* {})

(defn ->ADT* [constructor params]
  (let [value (get-in *env* [:vars constructor :value])]
    (if (seq params)
      (apply value params)
      value)))

(defmacro ->ADT [adt]
  (if (symbol? adt)
    `(->ADT* '~adt [])
    `(->ADT* '~(first adt) ~(vec (rest adt)))))

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

    (t/is (= forms
             (binding [*env* env]
               {:value [(->ADT (BoolForm true))
                        (->ADT (IntForm 43))
                        (->ADT SomethingElse)]
                ::tc/poly-type (tc/mono->poly (tc/vector-of (tc/->adt 'SimpleForm)))})))

    (t/is (= (::tc/poly-type simple-match)
             (tc/mono->poly (tc/fn-type [(tc/->adt 'SimpleForm)] (tc/primitive-type :int)))))

    (t/is (= [-2 43 -3] (:value matches)))))

(t/deftest poly-adts
  (let [{:keys [env]} (sut/interpret-str (fake-forms
                                          clj-core-interop

                                          '(defadt (Maybe a)
                                             (Just a)
                                             Nothing)

                                          '(def forms
                                             [(Just 4) Nothing (Just 23)])

                                          '(def (simple-match o)
                                             (case o
                                               (Just i) (inc i)
                                               Nothing 0))

                                          '(def matches
                                             [(simple-match (Just 4))
                                              (simple-match Nothing)
                                              (simple-match (Just 23))]))

                                         {})
        {:syms [forms simple-match matches]} (:vars env)]

    (binding [*env* env]
      (t/is (= {:value [(->ADT (Just 4))
                        (->ADT Nothing)
                        (->ADT (Just 23))]
                ::tc/poly-type (tc/mono->poly (tc/vector-of (tc/->adt 'Maybe [(tc/primitive-type :int)])))}
               forms)))

    (t/is (= (tc/mono->poly (tc/fn-type [(tc/->adt 'Maybe [(tc/primitive-type :int)])] (tc/primitive-type :int)))
             (::tc/poly-type simple-match)))

    (t/is (= [5 0 24] (:value matches)))))

(t/deftest loop-recur
  (let [{:keys [env]} (sut/interpret-str (fake-forms
                                          clj-core-interop

                                          '(def loop-recur
                                             (loop ((x 5)
                                                    (res []))
                                               (if (zero? x)
                                                 res
                                                 (recur (dec x) (conj res x))))))

                                         {})
        {:syms [loop-recur]} (:vars env)]

    (t/is (= loop-recur
             {:value [5 4 3 2 1]
              ::tc/poly-type (tc/mono->poly (tc/vector-of (tc/primitive-type :int)))}))))

(t/deftest simple-effects
  (let [{:keys [env]} (sut/interpret-str (fake-forms
                                          '(defeffect ConsoleIO
                                             ("::" (read-line!) String))

                                          '(def (res)
                                             (read-line!))

                                          '(def mocked-res
                                             (handling ((ConsoleIO (fn (read-line!)
                                                                     "foo")))
                                               (res)))

                                          '(def do-block
                                             (do
                                               (read-line!)
                                               "foo")))

                                         {})
        {:syms [mocked-res do-block]} (:vars env)]

    (t/is (= "foo" (:value mocked-res)))
    (t/is (= {::tc/poly-type (tc/mono->poly (tc/primitive-type :string)),
              ::tc/effects '[ConsoleIO]}

             (-> do-block
                 (dissoc :value))))))

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



(t/deftest quoting-test
  (let [{:keys [env]} (sut/interpret-str
                       (s/join "\n" ["(def simple-quote '(foo 4 [2 3]))"
                                     "(def double-quote ''[foo 24])"
                                     "(def syntax-quote `[1 ~'2 ~@['3 '4 '5]])"])
                       {})
        {:syms [simple-quote double-quote syntax-quote]} (:vars env)]

    (binding [*env* env]
      (t/is (= (->ADT (ListForm [(->ADT (SymbolForm 'foo))
                                 (->ADT (IntForm 4))
                                 (->ADT (VectorForm [(->ADT (IntForm 2)) (->ADT (IntForm 3))]))]))
               (:value simple-quote)))

      (t/is (= (->ADT (ListForm [(->ADT (SymbolForm 'VectorForm))
                                 (->ADT (VectorForm [(->ADT (ListForm [(->ADT (SymbolForm 'SymbolForm))
                                                                       (->ADT (ListForm [(->ADT (SymbolForm 'quote))
                                                                                         (->ADT (SymbolForm 'foo))]))]))
                                                     (->ADT (ListForm [(->ADT (SymbolForm 'IntForm))
                                                                       (->ADT (IntForm 24))]))]))]))

               (:value double-quote)))

      (t/is (= (->ADT (VectorForm [(->ADT (IntForm 1))
                                   (->ADT (IntForm 2))
                                   (->ADT (IntForm 3))
                                   (->ADT (IntForm 4))
                                   (->ADT (IntForm 5))]))
               (:value syntax-quote))))))

(def ^:dynamic with-trace)

(t/deftest macro-test
  (let [!tracer (atom [])]
    (binding [with-trace (fn [v]
                           (swap! !tracer conj v)
                           v)]
      (let [{:keys [env]} (sut/interpret-str (fake-forms
                                              clj-core-interop
                                              '(defclj bridje.compiler-test
                                                 ("::" (with-trace a) a))

                                              "(defmacro (if-not pred then else)
                                                `(if ~pred ~else ~then))"

                                              '(def foo
                                                 (if-not (zero? 5) (with-trace 10) (with-trace 25))))
                                             {})
            {:syms [foo]} (:vars env)]

        (t/is (= 10 (:value foo)))
        (t/is (= [10] @!tracer))))))

(t/deftest typedef-test
  (let [{:keys [env]} (sut/interpret-str (fake-forms
                                          "(:: (foo Int) Int)"

                                          '(def (foo x)
                                             x))

                                         {})
        {:syms [foo]} (:vars env)]

    (t/is (= (merge (tc/mono->poly (tc/fn-type [(tc/primitive-type :int)] (tc/primitive-type :int)))
                    {::tc/typedefd? true})

             (-> (get-in env [:vars 'foo ::tc/poly-type]))))))

(t/deftest poly-let-test
  (let [{:keys [env]} (sut/interpret-str (fake-forms
                                          '(defadt (T2 a b) (T2 a b))

                                          '(def foo
                                             (let ((id (fn (id x) x)))
                                               (T2 (id 4) (id "Hello")))))

                                         {})
        {:syms [foo]} (:vars env)]

    (binding [*env* env]
      (t/is (= {::tc/poly-type (tc/mono->poly (tc/->adt 'T2 [(tc/primitive-type :int) (tc/primitive-type :string)]))
                :value (->ADT (T2 4 "Hello"))}
               foo)))))
