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

    :match (throw (ex-info "niy" {:expr expr}))

    :loop (throw (ex-info "niy" {:expr expr}))
    :recur (throw (ex-info "niy" {:expr expr}))))

(defrecord ADT [adt-type params])

(defn interpret [{:keys [expr-type] :as expr} {:keys [global-env ns-sym] :as env}]
  (case expr-type
    (:string :bool :vector :set :record :if :local :let :fn :match :loop :recur)
    {:global-env global-env,
     :value (eval (interpret-value-expr expr env))}

    :def (let [{:keys [sym locals body-expr]} expr]
           {:global-env (assoc-in global-env [ns-sym :vars sym]
                                  (eval (if (seq locals)
                                          `(fn ~sym [~@locals]
                                             ~(interpret-value-expr body-expr env))
                                          (interpret-value-expr body-expr env))))
            :value (symbol (name ns-sym) (name sym))})

    :defmacro (throw (ex-info "niy" {:expr expr}))

    :defdata (let [{:keys [sym params]} expr
                   fq-sym (symbol (name ns-sym) (name sym))]
               {:global-env (-> global-env
                                (assoc-in [ns-sym :types sym] {:params params})
                                (update-in [ns-sym :vars] merge
                                           (if (seq params)
                                             (merge {(symbol (str "->" sym)) (eval `(fn [~@params]
                                                                                      (->ADT '~fq-sym
                                                                                             ~(into {}
                                                                                                    (map (fn [param]
                                                                                                           [(keyword param) param]))
                                                                                                    params))))}
                                                    (->> params
                                                         (into {} (map (fn [param]
                                                                         [(symbol (str sym "->" param)) (eval `(fn [obj#]
                                                                                                                 (get-in obj# [:params ~(keyword param)])))])))))

                                             {sym (eval `(->ADT '~fq-sym {}))})))
                :value fq-sym})))
