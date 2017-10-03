(ns bridje.interpreter)

(defn expr-leaves [expr]
  (case (:expr-type expr)
    (:string :bool :local :global) [expr]
    (:vector :set :call) (mapcat expr-leaves (:exprs expr))
    :record (mapcat expr-leaves (vals (:entries expr)))
    :if (mapcat (comp expr-leaves expr) #{:pred-expr :then-expr :else-expr})
    :let (concat (mapcat expr-leaves (map second (:bindings expr)))
                 (expr-leaves (:body-expr expr)))
    :fn (expr-leaves (:body-expr expr))))

(defn find-globals [expr]
  (->> (expr-leaves expr)
       (into {} (comp (filter #(= :global (:expr-type %)))
                      (map :global)
                      (distinct)
                      (map (fn [global]
                             [global (gensym (name global))]))))))

(defn interpret-value-expr [expr {:keys [current-ns] :as env}]
  (let [globals (find-globals expr)]
    (letfn [(interpret-value-expr* [{:keys [expr-type exprs] :as expr}]
              (case expr-type
                :string (:string expr)
                :bool (:bool expr)
                :vector (->> exprs
                             (into [] (map interpret-value-expr*)))

                :set (->> exprs
                          (into #{} (map interpret-value-expr*)))

                :record (->> (:entries expr)
                             (into {} (map (fn [[sym expr]]
                                             [(keyword sym) (interpret-value-expr* expr)]))))

                :if `(if ~(interpret-value-expr* (:pred-expr expr))
                       ~(interpret-value-expr* (:then-expr expr))
                       ~(interpret-value-expr* (:else-expr expr)))

                :local (:local expr)
                :global (get globals (:global expr))

                :let (let [{:keys [bindings body-expr]} expr]
                       `(let [~@(mapcat (fn [[local expr]]
                                          [local (interpret-value-expr* expr)])
                                        bindings)]
                          ~(interpret-value-expr* body-expr)))

                :fn (let [{:keys [locals body-expr]} expr]
                      `(fn [~@locals]
                         ~(interpret-value-expr* body-expr)))

                :call `(~@(map interpret-value-expr* exprs))

                :match (throw (ex-info "niy" {:expr expr}))

                :loop (throw (ex-info "niy" {:expr expr}))
                :recur (throw (ex-info "niy" {:expr expr}))))]

      ((eval `(fn [env#]
                (let [~@(mapcat (fn [[global global-sym]]
                                  [global-sym (get-in env [:global-env
                                                           (symbol (namespace global))
                                                           :vars
                                                           (symbol (name global))])])
                                globals)]
                  ~(interpret-value-expr* expr))))
       env))))

(defrecord ADT [adt-type params])

(defn interpret [{:keys [expr-type] :as expr} {:keys [global-env current-ns] :as env}]
  (case expr-type
    (:string :bool :vector :set :record :if :local :let :fn :call :match :loop :recur)
    {:global-env global-env,
     :value (interpret-value-expr expr env)}

    :def (let [{:keys [sym locals body-expr]} expr]
           {:global-env (assoc-in global-env [current-ns :vars sym]
                                  (interpret-value-expr (if (seq locals)
                                                          {:expr-type :fn
                                                           :locals locals
                                                           :body-expr body-expr}
                                                          body-expr)
                                                        env))
            :value (symbol (name current-ns) (name sym))})

    :defmacro (throw (ex-info "niy" {:expr expr}))

    :defdata (let [{:keys [sym params]} expr
                   fq-sym (symbol (name current-ns) (name sym))]
               {:global-env (-> global-env
                                (assoc-in [current-ns :types sym] {:params params})
                                (update-in [current-ns :vars] merge
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
