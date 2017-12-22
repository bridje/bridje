(ns bridje.analyser
  (:require [bridje.util :as u]
            [bridje.type-checker :as tc]
            [clojure.spec.alpha :as s]
            [clojure.walk :as w]
            [clojure.string :as str]
            [clojure.set :as set]))

#_(defn type-tv-mapping [form]
  (->> (u/sub-forms form)
       (into {} (comp (filter (every-pred (comp #{:symbol} :form-type)
                                          (comp #(Character/isLowerCase %) first name :sym)))
                      (distinct)
                      (map (fn [{:keys [sym]}]
                             {sym (tc/->type-var (name sym))}))))))

#_(defn type-declaration-tv-mapping [form]
  (->> (u/sub-forms form)
       (into {} (comp (keep :sym)
                      (filter (comp #(Character/isLowerCase %) first name))
                      (map (fn [sym]
                             [sym (tc/->type-var sym)]))))))

#_(defn parse-type [form {:keys [env tv-mapping]}]
  (let [tv-mapping (merge (type-declaration-tv-mapping form)
                          tv-mapping)]
    (letfn [(parse-type* [{:keys [form-type] :as form}]
              (case form-type
                :symbol (or (when-let [prim-type (get '{String :string, Bool :bool,
                                                        Int :int, Float :float,
                                                        BigInt :big-int, BigFloat :big-float}
                                                      (:sym form))]
                              (tc/primitive-type prim-type))

                            (get tv-mapping (:sym form))

                            (throw (ex-info "Unexpected symbol, parsing type" {:form form})))

                (:vector :set) (p/parse-forms (:forms form)
                                              (do-parse [elem-type (p/first-form-parser parse-type*)]
                                                (p/no-more-forms #::tc{:type form-type
                                                                       :elem-type elem-type})))

                :list (p/parse-forms (:forms form)
                                     (do-parse [fn-sym (p/literal-sym-parser 'Fn)
                                                param-forms (p/vector-parser (fn [forms] [forms []]))
                                                return-form p/form-parser]
                                       (p/pure #::tc{:type :fn
                                                     :param-types (into [] (map parse-type*) param-forms)
                                                     :return-type (parse-type* return-form)})))
                (throw (ex-info "Unexpected form, parsing type" {:form form}))))]

      #::tc{:type-vars (into #{} (map ::tc/type-var) (vals tv-mapping))
            :mono-type (parse-type* form)})))

