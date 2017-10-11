(ns bridje.analyser
  (:require [clojure.string :as s]))

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
      (if (seq forms)
        (let [{[result more-forms] :result, :keys [error]} (try
                                                             {:result (parser forms)}
                                                             (catch Exception e
                                                               {:error e}))]
          (cond
            result (recur (conj results result) more-forms)
            error (if (seq results)
                    [results forms]
                    (throw error))))

        [results forms]))))

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
      (throw (ex-info "TODO: expected form")))))

(defn form-type-parser [expected-form-type]
  (first-form-parser (fn [{:keys [form-type loc-range] :as form}]
                       (if (= expected-form-type form-type)
                         form
                         (throw (ex-info "Unexpected form type" {:expected expected-form-type
                                                                 :actual form-type
                                                                 :loc-range loc-range}))))))

(defn sym-parser [{:keys [ns-expected?]}]
  (do-parse [{:keys [loc-range] :as sym-form} (form-type-parser :symbol)]
    (cond
      (or (nil? ns-expected?) (= ns-expected? (some? (:ns sym-form)))) (pure sym-form)
      ns-expected? (throw (ex-info "Expected namespaced symbol" sym-form))
      :else (throw (ex-info "Unexpected namespaced symbol" sym-form)))))

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
                                       (do-parse [{aliased-sym :sym} (sym-parser {:ns-expected? false})]
                                         (pure [alias-sym aliased-sym]))))))]
     (no-more-forms (into {} entries)))))

