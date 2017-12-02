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
                             (into {} (map (fn [[sym expr]]
                                             [(keyword sym) (emit-value-expr* expr)]))))

                :if `(if ~(emit-value-expr* (:pred-expr expr))
                       ~(emit-value-expr* (:then-expr expr))
                       ~(emit-value-expr* (:else-expr expr)))

                :local (:local expr)
                :global (get globals (:global expr))
                :clj-var (:clj-var expr)

                :let (let [{:keys [bindings body-expr]} expr]
                       `(let [~@(mapcat (fn [[local expr]]
                                          [local (emit-value-expr* expr)])
                                        bindings)]
                          ~(emit-value-expr* body-expr)))

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
                                       :value ((eval (emit-value-expr (if (seq locals)
                                                                        {:expr-type :fn
                                                                         :sym sym
                                                                         :locals locals
                                                                         :body-expr body-expr}
                                                                        body-expr)
                                                                      env))
                                               env)})})
    :defdata
    (let [{:keys [attributes]} expr]
      {:env (-> env
                (update :attributes (fnil into {}) (map (juxt :attribute #(select-keys % [::tc/mono-type]))) attributes))})))
