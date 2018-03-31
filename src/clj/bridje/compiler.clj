(ns bridje.compiler
  (:require [bridje.reader :as reader]
            [bridje.quoter :as quoter]
            [bridje.analyser :as analyser]
            [bridje.emitter :as emitter]
            [bridje.type-checker :as tc]
            [bridje.util :as u]
            [clojure.set :as set]
            [clojure.java.io :as io]))

(def base-env
  {:vars {'concat {:value #(into [] (mapcat identity) %)
                   ::tc/poly-type (let [tv (tc/->type-var 'a)]
                                    (tc/mono->poly (tc/fn-type [(tc/vector-of (tc/vector-of tv))] (tc/vector-of tv))))}}})

(defn interpret-form [form {:keys [env]}]
  (-> form
      (quoter/expand-quotes {:env env})
      (analyser/analyse {:env env})
      (tc/with-type {:env env})
      (emitter/interpret-expr {:env env})))

(defn interpret-str [str {:keys [env]}]
  (reduce (fn [{:keys [env]} form]
            (interpret-form form {:env env}))

          {:env (or env base-env)}

          (concat (reader/read-forms (slurp (io/resource "bridje/forms.brj")))
                  (reader/read-forms str))))
