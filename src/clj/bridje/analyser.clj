(ns bridje.analyser
  (:require [bridje.forms :as f]
            [bridje.parser :as p :refer [do-parse]]
            [bridje.util :as u]
            [clojure.string :as s]
            [bridje.type-checker :as tc]
            [clojure.walk :as w]
            [clojure.string :as str]
            [clojure.set :as set]))

(defn type-tv-mapping [form]
  (->> (u/sub-forms form)
       (into {} (comp (filter (every-pred (comp #{:symbol} :form-type)
                                          (comp #(Character/isLowerCase %) first name :sym)))
                      (distinct)
                      (map (fn [{:keys [sym]}]
                             {sym (tc/->type-var (name sym))}))))))

(defn type-declaration-tv-mapping [form]
  (->> (u/sub-forms form)
       (into {} (comp (keep :sym)
                      (filter (comp #(Character/isLowerCase %) first name))
                      (map (fn [sym]
                             [sym (tc/->type-var sym)]))))))

(defn parse-type [form env]
  (let [tv-mapping (type-declaration-tv-mapping form)]
    (letfn [(parse-type* [{:keys [form-type] :as form}]
              (case form-type
                :symbol (or (when-let [prim-type (get '{String :string, Bool :bool,
                                                        Int :int, Float :float,
                                                        BigInt :big-int, BigFloat :big-float}
                                                      (:sym form))]
                              (tc/primitive-type prim-type))

                            (get tv-mapping (:sym form))

                            (throw (ex-info "Unexpected symbol, parsing type" {:form form})))

                (:vector :set) (p/parse-forms (:forms form)
                                              (do-parse [elem-type (p/first-form-parser parse-type*)]
                                                (p/no-more-forms #::tc{:type form-type
                                                                       :elem-type elem-type})))

                :list (p/parse-forms (:forms form)
                                     (do-parse [fn-sym (p/literal-sym-parser 'Fn)
                                                param-forms (p/vector-parser (fn [forms] [forms []]))
                                                return-form p/form-parser]
                                       (p/pure #::tc{:type :fn
                                                     :param-types (into [] (map parse-type*) param-forms)
                                                     :return-type (parse-type* return-form)})))
                (throw (ex-info "Unexpected form, parsing type" {:form form}))))]

      #::tc{:type-vars (into #{} (map ::tc/type-var) (vals tv-mapping))
            :mono-type (parse-type* form)})))

(def colon-sym
  (symbol ":"))

(def type-declaration-form-parser
  (p/list-parser (do-parse [colon (p/literal-sym-parser colon-sym)

                            {:keys [sym param-forms]}
                            (p/or-parser (-> p/sym-parser
                                             (p/fmap #(select-keys % [:sym])))

                                         (do-parse [{:keys [sym param-forms]}
                                                    (p/list-parser (do-parse [{:keys [sym]} p/sym-parser
                                                                              param-forms (p/maybe-many p/form-parser)]
                                                                     (p/no-more-forms {:sym sym
                                                                                       :param-forms param-forms})))]
                                           (p/pure {:sym sym, :param-forms param-forms})))

                            return-form p/form-parser]
                   (p/no-more-forms {:sym sym, :param-forms param-forms, :return-form return-form}))))

(defn type-declaration-parser [env]
  (do-parse [{:keys [sym param-forms return-form]} type-declaration-form-parser]
    (let [tv-mapping (type-declaration-tv-mapping {:param-forms param-forms,
                                                   :return-form return-form})]
      (p/pure {:sym sym
               ::tc/poly-type (parse-type (if param-forms
                                            {:form-type :list,
                                             :forms [{:form-type :symbol, :sym 'Fn}
                                                     {:form-type :vector, :forms param-forms}
                                                     return-form]}
                                            return-form)
                                          env)}))))

(defn bindings-parser [expr-parser ctx]
  (p/vector-parser (-> (fn [forms]
                         [(reduce (fn [{:keys [bindings locals]} pair]
                                    (let [[sym expr] (p/parse-forms pair
                                                                    (do-parse [{:keys [sym]} p/sym-parser
                                                                               expr (expr-parser (-> ctx
                                                                                                     (update :locals (fnil into {}) locals)
                                                                                                     (dissoc :loop-locals)))]
                                                                      (p/pure [sym expr])))
                                          local (gensym sym)]
                                      {:bindings (conj bindings [local expr])
                                       :locals (assoc locals sym local)}))
                                  {:bindings []
                                   :locals {}}
                                  (partition 2 forms))
                          []])
                       p/with-ensure-even-forms)))

(defn attributes-decl-parser [{:keys [prefix env]}]
  (p/record-parser
   (fn [entries]
     [(into {}
            (map (fn [[kw type-form]]
                   (let [{:keys [::tc/mono-type ::tc/type-vars] :as poly-type} (parse-type type-form env)]
                     (if (seq type-vars)
                       (throw (ex-info "Attribute types can't be polymorphic (for now?)"
                                       {::tc/poly-type poly-type}))
                       [(keyword (format "%s.%s" (name prefix) (name kw))) {::tc/mono-type mono-type}]))))
            entries)
      []])))

(defn analyse [{:keys [form-type forms] :as form} {:keys [env locals loop-locals] :as ctx}]
  (case form-type
    :string {:expr-type :string, :string (:string form)}
    :bool {:expr-type :bool, :bool (:bool form)}
    (:int :float :big-int :big-float) {:expr-type form-type, :number (:number form)}
    :vector {:expr-type :vector, :exprs (map #(analyse % ctx) forms)}
    :set {:expr-type :set, :exprs (map #(analyse % ctx) forms)}

    :record (p/parse-forms [form]
                           (p/record-parser (do-parse [entries (p/maybe-many (p/first-form-parser (fn [[sym form]]
                                                                                                    [sym (analyse form ctx)])))]
                                              (p/no-more-forms {:expr-type :record
                                                                :entries entries}))))

    :list
    (if (seq forms)
      (let [[first-form & more-forms] forms
            expr-parser (fn expr-parser
                          ([] (expr-parser ctx))
                          ([ctx]
                           (p/first-form-parser (fn [form]
                                                  (analyse form ctx)))))]

        (or (case (:form-type first-form)
              :symbol
              (or (case (keyword (:sym first-form))
                    :if (p/parse-forms more-forms
                                       (do-parse [pred-expr (expr-parser (dissoc ctx :loop-locals))
                                                  then-expr (expr-parser)
                                                  else-expr (expr-parser)]
                                         (p/no-more-forms {:expr-type :if
                                                           :pred-expr pred-expr
                                                           :then-expr then-expr
                                                           :else-expr else-expr})))

                    :let (p/parse-forms more-forms
                                        (do-parse [{:keys [bindings locals]} (bindings-parser expr-parser ctx)
                                                   body-expr (expr-parser (-> ctx
                                                                              (update :locals (fnil into {}) locals)))]
                                          (p/no-more-forms {:expr-type :let
                                                            :bindings bindings
                                                            :body-expr body-expr})))

                    :loop (p/parse-forms more-forms
                                         (do-parse [{:keys [bindings locals]} (bindings-parser expr-parser ctx)
                                                    body-expr (expr-parser (-> ctx
                                                                               (update :locals (fnil into {}) locals)
                                                                               (assoc :loop-locals (map second locals))))]
                                           (p/no-more-forms {:expr-type :loop
                                                             :bindings bindings
                                                             :body-expr body-expr})))

                    :recur (cond
                             (nil? loop-locals) (throw (ex-info "'recur' called from non-tail position"
                                                                {}))
                             (not= (count loop-locals) (count more-forms)) (throw (ex-info "'recur' called with wrong number of arguments"
                                                                                           {:expected (count loop-locals)
                                                                                            :found (count more-forms)}))
                             :else (p/parse-forms more-forms
                                                  (do-parse [exprs (p/maybe-many (expr-parser (dissoc ctx :loop-locals)))]
                                                    (p/no-more-forms {:expr-type :recur
                                                                      :exprs exprs
                                                                      :loop-locals loop-locals}))))

                    :fn (p/parse-forms more-forms
                                       (do-parse [{:keys [sym params]} (p/list-parser (do-parse [[name-param & more-params] (p/at-least-one p/sym-parser)]
                                                                                        (p/no-more-forms {:sym (:sym name-param)
                                                                                                          :params (map (comp (juxt identity gensym) :sym) more-params)})))
                                                  body-expr (expr-parser (-> ctx
                                                                             (update :locals (fnil into {}) params)
                                                                             (assoc :loop-locals (map second params))))]
                                         (p/no-more-forms {:expr-type :fn
                                                           :sym sym
                                                           :locals (map second params)
                                                           :body-expr body-expr})))

                    :def (p/parse-forms more-forms
                                        (do-parse [{:keys [sym params]} (p/or-parser p/sym-parser
                                                                                     (p/list-parser (do-parse [{:keys [sym]} p/sym-parser
                                                                                                               params (p/maybe-many p/sym-parser)]
                                                                                                      (p/no-more-forms {:sym sym
                                                                                                                        :params (map (comp (juxt identity gensym) :sym) params)}))))
                                                   body-expr (expr-parser (-> ctx
                                                                              (update :locals (fnil into {}) params)
                                                                              (assoc :loop-locals (map second params))))]
                                          (p/no-more-forms {:expr-type :def
                                                            :sym sym
                                                            :locals (when params
                                                                      (map second params))
                                                            :body-expr body-expr})))

                    :defattrs (p/parse-forms
                               more-forms
                               (do-parse [{:keys [sym]} p/sym-parser
                                          attributes (attributes-decl-parser {:prefix sym, :env env})]
                                 (p/no-more-forms {:expr-type :defattrs
                                                   :sym sym
                                                   :attributes attributes})))

                    :defadt (p/parse-forms
                             more-forms
                             (do-parse [{:keys [sym]} p/sym-parser
                                        constructors (p/maybe-many
                                                      (p/or-parser
                                                       (-> p/sym-parser
                                                           (p/fmap (fn [{:keys [sym]}]
                                                                     {:constructor-sym sym})))

                                                       (p/list-parser
                                                        (do-parse [{constructor-sym :sym} p/sym-parser
                                                                   attributes (attributes-decl-parser {:prefix constructor-sym, :env env})]
                                                          (p/no-more-forms {:constructor-sym constructor-sym
                                                                            :attributes attributes})))))]
                               (p/no-more-forms {:expr-type :defadt
                                                 :sym sym
                                                 :attributes (into {} (mapcat :attributes) constructors)
                                                 :constructors (into {} (map (fn [{:keys [constructor-sym attributes]}]
                                                                               [constructor-sym
                                                                                {:attributes (when attributes
                                                                                               (into #{} (keys attributes)))}]))
                                                                     constructors)})))

                    :defclj (p/parse-forms more-forms
                                           (do-parse [{ns :sym} p/sym-parser
                                                      clj-fns (p/maybe-many (type-declaration-parser env))]
                                             (let [clj-vars (try
                                                              (require ns)
                                                              (ns-publics ns)
                                                              (catch Exception e
                                                                (throw (ex-info "Failed requiring defclj namespace" {:ns ns} e))))]

                                               (when-let [unavailable-syms (seq (set/difference (into #{} (map :sym) clj-fns)
                                                                                                (set (keys clj-vars))))]
                                                 (throw (ex-info "Couldn't find CLJ vars" {:ns ns
                                                                                           :unavailable-syms (set unavailable-syms)})))

                                               (p/no-more-forms {:expr-type :defclj
                                                                 :clj-fns (->> clj-fns
                                                                               (into #{} (map (fn [{:keys [sym ::tc/poly-type]}]
                                                                                                {:ns ns
                                                                                                 :sym sym
                                                                                                 ::tc/poly-type poly-type
                                                                                                 :value @(get clj-vars sym)}))))}))))

                    ;; fall through to 'call'
                    nil))

              ;; fall through to 'call'
              (:list :keyword) nil)

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

    :keyword (let [{:keys [kw]} form]
               (if (get-in env [:attributes kw])
                 {:expr-type :attribute
                  :attribute kw}

                 (throw (ex-info "Cannot resolve attribute" {:attribute kw}))))))