#_(def type-declaration-form-parser
  (p/list-parser (do-parse [colon (p/literal-sym-parser colon-sym)

                            {:keys [sym param-forms]}
                            (p/or-parser (-> p/sym-parser
                                             (p/fmap #(select-keys % [:sym])))

                                         (do-parse [{:keys [sym param-forms]}
                                                    (p/list-parser (do-parse [{:keys [sym]} p/sym-parser
                                                                              param-forms (p/maybe-many p/form-parser)]
                                                                     (p/no-more-forms {:sym sym
                                                                                       :param-forms param-forms})))]
                                           (p/pure {:sym sym, :param-forms param-forms})))

                            return-form p/form-parser]
                   (p/no-more-forms {:sym sym, :param-forms param-forms, :return-form return-form}))))

#_(defn type-declaration-parser [env]
  (do-parse [{:keys [sym param-forms return-form]} type-declaration-form-parser]
    (let [tv-mapping (type-declaration-tv-mapping {:param-forms param-forms,
                                                   :return-form return-form})]
      (p/pure {:sym sym
               ::tc/poly-type (parse-type (if param-forms
                                            {:form-type :list,
                                             :forms [{:form-type :symbol, :sym 'Fn}
                                                     {:form-type :vector, :forms param-forms}
                                                     return-form]}
                                            return-form)
                                          {:env env})}))))

#_(defn attributes-decl-parser [{:keys [prefix env tv-mapping]}]
  (p/record-parser
   (fn [entries]
     [(into {}
            (map (fn [[kw type-form]]
                   (let [{:keys [::tc/mono-type ::tc/type-vars] :as poly-type} (parse-type type-form {:env env, :tv-mapping tv-mapping})]
                     (if (seq (set/difference type-vars (into #{} (map ::tc/type-var) (vals tv-mapping))))
                       (throw (ex-info "Attribute types can't be polymorphic (for now?)"
                                       {::tc/poly-type poly-type}))
                       [(keyword (format "%s.%s" (name prefix) (name kw))) {::tc/mono-type mono-type}]))))
            entries)
      []])))

(defn form-type-spec [form-type]
  #(= form-type (:form-type %)))

(s/def ::symbol-form
  (s/and (form-type-spec :symbol)
         #(symbol? (:sym %))))

(s/def ::colon-sym-form
  (s/and ::symbol-form
         (let [colon-sym (symbol ":")]
           #(= colon-sym (:sym %)))))

(s/def ::keyword-form
  (s/and (form-type-spec :keyword)
         #(keyword? (:kw %))))

(s/def ::list-form
  (form-type-spec :list))

(s/def ::vector-form
  (form-type-spec :vector))

(s/def ::record-form
  (form-type-spec :record))

(defn nested [form-type spec]
  (s/and (form-type-spec form-type)
         (s/conformer (comp (partial s/conform spec)
                            :forms))))

(declare analyse)

(defmulti analyse-call
  (fn [form _]
    (let [{:keys [form-type sym]} (first (:forms form))]
      (when (= :symbol form-type)
        (keyword sym))))

  :default ::default)

(defmethod analyse-call :if [{:keys [forms]} ctx]
  (let [{:keys [pred-form then-form else-form] :as conformed} (s/conform (s/cat :pred-form any?
                                                                                :then-form any?
                                                                                :else-form any?)
                                                                         (rest forms))]
    (if (= ::s/invalid conformed)
      (throw (ex-info "Invalid if" {}))
      {:expr-type :if
       :pred-expr (analyse pred-form (-> ctx (dissoc :loop-locals)))
       :then-expr (analyse then-form ctx)
       :else-expr (analyse else-form ctx)})))

(s/def ::bindings-form
  (nested :vector
          (s/* (s/cat :binding-sym-form ::symbol-form
                      :binding-form any?))))

(defmethod analyse-call :let [{:keys [forms] :as form} ctx]
  (let [conformed (s/conform (s/cat :bindings-forms ::bindings-form
                                    :body-form any?)
                             (rest forms))]
    (if (= ::s/invalid conformed)
      (throw (ex-info "Invalid let" {}))
      (let [{:keys [bindings-forms body-form]} conformed
            {:keys [bindings locals]} (reduce (fn [{:keys [bindings locals]} {:keys [binding-sym-form binding-form]}]
                                                (let [sym (:sym binding-sym-form)
                                                      local (gensym sym)
                                                      ctx (-> ctx
                                                              (update :locals (fnil into {}) locals)
                                                              (dissoc :loop-locals))]
                                                  {:bindings (conj bindings [local (analyse binding-form ctx)])
                                                   :locals (assoc locals sym local)}))
                                              {:bindings []
                                               :locals {}}
                                              bindings-forms)]
        {:expr-type :let
         :bindings bindings
         :body-expr (analyse body-form (-> ctx
                                           (update :locals (fnil into {}) locals)))}))))

(defmethod analyse-call :loop [{:keys [forms] :as form} ctx]
  (let [conformed (s/conform (s/cat :bindings-forms ::bindings-form
                                    :body-form any?)
                             (rest forms))]
    (if (= ::s/invalid conformed)
      (throw (ex-info "Invalid loop" {}))
      (let [{:keys [bindings-forms body-form]} conformed
            bindings (for [{:keys [binding-sym-form binding-form]} bindings-forms]
                       (let [sym (:sym binding-sym-form)
                             local (gensym sym)]
                         {:sym sym
                          :local local
                          :expr (analyse binding-form (-> ctx (dissoc :loop-locals)))}))]
        {:expr-type :loop
         :bindings (map (juxt :local :expr) bindings)
         :body-expr (analyse body-form (-> ctx
                                           (update :locals (fnil into {}) (map (juxt :sym :local)) bindings)
                                           (assoc :loop-locals (map :local bindings))))}))))

(defmethod analyse-call :recur [{[_recur & recur-arg-forms] :forms, :as form} {:keys [loop-locals] :as ctx}]
  (cond
    (nil? loop-locals) (throw (ex-info "'recur' called from non-tail position"
                                       {}))
    (not= (count loop-locals) (count recur-arg-forms)) (throw (ex-info "'recur' called with wrong number of arguments"
                                                                       {:expected (count loop-locals)
                                                                        :found (count recur-arg-forms)}))
    :else {:expr-type :recur
           :exprs (mapv #(analyse % (dissoc ctx :loop-locals)) recur-arg-forms)
           :loop-locals loop-locals}))

(defmethod analyse-call :fn [{:keys [forms] :as form} ctx]
  (let [conformed (s/conform (s/cat :params-form (nested :list
                                                         (s/cat :sym-form ::symbol-form
                                                                :param-forms (s/* ::symbol-form))),
                                    :body-form any?)
                             (rest forms))]

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
        :sym+params (nested :list
                            (s/cat :sym-form ::symbol-form
                                   :param-forms (s/* ::symbol-form)))))

(defmethod analyse-call :def [{:keys [forms] :as form} ctx]
  (let [conformed (s/conform (s/cat :params-form ::def-params, :body-form any?)
                             (rest forms))]

    (if (= ::s/invalid conformed)
      (throw (ex-info "Invalid def" {}))

      (let [{:keys [params-form body-form]} conformed
            {:keys [sym-form param-forms]} (case (first params-form)
                                             :just-sym {:sym-form (second params-form)}
                                             :sym+params (second params-form))
            local-mapping (some->> param-forms (map (comp (juxt identity gensym) :sym)))]

        {:expr-type :def
         :sym (:sym sym-form)
         :locals (some->> local-mapping (map second))
         :body-expr (analyse body-form (-> ctx
                                           (update :locals (fnil into {}) local-mapping)
                                           (dissoc :loop-locals)))}))))

(defn exact-sym [sym]
  (s/and ::symbol-form
         #(= sym (:sym %))))

(s/def ::primitive-type-form
  (s/or :string (exact-sym 'String)
        :bool (exact-sym 'Bool)
        :int (exact-sym 'Int)
        :float (exact-sym 'Float)
        :big-int (exact-sym 'BigInt)
        :big-float (exact-sym 'BigFloat)))

(s/def ::mono-type-form
  (s/or :primitive ::primitive-type-form
        :vector (nested :vector (s/cat :elem-type-form ::mono-type-form))
        :set (nested :set (s/cat :elem-type-form ::mono-type-form))
        :type-var (s/and ::symbol-form
                         #(Character/isLowerCase (first (name (:sym %)))))))

(defn extract-mono-type [mono-type-form {:keys [->type-var env] :as ctx}]
  (let [[mono-type-type arg] mono-type-form]
    (case mono-type-type
      :primitive (tc/primitive-type (first arg))
      :vector (tc/vector-of (extract-mono-type (:elem-type-form arg) ctx))
      :set (tc/set-of (extract-mono-type (:elem-type-form arg) ctx))
      :type-var (->type-var (:sym arg)))))

(s/def ::type-signature-form
  (nested :list
          (s/cat :_colon ::colon-sym-form
                 :params-form (s/or :fn-shorthand (nested :list
                                                          (s/cat :name-sym-form ::symbol-form
                                                                 :param-type-forms (s/* ::mono-type-form)))
                                    :just-name ::symbol-form)
                 :return-form ::mono-type-form)))

(defn extract-type-signature [type-signature-form ctx]
  (let [ctx (assoc ctx :->type-var (memoize tc/->type-var))
        {[param-form-type params-form] :params-form
         :keys [return-form]} type-signature-form
        {:keys [sym param-type-forms]} (case param-form-type
                                         :fn-shorthand {:sym (get-in params-form [:name-sym-form :sym])
                                                        :param-type-forms (:param-type-forms params-form)}
                                         :just-name {:sym (:sym params-form)})
        return-type (extract-mono-type return-form ctx)]

    {:sym sym
     ::tc/poly-type (tc/mono->poly (if param-type-forms
                                     (tc/fn-type (mapv #(extract-mono-type % ctx) param-type-forms) return-type)
                                     return-type))}))

(defn parse-type-signature [form ctx]
  (let [conformed (s/conform ::type-signature-form form)]
    (if (= ::s/invalid conformed)
      (throw (ex-info "Invalid type signature" {}))
      (extract-type-signature conformed ctx))))

(defmethod analyse-call :defclj [{:keys [forms] :as form} ctx]
  (let [conformed (s/conform (s/cat :ns-sym-form ::symbol-form
                                    :type-sig-forms (s/* ::type-signature-form))
                             (rest forms))]
    (if (= ::s/invalid conformed)
      (throw (ex-info "Invalid defclj" {}))

      (let [{:keys [ns-sym-form type-sig-forms]} conformed
            type-sigs (map #(extract-type-signature % ctx) type-sig-forms)
            ns-sym (:sym ns-sym-form)
            publics (try
                      (require ns-sym)
                      (ns-publics ns-sym)
                      (catch Exception e
                        (throw (ex-info "Failed requiring defclj namespace" {:ns ns-sym} e))))]

        (when-let [unavailable-syms (seq (set/difference (into #{} (map :sym) type-sigs)
                                                         (set (keys publics))))]
          (throw (ex-info "Couldn't find CLJ vars" {:ns ns-sym
                                                    :unavailable-syms (set unavailable-syms)})))

        {:expr-type :defclj
         :clj-fns (into #{} (map (fn [{:keys [sym ::tc/poly-type]}]
                                   {:ns ns-sym
                                    :sym sym
                                    :value @(get publics sym)
                                    ::tc/poly-type poly-type}))
                        type-sigs)}))))

(defmethod analyse-call ::default [{:keys [forms]} ctx]
  {:expr-type :call
   :exprs (mapv #(analyse % ctx) forms)})

(defn analyse [{:keys [form-type forms] :as form} {:keys [env locals] :as ctx}]
  (case form-type
    (:int :float :big-int :big-float) {:expr-type form-type
                                       :number (:number form)}

    :string {:expr-type :string
             :string (:string form)}

    (:vector :set) {:expr-type form-type
                    :exprs (mapv #(analyse % (-> ctx (dissoc :loop-locals))) forms)}

    :record (let [conformed (s/assert (s/* (s/cat :k ::keyword-form
                                                  :v any?))
                                      forms)]
              {:expr-type :record
               :entries (for [{:keys [k v]} conformed]
                          {:k (:kw k)
                           :v (analyse v (-> ctx (dissoc :loop-locals)))})})

    :list (analyse-call form ctx)

    :symbol (let [{:keys [sym]} form]
              (or (when-let [local (get locals sym)]
                    {:expr-type :local
                     :local local})

                  (when (contains? (:vars env) sym)
                    {:expr-type :global
                     :global (:sym form)})

                  (throw (ex-info "Can't find" {:sym sym
                                                :ctx ctx}))))

    :keyword (let [{:keys [kw]} form]
               (if (get-in env [:attributes kw])
                 {:expr-type :attribute
                  :attribute kw}

                 (throw (ex-info "Cannot resolve attribute" {:attribute kw}))))))
