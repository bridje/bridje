(ns bridje.type-checker
  (:require [clojure.set :as set]
            [clojure.walk :as w]))

;; https://gergo.erdi.hu/projects/tandoori/Tandoori-Compositional-Typeclass.pdf

(defn ->type-var [s]
  {::type :type-var
   ::type-var (gensym (name s))})

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
  ([sym tvs]
   (merge {::type :adt
           ::adt-sym sym}
          (when tvs
            {::type-vars tvs}))))

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

    :adt (into #{} (mapcat ftvs) (::type-vars mono-type))))

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

    :adt (let [{::keys [type-vars]} type]
           (merge type
                  (when type-vars
                    {::type-vars (into [] (map #(apply-mapping % mapping)) type-vars)})))))

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
                           :else (recur (concat (map vector t1-tvs t2-tvs)
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

(defn instantiate [{:keys [::type-vars ::mono-type]}]
  (let [tv-mapping (into {} (map (fn [tv] [tv (->type-var tv)])) type-vars)]
    (-> (apply-mapping mono-type tv-mapping)
        (with-meta {:mapping tv-mapping}))))

(defn type-value-expr [expr {:keys [env]}]
  (letfn [(type-value-expr* [{:keys [expr-type] :as expr} {:keys [local-mono-env loop-return-var] :as opts}]
            (letfn [(type-value-expr**
                      ([expr] (type-value-expr** expr {}))

                      ([expr recur-opts]
                       (type-value-expr* expr (merge opts recur-opts))))

                    (type-bindings [bindings]
                      (reduce (fn [{:keys [local-mono-env typings]} [local binding-expr]]
                                (let [{:keys [::mono-type] :as typing} (type-value-expr** binding-expr {:local-mono-env local-mono-env})]
                                  {:local-mono-env (assoc local-mono-env local mono-type)
                                   :typings (conj typings typing)}))
                              {:local-mono-env local-mono-env
                               :typings []}
                              bindings))]

              (case expr-type
                (:int :float :big-int :big-float :string :bool)
                {::mono-env {}
                 ::mono-type (primitive-type expr-type)}

                :symbol {::mono-type (->class clojure.lang.Symbol)
                         ::mono-env {}}

                :local
                (let [mono-type (or (get local-mono-env (:local expr))
                                    (->type-var (:local expr)))]
                  {::mono-env {(:local expr) mono-type}
                   ::mono-type mono-type})

                :global
                {::mono-env {}
                 ::mono-type (instantiate (get-in env [:vars (:global expr) ::poly-type]))}

                :effect-fn
                (let [{:keys [effect ::poly-type]} (get-in env [:effect-fns (:effect-fn expr)])]
                  {::mono-env {}
                   ::mono-type (instantiate poly-type)
                   ::effects #{effect}})

                (:vector :set)
                (let [elem-type-var (->type-var :elem)
                      elem-typings (map type-value-expr** (:exprs expr))]

                  (combine-typings {:typings elem-typings
                                    :extra-eqs (into []
                                                     (comp (map ::mono-type)
                                                           (map (fn [elem-type]
                                                                  [elem-type elem-type-var])))
                                                     elem-typings)
                                    :return-type {::type expr-type
                                                  ::elem-type elem-type-var}}))

                :record
                (let [entry-typings (->> (:entries expr)
                                         (map (fn [{:keys [k v]}]
                                                {:k k, :typing (type-value-expr** v)})))]

                  (combine-typings {:typings (map :typing entry-typings)
                                    :extra-eqs (->> entry-typings
                                                    (into [] (map (fn [{:keys [k typing]}]
                                                                    [(get-in env [:attributes k ::mono-type])
                                                                     (::mono-type typing)]))))
                                    :return-type {::type :record
                                                  ::attributes (->> entry-typings
                                                                    (into #{} (map :k)))}}))

                :attribute
                (let [{:keys [attribute]} expr
                      {:keys [::mono-type]} (get-in env [:attributes attribute])]
                  {::mono-env {}
                   ::mono-type (fn-type [{::type :record
                                          ::base (gensym 'r)
                                          ::attributes #{attribute}}]
                                        mono-type)})

                :if
                (let [type-var (->type-var :if)
                      [pred-typing then-typing else-typing :as typings] (map (comp type-value-expr** expr) [:pred-expr :then-expr :else-expr])]

                  (combine-typings {:typings typings
                                    :return-type type-var
                                    :extra-eqs [[(::mono-type pred-typing) {::type :primitive
                                                                            ::primitive-type :bool}]
                                                [(::mono-type then-typing) type-var]
                                                [(::mono-type else-typing) type-var]]}))

                :let
                (let [{:keys [local-mono-env typings]} (type-bindings (:bindings expr))
                      body-typing (type-value-expr* (:body-expr expr) {:local-mono-env local-mono-env})]

                  (combine-typings {:typings (conj typings body-typing)
                                    :return-type (::mono-type body-typing)}))

                :case
                (let [{:keys [expr adt clauses]} expr
                      expr-typing (type-value-expr** expr)
                      adt-mono-type (instantiate (get-in env [:adts adt ::poly-type]))
                      adt-tv-mapping (:mapping (meta adt-mono-type))

                      return-type-var (->type-var :case)
                      clause-typings (for [{:keys [constructor-sym default-sym bindings expr]} clauses]
                                       (let [param-types (doto (cond
                                                                 constructor-sym
                                                                 (let [{:keys [param-mono-types]} (get-in env [:constructor-syms constructor-sym])]
                                                                   (into {} (map vector bindings (map #(apply-mapping % adt-tv-mapping) param-mono-types))))

                                                                 default-sym
                                                                 {(first bindings) adt-mono-type})
                                                           (prn :param-types))]

                                         (doto (type-value-expr** expr {:local-mono-env (merge local-mono-env adt-tv-mapping)})
                                           (prn :typed))))]

                  (combine-typings {:typings (into [expr-typing] clause-typings)
                                    :return-type return-type-var
                                    :extra-eqs (into [[(::mono-type expr-typing) adt-mono-type]]
                                                     (map (juxt ::mono-type (constantly return-type-var)))
                                                     clause-typings)}))

                :loop
                (let [{:keys [local-mono-env typings]} (type-bindings (:bindings expr))
                      loop-return-var (->type-var 'loop-return)
                      body-typing (type-value-expr* (:body-expr expr) {:local-mono-env local-mono-env
                                                                       :loop-return-var loop-return-var})]

                  (combine-typings {:typings (conj typings body-typing)
                                    :return-type loop-return-var
                                    :extra-eqs [[(::mono-type body-typing) loop-return-var]]}))

                :recur
                (let [expr-typings (map type-value-expr** (:exprs expr))]
                  (combine-typings {:typings expr-typings
                                    :return-type loop-return-var
                                    :extra-eqs (mapv (fn [expr-typing loop-local]
                                                       [(::mono-type expr-typing) (get local-mono-env loop-local)])
                                                     expr-typings
                                                     (:loop-locals expr))}))

                :handling
                (let [{:keys [handlers body-expr]} expr
                      handler-typings (for [{:keys [effect handler-exprs]} handlers
                                            {:keys [expr-type sym] :as handler-expr} handler-exprs]
                                        (if-not (= :fn expr-type)
                                          (throw (ex-info "Expected function" {:expr handler-expr}))

                                          (let [handler-typing (type-value-expr** handler-expr)]
                                            (combine-typings {:typings [handler-typing]
                                                              :extra-eqs [[(instantiate (get-in env [:effect-fns (:sym handler-expr) ::poly-type]))
                                                                           (::mono-type handler-typing)]]}))))

                      body-typing (type-value-expr** (:body-expr expr))]

                  (-> (combine-typings {:typings (conj handler-typings body-typing)
                                        :return-type (::mono-type body-typing)})
                      (update ::effects set/difference (into #{} (map :effect) handlers))))

                :fn
                (let [{:keys [locals body-expr]} expr
                      {:keys [::mono-type ::mono-env ::effects]} (type-value-expr** body-expr)]
                  {::mono-env (apply dissoc mono-env locals)
                   ::mono-type (merge (fn-type (into [] (map #(or (get mono-env %) (->type-var %)) locals)) mono-type)
                                      {::effects effects})})

                :call
                (let [[fn-expr & arg-exprs] (:exprs expr)
                      [{{:keys [::param-types ::return-type] :as fn-expr-type} ::mono-type, :as fn-typing}
                       & param-typings
                       :as typings] (map type-value-expr** (:exprs expr))

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
                                                :extra-eqs (doto (mapv vector param-types (map ::mono-type param-typings)) prn)})
                              (update ::effects (fnil set/union #{}) (::effects fn-expr-type))))))))]

    (let [{::keys [mono-type effects]} (type-value-expr* expr {})]
      {::poly-type (mono->poly mono-type)
       ::effects effects})))

(defn type-defmacro [{:keys [locals body-expr]} {:keys [env]}]
  (let [form-adt (->adt 'Form)
        body-typing (type-value-expr body-expr {:env env})]
    (when (combine-typings {:typings [body-typing {::mono-env (into {} (map (juxt identity (constantly form-adt))) locals)}]
                            :return-type form-adt})
      {::poly-type {::mono-type :env-update
                    ::env-update-type :defmacro}})))

(defn type-expr [expr {:keys [env]}]
  (case (:expr-type expr)
    :def
    (let [{:keys [locals body-expr]} expr]
      {::poly-type {::mono-type :env-update
                    ::env-update-type :def}

       ::def-expr-type (type-value-expr (if locals
                                          {:expr-type :fn
                                           :locals locals
                                           :body-expr body-expr}
                                          body-expr)
                                        {:env env})})

    :defmacro
    (type-defmacro expr {:env env})

    :defattribute
    (let [{:keys [kw ::mono-type]} expr]
      {::poly-type {::mono-type :env-update
                    ::env-update-type :defattribute}})

    :defclj
    {::poly-type {::mono-type :env-update
                  ::env-update-type :defclj}}

    :defjava
    {::poly-type {::mono-type :env-update
                  ::env-update-type :defjava}}

    :defadt
    (let [adt-mono-type (->adt (:sym expr) (:type-vars expr))]
      {::poly-type {::mono-type :env-update
                    ::env-update-type :defadt}
       ::adt-mono-type adt-mono-type
       ::constructor-mono-types (->> (:constructors expr)
                                     (into {} (map (fn [{:keys [constructor-sym param-mono-types]}]
                                                     {constructor-sym (if param-mono-types
                                                                        (fn-type param-mono-types adt-mono-type)
                                                                        adt-mono-type)}))))})

    :defeffect
    {::poly-type {::mono-type :env-update
                  ::env-update-type :defeffect}}


    (type-value-expr expr {:env env})))
