(ns bridje.analyser
  (:require [bridje.forms :as f]
            [bridje.parser :as p :refer [do-parse]]
            [bridje.util :as u]
            [clojure.string :as s]
            [bridje.type-checker :as tc]))

(defn parse-type [{:keys [form-type] :as form} env]
  (case form-type
    :symbol (if-let [prim-type (get '{String :string, Bool :bool,
                                      Int :int, Float :float,
                                      BigInt :big-int, BigFloat :big-float}
                                    (:sym form))]
              (tc/primitive-type prim-type)
              (throw (ex-info "Unexpected symbol, parsing type" {:form form})))
    (throw (ex-info "Unexpected form, parsing type" {:form form}))))

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

                    :defdata (p/parse-forms more-forms
                                            (do-parse [{:keys [sym]} p/sym-parser
                                                       attributes (p/record-parser (fn [entries]
                                                                                     [(into []
                                                                                            (map (fn [[kw type-form]]
                                                                                                   {:attribute (keyword (format "%s.%s" (name sym) (name kw)))
                                                                                                    ::tc/mono-type (parse-type type-form env)}))
                                                                                            entries)
                                                                                      []]))]
                                              (p/no-more-forms {:expr-type :defdata
                                                                :sym sym
                                                                :attributes attributes})))

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
