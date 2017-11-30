(ns bridje.compiler
  (:require [bridje.reader :as reader]
            [bridje.quoter :as quoter]
            [bridje.analyser :as analyser]
            [bridje.emitter :as emitter]
            [bridje.type-checker :as tc]
            [bridje.util :as u]
            [clojure.set :as set]))

(defn compile-form [form {:keys [env]}]
  (-> form
      (quoter/expand-syntax-quotes {:env env})
      quoter/expand-quotes
      (analyser/analyse {:env env})
      (tc/with-type {:env env})
      (emitter/emit-expr {:env env})))

(defn compile-str [str {:keys [env]}]
  (let [{:keys [env form-codes]} (reduce (fn [{:keys [form-codes env]} form]
                                           (let [{:keys [env code]} (compile-form form {:env env})]
                                             {:env env
                                              :form-codes (conj form-codes code)}))

                                         {:env env
                                          :form-codes []}

                                         (reader/read-forms str))
        code `(fn [env#]
                (reduce (fn [env# code#] (code# env#))
                        env#
                        [~@form-codes]))]

    ((eval code) env)))

(defn run-main [env & args]
  (when-let [main-fn (get-in env [:vars 'main :value])]
    (main-fn args)))
