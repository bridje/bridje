(ns bridje.type-checker-test
  (:require [bridje.type-checker :as tc]
            [clojure.test :as t]))

(defn vector-of [elem-type]
  #::tc{:type :vector
        :elem-type elem-type})

(t/deftest types-basic-exprs
  (t/are [expr expected-type] (= (::tc/poly-type (tc/with-type expr {:env {:vars {'foo {::tc/poly-type (tc/mono->poly (tc/primitive-type :float))}}}}))
                                 expected-type)
    {:expr-type :int} (tc/mono->poly (tc/primitive-type :int))


    {:expr-type :vector
     :exprs [{:expr-type :float}
             {:expr-type :float}]}

    (tc/mono->poly (vector-of (tc/primitive-type :float)))


    {:expr-type :if,
     :pred-expr {:expr-type :bool, :bool false}
     :then-expr {:expr-type :int, :int 4}
     :else-expr {:expr-type :int, :int 5}}

    (tc/mono->poly (tc/primitive-type :int))

    {:expr-type :global
     :global 'foo}
    (tc/mono->poly (tc/primitive-type :float))

    {:expr-type :call
     :exprs [{:expr-type :fn
              :locals [::x]
              :body-expr {:expr-type :vector
                          :exprs [{:expr-type :local
                                   :local ::x}]}}
             {:expr-type :big-int
              :big-int 54}]}
    (tc/mono->poly (vector-of (tc/primitive-type :big-int)))))

(t/deftest types-identity-fn
  (let [res (::tc/poly-type (tc/with-type {:expr-type :fn
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
  (t/is (= (-> (tc/with-type {:expr-type :def
                              :sym :foo
                              :locals []
                              :body-expr {:expr-type :int
                                          :int 54}}
                             {})
               (get-in [::tc/poly-type ::tc/def-expr-type ::tc/poly-type]))
           (tc/mono->poly {::tc/type :fn
                           ::tc/param-types []
                           ::tc/return-type (tc/primitive-type :int)}))))

(t/deftest types-record
  (let [env {:attributes {:User.first-name {::tc/mono-type (tc/primitive-type :string)}
                          :User.last-name {::tc/mono-type (tc/primitive-type :string)}}}

        record-expr {:expr-type :record,
                     :entries [[:User.first-name {:expr-type :string, :string "James"}]
                               [:User.last-name {:expr-type :string, :string "Henderson"}]]}]
    (t/is (= (-> record-expr
                 (tc/with-type {:env env})
                 ::tc/poly-type)

             (tc/mono->poly #::tc{:type :record, :keys #{:User.last-name :User.first-name}})))

    (t/is (= (-> {:expr-type :call
                  :exprs [{:expr-type :attribute, :attribute :User.first-name}
                          record-expr]}
                 (tc/with-type {:env env})
                 ::tc/poly-type)

             (tc/mono->poly (tc/primitive-type :string))))))
