(ns bridje.analyser)

(defn analyse [env ns-env {:keys [form-type forms loc-range] :as form}]
  (case form-type
    :string {:expr-type :string, :string (:string form)}
    :bool {:expr-type :bool, :bool (:bool form)}
    :vector {:expr-type :vector, :exprs (map #(analyse env ns-env %) forms)}
    :set {:expr-type :set, :exprs (map #(analyse env ns-env %) forms)}
    :record (cond
              (pos? (mod (count forms) 2)) (throw (ex-info "Record requires even number of forms" {:loc-range loc-range}))
              :else {:expr-type :record
                     :entries (for [[{:keys [form-type loc-range] :as k-form} v-form] (partition 2 forms)]
                                (cond
                                  (not= :symbol form-type) (throw (ex-info "Expected symbol as key in record" {:loc-range loc-range}))
                                  (some? (:ns k-form)) (throw (ex-info "Unexpected namespaced symbol as key in record" {:loc-range loc-range}))
                                  :else [(:sym k-form) (analyse env ns-env v-form)]))})))
