(ns bridje.analyser
  (:require [bridje.util :as u]
            [bridje.type-checker :as tc]
            [clojure.spec.alpha :as s]
            [clojure.walk :as w]
            [clojure.string :as str]
            [clojure.set :as set]))

(defn ^:dynamic gen-local [sym]
  (gensym sym))

(s/def ::symbol-form
  (s/and (s/cat :_sym #{:symbol}
                :sym symbol?)
         (s/conformer #(:sym %))))

(defn exact-sym [sym]
  (s/and ::symbol-form #{sym}))

(s/def ::keyword-form
  (s/and (s/cat :_kw #{:keyword}
                :kw keyword?)
         (s/conformer #(:kw %))))

(def ^:dynamic ^:private *ctx* {})

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

(defn extract-mono-type [mono-type-form]
  (let [{:keys [env] :as ctx} *ctx*
        [mono-type-type arg] mono-type-form]
    (case mono-type-type
      :primitive (tc/primitive-type arg)
      :vector (tc/vector-of (extract-mono-type arg))
      :set (tc/set-of (extract-mono-type arg))
      :record (tc/record-of (gensym 'r) arg)
      :type-var (tc/->type-var (tc/new-type-var arg))
      :adt-or-class (or (when (get-in env [:adts arg])
                          (tc/->adt arg))

                        (when-let [{:keys [class]} (get-in env [:classes arg])]
                          (tc/->class class))

                        (throw (ex-info "Can't find type" {:type arg})))

      :applied (tc/->adt (get-in arg [:constructor-sym])
                         (mapv extract-mono-type (:param-forms arg))))))

(s/def ::type-signature-form
  (s/cat :_list #{:list}
         :_colon (exact-sym (symbol "::"))
         :params-form (s/or :fn-shorthand (s/spec (s/cat :_list #{:list}
                                                         :name-sym ::symbol-form
                                                         :param-type-forms (s/* ::mono-type-form)))
                            :just-name ::symbol-form)
         :return-form ::mono-type-form))

(defn extract-type-signature [type-signature-form]
  (binding [tc/new-type-var (memoize tc/new-type-var)]
    (let [{[param-form-type params-form] :params-form
           :keys [return-form]} type-signature-form
          {:keys [sym param-type-forms]} (case param-form-type
                                           :fn-shorthand {:sym (get-in params-form [:name-sym])
                                                          :param-type-forms (or (:param-type-forms params-form) [])}
                                           :just-name {:sym params-form})
          return-type (extract-mono-type return-form)]

      {:sym sym
       ::tc/poly-type (tc/mono->poly (if param-type-forms
                                       (tc/fn-type #{} (mapv extract-mono-type param-type-forms) return-type)
                                       return-type))})))

(defmacro with-ctx-update [update-form & body]
  `(binding [*ctx* (-> *ctx* ~update-form)]
     ~@body))

(defmulti analyse-expr
  (fn [[form-type params]]
    form-type)
  :default ::default)

(defmethod analyse-expr ::default [form]
  (throw (ex-info "ohno" {:form form})))

(defmethod analyse-expr :bool [[_ {:keys [bool]}]]
  {:expr-type :bool, :bool bool})

(defmethod analyse-expr :string [[_ {:keys [string] :as expr}]]
  {:expr-type :string, :string string})

(defmethod analyse-expr :number [[_ {:keys [num-type number]}]]
  {:expr-type num-type, :number number})

(defmethod analyse-expr :symbol [[_ sym]]
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

(s/def ::quote-form
  (s/cat :_list #{:list}
         :_quote (exact-sym 'quote),
         :sym ::symbol-form))

(defmethod analyse-expr :quote [[_ {:keys [sym]}]]
  {:expr-type :symbol
   :sym sym})

(s/def ::if-form
  (s/cat :_list #{:list}
         :_if (s/and ::symbol-form #{'if})
         :pred-form ::form
         :then-form ::form
         :else-form ::form))

(defmethod analyse-expr :if [[_ {:keys [pred-form then-form else-form]}]]
  {:expr-type :if
   :pred-expr (with-ctx-update (dissoc :loop-locals)
                (analyse-expr pred-form))
   :then-expr (analyse-expr then-form)
   :else-expr (analyse-expr else-form)})

(s/def ::bindings-form
  (s/and (s/cat :_list #{:list}
                :bindings (s/* (s/spec (s/cat :_list #{:list}
                                              :binding-sym ::symbol-form
                                              :binding-form ::form))))
         (s/conformer #(:bindings %))))

(s/def ::let-form
  (s/cat :_list #{:list}
         :_let (exact-sym 'let)
         :bindings-form (s/spec ::bindings-form)
         :body-form ::form))

(defmethod analyse-expr :let [[_ {:keys [bindings-form body-form]}]]
  (let [{:keys [bindings locals]} (reduce (fn [{:keys [bindings locals]} {:keys [binding-sym binding-form]}]
                                            (let [local (gen-local binding-sym)]
                                              {:bindings (conj bindings [local (with-ctx-update (-> (update :locals (fnil into {}) locals)
                                                                                                    (dissoc :loop-locals))
                                                                                 (analyse-expr binding-form))])
                                               :locals (assoc locals binding-sym local)}))
                                          {:bindings []
                                           :locals {}}
                                          bindings-form)]
    {:expr-type :let
     :bindings bindings
     :body-expr (with-ctx-update (update :locals (fnil into {}) locals)
                  (analyse-expr body-form))}))

(s/def ::case-form
  (s/cat :_list #{:list}
         :_case (exact-sym 'case)
         :expr-form ::form
         :clause-forms
         (s/* (s/cat :constructor-form
                     (s/or :constructor-call
                           (s/spec (s/cat :_list #{:list}
                                          :constructor-sym ::symbol-form
                                          :binding-syms (s/* ::symbol-form)))
                           :value-constructor (s/or :default-sym ::type-var-sym-form
                                                    :constructor-sym ::symbol-form))
                     :expr-form ::form))))

(defmethod analyse-expr :case [[_ {:keys [expr-form clause-forms]}]]
  (let [expr (with-ctx-update (dissoc :loop-locals)
               (analyse-expr expr-form))

        clauses (->> clause-forms
                     (into [] (map (fn [{[clause-type clause-arg] :constructor-form, :keys [expr-form]}]
                                     (let [{:keys [binding-syms] :as clause} (case clause-type
                                                                               :constructor-call clause-arg
                                                                               :value-constructor (let [[sym-type sym] clause-arg]
                                                                                                    {sym-type sym}))
                                           locals (map (juxt identity gen-local) binding-syms)]
                                       (merge (select-keys clause [:constructor-sym :default-sym])
                                              {:locals (map second locals)
                                               :expr (with-ctx-update (update :locals into locals)
                                                       (analyse-expr expr-form))}))))))

        adts (into #{} (map (comp :adt (get-in *ctx* [:env :adt-constructors]) :constructor-sym)) clauses)]

    (when-not (= 1 (count adts))
      (throw (ex-info "Ambiguous ADTs" {:adts adts})))

    {:expr-type :case
     :expr expr
     :adt (first adts)
     :clauses clauses}))

(s/def ::loop-form
  (s/cat :_list #{:list}
         :_loop (exact-sym 'loop)
         :bindings-form ::bindings-form
         :body-form ::form))

(defmethod analyse-expr :loop [[_ {:keys [bindings-form body-form]}]]
  (let [bindings (for [{:keys [binding-sym binding-form]} bindings-form]
                   (let [local (gen-local binding-sym)]
                     {:sym binding-sym
                      :local local
                      :expr (with-ctx-update (dissoc :loop-locals)
                              (analyse-expr binding-form))}))]
    {:expr-type :loop
     :bindings (map (juxt :local :expr) bindings)
     :body-expr (with-ctx-update (-> (update :locals (fnil into {}) (map (juxt :sym :local)) bindings)
                                     (assoc :loop-locals (map :local bindings)))
                  (analyse-expr body-form))}))

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

(s/def ::handling-form
  (s/cat :_list #{:list}
         :_handling (exact-sym 'handling)
         :handler-forms (s/spec (s/and (s/cat :_list #{:list}
                                              :effects (s/* (s/spec (s/cat :_list #{:list}
                                                                           :effect-sym ::symbol-form
                                                                           :handler-fns (s/* ::form)))))
                                       (s/conformer #(:effects %))))
         :body-form ::form))

(defmethod analyse-expr :handling [[_ {:keys [handler-forms body-form]}]]
  {:expr-type :handling
   :handlers (->> handler-forms
                  (into [] (map (fn [{:keys [effect-sym handler-fns]}]
                                  (if-let [effect-fns (get-in *ctx* [:env :effects effect-sym])]
                                    ;; TODO checks around the function names, etc
                                    {:effect effect-sym
                                     :handler-exprs (into [] (map analyse-expr) handler-fns)}

                                    (throw (ex-info "Can't find effect" {:effect effect-sym})))))))
   :body-expr (analyse-expr body-form)})

(defmethod analyse-expr :call [[_ {:keys [forms]}]]
  {:expr-type :call
   :exprs (mapv analyse-expr forms)})

(s/def ::fn-form
  (s/cat :_list #{:list}
         :_fn (exact-sym 'fn)
         :params-form (s/spec (s/cat :_list #{:list}
                                     :sym-form ::symbol-form
                                     :param-forms (s/* ::symbol-form))),
         :body-form ::form))

(defmethod analyse-expr :fn [[_ {:keys [params-form body-form]}]]
  (let [{:keys [sym-form param-forms]} params-form
        local-mapping (->> param-forms (map (juxt identity gen-local)))]

    {:expr-type :fn
     :sym sym-form
     :locals (map second local-mapping)
     :body-expr (with-ctx-update (-> (update :locals (fnil into {}) local-mapping)
                                     (dissoc :loop-locals))
                  (analyse-expr body-form))}))

(s/def ::typedef-form
  (s/and (s/cat :_list #{:list}
                :_typedef (exact-sym (symbol "::"))
                :params-form (s/and (s/or :just-name ::symbol-form
                                          :sym+params (s/spec (s/cat :_list #{:list}
                                                                     :sym-form ::symbol-form
                                                                     :param-forms (s/* ::mono-type-form))))
                                    (s/conformer (fn [[alt alt-opts]]
                                                   (case alt
                                                     :just-name {:sym-form alt-opts}
                                                     :sym+params (merge {:param-forms []} alt-opts)))))
                :return-form ::mono-type-form)
         (s/conformer (fn [form]
                        (merge (dissoc form :params-form)
                               (:params-form form))))))

(defmethod analyse-expr :typedef [[_ {:keys [sym-form param-forms return-form]}]]
  {:expr-type :typedef
   :sym sym-form
   ::tc/mono-type (if param-forms
                    (tc/fn-type #{} (mapv extract-mono-type param-forms) (extract-mono-type return-form))
                    (extract-mono-type return-form))})

(s/def ::def-params
  (s/and (s/or :just-sym ::symbol-form
               :sym+params (s/spec (s/cat :_list #{:list}
                                          :sym-form ::symbol-form
                                          :param-forms (s/* ::symbol-form))))

         (s/conformer (fn [[alt alt-opts]]
                        (case alt
                          :just-sym {:sym-form alt-opts}
                          :sym+params (merge {:param-forms []} alt-opts))))))

(s/def ::def-form
  (s/and (s/cat :_list #{:list}
                :_def (s/and ::symbol-form #{'def})
                :params-form ::def-params,
                :body-form ::form)

         (s/conformer (fn [form]
                        (merge (dissoc form :params-form)
                               (:params-form form))))))

(defmethod analyse-expr :def [[_ {:keys [sym-form param-forms body-form] :as form}]]
  (let [local-mapping (some->> param-forms (map (juxt identity gen-local)))]

    {:expr-type :def
     :sym sym-form
     :locals (some->> local-mapping (map second))
     :body-expr (with-ctx-update (-> (update :locals (fnil into {}) local-mapping)
                                     (dissoc :loop-locals))
                  (analyse-expr body-form))}))

(s/def ::defmacro-form
  (s/and (s/cat :_list #{:list}
                :_defmacro (exact-sym 'defmacro)
                :params-form ::def-params,
                :body-form ::form)
         (s/conformer (fn [form]
                        (merge (dissoc form :param-forms)
                               (:params-form form))))))

(defmethod analyse-expr :defmacro [[_ {:keys [sym-form param-forms body-form]}]]
  (let [local-mapping (some->> param-forms (map (juxt identity gen-local)))]

    {:expr-type :defmacro
     :sym sym-form
     :locals (some->> local-mapping (map second))
     :body-expr (with-ctx-update (-> (update :locals (fnil into {}) local-mapping)
                                     (dissoc :loop-locals))
                  (analyse-expr body-form ))}))

(s/def ::defclj-form
  (s/cat :_list #{:list}
         :_defclj (exact-sym 'defclj)
         :ns-sym ::symbol-form
         :type-sig-forms (s/* (s/spec ::type-signature-form))))

(defmethod analyse-expr :defclj [[_ {:keys [ns-sym type-sig-forms]}]]
  (let [type-sigs (map extract-type-signature type-sig-forms)
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
  (s/cat :_list #{:list}
         :_defjava (s/and ::symbol-form #{'defjava})
         :class-name ::symbol-form
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

(s/def ::attribute-typedef-form
  (s/cat :_list #{:list}
         :_typedef (exact-sym (symbol "::"))
         :keyword-form ::keyword-form
         :type-form ::mono-type-form))

(defmethod analyse-expr :attribute-typedef [[_ {:keys [keyword-form type-form]}]]
  (merge {::tc/mono-type (extract-mono-type type-form)}
         {:expr-type :attribute-typedef
          :attribute keyword-form}))

(s/def ::defeffect-form
  (s/cat :_list #{:list}
         :_defeffect (exact-sym 'defeffect)
         :sym ::symbol-form
         :definitions (s/* (s/spec ::type-signature-form))))

(defmethod analyse-expr :defeffect [[_ {:keys [sym definitions]}]]
  {:expr-type :defeffect
   :sym sym
   :definitions (into [] (comp (map extract-type-signature)
                               (map #(assoc-in % [::tc/poly-type ::tc/mono-type ::tc/effects] #{sym})))
                      definitions)})

(s/def ::defadt-form
  (s/and (s/cat :_list #{:list}
          :_defadt (exact-sym 'defadt)
          :name-form (s/and (s/or :just-name ::symbol-form
                                  :name+type-vars (s/spec (s/cat :_list #{:list}
                                                                 :name-sym ::symbol-form
                                                                 :type-var-syms (s/+ ::type-var-sym-form))))
                            (s/conformer (fn [[decl-type decl-args]]
                                           (case decl-type
                                             :just-name {:name-sym decl-args}
                                             :name+type-vars decl-args))))

          ;; TODO add attrs in here, will make recursive ADTs much easier
          :constructors-forms (s/* (s/or :value-constructor ::symbol-form
                                         :constructor+params (s/spec (s/cat :_list #{:list}
                                                                            :constructor-sym ::symbol-form
                                                                            :params-forms (s/* ::mono-type-form))))))
         (s/conformer (fn [form]
                        (-> (merge form (:name-form form))
                            (dissoc :name-form))))))

(defmethod analyse-expr :defadt [[_ {:keys [name-sym type-var-syms constructors-forms]}]]
  (binding [tc/new-type-var (memoize tc/new-type-var)]
    {:expr-type :defadt
     :sym name-sym
     ::tc/type-vars (into [] (map tc/new-type-var) type-var-syms)
     :constructors (->> constructors-forms
                        (into [] (map (fn [[c-type c-args]]
                                        (case c-type
                                          :value-constructor {:constructor-sym c-args}
                                          :constructor+params {:constructor-sym (:constructor-sym c-args)
                                                               :param-mono-types
                                                               (->> (:params-forms c-args)
                                                                    (into [] (map (fn [form]
                                                                                    (with-ctx-update (-> (assoc-in [:env :adts name-sym] {}))

                                                                                      (extract-mono-type form))))))})))))}))

(def ->form-type-kw
  (-> (fn [^Class class]
        (when class
          (-> (.getName class)
              (str/split #"\.")
              last
              (->> (re-seq #"[A-Z][a-z]*" )
                   butlast
                   (map str/lower-case)
                   (str/join "-")
                   keyword))))
      memoize))

(defn form-adt->form [form-adt]
  (let [form-type (->form-type-kw (class form-adt))]
    (case form-type
      (:vector :list :set :record)
      (into [form-type] (map form-adt->form) (:field0 form-adt))

      [form-type (:field0 form-adt)])))

(def ->form-adt-sym
  (-> (fn [form-type]
        (let [base (->> (str/split (name form-type) #"-")
                        (map str/capitalize)
                        str/join)]
          (symbol (str base "Form"))))
      memoize))

(defn form->form-adt [[form-type & [first-form :as forms] :as form]]
  (let [constructor (get-in *ctx* [:env :vars (->form-adt-sym form-type) :value])]
    (apply constructor (case form-type
                         (:vector :list :set :record) [(mapv form->form-adt forms)]
                         [first-form]))))

(defn call-conformer [{:keys [forms]}]
  (letfn [(fall-through [conformed-forms]
            (if (= ::s/invalid conformed-forms)
              (do
                (s/explain (s/* ::form) forms)
                ::s/invalid)
              {:forms conformed-forms}))]

    (if (= :symbol (get-in forms [0 0]))
      (if-let [{eval-macro :value} (get-in *ctx* [:env :macros (get-in forms [0 1])])]
        ;; TODO arity check
        (let [form (->> (rest forms)
                        (into [] (map form->form-adt))
                        (apply eval-macro)
                        form-adt->form)
              [[form-type params] :as conformed] (s/conform (s/* ::form) [form])]
          (if (= :call form-type)
            (recur params)
            ;; have to do this so spec doesn't return this as a call
            (-> (fall-through conformed)
                (vary-meta assoc ::unwrap-call? true))))

        (fall-through (s/conform (s/* ::form) forms)))

      (fall-through (s/conform (s/* ::form) forms)))))

(s/def ::form
  (s/and (s/or :bool (s/cat :_bool #{:bool}, :bool boolean?)
               :string (s/cat :_string #{:string}, :string string?)
               :number (s/cat :num-type #{:int :float :big-int :big-float}, :number number?)
               :keyword (s/cat :_keyword #{:keyword}, :kw keyword?)

               :symbol ::symbol-form
               :coll (s/cat :coll-type #{:vector :set}, :forms (s/* ::form))
               :record (s/cat :_record #{:record}, :entries (s/* (s/cat :k ::keyword-form, :v ::form)))

               :loop ::loop-form
               :recur (s/cat :_list #{:list}, :_recur (s/and ::symbol-form #{'recur}), :forms (s/* ::form))

               :quote ::quote-form

               :if ::if-form
               :let ::let-form
               :case ::case-form

               :fn ::fn-form

               :handling ::handling-form

               :typedef ::typedef-form
               :def ::def-form
               :defmacro ::defmacro-form
               :defadt ::defadt-form
               :defjava ::defjava-form
               :defclj ::defclj-form
               :defeffect ::defeffect-form
               :attribute-typedef ::attribute-typedef-form

               :call (s/and (s/cat :_list #{:list}
                                   :forms (s/* any?))
                            (s/conformer call-conformer)))

         (s/conformer (fn [form]
                        (cond-> form
                          (::unwrap-call? (meta (second form))) (get-in [1 :forms 0]))))))

(defn analyse [form ctx]
  (binding [*ctx* ctx]
    (let [conformed (s/conform ::form form)]
      (if-not (= ::s/invalid conformed)
        (analyse-expr conformed)
        (throw (ex-info "Invalid form" {:form form, :explain (comment (s/explain-data ::form form))}))))))
