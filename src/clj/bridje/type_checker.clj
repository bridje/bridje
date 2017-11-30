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

(defn ftvs [mono-type]
  (case (::type mono-type)
    :type-var #{(::type-var mono-type)}
    :primitive #{}
    (:vector :set) (ftvs (::elem-type mono-type))

    :fn (into (ftvs (::return-type mono-type))
              (mapcat ftvs)
              (::param-types mono-type))))

(defn instantiate [{:keys [::type-vars ::mono-type]}]
  (let [tv-mapping (into {} (map (fn [tv] [tv (gensym (name tv))])) type-vars)]
    (w/postwalk (some-fn tv-mapping identity) mono-type)))

(defn mono-type->poly-type [mono-type]
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
    :type-var (get mapping (::type-var type) type)))

(defn mono-env-apply-mapping [mono-env mapping]
  (into {}
        (map (fn [[local mono-type]]
               [local (apply-mapping mono-type mapping)]))
        mono-env))

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

                   (merge (into {}
                                (map (fn [[type-var mono-type]]
                                       [type-var (apply-mapping mono-type new-mapping)]))
                                mapping)
                          new-mapping)))

          (= :type-var t2-type)
          (recur (cons [t2 t1] more-eqs) mapping)

          (not= t1-type t2-type) (throw ex)

          :else (case (::type t1)
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
  (letfn [(type-value-expr* [{:keys [expr-type] :as expr}]
            (case expr-type
              (:int :float :big-int :big-float :string :bool)
              {::mono-env {}
               ::mono-type (primitive-type expr-type)}

              :local
              (let [type-var (->type-var (:local expr))]
                {::mono-env {(:local expr) type-var}
                 ::mono-type type-var})

              :global
              {::mono-env {}
               ::mono-type (instantiate (get-in env [:vars (:global expr) ::poly-type]))}

              (:vector :set)
              (let [elem-type-var (->type-var :elem)
                    elem-typings (map type-value-expr* (:exprs expr))
                    combined-typing (combine-typings {:typings elem-typings
                                                      :extra-eqs (into []
                                                                       (comp (map ::mono-type)
                                                                             (map (fn [elem-type]
                                                                                    [elem-type elem-type-var])))
                                                                       elem-typings)})]

                {::mono-env (::mono-env combined-typing)

                 ::mono-type {::type expr-type
                              ::elem-type (apply-mapping elem-type-var (::mapping combined-typing))}})

              :if
              (let [type-var (->type-var :if)
                    [pred-typing then-typing else-typing :as typings] (map (comp type-value-expr* expr) [:pred-expr :then-expr :else-expr])
                    combined-typing (combine-typings {:typings typings
                                                      :extra-eqs [[(::mono-type pred-typing) {::type :primitive
                                                                                              ::primitive-type :bool}]
                                                                  [(::mono-type then-typing) type-var]
                                                                  [(::mono-type else-typing) type-var]]})]

                {::mono-env (::mono-env combined-typing)
                 ::mono-type (apply-mapping type-var (::mapping combined-typing))})

              :fn
              (let [{:keys [locals body-expr]} expr
                    {:keys [::mono-type ::mono-env]} (type-value-expr* body-expr)]
                {::mono-env (apply dissoc mono-env locals)
                 ::mono-type {::type :fn
                              ::param-types (into [] (map #(or (get mono-env %) (->type-var %)) locals))
                              ::return-type mono-type}})

              :call
              (let [[fn-expr & arg-exprs] (:exprs expr)
                    [{{:keys [::param-types ::return-type] :as fn-expr-type} ::mono-type, :as fn-typing}
                     & param-typings
                     :as typings] (map type-value-expr* (:exprs expr))

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
                           ::mono-type (apply-mapping return-type mapping)})))))]

    {::poly-type (mono-type->poly-type (::mono-type (type-value-expr* expr)))}))

(defn with-type [expr {:keys [env]}]
  (merge expr
         (case (:expr-type expr)
           :def
           (let [{:keys [locals body-expr]} expr]
             {::poly-type {::mono-type :env-update
                           ::env-update-type :def
                           ::def-expr-type (type-value-expr (if locals
                                                              {:expr-type :fn
                                                               :locals locals
                                                               :body-expr body-expr}
                                                              body-expr)
                                                            {:env env})}})

           (type-value-expr expr {:env env}))))
