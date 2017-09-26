(ns bridje.compiler
  (:require [bridje.reader :as reader]
            [bridje.analyser :as analyser]
            [bridje.emitter.clj :as emitter.clj]))

(defn compile [env s]
  (reduce (fn [{:keys [env]} form]
            (emitter.clj/emit env (analyser/analyse env nil form)))
          {:env env}
          (reader/read-forms s)))

(comment
  (compile {} "\"Hello world!\""))
