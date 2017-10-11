(ns bridje.emitter
  (:require [clojure.string :as s]))

(defn sub-exprs [expr]
  (conj (case (:expr-type expr)
          (:string :bool :int :float :big-int :big-float :local :global :js-global) []
          (:vector :set :call :js-call) (mapcat sub-exprs (:exprs expr))
          :js-get (sub-exprs (:target-expr expr))
          :js-set (mapcat (comp sub-exprs expr) #{:target-expr :value-expr})
          :record (mapcat sub-exprs (map second (:entries expr)))
          :if (mapcat (comp sub-exprs expr) #{:pred-expr :then-expr :else-expr})
          :let (concat (mapcat sub-exprs (map second (:bindings expr)))
                       (sub-exprs (:body-expr expr)))
          :fn (sub-exprs (:body-expr expr)))
        expr))

(defn find-globals [expr]
  (->> (sub-exprs expr)
       (into {} (comp (filter #(= :global (:expr-type %)))
                      (map :global)
                      (distinct)
                      (map (fn [global]
                             [global (gensym (name global))]))))))

(defn find-records [expr]
  (->> (sub-exprs expr)
       (into {} (comp (filter #(= :record (:expr-type %)))
                      (map :entries)
                      (map #(into #{} (map first) %))
                      (distinct)
                      (map (fn [key-set]
                             [key-set (gensym 'record)]))))))

(defn emit-value-expr [expr {:keys [current-ns] :as env}]
  (let [records (find-records expr)
        globals (find-globals expr)]
    (letfn [(emit-value-expr* [{:keys [expr-type exprs] :as expr}]
              (case expr-type
                :string (pr-str (:string expr))
                :bool (pr-str (:bool expr))
                (:int :float) (pr-str (:number expr))
                (:big-int :big-float) (format "new _BigNumber('%s')" (str (:number expr)))

                :vector (format "_im.List.of(%s)"
                                (->> exprs
                                     (map emit-value-expr*)
                                     (s/join ", ")))

                :set (format "_im.Set.of(%s)"
                             (->> exprs
                                  (map emit-value-expr*)
                                  (s/join ", ")))

                :record (format "(new %s({%s}))"
                                (get records (into #{} (map first) (:entries expr)))
                                (->> (:entries expr)
                                     (map (fn [[sym expr]]
                                            (format "'%s': %s"
                                                    (str sym)
                                                    (emit-value-expr* expr))))
                                     (s/join ", ")))

                :if (format "%s ? %s : %s"
                            (emit-value-expr* (:pred-expr expr))
                            (emit-value-expr* (:then-expr expr))
                            (emit-value-expr* (:else-expr expr)))

                :local (str (:local expr))
                :global (get globals (:global expr))

                :js-global (name (:js-global expr))
                :js-call (format "%s.%s(%s)"
                                 (emit-value-expr* (:target-expr expr))
                                 (name (:method expr))
                                 (->> exprs
                                      (map emit-value-expr*)
                                      (s/join ", ")))

                :js-get (format "%s.%s" (emit-value-expr* (:target-expr expr)) (name (:field expr)))
                :js-set (format "(function () {const _val = %s; %s.%s = _val; return _val;})()"
                                (emit-value-expr* (:value-expr expr))
                                (emit-value-expr* (:target-expr expr))
                                (name (:field expr)))

                :let (let [{:keys [bindings body-expr]} expr]
                       (format "(function () {%s%n return %s;})()"
                               (->> bindings
                                    (map (fn [[local expr]]
                                           (format "let %s = %s;%n" local (emit-value-expr* expr))))
                                    s/join)
                               (emit-value-expr* body-expr)))

                :fn (let [{:keys [locals body-expr]} expr]
                      (format "(function (%s) {return %s;})"
                              (->> locals (map str) (s/join ", "))
                              (emit-value-expr* body-expr)))

                :call (let [[call-fn & args] exprs]
                        (format "%s(%s)"
                                (emit-value-expr* call-fn)
                                (->> args (map emit-value-expr*) (s/join ", "))))

                :match (throw (ex-info "niy" {:expr expr}))

                :loop (throw (ex-info "niy" {:expr expr}))
                :recur (throw (ex-info "niy" {:expr expr}))))]

      (format "(function () {%s})()"
              (s/join "\n"
                      [(s/join "\n"
                               (for [[global global-sym] globals]
                                 (format "const %s = _env.getIn(['%s', 'vars', '%s']);" (name global-sym) (namespace global) (name global))))
                       (s/join "\n"
                               (for [[key-set record-sym] records]
                                 (format "const %s = new _im.Record({%s});"
                                         (name record-sym)
                                         (->> (map #(format "'%s': null" (name %)) key-set)
                                              (s/join ", ")))))
                       (str "return " (emit-value-expr* expr))])))))

(defn emit-expr [{:keys [expr-type] :as expr} {:keys [global-env current-ns] :as env}]
  (case expr-type
    (:string :bool :vector :set :record :if :local :global :js-call :js-get :js-set :js-global :let :fn :call :match :loop :recur)
    {:global-env global-env,
     :code (emit-value-expr expr env)}

    :def (let [{:keys [sym locals body-expr]} expr]
           {:global-env (assoc-in global-env [current-ns :vars sym] {})

            :code (format "_ns = _ns.set(%s, %s);"
                          (pr-str (name sym))
                          (emit-value-expr (if (seq locals)
                                             {:expr-type :fn
                                              :locals locals
                                              :body-expr body-expr}
                                             body-expr)
                                           env))})

    :defdata (throw (ex-info "niy" {}))
    #_(let [{:keys [sym params]} expr
            fq-sym (symbol (name current-ns) (name sym))]
        {:global-env (-> global-env
                         (assoc-in [current-ns :types sym] {:params params})
                         (update-in [current-ns :vars] merge
                                    (if (seq params)
                                      (merge {(symbol (str "->" sym)) (eval `(fn [~@params]
                                                                               (->ADT '~fq-sym
                                                                                      ~(into {}
                                                                                             (map (fn [param]
                                                                                                    [(keyword param) param]))
                                                                                             params))))}
                                             (->> params
                                                  (into {} (map (fn [param]
                                                                  [(symbol (str sym "->" param)) (eval `(fn [obj#]
                                                                                                          (get-in obj# [:params ~(keyword param)])))])))))

                                      {sym (eval `(->ADT '~fq-sym {}))})))
         :value fq-sym})))

(defn emit-ns [{:keys [codes ns ns-header] :as arg}]
  (format "
const _BigNumber = require('bignumber.js');
const _im = require('immutable');

return {
  deps: _im.Set.of(%s),
  cb: function(_env) {
    let _ns = new _im.Map();

    %s

    return _env.setIn([%s, 'vars'], _ns);
  }
}
"
          (->> (into #{} (concat (vals (:aliases ns-header))
                                 (keys (:refers ns-header))))
               (map (comp pr-str name))
               (s/join ", "))
          (s/join "\n" codes)
          (pr-str (name ns))))

(comment
  (-> (first (bridje.reader/read-forms "(js/.isEmpty [])"))
      (bridje.analyser/analyse {})
      (emit-expr {})))
