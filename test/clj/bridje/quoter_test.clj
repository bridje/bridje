(ns bridje.quoter-test
  (:require [bridje.quoter :as q]
            [bridje.reader :as r]
            [clojure.test :as t]))

(t/deftest quotes-tests
  (t/is (= '[:list
             [:symbol VectorForm]
             [:vector
              [:list [:symbol IntForm] [:int 1]]
              [:list [:symbol IntForm] [:int 2]]]]
           (-> (first (r/read-forms "`[1 ~'2]"))
               (q/expand-quotes {}))))

  (t/is (= '[:list
             [:symbol ListForm]
             [:vector
              [:list [:symbol SymbolForm] [:list [:symbol quote] [:symbol foo]]]
              [:list [:symbol IntForm] [:int 4]]
              [:list
               [:symbol VectorForm]
               [:vector
                [:list [:symbol IntForm] [:int 2]]
                [:list [:symbol IntForm] [:int 3]]]]]]

           (-> (first (r/read-forms "'(foo 4 [2 3])"))
               (q/expand-quotes {}))))

  (t/is (= '[:list
             [:symbol ListForm]
             [:vector
              [:list [:symbol SymbolForm] [:list [:symbol quote] [:symbol VectorForm]]]
              [:list
               [:symbol VectorForm]
               [:vector
                [:list
                 [:symbol ListForm]
                 [:vector
                  [:list
                   [:symbol SymbolForm]
                   [:list [:symbol quote] [:symbol SymbolForm]]]
                  [:list
                   [:symbol ListForm]
                   [:vector
                    [:list [:symbol SymbolForm] [:list [:symbol quote] [:symbol quote]]]
                    [:list [:symbol SymbolForm] [:list [:symbol quote] [:symbol foo]]]]]]]
                [:list
                 [:symbol ListForm]
                 [:vector
                  [:list [:symbol SymbolForm] [:list [:symbol quote] [:symbol IntForm]]]
                  [:list [:symbol IntForm] [:int 23]]]]]]]]
           (-> (first (r/read-forms "''[foo 23]"))
               (q/expand-quotes {}))))

  (t/is (= '[:symbol foo]
           (-> (first (r/read-forms "``~~foo"))
               (q/expand-quotes {})))))
