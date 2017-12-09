(ns bridje.parser)

(defn parse-forms [forms parser]
  (first (parser forms)))

(defn pure [v]
  (fn [forms]
    [v forms]))

(defn fmap [parser f]
  (fn [forms]
    (when-let [[res more-forms] (parser forms)]
      [(f res) more-forms])))

(defn then [parser f]
  (fn [forms]
    (when-let [[res more-forms] (parser forms)]
      ((f res) more-forms))))

(defmacro do-parse {:style/indent 1} [bindings & body]
  (if-let [[binding value & more-bindings] (seq bindings)]
    `(fn [forms#]
       (let [[~binding more-forms#] (~value forms#)]
         ((do-parse ~more-bindings ~@body) more-forms#)))

    `(do ~@body)))

(defn dbg-parser [parser parser-sym]
  (fn [forms]
    (let [instance-sym (gensym parser-sym)]
      (prn :dbg-forms parser-sym instance-sym forms)
      (let [res (parser forms)]
        (prn :dbg-res parser-sym instance-sym res)
        res))))

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
  (first-form-parser (fn [{:keys [form-type] :as form}]
                       (if (= expected-form-type form-type)
                         form
                         (throw (ex-info "Unexpected form type" {:expected expected-form-type
                                                                 :actual form-type}))))))

(def form-parser
  (fn [[form & more-forms]]
    (if form
      [form more-forms]
      (throw (ex-info "Expected form")))))

(def sym-parser
  (form-type-parser :symbol))

(defn literal-sym-parser [expected-sym]
  (do-parse [{:keys [sym]} sym-parser]
    (if (= expected-sym sym)
      (pure sym)
      (throw (ex-info "Expected sym" {:expected expected-sym
                                      :actual sym})))))

(def kw-parser
  (form-type-parser :keyword))

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
  (do-parse [{:keys [forms]} (form-type-parser :record)]
    (cond
      (pos? (mod (count forms) 2)) (throw (ex-info "Record requires even number of forms" {}))
      :else (pure (first (nested-parser (for [[{:keys [form-type] :as k-form} v-form] (partition 2 forms)]
                                          (cond
                                            (not= :keyword form-type) (throw (ex-info "Expected keyword as key in record" {}))
                                            :else [(:kw k-form) v-form]))))))))

(defn no-more-forms [value]
  (fn [forms]
    (if (seq forms)
      (throw (ex-info "Unexpected form" {:form (first forms)}))
      [value []])))

(defn with-ensure-even-forms [parser]
  (fn [forms]
    (if (zero? (mod (count forms) 2))
      (parser forms)
      (throw (ex-info "Expected even number of forms" {:forms forms})))))
