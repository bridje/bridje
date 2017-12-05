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
                  (string? clj) {:form-type :string, :string clj}
                  (vector? clj) {:form-type :vector, :forms clj}
                  (list? clj) {:form-type :list, :forms clj}
                  (symbol? clj) {:form-type :symbol, :sym clj}
                  (keyword? clj) {:form-type :keyword, :kw clj}

                  :else clj))
              clj))

(defn fake-forms [& forms]
  (->> forms
       (map (fn [form]
              (cond-> form
                (not (string? form)) prn-str)))
       s/join))
