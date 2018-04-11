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

(def ^:dynamic ^:private *ctx* {})

(defmacro with-ctx-update [update-form & body]
  `(binding [*ctx* (-> *ctx* ~update-form)]
     ~@body))

#_(ns-unmap *ns* 'analyse-expr)
(defmulti analyse-expr
  (fn [[form-type params]]
    form-type)
  :default ::default)

(defmethod analyse-expr ::default [form]
  (throw (ex-info "ohno" {:form form})))

(defmethod analyse-expr :bool [[_ {:keys [bool]}]]
  {:expr-type :bool, :bool bool})

(defmethod analyse-expr :string [[_ {:keys [string]}]]
  {:expr-type :string, :string string})

(defmethod analyse-expr :number [[_ {:keys [num-type number]}]]
  {:expr-type num-type, :number number})

(defmethod analyse-expr :symbol [[_ {:keys [sym]}]]
  (let [{:keys [env locals]} *ctx*]
    (or (when-let [local (get locals sym)]
          {:expr-type :local
           :local local})

        (when (contains? (:vars env) sym)
          {:expr-type :global
           :global sym})

        (when (contains? (:effect-fns env) sym)
          {:expr-type :effect-fn
           :effect-fn sym})

        (throw (ex-info "Can't find" {:sym sym, :ctx *ctx*})))))

(defmethod analyse-expr :coll [[_ {:keys [coll-type forms]}]]
  {:expr-type coll-type
   :exprs (with-ctx-update (dissoc :loop-locals)
            (mapv analyse-expr forms))})

(s/def ::record-form
  (s/cat :_record #{:record}
         :entries (s/* (s/cat :k ::keyword-form, :v (s/spec ::form)))))

(defmethod analyse-expr :record [[_ {:keys [entries]}]]
  {:expr-type :record
   :entries (->> entries
                 (mapv (fn [{:keys [k v]}]
                         {:k k
                          :v (with-ctx-update (dissoc :loop-locals)
                               (analyse-expr v))})))})

(defmethod analyse-expr :keyword [[_ {:keys [kw]}]]
  (if (get-in *ctx* [:env :attributes kw])
    {:expr-type :attribute
     :attribute kw}

    (throw (ex-info "Cannot resolve attribute" {:attribute kw
                                                :ctx *ctx*}))))

#_(defmethod analyse-call 'quote [[_ & forms] ctx]
  (let [{:keys [sym]} (let [conformed (s/conform (s/cat :sym ::symbol-form)
                                                 forms)]
                        (if (= ::s/invalid conformed)
                          (throw (ex-info "Invalid 'quote'" {}))
                          conformed))]
    {:expr-type :symbol
     :sym sym}))

(s/def ::if-form
  (s/cat :_list #{:list}
         :_if (s/and ::symbol-form #{'if})
         :pred-form (s/spec ::form)
         :then-form (s/spec ::form)
         :else-form (s/spec ::form)))

(defmethod analyse-expr :if [[_ {:keys [pred-form then-form else-form]}]]
  {:expr-type :if
   :pred-expr (with-ctx-update (dissoc :loop-locals)
                (analyse-expr pred-form))
   :then-expr (analyse-expr then-form)
   :else-expr (analyse-expr else-form)})

(s/def ::bindings-form
  (s/and (s/cat :_vector #{:vector}
                :bindings (s/* (s/cat :binding-sym ::symbol-form
                                      :binding-form any?)))
         (s/conformer #(:bindings %))))

#_(defmethod analyse-call 'let [[_ & forms] ctx]
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

#_(defmethod analyse-call 'case [[_ & forms] ctx]
  (let [{:keys [expr-form clause-forms]}
        (let [conformed (s/conform
                         (s/cat :expr-form any?
                                :clause-forms
                                (s/* (s/cat :constructor-form
                                            (s/or :constructor-call
                                                  (s/spec (s/cat :_list #{:list}
                                                                 :constructor-sym ::symbol-form
                                                                 :binding-syms (s/* ::symbol-form)))
                                                  :value-constructor (s/or :default-sym ::type-var-sym-form
                                                                           :constructor-sym ::symbol-form))
                                            :expr-form any?)))
                         forms)]
          (if (= ::s/invalid conformed)
            (throw (ex-info "Invalid 'case'" {}))
            conformed))

        expr (analyse expr-form (-> ctx (dissoc :loop-locals)))
        clauses (->> clause-forms
                     (into [] (map (fn [{[clause-type clause-arg] :constructor-form, :keys [expr-form]}]
                                     (let [{:keys [binding-syms] :as clause} (case clause-type
                                                                               :constructor-call clause-arg
                                                                               :value-constructor (let [[sym-type sym] clause-arg]
                                                                                                    {sym-type sym}))
                                           locals (map (juxt identity gensym) binding-syms)]
                                       (merge (select-keys clause [:constructor-sym :default-sym])
                                              {:bindings (map second locals)
                                               :expr (analyse expr-form (-> ctx (update :locals into locals)))}))))))

        adt (let [adts (into #{}
                             (comp (keep :constructor-sym)
                                   (map (fn [constructor-sym]
                                          (or (get-in ctx [:env :constructor-syms constructor-sym :adt])
                                              (throw (ex-info "Unknown constructor" {:constructor-sym constructor-sym}))))))
                             clauses)]
              (if (= 1 (count adts))
                (first adts)
                (throw (ex-info "Ambiguous constructors" {:constructor-syms (into #{} (keep :constructor-sym) clauses)
                                                          :adts adts}))))]

    {:expr-type :case
     :adt adt
     :expr expr
     :clauses clauses}))

#_(defmethod analyse-call 'loop [[_loop & forms] ctx]
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

(defmethod analyse-expr :recur [[_ {:keys [forms]}]]
  (let [{:keys [loop-locals]} *ctx*]
    (cond
      (nil? loop-locals)
      (throw (ex-info "'recur' called from non-tail position" {}))

      (not= (count loop-locals) (count forms))
      (throw (ex-info "'recur' called with wrong number of arguments"
                      {:expected (count loop-locals), :found (count forms)}))

      :else {:expr-type :recur
             :exprs (with-ctx-update (dissoc :loop-locals)
                      (mapv analyse-expr forms))
             :loop-locals loop-locals})))

(defmethod analyse-expr :call [[_ {:keys [forms]}]]
  {:expr-type :call
   :exprs (mapv analyse-expr forms)})

#_(defmethod analyse-call 'fn [[_fn & forms] ctx]
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

#_(defmethod analyse-call 'def [[_def & forms] ctx]
  (let [{:keys [params-form body-form]} (let [conformed (s/conform (s/cat :params-form ::def-params, :body-form any?) forms)]

                                          (if (= ::s/invalid conformed)
                                            (throw (ex-info "Invalid def" {:forms forms}))
                                            conformed))

        {:keys [sym-form param-forms]} (case (first params-form)
                                         :just-sym {:sym-form (second params-form)}
                                         :sym+params (merge {:param-forms []} (second params-form)))

        local-mapping (some->> param-forms (map (juxt identity gensym)))]

    {:expr-type :def
     :sym sym-form
     :locals (some->> local-mapping (map second))
     :body-expr (analyse body-form (-> ctx
                                       (update :locals (fnil into {}) local-mapping)
                                       (dissoc :loop-locals)))}))

#_(defmethod analyse-call 'defmacro [[_defmacro & forms] ctx]
  (let [{:keys [params-form body-form]} (let [conformed (s/conform (s/cat :params-form ::def-params, :body-form any?) forms)]

                                          (if (= ::s/invalid conformed)
                                            (throw (ex-info "Invalid defmacro" {:forms forms}))
                                            conformed))

        {:keys [sym-form param-forms]} (case (first params-form)
                                         :just-sym {:sym-form (second params-form)}
                                         :sym+params (merge {:param-forms []} (second params-form)))

        local-mapping (some->> param-forms (map (juxt identity gensym)))]

    {:expr-type :defmacro
     :sym sym-form
     :locals (some->> local-mapping (map second))
     :body-expr (analyse body-form (-> ctx
                                       (update :locals (fnil into {}) local-mapping)
                                       (dissoc :loop-locals)))}))

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
        :adt-or-class (s/and ::symbol-form
                             #(Character/isUpperCase (first (name %))))
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
      :adt-or-class (or (when (get-in env [:adts arg])
                          (tc/->adt arg))

                        (when-let [{:keys [class]} (get-in env [:classes arg])]
                          (tc/->class class))

                        (throw (ex-info "Can't find type" {:type arg})))

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
                                                        :param-type-forms (or (:param-type-forms params-form) [])}
                                         :just-name {:sym params-form})
        return-type (extract-mono-type return-form ctx)]

    {:sym sym
     ::tc/poly-type (tc/mono->poly (if param-type-forms
                                     (tc/fn-type (mapv #(extract-mono-type % ctx) param-type-forms) return-type)
                                     return-type))}))


#_(defmethod analyse-call 'defclj [[_defclj & forms] ctx]
  (let [{:keys [ns-sym type-sig-forms]} (let [conformed (s/conform (s/cat :ns-sym ::symbol-form
                                                                          :type-sig-forms (s/* (s/spec ::type-signature-form)))
                                                                   forms)]
                                          (if (= ::s/invalid conformed)
                                            (throw (ex-info "Invalid defclj" {}))
                                            conformed))

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

    {:expr-type :defclj
     :clj-fns (into #{} (map (fn [{:keys [sym ::tc/poly-type]}]
                               {:ns ns-sym
                                :sym sym
                                :value @(get publics sym)
                                ::tc/poly-type poly-type}))
                    type-sigs)}))

(s/def ::defjava-form
  (s/cat :class-name ::symbol-form
         :type-sig-forms (s/* (s/spec ::type-signature-form))))

(defmethod analyse-expr :defjava [[_ {:keys [class-name type-sig-forms]}]]
  (let [class-basename (symbol (last (str/split (name class-name) #"\.")))
        class (Class/forName (name class-name))]

    ;; TODO there're loads of checks we can make here

    {:expr-type :defjava
     :class class
     :members (with-ctx-update (assoc-in [:env :classes class-basename] {:class class})
                (->> type-sig-forms
                     (into [] (comp (map extract-type-signature)
                                    (map (fn [{:keys [sym ::tc/poly-type]}]
                                           (let [[_ prefix base suffix] (re-matches #"([.\-]+)?(.+?)(!)?" (name sym))]
                                             {:sym (symbol base)
                                              :op (case prefix
                                                    ".-" (if suffix :put-field :get-field)
                                                    "-" (if suffix :put-static :get-static)
                                                    "." :invoke-virtual
                                                    nil :invoke-static)
                                              ::tc/poly-type poly-type})))))))}))

#_(defmethod analyse-call (symbol "::") [[_ & forms] ctx]
  (let [{[subject-type subject-form] :subject, :keys [type-form]} (s/conform (s/cat :subject (s/or :keyword ::keyword-form
                                                                                                   :symbol ::symbol-form)
                                                                                    :type-form ::mono-type-form)
                                                                             forms)]
    (merge {::tc/mono-type (extract-mono-type type-form ctx)}
           (case subject-type
             :keyword {:expr-type :defattribute
                       :attribute subject-form}))))

#_(defmethod analyse-call 'defeffect [[_ & forms] ctx]
  (let [{:keys [sym definitions]} (let [conformed (s/conform (s/cat :sym ::symbol-form
                                                                    :definitions (s/* (s/spec ::type-signature-form)))
                                                             forms)]
                                    (if (= ::s/invalid conformed)
                                      (throw (ex-info "Invalid 'defeffect'"))
                                      conformed))]
    {:expr-type :defeffect
     :sym sym
     :definitions (into [] (comp (map #(extract-type-signature % ctx))
                                 (map #(assoc-in % [::tc/poly-type ::tc/mono-type ::tc/effects] #{sym})))
                        definitions)}))

(s/def ::defadt-form
  (s/cat :name-sym ::symbol-form
         ;; TODO add attrs in here, will make recursive ADTs much easier
         :constructors-forms (s/* (s/or :value-constructor ::symbol-form
                                        :constructor+params (s/spec (s/cat :_list #{:list}
                                                                           :constructor-sym ::symbol-form
                                                                           :params-forms (s/* ::mono-type-form)))))))

(defmethod analyse-expr :defadt [[_ {:keys [name-sym constructors-forms]}]]
  {:expr-type :defadt
   :sym name-sym
   :constructors (->> constructors-forms
                      (into [] (map (fn [[c-type c-args]]
                                      (case c-type
                                        :value-constructor {:constructor-sym c-args}
                                        :constructor+params {:constructor-sym (:constructor-sym c-args)
                                                             :param-mono-types
                                                             (->> (:params-forms c-args)
                                                                  (into [] (map (fn [form]
                                                                                  (with-ctx-update (assoc-in [:env :adts name-sym] {})

                                                                                    (extract-mono-type form))))))})))))})

(def ->form-type-kw
  (-> (fn [sym]
        (when sym
          (->> (name sym)
               (re-seq #"[A-Z][a-z]*" )
               butlast
               (map str/lower-case)
               (str/join "-")
               keyword)))
      memoize))

(defn form-adt->form [{:brj/keys [constructor constructor-params]}]
  (let [form-type (->form-type-kw constructor)]
    (case form-type
      (:vector :list :set :record)
      (into [form-type] (map form-adt->form) (get-in constructor-params [0]))

      [form-type (first constructor-params)])))

(def ->form-adt-sym
  (-> (fn [form-type]
        (let [base (->> (str/split (name form-type) #"-")
                        (map str/capitalize)
                        str/join)]
          (symbol (str base "Form"))))
      memoize))

(defn form->form-adt [[form-type & [first-form :as forms] :as form]]
  (case form-type
    {:brj/constructor (->form-adt-sym form-type)
     :brj/constructor-params (case form-type
                               (:vector :list :set :record) [(mapv form->form-adt forms)]
                               [first-form])}))



(defn call-conformer [{:keys [forms]}]
  (letfn [(fall-through [forms]
            (let [conformed-forms (s/conform (s/* ::form) forms)]
              (if (= ::s/invalid conformed-forms)
                (do
                  (s/explain (s/* ::form) forms)
                  ::s/invalid)
                {:forms conformed-forms})))]

    (if (= :symbol (get-in forms [0 0]))
      (if-let [{eval-macro :value} (get-in *ctx* [:env :macros (get-in forms [0 1])])]
        ;; TODO arity check
        (recur {:forms (->> (rest forms)
                            (into [] (map form->form-adt))
                            (apply eval-macro)
                            (into [] (map form-adt->form)))})

        (fall-through forms))

      (fall-through forms))))

(s/def ::form
  (s/or :bool (s/cat :_bool #{:bool}, :bool boolean?)
        :string (s/cat :_string #{:string}, :string string?)
        :number (s/cat :num-type #{:int :float :big-int :big-float}, :number number?)
        :keyword (s/cat :_keyword #{:keyword}, :kw keyword?)
        ;; :quote (s/cat :_quote {:quote}, :form (s/spec ::form))

        :symbol (s/cat :_symbol #{:symbol}, :sym symbol?)
        :coll (s/cat :coll-type #{:vector :set}, :forms (s/* (s/spec ::form)))
        :record ::record-form

        :recur (s/cat :forms (s/* (s/spec ::form)))

        :if ::if-form

        :defadt ::defadt-form
        :defjava ::defjava-form

        :call (s/and (s/cat :_list #{:list}
                            :forms (s/* any?))
                     (s/conformer call-conformer))))

(defn analyse [form ctx]
  (binding [*ctx* ctx]
    (let [conformed (s/conform ::form form)]
      (if-not (= ::s/invalid conformed)
        (analyse-expr conformed)
        (throw (ex-info "Invalid form" {:form form, :explain (s/explain-data ::form form)}))))))

(analyse [:record
          [:keyword :foo] [:keyword :foo]
          [:keyword :bar] [:int 6]]
         {:env {:attributes {:foo {}}}})
