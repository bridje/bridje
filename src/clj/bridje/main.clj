(ns bridje.main
  (:require [bridje.compiler :refer [compile!]]
            [bridje.file-io :as file-io]
            [clojure.java.io :as io]
            [clojure.string :as s]
            [clojure.set :as set]
            [clojure.tools.cli :as cli]))

(defn load-ns [ns {:keys [io env]}]
  (let [cbs (loop [to-load #{ns}
                   cbs []
                   loaded #{}]
              (if (empty? to-load)
                cbs
                (let [results (map (fn [ns-sym]
                                     (file-io/slurp-compiled-file io ns-sym :clj))
                                   to-load)]
                  (recur (set/difference (into #{} (mapcat :deps) results) loaded)
                         (into (into [] (map (comp eval :cb)) results) cbs)
                         (set/union loaded to-load)))))]

    (reduce (fn [env cb]
              (cb env))
            env
            cbs)))

(defn bootstrap-env [{:keys [io]}]
  (compile! 'bridje.kernel.analyser {:io io})
  (load-ns 'bridje.kernel.analyser {:io io}))

(defn run-main [{:keys [main-ns args]} {:keys [io env]}]
  (let [env (load-ns main-ns {:io io, :env (or env (bootstrap-env {:io io}))})]
    (when-let [main-fn (get-in env [main-ns :vars 'main :value])]
      (main-fn args))))

(defn -main [& args]
  (let [{[cmd & params] :arguments, :as cli-opts} (cli/parse-opts args [])

        io (file-io/->io {})]

    (case (keyword cmd)
      :compile (compile! (first params) {:io io})

      :run (run-main {:main-ns (symbol (first args))
                      :args (rest args)}
                     {:io io}))))
