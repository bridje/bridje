(ns bridje.emitter
  (:require [bridje.util :as u]
            [clojure.string :as s]))

(defn safe-name [sym]
  (-> (name sym)
      (s/replace #"_" "_US_")
      (s/replace #"-" "_DASH_")
      (s/replace #">" "_GT_")
      (s/replace #"<" "_LT_")
      (s/replace #"!" "_BANG_")
      (s/replace #"=" "_EQ_")
      (s/replace #"\?" "_Q_")))

(defn find-globals [expr]
  (->> (u/sub-exprs expr)
       (into {} (comp (filter #(= :global (:expr-type %)))
                      (map :global)
                      (distinct)
                      (map (fn [global]
                             [global (gensym (safe-name global))]))))))

(defn find-records [expr]
  (->> (u/sub-exprs expr)
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

                :match (let [{:keys [match-expr clauses default-expr]} expr]
                         (format "(function () {switch (%s._brjType) {%s \n default: return %s;}})()"
                                 (emit-value-expr* match-expr)
                                 (->> (for [[fq-sym expr] clauses]
                                        (format "case '%s/%s': return %s;"
                                                (namespace fq-sym) (name fq-sym)
                                                (emit-value-expr* expr)))
                                      (s/join "\n"))
                                 (emit-value-expr* default-expr)))

                :loop (throw (ex-info "niy" {:expr expr}))
                :recur (throw (ex-info "niy" {:expr expr}))))]

      (format "(function () {%s})()"
              (s/join "\n"
                      [(s/join "\n"
                               (for [[global global-sym] globals]
                                 (format "const %s = _env.getIn(['%s', 'vars', '%s', 'value']);" (name global-sym) (namespace global) (name global))))
                       (s/join "\n"
                               (for [[key-set record-sym] records]
                                 (format "const %s = _im.Record({%s});"
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

            :code (format "_ns = _ns.setIn(['vars', '%s'], _im.Map({value: %s}));"
                          (name sym)
                          (emit-value-expr (if (seq locals)
                                             {:expr-type :fn
                                              :locals locals
                                              :body-expr body-expr}
                                             body-expr)
                                           env))})

    :defdata
    (let [{:keys [sym params]} expr
          record-sym (gensym sym)]
      {:global-env (-> global-env
                       (assoc-in [current-ns :types sym] {:params params})
                       (update-in [current-ns :vars] merge

                                  (if (seq params)
                                    (merge {(symbol (str "->" sym)) {}}
                                           (->> params
                                                (into {} (map (fn [param]
                                                                [(symbol (str sym "->" param)) {}])))))

                                    {sym {}})))
       :code (->> [;; make record
                   (format "const %s = _im.Record({%s});"
                           (safe-name record-sym)
                           (->> params
                                (map #(format "'%s': null" (name %)))
                                (s/join ", ")))

                   (format "%s.prototype._brjType = '%s/%s';"
                           (safe-name record-sym) (name current-ns) (name sym))

                   (format "_ns = _ns.setIn(['types', '%s'], _im.Map({type: %s, params: [%s]}));"
                           (name sym)
                           (safe-name record-sym)
                           (->> params (map (comp pr-str name)) (s/join ", ")))

                   ;; make constructor functions + add to env
                   (if (seq params)
                     (format "_ns = _ns.setIn(['vars', '->%s'], _im.Map({value: %s}));"
                             (name sym)
                             (format "function (%s) {return new %s({%s});}"
                                     (->> params (map name) (s/join ", "))
                                     (name record-sym)
                                     (->> params
                                          (map (comp #(format "'%s': %s" % %) name))
                                          (s/join ", "))))
                     (format "_ns = _ns.setIn(['vars', '%s'], _im.Map({value: new %s({})}));"
                             (name sym)
                             (name record-sym)))

                   ;; make accessor functions and add to env
                   (when (seq params)
                     (format "_ns = _ns.update('vars', _vars => _vars.merge(_im.Map({%s})));"
                             (->> params
                                  (map (fn [param]
                                         (format "'%s->%s': _im.Map({value: _obj => _obj.get('%s')})"
                                                 (name sym) (name param)
                                                 (name param))))
                                  (s/join ", "))))]
                  (s/join "\n"))})))

(defn emit-ns [{:keys [codes ns ns-header] :as arg}]
  (format "
const _BigNumber = require('bignumber.js');
const _im = require('immutable');

return {
  deps: _im.Set.of(%s),
  cb: function(_env) {
    let _ns = new _im.Map();

    %s

    return _env.set(%s, _ns);
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
  (-> (first (bridje.reader/read-forms "(defdata Nothing)"))
      (bridje.analyser/analyse {})
      (emit-expr {})
      :code))
