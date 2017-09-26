(ns bridje.compiler
  (:require [bridje.reader :as reader]
            [bridje.analyser :as analyser]
            [bridje.interpreter :as interpreter]))

(defn interpret [env s]
  (reduce (fn [{:keys [env]} form]
            (interpreter/interpret env (analyser/analyse env nil form)))
          {:env env}
          (reader/read-forms s)))

(comment
  (interpret {} "[{foo \"bar\", baz true} #{\"Hello\" \"world!\"}]"))
