(ns bridje.interpreter)

(defn interpret-value-expr [env {:keys [expr-type exprs] :as expr}]
  (case expr-type
    :string (:string expr)
    :bool (:bool expr)
    :vector (->> exprs
                 (into [] (map #(interpret-value-expr env %))))

    :set (->> exprs
              (into #{} (map #(interpret-value-expr env %))))

    :record (->> (:entries expr)
                 (into {} (map (fn [[sym expr]]
                                 [(symbol sym) (interpret-value-expr env expr)]))))))

(defn interpret [env {:keys [expr-type] :as expr}]
  (case expr-type
    (:string :bool :vector :set :record) {:env env, :value (interpret-value-expr env expr)}))
