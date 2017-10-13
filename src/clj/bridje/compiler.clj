(ns bridje.compiler
  (:require [bridje.reader :as reader]
            [bridje.analyser :as analyser]
            [bridje.emitter :as emitter]
            [bridje.file-io :as file-io]))

(defn read-ns-content [ns {:keys [io]}]
  (let [content (or (file-io/slurp-source-file io ns)
                    (throw (ex-info "Error reading NS" {:ns ns})))
        [ns-form & more-forms] (or (seq (reader/read-forms content))
                                   (throw (ex-info "Error reading forms in NS" {:ns ns})))
        {:keys [aliases refers] :as ns-header} (analyser/analyse-ns-form ns-form)]

    {:deps (into #{} (concat (vals aliases)
                             (keys refers)))
     :content {:ns-header ns-header
               :forms more-forms}}))

(defn transitive-read-forms [entry-ns-syms {:keys [io]}]
  (loop [[ns :as dep-queue] (vec entry-ns-syms)
         chains (into {} (map (juxt identity vector) entry-ns-syms))
         ns-order []
         ns-content {}]
    (cond
      (nil? ns) (for [ns ns-order]
                  (merge {:ns ns} (get ns-content ns)))

      (contains? ns-content ns) (recur (subvec dep-queue 1) chains (cons ns ns-order) ns-content)

      :else (let [{:keys [deps content]} (read-ns-content ns {:io io})
                  chain (get chains ns)]
              (if-let [cycle-ns (some (set chain) deps)]
                (throw (ex-info "cycle" {:cycle (reverse (cons cycle-ns chain))}))

                (recur (conj (into (subvec dep-queue 1)
                                   (remove (some-fn chains ns-content))
                                   deps)
                             ns)

                       (into chains (map (fn [dep-ns] [dep-ns (cons dep-ns chain)]) deps))

                       ns-order
                       (assoc ns-content ns content)))))))

(defn compile-ns [{:keys [ns ns-header forms]} env]
  (reduce (fn [{:keys [codes env]} form]
            (let [{:keys [global-env code]} (-> form
                                                (analyser/analyse env)
                                                (emitter/emit-expr env))]
              {:env (merge env {:global-env global-env})
               :codes (conj codes code)}))

          {:env (-> env
                    (assoc :current-ns ns)
                    (assoc-in [:global-env ns] ns-header))
           :codes []}

          forms))

(defn compile! [entry-ns {:keys [io env]}]
  (let [ns-order (transitive-read-forms [entry-ns] {:io io, :env env})]
    (reduce (fn [env {:keys [ns ns-header] :as ns-content}]
              (let [{:keys [env codes]} (compile-ns ns-content env)]
                (file-io/spit-compiled-file io ns :clj (emitter/emit-ns {:codes codes, :ns ns, :ns-header ns-header}))
                env))
            env
            ns-order)))
