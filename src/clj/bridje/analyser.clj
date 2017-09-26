(ns bridje.analyser)

(defn analyse [env ns-env {:keys [form-type forms] :as form}]
  (case form-type
    :string {:expr-type :string, :string (:string form)}
    :bool {:expr-type :bool, :bool (:bool form)}
    :vector {:expr-type :vector, :exprs (map #(analyse env ns-env %) forms)}
    :set {:expr-type :set, :exprs (map #(analyse env ns-env %) forms)}))
