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

(defn no-more-forms [value]
  (fn [forms]
    (if (seq forms)
      (throw (ex-info "Unexpected form" {:form (first forms)}))
      [value []])))

(defn analyse [{:keys [form-type forms loc-range] :as form} {:keys [global-env ns-sym] :as env}]
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
           :list (if (seq forms)
                   (let [[first-form & more-forms] forms
                         expr-parser (first-form-parser (fn [form]
                                                          (analyse form env)))]
                     (case (:form-type first-form)
                       :symbol (if (nil? (:ns first-form))
                                 (case (keyword (:sym first-form))
                                   :if (parse-forms more-forms
                                                    (do-parse [pred-expr expr-parser
                                                               then-expr expr-parser
                                                               else-expr expr-parser]
                                                      (pure {:expr-type :if
                                                             :pred-expr pred-expr
                                                             :then-expr then-expr
                                                             :else-expr else-expr})))

                                   :def (parse-forms more-forms
                                                     (do-parse [{:keys [sym]} (sym-parser {:ns-expected? false})
                                                                body-expr expr-parser]
                                                       (no-more-forms {:expr-type :def
                                                                       :sym sym
                                                                       :body-expr body-expr}))))

                                 (throw (ex-info "niy" {})))))

                   (throw (ex-info "niy" {}))))))
