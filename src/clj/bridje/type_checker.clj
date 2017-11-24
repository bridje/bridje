(ns bridje.type-checker
  (:require [clojure.set :as set]))

;; https://gergo.erdi.hu/projects/tandoori/Tandoori-Compositional-Typeclass.pdf

(defn ->type-var [s]
  {:type/type :type-var
   :type/type-var (gensym (name s))})

;; mono-env :: {Local -> Type}
;; typing :: (mono-env, type)

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

(do
  (defn apply-mapping [type mapping]
    (case (:type/type type)
      (:int :float :big-int :big-float :string :bool) type
      :type-var (get mapping (:type/type-var type) type)))

  (defn mono-env-apply-mapping [mono-env mapping]
    (into {}
          (map (fn [[local mono-type]]
                 [local (apply-mapping mono-type mapping)]))
          mono-env))

  (defn unify-eqs [eqs]
    (loop [[[{t1-type :type/type, :as t1} {t2-type :type/type, :as t2} :as eq] & more-eqs] eqs
           mapping {}]
      (if-not eq
        mapping

        (let [ex (ex-info "Cannot unify types" {:types [t1 t2]})]
          (cond
            (= t1 t2) (recur more-eqs mapping)

            (= :type-var t1-type)
            (let [new-mapping {(:type/type-var t1) t2}]
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

            :else (case (:type/type t1)
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

  (defn type-expr [expr {:keys [env current-ns]}]
    (letfn [(type-coll-expr [{:keys [expr-type] :as expr}]
              (let [elem-type-var (->type-var :elem)
                    elem-typings (map type-expr* (:exprs expr))
                    type-eqs (into (mono-envs->type-equations (map :type/mono-env elem-typings))
                                   (into []
                                         (comp (map :type/mono-type)
                                               (map (fn [elem-type]
                                                      [elem-type elem-type-var])))
                                         elem-typings))
                    mapping (unify-eqs type-eqs)]

                ;; make type equations from all the mono-envs, and each elem type being equal to the elem-type-var
                ;; unify into a mapping
                ;; apply the mapping to each of the mono-envs
                ;; union the mono-envs, result.
                {:type/mono-env (->> elem-typings
                                     (map (comp #(mono-env-apply-mapping % mapping)
                                                :type/mono-env))
                                     mono-env-union)
                 :type/mono-type {:type/type expr-type
                                  :type/elem-type (apply-mapping elem-type-var mapping)}}))

            (type-expr* [{:keys [expr-type] :as expr}]
              (case expr-type
                (:int :float :big-int :big-float :string :bool)
                {:type/mono-env {}
                 :type/mono-type {:type/type expr-type}}

                :local
                (let [type-var (->type-var (:local expr))]
                  {:type/mono-env {(:local expr) type-var}
                   :type/mono-type type-var})

                (:vector :set) (type-coll-expr expr)


                ))]

      (type-expr* expr)))

  (for [expr (let [identity-fn {:expr-type :fn
                                :sym :foo
                                :locals [::foo-param]
                                :body-expr {:expr-type :local
                                            :local ::foo-param}}]
               [#_{:expr-type :int}
                #_{:expr-type :local
                 :local ::foo}

                {:expr-type :vector
                 :exprs [{:expr-type :int}
                         {:expr-type :int}]}
                #_identity-fn

                #_{:expr-type :call,
                   :exprs [identity-fn
                           {:expr-type :int, :int 4}]}

                #_{:expr-type :if,
                   :pred-expr {:expr-type :bool, :bool false}
                   :then-expr {:expr-type :int, :int 4}
                   :else-expr {:expr-type :int, :int 5}}])]
    [(type-expr expr {})])
  )
