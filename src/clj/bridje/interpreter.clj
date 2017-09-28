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
                                 [(keyword sym) (interpret-value-expr env expr)]))))

    :if `(if ~(interpret-value-expr env (:pred-expr expr))
           ~(interpret-value-expr env (:then-expr expr))
           ~(interpret-value-expr env (:else-expr expr)))))

(defn interpret [env {:keys [expr-type] :as expr}]
  (case expr-type
    (:string :bool :vector :set :record :if) {:env env, :value (eval (interpret-value-expr env expr))}))
