(ns bridje.type-checker-test
  (:require [bridje.type-checker :as tc]
            [clojure.test :as t]))

(t/deftest types-basic-exprs
  (t/are [expr expected-type] (= (::tc/poly-type (tc/type-expr expr {:env {:vars {'foo {::tc/poly-type (tc/mono->poly (tc/primitive-type :float))}}}}))
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
   :sym 'foo-id
   :locals ['x]
   :body-expr {:expr-type :local
               :local 'x}})

(t/deftest types-identity-fn
  (binding [tc/new-type-var identity]
    (t/is (= (tc/mono->poly (tc/->fn-type [(tc/->type-var 'x)] (tc/->type-var 'x)))
             (-> (tc/type-expr id-fn {})
                 ::tc/poly-type)))))

(t/deftest types-def
  (t/is (= (tc/mono->poly (tc/->fn-type [] (tc/primitive-type :int)))

           (-> (tc/type-expr {:expr-type :def
                              :sym 'foo
                              :locals []
                              :body-expr {:expr-type :int
                                          :int 54}}
                             {})
               ::tc/def-poly-type))))

(t/deftest subsumption
  (t/are [off req] (true? (tc/<= (tc/mono->poly off) (tc/mono->poly req)))
    ;; x -> x <= Int -> Int
    (tc/->fn-type [(tc/->type-var 'x)] (tc/->type-var 'x))
    (tc/->fn-type [(tc/primitive-type :int)] (tc/primitive-type :int))

    ;; (a, b) -> b <= (Int, Int) -> Int
    (tc/->fn-type [(tc/->type-var 'a) (tc/->type-var 'b)] (tc/->type-var 'b))
    (tc/->fn-type [(tc/primitive-type :int) (tc/primitive-type :int)] (tc/primitive-type :int))

    ;; (a, b) -> b <= (x, x) -> x
    (tc/->fn-type [(tc/->type-var 'a) (tc/->type-var 'b)] (tc/->type-var 'b))
    (tc/->fn-type [(tc/->type-var 'x) (tc/->type-var 'x)] (tc/->type-var 'x))

    ;; a -> a <= [x] -> [x]
    (tc/->fn-type [(tc/->type-var 'a)] (tc/->type-var 'a))
    (tc/->fn-type [(tc/vector-of (tc/->type-var 'x))] (tc/vector-of (tc/->type-var 'x)))

    ;; (a, a) -> Int <= ([Int], [Int]) -> Int
    (tc/->fn-type [(tc/->type-var 'a) (tc/->type-var 'a)] (tc/primitive-type :int))
    (tc/->fn-type [(tc/vector-of (tc/primitive-type :int)) (tc/vector-of (tc/primitive-type :int))] (tc/primitive-type :int)))


  (t/are [off req] (false? (tc/<= (tc/mono->poly off) (tc/mono->poly req)))
    ;; Int -> Int </= x -> x
    (tc/->fn-type [(tc/primitive-type :int)] (tc/primitive-type :int))
    (tc/->fn-type [(tc/->type-var 'x)] (tc/->type-var 'x))

    ;; [x] -> [x] </= a -> a
    (tc/->fn-type [(tc/vector-of (tc/->type-var 'x))] (tc/vector-of (tc/->type-var 'x)))
    (tc/->fn-type [(tc/->type-var 'a)] (tc/->type-var 'a))

    ;; (x, x) -> x </= (a, b) -> b
    (tc/->fn-type [(tc/->type-var 'x) (tc/->type-var 'x)] (tc/->type-var 'x))
    (tc/->fn-type [(tc/->type-var 'a) (tc/->type-var 'b)] (tc/->type-var 'b))))


(t/deftest types-typedefd-identity-fn
  (binding [tc/new-type-var identity]
    (let [more-specific-id-type (merge (tc/mono->poly (tc/->fn-type [(tc/primitive-type :int)] (tc/primitive-type :int)))
                                       {::tc/typedefd? true})]


      (t/is (= more-specific-id-type

               (-> (tc/type-expr (merge id-fn {:expr-type :def})
                                 {:env {:vars {'foo-id {::tc/poly-type more-specific-id-type}}}})
                   ::tc/def-poly-type))))))

(t/deftest types-record
  (let [env {:attributes {:User/first-name {::tc/mono-type (tc/primitive-type :string)}
                          :User/last-name {::tc/mono-type (tc/primitive-type :string)}}}

        record-expr {:expr-type :record,
                     :entries [{:k :User/first-name, :v {:expr-type :string, :string "James"}}
                               {:k :User/last-name, :v {:expr-type :string, :string "Henderson"}}]}]
    (t/is (= (-> record-expr
                 (tc/type-expr {:env env})
                 ::tc/poly-type)

             (tc/mono->poly #::tc{:type :record, :attributes #{:User/first-name :User/last-name}})))

    (t/is (= (-> {:expr-type :call
                  :exprs [{:expr-type :attribute, :attribute :User/first-name}
                          record-expr]}
                 (tc/type-expr {:env env})
                 ::tc/poly-type)

             (tc/mono->poly (tc/primitive-type :string))))))

(t/deftest types-let
  (t/is (= (-> {:expr-type :let
                :bindings [[::x {:expr-type :string, :string "world"}]
                           [::y {:expr-type :string, :string "hello"}]]
                :body-expr {:expr-type :vector
                            :exprs [{:expr-type :local, :local ::y}
                                    {:expr-type :local, :local ::x}]}}
               (tc/type-expr {})
               ::tc/poly-type)
           (tc/mono->poly (tc/vector-of (tc/primitive-type :string))))))

(t/deftest types-loop-recur
  (t/is (= (-> {:expr-type :loop
                :bindings [[::x {:expr-type :int, :int 5}]]
                :body-expr {:expr-type :if
                            :pred-expr {:expr-type :bool, :bool false}
                            :then-expr {:expr-type :recur, :exprs [{:expr-type :local, :local ::x}], :loop-locals [::x]}
                            :else-expr {:expr-type :local, :local ::x}}}
               (tc/type-expr {})
               ::tc/poly-type)
           (tc/mono->poly (tc/primitive-type :int)))))

(t/deftest types-handling
  (let [ctx {:env {:effect-fns {'read-file! {:effect 'FileIO
                                             ::tc/poly-type (tc/mono->poly (tc/->fn-type [(tc/primitive-type :string)]
                                                                                         (tc/primitive-type :string)))}}}}]
    (t/is (= {::tc/poly-type (tc/mono->poly (tc/primitive-type :string))
              ::tc/effects #{'FileIO}}
             (-> '{:expr-type :call,
                   :exprs
                   [{:expr-type :effect-fn, :effect-fn read-file!}
                    {:expr-type :string, :string "/tmp/foo.txt"}]}
                 (tc/type-expr ctx)
                 (select-keys [::tc/poly-type ::tc/effects]))))

    (t/is (= {::tc/poly-type (tc/mono->poly (tc/primitive-type :string))
              ::tc/effects #{}}
             (-> '{:expr-type :handling,
                   :handlers
                   [{:effect FileIO,
                     :handler-exprs
                     [{:expr-type :fn,
                       :sym read-file!,
                       :locals (file)
                       :body-expr {:expr-type :string, :string "Foo"}}]}],
                   :body-expr
                   {:expr-type :call,
                    :exprs
                    [{:expr-type :effect-fn, :effect-fn read-file!}
                     {:expr-type :string, :string "/tmp/foo.txt"}]}}
                 (tc/type-expr ctx))))))
