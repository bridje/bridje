(ns bridje.analyser
  (:require [bridje.util :as u]
            [clojure.string :as s]))

(defn parse-forms [forms parser]
  (first (parser forms)))

(defn pure [v]
  (fn [forms]
    [v forms]))

(defmacro do-parse {:style/indent 1} [bindings & body]
  (if-let [[binding value & more-bindings] (seq bindings)]
    `(fn [forms#]
       (let [[~binding more-forms#] (~value forms#)]
         ((do-parse ~more-bindings ~@body) more-forms#)))

    `(do ~@body)))

(defn or-parser [& parsers]
  (fn [forms]
    (loop [[parser & more-parsers] parsers
           errors []]
      (if parser
        (let [{:keys [result error]} (try
                                       {:result (parser forms)}
                                       (catch Exception e
                                         {:error e}))]
          (or result
              (recur more-parsers (conj errors error))))
        (throw (ex-info "No matching parser" {:errors errors}))))))

(defn maybe-many [parser]
  (fn [forms]
    (loop [results []
           forms forms]
      (let [[result more-forms] (when (seq forms)
                                  (parser forms))]
        (if (not= more-forms forms)
          (recur (conj results result) more-forms)
          [results forms])))))

(defn at-least-one [parser]
  (do-parse [results (maybe-many parser)]
    (if (seq results)
      (pure results)
      (throw (ex-info "TODO: expected at-least-one" {})))))

(defn nested-parser [forms parser]
  (fn [outer-forms]
    [(parser forms) outer-forms]))

(defn first-form-parser [p]
  (fn [forms]
    (if-let [[form & more-forms] (seq forms)]
      [(p form) more-forms]
      (throw (ex-info "TODO: expected form" {})))))

(defn form-type-parser [expected-form-type]
  (first-form-parser (fn [{:keys [form-type loc-range] :as form}]
                       (if (= expected-form-type form-type)
                         form
                         (throw (ex-info "Unexpected form type" {:expected expected-form-type
                                                                 :actual form-type
                                                                 :loc-range loc-range}))))))

(def sym-parser
  (form-type-parser :symbol))

(def ns-sym-parser
  (form-type-parser :namespaced-symbol))

(defn coll-parser [form-type nested-parser]
  (do-parse [{:keys [forms]} (form-type-parser form-type)]
    (pure (first (nested-parser forms)))))

(defn list-parser [nested-parser]
  (coll-parser :list nested-parser))

(defn set-parser [nested-parser]
  (coll-parser :set nested-parser))

(defn vector-parser [nested-parser]
  (coll-parser :vector nested-parser))

(defn record-parser [nested-parser]
  (do-parse [{:keys [loc-range forms]} (form-type-parser :record)]
    (cond
      (pos? (mod (count forms) 2)) (throw (ex-info "Record requires even number of forms" {:loc-range loc-range}))
      :else (pure (first (nested-parser (for [[{:keys [form-type loc-range] :as k-form} v-form] (partition 2 forms)]
                                          (cond
                                            (not= :symbol form-type) (throw (ex-info "Expected symbol as key in record" {:loc-range loc-range}))
                                            (some? (:ns k-form)) (throw (ex-info "Unexpected namespaced symbol as key in record" {:loc-range loc-range}))
                                            :else [(:sym k-form) v-form]))))))))

(defn when-more-forms [parser]
  (fn [forms]
    (if (seq forms)
      (parser forms)
      [nil []])))

(defn no-more-forms [value]
  (fn [forms]
    (if (seq forms)
      (throw (ex-info "Unexpected form" {:form (first forms)}))
      [value []])))

(defn with-ensure-even-forms [parser parent-form]
  (fn [forms]
    (if (zero? (mod (count forms) 2))
      (parser forms)
      (throw (ex-info "Expected even number of forms" {:form parent-form})))))

