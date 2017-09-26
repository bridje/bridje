(ns bridje.analyser)

(defn analyse [env ns-env {:keys [form-type] :as form}]
  (case form-type
    :string {:expr-type :string
             :string (:string form)}))
