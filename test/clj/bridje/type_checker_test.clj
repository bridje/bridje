(ns bridje.type-checker-test
  (:require [bridje.type-checker :as tc]
            [clojure.test :as t]))

(t/deftest types-basic-exprs
  (t/are [expr expected-type] (= (::tc/poly-type (tc/with-type expr {:env {:vars {'foo {::tc/poly-type (tc/mono->poly (tc/primitive-type :float))}}}}))
                                 expected-type)
    {:expr-type :int} (tc/mono->poly (tc/primitive-type :int))


    {:expr-type :vector
     :exprs [{:expr-type :float}
             {:expr-type :float}]}

    (tc/mono->poly (tc/vector-of (tc/primitive-type :float)))


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
    (tc/mono->poly (tc/vector-of (tc/primitive-type :big-int)))))

(def id-fn
  {:expr-type :fn
   :sym :foo
   :locals [::foo-param]
   :body-expr {:expr-type :local
               :local ::foo-param}})

(t/deftest types-identity-fn
  (let [res (::tc/poly-type (tc/with-type id-fn {}))
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

             (tc/mono->poly #::tc{:type :record, :attributes #{:User.first-name :User.last-name}})))

    (t/is (= (-> {:expr-type :call
                  :exprs [{:expr-type :attribute, :attribute :User.first-name}
                          record-expr]}
                 (tc/with-type {:env env})
                 ::tc/poly-type)

             (tc/mono->poly (tc/primitive-type :string))))))

(t/deftest types-adt
  (let [res (-> {:expr-type :defadt,
                 :sym 'MaybeInt
                 :attributes {:JustInt.val {::tc/mono-type (tc/primitive-type :int)}}
                 :constructors {'JustInt {:attributes #{:JustInt.val}}
                                'NothingInt {}}}
                (tc/with-type {:env {}}))
        just-constructor (get-in res [:constructors 'JustInt])]

    (t/is (= (-> (get-in just-constructor [::tc/poly-type ::tc/mono-type])
                 (update-in [::tc/param-types 0] select-keys [::tc/attributes])
                 (update ::tc/return-type select-keys [::tc/adt ::tc/attributes]))
             #::tc{:type :fn
                   :param-types [{::tc/attributes #{:JustInt.val}}]
                   :return-type #::tc{:adt 'MaybeInt, :attributes #{:JustInt.val}} }))

    (t/is (= (-> res
                 (get-in [:constructors 'NothingInt ::tc/poly-type ::tc/mono-type])
                 (select-keys [::tc/adt ::tc/attributes]))
             #::tc{:adt 'MaybeInt, :attributes #{}}))))

(t/deftest types-let
  (t/is (= (-> {:expr-type :let
                :bindings [[::x {:expr-type :string, :string "world"}]
                           [::y {:expr-type :string, :string "hello"}]]
                :body-expr {:expr-type :vector
                            :exprs [{:expr-type :local, :local ::y}
                                    {:expr-type :local, :local ::x}]}}
               (tc/with-type {})
               ::tc/poly-type)
           (tc/mono->poly (tc/vector-of (tc/primitive-type :string))))))

(t/deftest types-loop-recur
  (t/is (= (-> {:expr-type :loop
                :bindings [[::x {:expr-type :int, :int 5}]]
                :body-expr {:expr-type :if
                            :pred-expr {:expr-type :bool, :bool false}
                            :then-expr {:expr-type :recur, :exprs [{:expr-type :local, :local ::x}], :loop-locals [::x]}
                            :else-expr {:expr-type :local, :local ::x}}}
               (tc/with-type {})
               ::tc/poly-type)
           (tc/mono->poly (tc/primitive-type :int)))))
