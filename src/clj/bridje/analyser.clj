(ns bridje.analyser
  (:require [bridje.util :as u]
            [bridje.type-checker :as tc]
            [clojure.spec.alpha :as s]
            [clojure.walk :as w]
            [clojure.string :as str]
            [clojure.set :as set]))

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

(s/def ::type-var-sym-form
  (s/and ::symbol-form
         #(Character/isLowerCase (first (name (:sym %))))))

(s/def ::mono-type-form
  (s/or :primitive ::primitive-type-form
        :vector (nested :vector (s/cat :elem-type-form ::mono-type-form))
        :set (nested :set (s/cat :elem-type-form ::mono-type-form))
        :type-var ::type-var-sym-form
        :record (nested :record (s/* ::keyword-form))
        :applied (nested :list (s/cat :constructor-sym ::symbol-form
                                      :param-forms (s/* ::mono-type-form)))))

(defn extract-mono-type [mono-type-form {:keys [->type-var env] :as ctx}]
  (let [[mono-type-type arg] mono-type-form]
    (case mono-type-type
      :primitive (tc/primitive-type (first arg))
      :vector (tc/vector-of (extract-mono-type (:elem-type-form arg) ctx))
      :set (tc/set-of (extract-mono-type (:elem-type-form arg) ctx))
      :record (tc/record-of (gensym 'r) (into #{} (map :kw) arg))
      :type-var (->type-var (:sym arg))
      :applied (tc/->adt (get-in arg [:constructor-sym :sym])
                         (->> (:param-forms arg)
                              (into [] (map #(extract-mono-type % ctx))))))))

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

(defmethod analyse-call :defdata [{:keys [forms]} ctx]
  (let [conformed (s/conform (s/cat :title-form (s/? (s/or :just-name ::symbol-form
                                                           :name+params (nested :list (s/cat :name-form ::symbol-form
                                                                                             :type-var-forms (s/* ::type-var-sym-form)))))
                                    :attrs-form (s/? (nested :record (s/* (s/cat :k ::keyword-form
                                                                                 :v ::mono-type-form))))

                                    :constructors-forms (s/* (s/or :value-constructor ::symbol-form
                                                                   :constructor+params (nested :list (s/cat :constructor-sym ::symbol-form
                                                                                                            :params-forms (s/* ::mono-type-form))))))
                             (rest forms))]

    (if (= ::s/invalid conformed)
      (throw (ex-info "Invalid 'defdata'" {}))
      (let [{:keys [title-form attrs-form constructors-forms]} conformed
            ->type-var (memoize tc/->type-var)
            ctx (merge ctx {:->type-var ->type-var})
            {:keys [name-sym tvs]} (when-let [[tf-type tf-args] title-form]
                                     (case tf-type
                                       :just-name {:name-sym (:sym tf-args)}
                                       :name+params {:name-sym (get-in tf-args [:name-form :sym])
                                                     :tvs (->> (:type-var-forms tf-args)
                                                               (into [] (map (fn [tv-form]
                                                                               (->type-var (:sym tv-form))))))}))
            attrs (->> attrs-form
                       (into [] (map (fn [{:keys [k v]}]
                                       {:k (:kw k)
                                        ::tc/mono-type (extract-mono-type v ctx)}))))]
        {:expr-type :defdata
         :name-sym name-sym
         :type-vars tvs
         :attrs attrs
         :constructors (->> constructors-forms
                            (into [] (map (fn [[c-type c-args]]
                                            (case c-type
                                              :value-constructor {:constructor-sym (:sym c-args)}
                                              :constructor+params {:constructor-sym (get-in c-args [:constructor-sym :sym])
                                                                   :param-mono-types (->> (:params-forms c-args)
                                                                                          (into [] (map (fn [form]
                                                                                                          (extract-mono-type form ctx)))))})))))}))))

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
