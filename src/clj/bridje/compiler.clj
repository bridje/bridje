(ns bridje.compiler
  (:require [bridje.reader :as reader]
            [bridje.analyser :as analyser]
            [bridje.interpreter :as interpreter]))

(defn interpret [s {:keys [global-env current-ns]}]
  (reduce (fn [{:keys [global-env]} form]
            (let [env {:global-env global-env, :current-ns current-ns}]
              (-> form
                  (analyser/analyse env)
                  (interpreter/interpret env))))
          {:global-env global-env}
          (reader/read-forms s)))

(comment
  (interpret "(if true [{foo \"bar\", baz true} #{\"Hello\" \"world!\"}] false)"
             {:current-ns 'bridje.foo})

  (interpret "(def foo [\"Hello\" \"World\"])"
             {:current-ns 'bridje.foo})

  (interpret "(let [x \"Hello\", y \"World\"] [y x])"
             {:current-ns 'bridje.foo})

  (interpret "(fn [x] [x x])"
             {:current-ns 'bridje.foo})

  (-> (interpret "(defdata Nothing)"
                 {:current-ns 'bridje.foo})
      (get-in [:global-env 'bridje.foo :vars 'Nothing]))

  (interpret "(defdata (Just a)) (->Just \"Hello\")"
             {:current-ns 'bridje.foo})

  )
