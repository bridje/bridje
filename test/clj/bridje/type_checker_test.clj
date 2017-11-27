(ns bridje.type-checker-test
  (:require [bridje.type-checker :as tc]
            [clojure.test :as t]))

(t/deftest types-basic-exprs
  (t/are [expr expected-type] (= (::tc/poly-type (tc/type-expr expr {}))
                                 expected-type)
    {:expr-type :int} #::tc{:mono-type #::tc{:type :int}
                            :type-vars #{}}


    {:expr-type :vector
     :exprs [{:expr-type :float}
             {:expr-type :float}]}

    #::tc{:type-vars #{}
          :mono-type #::tc{:type :vector, :elem-type {::tc/type :float}}}


    {:expr-type :if,
     :pred-expr {:expr-type :bool, :bool false}
     :then-expr {:expr-type :int, :int 4}
     :else-expr {:expr-type :int, :int 5}}

    #::tc{:mono-type #::tc{:type :int}
          :type-vars #{}}))

(t/deftest types-identity-fn
  (let [res (::tc/poly-type (tc/type-expr {:expr-type :fn
                                           :sym :foo
                                           :locals [::foo-param]
                                           :body-expr {:expr-type :local
                                                       :local ::foo-param}}
                                          {}))
        param-type (get-in res [::tc/mono-type ::tc/param-types 0])]

    (t/is (= res
             {::tc/type-vars #{(::tc/type-var param-type)}
              ::tc/mono-type {::tc/type :fn
                              ::tc/param-types [param-type]
                              ::tc/return-type param-type}}))))
