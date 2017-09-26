(ns bridje.emitter.clj)

(defn emit [env {:keys [expr-type] :as expr}]
  (case expr-type
    :string {:env env
             :value (:string expr)}))
