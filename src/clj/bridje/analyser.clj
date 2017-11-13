(ns bridje.analyser
  (:require [bridje.forms :as f]
            [bridje.util :as u]
            [clojure.string :as s]))

(defn parse-forms [forms parser]
  (first (parser forms)))

(defn pure [v]
  (fn [forms]
    [v forms]))

(defn fmap [parser f]
  (fn [forms]
    (when-let [[res more-forms] (parser forms)]
      [(f res) more-forms])))

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

(defn resolve-sym [{:keys [ns sym]} resolve-type {:keys [env current-ns]}]
  (if ns
    (or (when-let [alias-ns (get-in env [current-ns :aliases ns])]
          (when (get-in env [alias-ns resolve-type sym])
            (symbol (name alias-ns) (name sym))))

        (when (contains? env ns)
          (when (get-in env [ns resolve-type sym])
            (symbol (name ns) (name sym)))))

    (or (when (get-in env [current-ns resolve-type sym])
          (symbol (name current-ns) (name sym)))

        (when-let [refer-ns (get-in env [current-ns :refers sym])]
          (symbol (name refer-ns) (name sym))))))

(defn analyse-kernel-expr [{:keys [form-type forms loc-range] :as form} {:keys [env locals loop-locals current-ns] :as ctx}]
  (merge {:loc-range loc-range}
         (case form-type
           :string {:expr-type :string, :string (:string form)}
           :bool {:expr-type :bool, :bool (:bool form)}
           (:int :float :big-int :big-float) {:expr-type form-type, :number (:number form)}
           :vector {:expr-type :vector, :exprs (map #(analyse-kernel-expr % ctx) forms)}
           :set {:expr-type :set, :exprs (map #(analyse-kernel-expr % ctx) forms)}

           :record (parse-forms [form]
                                (record-parser (do-parse [entries (maybe-many (first-form-parser (fn [[sym form]]
                                                                                                   [sym (analyse-kernel-expr form ctx)])))]
                                                 (no-more-forms {:expr-type :record
                                                                 :entries entries}))))

           :list
           (if (seq forms)
             (let [[first-form & more-forms] forms
                   expr-parser (fn expr-parser
                                 ([] (expr-parser ctx))
                                 ([ctx]
                                  (first-form-parser (fn [form]
                                                       (analyse-kernel-expr form ctx)))))

                   bindings-parser (vector-parser (-> (fn [forms]
                                                        [(reduce (fn [{:keys [bindings locals]} pair]
                                                                   (let [[sym expr] (parse-forms pair
                                                                                                 (do-parse [{:keys [sym]} sym-parser
                                                                                                            expr (expr-parser (-> ctx
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
                                            (do-parse [pred-expr (expr-parser (dissoc ctx :loop-locals))
                                                       then-expr (expr-parser)
                                                       else-expr (expr-parser)]
                                              (no-more-forms {:expr-type :if
                                                              :pred-expr pred-expr
                                                              :then-expr then-expr
                                                              :else-expr else-expr})))

                           :let (parse-forms more-forms
                                             (do-parse [{:keys [bindings locals]} bindings-parser
                                                        body-expr (expr-parser (-> ctx
                                                                                   (update :locals (fnil into {}) locals)))]
                                               (no-more-forms {:expr-type :let
                                                               :bindings bindings
                                                               :body-expr body-expr})))

                           :fn (parse-forms more-forms
                                            (do-parse [{:keys [sym params]} (list-parser (do-parse [[name-param & more-params] (at-least-one sym-parser)]
                                                                                           (no-more-forms {:sym (:sym name-param)
                                                                                                           :params (map (comp (juxt identity gensym) :sym) more-params)})))
                                                       body-expr (expr-parser (-> ctx
                                                                                  (update :locals (fnil into {}) params)
                                                                                  (assoc :loop-locals (map second params))))]
                                              (no-more-forms {:expr-type :fn
                                                              :sym sym
                                                              :locals (map second params)
                                                              :body-expr body-expr})))

                           :def (parse-forms more-forms
                                             (do-parse [{:keys [sym params]} (or-parser sym-parser
                                                                                        (list-parser (do-parse [{:keys [sym]} sym-parser
                                                                                                                params (at-least-one sym-parser)]
                                                                                                       (no-more-forms {:sym sym
                                                                                                                       :params (map (comp (juxt identity gensym) :sym) params)}))))
                                                        body-expr (expr-parser (-> ctx
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
                                                                                                                    params (or-parser (-> (set-parser (at-least-one sym-parser))
                                                                                                                                          (fmap #(into #{} (map :sym) %)))
                                                                                                                                      (-> (at-least-one sym-parser)
                                                                                                                                          (fmap #(into [] (map :sym) %))))]
                                                                                                           (no-more-forms {:sym sym
                                                                                                                           :params params}))))]
                                                   (no-more-forms {:expr-type :defdata
                                                                   :sym sym
                                                                   :params params})))

                           :match (parse-forms more-forms
                                               (do-parse [match-expr (expr-parser (dissoc ctx :loop-locals))]
                                                 (fn [forms]
                                                   (cond
                                                     (zero? (mod (count forms) 2)) (throw (ex-info "Missing default in 'match'" {:loc-range (:loc-range form)}))
                                                     :else (let [clauses (parse-forms (butlast forms)
                                                                                      (do-parse [clauses (maybe-many (do-parse [sym (or-parser sym-parser ns-sym-parser)
                                                                                                                                expr (expr-parser)]
                                                                                                                       (if-let [fq-sym (resolve-sym sym :types ctx)]
                                                                                                                         (pure [fq-sym expr])
                                                                                                                         (throw (ex-info "Can't resolve type:"
                                                                                                                                         {:type (select-keys sym [:ns :sym])
                                                                                                                                          :loc-range (:loc-range sym)})))))]
                                                                                        (no-more-forms clauses)))
                                                                 default-expr (analyse-kernel-expr (last forms) ctx)]

                                                             [{:expr-type :match
                                                               :match-expr match-expr
                                                               :clauses clauses
                                                               :default-expr default-expr}
                                                              []])))))

                           :loop (parse-forms more-forms
                                              (do-parse [{:keys [bindings locals]} bindings-parser
                                                         body-expr (expr-parser (-> ctx
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
                                                       (do-parse [exprs (maybe-many (expr-parser (dissoc ctx :loop-locals)))]
                                                         (no-more-forms {:expr-type :recur
                                                                         :exprs exprs
                                                                         :loop-locals loop-locals}))))

                           :clj (parse-forms more-forms
                                             (do-parse [{:keys [ns sym]} (or-parser sym-parser ns-sym-parser)]
                                               (no-more-forms {:expr-type :clj-var
                                                               :clj-var (symbol (or (some-> ns name)
                                                                                    (name 'clojure.core))
                                                                                (name sym))})))

                           ;; fall through to 'call'
                           nil))

                     ;; fall through to 'call'
                     (:list :namespaced-symbol) nil)

                   {:expr-type :call
                    :exprs (map #(analyse-kernel-expr % ctx) forms)}))

             (throw (ex-info "niy" {})))

           :symbol (or (when-let [local (get locals (:sym form))]
                         {:expr-type :local
                          :local local})

                       (when-let [global (resolve-sym form :vars ctx)]
                         {:expr-type :global
                          :global global})

                       (throw (ex-info "Can't find" {:sym (:sym form)
                                                     :ctx ctx})))

           :namespaced-symbol (if-let [global (resolve-sym form :vars ctx)]
                                {:expr-type :global
                                 :global global}

                                (throw (ex-info "Can't find" {:ns (:ns form)
                                                              :sym (:sym form)
                                                              :ctx ctx}))))))

(defn analyse [form {:keys [current-ns env] :as ctx}]
  (if (u/kernel? current-ns)
    (analyse-kernel-expr form ctx)

    (-> form
        f/wrap-forms
        ;; TODO needs to pass env through
        ((get-in env '[bridje.kernel.analyser :vars analyse :value]) {:current-ns current-ns})
        f/unwrap-exprs)))

(comment
  (let [ctx {:env (bridje.main/bootstrap-env {:io (:compiler-io (bridje.fake-io/fake-io {}))})
             :current-ns 'the-ns}]
    (-> (first (bridje.reader/read-forms (pr-str true)))
        (analyse ctx)
        (bridje.emitter/emit-value-expr ctx))))
