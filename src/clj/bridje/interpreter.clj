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
           ~(interpret-value-expr (:else-expr expr) env))

    :local (:local expr)

    :let (let [{:keys [bindings body-expr]} expr]
           `(let [~@(mapcat (fn [[local expr]]
                              [local (interpret-value-expr expr env)])
                            bindings)]
              ~(interpret-value-expr body-expr env)))

    :fn (let [{:keys [locals body-expr]} expr]
          `(fn [~@locals]
             ~(interpret-value-expr body-expr env)))

    :match (throw (ex-info "niy" {}))

    :loop (throw (ex-info "niy" {}))
    :recur (throw (ex-info "niy" {}))))

(defn interpret [{:keys [expr-type] :as expr} {:keys [global-env ns-sym] :as env}]
  (case expr-type
    (:string :bool :vector :set :record :if :local :let :fn :match :loop :recur)
    {:global-env global-env,
     :value (eval (interpret-value-expr expr env))}

    :def (let [{:keys [sym locals body-expr]} expr]
           {:global-env (assoc-in global-env [ns-sym :vars (symbol sym)]
                                  (eval (if (seq locals)
                                          `(fn ~(symbol sym) [~@locals]
                                             ~(interpret-value-expr body-expr env))
                                          (interpret-value-expr body-expr env))))
            :value (symbol (name ns-sym) sym)})

    :defmacro (throw (ex-info "niy" {}))

    :defdata (throw (ex-info "niy" {}))))
