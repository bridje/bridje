(ns bridje.analyser)

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

(defn at-least-one [parser]
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

        (if (seq results)
          [results forms]
          (throw (ex-info "TODO: expected at-least-one" {})))))))

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

(defn vector-parser [nested-parser]
  (coll-parser :vector nested-parser))

(defn record-parser [nested-parser]
  (do-parse [{:keys [entries]} (form-type-parser :record)]
    (pure (first (nested-parser entries)))))

(defn maybe [parser]
  )

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

(defn env-resolve [{:keys [ns sym]} {:keys [global-env current-ns]}]
  (if ns
    (when-let [alias-ns (get-in global-env [current-ns :aliases ns])]
      (when (get-in global-env [alias-ns :vars sym])
        (symbol (name alias-ns) (name sym))))

    (or (when (get-in global-env [current-ns :vars sym])
          (symbol (name current-ns) (name sym)))

        (when-let [refer-ns (get-in global-env [current-ns :refers sym])]
          (symbol (name refer-ns) (name sym))))))

(defn analyse-ns-form [{:keys [form-type forms loc-range] :as form} {:keys [global-env locals ns-sym] :as env}]
  ;; TODO WIP
  (if-not (and (= :list form-type)
               (= {:form-type :symbol, :sym 'ns, :ns nil}
                  (-> (first forms) (select-keys [:form-type :sym :ns]))))
    (throw (ex-info "Expecting NS form" {:form form}))

    (parse-forms (rest forms)
                 (do-parse [{ns-sym :sym} (sym-parser {:ns-expected? true})
                            {:keys [refers aliases]} (maybe (record-parser (fn [entries]
                                                                             (reduce (fn [acc [{:keys [sym]}]]
                                                                                       )))))]))))

(defn analyse [{:keys [form-type forms loc-range] :as form} {:keys [global-env locals current-ns] :as env}]
  (merge {:loc-range loc-range}
         (case form-type
           :string {:expr-type :string, :string (:string form)}
           :bool {:expr-type :bool, :bool (:bool form)}
           :vector {:expr-type :vector, :exprs (map #(analyse % env) forms)}
           :set {:expr-type :set, :exprs (map #(analyse % env) forms)}

           :record (cond
                     (pos? (mod (count forms) 2)) (throw (ex-info "Record requires even number of forms" {:loc-range loc-range}))
                     :else {:expr-type :record
                            :entries (for [[{:keys [form-type loc-range] :as k-form} v-form] (partition 2 forms)]
                                       (cond
                                         (not= :symbol form-type) (throw (ex-info "Expected symbol as key in record" {:loc-range loc-range}))
                                         (some? (:ns k-form)) (throw (ex-info "Unexpected namespaced symbol as key in record" {:loc-range loc-range}))
                                         :else [(:sym k-form) (analyse v-form env)]))})

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

                     {:expr-type :call
                      :exprs (map #(analyse % env) forms)})))

             (throw (ex-info "niy" {})))

           :symbol (or (when (nil? (:ns form))
                         (when-let [local (get locals (:sym form))]
                           {:expr-type :local
                            :local local}))

                       (when-let [global (env-resolve form env)]
                         {:expr-type :global
                          :global global})

                       (throw (ex-info "Can't find" {:fq (:fq form)
                                                     :env env}))))))

(comment
  (analyse (first (bridje.reader/read-forms "foo"))
           {:global-env {'the-ns {:vars {'foo 42}}}
            :current-ns 'the-ns}))