(def refers-parser
  (record-parser
   (do-parse [entries (maybe-many
                       (first-form-parser
                        (fn [[refer-ns form]]
                          (parse-forms [form]
                                       (do-parse [referred-sym-forms (set-parser (maybe-many (sym-parser {:ns-expected? false})))]
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
               (= {:form-type :symbol, :sym 'ns, :ns nil}
                  (-> (first forms) (select-keys [:form-type :sym :ns]))))
    (throw (ex-info "Expecting NS form" {:form form}))

    (parse-forms (rest forms)
                 (do-parse [{ns-sym :sym} (sym-parser {:ns-expected? false})
                            {:keys [refers aliases]} (when-more-forms (record-parser ns-entries-parser))]
                   (no-more-forms {:ns ns-sym
                                   :refers refers
                                   :aliases aliases})))))

(defn env-resolve [{:keys [ns sym]} {:keys [global-env current-ns]}]
  (if ns
    (when-let [alias-ns (get-in global-env [current-ns :aliases ns])]
      (when (get-in global-env [alias-ns :vars sym])
        (symbol (name alias-ns) (name sym))))

    (or (when (get-in global-env [current-ns :vars sym])
          (symbol (name current-ns) (name sym)))

        (when-let [refer-ns (get-in global-env [current-ns :refers sym])]
          (symbol (name refer-ns) (name sym))))))

(defn analyse [{:keys [form-type forms loc-range] :as form} {:keys [global-env locals current-ns] :as env}]
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

           :list
           (if (seq forms)
             (let [[first-form & more-forms] forms
                   expr-parser (fn expr-parser
                                 ([] (expr-parser env))
                                 ([env]
                                  (first-form-parser (fn [form]
                                                       (analyse form env)))))]
               (case (:form-type first-form)
                 :symbol
                 (or (when (nil? (:ns first-form))
                       (case (keyword (:sym first-form))
                         :if (parse-forms more-forms
                                          (do-parse [pred-expr (expr-parser)
                                                     then-expr (expr-parser)
                                                     else-expr (expr-parser)]
                                            (no-more-forms {:expr-type :if
                                                            :pred-expr pred-expr
                                                            :then-expr then-expr
                                                            :else-expr else-expr})))

                         :let (parse-forms more-forms
                                           (do-parse [{:keys [bindings locals]}
                                                      (vector-parser (-> (fn [forms]
                                                                           [(reduce (fn [{:keys [bindings locals]} pair]
                                                                                      (let [[sym expr] (parse-forms pair
                                                                                                                    (do-parse [{:keys [sym]} (sym-parser {:ns-expected? false})
                                                                                                                               expr (expr-parser (assoc env :locals locals))]
                                                                                                                      (pure [sym expr])))
                                                                                            local (gensym sym)]
                                                                                        {:bindings (conj bindings [local expr])
                                                                                         :locals (assoc locals sym local)}))
                                                                                    {:bindings []
                                                                                     :locals locals}
                                                                                    (partition 2 forms))
                                                                            []])
                                                                         (with-ensure-even-forms form)))

                                                      body-expr (expr-parser (assoc env :locals locals))]

                                             (no-more-forms {:expr-type :let
                                                             :bindings bindings
                                                             :body-expr body-expr})))

                         :fn (parse-forms more-forms
                                          (do-parse [params (vector-parser (do-parse [params (at-least-one (sym-parser {:ns-expected? false}))]
                                                                             (no-more-forms (map (comp (juxt identity gensym) :sym) params))))
                                                     body-expr (expr-parser (update env :locals (fnil into {}) params))]
                                            (no-more-forms {:expr-type :fn
                                                            :locals (map second params)
                                                            :body-expr body-expr})))

                         :def (parse-forms more-forms
                                           (do-parse [{:keys [sym params]} (or-parser (sym-parser {:ns-expected? false})
                                                                                      (list-parser (do-parse [{:keys [sym]} (sym-parser {:ns-expected? false})
                                                                                                              params (at-least-one (sym-parser {:ns-expected? false}))]
                                                                                                     (no-more-forms {:sym sym
                                                                                                                     :params (map (comp (juxt identity gensym) :sym) params)}))))
                                                      body-expr (expr-parser (update env :locals (fnil into {}) params))]
                                             (no-more-forms {:expr-type :def
                                                             :sym sym
                                                             :locals (map second params)
                                                             :body-expr body-expr})))

                         :defmacro (throw (ex-info "niy" {}))

                         :defdata (parse-forms more-forms
                                               (do-parse [{:keys [sym params]} (or-parser (sym-parser {:ns-expected? false})
                                                                                          (list-parser (do-parse [{:keys [sym]} (sym-parser {:ns-expected? false})
                                                                                                                  params (at-least-one (sym-parser {:ns-expected? false}))]
                                                                                                         (no-more-forms {:sym sym
                                                                                                                         :params (map :sym params)}))))]
                                                 (no-more-forms {:expr-type :defdata
                                                                 :sym sym
                                                                 :params params})))
                         :match (throw (ex-info "niy" {}))

                         :loop (throw (ex-info "niy" {}))
                         :recur (throw (ex-info "niy" {}))

                         nil))

                     (when (= 'js (:ns first-form))
                       (let [sym-str (name (:sym first-form))]
                         (when (s/starts-with? sym-str ".")
                           (case (last sym-str)
                             \? (parse-forms more-forms
                                             (do-parse [target-expr (expr-parser)]
                                               (no-more-forms {:expr-type :js-get
                                                               :field (symbol (subs sym-str 1 (dec (count sym-str))))
                                                               :target-expr target-expr})))

                             \= (parse-forms more-forms
                                             (do-parse [target-expr (expr-parser)
                                                        value-expr (expr-parser)]
                                               (no-more-forms {:expr-type :js-set
                                                               :field (symbol (subs sym-str 1 (dec (count sym-str))))
                                                               :target-expr target-expr
                                                               :value-expr value-expr})))

                             (parse-forms more-forms
                                          (do-parse [target-expr (expr-parser)
                                                     exprs (maybe-many (expr-parser))]
                                            (no-more-forms {:expr-type :js-call
                                                            :method (symbol (subs sym-str 1))
                                                            :target-expr target-expr
                                                            :exprs exprs})))))))

                     {:expr-type :call
                      :exprs (map #(analyse % env) forms)})))

             (throw (ex-info "niy" {})))

           :symbol (or (when (nil? (:ns form))
                         (when-let [local (get locals (:sym form))]
                           {:expr-type :local
                            :local local}))

                       (when (= 'js (:ns form))
                         {:expr-type :js-global
                          :js-global (:sym form)})

                       (when-let [global (env-resolve form env)]
                         {:expr-type :global
                          :global global})

                       (throw (ex-info "Can't find" {:fq (:fq form)
                                                     :env env}))))))

(comment
  (analyse (first (bridje.reader/read-forms "foo"))
           {:global-env {'the-ns {:vars {'foo 42}}}
            :current-ns 'the-ns}))
