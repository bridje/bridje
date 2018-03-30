(ns bridje.test-util
  (:require [bridje.util :as u]
            [clojure.test :as t]
            [clojure.string :as s]
            [clojure.java.io :as io]
            [clojure.walk :as w]))

(defn fake-forms [& forms]
  (binding [*print-namespace-maps* false]
    (->> forms
         (map (fn [form]
                (cond-> form
                  (not (string? form)) (-> (->> (w/postwalk (fn [form]
                                                              (if (= "::" form)
                                                                (symbol "::")
                                                                form))))
                                           prn-str))))
         s/join)))
