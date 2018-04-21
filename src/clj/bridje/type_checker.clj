(ns bridje.type-checker
  (:require [clojure.set :as set]
            [clojure.walk :as w]))

;; https://gergo.erdi.hu/projects/tandoori/Tandoori-Compositional-Typeclass.pdf

(defn ->type-var [s]
  {::type :type-var
   ::type-var s})

(defn primitive-type [primitive-type]
  {::type :primitive
   ::primitive-type primitive-type})

(defn vector-of [elem-type]
  {::type :vector
   ::elem-type elem-type})

(defn set-of [elem-type]
  {::type :set
   ::elem-type elem-type})

(defn ->adt
  ([sym] (->adt sym nil))
  ([sym param-types]
   (merge {::type :adt
           ::adt-sym sym}
          (when (seq param-types)
            {::param-types param-types}))))

(defn ->class [class]
  {::type :class
   ::class class})

(defn record-of [base attributes]
  {::type :record
   ::base base
   ::attributes attributes})

(defn fn-type
  ([param-types return-type]
   (fn-type #{} param-types return-type))

  ([effects param-types return-type]
   {::type :fn
    ::param-types param-types
    ::return-type return-type
    ::effects effects}))

(defn ftvs [mono-type]
  (case (::type mono-type)
    :type-var #{(::type-var mono-type)}
    (:primitive :class) #{}
    :record (into #{} (keep ::base) [mono-type])
    (:vector :set) (ftvs (::elem-type mono-type))

    :fn (into (ftvs (::return-type mono-type)) (mapcat ftvs) (::param-types mono-type))

    :adt (into #{} (mapcat ftvs) (::param-types mono-type))))

(defn mono->poly [mono-type]
  {::type-vars (ftvs mono-type)
   ::mono-type mono-type})

(defn mono-envs->type-equations [mono-envs]
  (let [new-type-vars (into {}
                            (comp (mapcat keys)
                                  (distinct)
                                  (map (fn [local]
                                         [local (->type-var local)])))
                            mono-envs)]
    (into []
          (mapcat (fn [mono-env]
                    (for [[local mono-type] mono-env]
                      [(get new-type-vars local)
                       mono-type])))
          mono-envs)))

(defn apply-mapping [type mapping]
  (case (::type type)
    (:primitive :class) type
    (:vector :set) (-> type (update ::elem-type apply-mapping mapping))
    :record (let [{existing-base ::base, existing-attributes ::attributes} type]
              (if-let [{new-base ::base, extra-attributes ::attributes} (get mapping existing-base)]
                {::type :record
                 ::base new-base
                 ::attributes (set/union existing-attributes extra-attributes)}

                type))

    :type-var (get mapping (::type-var type) type)

    :fn (let [{:keys [::param-types ::return-type]} type]
          {::type :fn
           ::param-types (map #(apply-mapping % mapping) param-types)
           ::return-type (apply-mapping return-type mapping)})

    :adt (let [{::keys [param-types]} type]
           (merge type
                  (when param-types
                    {::param-types (into [] (map #(apply-mapping % mapping)) param-types)})))))

(defn mono-env-apply-mapping [mono-env mapping]
  (into {}
        (map (fn [[local mono-type]]
               [local (apply-mapping mono-type mapping)]))
        mono-env))

(defn mapping-apply-mapping [old-mapping new-mapping]
  (merge (into {}
               (map (fn [[type-var mono-type]]
                      [type-var (apply-mapping mono-type new-mapping)]))
               old-mapping)
         new-mapping))

(defn unify-records [t1 t2]
  (let [{t1-base ::base, t1-attributes ::attributes} t1
        {t2-base ::base, t2-attributes ::attributes} t2
        t1-t2-attributes (set/difference t1-attributes t2-attributes)
        t2-t1-attributes (set/difference t2-attributes t1-attributes)]
    (when-not (or (and (nil? t1-base) (seq t2-t1-attributes))
                  (and (nil? t2-base) (seq t1-t2-attributes)))
      (let [new-base (gensym 'r)]
        (merge (when (and t1-base (seq t2-t1-attributes))
                 {t1-base {::type :record
                           ::base (when t2-base
                                    new-base)
                           ::attributes t2-t1-attributes}})

               (when (and t2-base (seq t1-t2-attributes))
                 {t2-base {::type :record
                           ::base (when t1-base
                                    new-base)
                           ::attributes t1-t2-attributes}}))))))

(defn unify-eqs [eqs]
  (loop [[[{t1-type ::type, :as t1} {t2-type ::type, :as t2} :as eq] & more-eqs] eqs
         mapping {}]
    (if-not eq
      mapping

      (let [ex (ex-info "Cannot unify types" {:types [t1 t2]})]
        (cond
          (= t1 t2) (recur more-eqs mapping)

          (= :type-var t1-type)
          (let [new-mapping {(::type-var t1) t2}]
            (recur (map (fn [[t1 t2]]
                          [(apply-mapping t1 new-mapping)
                           (apply-mapping t2 new-mapping)])
                        more-eqs)

                   (mapping-apply-mapping mapping new-mapping)))

          (= :type-var t2-type)
          (recur (cons [t2 t1] more-eqs) mapping)

          (not= t1-type t2-type) (throw ex)

          :else (case (::type t1)
                  :record (recur more-eqs (mapping-apply-mapping mapping (unify-records t1 t2)))
                  (:vector :set) (recur (cons [(::elem-type t1) (::elem-type t2)] more-eqs) mapping)
                  :fn (let [{t1-param-types ::param-types, t1-return-type ::return-type} t1
                            {t2-param-types ::param-types, t2-return-type ::return-type} t2]
                        (cond
                          (not= (count t1-param-types) (count t2-param-types)) (throw ex)
                          :else (recur (concat (map vector t1-param-types t2-param-types)
                                               [[t1-return-type t2-return-type]]
                                               more-eqs)
                                       mapping)))

                  :adt (let [{t1-adt-sym :adt-sym, t1-tvs :type-vars} t1
                             {t2-adt-sym :adt-sym, t2-tvs :type-vars} t2]
                         (cond
                           (not= t1-adt-sym t2-adt-sym) (throw ex)
                           :else (recur (concat (map vector
                                                     (for [tv t1-tvs]
                                                       {::type :type-var
                                                        :type-var tv})
                                                     (for [tv t2-tvs]
                                                       {::type :type-var
                                                        :type-var tv}))
                                                more-eqs)
                                        mapping)))

                  (throw ex)))))))

(defn mono-env-union [mono-envs]
  (->> mono-envs
       (mapcat identity)
       (reduce (fn [mono-env [local mono-type]]
                 (if-let [existing-mono-type (get mono-env local)]
                   (if (= existing-mono-type mono-type)
                     mono-env
                     (throw (ex-info "Can't unify types" {:local local
                                                          :mono-types [mono-type existing-mono-type]})))
                   (assoc mono-env local mono-type)))

               {})))

(defn combine-typings [{:keys [typings return-type extra-eqs]}]
  (let [mono-envs (map ::mono-env typings)
        mapping (unify-eqs (into (mono-envs->type-equations mono-envs)
                                 extra-eqs))]

    (merge {::mono-env (->> mono-envs
                            (map #(mono-env-apply-mapping % mapping))
                            mono-env-union)

            ::effects (into #{} (mapcat ::effects) typings)}

           (when return-type
             {::mono-type (apply-mapping return-type mapping)}))))

(def ^:dynamic new-type-var (comp gensym name))

(defn instantiate [{:keys [::type-vars ::mono-type]}]
  (apply-mapping mono-type (into {} (map (juxt identity (comp ->type-var new-type-var))) type-vars)))

(defn instantiate-adt [{:keys [sym ::poly-type ::constructor-poly-types] :as adt}]
  (binding [new-type-var (memoize new-type-var)]
    {::mono-type (instantiate poly-type)
     ::constructor-mono-types (->> constructor-poly-types
                                   (into {} (map (juxt key (comp instantiate val)))))}))

(def ^:dynamic *ctx* {})

(defmacro with-ctx-update [update-form & body]
  `(binding [*ctx* (-> *ctx* ~update-form)]
     ~@body))

(defmulti type-value-expr* :expr-type, :default ::default)

(defmethod type-value-expr* ::default [{:keys [expr-type] :as expr}]
  (case expr-type
    (:int :float :big-int :big-float :string :bool)
    {::mono-env {}
     ::mono-type (primitive-type expr-type)}))

(defmethod type-value-expr* :symbol [_]
  {::mono-type (->class clojure.lang.Symbol)
   ::mono-env {}})

(defmethod type-value-expr* :local [{:keys [local]}]
  (let [mono-type (or (get-in *ctx* [:local-mono-env local])
                      (->type-var local))]
    {::mono-env {local mono-type}
     ::mono-type mono-type}))

(defmethod type-value-expr* :global [{:keys [global]}]
  {::mono-env {}
   ::mono-type (instantiate (get-in *ctx* [:env :vars global ::poly-type]))})

(defmethod type-value-expr* :effect-fn [{:keys [effect-fn]}]
  (let [{:keys [effect ::poly-type]} (get-in *ctx* [:env :effect-fns effect-fn])]
    {::mono-env {}
     ::mono-type (instantiate poly-type)
     ::effects #{effect}}))

(defn type-coll-expr [{:keys [expr-type exprs]}]
  (let [elem-type-var (->type-var :elem)
        elem-typings (map type-value-expr* exprs)]

    (combine-typings {:typings elem-typings
                      :extra-eqs (into []
                                       (comp (map ::mono-type)
                                             (map (fn [elem-type]
                                                    [elem-type elem-type-var])))
                                       elem-typings)
                      :return-type {::type expr-type
                                    ::elem-type elem-type-var}})))

(defmethod type-value-expr* :vector [expr]
  (type-coll-expr expr))

(defmethod type-value-expr* :set [expr]
  (type-coll-expr expr))

(defmethod type-value-expr* :record [{:keys [entries]}]
  (let [entry-typings (->> entries
                           (map (fn [{:keys [k v]}]
                                  {:k k, :typing (type-value-expr* v)})))]

    (clojure.pprint/pprint entry-typings)

    (combine-typings {:typings (map :typing entry-typings)
                      :extra-eqs (->> entry-typings
                                      (into [] (map (fn [{:keys [k typing]}]
                                                      [(doto (get-in *ctx* [:env :attributes k ::mono-type]) prn)
                                                       (::mono-type typing)]))))
                      :return-type {::type :record
                                    ::attributes (->> entry-typings
                                                      (into #{} (map :k)))}})))

(defmethod type-value-expr* :attribute [{:keys [attribute]}]
  (let [{:keys [::mono-type]} (get-in *ctx* [:env :attributes attribute])]
    {::mono-env {}
     ::mono-type (fn-type [{::type :record
                            ::base (gensym 'r)
                            ::attributes #{attribute}}]
                          mono-type)}))

(defmethod type-value-expr* :if [expr]
  (let [type-var (->type-var :if)
        [pred-typing then-typing else-typing :as typings] (map (comp type-value-expr* expr) [:pred-expr :then-expr :else-expr])]

    (combine-typings {:typings typings
                      :return-type type-var
                      :extra-eqs [[(::mono-type pred-typing) {::type :primitive
                                                              ::primitive-type :bool}]
                                  [(::mono-type then-typing) type-var]
                                  [(::mono-type else-typing) type-var]]})))

(defn type-bindings [bindings]
  (reduce (fn [{:keys [local-mono-env typings]} [local binding-expr]]
            (let [{:keys [::mono-type] :as typing} (with-ctx-update (assoc :local-mono-env local-mono-env)
                                                     (type-value-expr* binding-expr))]
              {:local-mono-env (assoc local-mono-env local mono-type)
               :typings (conj typings typing)}))
          {:local-mono-env (:local-mono-env *ctx*)
           :typings []}
          bindings))

(defmethod type-value-expr* :let [{:keys [bindings body-expr]}]
  (let [{:keys [local-mono-env typings]} (type-bindings bindings)
        body-typing (with-ctx-update (assoc :local-mono-env local-mono-env)
                      (type-value-expr* body-expr))]

    (combine-typings {:typings (conj typings body-typing)
                      :return-type (::mono-type body-typing)})))

(defmethod type-value-expr* :case [{:keys [expr clauses] :as case-expr}]
  (let [expr-typing (type-value-expr* expr)
        clause-typings (for [{:keys [constructor-sym default-sym locals expr]} clauses]
                         {:pattern-typing (cond
                                            constructor-sym
                                            (let [adt-sym (get-in *ctx* [:env :adt-constructors constructor-sym])
                                                  {::keys [mono-type constructor-mono-types]} (instantiate-adt (get-in *ctx* [:env :adts adt-sym]))]
                                              {::mono-env (into {} (mapv vector locals (get-in constructor-mono-types [constructor-sym ::param-types])))
                                               ::mono-type mono-type})

                                            default-sym
                                            (let [tv (->type-var (new-type-var default-sym))]
                                              {::mono-env {default-sym tv}
                                               ::mono-type tv}))

                          :expr-typing (type-value-expr* expr)})

        return-type-var (->type-var :case)]

    (combine-typings {:typings (concat [expr-typing]
                                       (into [] (mapcat (juxt :pattern-typing :expr-typing)) clause-typings))
                      :return-type return-type-var
                      :extra-eqs (concat (into []
                                               (map (juxt (constantly (::mono-type expr-typing))
                                                          (comp ::mono-type :pattern-typing)))
                                               clause-typings)

                                         (into []
                                               (map (juxt (constantly return-type-var)
                                                          (comp ::mono-type :expr-typing)))
                                               clause-typings))})))

(defmethod type-value-expr* :loop [{:keys [bindings body-expr]}]
  (let [{:keys [local-mono-env typings]} (type-bindings bindings)
        loop-return-var (->type-var 'loop-return)
        body-typing (with-ctx-update (merge {:local-mono-env local-mono-env
                                             :loop-return-var loop-return-var})
                      (type-value-expr* body-expr))]

    (combine-typings {:typings (conj typings body-typing)
                      :return-type loop-return-var
                      :extra-eqs [[(::mono-type body-typing) loop-return-var]]})))

(defmethod type-value-expr* :recur [{:keys [loop-locals exprs]}]
  (let [expr-typings (map type-value-expr* exprs)]
    (combine-typings {:typings expr-typings
                      :return-type (:loop-return-var *ctx*)
                      :extra-eqs (mapv (fn [expr-typing loop-local]
                                         [(::mono-type expr-typing) (get-in *ctx* [:local-mono-env loop-local])])
                                       expr-typings
                                       loop-locals)})))

(defmethod type-value-expr* :handling [{:keys [handlers body-expr]}]
  (let [handler-typings (for [{:keys [effect handler-exprs]} handlers
                              {:keys [expr-type sym] :as handler-expr} handler-exprs]
                          (if-not (= :fn expr-type)
                            (throw (ex-info "Expected function" {:expr handler-expr}))

                            (let [handler-typing (type-value-expr* handler-expr)]
                              (combine-typings {:typings [handler-typing]
                                                :extra-eqs [[(instantiate (get-in *ctx* [:env :effect-fns (:sym handler-expr) ::poly-type]))
                                                             (::mono-type handler-typing)]]}))))

        body-typing (type-value-expr* body-expr)]

    (-> (combine-typings {:typings (conj handler-typings body-typing)
                          :return-type (::mono-type body-typing)})
        (update ::effects set/difference (into #{} (map :effect) handlers)))))

(defmethod type-value-expr* :fn [{:keys [locals body-expr]}]
  (let [{:keys [::mono-type ::mono-env ::effects]} (type-value-expr* body-expr)]
    {::mono-env (apply dissoc mono-env locals)
     ::mono-type (merge (fn-type (into [] (map #(or (get mono-env %) (->type-var %)) locals)) mono-type)
                        {::effects effects})}))

(defmethod type-value-expr* :call [{:keys [exprs]}]
  (let [[fn-expr & arg-exprs] exprs
        [{{:keys [::param-types ::return-type] :as fn-expr-type} ::mono-type, :as fn-typing}
         & param-typings
         :as typings] (map type-value-expr* exprs)

        expected-param-count (count param-types)
        actual-param-count (count param-typings)]

    (cond
      (not= (::type fn-expr-type) :fn)
      (throw (ex-info "Expected function" {::mono-type fn-expr-type}))

      (not= expected-param-count actual-param-count)
      (throw (ex-info "Wrong number of args passed to fn"
                      {:expected expected-param-count
                       :actual actual-param-count}))

      :else (-> (combine-typings {:typings typings
                                  :return-type return-type
                                  :extra-eqs (mapv vector param-types (map ::mono-type param-typings))})
                (update ::effects (fnil set/union #{}) (::effects fn-expr-type))))))

(defn type-value-expr [expr]
  (let [{::keys [mono-type effects]} (type-value-expr* expr)]
    {::poly-type (mono->poly mono-type)
     ::effects effects}))

(defmulti type-expr* :expr-type, :default ::default)

(defmethod type-expr* ::default [expr]
  (type-value-expr expr))

(defmethod type-expr* :def [{:keys [locals body-expr]}]
  {::poly-type {::mono-type :env-update
                ::env-update-type :def}

   ::def-poly-type (type-value-expr (if locals
                                      {:expr-type :fn
                                       :locals locals
                                       :body-expr body-expr}
                                      body-expr))})

(defmethod type-expr* :defmacro [{:keys [locals body-expr]}]
  (let [form-adt (->adt 'Form)
        body-typing (type-value-expr body-expr)]
    (when (combine-typings {:typings [body-typing {::mono-env (into {} (map (juxt identity (constantly form-adt))) locals)}]
                            :return-type form-adt})
      {::poly-type {::mono-type :env-update
                    ::env-update-type :defmacro}})))


(defmethod type-expr* :defattribute [{:keys [kw ::mono-type]}]
  {::poly-type {::mono-type :env-update
                ::env-update-type :defattribute}})

(defmethod type-expr* :defclj [_]
  {::poly-type {::mono-type :env-update
                ::env-update-type :defclj}})

(defmethod type-expr* :defjava [_]
  {::poly-type {::mono-type :env-update
                ::env-update-type :defjava}})

(defmethod type-expr* :defadt [{:keys [sym ::type-vars constructors]}]
  (let [adt-mono-type (->adt sym (mapv ->type-var type-vars))]
    {::poly-type {::mono-type :env-update
                  ::env-update-type :defadt}

     ::adt-poly-type (mono->poly adt-mono-type)
     ::constructor-poly-types (->> constructors
                                   (into {} (map (fn [{:keys [constructor-sym param-mono-types]}]
                                                   [constructor-sym (mono->poly (if (seq param-mono-types)
                                                                                  (fn-type param-mono-types adt-mono-type)
                                                                                  adt-mono-type))]))))}))

(defmethod type-expr* :defeffect [_]
  {::poly-type {::mono-type :env-update
                ::env-update-type :defeffect}})

(defn type-expr [expr {:keys [env] :as ctx}]
  (binding [*ctx* ctx]
    (type-expr* expr)))
