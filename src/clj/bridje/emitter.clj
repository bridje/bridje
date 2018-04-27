(ns bridje.emitter
  (:require [bridje.runtime :as rt]
            [bridje.type-checker :as tc]
            [bridje.util :as u]
            [clojure.string :as str]))

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

(def ^:dynamic *ctx* {})

(defmacro with-ctx-update [update-form & body]
  `(binding [*ctx* (-> ctx ~update-form)]
     ~@body))

(defmulti emit-value-expr* :expr-type)

(defmethod emit-value-expr* :string [{:keys [string]}] string)
(defmethod emit-value-expr* :bool [{:keys [bool]}] bool)
(defmethod emit-value-expr* :int [{:keys [number]}] number)
(defmethod emit-value-expr* :float [{:keys [number]}] number)
(defmethod emit-value-expr* :big-int [{:keys [number]}] number)
(defmethod emit-value-expr* :big-float [{:keys [number]}] number)

(defmethod emit-value-expr* :symbol [{:keys [sym]}]
  `'~sym)

(defmethod emit-value-expr* :vector [{:keys [exprs]}]
  (into [] (map emit-value-expr*) exprs))

(defmethod emit-value-expr* :set [{:keys [exprs]}] (into #{} (map emit-value-expr*) exprs))

(defmethod emit-value-expr* :record [{:keys [entries]}]
  (into {} (map (juxt :k (comp emit-value-expr* :v))) entries))

(defmethod emit-value-expr* :attribute [{:keys [attribute]}] attribute)

(defmethod emit-value-expr* :if [{:keys [pred-expr then-expr else-expr]}]
  `(if ~(emit-value-expr* pred-expr)
     ~(emit-value-expr* then-expr)
     ~(emit-value-expr* else-expr)))

(defmethod emit-value-expr* :local [{:keys [local]}]
  local)

(defmethod emit-value-expr* :global [{:keys [global]}]
  (get-in *ctx* [:globals global]))

(defmethod emit-value-expr* :effect-fn [{:keys [effect-fn]}]
  `(get rt/*effect-fns* '~effect-fn))

(defmethod emit-value-expr* :clj-var [{:keys [clj-var]}]
  clj-var)

(defmethod emit-value-expr* :let [{:keys [bindings body-expr]}]
  `(let [~@(mapcat (fn [[local expr]]
                     [local (emit-value-expr* expr)])
                   bindings)]
     ~(emit-value-expr* body-expr)))

(defmethod emit-value-expr* :case [{:keys [expr adt clauses]}]
  (let [expr-sym (gensym 'expr)
        constructor (gensym 'constructor)]

    `(let [~expr-sym ~(emit-value-expr* expr)]
       (cond
         ~@(mapcat (fn [{:keys [constructor-sym default-sym locals expr]}]
                     (cond
                       constructor-sym (let [{:keys [class fields]} (get-in *ctx* [:env :adt-constructors constructor-sym])]
                                         `[(instance? ~class ~expr-sym)
                                           (let [~@(interleave locals (mapv #(list '. expr-sym %) fields))]
                                             ~(emit-value-expr* expr))])

                       default-sym `[:else (let [~(first locals) ~expr-sym]
                                             ~(emit-value-expr* expr))]))
                   clauses)))))

(defmethod emit-value-expr* :fn [{:keys [sym locals body-expr]}]
  `(fn ~sym [~@locals]
     ~(emit-value-expr* body-expr)))

(defmethod emit-value-expr* :call [{:keys [exprs]}]
  `(~@(mapv emit-value-expr* exprs)))

(defmethod emit-value-expr* :loop [{:keys [bindings body-expr]}]
  `(loop [~@(mapcat (fn [[local expr]]
                      [local (emit-value-expr* expr)])
                    bindings)]
     ~(emit-value-expr* body-expr)))

(defmethod emit-value-expr* :recur [{:keys [exprs]}]
  `(recur ~@(mapv emit-value-expr* exprs)))

(defmethod emit-value-expr* :handling [{:keys [handlers body-expr]}]
  `(binding [rt/*effect-fns* (merge rt/*effect-fns*
                                    ~(into {}
                                           (comp (mapcat :handler-exprs)
                                                 (map (fn [{:keys [sym] :as fn-expr}]
                                                        [`'~sym (emit-value-expr* fn-expr)])))
                                           handlers))]
     ~(emit-value-expr* body-expr)))

(defn emit-value-expr [expr env]
  (let [sub-exprs (u/sub-exprs expr)
        globals (find-globals sub-exprs)
        clj-namespaces (find-clj-namespaces sub-exprs)
        env-sym (gensym 'env)]

    (binding [*ctx* {:globals globals, :env env}]
      `(fn [~env-sym]
         (do
           ~@(for [clj-ns clj-namespaces]
               `(require '~clj-ns)))

         (let [~@(mapcat (fn [[global global-sym]]

                           [global-sym `(get-in ~env-sym [:vars
                                                          '~(symbol (name global))
                                                          :value])])
                         globals)]
           ~(emit-value-expr* expr))))))

(defmulti interpret-expr
  (fn [{:keys [expr-type]} {:keys [env]}]
    expr-type))

(defmethod interpret-expr :typedef [{:keys [sym ::tc/typedef-poly-type]} {:keys [env]}]
  {:env (-> env
            (update-in [:vars sym] merge {::tc/poly-type typedef-poly-type}))})

(defmethod interpret-expr :def [{:keys [sym locals body-expr ::tc/def-poly-type] :as expr} {:keys [env] :as ctx}]
  {:env (assoc-in env [:vars sym] {::tc/poly-type def-poly-type
                                   :value ((eval (emit-value-expr (if locals
                                                                    {:expr-type :fn
                                                                     :sym sym
                                                                     :locals locals
                                                                     :body-expr body-expr}
                                                                    body-expr)
                                                                  env))
                                           env)})})

(defmethod interpret-expr :defmacro [{:keys [sym locals body-expr]} {:keys [env]}]
  {:env (assoc-in env [:macros sym] {:value ((eval (emit-value-expr (if locals
                                                                      {:expr-type :fn
                                                                       :sym sym
                                                                       :locals locals
                                                                       :body-expr body-expr}
                                                                      body-expr)
                                                                    env))
                                             env)})})

(defmethod interpret-expr :attribute-typedef [{:keys [attribute ::tc/mono-type]} {:keys [env]}]
  {:env (-> env
            (update :attributes assoc attribute {::tc/mono-type mono-type}))})

(defmethod interpret-expr :defadt [{:keys [sym ::tc/adt-poly-type ::tc/constructor-poly-types constructors]} {:keys [env]}]
  (let [adt-ns (create-ns 'brj.adts)]
    {:env (-> env
              (update :adts assoc sym {:sym sym
                                       ::tc/poly-type adt-poly-type
                                       :constructor-syms (mapv :constructor-sym constructors)})

              (update :adt-constructors (fnil into {}) (for [{:keys [constructor-sym param-mono-types]} constructors]
                                                         (let [fields (map (comp symbol str) (repeat 'field) (range (count param-mono-types)))
                                                               class (binding [*ns* adt-ns]
                                                                       (eval `(defrecord ~constructor-sym [~@fields])))]
                                                           [constructor-sym
                                                            {:adt sym
                                                             :class class
                                                             :fields fields
                                                             ::tc/poly-type (get constructor-poly-types constructor-sym)}])))

              (update :vars merge (->> constructors
                                       (into {} (map (juxt :constructor-sym
                                                           (fn [{:keys [constructor-sym param-mono-types]}]
                                                             (let [constructor @(ns-resolve adt-ns (symbol (str "->" (name constructor-sym))))]
                                                               {:value (if (seq param-mono-types)
                                                                         constructor
                                                                         (constructor))

                                                                ::tc/poly-type (get constructor-poly-types constructor-sym)}))))))))}))

(defmethod interpret-expr :defeffect [{:keys [sym definitions]} {:keys [env]}]
  {:env (-> env
            (update :effects assoc sym {:definitions (into #{} (map :sym) definitions)})

            (update :effect-fns (fnil into {})
                    (map (juxt :sym
                               #(merge {:effect sym}
                                       (select-keys % [::tc/poly-type]))))
                    definitions))})

(defmethod interpret-expr :defclj [{:keys [clj-fns]} {:keys [env]}]
  {:env (reduce (fn [env {:keys [sym value ::tc/poly-type] :as foo}]
                  (-> env
                      (assoc-in [:vars sym] {:value value
                                             ::tc/poly-type poly-type})))
                env
                clj-fns)})

(defmethod interpret-expr :defjava [{:keys [^Class class members]} {:keys [env]}]
  (let [class-basename (symbol (last (str/split (.getName class) #"\.")))]
    {:env (-> env
              (assoc-in [:classes class-basename] {:class class})
              (update :vars merge (->> members
                                       (into {} (map (fn [{:keys [sym op ::tc/poly-type]}]
                                                       (let [mono-type (::tc/mono-type poly-type)
                                                             param-syms (when (= :fn (::tc/type mono-type))
                                                                          (repeatedly (count (::tc/param-types mono-type))
                                                                                      #(gensym 'param)))]
                                                         [sym {::tc/poly-type poly-type
                                                               :value (eval (case op
                                                                              ;; TODO all the rest
                                                                              :invoke-virtual
                                                                              `(fn [~@param-syms]
                                                                                 (~(symbol (str "." (name sym))) ~@param-syms))

                                                                              :invoke-static
                                                                              `(fn [~@param-syms]
                                                                                 (~(symbol (.getName class) (name sym))
                                                                                  ~@param-syms))))}])))))))}))
