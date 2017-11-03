(ns bridje.main
  (:require [bridje.compiler :refer [load-ns]]
            [bridje.file-io :as file-io]
            [clojure.java.io :as io]
            [clojure.string :as s]
            [clojure.set :as set]
            [clojure.tools.cli :as cli]))

(defn bootstrap-env [{:keys [io]}]
  (load-ns 'bridje.kernel.analyser {:io io}))

(defn run-main [{:keys [main-ns args]} {:keys [io env]}]
  (let [env (load-ns main-ns {:io io, :env (or env (bootstrap-env {:io io}))})]
    (when-let [main-fn (get-in env [main-ns :vars 'main :value])]
      (main-fn args))))

(defn -main [& args]
  (let [{[cmd & params] :arguments, :as cli-opts} (cli/parse-opts args [])

        io (file-io/->io {})]

    (case (keyword cmd)
      :run (run-main {:main-ns (symbol (first args))
                      :args (rest args)}
                     {:io io}))))
