(ns bridje.fake-io
  (:require [bridje.file-io :as file-io]
            [clojure.test :as t]
            [clojure.string :as s]
            [clojure.java.io :as io]))

(defn fake-file [& forms]
  (->> forms
       (map prn-str)
       s/join))

(defn fake-io [{:keys [source-files compiled-files]}]
  (let [!compiled-files (atom compiled-files)]
    {:!compiled-files !compiled-files
     :compiler-io (reify file-io/FileIO
                    (slurp-source-file [_ ns-sym]
                      (get source-files ns-sym))

                    (slurp-compiled-file [_ ns-sym file-type]
                      (get @!compiled-files [ns-sym file-type]))

                    (spit-compiled-file [_ ns-sym file-type content]
                      (swap! !compiled-files assoc [ns-sym file-type] content)))}))
