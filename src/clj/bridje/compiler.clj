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
  (interpret {} "(if false [{foo \"bar\", baz true} #{\"Hello\" \"world!\"}] false)")
  ;; TODO loc-ranges not being passed through here
  (analyser/analyse {} {} (first (reader/read-forms "(def foo [\"Hello\" \"World\"])")))
  )
