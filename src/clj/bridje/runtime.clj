(ns bridje.runtime
  (:refer-clojure :exclude [concat])
  (:require [clojure.core :as cc]))

(def ^:dynamic *effect-fns* {})

(defn concat [colls]
  (into [] (mapcat identity) colls))
