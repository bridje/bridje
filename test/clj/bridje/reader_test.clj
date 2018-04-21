(ns bridje.reader-test
  (:require [bridje.reader :as sut]
            [clojure.test :as t]
            [clojure.walk :as w]))

(t/deftest testing-reading
  (t/are [in-str forms] (= (sut/read-forms in-str) forms)

    "[\"Hello\" \"World\"]" [[:vector [:string "Hello"] [:string "World"]]]
    "(def (plus a b) (+ a b))" '[[:list [:symbol def]
                                  [:list [:symbol plus] [:symbol a] [:symbol b]]
                                  [:list [:symbol +] [:symbol a] [:symbol b]]]]
    ":User/first-name" [[:keyword :User/first-name]]))
