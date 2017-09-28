(ns bridje.interpreter)

(defn interpret-value-expr [{:keys [expr-type exprs] :as expr} {:keys [global-env ns-sym] :as env}]
  (case expr-type
    :string (:string expr)
    :bool (:bool expr)
    :vector (->> exprs
                 (into [] (map #(interpret-value-expr % env))))

    :set (->> exprs
              (into #{} (map #(interpret-value-expr % env))))

    :record (->> (:entries expr)
                 (into {} (map (fn [[sym expr]]
                                 [(keyword sym) (interpret-value-expr expr env)]))))

    :if `(if ~(interpret-value-expr (:pred-expr expr) env)
           ~(interpret-value-expr (:then-expr expr) env)
           ~(interpret-value-expr (:else-expr expr) env))))

(do
  (defn interpret [{:keys [expr-type] :as expr} {:keys [global-env ns-sym] :as env}]
   (case expr-type
     (:string :bool :vector :set :record :if) {:global-env global-env, :value (eval (interpret-value-expr expr env))}
     :def (let [{:keys [sym body-expr]} expr]
            {:global-env (assoc-in global-env [ns-sym :vars (symbol sym)] (interpret-value-expr body-expr env))
             :value (symbol (name ns-sym) sym)}))
    )

  #_(-> (first (bridje.reader/read-forms "(def bar [\"Hello\" \"World\"])"))
      (bridje.analyser/analyse {:ns-sym 'bridje.foo})
      (interpret {:global-env {}, :ns-sym 'bridje.foo})))
