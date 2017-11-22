(ns bridje.type-checker)

(do
  (defn free-type-variables [type]
    (case (:type/type type)
      :literal #{}
      ))

  (defn substitute [type subst]
    (case (:type/type type)
      :literal type
      :type-var (get subst (:type/type-var type) type)
      :fn (-> type
              (update :type/param-types (fn [params] (mapv #(substitute % subst) params)))
              (update :type/return-type substitute subst))))

  (defn substitute-scheme [{:keys [scheme/type-vars scheme/type] :as scheme} subst]
    (-> scheme
        (update :scheme/type substitute (apply dissoc subst type-vars))))

  (defn substitute-env [type-env subst]
    (into {}
          (map (fn [[local scheme]]
                 (substitute-scheme scheme subst)))
          type-env))

  (defn instantiate [{:keys [scheme/type-vars scheme/type]}]
    (substitute type
                (into {}
                      (map (fn [type-var]
                             [type-var (gensym (name type-var))]))
                      type-vars)))

  (defn merge-substs [& substs]
    (reduce (fn [s1 s2]
              (merge (into {}
                           (map (fn [[tv type]]
                                  [tv (substitute type s1)]))
                           s2)
                     s1))
            substs))

  (defn type-var [s]
    {:type/type :type-var
     :type/type-var (gensym (name s))})

  (defn mgu [t1 t2]
    (cond
      (or (and (= :type-var (:type/type t1) (:type/type t2))
               (= (:type/type-var t1) (:type/type-var t2)))

          (and (= :literal
                  (:type/type t1)
                  (:type/type t2))

               (= (:type/literal-type t1)
                  (:type/literal-type t2))))
      {}

      ;; TODO occurs checks
      (= :type-var (:type/type t1))
      {(:type/type-var t1) t2}

      (= :type-var (:type/type t2))
      {(:type/type-var t2) t1}

      (and (= :fn (:type/type t1) (:type/type t2))
           (= (count (:type/param-types t1))
              (count (:type/param-types t2))))
      (let [param-subst (reduce (fn [subst [t1-param t2-param]]
                                  (merge-substs subst
                                                (mgu (substitute t1-param subst)
                                                     (substitute t2-param subst))))

                                {}

                                (map vector
                                     (:type/param-types t1)
                                     (:type/param-types t2)))

            return-subst (mgu (substitute (:type/return-type t1) param-subst)
                              (substitute (:type/return-type t2) param-subst))]
        (merge-substs param-subst return-subst))

      :else (throw (ex-info "Cannot unify types" {:types [t1 t2]}))))

  (defn type-expr [expr {:keys [env current-ns]}]
    (letfn [(type-expr* [{:keys [expr-type] :as expr} {:keys [type-env]}]
              (case expr-type
                (:int :float :big-int :big-float :string :bool)
                [{:type/type :literal, :type/literal-type expr-type} {}]

                :fn
                (let [param-type-vars (for [param (:locals expr)]
                                        [param (type-var param)])

                      [body-type subst] (type-expr* (:body-expr expr)
                                                    {:type-env (merge type-env
                                                                      (into {}
                                                                            (map (fn [[local type-var]]
                                                                                   [local {:scheme/type-vars []
                                                                                           :scheme/type type-var}]))
                                                                            param-type-vars))})]

                  [{:type/type :fn
                    :type/param-types (into [] (for [[_ param-type-var] param-type-vars]
                                                 (substitute param-type-var subst)))
                    :type/return-type body-type}

                   subst])

                :call
                (let [return-var (type-var "ret")
                      [fn-type fn-subst] (type-expr* (first (:exprs expr)) {:type-env type-env})
                      [arg-types arg-subst] (reduce (fn [[arg-types arg-subst] arg-expr]
                                                      (let [[arg-type arg-subst*] (type-expr* arg-expr (substitute-env type-env arg-subst))]
                                                        [(conj arg-types arg-type) (merge-substs arg-subst* arg-subst)]))
                                                    [[] fn-subst]
                                                    (rest (:exprs expr)))
                      unify-subst (mgu (substitute fn-type arg-subst)
                                       {:type/type :fn
                                        :type/param-types arg-types
                                        :type/return-type return-var})]
                  [(substitute return-var unify-subst) (merge-substs unify-subst arg-subst fn-subst)])

                :local
                (if-let [local-type (get type-env (:local expr))]
                  [(instantiate local-type) {}]
                  (throw (ex-info "Unbound variable", {:local (:local expr)})))))]

      (when-let [[result-type subst] (type-expr* expr {:type-env {}})]
        (substitute result-type subst))))

  (for [expr (let [identity-fn {:expr-type :fn
                                :sym :foo
                                :locals [::foo-param]
                                :body-expr {:expr-type :local
                                            :local ::foo-param}}]
               [{:expr-type :int}
                identity-fn

                {:expr-type :call,
                 :exprs [identity-fn
                         {:expr-type :int, :int 4}]}

                #_{:expr-type :if,
                   :pred-expr {:expr-type :bool, :bool false}
                   :then-expr {:expr-type :int, :int 4}
                   :else-expr {:expr-type :int, :int 5}}])
        ]
    (type-expr expr {}))

  )
