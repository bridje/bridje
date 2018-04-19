(ns bridje.compiler
  (:require [bridje.reader :as reader]
            [bridje.quoter :as quoter]
            [bridje.analyser :as analyser]
            [bridje.emitter :as emitter]
            [bridje.type-checker :as tc]
            [bridje.util :as u]
            [clojure.set :as set]
            [clojure.java.io :as io]))

(defn interpret-form [form {:keys [env]}]
  (-> form
      (quoter/expand-quotes {:env env})
      (analyser/analyse {:env env})
      (as-> expr (merge expr (tc/type-expr expr {:env env})))
      (emitter/interpret-expr {:env env})))

(def base-env
  (->> (reader/read-forms (slurp (io/resource "bridje/core.brj")))

       (reduce (fn [env form]
                 (:env (interpret-form form {:env env})))

               {})))

(defn interpret-str [str {:keys [env]}]
  (->> (reader/read-forms str)

       (reduce (fn [{:keys [env]} form]
                 (interpret-form form {:env env}))

               {:env (or env base-env)})))
