(ns bridje.test-util
  (:require [bridje.util :as u]
            [clojure.test :as t]
            [clojure.string :as s]
            [clojure.java.io :as io]
            [clojure.walk :as w]))

(defn clj->form [clj]
  (w/postwalk (fn [clj]
                (cond
                  ;; add more to here as we need it
                  (string? clj) [:string clj]
                  (vector? clj) (into [:vector] clj)
                  (list? clj) (into [:list] clj)
                  (symbol? clj) [:symbol clj]
                  (keyword? clj) [:keyword clj]

                  :else clj))
              clj))

(defn fake-forms [& forms]
  (->> forms
       (map (fn [form]
              (cond-> form
                (not (string? form)) prn-str)))
       s/join))
