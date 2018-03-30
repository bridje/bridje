(ns bridje.emitter
  (:require [bridje.runtime :as rt]
            [bridje.type-checker :as tc]
            [bridje.util :as u]
            [clojure.string :as s]))

(defn find-globals [sub-exprs]
  (->> sub-exprs
       (into {} (comp (filter #(= :global (:expr-type %)))
                      (map :global)
                      (distinct)
                      (map (fn [global]
                             [global (gensym (name global))]))))))

(defn find-clj-namespaces [sub-exprs]
  (->> sub-exprs
       (into #{} (comp (filter #(= :clj-var (:expr-type %)))
                       (map (comp symbol namespace :clj-var))))))

(defn emit-value-expr [expr env]
  (let [sub-exprs (u/sub-exprs expr)
        globals (find-globals sub-exprs)
        clj-namespaces (find-clj-namespaces sub-exprs)]

    (letfn [(emit-value-expr* [{:keys [expr-type exprs] :as expr}]
              (case expr-type
                :string (:string expr)
                :bool (:bool expr)
                (:int :float :big-int :big-float) (:number expr)

                :vector (->> exprs
                             (into [] (map emit-value-expr*)))

                :set (->> exprs
                          (into #{} (map emit-value-expr*)))

                :record (->> (:entries expr)
                             (into {} (map (fn [{:keys [k v]}]
                                             [k (emit-value-expr* v)]))))

                :attribute (:attribute expr)

                :if `(if ~(emit-value-expr* (:pred-expr expr))
                       ~(emit-value-expr* (:then-expr expr))
                       ~(emit-value-expr* (:else-expr expr)))

                :local (:local expr)
                :global (get globals (:global expr))
                :effect-fn `(get rt/*effect-fns* '~(:effect-fn expr))
                :clj-var (:clj-var expr)

                :let (let [{:keys [bindings body-expr]} expr]
                       `(let [~@(mapcat (fn [[local expr]]
                                          [local (emit-value-expr* expr)])
                                        bindings)]
                          ~(emit-value-expr* body-expr)))

                :case (let [{:keys [expr adt clauses]} expr
                            expr-sym (gensym 'expr)
                            constructor (gensym 'constructor)]
                        `(let [~expr-sym ~(emit-value-expr* expr)
                               ~constructor (:brj/constructor ~expr-sym)]
                           (cond
                             ~@(mapcat (fn [{:keys [constructor-sym default-sym bindings expr]}]
                                         [(cond
                                            constructor-sym `(= ~constructor '~constructor-sym)
                                            default-sym :else)
                                          `(let [[~@bindings] ~(cond
                                                                 constructor-sym `(:brj/constructor-params ~expr-sym)
                                                                 default-sym expr-sym)]
                                             ~(emit-value-expr* expr))])
                                       clauses))))

                :fn (let [{:keys [sym locals body-expr]} expr]
                      `(fn ~sym [~@locals]
                         ~(emit-value-expr* body-expr)))

                :call `(~@(map emit-value-expr* exprs))

                :match (let [{:keys [match-expr clauses default-expr]} expr]
                         `(let [match# ~(emit-value-expr* match-expr)]
                            (case (:adt-type match#)
                              ~@(->> (for [[sym expr] clauses]
                                       `[~sym ~(emit-value-expr* expr)])
                                     (apply concat))

                              ~(emit-value-expr* default-expr))))

                :loop (let [{:keys [bindings body-expr]} expr]
                        `(loop [~@(mapcat (fn [[local expr]]
                                            [local (emit-value-expr* expr)])
                                          bindings)]
                           ~(emit-value-expr* body-expr)))

                :recur `(recur ~@(map emit-value-expr* exprs))))]

      (let [env-sym (gensym 'env)]
        `(fn [~env-sym]
           (do
             ~@(for [clj-ns clj-namespaces]
                 `(require '~clj-ns)))

           (let [~@(mapcat (fn [[global global-sym]]

                             [global-sym `(get-in ~env-sym [:vars
                                                            '~(symbol (name global))
                                                            :value])])
                           globals)]
             ~(emit-value-expr* expr)))))))

(defn interpret-expr [{:keys [expr-type] :as expr} {:keys [env]}]
  (case expr-type
    :def
    (let [{:keys [sym locals body-expr]} expr
          poly-type (get-in expr [::tc/poly-type ::tc/def-expr-type ::tc/poly-type])]
      {:env (assoc-in env [:vars sym] {::tc/poly-type poly-type
                                       :value ((eval (emit-value-expr (if locals
                                                                        {:expr-type :fn
                                                                         :sym sym
                                                                         :locals locals
                                                                         :body-expr body-expr}
                                                                        body-expr)
                                                                      env))
                                               env)})})
    :defattribute
    (let [{:keys [attribute ::tc/mono-type]} expr]
      {:env (-> env
                (update :attributes assoc attribute {::tc/mono-type mono-type}))})

    :defadt
    (let [{:keys [sym constructors]} expr
          mono-type (tc/->adt sym)]
      {:env (-> env
                (update :adts assoc sym {:constructors (into #{} (map :constructor-sym) constructors)
                                         ::tc/poly-type (tc/mono->poly mono-type)})

                (update :constructor-syms (fnil into {})
                        (map (juxt :constructor-sym
                                   #(merge {:adt sym}
                                           (select-keys % [:param-mono-types]))))
                        constructors)

                (update :vars merge (->> constructors
                                         (into {} (map (fn [{:keys [constructor-sym param-mono-types]}]
                                                         [constructor-sym
                                                          (if param-mono-types
                                                            {:value (fn [& params]
                                                                      {:brj/constructor constructor-sym
                                                                       :brj/constructor-params params})
                                                             ::tc/poly-type (tc/mono->poly (tc/fn-type param-mono-types mono-type))}

                                                            {:value {:brj/constructor constructor-sym}
                                                             ::tc/poly-type (tc/mono->poly mono-type)})]))))))})

    :defeffect
    (let [{:keys [sym definitions]} expr]
      {:env (-> env
                (update :effects assoc sym {:definitions (into #{} (map :sym) definitions)})

                (update :effect-fns (fnil into {})
                        (map (juxt :sym
                                   #(merge {:effect sym}
                                           (select-keys % [::tc/poly-type]))))
                        definitions))})

    :defclj
    {:env (reduce (fn [env {:keys [sym value ::tc/poly-type] :as foo}]
                    (-> env
                        (assoc-in [:vars sym] {:value value
                                               ::tc/poly-type poly-type})))
                  env
                  (:clj-fns expr))}))
