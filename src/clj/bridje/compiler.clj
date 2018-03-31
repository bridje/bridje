(ns bridje.compiler
  (:require [bridje.reader :as reader]
            [bridje.quoter :as quoter]
            [bridje.analyser :as analyser]
            [bridje.emitter :as emitter]
            [bridje.type-checker :as tc]
            [bridje.util :as u]
            [clojure.set :as set]))

(defn interpret-form [form {:keys [env]}]
  (-> form
      #_(quoter/expand-syntax-quotes {:env env})
      (analyser/analyse {:env env})
      (tc/with-type {:env env})
      (emitter/interpret-expr {:env env})))

(defn interpret-str [str {:keys [env]}]
  (reduce (fn [{:keys [env]} form]
            (interpret-form form {:env env}))

          {:env env}

          (reader/read-forms str)))
