(ns bridje.analyser
  (:require [bridje.util :as u]
            [bridje.type-checker :as tc]
            [clojure.spec.alpha :as s]
            [clojure.walk :as w]
            [clojure.string :as str]
            [clojure.set :as set]))

(s/def ::symbol-form
  (s/and (s/cat :_sym #{:symbol}
                :sym symbol?)
         (s/conformer #(:sym %))))

(s/def ::colon-sym-form
  (s/and ::symbol-form
         #{(symbol "::")}))

(s/def ::keyword-form
  (s/and (s/cat :_kw #{:keyword}
                :kw keyword?)
         (s/conformer #(:kw %))))

(declare analyse)

(ns-unmap *ns* 'analyse-call)
(defmulti analyse-call
  (fn [[[form-type maybe-sym] & more-forms :as forms] _]
    (when (= :symbol form-type)
      maybe-sym)))

(defmethod analyse-call 'if [[_ & forms] ctx]
  (let [{:keys [pred-form then-form else-form] :as conformed} (s/conform (s/cat :pred-form any?
                                                                                :then-form any?
                                                                                :else-form any?)
                                                                         forms)]
    (if (= ::s/invalid conformed)
      (throw (ex-info "Invalid if" {}))
      {:expr-type :if
       :pred-expr (analyse pred-form (-> ctx (dissoc :loop-locals)))
       :then-expr (analyse then-form ctx)
       :else-expr (analyse else-form ctx)})))

(s/def ::bindings-form
  (s/and (s/cat :_vector #{:vector}
                :bindings (s/* (s/cat :binding-sym ::symbol-form
                                      :binding-form any?)))
         (s/conformer #(:bindings %))))

(defmethod analyse-call 'let [[_ & forms] ctx]
  (let [conformed (s/conform (s/cat :bindings-form (s/spec ::bindings-form)
                                    :body-form any?)
                             forms)]
    (if (= ::s/invalid conformed)
      (throw (ex-info "Invalid let" {}))
      (let [{:keys [bindings-form body-form]} conformed
            {:keys [bindings locals]} (reduce (fn [{:keys [bindings locals]} {:keys [binding-sym binding-form]}]
                                                (let [local (gensym binding-sym)
                                                      ctx (-> ctx
                                                              (update :locals (fnil into {}) locals)
                                                              (dissoc :loop-locals))]
                                                  {:bindings (conj bindings [local (analyse binding-form ctx)])
                                                   :locals (assoc locals binding-sym local)}))
                                              {:bindings []
                                               :locals {}}
                                              bindings-form)]
        {:expr-type :let
         :bindings bindings
         :body-expr (analyse body-form (-> ctx
                                           (update :locals (fnil into {}) locals)))}))))

(defmethod analyse-call 'loop [[_loop & forms] ctx]
  (let [conformed (s/conform (s/cat :bindings-form ::bindings-form
                                    :body-form any?)
                             forms)]
    (if (= ::s/invalid conformed)
      (throw (ex-info "Invalid loop" {}))
      (let [{:keys [bindings-form body-form]} conformed
            bindings (for [{:keys [binding-sym binding-form]} bindings-form]
                       (let [local (gensym binding-sym)]
                         {:sym binding-sym
                          :local local
                          :expr (analyse binding-form (-> ctx (dissoc :loop-locals)))}))]
        {:expr-type :loop
         :bindings (map (juxt :local :expr) bindings)
         :body-expr (analyse body-form (-> ctx
                                           (update :locals (fnil into {}) (map (juxt :sym :local)) bindings)
                                           (assoc :loop-locals (map :local bindings))))}))))

(defmethod analyse-call 'recur [[_recur & recur-arg-forms] {:keys [loop-locals] :as ctx}]
  (cond
    (nil? loop-locals) (throw (ex-info "'recur' called from non-tail position"
                                       {}))
    (not= (count loop-locals) (count recur-arg-forms)) (throw (ex-info "'recur' called with wrong number of arguments"
                                                                       {:expected (count loop-locals)
                                                                        :found (count recur-arg-forms)}))
    :else {:expr-type :recur
           :exprs (mapv #(analyse % (dissoc ctx :loop-locals)) recur-arg-forms)
           :loop-locals loop-locals}))

(defmethod analyse-call 'fn [[_fn & forms] ctx]
  (let [conformed (s/conform (s/cat :params-form (s/spec (s/cat :_list #{:list}
                                                                :sym-form ::symbol-form
                                                                :param-forms (s/* ::symbol-form))),
                                    :body-form any?)
                             forms)]

    (if (= ::s/invalid conformed)
      (throw (ex-info "Invalid fn" {}))

      (let [{:keys [params-form body-form]} conformed
            {:keys [sym-form param-forms]} params-form
            local-mapping (->> param-forms (map (comp (juxt identity gensym) :sym)))]

        {:expr-type :fn
         :sym (:sym sym-form)
         :locals (map second local-mapping)
         :body-expr (analyse body-form (-> ctx
                                           (update :locals (fnil into {}) local-mapping)
                                           (dissoc :loop-locals)))}))))

(s/def ::def-params
  (s/or :just-sym ::symbol-form
        :sym+params (s/spec (s/cat :_list #{:list}
                                   :sym-form ::symbol-form
                                   :param-forms (s/* ::symbol-form)))))

(defmethod analyse-call 'def [[_def & forms] ctx]
  (let [conformed (s/conform (s/cat :params-form ::def-params, :body-form any?) forms)]

    (if (= ::s/invalid conformed)
      (throw (ex-info "Invalid def" {:forms forms}))

      (let [{:keys [params-form body-form]} conformed
            {:keys [sym-form param-forms]} (case (first params-form)
                                             :just-sym {:sym-form (second params-form)}
                                             :sym+params (second params-form))
            local-mapping (some->> param-forms (map (juxt identity gensym)))]

        {:expr-type :def
         :sym sym-form
         :locals (some->> local-mapping (map second))
         :body-expr (analyse body-form (-> ctx
                                           (update :locals (fnil into {}) local-mapping)
                                           (dissoc :loop-locals)))}))))

(defn exact-sym [sym]
  (s/and ::symbol-form #{sym}))

(s/def ::primitive-type-form
  (s/and (s/or :string (exact-sym 'String)
               :bool (exact-sym 'Bool)
               :int (exact-sym 'Int)
               :float (exact-sym 'Float)
               :big-int (exact-sym 'BigInt)
               :big-float (exact-sym 'BigFloat))
         (s/conformer first)))

(s/def ::type-var-sym-form
  (s/and ::symbol-form
         #(Character/isLowerCase (first (name %)))))

(s/def ::mono-type-form
  (s/or :primitive ::primitive-type-form
        :vector (s/spec (s/and (s/cat :_vector #{:vector}
                                      :elem-type-form ::mono-type-form)
                               (s/conformer #(:elem-type-form %))))
        :set (s/spec (s/and (s/cat :_set #{:set}
                                   :elem-type-form ::mono-type-form)
                            (s/conformer #(:elem-type-form %))))
        :type-var ::type-var-sym-form
        :record (s/spec (s/and (s/cat :_record #{:record}
                                      :kws (s/* ::keyword-form))
                               (s/conformer #(set (:kws %)))))
        :applied (s/spec (s/cat :_list #{:list}
                                :constructor-sym ::symbol-form
                                :param-forms (s/* ::mono-type-form)))))

(defn extract-mono-type [mono-type-form {:keys [->type-var env] :as ctx}]
  (let [[mono-type-type arg] mono-type-form]
    (case mono-type-type
      :primitive (tc/primitive-type arg)
      :vector (tc/vector-of (extract-mono-type arg ctx))
      :set (tc/set-of (extract-mono-type arg ctx))
      :record (tc/record-of (gensym 'r) arg)
      :type-var (->type-var arg)
      :applied (tc/->adt (get-in arg [:constructor-sym])
                         (->> (:param-forms arg)
                              (into [] (map #(extract-mono-type % ctx))))))))

(s/def ::type-signature-form
  (s/cat :_list #{:list}
         :_colon ::colon-sym-form
         :params-form (s/or :fn-shorthand (s/spec (s/cat :_list #{:list}
                                                         :name-sym ::symbol-form
                                                         :param-type-forms (s/* ::mono-type-form)))
                            :just-name ::symbol-form)
         :return-form ::mono-type-form))

(defn extract-type-signature [type-signature-form ctx]
  (let [ctx (assoc ctx :->type-var (memoize tc/->type-var))
        {[param-form-type params-form] :params-form
         :keys [return-form]} type-signature-form
        {:keys [sym param-type-forms]} (case param-form-type
                                         :fn-shorthand {:sym (get-in params-form [:name-sym])
                                                        :param-type-forms (:param-type-forms params-form)}
                                         :just-name {:sym params-form})
        return-type (extract-mono-type return-form ctx)]

    {:sym sym
     ::tc/poly-type (tc/mono->poly (if param-type-forms
                                     (tc/fn-type (mapv #(extract-mono-type % ctx) param-type-forms) return-type)
                                     return-type))}))


(defmethod analyse-call 'defclj [[_defclj & forms] ctx]
  (let [conformed (s/conform (s/cat :ns-sym ::symbol-form
                                    :type-sig-forms (s/* (s/spec ::type-signature-form)))
                             forms)]

    (if (= ::s/invalid conformed)
      (throw (ex-info "Invalid defclj" {}))

      (let [{:keys [ns-sym type-sig-forms]} conformed
            type-sigs (map #(extract-type-signature % ctx) type-sig-forms)
            publics (try
                      (require ns-sym)
                      (ns-publics ns-sym)
                      (catch Exception e
                        (throw (ex-info "Failed requiring defclj namespace" {:ns ns-sym} e))))]

        (when-let [unavailable-syms (seq (set/difference (into #{} (map :sym) type-sigs)
                                                         (set (keys publics))))]
          (throw (ex-info "Couldn't find CLJ vars" {:ns ns-sym
                                                    :unavailable-syms (set unavailable-syms)})))

        (set/difference (into #{} (map :sym) type-sigs)
                        (set (keys publics)))

        {:expr-type :defclj
         :clj-fns (into #{} (map (fn [{:keys [sym ::tc/poly-type]}]
                                   {:ns ns-sym
                                    :sym sym
                                    :value @(get publics sym)
                                    ::tc/poly-type poly-type}))
                        type-sigs)}))))

(defmethod analyse-call (symbol "::") [[_ & forms] ctx]
  (let [{[subject-type subject-form] :subject, :keys [type-form]} (s/conform (s/cat :subject (s/or :keyword ::keyword-form
                                                                                                   :symbol ::symbol-form)
                                                                                    :type-form ::mono-type-form)
                                                                             forms)]
    (merge {::tc/mono-type (extract-mono-type type-form ctx)}
           (case subject-type
             :keyword {:expr-type :defattribute
                       :attribute subject-form}))))

(defmethod analyse-call 'defadt [[_ & forms] ctx]
  (let [conformed (s/conform (s/cat :name-sym ::symbol-form
                                    ;; TODO add attrs in here, will make recursive ADTs much easier
                                    :constructors-forms (s/* (s/or :value-constructor ::symbol-form
                                                                   :constructor+params (s/spec (s/cat :_list #{:list}
                                                                                                      :constructor-sym ::symbol-form
                                                                                                      :params-forms (s/* ::mono-type-form))))))
                             forms)]

    (if (= ::s/invalid conformed)
      (throw (ex-info "Invalid 'defadt'" {}))
      (let [{:keys [name-sym constructors-forms]} conformed]
        {:expr-type :defadt
         :sym name-sym
         :constructors (->> constructors-forms
                            (into [] (map (fn [[c-type c-args]]
                                            (case c-type
                                              :value-constructor {:constructor-sym c-args}
                                              :constructor+params {:constructor-sym (:constructor-sym c-args)
                                                                   :param-mono-types (->> (:params-forms c-args)
                                                                                          (into [] (map (fn [form]
                                                                                                          (extract-mono-type form ctx)))))})))))}))))

(defmethod analyse-call :default [forms ctx]
  {:expr-type :call
   :exprs (mapv #(analyse % ctx) forms)})

(defn analyse [[form-type & [form-value :as forms]] {:keys [env locals] :as ctx}]
  (case form-type
    (:int :float :big-int :big-float) {:expr-type form-type
                                       :number form-value}

    :bool {:expr-type :bool
           :bool form-value}

    :string {:expr-type :string
             :string form-value}

    (:vector :set) {:expr-type form-type
                    :exprs (mapv #(analyse % (-> ctx (dissoc :loop-locals))) forms)}

    :record (let [conformed (s/conform (s/* (s/cat :k ::keyword-form
                                                   :v any?))
                                       forms)]
              {:expr-type :record
               :entries (for [{:keys [k v]} conformed]
                          {:k k
                           :v (analyse v (-> ctx (dissoc :loop-locals)))})})

    :list (analyse-call forms ctx)

    :symbol (let [[sym] forms]
              (or (when-let [local (get locals sym)]
                    {:expr-type :local
                     :local local})

                  (when (contains? (:vars env) sym)
                    {:expr-type :global
                     :global sym})

                  (throw (ex-info "Can't find" {:sym sym
                                                :ctx ctx}))))

    :keyword (let [[kw] forms]
               (if (get-in env [:attributes kw])
                 {:expr-type :attribute
                  :attribute kw}

                 (throw (ex-info "Cannot resolve attribute" {:attribute kw}))))))
