(ns bridje.reader-test
  (:require [bridje.reader :as sut]
            [bridje.test-util :refer [without-loc]]
            [clojure.test :as t]))

(t/deftest testing-reading
  (t/is (= (-> (sut/read-forms "[\"Hello\" \"World\"]")
               without-loc)
           [{:form-type :vector,
             :forms [{:form-type :string, :string "Hello"}
                     {:form-type :string, :string "World"}]}])))
