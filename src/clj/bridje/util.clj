(ns bridje.util)

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
          :match (concat (mapcat (comp sub-exprs second) (:clauses expr))
                         (mapcat (comp sub-exprs expr) #{:match-expr :default-expr}))
          :fn (sub-exprs (:body-expr expr)))
        expr))
