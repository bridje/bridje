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

(defn ->adt [sym]
  {::type :adt
   ::adt-sym sym})

(defn record-of [base attributes]
  {::type :record
   ::base base
   ::attributes attributes})

(defn fn-type [param-types return-type]
  {::type :fn
   ::param-types param-types
   ::return-type return-type})

(defn ftvs [mono-type]
  (case (::type mono-type)
    :type-var #{(::type-var mono-type)}
    :primitive #{}
    :record (into #{} (keep ::base) [mono-type])
    (:vector :set) (ftvs (::elem-type mono-type))

    :fn (into (ftvs (::return-type mono-type)) (mapcat ftvs) (::param-types mono-type))

    :adt (into #{} (mapcat ftvs) (::type-params mono-type))))

(defn instantiate [{:keys [::type-vars ::mono-type]}]
  (let [tv-mapping (into {} (map (fn [tv] [tv (gensym (name tv))])) type-vars)]
    (w/postwalk (some-fn tv-mapping identity) mono-type)))

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
    :primitive type
    (:vector :set) (-> type (update ::elem-type apply-mapping mapping))
    :record (let [{existing-base ::base, existing-attributes ::attributes} type]
              (if-let [{new-base ::base, extra-attributes ::attributes} (get mapping existing-base)]
                {::type :record
                 ::base new-base
                 ::attributes (set/union existing-attributes extra-attributes)}

                type))

    :type-var (get mapping (::type-var type) type)
    :fn (fn [{:keys [::param-types ::return-type]}]
          {::type :fn
           ::param-types (map #(apply-mapping % mapping) param-types)
           ::return-type (apply-mapping return-type mapping)})
    :applied (-> type (update ::type-params (fn [tps] (map #(apply-mapping % mapping) tps))))

    ;; TODO will need to change when ADTs have type-vars
    :adt type))

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

(defn combine-typings [{:keys [typings extra-eqs]}]
  (let [mono-envs (map ::mono-env typings)
        mapping (unify-eqs (into (mono-envs->type-equations mono-envs)
                                 extra-eqs))]

    {::mono-env (->> mono-envs
                         (map #(mono-env-apply-mapping % mapping))
                         mono-env-union)

     ::mapping mapping}))

(defn type-value-expr [expr {:keys [env]}]
  (letfn [(type-value-expr* [{:keys [expr-type] :as expr} {:keys [local-mono-env loop-return-var] :as opts}]
            (let [type-value-expr** (fn tve
                                      ([expr]
                                       (tve expr {}))
                                      ([expr recur-opts]
                                       (type-value-expr* expr (merge opts recur-opts))))

                  type-bindings (fn [bindings]
                                  (reduce (fn [{:keys [local-mono-env typings]} [local binding-expr]]
                                            (let [{:keys [::mono-type] :as typing} (type-value-expr* binding-expr local-mono-env)]
                                              {:local-mono-env (assoc local-mono-env local mono-type)
                                               :typings (conj typings typing)}))
                                          {:local-mono-env local-mono-env
                                           :typings []}
                                          bindings))]

              (case expr-type
                (:int :float :big-int :big-float :string :bool)
                {::mono-env {}
                 ::mono-type (primitive-type expr-type)}

                :local
                (let [mono-type (or (get local-mono-env (:local expr))
                                    (->type-var (:local expr)))]
                  {::mono-env {(:local expr) mono-type}
                   ::mono-type mono-type})

                :global
                {::mono-env {}
                 ::mono-type (instantiate (get-in env [:vars (:global expr) ::poly-type]))}

                (:vector :set)
                (let [elem-type-var (->type-var :elem)
                      elem-typings (map type-value-expr** (:exprs expr))
                      combined-typing (combine-typings {:typings elem-typings
                                                        :extra-eqs (into []
                                                                         (comp (map ::mono-type)
                                                                               (map (fn [elem-type]
                                                                                      [elem-type elem-type-var])))
                                                                         elem-typings)})]

                  {::mono-env (::mono-env combined-typing)

                   ::mono-type {::type expr-type
                                ::elem-type (apply-mapping elem-type-var (::mapping combined-typing))}})

                :record
                (let [entry-typings (->> (:entries expr)
                                         (map (fn [{:keys [k v]}]
                                                {:k k, :typing (type-value-expr** v)})))

                      combined-typing (combine-typings {:typings (map :typing entry-typings)
                                                        :extra-eqs (->> entry-typings
                                                                        (into [] (map (fn [{:keys [k typing]}]
                                                                                        [(get-in env [:attributes k ::mono-type])
                                                                                         (::mono-type typing)]))))})]
                  {::mono-env (::mono-env combined-typing)
                   ::mono-type {::type :record
                                ::attributes (->> entry-typings
                                                  (into #{} (map :k)))}})

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
                      [pred-typing then-typing else-typing :as typings] (map (comp type-value-expr** expr) [:pred-expr :then-expr :else-expr])
                      combined-typing (combine-typings {:typings typings
                                                        :extra-eqs [[(::mono-type pred-typing) {::type :primitive
                                                                                                ::primitive-type :bool}]
                                                                    [(::mono-type then-typing) type-var]
                                                                    [(::mono-type else-typing) type-var]]})]

                  {::mono-env (::mono-env combined-typing)
                   ::mono-type (apply-mapping type-var (::mapping combined-typing))})

                :let
                (let [{:keys [local-mono-env typings]} (type-bindings (:bindings expr))

                      body-typing (type-value-expr* (:body-expr expr) {:local-mono-env local-mono-env})

                      combined-typing (combine-typings {:typings (conj typings body-typing)})]

                  {::mono-env (::mono-env combined-typing)
                   ::mono-type (apply-mapping (::mono-type body-typing) (::mapping combined-typing))})

                :case
                (let [{:keys [expr adt clauses]} expr
                      expr-typing (type-value-expr** expr)
                      adt-mono-type (->adt adt)
                      return-type-var (->type-var :case)
                      clause-typings (for [{:keys [constructor-sym default-sym bindings expr]} clauses]
                                       (let [param-types (cond
                                                           constructor-sym
                                                           (let [{:keys [param-mono-types]} (get-in env [:constructor-syms constructor-sym])]
                                                             (into {} (map vector bindings param-mono-types)))

                                                           default-sym
                                                           {(first bindings) adt-mono-type})]
                                         (type-value-expr** expr {:local-mono-env (merge local-mono-env param-types)})))

                      combined-typing (combine-typings {:typings (into [expr-typing] clause-typings)
                                                        :extra-eqs (into [[(::mono-type expr-typing) adt-mono-type]]
                                                                         (map (juxt ::mono-type (constantly return-type-var)))
                                                                         clause-typings)})]

                  {::mono-env (::mono-env combined-typing)
                   ::mono-type (apply-mapping return-type-var (::mapping combined-typing))})

                :loop
                (let [{:keys [local-mono-env typings]} (type-bindings (:bindings expr))

                      loop-return-var (->type-var 'loop-return)
                      body-typing (type-value-expr* (:body-expr expr) {:local-mono-env local-mono-env
                                                                       :loop-return-var loop-return-var})

                      combined-typing (combine-typings {:typings (conj typings body-typing)
                                                        :extra-eqs [[(::mono-type body-typing) loop-return-var]]})]

                  {::mono-env (::mono-env combined-typing)
                   ::mono-type (apply-mapping loop-return-var (::mapping combined-typing))})

                :recur
                (let [expr-typings (map type-value-expr** (:exprs expr))
                      combined-typings (combine-typings {:typings expr-typings
                                                         :extra-eqs (mapv (fn [expr-typing loop-local]
                                                                            [(::mono-type expr-typing) (get local-mono-env loop-local)])
                                                                          expr-typings
                                                                          (:loop-locals expr))})]
                  {::mono-env (::mono-env combined-typings)
                   ::mono-type (apply-mapping loop-return-var (::mapping combined-typings))})

                :fn
                (let [{:keys [locals body-expr]} expr
                      {:keys [::mono-type ::mono-env]} (type-value-expr** body-expr)]
                  {::mono-env (apply dissoc mono-env locals)
                   ::mono-type (fn-type (into [] (map #(or (get mono-env %) (->type-var %)) locals)) mono-type)})

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

                    :else (let [{:keys [::mapping ::mono-env]} (combine-typings {:typings typings
                                                                                 :extra-eqs (mapv vector param-types (map ::mono-type param-typings))})]
                            {::mono-env mono-env
                             ::mono-type (apply-mapping return-type mapping)}))))))]

    {::poly-type (mono->poly (::mono-type (type-value-expr* expr {})))}))

(defn with-type [expr {:keys [env]}]
  (merge expr
         (case (:expr-type expr)
           :def
           {::poly-type (let [{:keys [locals body-expr]} expr]
                          {::mono-type :env-update
                           ::env-update-type :def
                           ::def-expr-type (type-value-expr (if locals
                                                              {:expr-type :fn
                                                               :locals locals
                                                               :body-expr body-expr}
                                                              body-expr)
                                                            {:env env})})}

           :defattribute
           (let [{:keys [kw ::mono-type]} expr]
             {::poly-type {::mono-type :env-update
                           ::env-update-type :defattribute}})

           :defclj
           {::poly-type {::mono-type :env-update
                         ::env-update-type :defclj}}

           :defadt {::poly-type {::mono-type :env-update
                                 ::env-update-type :defadt}}

           (type-value-expr expr {:env env}))))
