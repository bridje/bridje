(ns bridje.compiler
  (:require [bridje.reader :as reader]
            [bridje.analyser :as analyser]
            [bridje.interpreter :as interpreter]))

(defn interpret [s {:keys [global-env ns-sym]}]
  (reduce (fn [{:keys [global-env]} form]
            (let [env {:global-env global-env, :ns-sym ns-sym}]
              (-> form
                  (analyser/analyse env)
                  (interpreter/interpret env))))
          {:global-env global-env}
          (reader/read-forms s)))

(comment
  (interpret "(if true [{foo \"bar\", baz true} #{\"Hello\" \"world!\"}] false)"
             {:ns-sym 'bridje.foo})

  (interpret "(def foo [\"Hello\" \"World\"])"
             {:ns-sym 'bridje.foo})

  (interpret "(let [x \"Hello\", y \"World\"] [y x])"
             {:ns-sym 'bridje.foo})

  (interpret "(fn [x] [x x])"
             {:ns-sym 'bridje.foo})

  )