(def aliases-parser
  (record-parser
   (do-parse [entries (maybe-many
                       (first-form-parser
                        (fn [[alias-sym form]]
                          (parse-forms [form]
                                       (do-parse [{aliased-sym :sym} sym-parser]
                                         (pure [alias-sym aliased-sym]))))))]
     (no-more-forms (into {} entries)))))

(def refers-parser
  (record-parser
   (do-parse [entries (maybe-many
                       (first-form-parser
                        (fn [[refer-ns form]]
                          (parse-forms [form]
                                       (do-parse [referred-sym-forms (set-parser (maybe-many sym-parser))]
                                         (pure [refer-ns (into #{} (map :sym referred-sym-forms))]))))))]
     (no-more-forms (into {} entries)))))

(defn ns-entries-parser [entries]
  [(reduce (fn [acc [sym form]]
             (let [[kw parser] (case sym
                                 aliases [:aliases aliases-parser]
                                 refers [:refers refers-parser])]
               (assoc acc kw (parse-forms [form] parser))))
           {}
           entries)
   nil])

(defn analyse-ns-form [{:keys [form-type forms loc-range] :as form}]
  (if-not (and (= :list form-type)
               (= {:form-type :symbol, :sym 'ns}
                  (-> (first forms) (select-keys [:form-type :sym :ns]))))
    (throw (ex-info "Expecting NS form" {:form form}))

    (parse-forms (rest forms)
                 (do-parse [{ns-sym :sym} sym-parser
                            {:keys [refers aliases]} (when-more-forms (record-parser ns-entries-parser))]
                   (no-more-forms {:ns ns-sym
                                   :refers refers
                                   :aliases aliases})))))

(defn env-resolve [{:keys [ns sym]} resolve-type {:keys [global-env current-ns]}]
  (if ns
    (or (when-let [alias-ns (get-in global-env [current-ns :aliases ns])]
          (when (get-in global-env [alias-ns resolve-type sym])
            (symbol (name alias-ns) (name sym))))

        (when (contains? global-env ns)
          (when (get-in global-env [ns resolve-type sym])
            (symbol (name ns) (name sym)))))

    (or (when (get-in global-env [current-ns resolve-type sym])
          (symbol (name current-ns) (name sym)))

        (when-let [refer-ns (get-in global-env [current-ns :refers sym])]
          (symbol (name refer-ns) (name sym))))))

(defn analyse [{:keys [form-type forms loc-range] :as form} {:keys [global-env locals loop-locals current-ns] :as env}]
  (merge {:loc-range loc-range}
         (case form-type
           :string {:expr-type :string, :string (:string form)}
           :bool {:expr-type :bool, :bool (:bool form)}
           (:int :float :big-int :big-float) {:expr-type form-type, :number (:number form)}
           :vector {:expr-type :vector, :exprs (map #(analyse % env) forms)}
           :set {:expr-type :set, :exprs (map #(analyse % env) forms)}

           :record (parse-forms [form]
                                (record-parser (do-parse [entries (maybe-many (first-form-parser (fn [[sym form]]
                                                                                                   [sym (analyse form env)])))]
                                                 (no-more-forms {:expr-type :record
                                                                 :entries entries}))))

           :quote {:expr-type :quote, :form (:form form)}

           :list
           (if (seq forms)
             (let [[first-form & more-forms] forms
                   expr-parser (fn expr-parser
                                 ([] (expr-parser env))
                                 ([env]
                                  (first-form-parser (fn [form]
                                                       (analyse form env)))))

                   bindings-parser (vector-parser (-> (fn [forms]
                                                        [(reduce (fn [{:keys [bindings locals]} pair]
                                                                   (let [[sym expr] (parse-forms pair
                                                                                                 (do-parse [{:keys [sym]} sym-parser
                                                                                                            expr (expr-parser (-> env
                                                                                                                                  (update :locals (fnil into {}) locals)
                                                                                                                                  (dissoc :loop-locals)))]
                                                                                                   (pure [sym expr])))
                                                                         local (gensym sym)]
                                                                     {:bindings (conj bindings [local expr])
                                                                      :locals (assoc locals sym local)}))
                                                                 {:bindings []
                                                                  :locals {}}
                                                                 (partition 2 forms))
                                                         []])
                                                      (with-ensure-even-forms form)))]

               (or (case (:form-type first-form)
                     :symbol
                     (or (case (keyword (:sym first-form))
                           :if (parse-forms more-forms
                                            (do-parse [pred-expr (expr-parser (dissoc env :loop-locals))
                                                       then-expr (expr-parser)
                                                       else-expr (expr-parser)]
                                              (no-more-forms {:expr-type :if
                                                              :pred-expr pred-expr
                                                              :then-expr then-expr
                                                              :else-expr else-expr})))

                           :let (parse-forms more-forms
                                             (do-parse [{:keys [bindings locals]} bindings-parser
                                                        body-expr (expr-parser (-> env
                                                                                   (update :locals (fnil into {}) locals)))]
                                               (no-more-forms {:expr-type :let
                                                               :bindings bindings
                                                               :body-expr body-expr})))

                           :fn (parse-forms more-forms
                                            (do-parse [params (vector-parser (do-parse [params (at-least-one sym-parser)]
                                                                               (no-more-forms (map (comp (juxt identity gensym) :sym) params))))
                                                       body-expr (expr-parser (-> env
                                                                                  (update :locals (fnil into {}) params)
                                                                                  (assoc :loop-locals (map second params))))]
                                              (no-more-forms {:expr-type :fn
                                                              :locals (map second params)
                                                              :body-expr body-expr})))

                           :def (parse-forms more-forms
                                             (do-parse [{:keys [sym params]} (or-parser sym-parser
                                                                                        (list-parser (do-parse [{:keys [sym]} sym-parser
                                                                                                                params (at-least-one sym-parser)]
                                                                                                       (no-more-forms {:sym sym
                                                                                                                       :params (map (comp (juxt identity gensym) :sym) params)}))))
                                                        body-expr (expr-parser (-> env
                                                                                   (update :locals (fnil into {}) params)
                                                                                   (assoc :loop-locals (map second params))))]
                                               (no-more-forms {:expr-type :def
                                                               :sym sym
                                                               :locals (map second params)
                                                               :body-expr body-expr})))

                           :defmacro (throw (ex-info "niy" {}))

                           :defdata (parse-forms more-forms
                                                 (do-parse [{:keys [sym params]} (or-parser sym-parser
                                                                                            (list-parser (do-parse [{:keys [sym]} sym-parser
                                                                                                                    params (at-least-one sym-parser)]
                                                                                                           (no-more-forms {:sym sym
                                                                                                                           :params (map :sym params)}))))]
                                                   (no-more-forms {:expr-type :defdata
                                                                   :sym sym
                                                                   :params params})))

                           :match (parse-forms more-forms
                                               (do-parse [match-expr (expr-parser (dissoc env :loop-locals))]
                                                 (fn [forms]
                                                   (cond
                                                     (zero? (mod (count forms) 2)) (throw (ex-info "Missing default in 'match'" {:loc-range (:loc-range form)}))
                                                     :else (let [clauses (parse-forms (butlast forms)
                                                                                      (do-parse [clauses (maybe-many (do-parse [sym (or-parser sym-parser ns-sym-parser)
                                                                                                                                expr (expr-parser)]
                                                                                                                       (if-let [fq-sym (env-resolve sym :types env)]
                                                                                                                         (pure [fq-sym expr])
                                                                                                                         (throw (ex-info "Can't resolve type:"
                                                                                                                                         {:type (select-keys sym [:ns :sym])
                                                                                                                                          :loc-range (:loc-range sym)})))))]
                                                                                        (no-more-forms clauses)))
                                                                 default-expr (analyse (last forms) env)]

                                                             [{:expr-type :match
                                                               :match-expr match-expr
                                                               :clauses clauses
                                                               :default-expr default-expr}
                                                              []])))))

                           :loop (parse-forms more-forms
                                              (do-parse [{:keys [bindings locals]} bindings-parser
                                                         body-expr (expr-parser (-> env
                                                                                    (update :locals (fnil into {}) locals)
                                                                                    (assoc :loop-locals (map second locals))))]
                                                (no-more-forms {:expr-type :loop
                                                                :bindings bindings
                                                                :body-expr body-expr})))

                           :recur (cond
                                    (nil? loop-locals) (throw (ex-info "'recur' called from non-tail position"
                                                                       {:loc-range loc-range}))
                                    (not= (count loop-locals) (count more-forms)) (throw (ex-info "'recur' called with wrong number of arguments"
                                                                                                  {:loc-range loc-range
                                                                                                   :expected (count loop-locals)
                                                                                                   :found (count more-forms)}))
                                    :else (parse-forms more-forms
                                                       (do-parse [exprs (maybe-many (expr-parser (dissoc env :loop-locals)))]
                                                         (no-more-forms {:expr-type :recur
                                                                         :exprs exprs
                                                                         :loop-locals loop-locals}))))

                           :clj (parse-forms more-forms
                                             (do-parse [{:keys [ns sym]} (or sym-parser ns-sym-parser)]
                                               (no-more-forms {:expr-type :clj-var
                                                               :clj-var (symbol (or (some-> ns name)
                                                                                    (name 'clojure.core))
                                                                                (name sym))})))

                           ;; fall through to 'call'
                           nil))

                     ;; fall through to 'call'
                     (:list :namespaced-symbol) nil)

                   {:expr-type :call
                    :exprs (map #(analyse % env) forms)}))

             (throw (ex-info "niy" {})))

           :symbol (or (when-let [local (get locals (:sym form))]
                         {:expr-type :local
                          :local local})

                       (when-let [global (env-resolve form :vars env)]
                         {:expr-type :global
                          :global global})

                       (throw (ex-info "Can't find" {:sym (:sym form)
                                                     :env env})))

           :namespaced-symbol (if-let [global (env-resolve form :vars env)]
                                {:expr-type :global
                                 :global global}

                                (throw (ex-info "Can't find" {:ns (:ns form)
                                                              :sym (:sym form)
                                                              :env env}))))))

(defn syntax-quote-form [{:keys [form-type forms] :as form} {:keys [env splice?]}]
  (letfn [(sym-form [sym]
            {:form-type :symbol
             :sym (name sym)})]

    (let [quoted-form (case form-type
                        :syntax-quote (-> (:form form)
                                          (syntax-quote-form {:env env})
                                          (syntax-quote-form {:env env}))

                        :unquote (:form form)

                        :unquote-splicing (if splice?
                                            (:form form)
                                            (throw (ex-info "unquote-splicing used outside of collection" {:form form})))

                        :quote {:form-type :quote
                                :form (syntax-quote-form (:form form) {:env env})}

                        {:form-type :list
                         :forms [(let [sym (symbol (str "->" (name (u/form-adt-kw form-type))))]
                                   {:form-type :namespaced-symbol
                                    :ns (name 'bridje.forms)
                                    :sym (name sym)})
                                 {:form-type :record
                                  :forms (case form-type
                                           :string [(sym-form 'string) form]
                                           :bool [(sym-form 'bool) form]

                                           (:int :float :big-int :big-float) [(sym-form 'number) form]

                                           ;; TODO should we make 'symbol' a type in the kernel lang?
                                           :symbol [(sym-form 'sym) {:form-type :string, :string (name (:sym form))}]
                                           :namespaced-symbol [(sym-form 'ns) {:form-type :string, :string (name (:ns form))}
                                                               (sym-form 'sym) {:form-type :string, :string (name (:sym form))}]

                                           (:list :vector :set :record) [(sym-form 'forms) (let [splice? (some #(= :unquote-splicing (:form-type %)) forms)
                                                                                                 inner-forms {:form-type :vector
                                                                                                              :forms (mapv #(syntax-quote-form % {:env env, :splice? splice?}) forms)}]
                                                                                             (if splice?
                                                                                               {:form-type :list
                                                                                                :forms [{:form-type :namespaced-symbol
                                                                                                         :ns 'bridje.forms
                                                                                                         :sym 'concat}
                                                                                                        inner-forms]}

                                                                                               inner-forms))])}]})]
      (if (and splice? (not= form-type :unquote-splicing))
        {:form-type :vector
         :forms [quoted-form]}

        quoted-form))))

(defn expand-syntax-quotes [form env]
  (letfn [(expand-sq* [{:keys [form-type] :as form}]
            (case form-type
              (:string :bool :int :big-int :float :big-float :symbol :namespaced-symbol) form
              (:vector :set :list :record) (update form :forms #(mapv expand-sq* %))
              :quote (update form :form expand-sq*)

              :syntax-quote (syntax-quote-form (:form form) env)
              :unquote (throw (ex-info "'unquote' outside of 'syntax-quote'" {:form form}))
              :unquote-splicing (throw (ex-info "'unquote-splicing' outside of 'syntax-quote'" {:form form}))))]

    (expand-sq* form)))

(comment
  (-> (first (bridje.reader/read-forms "`[1 ~@['2 '3 '4]]"))
      (expand-syntax-quotes {})
      (expand-normal-quotes)))

(defn quote-form [{:keys [form-type forms] :as form}]
  (letfn [(sym-form [sym]
            {:form-type :symbol
             :sym (name sym)})]
    (if (= form-type :quote)
      (quote-form (quote-form (:form form)))

      {:form-type :list
       :forms [(let [sym (symbol (str "->" (name (u/form-adt-kw form-type))))]
                 {:form-type :namespaced-symbol
                  :ns (name 'bridje.forms)
                  :sym (name sym)})
               {:form-type :record
                :forms (case form-type
                         :string [(sym-form 'string) form]
                         :bool [(sym-form 'bool) form]

                         (:int :float :big-int :big-float) [(sym-form 'number) form]

                         ;; TODO should we make 'symbol' a type in the kernel lang?
                         :symbol [(sym-form 'sym) {:form-type :string, :string (name (:sym form))}]
                         :namespaced-symbol [(sym-form 'ns) {:form-type :string, :string (name (:ns form))}
                                             (sym-form 'sym) {:form-type :string, :string (name (:sym form))}]

                         (:list :vector :set :record) [(sym-form 'forms) {:form-type :vector,
                                                                          :forms (mapv quote-form forms)}])}]})))

(defn expand-normal-quotes [{:keys [form-type] :as form}]
  (case form-type
    (:string :bool :int :big-int :float :big-float :symbol :namespaced-symbol) form
    (:vector :set :list :record) (update form :forms #(mapv expand-normal-quotes %))
    :quote (quote-form (:form form))))

(comment
  (let [env {:global-env {"bridje.forms" {:vars {"->VectorForm" {:value {}}
                                                 "->IntForm" {:value {}}
                                                 "->ListForm" {:value {}}
                                                 "->StringForm" {:value {}}
                                                 "->RecordForm" {:value {}}
                                                 "->SymbolForm" {:value {}}
                                                 "->NamespacedSymbolForm" {:value {}}}}}}]
    (-> (expand-normal-quotes (first (bridje.reader/read-forms "['[1 '''1]]")))
        (analyse env)
        (bridje.emitter/emit-value-expr env))))

(defn expand-quotes [form env]
  (-> form
      (expand-syntax-quotes env)
      expand-normal-quotes))

(comment
  (analyse (first (bridje.reader/read-forms (pr-str '(loop [x 5
                                                            res []]
                                                       (if ((clj zero?) x)
                                                         res
                                                         (recur ((clj dec) x)
                                                                ((clj conj) res x)))))))

           {:global-env {'the-ns {:vars {'foo {}
                                         '->Just {}
                                         'Just->a {}}
                                  :types {'Nothing {}
                                          'Just {}}}}
            :current-ns 'the-ns}))
