(ns bridje.reader-test
  (:require [bridje.reader :as sut]
            [bridje.test-util :refer [clj->form]]
            [clojure.test :as t]
            [clojure.walk :as w]))

(t/deftest testing-reading
  (t/are [in-str forms] (= (sut/read-forms in-str)
                           (map clj->form forms))

    "[\"Hello\" \"World\"]" [["Hello" "World"]]
    "(def (plus a b) (+ a b))" ['(def (plus a b) (+ a b))]
    ":User/first-name" [:User/first-name]))
