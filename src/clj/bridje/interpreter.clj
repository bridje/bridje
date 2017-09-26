(ns bridje.interpreter)

(declare interpret)

(defn interpret-coll [env init exprs]
  (reduce (fn [{:keys [env value]} expr]
            (let [{:keys [env], el :value} (interpret env expr)]
              {:env env, :value (conj value el)}))
          {:env env, :value init}
          exprs))

(defn interpret [env {:keys [expr-type exprs] :as expr}]
  (case expr-type
    :string {:env env, :value (:string expr)}
    :bool {:env env, :value (:bool expr)}
    :vector (interpret-coll env [] exprs)
    :set (interpret-coll env #{} exprs)))
