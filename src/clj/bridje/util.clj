(ns bridje.util)

(defn sub-exprs [expr]
  (conj (case (:expr-type expr)
          (:string :bool :int :float :big-int :big-float :local :global :clj-var) []
          (:vector :set :call :recur) (mapcat sub-exprs (:exprs expr))
          :record (mapcat sub-exprs (map second (:entries expr)))
          :if (mapcat (comp sub-exprs expr) #{:pred-expr :then-expr :else-expr})
          (:let :loop) (concat (mapcat sub-exprs (map second (:bindings expr)))
                               (sub-exprs (:body-expr expr)))
          :match (concat (mapcat (comp sub-exprs second) (:clauses expr))
                         (mapcat (comp sub-exprs expr) #{:match-expr :default-expr}))
          :fn (sub-exprs (:body-expr expr)))
        expr))
