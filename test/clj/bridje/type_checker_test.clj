(ns bridje.type-checker-test
  (:require [bridje.type-checker :as tc]
            [clojure.test :as t]))

(defn primitive [primitive-type]
  #::tc{:type :primitive
        :primitive-type primitive-type})

(defn vector-of [elem-type]
  #::tc{:type :vector
        :elem-type elem-type})

(defn mono->poly [mono]
  #::tc{:type-vars #{}
        :mono-type mono})

(t/deftest types-basic-exprs
  (t/are [expr expected-type] (= (::tc/poly-type (tc/type-expr expr {:env {'foo {::tc/poly-type (mono->poly (primitive :float))}}}))
                                 expected-type)
    {:expr-type :int} (mono->poly (primitive :int))


    {:expr-type :vector
     :exprs [{:expr-type :float}
             {:expr-type :float}]}

    (mono->poly (vector-of (primitive :float)))


    {:expr-type :if,
     :pred-expr {:expr-type :bool, :bool false}
     :then-expr {:expr-type :int, :int 4}
     :else-expr {:expr-type :int, :int 5}}

    (mono->poly (primitive :int))

    {:expr-type :global
     :global 'foo}
    (mono->poly (primitive :float))

    {:expr-type :call
     :exprs [{:expr-type :fn
              :locals [::x]
              :body-expr {:expr-type :vector
                          :exprs [{:expr-type :local
                                   :local ::x}]}}
             {:expr-type :big-int
              :big-int 54}]}
    (mono->poly (vector-of (primitive :big-int)))))

(t/deftest types-identity-fn
  (let [res (::tc/poly-type (tc/type-expr {:expr-type :fn
                                           :sym :foo
                                           :locals [::foo-param]
                                           :body-expr {:expr-type :local
                                                       :local ::foo-param}}
                                          {}))
        param-type (get-in res [::tc/mono-type ::tc/param-types 0])
        identity-type {::tc/type-vars #{(::tc/type-var param-type)}
                       ::tc/mono-type {::tc/type :fn
                                       ::tc/param-types [param-type]
                                       ::tc/return-type param-type}}]

    (t/is (= res identity-type))))

(t/deftest types-def
  (t/is (= (-> (tc/type-expr {:expr-type :def
                              :sym :foo
                              :locals []
                              :body-expr {:expr-type :int
                                          :int 54}}
                             {})
               (get-in [::tc/poly-type ::tc/def-expr-type ::tc/poly-type]))
           {::tc/type-vars #{}
            ::tc/mono-type {::tc/type :fn
                            ::tc/param-types []
                            ::tc/return-type (primitive :int)}})))
