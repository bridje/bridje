(ns bridje.main
  (:require [bridje.compiler :refer [compile!]]
            [bridje.file-io :as file-io]
            [clojure.java.io :as io]
            [clojure.string :as s]
            [clojure.set :as set]
            [clojure.tools.cli :as cli]))

(defn run-main [{:keys [main-ns args]} {:keys [io]}]
  (let [cbs (loop [to-load #{main-ns}
                   cbs []
                   loaded #{}]
              (if (empty? to-load)
                cbs
                (let [results (map (fn [ns-sym]
                                     (file-io/slurp-compiled-file io ns-sym :clj))
                                   to-load)]
                  (recur (set/difference (into #{} (mapcat :deps) results) loaded)
                         (into (into [] (map (comp eval :cb)) results) cbs)
                         (set/union loaded to-load)))))

        env (reduce (fn [env cb]
                      (cb env))
                    {}
                    cbs)]

    (when-let [main-fn (get-in env [main-ns :vars 'main :value])]
      (main-fn args))))

(defn -main [& args]
  (let [{[cmd & params] :arguments,
         {:keys [source-paths]} :options
         :as cli-opts} (cli/parse-opts args
                                       [["-p" "--source-paths PATHS" "Source paths"
                                         :parse-fn (comp #(map io/file %) #(s/split % #":"))]])

        io (file-io/->io {:source-paths (or source-paths
                                            [(io/file ".")])})]

    (case (keyword cmd)
      :compile (compile! (first params) {:env {},
                                         :io io})

      :run (run-main {:main-ns (symbol (first args))
                      :args (rest args)}
                     io))))
