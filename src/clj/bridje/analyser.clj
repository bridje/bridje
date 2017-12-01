(ns bridje.analyser
  (:require [bridje.forms :as f]
            [bridje.util :as u]
            [clojure.string :as s]
            [bridje.type-checker :as tc]))

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
  (first-form-parser (fn [{:keys [form-type] :as form}]
                       (if (= expected-form-type form-type)
                         form
                         (throw (ex-info "Unexpected form type" {:expected expected-form-type
                                                                 :actual form-type
                                                                 }))))))

(def sym-parser
  (form-type-parser :symbol))

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

(defn parse-poly-type [{:keys [form-type] :as form} env]
  ;; TODO need to parse more than just primitives here
  (case form-type
    :symbol (if-let [prim-type (get '{String :string, Bool :bool,
                                      Int :int, Float :float,
                                      BigInt :big-int, BigFloat :big-float}
                                    (:sym form))]
              (tc/mono->poly (tc/primitive-type prim-type))
              (throw (ex-info "Unexpected symbol, parsing type" {:form form})))
    (throw (ex-info "Unexpected form, parsing type" {:form form}))))

(defn analyse [{:keys [form-type forms] :as form} {:keys [env locals loop-locals] :as ctx}]
  (case form-type
    :string {:expr-type :string, :string (:string form)}
    :bool {:expr-type :bool, :bool (:bool form)}
    (:int :float :big-int :big-float) {:expr-type form-type, :number (:number form)}
    :vector {:expr-type :vector, :exprs (map #(analyse % ctx) forms)}
    :set {:expr-type :set, :exprs (map #(analyse % ctx) forms)}

    :record (parse-forms [form]
                         (record-parser (do-parse [entries (maybe-many (first-form-parser (fn [[sym form]]
                                                                                            [sym (analyse form ctx)])))]
                                          (no-more-forms {:expr-type :record
                                                          :entries entries}))))

    :list
    (if (seq forms)
      (let [[first-form & more-forms] forms
            expr-parser (fn expr-parser
                          ([] (expr-parser ctx))
                          ([ctx]
                           (first-form-parser (fn [form]
                                                (analyse form ctx)))))

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
                                                                                                         params (maybe-many sym-parser)]
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
                                          (do-parse [{:keys [sym]} sym-parser
                                                     attributes (record-parser (fn [entries]
                                                                                 [(into []
                                                                                        (map (fn [[kw type-form]]
                                                                                               {:attribute (keyword (format "%s.%s" (name sym) (name kw)))
                                                                                                ::tc/poly-type (parse-poly-type type-form env)}))
                                                                                        entries)
                                                                                  []]))]
                                            (no-more-forms {:expr-type :defdata
                                                            :sym sym
                                                            :attributes attributes})))

                    :match (parse-forms more-forms
                                        (do-parse [match-expr (expr-parser (dissoc ctx :loop-locals))]
                                          (fn [forms]
                                            (cond
                                              (zero? (mod (count forms) 2)) (throw (ex-info "Missing default in 'match'" {}))
                                              :else (let [clauses (parse-forms (butlast forms)
                                                                               (do-parse [clauses (maybe-many (do-parse [{:keys [sym]} sym-parser
                                                                                                                         expr (expr-parser)]
                                                                                                                (if (contains? (:types env) sym)
                                                                                                                  (pure [sym expr])
                                                                                                                  (throw (ex-info "Can't resolve type:"
                                                                                                                                  {:type sym})))))]
                                                                                 (no-more-forms clauses)))
                                                          default-expr (analyse (last forms) ctx)]

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
                                                                {}))
                             (not= (count loop-locals) (count more-forms)) (throw (ex-info "'recur' called with wrong number of arguments"
                                                                                           {:expected (count loop-locals)
                                                                                            :found (count more-forms)}))
                             :else (parse-forms more-forms
                                                (do-parse [exprs (maybe-many (expr-parser (dissoc ctx :loop-locals)))]
                                                  (no-more-forms {:expr-type :recur
                                                                  :exprs exprs
                                                                  :loop-locals loop-locals}))))

                    ;; fall through to 'call'
                    nil))

              ;; fall through to 'call'
              (:list) nil)

            {:expr-type :call
             :exprs (map #(analyse % ctx) forms)}))

      (throw (ex-info "niy" {})))

    :symbol (let [{:keys [sym]} form]
              (or (when-let [local (get locals sym)]
                    {:expr-type :local
                     :local local})

                  (when (contains? (:vars env) sym)
                    {:expr-type :global
                     :global (:sym form)})

                  (throw (ex-info "Can't find" {:sym sym
                                                :ctx ctx}))))

    :keyword (if-let [attribute (get-in env [:attributes (:kw form)])]
               {:expr-type :attribute
                :attribute attribute}

               (throw (ex-info "Cannot resolve attribute" {:attribute (:kw form)})))))
