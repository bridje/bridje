(ns bridje.emitter
  (:require [bridje.runtime :as rt]
            [bridje.util :as u]
            [clojure.string :as s]))

(defn find-globals [sub-exprs]
  (->> sub-exprs
       (into {} (comp (filter #(= :global (:expr-type %)))
                      (map :global)
                      (distinct)
                      (map (fn [global]
                             [global (gensym (name global))]))))))

(defn find-clj-namespaces [sub-exprs]
  (->> sub-exprs
       (into #{} (comp (filter #(= :clj-var (:expr-type %)))
                       (map (comp symbol namespace :clj-var))))))

(def form-adt-kw
  (-> (fn [form-type]
        (let [[_ fst snd] (re-matches #"([a-z]+)(-[a-z]+)*" (name form-type))]
          (keyword (name :bridje.forms)
                   (str (s/capitalize fst)
                        (when snd
                          (s/capitalize (subs snd 1)))
                        "Form"))))
      memoize))

(defn emit-form [{:keys [form-type forms] :as form}]
  `(rt/->ADT ~(form-adt-kw form-type)
             ~(case form-type
                :bool {:bool (:bool form)}
                :string {:string (:string form)}
                (:int :float :big-int :big-float) {:number (:number form)}
                (:list :vector :set :record) {:forms (into [] (map emit-form) forms)}
                (:quote :syntax-quote :unquote :unquote-splicing) {:form (emit-form (:form form))}
                :symbol `'~(select-keys form [:fq :ns :sym]))))

(defn emit-value-expr [expr {:keys [current-ns] :as env}]
  (let [sub-exprs (u/sub-exprs expr)
        globals (find-globals sub-exprs)
        clj-namespaces (find-clj-namespaces sub-exprs)]

    (letfn [(emit-value-expr* [{:keys [expr-type exprs] :as expr}]
              (case expr-type
                :string (:string expr)
                :bool (:bool expr)
                (:int :float :big-int :big-float) (:number expr)

                :vector (->> exprs
                             (into [] (map emit-value-expr*)))

                :set (->> exprs
                          (into #{} (map emit-value-expr*)))

                :record (->> (:entries expr)
                             (into {} (map (fn [[sym expr]]
                                             [(keyword sym) (emit-value-expr* expr)]))))

                :if `(if ~(emit-value-expr* (:pred-expr expr))
                       ~(emit-value-expr* (:then-expr expr))
                       ~(emit-value-expr* (:else-expr expr)))

                :local (:local expr)
                :global (get globals (:global expr))
                :clj-var (:clj-var expr)

                :quote (emit-form (:form expr))

                :let (let [{:keys [bindings body-expr]} expr]
                       `(let [~@(mapcat (fn [[local expr]]
                                          [local (emit-value-expr* expr)])
                                        bindings)]
                          ~(emit-value-expr* body-expr)))

                :fn (let [{:keys [locals body-expr]} expr]
                      `(fn [~@locals]
                         ~(emit-value-expr* body-expr)))

                :call `(~@(map emit-value-expr* exprs))

                :match (let [{:keys [match-expr clauses default-expr]} expr]
                         `(let [match# ~(emit-value-expr* match-expr)]
                            (case (:adt-type match#)
                              ~@(->> (for [[fq-sym expr] clauses]
                                       `[~fq-sym ~(emit-value-expr* expr)])
                                     (apply concat))

                              ~(emit-value-expr* default-expr))))

                :loop (let [{:keys [bindings body-expr]} expr]
                        `(loop [~@(mapcat (fn [[local expr]]
                                            [local (emit-value-expr* expr)])
                                          bindings)]
                           ~(emit-value-expr* body-expr)))

                :recur `(recur ~@(map emit-value-expr* exprs))))]

      (let [env-sym (gensym 'env)]
        `(fn [~env-sym]
           (do
             ~@(for [clj-ns clj-namespaces]
                 `(require '~clj-ns)))

           (let [~@(mapcat (fn [[global global-sym]]
                             [global-sym `(get-in ~env-sym ['~(symbol (namespace global))
                                                           :vars
                                                           '~(symbol (name global))
                                                           :value])])
                           globals)]
             ~(emit-value-expr* expr)))))))

(defn emit-expr [{:keys [expr-type] :as expr} {:keys [global-env current-ns] :as env}]
  (case expr-type
    :def
    (let [{:keys [sym locals body-expr]} expr]
      {:global-env (assoc-in global-env [current-ns :vars sym] {})
       :code `(fn [env#]
                (assoc-in env# ['~current-ns :vars '~sym]
                          {:value (~(emit-value-expr (if (seq locals)
                                                       {:expr-type :fn
                                                        :locals locals
                                                        :body-expr body-expr}
                                                       body-expr)
                                                     env)
                                   env#)}))})

    :defmacro (throw (ex-info "niy" {:expr expr}))

    :defdata
    (let [{:keys [sym params]} expr
          fq-sym (symbol (name current-ns) (name sym))]
      {:global-env (-> global-env
                       (assoc-in [current-ns :types sym] {:params params})
                       (update-in [current-ns :vars] merge
                                  (if (seq params)
                                    (merge {(symbol (str "->" (name sym))) {}}
                                           (into {}
                                                 (map (fn [param]
                                                        [(symbol (format "%s->%s" (name sym) (name param))) {}]))
                                                 params))

                                    {sym {}})))
       :code `(fn [env#]
                (-> env#
                    (assoc-in ['~current-ns :types '~sym] {:params '[~@params]})
                    (update-in ['~current-ns :vars] merge
                            ~(if (seq params)
                               (merge `{'~(symbol (str "->" sym)) {:value (fn [~@params]
                                                                            (rt/->ADT '~fq-sym
                                                                                      ~(into {}
                                                                                             (map (fn [param]
                                                                                                    [(keyword param) param]))
                                                                                             params)))}}
                                      (->> params
                                           (into {} (map (fn [param]
                                                           `['~(symbol (str sym "->" param)) {:value (fn [obj#]
                                                                                                       (get-in obj# [:params ~(keyword param)]))}])))))

                               `{'~sym {:value (rt/->ADT '~fq-sym {})}}))))})))

(defn emit-ns [{:keys [codes ns ns-header] :as arg}]
  {:deps (into #{} (concat (vals (:aliases ns-header))
                           (keys (:refers ns-header))))

   :cb `(fn [env#]
          (reduce (fn [env# code#]
                    (code# env#))
                  env#
                  [~@codes]))})
