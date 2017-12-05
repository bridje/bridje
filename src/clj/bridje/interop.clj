(ns bridje.interop
  (:require [clojure.string :as str]))

(defn ++ [strs]
  (str/join strs))

(defn concat [vecs]
  (into [] (mapcat identity) vecs))
