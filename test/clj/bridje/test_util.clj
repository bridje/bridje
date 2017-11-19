(ns bridje.test-util
  (:require [bridje.file-io :as file-io]
            [bridje.util :as u]
            [clojure.test :as t]
            [clojure.string :as s]
            [clojure.java.io :as io]
            [clojure.walk :as w]))

(defn without-loc [v]
  (w/postwalk (fn [v]
                (cond-> v
                  (map? v) (dissoc :loc-range)))
              v))

(defn clj->form [clj]
  (w/postwalk (fn [clj]
                (cond
                  ;; add more to here as we need it
                  (string? clj) {:form-type :string, :string clj}
                  (vector? clj) {:form-type :vector, :forms clj}
                  (list? clj) {:form-type :list, :forms clj}
                  (symbol? clj) {:form-type :symbol, :sym clj}

                  :else clj))
              clj))

(defn fake-file [& forms]
  (->> forms
       (map prn-str)
       s/join))

(defn fake-io [{:keys [source-files compiled-files]}]
  (let [!compiled-files (atom compiled-files)]
    {:!compiled-files !compiled-files
     :compiler-io (reify file-io/FileIO
                    (slurp-source-file [_ ns-sym]
                      (or (get source-files ns-sym)
                          (when (u/kernel? ns-sym)
                            (some-> (io/resource (file-io/->file-path ns-sym :brj)) slurp))))

                    (slurp-compiled-file [_ ns-sym file-type]
                      (get @!compiled-files [ns-sym file-type]))

                    (spit-compiled-file [_ ns-sym file-type content]
                      (swap! !compiled-files assoc [ns-sym file-type] content)))}))
